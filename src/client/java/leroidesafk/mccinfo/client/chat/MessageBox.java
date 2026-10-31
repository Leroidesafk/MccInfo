package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.EventsScreen;
import leroidesafk.mccinfo.client.gui.MovableOverlay;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public abstract class MessageBox implements MovableOverlay {
	private static final int LINE_HEIGHT = 9;
	private static final int FRAME_HEIGHT = OverlayStyle.TITLE_HEIGHT + OverlayStyle.PADDING * 2;
	private static final int DEFAULT_WIDTH = 240;
	private static final int DEFAULT_HEIGHT = FRAME_HEIGHT + 8 * LINE_HEIGHT;
	private static final int MIN_WIDTH = 100;
	private static final int MIN_HEIGHT = FRAME_HEIGHT + 2 * LINE_HEIGHT;
	private static final int MAX_MESSAGES = 100;
	private static final int MIN_TEXT_PIXELS = 1;
	private static final int MAX_TEXT_PIXELS = 8;
	private static final int SCROLL_LINES = 3;

	private final String title;
	private final int titleColor;
	private final OverlayStyle.Icon icon;
	private final List<Component> preview;
	private final int defaultX;
	private final int defaultY;
	private final List<Component> messages = new ArrayList<>();
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private int linesWidth = ModConfig.UNSET;
	private int scroll;

	public MessageBox(String title, int titleColor, OverlayStyle.Icon icon, List<Component> preview, int defaultX, int defaultY) {
		this.title = title;
		this.titleColor = titleColor;
		this.icon = icon;
		this.preview = preview;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	// Config fields of each box
	public abstract int storedX();

	public abstract int storedY();

	public abstract int storedWidth();

	public abstract int storedHeight();

	public abstract int storedTextPixels();

	public abstract void storePosition(int x, int y);

	public abstract void storeSize(int width, int height);

	public abstract void storeTextPixels(int pixels);

	public boolean allowed() {
		return true;
	}

	@Override
	public boolean visible() {
		return enabled() && !hidden() && allowed() && !this.messages.isEmpty();
	}

	@Override
	public int width() {
		int width = storedWidth();
		return width == ModConfig.UNSET ? DEFAULT_WIDTH : Math.max(MIN_WIDTH, width);
	}

	@Override
	public int height() {
		int height = storedHeight();
		return height == ModConfig.UNSET ? DEFAULT_HEIGHT : Math.max(MIN_HEIGHT, height);
	}

	@Override
	public boolean resizable() {
		return true;
	}

	@Override
	public boolean textScalable() {
		return true;
	}

	@Override
	public void changeTextScale(int steps) {
		storeTextPixels(Math.clamp(textPixels() + steps, MIN_TEXT_PIXELS, MAX_TEXT_PIXELS));
	}

	@Override
	public void resetTextScale() {
		storeTextPixels(ModConfig.UNSET);
	}

	@Override
	public void setSize(int width, int height) {
		storeSize(width == ModConfig.UNSET ? ModConfig.UNSET : Math.max(MIN_WIDTH, width), height == ModConfig.UNSET ? ModConfig.UNSET : Math.max(MIN_HEIGHT, height));
	}

	@Override
	public int x(int screenWidth) {
		int x = storedX();
		return OverlayStyle.clamp(x == ModConfig.UNSET ? this.defaultX : x, screenWidth - width());
	}

	@Override
	public int y(int screenHeight) {
		int y = storedY();
		return OverlayStyle.clamp(y == ModConfig.UNSET ? this.defaultY : y, screenHeight - height());
	}

	@Override
	public void setPosition(int x, int y) {
		storePosition(x, y);
	}

	@Override
	public void extractPreview(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
		// Sample lines while empty
		if (this.messages.isEmpty()) {
			extract(graphics, x, y, this.preview.stream().map(Component::getVisualOrderText).toList(), 0, alpha);
		} else {
			extract(graphics, x, y, lines(), this.scroll, alpha);
		}
	}

	public boolean replace(Component old, Component updated) {
		for (int index = this.messages.size() - 1; index >= 0; index--) {
			if (this.messages.get(index) == old) {
				this.messages.set(index, updated);
				this.linesWidth = ModConfig.UNSET;
				return true;
			}
		}
		return false;
	}

	protected String title() {
		return this.title;
	}

	protected int titleColor() {
		return this.titleColor;
	}

	protected OverlayStyle.Icon icon() {
		return this.icon;
	}

	public void add(Component message) {
		this.messages.add(message);
		// Drop the oldest, or split only the new one
		if (this.messages.size() > MAX_MESSAGES) {
			this.messages.removeFirst();
			this.linesWidth = ModConfig.UNSET;
		} else if (this.linesWidth == textWidth()) {
			List<FormattedCharSequence> split = split(message);
			this.lines.addAll(split);
			// Stay on the same lines when scrolled up
			if (this.scroll > 0) {
				this.scroll += split.size();
			}
		}
	}

	public void remove(Component message) {
		if (this.messages.removeIf(existing -> existing == message)) {
			this.linesWidth = ModConfig.UNSET;
		}
	}

	public void clear() {
		this.messages.clear();
		this.lines.clear();
		this.linesWidth = ModConfig.UNSET;
		this.scroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		Minecraft client = Minecraft.getInstance();
		int x = x(client.getWindow().getGuiScaledWidth());
		int y = y(client.getWindow().getGuiScaledHeight());
		if (!visible() || mouseX < x || mouseX >= x + width() || mouseY < y || mouseY >= y + height()) {
			return false;
		}
		lines();
		this.scroll = Math.clamp(this.scroll + (int) Math.signum(scrollY) * SCROLL_LINES, 0, maxScroll());
		return true;
	}

	public void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!visible() || EventsScreen.editingOverlays()) {
			return;
		}
		extract(graphics, x(graphics.guiWidth()), y(graphics.guiHeight()), lines(), this.scroll, 1.0F);
	}

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	private int textPixels() {
		int pixels = storedTextPixels();
		return Math.clamp(pixels == ModConfig.UNSET ? guiScale() : pixels, MIN_TEXT_PIXELS, MAX_TEXT_PIXELS);
	}

	private float textScale() {
		return textPixels() / (float) guiScale();
	}

	private int textWidth() {
		return (int) ((width() - OverlayStyle.PADDING * 2) / textScale());
	}

	private List<FormattedCharSequence> split(Component message) {
		return Minecraft.getInstance().font.split(message, textWidth());
	}

	private List<FormattedCharSequence> lines() {
		// Split again when the width changes
		if (this.linesWidth != textWidth()) {
			this.linesWidth = textWidth();
			this.lines.clear();
			for (Component message : this.messages) {
				this.lines.addAll(split(message));
			}
		}
		this.scroll = Math.clamp(this.scroll, 0, maxScroll());
		return this.lines;
	}

	private int visibleLines() {
		return Math.max(1, (int) ((height() - FRAME_HEIGHT) / (LINE_HEIGHT * textScale())));
	}

	private int maxScroll() {
		return Math.max(0, this.lines.size() - visibleLines());
	}

	private void extract(GuiGraphicsExtractor graphics, int x, int y, List<FormattedCharSequence> shown, int scroll, float alpha) {
		Minecraft client = Minecraft.getInstance();
		// Tooltips only with the chat open
		GuiGraphicsExtractor.HoveredTextEffects effects = client.gui.screen() instanceof ChatScreen
				? GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR : GuiGraphicsExtractor.HoveredTextEffects.NONE;
		int height = height();
		OverlayStyle.extractFrame(graphics, x, y, width(), height, titleColor(), icon(), title(), alpha);

		// Visible lines, from the bottom
		int last = shown.size() - scroll;
		int first = Math.max(0, last - visibleLines());
		float scale = textScale();
		float lineHeight = LINE_HEIGHT * scale;
		float lineY = y + height - OverlayStyle.PADDING - (last - first) * lineHeight;
		for (int i = first; i < last; i++) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + OverlayStyle.PADDING, lineY);
			graphics.pose().scale(scale, scale);
			ActiveTextCollector text = graphics.textRenderer(effects);
			text.accept(TextAlignment.LEFT, 0, 0, text.defaultParameters().withOpacity(alpha), shown.get(i));
			ModChat.extractItems(graphics, shown.get(i), 0, 0, alpha);
			graphics.pose().popMatrix();
			lineY += lineHeight;
		}
	}
}
