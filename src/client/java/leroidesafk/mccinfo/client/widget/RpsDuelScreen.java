package leroidesafk.mccinfo.client.widget;

import com.google.gson.JsonObject;
import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.regex.Pattern;

public final class RpsDuelScreen extends Screen implements MenuBackdrop.Overlay {
	private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final int WIDTH = 220;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int LINE_GAP = 6;
	private static final int INPUT_HEIGHT = 14;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_WIDTH = 80;
	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_INPUT = 0xFF262626;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_META = 0xFF8C8C8C;
	private static final int COLOR_BUTTON_BORDER = 0xFF3A3A3A;
	private static final int COLOR_BUTTON = 0xFF2A2A2A;
	private static final int COLOR_BUTTON_HOVER = 0xFF3A3A3A;
	private static final int COLOR_BUTTON_TEXT = 0xFFFFFFFF;
	private static final int COLOR_BUTTON_OFF = 0xFF5A5A5A;

	private final @Nullable Screen parent;
	private EditBox name;
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelHeight;
	private int descriptionY;
	private int inputY;
	private int buttonX;
	private int buttonY;

	public RpsDuelScreen(@Nullable Screen parent) {
		super(Widgets.name("rps"));
		this.parent = parent;
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
		this.panelHeight = BORDER * 2 + PADDING * 2 + HEADER_HEIGHT + this.font.lineHeight + LINE_GAP + INPUT_HEIGHT + LINE_GAP * 2 + BUTTON_HEIGHT;
		this.left = (virtualWidth - WIDTH) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		int inner = WIDTH - BORDER * 2 - PADDING * 2;
		this.descriptionY = this.top + BORDER + PADDING + HEADER_HEIGHT;
		this.inputY = this.descriptionY + this.font.lineHeight + LINE_GAP;
		this.buttonX = this.left + (WIDTH - BUTTON_WIDTH) / 2;
		this.buttonY = this.inputY + INPUT_HEIGHT + LINE_GAP * 2;
		// Name field, kept when the screen is rebuilt
		String previous = this.name == null ? "" : this.name.getValue();
		this.name = new EditBox(this.font, this.left + BORDER + PADDING + 4, this.inputY + (INPUT_HEIGHT - 8) / 2, inner - 8, 9,
				Component.translatable("screen.mccinfo.widgets.rps.player"));
		this.name.setBordered(false);
		this.name.setTextColor(COLOR_TEXT);
		this.name.setMaxLength(16);
		this.name.setHint(Component.translatable("screen.mccinfo.widgets.rps.player").withColor(COLOR_META));
		this.name.setValue(previous);
		this.addWidget(this.name);
		this.setInitialFocus(this.name);
	}

	private String target() {
		return this.name == null ? "" : this.name.getValue().strip();
	}

	// Valid name, not your own
	private boolean valid() {
		String target = target();
		String own = this.minecraft == null || this.minecraft.player == null ? "" : this.minecraft.player.getGameProfile().name();
		return PLAYER_NAME.matcher(target).matches() && !target.toLowerCase(Locale.ROOT).equals(own.toLowerCase(Locale.ROOT));
	}

	private void send() {
		if (!valid()) {
			return;
		}
		NeuItems.clickSound();
		// Invite by name, then close everything
		JsonObject message = new JsonObject();
		message.addProperty("type", "rps_invite");
		message.addProperty("name", target());
		if (!SpotSync.sendVoice(message)) {
			if (this.minecraft.player != null) {
				this.minecraft.player.sendOverlayMessage(Component.translatable("message.mccinfo.mccinfo.offline").withStyle(ChatFormatting.RED));
			}
			return;
		}
		this.minecraft.gui.setScreen(null);
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
		graphics.text(this.font, Component.translatable("screen.mccinfo.widgets.rps.description"), contentLeft, this.descriptionY, COLOR_META, true);
		graphics.fill(contentLeft, this.inputY, contentLeft + inner, this.inputY + INPUT_HEIGHT, COLOR_INPUT);
		this.name.extractRenderState(graphics, virtualX, virtualY, a);
		// Send button, greyed while the name is not valid
		boolean valid = valid();
		boolean over = valid && overButton(virtualX, virtualY);
		graphics.fill(this.buttonX, this.buttonY, this.buttonX + BUTTON_WIDTH, this.buttonY + BUTTON_HEIGHT, COLOR_BUTTON_BORDER);
		graphics.fill(this.buttonX + 1, this.buttonY + 1, this.buttonX + BUTTON_WIDTH - 1, this.buttonY + BUTTON_HEIGHT - 1, over ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
		Component label = Component.translatable("screen.mccinfo.widgets.rps.send");
		graphics.text(this.font, label, this.buttonX + (BUTTON_WIDTH - this.font.width(label)) / 2, this.buttonY + (BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1,
				valid ? COLOR_BUTTON_TEXT : COLOR_BUTTON_OFF, true);
		if (over) {
			NeuItems.pointer(graphics);
		}
		graphics.pose().popMatrix();
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

	private boolean overButton(double x, double y) {
		return x >= this.buttonX && x < this.buttonX + BUTTON_WIDTH && y >= this.buttonY && y < this.buttonY + BUTTON_HEIGHT;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		int contentLeft = this.left + BORDER + PADDING;
		// Click on the field
		if (x >= contentLeft && x < this.left + WIDTH - BORDER - PADDING && y >= this.inputY && y < this.inputY + INPUT_HEIGHT) {
			this.setFocused(this.name);
			return true;
		}
		if (overButton(x, y)) {
			send();
			return true;
		}
		// Click outside goes back to the wheel
		if (x < this.left || x >= this.left + WIDTH || y < this.top || y >= this.top + this.panelHeight) {
			this.onClose();
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			send();
			return true;
		}
		return super.keyPressed(event);
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
