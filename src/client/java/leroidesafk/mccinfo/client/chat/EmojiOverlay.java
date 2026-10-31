package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.MovableOverlay;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EmojiOverlay {
	private static final int BORDER = 2;
	private static final int HEADER_HEIGHT = 14;
	private static final int PADDING = 3;
	private static final int CELL = 12;
	private static final int DEFAULT_WIDTH = 132;
	private static final int DEFAULT_HEIGHT = 96;
	private static final int MIN_WIDTH = 60;
	private static final int MIN_HEIGHT = 50;
	private static final int MIN_SCALE = 1;
	private static final int MAX_SCALE = 3;
	private static final int MIN_THUMB = 6;
	private static final float SCROLL_STEP = 12.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final int DEFAULT_X_MARGIN = 4;
	private static final int DEFAULT_Y_MARGIN = 16;
	private static final int TITLE_PADDING = 6;
	private static final int COLOR_OUTLINE = 0xFF262626;
	private static final int COLOR_OUTLINE_HEADER = 0xFF383838;
	private static final int COLOR_HEADER = 0xFF232323;
	private static final int COLOR_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HOVER = 0xFF3A3A3A;
	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;
	private static final int COLOR_ALIAS = 0xFEE761;
	private static final String BULLET = "▪ ";

	private static float scroll;
	private static float scrollTarget;
	private static long lastFrame;
	private static boolean draggingScrollbar;
	private static double dragOffset;
	private static Emojis.Emoji hovered;

	public static final MovableOverlay MOVABLE = new MovableOverlay() {
		@Override
		public boolean enabled() {
			return Emojis.active();
		}

		@Override
		public boolean visible() {
			return enabled() && !hidden() && Minecraft.getInstance().gui.screen() instanceof ChatScreen;
		}

		@Override
		public int width() {
			int width = ModConfig.get().emojiWidth;
			return width == ModConfig.UNSET ? DEFAULT_WIDTH : Math.max(MIN_WIDTH, width);
		}

		@Override
		public int height() {
			int height = ModConfig.get().emojiHeight;
			return height == ModConfig.UNSET ? DEFAULT_HEIGHT : Math.max(MIN_HEIGHT, height);
		}

		@Override
		public boolean resizable() {
			return true;
		}

		@Override
		public void setSize(int width, int height) {
			ModConfig.get().emojiWidth = width == ModConfig.UNSET ? ModConfig.UNSET : Math.max(MIN_WIDTH, width);
			ModConfig.get().emojiHeight = height == ModConfig.UNSET ? ModConfig.UNSET : Math.max(MIN_HEIGHT, height);
		}

		@Override
		public boolean textScalable() {
			return true;
		}

		@Override
		public void changeTextScale(int steps) {
			ModConfig.get().emojiScale = Math.clamp(scale() + steps, MIN_SCALE, MAX_SCALE);
		}

		@Override
		public void resetTextScale() {
			ModConfig.get().emojiScale = ModConfig.UNSET;
		}

		@Override
		public int x(int screenWidth) {
			int x = ModConfig.get().emojiX;
			return OverlayStyle.clamp(x == ModConfig.UNSET ? screenWidth - width() - DEFAULT_X_MARGIN : x, screenWidth - width());
		}

		@Override
		public int y(int screenHeight) {
			int y = ModConfig.get().emojiY;
			return OverlayStyle.clamp(y == ModConfig.UNSET ? screenHeight - height() - DEFAULT_Y_MARGIN : y, screenHeight - height());
		}

		@Override
		public void setPosition(int x, int y) {
			ModConfig.get().emojiX = x;
			ModConfig.get().emojiY = y;
		}

		@Override
		public boolean hidden() {
			return ModConfig.get().emojiHidden;
		}

		@Override
		public void setHidden(boolean hidden) {
			ModConfig.get().emojiHidden = hidden;
		}

		@Override
		public void extractPreview(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
			extract(graphics, x, y, Integer.MIN_VALUE, Integer.MIN_VALUE, alpha);
		}
	};

	private EmojiOverlay() {
	}

	public static void register() {
		OverlayStyle.registerHud(Mccinfo.id("emojis"), EmojiOverlay::extractHud);
	}

	private static int scale() {
		int scale = ModConfig.get().emojiScale;
		return scale == ModConfig.UNSET ? MIN_SCALE : Math.clamp(scale, MIN_SCALE, MAX_SCALE);
	}

	private static int cell() {
		return CELL * scale();
	}

	private static int columns() {
		return Math.max(1, (MOVABLE.width() - BORDER * 2 - PADDING * 2) / cell());
	}

	private static int contentTop(int y) {
		return y + BORDER + HEADER_HEIGHT;
	}

	private static int viewHeight() {
		return MOVABLE.height() - BORDER * 2 - HEADER_HEIGHT;
	}

	private static int maxScroll() {
		int rows = (Emojis.all().size() + columns() - 1) / columns();
		return Math.max(0, rows * cell() + PADDING * 2 - viewHeight());
	}

	private static int trackHeight() {
		return MOVABLE.height() - BORDER - HEADER_HEIGHT - BORDER;
	}

	private static int thumbHeight() {
		int track = trackHeight();
		return Math.max(MIN_THUMB, track * track / (track + maxScroll()));
	}

	private static int thumbY(int y) {
		int max = maxScroll();
		return contentTop(y) + (max == 0 ? 0 : Math.round((trackHeight() - thumbHeight()) * scroll / max));
	}

	private static double mouseX(Minecraft client) {
		return client.mouseHandler.getScaledXPos(client.getWindow());
	}

	private static double mouseY(Minecraft client) {
		return client.mouseHandler.getScaledYPos(client.getWindow());
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		hovered = null;
		if (!MOVABLE.visible()) {
			draggingScrollbar = false;
			return;
		}
		int x = MOVABLE.x(graphics.guiWidth());
		int y = MOVABLE.y(graphics.guiHeight());
		double mouseX = mouseX(client);
		double mouseY = mouseY(client);
		// Scrollbar drag
		if (draggingScrollbar) {
			if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
				dragScrollbar(y, mouseY);
			} else {
				draggingScrollbar = false;
			}
		}
		updateScroll();
		extract(graphics, x, y, (int) mouseX, (int) mouseY, 1.0F);
	}

	private static void updateScroll() {
		long now = Util.getMillis();
		long elapsed = lastFrame == 0 ? 0 : now - lastFrame;
		lastFrame = now;
		scrollTarget = Mth.clamp(scrollTarget, 0.0F, maxScroll());
		if (draggingScrollbar || Math.abs(scrollTarget - scroll) < 0.5F) {
			scroll = scrollTarget;
		} else {
			scroll += (scrollTarget - scroll) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
		scroll = Mth.clamp(scroll, 0.0F, maxScroll());
	}

	private static void extract(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, float alpha) {
		Minecraft client = Minecraft.getInstance();
		int width = MOVABLE.width();
		int height = MOVABLE.height();
		int right = x + width;
		int bottom = y + height;
		int top = contentTop(y);
		// Frame and title
		graphics.fill(x, y, right, bottom, OverlayStyle.withAlpha(COLOR_OUTLINE, alpha));
		graphics.fill(x, y, right, top, OverlayStyle.withAlpha(COLOR_OUTLINE_HEADER, alpha));
		graphics.fill(x + BORDER, y, right - BORDER, top, OverlayStyle.withAlpha(COLOR_HEADER, alpha));
		graphics.fill(x + BORDER, top, right - BORDER, bottom - BORDER, OverlayStyle.withAlpha(COLOR_BACKGROUND, alpha));
		Component title = Component.translatable("screen.mccinfo.emojis.title");
		graphics.text(client.font, title, x + BORDER + TITLE_PADDING, y + BORDER + (HEADER_HEIGHT - client.font.lineHeight) / 2 + 1,
				OverlayStyle.withAlpha(COLOR_TITLE, alpha), true);

		// Emoji grid
		int cell = cell();
		int columns = columns();
		int gridLeft = x + BORDER + PADDING;
		int viewBottom = bottom - BORDER;
		boolean inside = mouseX >= x + BORDER && mouseX < right - BORDER && mouseY >= top && mouseY < viewBottom;
		graphics.enableScissor(x + BORDER, top, right - BORDER, viewBottom);
		List<Emojis.Emoji> list = Emojis.all();
		int scrollPixels = Math.round(scroll);
		for (int index = 0; index < list.size(); index++) {
			int cellX = gridLeft + index % columns * cell;
			int cellY = top + PADDING + index / columns * cell - scrollPixels;
			if (cellY + cell <= top || cellY >= viewBottom) {
				continue;
			}
			Emojis.Emoji emoji = list.get(index);
			boolean over = inside && mouseX >= cellX && mouseX < cellX + cell && mouseY >= cellY && mouseY < cellY + cell;
			if (over) {
				hovered = emoji;
				graphics.fill(cellX, cellY, cellX + cell, cellY + cell, OverlayStyle.withAlpha(COLOR_HOVER, alpha));
			}
			Component icon = emoji.icon();
			graphics.pose().pushMatrix();
			graphics.pose().translate(cellX + cell / 2.0F, cellY + cell / 2.0F);
			graphics.pose().scale(scale(), scale());
			graphics.text(client.font, icon, -client.font.width(icon) / 2, -client.font.lineHeight / 2 + 1, OverlayStyle.withAlpha(0xFFFFFFFF, alpha), false);
			graphics.pose().popMatrix();
		}
		graphics.disableScissor();
		// Scrollbar
		if (maxScroll() > 0) {
			int thumbY = thumbY(y);
			graphics.fill(right - BORDER, thumbY, right, thumbY + thumbHeight(), OverlayStyle.withAlpha(COLOR_SCROLLBAR, alpha));
		}
		if (hovered != null) {
			NeuItems.pointer(graphics);
		}
	}

	public static void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (hovered == null || !MOVABLE.visible()) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		for (String line : hovered.lines()) {
			lines.add(Component.literal(BULLET).append(Component.literal(line).withColor(COLOR_ALIAS)));
		}
		graphics.setTooltipForNextFrame(Minecraft.getInstance().font, lines, Optional.empty(), mouseX, mouseY);
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !MOVABLE.visible()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		int x = MOVABLE.x(client.getWindow().getGuiScaledWidth());
		int y = MOVABLE.y(client.getWindow().getGuiScaledHeight());
		int right = x + MOVABLE.width();
		int bottom = y + MOVABLE.height();
		double mouseX = event.x();
		double mouseY = event.y();
		if (mouseX < x || mouseX >= right || mouseY < contentTop(y) || mouseY >= bottom) {
			return false;
		}
		// Click on the scrollbar
		if (maxScroll() > 0 && mouseX >= right - BORDER && mouseY < bottom - BORDER) {
			int thumbY = thumbY(y);
			dragOffset = mouseY >= thumbY && mouseY < thumbY + thumbHeight() ? mouseY - thumbY : thumbHeight() / 2.0;
			draggingScrollbar = true;
			dragScrollbar(y, mouseY);
			return true;
		}
		// Click on an emoji
		Emojis.Emoji emoji = emojiAt(x, y, mouseX, mouseY);
		if (emoji != null) {
			NeuItems.clickSound();
			Emojis.insert(emoji);
		}
		return true;
	}

	private static Emojis.Emoji emojiAt(int x, int y, double mouseX, double mouseY) {
		int cell = cell();
		int columns = columns();
		int top = contentTop(y);
		if (mouseY < top || mouseY >= y + MOVABLE.height() - BORDER) {
			return null;
		}
		int column = (int) Math.floor((mouseX - x - BORDER - PADDING) / cell);
		int row = (int) Math.floor((mouseY - top - PADDING + scroll) / cell);
		if (column < 0 || column >= columns || row < 0) {
			return null;
		}
		int index = row * columns + column;
		return index < Emojis.all().size() ? Emojis.all().get(index) : null;
	}

	private static void dragScrollbar(int y, double mouseY) {
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			scrollTarget = Mth.clamp((float) ((mouseY - dragOffset - contentTop(y)) * maxScroll() / range), 0.0F, maxScroll());
			scroll = scrollTarget;
		}
	}

	public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (scrollY == 0 || !MOVABLE.visible()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		int x = MOVABLE.x(client.getWindow().getGuiScaledWidth());
		int y = MOVABLE.y(client.getWindow().getGuiScaledHeight());
		if (mouseX < x || mouseX >= x + MOVABLE.width() || mouseY < y || mouseY >= y + MOVABLE.height()) {
			return false;
		}
		scrollTarget = Mth.clamp(scrollTarget - (float) scrollY * SCROLL_STEP * scale(), 0.0F, maxScroll());
		return true;
	}
}
