package leroidesafk.mccinfo.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class MenuTooltip {
	// Tooltip size and colors
	private static final int MAX_WIDTH = 200;
	private static final int PADDING = 5;
	private static final int LINE = 10;
	private static final int OFFSET = 8;
	private static final int MARGIN = 4;
	private static final int COLOR_BORDER = 0xFF3A3A3A;
	private static final int COLOR_BACKGROUND = 0xFF202020;
	private static final int COLOR_TEXT = 0xFFFFFFFF;

	private MenuTooltip() {
	}

	public static void extract(GuiGraphicsExtractor graphics, Component text, int mouseX, int mouseY, float scale) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		// Text wrapped to the max width
		List<FormattedCharSequence> lines = font.split(text, MAX_WIDTH);
		if (lines.isEmpty()) {
			return;
		}
		int textWidth = 0;
		for (FormattedCharSequence line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int width = textWidth + PADDING * 2;
		int height = lines.size() * LINE + PADDING * 2 - 1;
		int screenWidth = (int) (client.getWindow().getGuiScaledWidth() / scale);
		int screenHeight = (int) (client.getWindow().getGuiScaledHeight() / scale);
		// Above right of the mouse, flipped when it leaves the screen
		int x = mouseX + OFFSET;
		int y = mouseY - height - OFFSET / 2;
		if (x + width > screenWidth - MARGIN) {
			x = mouseX - OFFSET - width;
		}
		if (y < MARGIN) {
			y = mouseY + OFFSET * 2;
		}
		x = Math.max(MARGIN, Math.min(x, screenWidth - MARGIN - width));
		y = Math.max(MARGIN, Math.min(y, screenHeight - MARGIN - height));
		// On top of everything
		graphics.nextStratum();
		graphics.fill(x, y, x + width, y + height, COLOR_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_BACKGROUND);
		int lineY = y + PADDING;
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, x + PADDING, lineY, COLOR_TEXT, true);
			lineY += LINE;
		}
	}
}
