package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class SliderKnob {
	// Knob size and how far it goes over the track
	public static final int WIDTH = 5;
	public static final int HEIGHT = 16;
	public static final int PROTRUDE = 2;
	public static final int TRACK_HEIGHT = HEIGHT - PROTRUDE * 2;
	// Knob in widgets.png
	private static final Identifier TEXTURE = Mccinfo.id("textures/gui/widgets.png");
	private static final int TEXTURE_SIZE = 128;
	private static final int U = 30;
	private static final int V = 0;
	private static final int DISABLED_COLOR = 0xA0181818;

	private SliderKnob() {
	}

	public static int trackY(int y, int height) {
		return y + (height - TRACK_HEIGHT) / 2;
	}

	// Drawn at its own size, greyed when disabled
	public static void draw(GuiGraphicsExtractor graphics, int x, int trackY, boolean active) {
		int y = trackY - PROTRUDE;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, U, V, WIDTH, HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
		if (!active) {
			graphics.fill(x, y, x + WIDTH, y + HEIGHT, DISABLED_COLOR);
		}
	}
}
