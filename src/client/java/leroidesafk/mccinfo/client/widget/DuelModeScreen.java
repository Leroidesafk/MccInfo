package leroidesafk.mccinfo.client.widget;

import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Function;

public final class DuelModeScreen extends Screen implements MenuBackdrop.Overlay {
	private static final int WIDTH = 220;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int LINE_GAP = 6;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_GAP = 6;
	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_META = 0xFF8C8C8C;
	private static final int COLOR_BUTTON_BORDER = 0xFF3A3A3A;
	private static final int COLOR_BUTTON = 0xFF2A2A2A;
	private static final int COLOR_BUTTON_HOVER = 0xFF3A3A3A;
	private static final int COLOR_BUTTON_TEXT = 0xFFFFFFFF;
	private static final int COLOR_BUTTON_OFF = 0xFF5A5A5A;

	private final @Nullable Screen parent;
	private final String widget;
	private final Function<Screen, Screen> playerScreen;
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelHeight;
	private int buttonsY;
	private int buttonWidth;
	private List<FormattedCharSequence> description = List.of();

	public DuelModeScreen(@Nullable Screen parent, String widget, Function<Screen, Screen> playerScreen) {
		super(Widgets.name(widget));
		this.parent = parent;
		this.widget = widget;
		this.playerScreen = playerScreen;
	}

	@Override
	public Screen backdropParent() {
		return this.parent;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Wheel dimmed behind
		if (!MenuBackdrop.extract(this.parent, graphics, this.width, this.height, a)) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		// Description wrapped to the panel width
		this.description = this.font.split(Component.translatable("screen.mccinfo.widgets.duel.description"), WIDTH - BORDER * 2 - PADDING * 2);
		int descriptionHeight = this.description.size() * this.font.lineHeight;
		this.panelHeight = BORDER * 2 + PADDING * 2 + HEADER_HEIGHT + descriptionHeight + LINE_GAP * 2 + BUTTON_HEIGHT;
		this.left = (virtualWidth - WIDTH) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.buttonsY = this.top + BORDER + PADDING + HEADER_HEIGHT + descriptionHeight + LINE_GAP * 2;
		this.buttonWidth = (WIDTH - BORDER * 2 - PADDING * 2 - BUTTON_GAP) / 2;
	}

	private int buttonX(int index) {
		return this.left + BORDER + PADDING + index * (this.buttonWidth + BUTTON_GAP);
	}

	private boolean overButton(int index, double x, double y) {
		return x >= buttonX(index) && x < buttonX(index) + this.buttonWidth && y >= this.buttonsY && y < this.buttonsY + BUTTON_HEIGHT;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int virtualX = (int) (mouseX / this.scale);
		int virtualY = (int) (mouseY / this.scale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		int right = this.left + WIDTH;
		int bottom = this.top + this.panelHeight;
		int inner = WIDTH - BORDER * 2 - PADDING * 2;
		int contentLeft = this.left + BORDER + PADDING;
		graphics.fill(this.left, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		header(graphics, this.title, contentLeft, inner, this.top + BORDER + PADDING);
		// Description lines
		int lineY = this.top + BORDER + PADDING + HEADER_HEIGHT;
		for (FormattedCharSequence line : this.description) {
			graphics.text(this.font, line, contentLeft, lineY, COLOR_META, true);
			lineY += this.font.lineHeight;
		}
		// Buttons, Waypoint greyed outside the hub and fishing
		button(graphics, 0, Component.translatable("screen.mccinfo.widgets.duel.player"), virtualX, virtualY, true);
		button(graphics, 1, Component.translatable("screen.mccinfo.widgets.duel.waypoint"), virtualX, virtualY, WidgetWaypoints.canPlace());
		graphics.pose().popMatrix();
	}

	private void button(GuiGraphicsExtractor graphics, int index, Component label, int mouseX, int mouseY, boolean enabled) {
		int x = buttonX(index);
		boolean over = enabled && overButton(index, mouseX, mouseY);
		graphics.fill(x, this.buttonsY, x + this.buttonWidth, this.buttonsY + BUTTON_HEIGHT, COLOR_BUTTON_BORDER);
		graphics.fill(x + 1, this.buttonsY + 1, x + this.buttonWidth - 1, this.buttonsY + BUTTON_HEIGHT - 1, over ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
		graphics.text(this.font, label, x + (this.buttonWidth - this.font.width(label)) / 2, this.buttonsY + (BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1,
				enabled ? COLOR_BUTTON_TEXT : COLOR_BUTTON_OFF, true);
		if (over) {
			NeuItems.pointer(graphics);
		}
	}

	private void header(GuiGraphicsExtractor graphics, Component label, int x, int width, int y) {
		int textWidth = this.font.width(label);
		int lineLength = Math.max(0, (width - textWidth - HEADER_LABEL_GAP * 2) / 2);
		int textY = y + (HEADER_HEIGHT - this.font.lineHeight) / 2 - 3;
		int lineY = textY + this.font.lineHeight / 2 - 1;
		if (lineLength > 0) {
			graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_HEADER_LINE);
		}
		graphics.text(this.font, label, x + lineLength + HEADER_LABEL_GAP, textY, COLOR_HEADER_TEXT, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		// Player: name screen over the wheel
		if (overButton(0, x, y)) {
			NeuItems.clickSound();
			this.minecraft.gui.setScreen(this.playerScreen.apply(this.parent));
			return true;
		}
		// Waypoint: placed at the feet (messages until I find an alternative)
		if (overButton(1, x, y)) {
			if (!WidgetWaypoints.canPlace()) {
				if (this.minecraft.player != null) {
					this.minecraft.player.sendOverlayMessage(Component.translatable("message.mccinfo.widgets.waypoint_server").withStyle(ChatFormatting.RED));
				}
				return true;
			}
			NeuItems.clickSound();
			this.minecraft.gui.setScreen(null);
			if (!WidgetWaypoints.place(this.widget) && this.minecraft.player != null) {
				this.minecraft.player.sendOverlayMessage(Component.translatable("message.mccinfo.mccinfo.offline").withStyle(ChatFormatting.RED));
			}
			return true;
		}
		// Click outside goes back to the wheel
		if (x < this.left || x >= this.left + WIDTH || y < this.top || y >= this.top + this.panelHeight) {
			this.onClose();
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
