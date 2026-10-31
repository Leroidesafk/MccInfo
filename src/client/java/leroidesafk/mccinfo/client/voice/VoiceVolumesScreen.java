package leroidesafk.mccinfo.client.voice;

import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.MenuTooltip;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.gui.SliderKnob;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class VoiceVolumesScreen extends Screen implements MenuBackdrop.Overlay {
	// Panel and list layout
	private static final int MARGIN = 24;
	private static final int MAX_WIDTH = 320;
	private static final int MAX_HEIGHT = 380;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int SEARCH_HEIGHT = 16;
	private static final int GAP = 8;
	private static final int ROW_HEIGHT = 28;
	private static final int ROW_GAP = 4;
	private static final int ICON_SIZE = 16;
	private static final int ROW_PADDING = 6;
	// Volume sliders
	private static final int SLIDER_WIDTH = 140;
	private static final int SLIDER_HEIGHT = 16;
	private static final int KNOB_WIDTH = SliderKnob.WIDTH;
	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final int SCROLLBAR_GRAB = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final double MAX_VOLUME = 4.0;
	// Volume of everyone else
	private static final Identifier OTHERS_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/other_volume");

	// Colors
	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_ROW = 0xFF202020;
	private static final int COLOR_ROW_BORDER = 0xFF3A3A3A;
	private static final int COLOR_INPUT = 0xFF2C2C2C;
	private static final int COLOR_INPUT_BORDER = 0xFF161616;
	private static final int COLOR_SLIDER = 0xFF2A2A2A;
	private static final int COLOR_SLIDER_BORDER = 0xFF3A3A3A;
	private static final int COLOR_SLIDER_FILL = SettingsPanel.ACCENT_DARK;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_DESCRIPTION = 0xFF8C8C8C;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;
	private static final Map<UUID, ResolvableProfile> PROFILES = new HashMap<>();

	private final Screen parent;

	@Override
	public Screen backdropParent() {
		return this.parent;
	}
	// Player search
	private EditBox search;
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int searchY;
	private int othersY;
	private int listTop;
	private int listBottom;
	private float scroll;
	private float scrollTarget;
	private long lastFrame;
	private int maxScroll;
	private boolean draggingScrollbar;
	private double dragOffset;
	// Slider being dragged
	private UUID draggedSlider;
	private String draggedName;
	private boolean volumeChanged;
	// Players shown after the search
	private List<VoiceChat.VolumePlayer> shown = List.of();

	public VoiceVolumesScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.volumes.title"));
		this.parent = parent;
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
		int inner = this.top + BORDER + PADDING;
		this.searchY = inner + HEADER_HEIGHT;
		this.othersY = this.searchY + SEARCH_HEIGHT + GAP;
		this.listTop = this.othersY + ROW_HEIGHT + GAP + HEADER_HEIGHT;
		this.listBottom = this.top + this.panelHeight - BORDER - PADDING;
		String query = this.search == null ? "" : this.search.getValue();
		int contentLeft = this.left + BORDER + PADDING;
		this.search = new EditBox(this.font, contentLeft + 5, this.searchY + (SEARCH_HEIGHT - 8) / 2, contentWidth() - 10, 9,
				Component.translatable("screen.mccinfo.volumes.search"));
		this.search.setBordered(false);
		this.search.setTextColor(COLOR_WHITE);
		this.search.setMaxLength(16);
		this.search.setHint(Component.translatable("screen.mccinfo.volumes.search").withColor(COLOR_DESCRIPTION));
		this.search.setValue(query);
		this.search.setResponder(value -> this.scrollTarget = 0);
		this.addWidget(this.search);
	}

	private int contentWidth() {
		return this.panelWidth - BORDER * 2 - PADDING * 2;
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
		String query = this.search.getValue().trim().toLowerCase(Locale.ROOT);
		this.shown = VoiceChat.volumePlayers().stream().filter(player -> player.name().toLowerCase(Locale.ROOT).contains(query)).toList();
		int listHeight = this.shown.size() * (ROW_HEIGHT + ROW_GAP) - (this.shown.isEmpty() ? 0 : ROW_GAP);
		this.maxScroll = Math.max(0, listHeight - (this.listBottom - this.listTop));
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll);
		this.scroll = Mth.clamp(this.scroll, 0.0F, this.maxScroll);

		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		int right = this.left + this.panelWidth;
		int bottom = this.top + this.panelHeight;
		graphics.fill(this.left, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		int contentLeft = this.left + BORDER + PADDING;
		int width = contentWidth();
		extractHeader(graphics, this.title, contentLeft, width, this.top + BORDER + PADDING);

		graphics.fill(contentLeft, this.searchY, contentLeft + width, this.searchY + SEARCH_HEIGHT, COLOR_INPUT_BORDER);
		graphics.fill(contentLeft, this.searchY, contentLeft + width - 1, this.searchY + SEARCH_HEIGHT - 1, COLOR_INPUT);
		this.search.extractRenderState(graphics, virtualX, virtualY, a);

		boolean othersHovered = extractRow(graphics, VoiceChat.OTHERS, null, contentLeft, this.othersY, width, virtualX, virtualY);
		extractHeader(graphics, Component.translatable("screen.mccinfo.volumes.players"), contentLeft, width, this.othersY + ROW_HEIGHT + GAP);

		boolean inList = virtualY >= this.listTop && virtualY < this.listBottom;
		graphics.enableScissor(this.left + BORDER, this.listTop, right - BORDER, this.listBottom);
		if (this.shown.isEmpty()) {
			Component empty = Component.translatable(query.isEmpty() ? "screen.mccinfo.volumes.empty" : "screen.mccinfo.volumes.no_results");
			graphics.centeredText(this.font, empty, contentLeft + width / 2, this.listTop + GAP, COLOR_DESCRIPTION);
		}
		int y = this.listTop - Math.round(this.scroll);
		for (VoiceChat.VolumePlayer player : this.shown) {
			if (y + ROW_HEIGHT > this.listTop && y < this.listBottom) {
				extractRow(graphics, player.id(), player.name(), contentLeft, y, width, inList ? virtualX : Integer.MIN_VALUE, inList ? virtualY : Integer.MIN_VALUE);
			}
			y += ROW_HEIGHT + ROW_GAP;
		}
		graphics.disableScissor();

		if (this.maxScroll > 0) {
			int x = right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			int thumbY = thumbY();
			graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR);
		}
		if (othersHovered) {
			MenuTooltip.extract(graphics, Component.translatable("screen.mccinfo.volumes.others.description"), virtualX, virtualY, this.scale);
		}
		graphics.pose().popMatrix();
	}

	private boolean extractRow(GuiGraphicsExtractor graphics, UUID id, String name, int x, int y, int width, int mouseX, int mouseY) {
		graphics.fill(x, y, x + width, y + ROW_HEIGHT, COLOR_ROW_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + ROW_HEIGHT - 1, COLOR_ROW);
		int iconX = x + ROW_PADDING;
		int iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2;
		if (name == null) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OTHERS_ICON, ICON_SIZE, ICON_SIZE, 0, 0, iconX, iconY, ICON_SIZE, ICON_SIZE);
		} else {
			PlayerFaceExtractor.extractRenderState(graphics, PROFILES.computeIfAbsent(id, ResolvableProfile::createUnresolved), iconX, iconY, ICON_SIZE);
		}
		Component label = name == null ? Component.translatable("screen.mccinfo.volumes.others") : Component.literal(name);
		int textX = iconX + ICON_SIZE + ROW_PADDING;
		int sliderX = sliderX(x, width);
		int sliderY = y + (ROW_HEIGHT - SLIDER_HEIGHT) / 2;
		graphics.enableScissor(textX, y, sliderX - ROW_PADDING, y + ROW_HEIGHT);
		graphics.text(this.font, label, textX, y + (ROW_HEIGHT - this.font.lineHeight) / 2 + 1, COLOR_WHITE, true);
		graphics.disableScissor();
		boolean sliderHovered = mouseX >= sliderX && mouseX < sliderX + SLIDER_WIDTH && mouseY >= sliderY && mouseY < sliderY + SLIDER_HEIGHT;
		extractSlider(graphics, VoiceChat.volume(id), sliderX, sliderY, sliderHovered || id.equals(this.draggedSlider));
		if (sliderHovered) {
			NeuItems.pointer(graphics);
		}
		return name == null && mouseX >= x && mouseX < sliderX && mouseY >= y && mouseY < y + ROW_HEIGHT;
	}

	// Slider on the right of each row
	private static int sliderX(int x, int width) {
		return x + width - ROW_PADDING - SLIDER_WIDTH;
	}

	private void extractSlider(GuiGraphicsExtractor graphics, double volume, int x, int y, boolean active) {
		double value = Mth.clamp(volume / MAX_VOLUME, 0.0, 1.0);
		int trackY = SliderKnob.trackY(y, SLIDER_HEIGHT);
		int trackBottom = trackY + SliderKnob.TRACK_HEIGHT;
		graphics.fill(x, trackY, x + SLIDER_WIDTH, trackBottom, COLOR_SLIDER_BORDER);
		graphics.fill(x + 1, trackY + 1, x + SLIDER_WIDTH - 1, trackBottom - 1, COLOR_SLIDER);
		int inner = SLIDER_WIDTH - 2 - KNOB_WIDTH;
		int knobX = x + 1 + (int) Math.round(inner * value);
		graphics.fill(x + 1, trackY + 1, knobX, trackBottom - 1, COLOR_SLIDER_FILL);
		SliderKnob.draw(graphics, knobX, trackY, true);
		Component label = volume <= 0.0 ? Component.translatable("screen.mccinfo.volumes.muted")
				: Component.translatable("screen.mccinfo.volumes.amplification", amplification(volume));
		graphics.text(this.font, label, x + (SLIDER_WIDTH - this.font.width(label)) / 2, y + (SLIDER_HEIGHT - this.font.lineHeight) / 2 + 1, COLOR_WHITE, true);
	}

	// Change shown as a percentage
	private static String amplification(double volume) {
		long amount = Math.round(volume * 100.0 - 100.0);
		return (amount > 0 ? "+" : "") + amount + "%";
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

	// Slider under the mouse
	private UUID sliderAt(double x, double y) {
		int contentLeft = this.left + BORDER + PADDING;
		int sliderX = sliderX(contentLeft, contentWidth());
		if (x < sliderX || x >= sliderX + SLIDER_WIDTH) {
			return null;
		}
		if (y >= this.othersY && y < this.othersY + ROW_HEIGHT) {
			this.draggedName = null;
			return VoiceChat.OTHERS;
		}
		if (y < this.listTop || y >= this.listBottom) {
			return null;
		}
		int offset = (int) (y - this.listTop + this.scroll);
		int index = offset / (ROW_HEIGHT + ROW_GAP);
		if (index < 0 || index >= this.shown.size() || offset % (ROW_HEIGHT + ROW_GAP) >= ROW_HEIGHT) {
			return null;
		}
		this.draggedName = this.shown.get(index).name();
		return this.shown.get(index).id();
	}

	private void dragSlider(double x) {
		int sliderX = sliderX(this.left + BORDER + PADDING, contentWidth());
		double value = Mth.clamp((x - sliderX - 1 - KNOB_WIDTH / 2.0) / (SLIDER_WIDTH - 2 - KNOB_WIDTH), 0.0, 1.0);
		VoiceChat.setVolume(this.draggedSlider, this.draggedName, value * MAX_VOLUME);
		this.volumeChanged = true;
	}

	// Click a slider to drag it
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		int right = this.left + this.panelWidth;
		int contentLeft = this.left + BORDER + PADDING;
		if (x >= contentLeft && x < contentLeft + contentWidth() && y >= this.searchY && y < this.searchY + SEARCH_HEIGHT) {
			this.setFocused(this.search);
			this.search.onClick(new MouseButtonEvent(x, y, event.buttonInfo()), doubleClick);
			return true;
		}
		this.setFocused(null);
		if (this.maxScroll > 0 && x >= right - BORDER - SCROLLBAR_GRAB && x < right && y >= this.listTop && y < this.top + this.panelHeight - BORDER) {
			int thumbY = thumbY();
			int thumbHeight = thumbHeight();
			this.dragOffset = y >= thumbY && y < thumbY + thumbHeight ? y - thumbY : thumbHeight / 2.0;
			this.draggingScrollbar = true;
			dragScrollbar(y);
			return true;
		}
		UUID slider = sliderAt(x, y);
		if (slider != null) {
			NeuItems.clickSound();
			this.draggedSlider = slider;
			dragSlider(x);
			return true;
		}
		if (x < this.left || x >= right || y < this.top || y >= this.top + this.panelHeight) {
			this.onClose();
		}
		return true;
	}

	private void dragScrollbar(double y) {
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			this.scrollTarget = Mth.clamp((float) ((y - this.dragOffset - this.listTop) * this.maxScroll / range), 0.0F, this.maxScroll);
			this.scroll = this.scrollTarget;
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingScrollbar) {
			dragScrollbar(event.y() / this.scale);
			return true;
		}
		if (this.draggedSlider != null) {
			dragSlider(event.x() / this.scale);
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
		if (this.draggedSlider != null) {
			this.draggedSlider = null;
			saveVolumes();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		UUID slider = scrollY == 0 || this.draggedSlider != null ? null : sliderAt(x / this.scale, y / this.scale);
		if (slider != null) {
			long percent = Math.round(VoiceChat.volume(slider) * 100.0) + (long) Math.signum(scrollY);
			VoiceChat.setVolume(slider, this.draggedName, Mth.clamp(percent, 0L, Math.round(MAX_VOLUME * 100.0)) / 100.0);
			this.volumeChanged = true;
			return true;
		}
		if (this.maxScroll > 0 && scrollY != 0) {
			this.scrollTarget = Mth.clamp(this.scrollTarget - (float) scrollY * SCROLL_STEP, 0.0F, this.maxScroll);
		}
		return true;
	}

	// Volumes saved only if they changed
	private void saveVolumes() {
		if (this.volumeChanged) {
			this.volumeChanged = false;
			VoiceChat.saveVolumes();
		}
	}

	@Override
	public void removed() {
		this.draggedSlider = null;
		saveVolumes();
		super.removed();
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
