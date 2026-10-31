package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ToggleSwitch extends AbstractButton {
	// Switch size and its widgets.png texture
	public static final int WIDTH = 30;
	public static final int HEIGHT = 16;
	private static final Identifier TEXTURE = Mccinfo.id("textures/gui/widgets.png");
	private static final int TEXTURE_SIZE = 128;
	private static final int KNOB_SIZE = 16;
	private static final int DISABLED_COLOR = 0xA0181818;
	private static final int LABEL_GAP = 4;
	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final float ANIMATION_MILLIS = 120.0F;

	private final BooleanSupplier value;
	private final Consumer<Boolean> onChange;
	private float knob;
	private long lastFrame;

	public ToggleSwitch(int x, int y, Component label, BooleanSupplier value, Consumer<Boolean> onChange) {
		super(x, y, WIDTH + (label.getString().isEmpty() ? 0 : LABEL_GAP + Minecraft.getInstance().font.width(label)), HEIGHT, label);
		this.value = value;
		this.onChange = onChange;
		this.knob = value.getAsBoolean() ? 1.0F : 0.0F;
	}

	// Background, then the knob at its place
	public static void draw(GuiGraphicsExtractor graphics, int x, int y, float knob, boolean enabled) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, WIDTH, HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
		int knobX = x + Math.round(knob * (WIDTH - KNOB_SIZE));
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, knobX, y, 0.0F, HEIGHT, KNOB_SIZE, KNOB_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
		if (!enabled) {
			graphics.fill(x, y, x + WIDTH, y + HEIGHT, DISABLED_COLOR);
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.onChange.accept(!this.value.getAsBoolean());
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Knob slides in 120 ms
		long now = Util.getMillis();
		float target = this.value.getAsBoolean() ? 1.0F : 0.0F;
		float step = this.lastFrame == 0 ? 1.0F : Math.min(1.0F, (now - this.lastFrame) / ANIMATION_MILLIS);
		this.lastFrame = now;
		this.knob += Math.signum(target - this.knob) * Math.min(Math.abs(target - this.knob), step);
		draw(graphics, this.getX(), this.getY(), this.knob, this.active);
		if (!this.getMessage().getString().isEmpty()) {
			Font font = Minecraft.getInstance().font;
			graphics.text(font, this.getMessage(), this.getX() + WIDTH + LABEL_GAP, this.getY() + (HEIGHT - font.lineHeight) / 2 + 1, LABEL_COLOR, true);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}

	@Override
	protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
		return CommonComponents.optionStatus(this.getMessage(), this.value.getAsBoolean());
	}
}
