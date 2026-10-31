package leroidesafk.mccinfo.client.patchnotes;

import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Scrollable list of every patch note, drawn by PatchNoteScreen
public final class PatchNotePanel {
	private static final int BORDER = 3;
	private static final int PADDING = 20;
	private static final int MARGIN = 24;
	private static final int PANEL_WIDTH = 460;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int VERSION_GAP = 18;
	private static final int SECTION_GAP = 7;
	private static final int BULLET_INDENT = 12;

	private static final int LINE = 9;
	private static final int META_HEIGHT = LINE + 4;
	private static final int VERSION_HEIGHT = 16;
	private static final Pattern DATE = Pattern.compile("([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})");
	private static final int SECTION_HEIGHT = LINE + 4;
	private static final int BODY_HEIGHT = LINE + 2;

	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_META_TEXT = 0xFF8C8C8C;
	private static final int COLOR_SECTION_TEXT = 0xFFFFFFFF;
	private static final int COLOR_BODY_TEXT = 0xFFBDBDBD;
	private static final int COLOR_BULLET = 0xFF5A5A5A;
	private static final int COLOR_LINK = 0x3C8CFF;
	// Markdown link: [text](https://...) (note to myself, add other dicord Markdown style)
	private static final Pattern LINK = Pattern.compile("\\[([^\\[\\]]+)]\\((https?://[^\\s()]+)\\)");

	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final int SCROLLBAR_GRAB = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_WHEEL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final int COLOR_SCROLLBAR_THUMB = 0xFF5A5A5A;

	// Each line of the panel is laid out once, then only drawn
	private enum Kind {
		HEADER, META, VERSION, SECTION, BULLET, CONTINUATION
	}

	private record Row(Kind kind, FormattedCharSequence text, int y, List<LinkSpan> links, FormattedCharSequence extra) {
		Row(Kind kind, FormattedCharSequence text, int y) {
			this(kind, text, y, List.of(), null);
		}

		Row(Kind kind, FormattedCharSequence text, int y, List<LinkSpan> links) {
			this(kind, text, y, links, null);
		}
	}

	private record LinkSpan(int start, int end, URI uri) {
	}

	private final List<PatchNotes.Note> notes;
	private Font font;
	private int screenWidth = -1;
	private int screenHeight = -1;
	private float scale = -1.0F;
	private List<Row> rows = List.of();
	private int panelWidth;
	private int contentWidth;
	private int contentHeight;

	private int left;
	private int top;
	private int panelHeight;
	private float scrollOffset;
	private float scrollTarget;
	private long lastFrame;
	private int maxScroll;
	private int viewportTop;
	private int viewportBottom;
	private boolean draggingScrollbar;
	private double dragOffset;

	public PatchNotePanel(List<PatchNotes.Note> notes) {
		this.notes = notes;
	}

	// Rebuilt only when the font, screen size or menu scale changes
	void layout(Font font, int width, int height) {
		float scale = SettingsPanel.uiScale();
		if (font == this.font && width == this.screenWidth && height == this.screenHeight && scale == this.scale) {
			return;
		}
		this.font = font;
		this.screenWidth = width;
		this.screenHeight = height;
		this.scale = scale;
		int virtualWidth = (int) (width / scale);
		int virtualHeight = (int) (height / scale);
		this.panelWidth = Math.min(PANEL_WIDTH, virtualWidth - MARGIN * 2);
		this.contentWidth = this.panelWidth - BORDER * 2 - PADDING * 2;
		List<Row> built = new ArrayList<>();
		int y = 0;
		if (this.notes.isEmpty()) {
			built.add(new Row(Kind.META, Component.translatable("screen.mccinfo.patchnotes.empty").getVisualOrderText(), y));
			y += META_HEIGHT;
		}
		int bodyWidth = this.contentWidth - BULLET_INDENT;
		for (PatchNotes.Note note : this.notes) {
			if (y > 0) {
				y += VERSION_GAP;
			}
			Component title = note.title().isEmpty() ? Component.translatable("screen.mccinfo.patchnotes.version", note.version()) : Component.literal(note.title());
			built.add(new Row(Kind.HEADER, title.getVisualOrderText(), y));
			y += HEADER_HEIGHT;
			FormattedCharSequence date = note.date().isEmpty() ? null : Component.literal(formatDate(note.date())).getVisualOrderText();
			built.add(new Row(Kind.VERSION, Component.literal(note.title().isEmpty() ? "" : note.version()).getVisualOrderText(), y, List.of(), date));
			y += VERSION_HEIGHT;
			for (PatchNotes.Section section : note.sections()) {
				if (!section.name().isEmpty()) {
					y += SECTION_GAP;
					built.add(new Row(Kind.SECTION, Component.literal(section.name()).getVisualOrderText(), y));
					y += SECTION_HEIGHT;
				}
				for (String change : section.changes()) {
					boolean first = true;
					for (FormattedCharSequence line : font.split(richText(change), bodyWidth)) {
						built.add(new Row(first ? Kind.BULLET : Kind.CONTINUATION, line, y, linkSpans(font, line)));
						y += BODY_HEIGHT;
						first = false;
					}
				}
			}
		}
		this.rows = built;
		this.contentHeight = y;
		int viewportHeight = Math.max(HEADER_HEIGHT, Math.min(this.contentHeight, virtualHeight - MARGIN * 2 - BORDER * 2 - PADDING * 2));
		this.panelHeight = viewportHeight + BORDER * 2 + PADDING * 2;
		this.left = (virtualWidth - this.panelWidth) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.viewportTop = this.top + BORDER + PADDING;
		this.viewportBottom = this.viewportTop + viewportHeight;
		this.maxScroll = Math.max(0, this.contentHeight - viewportHeight);
		this.scrollOffset = Mth.clamp(this.scrollOffset, 0.0F, this.maxScroll);
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll);
	}

	// "10/30/2026" -> "Oct 30, 2026"
	static String formatDate(String date) {
		Matcher matcher = DATE.matcher(date.trim());
		if (!matcher.matches()) {
			return date;
		}
		int month = Integer.parseInt(matcher.group(1));
		if (month < 1 || month > 12) {
			return date;
		}
		String name = java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
		return name + " " + Integer.parseInt(matcher.group(2)) + ", " + matcher.group(3);
	}

	// Turns the Markdown links into clickable blue text
	static Component richText(String text) {
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

	// Where the links are on a wrapped line, for the clicks
	private static List<LinkSpan> linkSpans(Font font, FormattedCharSequence line) {
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
			x[0] += font.width(FormattedText.of(Character.toString(codePoint), style));
			return true;
		});
		if (current[0] != null) {
			spans.add(new LinkSpan(start[0], x[0], current[0]));
		}
		return spans;
	}

	private URI linkAt(double screenX, double screenY) {
		if (this.font == null) {
			return null;
		}
		double virtualX = screenX / this.scale;
		double virtualY = screenY / this.scale;
		if (virtualY < this.viewportTop || virtualY >= this.viewportBottom) {
			return null;
		}
		int textLeft = this.left + BORDER + PADDING + BULLET_INDENT;
		int scroll = Math.round(this.scrollOffset);
		for (Row row : this.rows) {
			if (row.links().isEmpty()) {
				continue;
			}
			int y = this.viewportTop + row.y() - scroll;
			if (virtualY < y || virtualY >= y + LINE) {
				continue;
			}
			for (LinkSpan span : row.links()) {
				if (virtualX >= textLeft + span.start() && virtualX < textLeft + span.end()) {
					return span.uri();
				}
			}
		}
		return null;
	}

	// Links open the usual Minecraft confirmation first
	boolean click(double x, double y) {
		URI uri = linkAt(x, y);
		if (uri == null) {
			return false;
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (scheme.equals("http") || scheme.equals("https")) {
			NeuItems.clickSound();
			Minecraft client = Minecraft.getInstance();
			ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), uri);
		}
		return true;
	}

	boolean contains(double x, double y) {
		double virtualX = x / this.scale;
		double virtualY = y / this.scale;
		return virtualX >= this.left && virtualX < this.left + this.panelWidth && virtualY >= this.top && virtualY < this.top + this.panelHeight;
	}

	void extract(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
		if (linkAt(mouseX, mouseY) != null) {
			NeuItems.pointer(graphics);
		}
		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		int right = this.left + this.panelWidth;
		graphics.fill(this.left, this.top, right, this.top + this.panelHeight, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, this.top + this.panelHeight - BORDER, COLOR_PANEL_BACKGROUND);

		int contentLeft = this.left + BORDER + PADDING;
		updateScroll();
		int scroll = Math.round(this.scrollOffset);
		graphics.enableScissor(this.left + BORDER, this.viewportTop, right - BORDER, this.viewportBottom);
		for (Row row : this.rows) {
			int y = this.viewportTop + row.y() - scroll;
			if (y + HEADER_HEIGHT <= this.viewportTop || y >= this.viewportBottom) {
				continue;
			}
			switch (row.kind()) {
				case HEADER -> extractHeader(graphics, row.text(), contentLeft, y);
				case META -> graphics.text(this.font, row.text(), contentLeft + (this.contentWidth - this.font.width(row.text())) / 2, y, COLOR_META_TEXT, true);
				case VERSION -> {
					graphics.text(this.font, row.text(), contentLeft + (this.contentWidth - this.font.width(row.text())) / 2, y + (VERSION_HEIGHT - LINE) / 2 + 1, COLOR_META_TEXT, true);
					if (row.extra() != null) {
						graphics.text(this.font, row.extra(), contentLeft + this.contentWidth - this.font.width(row.extra()), y + (VERSION_HEIGHT - LINE) / 2 + 1, COLOR_META_TEXT, true);
					}
				}
				case SECTION -> graphics.text(this.font, row.text(), contentLeft, y, COLOR_SECTION_TEXT, true);
				case BULLET -> {
					int bulletY = y + 3;
					graphics.fill(contentLeft + 3, bulletY, contentLeft + 5, bulletY + 2, COLOR_BULLET);
					graphics.text(this.font, row.text(), contentLeft + BULLET_INDENT, y, COLOR_BODY_TEXT, true);
				}
				case CONTINUATION -> graphics.text(this.font, row.text(), contentLeft + BULLET_INDENT, y, COLOR_BODY_TEXT, true);
			}
		}
		graphics.disableScissor();
		if (this.maxScroll > 0) {
			int x = right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			int thumbY = thumbY();
			graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR_THUMB);
		}
		graphics.pose().popMatrix();
	}

	private void extractHeader(GuiGraphicsExtractor graphics, FormattedCharSequence label, int contentLeft, int y) {
		int textWidth = this.font.width(label);
		int lineLength = Math.max(0, (this.contentWidth - textWidth - HEADER_LABEL_GAP * 2) / 2);
		int textX = contentLeft + lineLength + HEADER_LABEL_GAP;
		int textY = y + (HEADER_HEIGHT - LINE) / 2 + 1;
		int lineY = textY + LINE / 2 - 1;
		if (lineLength > 0) {
			graphics.fill(contentLeft, lineY, contentLeft + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(contentLeft + this.contentWidth - lineLength, lineY, contentLeft + this.contentWidth, lineY + 1, COLOR_HEADER_LINE);
		}
		graphics.text(this.font, label, textX, textY, COLOR_HEADER_TEXT, true);
	}

	// Smooth scrolling
	private void updateScroll() {
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		if (this.draggingScrollbar || Math.abs(this.scrollTarget - this.scrollOffset) < 0.5F) {
			this.scrollOffset = this.scrollTarget;
		} else {
			this.scrollOffset += (this.scrollTarget - this.scrollOffset) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
	}

	private int trackTop() {
		return this.top + BORDER;
	}

	private int trackHeight() {
		return this.panelHeight - BORDER * 2;
	}

	private int thumbHeight() {
		int trackHeight = trackHeight();
		return Math.max(MIN_THUMB, trackHeight * trackHeight / (trackHeight + this.maxScroll));
	}

	private int thumbY() {
		return trackTop() + Math.round((trackHeight() - thumbHeight()) * this.scrollOffset / this.maxScroll);
	}

	void scroll(double amount) {
		this.scrollTarget = Mth.clamp(this.scrollTarget - (float) amount * SCROLL_WHEEL_STEP, 0.0F, this.maxScroll);
	}

	// Scrollbar drag, also from a click on the track
	boolean startDrag(double x, double y) {
		double virtualX = x / this.scale;
		double virtualY = y / this.scale;
		int right = this.left + this.panelWidth;
		if (this.maxScroll <= 0 || virtualX < right - BORDER - SCROLLBAR_GRAB || virtualX >= right
				|| virtualY < trackTop() || virtualY >= trackTop() + trackHeight()) {
			return false;
		}
		int thumbY = thumbY();
		int thumbHeight = thumbHeight();
		this.dragOffset = virtualY >= thumbY && virtualY < thumbY + thumbHeight ? virtualY - thumbY : thumbHeight / 2.0;
		this.draggingScrollbar = true;
		drag(y);
		return true;
	}

	boolean drag(double y) {
		if (!this.draggingScrollbar) {
			return false;
		}
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			this.scrollTarget = Mth.clamp((float) ((y / this.scale - this.dragOffset - trackTop()) * this.maxScroll / range), 0.0F, this.maxScroll);
			this.scrollOffset = this.scrollTarget;
		}
		return true;
	}

	boolean stopDrag() {
		boolean was = this.draggingScrollbar;
		this.draggingScrollbar = false;
		return was;
	}
}
