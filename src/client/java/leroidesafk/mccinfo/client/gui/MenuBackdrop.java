package leroidesafk.mccinfo.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class MenuBackdrop {
	// Dim over the menu, mouse far away so nothing is hovered
	private static final int DIM_COLOR = 0xB0000000;
	private static final int NO_MOUSE = -10_000;

	// Screen opened over a menu
	public interface Overlay {
		Screen backdropParent();
	}

	// Screen that can be drawn behind another, like the Widgets Wheel
	public interface Host {
	}

	private static boolean host(Screen parent) {
		return parent instanceof EventsScreen || parent instanceof Host;
	}

	private MenuBackdrop() {
	}

	// Gets the opening slide and fade of the menu windows
	public static boolean drawnOver(Screen screen) {
		return screen instanceof Overlay overlay && host(overlay.backdropParent());
	}

	// Menu drawn as it is, then dimmed
	public static boolean extract(Screen parent, GuiGraphicsExtractor graphics, int width, int height, float a) {
		if (!host(parent)) {
			return false;
		}
		if (parent.width != width || parent.height != height) {
			parent.init(width, height);
		}
		// Without the slide and fade of the screen on top
		boolean shifted = ScreenOpenAnimation.suspendShift(graphics);
		float alpha = ScreenOpenAnimation.suspendAlpha();
		try {
			parent.extractBackground(graphics, NO_MOUSE, NO_MOUSE, a);
			graphics.nextStratum();
			parent.extractRenderState(graphics, NO_MOUSE, NO_MOUSE, a);
			graphics.nextStratum();
		} finally {
			ScreenOpenAnimation.restoreAlpha(alpha);
		}
		graphics.fill(0, 0, width, height, DIM_COLOR);
		ScreenOpenAnimation.resumeShift(graphics, shifted);
		return true;
	}
}
