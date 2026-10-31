package leroidesafk.mccinfo.client.widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

public final class RpsBetScreen extends Screen {
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int FIELD_WIDTH = 100;
	private static final int BUTTON_WIDTH = 98;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SPACING = 4;

	private final Screen parent;
	private final String targetName;
	private final int max;
	private final IntConsumer challenge;
	private EditBox bet;
	private Button send;

	public RpsBetScreen(Screen parent, String targetName, int max, IntConsumer challenge) {
		super(Component.translatable("screen.mccinfo.rps.bet_title", targetName));
		this.parent = parent;
		this.targetName = targetName;
		this.max = max;
		this.challenge = challenge;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - BUTTON_HEIGHT;
		// Bet field
		String previous = this.bet == null ? "1" : this.bet.getValue();
		this.bet = new EditBox(this.font, centerX - FIELD_WIDTH / 2, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.translatable("screen.mccinfo.rps.bet"));
		this.bet.setMaxLength(9);
		this.bet.setValue(previous);
		this.bet.setResponder(value -> updateSend());
		this.addRenderableWidget(this.bet);
		this.setInitialFocus(this.bet);
		// Challenge and cancel buttons
		this.send = this.addRenderableWidget(Button.builder(Component.translatable("screen.mccinfo.rps.challenge").withStyle(ChatFormatting.GREEN),
				button -> challenge()).bounds(centerX - BUTTON_WIDTH - SPACING / 2, y + BUTTON_HEIGHT + SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
				.bounds(centerX + SPACING / 2, y + BUTTON_HEIGHT + SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		updateSend();
	}

	private int betValue() {
		try {
			int value = Integer.parseInt(this.bet.getValue().trim());
			return value >= 1 && value <= this.max ? value : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void updateSend() {
		if (this.send != null) {
			this.send.active = betValue() > 0;
		}
	}

	private void challenge() {
		int value = betValue();
		if (value > 0) {
			this.onClose();
			this.challenge.accept(value);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && betValue() > 0) {
			challenge();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int centerX = this.width / 2;
		int y = this.height / 2 - BUTTON_HEIGHT;
		// Title
		graphics.centeredText(this.font, Component.translatable("screen.mccinfo.rps.bet_title",
				Component.literal(this.targetName).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.BOLD), centerX, y - 36, TEXT_COLOR);
		// Allowed range, red if invalid
		graphics.centeredText(this.font, Component.translatable("screen.mccinfo.rps.bet_range", this.max)
				.withStyle(betValue() > 0 ? ChatFormatting.GRAY : ChatFormatting.RED), centerX, y - 16, TEXT_COLOR);
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
