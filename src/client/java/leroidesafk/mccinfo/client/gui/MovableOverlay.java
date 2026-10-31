package leroidesafk.mccinfo.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

// Box moved, resized and hidden in the Overlay tab
public interface MovableOverlay {
	boolean enabled();

	boolean visible();

	int width();

	int height();

	int x(int screenWidth);

	int y(int screenHeight);

	void setPosition(int x, int y);

	// Optional: resize, text size and zoom
	default boolean resizable() {
		return false;
	}

	default boolean resizableFromTop() {
		return false;
	}

	default void setSize(int width, int height) {
	}

	default boolean textScalable() {
		return false;
	}

	default void changeTextScale(int steps) {
	}

	default void resetTextScale() {
	}

	default boolean zoomable() {
		return false;
	}

	default void changeZoom(int steps) {
	}

	default void resetZoom() {
	}

	// Hidden from the HUD, still shown faded in the Overlay tab
	boolean hidden();

	void setHidden(boolean hidden);

	void extractPreview(GuiGraphicsExtractor graphics, int x, int y, float alpha);
}
