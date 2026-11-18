package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Box on the right of the menu about the hovered setting
final class SettingsInfoBox {
	// Box layout, colors, links, pictures and enlarged picture
	static final int GAP = 6;
	static final int MIN_WIDTH = 150;
	static final int MAX_WIDTH = 220;
	private static final int BORDER = 3;
	private static final int TOP_BAR = 30;
	private static final int PADDING = 10;
	private static final int LINE = 10;
	private static final int PARAGRAPH_GAP = 4;
	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final int COLOR_OUTLINE = 0xFF262626;
	private static final int COLOR_OUTLINE_HEADER = 0xFF383838;
	private static final int COLOR_HEADER = 0xFF232323;
	private static final int COLOR_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HINT = 0xFF8C8C8C;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;
	private static final int COLOR_LINK = 0x3C8CFF;
	private static final float MIN_TEXT_ALPHA = 0.1F;
	private static final float PREVIEW_MILLIS = 200.0F;
	private static final float PREVIEW_FILL = 0.85F;
	private static final int PREVIEW_RISE = 24;
	private static final int PREVIEW_BACKDROP = 0xB0;
	private static final int PREVIEW_BORDER = 3;
	private static final Pattern LINK = Pattern.compile("\\[([^\\[\\]]+)]\\((https?://[^\\s()]+)\\)");
	private static final Pattern IMAGE = Pattern.compile("^\\s*!\\[[^\\]]*]\\(([a-z0-9_.-]{1,64}\\.png)\\)\\s*$");

	private record LinkSpan(int start, int end, URI uri) {
	}

	private record Row(int y, int height, FormattedCharSequence text, List<LinkSpan> links, SettingsInfo.Image image) {
	}

	// Shown card, laid out rows, scroll, fade and enlarged picture
	private final Font font = Minecraft.getInstance().font;
	private SettingsPanel.Card card;
	private Component title = Component.empty();
	private List<Row> rows = List.of();
	private String signature;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private int contentHeight;
	private int maxScroll;
	private float scroll;
	private float scrollTarget;
	private long lastFrame;
	private float alpha = 1.0F;
	private SettingsInfo.Image preview;
	private float previewProgress;
	private boolean previewClosing;
	private long previewFrame;

	void bounds(int left, int top, int right, int bottom) {
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.signature = null;
	}

	void alpha(float alpha) {
		this.alpha = Math.clamp(alpha, 0.0F, 1.0F);
	}

	private int faded(int color) {
		return Math.round((color >>> 24) * this.alpha) << 24 | color & 0xFFFFFF;
	}

	// New card: content changes and scroll goes back to the top
	void show(SettingsPanel.Card card) {
		if (this.card != null && this.card.key().equals(card.key()) && this.card.title().getString().equals(card.title().getString())) {
			return;
		}
		this.card = card;
		this.scroll = 0;
		this.scrollTarget = 0;
		this.signature = null;
	}

	private int viewportTop() {
		return this.top + BORDER + TOP_BAR;
	}

	private int viewportBottom() {
		return this.bottom - BORDER;
	}

	private int textLeft() {
		return this.left + BORDER + PADDING;
	}

	private int textWidth() {
		return this.right - this.left - BORDER * 2 - PADDING * 2;
	}

	void extractBackground(GuiGraphicsExtractor graphics) {
		graphics.fill(this.left, this.top, this.right, this.bottom, faded(COLOR_OUTLINE));
		graphics.fill(this.left, this.top, this.right, viewportTop(), faded(COLOR_OUTLINE_HEADER));
		graphics.fill(this.left + BORDER, this.top, this.right - BORDER, viewportTop(), faded(COLOR_HEADER));
		graphics.fill(this.left + BORDER, viewportTop(), this.right - BORDER, viewportBottom(), faded(COLOR_BACKGROUND));
	}

	// Rows laid out again when the card, width or relay data change
	private void layout() {
		String key = this.card == null ? "" : this.card.key();
		String current = key + "|" + textWidth() + "|" + SettingsInfo.revision();
		if (current.equals(this.signature)) {
			return;
		}
		this.signature = current;
		int width = Math.max(1, textWidth());
		List<Row> built = new ArrayList<>();
		int y = 0;
		if (this.card == null) {
			this.title = Component.translatable("screen.mccinfo.settings_info.title");
			for (FormattedCharSequence line : this.font.split(Component.translatable("screen.mccinfo.settings_info.empty"), width)) {
				built.add(new Row(y, LINE, line, List.of(), null));
				y += LINE;
			}
		} else {
			SettingsInfo.Entry entry = SettingsInfo.entry(key);
			this.title = entry != null && !entry.title().isEmpty() ? Component.literal(entry.title()) : this.card.title();
			List<String> paragraphs = entry != null && !entry.text().isEmpty() ? entry.text() : null;
			if (paragraphs == null) {
				for (FormattedCharSequence line : this.font.split(this.card.description(), width)) {
					built.add(new Row(y, LINE, line, List.of(), null));
					y += LINE;
				}
			} else {
				for (int i = 0; i < paragraphs.size(); i++) {
					String paragraph = paragraphs.get(i);
					if (i > 0) {
						y += PARAGRAPH_GAP;
					}
					Matcher image = IMAGE.matcher(paragraph);
					if (image.matches()) {
						SettingsInfo.Image loaded = SettingsInfo.image(image.group(1));
						if (loaded != null && loaded.width() > 0) {
							int shown = Math.min(width, loaded.width());
							int height = Math.max(1, Math.round(loaded.height() * (float) shown / loaded.width()));
							built.add(new Row(y, height, null, List.of(), loaded));
							y += height;
						}
						continue;
					}
					if (paragraph.isBlank()) {
						y += LINE;
						continue;
					}
					for (FormattedCharSequence line : this.font.split(richText(paragraph), width)) {
						built.add(new Row(y, LINE, line, linkSpans(line), null));
						y += LINE;
					}
				}
			}
		}
		this.rows = built;
		this.contentHeight = y;
		int viewport = viewportBottom() - viewportTop() - PADDING * 2;
		this.maxScroll = Math.max(0, this.contentHeight - viewport);
		this.scrollTarget = Math.clamp(this.scrollTarget, 0, this.maxScroll);
		this.scroll = Math.clamp(this.scroll, 0, this.maxScroll);
	}

	// Markdown links to clickable blue text
	private static Component richText(String text) {
		MutableComponent result = Component.empty();
		Matcher matcher = LINK.matcher(text);
		int last = 0;
		while (matcher.find()) {
			URI uri;
			try {
				uri = URI.create(matcher.group(2));
			} catch (IllegalArgumentException e) {
				continue;
			}
			result.append(text.substring(last, matcher.start()));
			result.append(Component.literal(matcher.group(1)).withStyle(Style.EMPTY.withColor(COLOR_LINK).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(uri))));
			last = matcher.end();
		}
		result.append(text.substring(last));
		return result;
	}

	private List<LinkSpan> linkSpans(FormattedCharSequence line) {
		List<LinkSpan> spans = new ArrayList<>();
		int[] x = {0};
		URI[] current = {null};
		int[] start = {0};
		line.accept((index, style, codePoint) -> {
			URI uri = style.getClickEvent() instanceof ClickEvent.OpenUrl(URI target) ? target : null;
			if (uri != current[0]) {
				if (current[0] != null) {
					spans.add(new LinkSpan(start[0], x[0], current[0]));
				}
				current[0] = uri;
				start[0] = x[0];
			}
			x[0] += this.font.width(FormattedText.of(Character.toString(codePoint), style));
			return true;
		});
		if (current[0] != null) {
			spans.add(new LinkSpan(start[0], x[0], current[0]));
		}
		return spans;
	}

	private URI linkAt(double mouseX, double mouseY) {
		if (mouseY < viewportTop() || mouseY >= viewportBottom()) {
			return null;
		}
		int originY = viewportTop() + PADDING - Math.round(this.scroll);
		for (Row row : this.rows) {
			if (row.links().isEmpty()) {
				continue;
			}
			int y = originY + row.y();
			if (mouseY < y || mouseY >= y + LINE) {
				continue;
			}
			for (LinkSpan span : row.links()) {
				if (mouseX >= textLeft() + span.start() && mouseX < textLeft() + span.end()) {
					return span.uri();
				}
			}
		}
		return null;
	}

	// Title, text, pictures and scrollbar
	void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		layout();
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		if (Math.abs(this.scrollTarget - this.scroll) < 0.5F) {
			this.scroll = this.scrollTarget;
		} else {
			this.scroll += (this.scrollTarget - this.scroll) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
		if (this.alpha < MIN_TEXT_ALPHA) {
			return;
		}
		Component header = this.title.copy().withStyle(ChatFormatting.BOLD);
		int titleWidth = textWidth();
		FormattedText clipped = this.font.width(header) > titleWidth
				? FormattedText.composite(this.font.substrByWidth(header, titleWidth - this.font.width("...")), FormattedText.of("..."))
				: header;
		graphics.text(this.font, net.minecraft.locale.Language.getInstance().getVisualOrder(clipped), textLeft(),
				this.top + BORDER + (TOP_BAR - this.font.lineHeight) / 2 + 1, faded(COLOR_TITLE), true);
		graphics.enableScissor(this.left + BORDER, viewportTop(), this.right - BORDER, viewportBottom());
		int originY = viewportTop() + PADDING - Math.round(this.scroll);
		int color = faded(this.card == null ? COLOR_HINT : COLOR_TEXT);
		for (Row row : this.rows) {
			int y = originY + row.y();
			if (y + row.height() <= viewportTop() || y >= viewportBottom()) {
				continue;
			}
			if (row.image() != null) {
				int width = Math.min(textWidth(), row.image().width());
				drawImage(graphics, row.image(), textLeft(), y, width, row.height(), faded(0xFFFFFFFF));
			} else {
				graphics.text(this.font, row.text(), textLeft(), y, color, true);
			}
		}
		graphics.disableScissor();
		if (this.maxScroll > 0) {
			int trackTop = viewportTop();
			int trackHeight = viewportBottom() - trackTop;
			int thumb = Math.max(MIN_THUMB, trackHeight * trackHeight / (trackHeight + this.maxScroll));
			int thumbY = trackTop + Math.round((trackHeight - thumb) * this.scroll / this.maxScroll);
			int x = this.right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumb, faded(COLOR_SCROLLBAR));
		}
		if (linkAt(mouseX, mouseY) != null || imageAt(mouseX, mouseY) != null) {
			NeuItems.pointer(graphics);
		}
	}

	private static void drawImage(GuiGraphicsExtractor graphics, SettingsInfo.Image image, int x, int y, int width, int height, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, image.location(), x, y, 0.0F, 0.0F, width, height, image.width(), image.height(),
				image.width(), image.height(), color);
	}

	private SettingsInfo.Image imageAt(double mouseX, double mouseY) {
		if (mouseY < viewportTop() || mouseY >= viewportBottom() || mouseX < textLeft()) {
			return null;
		}
		int originY = viewportTop() + PADDING - Math.round(this.scroll);
		for (Row row : this.rows) {
			if (row.image() == null) {
				continue;
			}
			int y = originY + row.y();
			int width = Math.min(textWidth(), row.image().width());
			if (mouseY >= y && mouseY < y + row.height() && mouseX < textLeft() + width) {
				return row.image();
			}
		}
		return null;
	}

	boolean previewOpen() {
		return this.preview != null;
	}

	boolean closePreview() {
		if (this.preview == null || this.previewClosing) {
			return this.preview != null;
		}
		this.previewClosing = true;
		NeuItems.clickSound();
		return true;
	}

	// Enlarged picture: fades in from below over a dark background
	void extractPreview(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
		if (this.preview == null) {
			return;
		}
		long now = Util.getMillis();
		long elapsed = this.previewFrame == 0 ? 0 : now - this.previewFrame;
		this.previewFrame = now;
		float step = elapsed / PREVIEW_MILLIS;
		this.previewProgress = this.previewClosing ? Math.max(0.0F, this.previewProgress - step) : Math.min(1.0F, this.previewProgress + step);
		if (this.previewClosing && this.previewProgress <= 0.0F) {
			this.preview = null;
			return;
		}
		float t = this.previewProgress * this.previewProgress * (3.0F - 2.0F * this.previewProgress);
		SettingsInfo.Image image = this.preview;
		float fit = Math.min(screenWidth * PREVIEW_FILL / image.width(), screenHeight * PREVIEW_FILL / image.height());
		int width = Math.max(1, Math.round(image.width() * fit));
		int height = Math.max(1, Math.round(image.height() * fit));
		int x = (screenWidth - width) / 2;
		int y = (screenHeight - height) / 2 + Math.round((1.0F - t) * PREVIEW_RISE);
		graphics.nextStratum();
		graphics.fill(0, 0, screenWidth, screenHeight, Math.round(PREVIEW_BACKDROP * t) << 24);
		int alpha = Math.round(255 * t);
		graphics.fill(x - PREVIEW_BORDER, y - PREVIEW_BORDER, x + width + PREVIEW_BORDER, y + height + PREVIEW_BORDER, alpha << 24 | COLOR_OUTLINE & 0xFFFFFF);
		drawImage(graphics, image, x, y, width, height, alpha << 24 | 0xFFFFFF);
		NeuItems.pointer(graphics);
	}

	boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.left && mouseX < this.right && mouseY >= this.top && mouseY < this.bottom;
	}

	// Picture opens in big, link opens after confirmation
	boolean click(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		SettingsInfo.Image image = imageAt(mouseX, mouseY);
		if (image != null) {
			NeuItems.clickSound();
			this.preview = image;
			this.previewProgress = 0.0F;
			this.previewClosing = false;
			this.previewFrame = 0;
			return true;
		}
		URI uri = linkAt(mouseX, mouseY);
		if (uri != null) {
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			if (scheme.equals("http") || scheme.equals("https")) {
				NeuItems.clickSound();
				Minecraft client = Minecraft.getInstance();
				ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), uri);
			}
		}
		return true;
	}

	boolean scroll(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		this.scrollTarget = Math.clamp(this.scrollTarget - (float) amount * SCROLL_STEP, 0, this.maxScroll);
		return true;
	}
}
