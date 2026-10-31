package leroidesafk.mccinfo.client.voice;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class VoiceDisclaimerScreen extends Screen {
	// Panel layout
	private static final int WIDTH = 260;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int PARAGRAPH_GAP = 6;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_WIDTH = 110;
	// Colors
	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_BUTTON_BORDER = 0xFF3A3A3A;
	private static final int COLOR_BUTTON = 0xFF2A2A2A;
	private static final int COLOR_BUTTON_HOVER = 0xFF3A3A3A;
	private static final int COLOR_BUTTON_TEXT = 0xFFFFFFFF;
	// Texts of the notice
	private static final String[] PARAGRAPHS = {"screen.mccinfo.voice_disclaimer.text", "screen.mccinfo.voice_disclaimer.report"};

	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelHeight;
	private int buttonX;
	private int buttonY;
	private final List<List<FormattedCharSequence>> paragraphs = new ArrayList<>();

	public VoiceDisclaimerScreen() {
		super(Component.translatable("screen.mccinfo.voice_disclaimer.title"));
	}

	// Shown once, or on request from the debug command
	private static boolean ticking;
	private static boolean requested;

	static void register() {
		if (ticking) {
			return;
		}
		ticking = true;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (requested && client.gui.screen() == null) {
				requested = false;
				client.gui.setScreen(new VoiceDisclaimerScreen());
				return;
			}
			if (!ModConfig.get().voiceDisclaimerSeen && client.gui.screen() == null && client.player != null && MccIsland.isConnected()
					&& VoiceChat.present() && Permissions.has(Permissions.Perm.VOICE_CHAT)) {
				client.gui.setScreen(new VoiceDisclaimerScreen());
			}
		});
	}

	public static int open() {
		register();
		requested = true;
		return 1;
	}

	// Text wrapped to the panel width
	@Override
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		int inner = WIDTH - BORDER * 2 - PADDING * 2;
		this.paragraphs.clear();
		int textHeight = 0;
		for (String key : PARAGRAPHS) {
			List<FormattedCharSequence> lines = this.font.split(Component.translatable(key), inner);
			this.paragraphs.add(lines);
			textHeight += lines.size() * this.font.lineHeight + PARAGRAPH_GAP;
		}
		this.panelHeight = BORDER * 2 + PADDING * 2 + HEADER_HEIGHT + textHeight + PARAGRAPH_GAP + BUTTON_HEIGHT;
		this.left = (virtualWidth - WIDTH) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.buttonX = this.left + (WIDTH - BUTTON_WIDTH) / 2;
		this.buttonY = this.top + this.panelHeight - BORDER - PADDING - BUTTON_HEIGHT;
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
		int contentLeft = this.left + BORDER + PADDING;
		int inner = WIDTH - BORDER * 2 - PADDING * 2;
		graphics.fill(this.left, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		header(graphics, this.title, contentLeft, inner, this.top + BORDER + PADDING);
		int y = this.top + BORDER + PADDING + HEADER_HEIGHT;
		for (List<FormattedCharSequence> lines : this.paragraphs) {
			for (FormattedCharSequence line : lines) {
				graphics.text(this.font, line, contentLeft, y, COLOR_TEXT, true);
				y += this.font.lineHeight;
			}
			y += PARAGRAPH_GAP;
		}
		boolean over = overButton(virtualX, virtualY);
		graphics.fill(this.buttonX, this.buttonY, this.buttonX + BUTTON_WIDTH, this.buttonY + BUTTON_HEIGHT, COLOR_BUTTON_BORDER);
		graphics.fill(this.buttonX + 1, this.buttonY + 1, this.buttonX + BUTTON_WIDTH - 1, this.buttonY + BUTTON_HEIGHT - 1, over ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
		Component label = Component.translatable("screen.mccinfo.voice_disclaimer.ok");
		graphics.text(this.font, label, this.buttonX + (BUTTON_WIDTH - this.font.width(label)) / 2, this.buttonY + (BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1,
				COLOR_BUTTON_TEXT, true);
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

	// Never shown again once accepted
	private void accept() {
		NeuItems.clickSound();
		ModConfig config = ModConfig.get();
		config.voiceDisclaimerSeen = true;
		config.save();
		this.minecraft.gui.setScreen(null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && overButton(event.x() / this.scale, event.y() / this.scale)) {
			accept();
			return true;
		}
		return true;
	}

	// Enter or Escape also accept
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER || event.key() == GLFW.GLFW_KEY_ESCAPE) {
			accept();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
