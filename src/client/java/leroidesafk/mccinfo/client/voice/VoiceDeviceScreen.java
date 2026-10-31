package leroidesafk.mccinfo.client.voice;

import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class VoiceDeviceScreen extends Screen implements MenuBackdrop.Overlay {
	// Panel and list layout
	private static final int MARGIN = 24;
	private static final int MAX_WIDTH = 320;
	private static final int MAX_HEIGHT = 380;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_GAP = 4;
	private static final int ROW_PADDING = 6;
	private static final int ICON_SIZE = 16;
	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final int SCROLLBAR_GRAB = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	// Simple Voice Chat icons
	private static final Identifier MICROPHONE_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/microphone");
	private static final Identifier SPEAKER_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/speaker");
	private static final Identifier SELECTED_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/device_selected");

	// Colors
	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_ROW = 0xFF202020;
	private static final int COLOR_ROW_HOVER = 0xFF2A2A2A;
	private static final int COLOR_ROW_BORDER = 0xFF3A3A3A;
	private static final int COLOR_SELECTED = SettingsPanel.ACCENT;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;

	private final Screen parent;

	@Override
	public Screen backdropParent() {
		return this.parent;
	}
	// Microphone or speaker list
	private final boolean microphone;
	private final List<String> devices = new ArrayList<>();
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listTop;
	private int listBottom;
	private float scroll;
	private float scrollTarget;
	private long lastFrame;
	private int maxScroll;
	private boolean draggingScrollbar;
	private double dragOffset;

	public VoiceDeviceScreen(Screen parent, boolean microphone) {
		super(Component.translatable(microphone ? "screen.mccinfo.devices.microphone" : "screen.mccinfo.devices.speaker"));
		this.parent = parent;
		this.microphone = microphone;
	}

	@Override
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		this.panelWidth = Math.min(MAX_WIDTH, virtualWidth - MARGIN * 2);
		this.panelHeight = Math.min(MAX_HEIGHT, virtualHeight - MARGIN * 2);
		this.left = (virtualWidth - this.panelWidth) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.listTop = this.top + BORDER + PADDING + HEADER_HEIGHT + 2;
		this.listBottom = this.top + this.panelHeight - BORDER - PADDING;
		this.devices.clear();
		this.devices.add("");
		this.devices.addAll(this.microphone ? VoiceSettings.microphones() : VoiceSettings.speakers());
	}

	private int contentLeft() {
		return this.left + BORDER + PADDING;
	}

	private int contentWidth() {
		return this.panelWidth - BORDER * 2 - PADDING * 2;
	}

	// Device in use, default when none
	private String selected() {
		return this.microphone ? VoiceSettings.microphone() : VoiceSettings.speaker();
	}

	private boolean isSelected(String device) {
		String selected = selected();
		return device.isEmpty() ? selected == null || selected.isEmpty() || !this.devices.contains(selected) : device.equals(selected);
	}

	// Menu dimmed behind
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!MenuBackdrop.extract(this.parent, graphics, this.width, this.height, a)) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		updateScroll();
		int virtualX = (int) (mouseX / this.scale);
		int virtualY = (int) (mouseY / this.scale);
		int height = this.devices.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
		this.maxScroll = Math.max(0, height - (this.listBottom - this.listTop));
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll);
		this.scroll = Mth.clamp(this.scroll, 0.0F, this.maxScroll);

		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		int right = this.left + this.panelWidth;
		int bottom = this.top + this.panelHeight;
		graphics.fill(this.left, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		int x = contentLeft();
		int width = contentWidth();
		extractHeader(graphics, this.title, x, width, this.top + BORDER + PADDING);

		boolean inList = virtualY >= this.listTop && virtualY < this.listBottom;
		graphics.enableScissor(this.left + BORDER, this.listTop, right - BORDER, this.listBottom);
		int y = this.listTop - Math.round(this.scroll);
		for (String device : this.devices) {
			if (y + ROW_HEIGHT > this.listTop && y < this.listBottom) {
				boolean hovered = inList && virtualX >= x && virtualX < x + width && virtualY >= y && virtualY < y + ROW_HEIGHT;
				boolean selected = isSelected(device);
				graphics.fill(x, y, x + width, y + ROW_HEIGHT, selected ? COLOR_SELECTED : COLOR_ROW_BORDER);
				graphics.fill(x + 1, y + 1, x + width - 1, y + ROW_HEIGHT - 1, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
				int iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.microphone ? MICROPHONE_ICON : SPEAKER_ICON, x + ROW_PADDING, iconY, ICON_SIZE, ICON_SIZE);
				if (selected) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTED_ICON, x + ROW_PADDING, iconY, ICON_SIZE, ICON_SIZE);
				}
				Component name = device.isEmpty() ? Component.translatable("screen.mccinfo.devices.default") : Component.literal(VoiceSettings.deviceName(device));
				int textX = x + ROW_PADDING * 2 + ICON_SIZE;
				graphics.enableScissor(textX, Math.max(y, this.listTop), x + width - ROW_PADDING, Math.min(y + ROW_HEIGHT, this.listBottom));
				graphics.text(this.font, name, textX, y + (ROW_HEIGHT - this.font.lineHeight) / 2 + 1, selected || hovered ? COLOR_WHITE : COLOR_TEXT, true);
				graphics.disableScissor();
				if (hovered && !selected) {
					NeuItems.pointer(graphics);
				}
			}
			y += ROW_HEIGHT + ROW_GAP;
		}
		graphics.disableScissor();
		if (this.maxScroll > 0) {
			int barX = right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			int thumbY = thumbY();
			graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR);
		}
		graphics.pose().popMatrix();
	}

	private void extractHeader(GuiGraphicsExtractor graphics, Component label, int x, int width, int y) {
		int textWidth = this.font.width(label);
		int lineLength = Math.max(0, (width - textWidth - HEADER_LABEL_GAP * 2) / 2);
		int textY = y + (HEADER_HEIGHT - this.font.lineHeight) / 2;
		int lineY = textY + this.font.lineHeight / 2 - 1;
		if (lineLength > 0) {
			graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_HEADER_LINE);
		}
		graphics.text(this.font, label, x + lineLength + HEADER_LABEL_GAP, textY, COLOR_HEADER_TEXT, true);
	}

	// Smooth scroll
	private void updateScroll() {
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		if (this.draggingScrollbar || Math.abs(this.scrollTarget - this.scroll) < 0.5F) {
			this.scroll = this.scrollTarget;
		} else {
			this.scroll += (this.scrollTarget - this.scroll) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
	}

	private int trackHeight() {
		return this.top + this.panelHeight - BORDER - this.listTop;
	}

	private int thumbHeight() {
		int trackHeight = trackHeight();
		return Math.max(MIN_THUMB, trackHeight * trackHeight / (trackHeight + this.maxScroll));
	}

	private int thumbY() {
		return this.listTop + Math.round((trackHeight() - thumbHeight()) * this.scroll / this.maxScroll);
	}

	// Scrollbar dragged like the menu
	private void dragScrollbar(double y) {
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			this.scrollTarget = Mth.clamp((float) ((y - this.dragOffset - this.listTop) * this.maxScroll / range), 0.0F, this.maxScroll);
			this.scroll = this.scrollTarget;
		}
	}

	// Click a device to use it
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		int right = this.left + this.panelWidth;
		if (this.maxScroll > 0 && x >= right - BORDER - SCROLLBAR_GRAB && x < right && y >= this.listTop && y < this.top + this.panelHeight - BORDER) {
			int thumbY = thumbY();
			int thumbHeight = thumbHeight();
			this.dragOffset = y >= thumbY && y < thumbY + thumbHeight ? y - thumbY : thumbHeight / 2.0;
			this.draggingScrollbar = true;
			dragScrollbar(y);
			return true;
		}
		int contentLeft = contentLeft();
		if (x >= contentLeft && x < contentLeft + contentWidth() && y >= this.listTop && y < this.listBottom) {
			int offset = (int) (y - this.listTop + this.scroll);
			int index = offset / (ROW_HEIGHT + ROW_GAP);
			if (index >= 0 && index < this.devices.size() && offset % (ROW_HEIGHT + ROW_GAP) < ROW_HEIGHT) {
				String device = this.devices.get(index);
				if (!isSelected(device)) {
					NeuItems.clickSound();
					if (this.microphone) {
						VoiceSettings.setMicrophone(device);
					} else {
						VoiceSettings.setSpeaker(device);
					}
				}
				return true;
			}
		}
		if (x < this.left || x >= right || y < this.top || y >= this.top + this.panelHeight) {
			this.onClose();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingScrollbar) {
			dragScrollbar(event.y() / this.scale);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.draggingScrollbar) {
			this.draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.maxScroll > 0 && scrollY != 0) {
			this.scrollTarget = Mth.clamp(this.scrollTarget - (float) scrollY * SCROLL_STEP, 0.0F, this.maxScroll);
		}
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
