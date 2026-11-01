package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class SettingsPanel {
	// Menu scale
	private static final int MIN_SCREEN_WIDTH = 480;
	private static final int MIN_SCREEN_HEIGHT = 300;
	private static final float BASE_SCALE = 2.0F / 3.0F;
	static final int MAX_WIDTH = 600;
	static final int MAX_HEIGHT = 408;
	private static final int BORDER = 3;
	private static final int DIVIDER = 3;
	private static final int TOP_BAR = 30;
	private static final int MIN_SIDEBAR = 110;
	private static final int MAX_SIDEBAR = 172;
	private static final int SIDEBAR_PADDING = 10;
	private static final int SIDEBAR_LINE = 15;
	private static final int SIDEBAR_GAP = 6;
	private static final int CONTENT_PADDING = 10;
	private static final int CARD_PADDING = 13;
	private static final int CONTROL_MARGIN = 22;
	private static final int CARD_GAP = 8;
	private static final int CHILD_INDENT = 18;
	private static final int ARROW_HEAD = 3;
	private static final int TITLE_GAP = 4;
	private static final int TEXT_LINE = 10;
	private static final int SECTION_HEIGHT = 22;
	private static final int SECTION_LABEL_GAP = 7;
	private static final int CONTROL_HEIGHT = 16;
	private static final int CONTROL_GAP = 4;
	private static final int BUTTON_PADDING = 8;
	private static final int ICON_BUTTON_WIDTH = 20;
	private static final int ICON_BUTTON_SPRITE = 16;
	private static final int ICON_BUTTON_HEIGHT = 24;
	private static final int ICON_BUTTON_SHEET = 32;
	private static final int ICON_BUTTON_INACTIVE = 0x60FFFFFF;
	private static final int ROW_GAP = 7;
	private static final int CHIP_HEIGHT = 14;
	private static final int CHIP_GAP = 4;
	private static final int CHIP_PADDING = 5;
	private static final int CHOICE_HEIGHT = 14;
	private static final int CHOICE_PADDING = 6;
	private static final float BELOW_RATIO = 0.45F;
	private static final int SEARCH_WIDTH = 150;
	private static final int SEARCH_HEIGHT = 17;
	private static final int SEARCH_ICON = 15;
	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final int MIN_THUMB = 8;
	private static final int SCROLLBAR_GRAB = 2;
	private static final float KNOB_MILLIS = 120.0F;
	private static final float FADE_MILLIS = 150.0F;
	private static final float REVEAL_MILLIS = 220.0F;
	// Page icons, numbered line by line in icons.png
	private static final Identifier ICONS = Mccinfo.id("textures/gui/ui/icons.png");
	private static final int ICONS_SIZE = 40;
	private static final int ICON = 8;
	private static final int ICONS_PER_ROW = ICONS_SIZE / ICON;
	static final int ICON_SEARCH = 0;
	static final int ICON_LINK = 1;
	static final int ICON_KEYBINDS = 2;
	static final int ICON_UTILS = 3;
	static final int ICON_ADMIN = 4;
	static final int ICON_FISHING = 5;
	static final int ICON_HALLOWEEN = 6;
	static final int ICON_SUMMER = 7;
	static final int ICON_PATCH_NOTES = 8;
	static final int ICON_MONEY = 9;
	static final int ICON_VOICE = 10;
	static final int ICON_WIDGETS = 14;
	static final int NO_ICON = -1;
	// Cards, controls, search and scroll layout
	private static final int ICON_GAP = 4;
	private static final int MIN_FLEX_WIDTH = 30;
	private static final int INPUT_WIDTH = 80;
	private static final int SLIDER_WIDTH = 150;
	private static final int SLIDER_KNOB = SliderKnob.WIDTH;
	private static final String[] CHEVRON_GLYPH = {"#...#", ".#.#.", "..#.."};
	private static final String[] CROSS_GLYPH = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};

	// Colors
	private static final int COLOR_OUTLINE = 0xFF262626;
	private static final int COLOR_OUTLINE_HEADER = 0xFF383838;
	private static final int COLOR_HEADER = 0xFF232323;
	private static final int COLOR_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_CARD = 0xFF202020;
	private static final int COLOR_CARD_BORDER = 0xFF3A3A3A;
	private static final int COLOR_WARNING_BORDER = 0xFFD83A3A;
	private static final int COLOR_CONTROL = 0xFF2A2A2A;
	private static final int COLOR_CONTROL_HOVER = 0xFF3A3A3A;
	private static final int COLOR_INPUT = 0xFF2C2C2C;
	private static final int COLOR_INPUT_BORDER = 0xFF161616;
	private static final int COLOR_INPUT_TEXT = 0xFF8C8C8C;
	private static final int COLOR_INPUT_FOCUSED = 0xFFFFFFFF;
	private static final int COLOR_DROPDOWN = 0xFF1E1E1E;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_DESCRIPTION = 0xFF8C8C8C;
	// Mod color: orange
	public static final int ACCENT_BLUE = 0xFF3C8CFF;
	public static final int ACCENT_BLUE_DARK = 0xFF27508F;
	public static final int ACCENT_ORANGE = 0xFFFFA219;
	public static final int ACCENT_ORANGE_DARK = 0xFF9A610F;
	public static final int ACCENT = ACCENT_ORANGE;
	public static final int ACCENT_DARK = ACCENT_ORANGE_DARK;
	private static final int COLOR_SELECTED = ACCENT;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;
	private static final int COLOR_SLIDER_FILL = ACCENT_DARK;
	private static final int COLOR_SLIDER_LEVEL = 0xFF2F7A3A;
	private static final int COLOR_METER_LOW = 0xFF3FBF4F;
	private static final int COLOR_METER_HIGH = 0xFFD83A3A;

	// Page, its sections and their cards
	record Page(String id, Component name, int icon, List<Section> sections) {
	}

	record Section(Component name, List<Card> cards) {
	}

	record Card(String key, Component title, Component description, List<Control> controls, Supplier<List<Chip>> chips, List<Card> row, int level, boolean warning, boolean wide) {
		boolean child() {
			return this.level > 0;
		}

		static Card of(String key, Component title, Component description, Control... controls) {
			return new Card(key, title, description, List.of(controls), null, null, 0, false, false);
		}

		static Card wide(String key, Component title, Component description, Control... controls) {
			return new Card(key, title, description, List.of(controls), null, null, 0, false, true);
		}

		static Card warning(String key, Component title, Component description, Control... controls) {
			return new Card(key, title, description, List.of(controls), null, null, 0, true, false);
		}

		static Card withChips(String key, Component title, Component description, List<Control> controls, Supplier<List<Chip>> chips) {
			return new Card(key, title, description, controls, chips, null, 0, false, false);
		}

		static Card row(String key, Card... cards) {
			return new Card(key, Component.empty(), Component.empty(), List.of(), null, List.of(cards), 0, false, false);
		}

		static Card child(String key, Component title, Component description, Control... controls) {
			return new Card(key, title, description, List.of(controls), null, null, 1, false, false);
		}

		static Card grandchild(String key, Component title, Component description, Control... controls) {
			return new Card(key, title, description, List.of(controls), null, null, 2, false, false);
		}

		private boolean matches(String lower) {
			if (this.row != null) {
				return this.row.stream().anyMatch(card -> card.matches(lower));
			}
			return this.title.getString().toLowerCase(Locale.ROOT).contains(lower) || this.description.getString().toLowerCase(Locale.ROOT).contains(lower);
		}
	}

	// Controls of a card
	sealed interface Control permits Toggle, Action, Dropdown, Input, IconButton, Slider, Meter, Break {
	}

	record Input(EditBox box) implements Control {
	}

	record Toggle(BooleanSupplier value, Consumer<Boolean> onChange) implements Control {
	}

	record Action(Supplier<Component> label, int minWidth, Runnable run) implements Control {
	}

	record IconButton(Identifier sprite, boolean sheet, BooleanSupplier state, BooleanSupplier active, Supplier<Component> tooltip, Runnable run) implements Control {
	}

	record Dropdown(Supplier<Component> label, int width, List<Component> choices, IntConsumer select) implements Control {
	}

	record Slider(double min, DoubleSupplier max, double step, DoubleSupplier value, DoubleConsumer set, Runnable save, DoubleFunction<Component> label,
			BooleanSupplier active, DoubleSupplier level, Supplier<Component> tooltip) implements Control {
		private double clamp(double value) {
			double max = this.max.getAsDouble();
			return Math.clamp(Math.round(value / this.step) * this.step, this.min, Math.max(this.min, max));
		}
	}

	record Meter(DoubleSupplier level, int span) implements Control {
	}

	record Break() implements Control {
	}

	private record SliderHit(Slider slider, int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	// Removable pill under the text
	record Chip(Component label, Runnable remove) {
	}

	// Link of the left column
	record Link(Component name, int icon, Runnable action) {
	}

	private record Hit(int x, int y, int width, int height, Runnable action) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	private record Placed(Control control, int dx, int dy, int width) {
	}

	private record PlacedChip(Chip chip, int dx, int dy, int width) {
	}

	// Card measured once per width
	private record Layout(int height, int titleY, List<FormattedCharSequence> lines, List<Placed> controls, List<PlacedChip> chips, List<Layout> parts) {
	}

	// Cards and sections with their appear progress
	private record ShownCard(Card card, float reveal) {
	}

	private record ShownSection(Section section, float reveal, List<ShownCard> cards) {
	}

	private record OpenDropdown(Dropdown dropdown, int x, int y, int width) {
		private int indexAt(double mouseX, double mouseY) {
			if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y) {
				return -1;
			}
			int index = (int) ((mouseY - this.y) / CHOICE_HEIGHT);
			return index < this.dropdown.choices().size() ? index : -1;
		}
	}

	private final Supplier<List<List<Page>>> groups;
	private final Supplier<List<Link>> links;
	private final Runnable onNavigate;
	private final Supplier<Page> footer;
	private final Consumer<EditBox> focus;
	private final Font font = Minecraft.getInstance().font;
	private final List<Hit> hits = new ArrayList<>();
	private final List<SliderHit> sliderHits = new ArrayList<>();
	private SliderHit draggedSlider;
	private double clickX;
	private double clickY;
	private final Map<String, Float> knobs = new HashMap<>();
	private final Map<String, Float> fades = new HashMap<>();
	private String selected;
	private String jumpSection;
	private Component tooltip;
	private int tooltipX;
	private int tooltipY;
	private String query = "";
	private float scroll;
	private float scrollTarget;
	private float scrollStep = 1.0F;
	private int maxScroll;
	private boolean draggingScrollbar;
	private double dragOffset;
	private long lastFrame;
	private float fadeStep = 1.0F;
	private float knobStep = 1.0F;
	private float revealStep = 1.0F;
	private boolean interactive = true;
	private String revealSignature;
	// Appear animation, previous order kept while a card leaves
	private final Map<String, Float> reveals = new HashMap<>();
	private final Map<String, Section> sectionCache = new HashMap<>();
	private final Map<String, Card> cardCache = new HashMap<>();
	private final Map<String, List<String>> cardOrders = new HashMap<>();
	private List<String> sectionOrder = List.of();
	private OpenDropdown dropdown;
	private EditBox search;

	private int left;
	private int top;
	private int right;
	private int bottom;
	private int sidebarRight;
	private int contentLeft;
	private int viewportTop;

	SettingsPanel(Supplier<List<List<Page>>> groups, Supplier<Page> footer, Supplier<List<Link>> links, Runnable onNavigate, Consumer<EditBox> focus) {
		this.groups = groups;
		this.footer = footer;
		this.links = links;
		this.onNavigate = onNavigate;
		this.focus = focus;
	}

	// Menu scale for the current GUI Scale
	public static float uiScale() {
		var window = Minecraft.getInstance().getWindow();
		float fit = Math.min(window.getGuiScaledWidth() / (float) MIN_SCREEN_WIDTH, window.getGuiScaledHeight() / (float) MIN_SCREEN_HEIGHT);
		return Math.min(BASE_SCALE, fit);
	}

	static int width(int screenWidth) {
		return Math.min(MAX_WIDTH, screenWidth - 16);
	}

	EditBox search() {
		return this.search;
	}

	boolean overSearch(double mouseX, double mouseY) {
		int x = this.search.getX() - SEARCH_ICON;
		int y = this.top + BORDER + (TOP_BAR - SEARCH_HEIGHT) / 2;
		return mouseX >= x && mouseX < x + this.search.getWidth() + SEARCH_ICON + 4 && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
	}

	EditBox init(int screenWidth, int top, int height) {
		int width = width(screenWidth);
		this.left = (screenWidth - width) / 2;
		this.right = this.left + width;
		this.top = top;
		this.bottom = top + height;
		this.dropdown = null;
		int sidebarWidth = Math.clamp(width * 29 / 100, MIN_SIDEBAR, MAX_SIDEBAR);
		this.sidebarRight = this.left + BORDER + sidebarWidth;
		this.contentLeft = this.sidebarRight + DIVIDER;
		this.viewportTop = this.top + BORDER + TOP_BAR;
		int searchX = this.contentLeft + CONTENT_PADDING;
		int searchY = this.top + BORDER + (TOP_BAR - SEARCH_HEIGHT) / 2;
		int searchWidth = Math.min(SEARCH_WIDTH, this.right - BORDER - CONTENT_PADDING - searchX);
		this.search = new EditBox(this.font, searchX + SEARCH_ICON, searchY + (SEARCH_HEIGHT - 8) / 2, searchWidth - SEARCH_ICON - 4, 9,
				Component.translatable("screen.mccinfo.settings.search"));
		this.search.setBordered(false);
		this.search.setTextColor(COLOR_WHITE);
		this.search.setMaxLength(32);
		this.search.setHint(Component.translatable("screen.mccinfo.settings.search").withColor(COLOR_DESCRIPTION));
		this.search.setValue(this.query);
		this.search.setResponder(value -> {
			this.query = value;
			this.scroll = 0;
		this.scrollTarget = 0;
			this.dropdown = null;
		});
		return this.search;
	}

	// Hover fade, 150 ms
	void beginFrame() {
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		this.fadeStep = Math.min(1.0F, elapsed / FADE_MILLIS);
		this.knobStep = Math.min(1.0F, elapsed / KNOB_MILLIS);
		this.revealStep = Math.min(1.0F, elapsed / REVEAL_MILLIS);
		this.scrollStep = 1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS);
	}

	float fade(String key, boolean hovered) {
		float value = this.fades.getOrDefault(key, 0.0F);
		value = hovered ? Math.min(1.0F, value + this.fadeStep) : Math.max(0.0F, value - this.fadeStep);
		this.fades.put(key, value);
		return value;
	}

	static int mix(int from, int to, float t) {
		int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
		int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	void extractBackground(GuiGraphicsExtractor graphics) {
		graphics.fill(this.left, this.top, this.right, this.bottom, COLOR_OUTLINE);
		graphics.fill(this.left, this.top, this.right, this.viewportTop, COLOR_OUTLINE_HEADER);
		graphics.fill(this.left + BORDER, this.top, this.sidebarRight, this.viewportTop, COLOR_HEADER);
		graphics.fill(this.contentLeft, this.top, this.right - BORDER, this.viewportTop, COLOR_HEADER);
		graphics.fill(this.left + BORDER, this.viewportTop, this.sidebarRight, this.bottom - BORDER, COLOR_BACKGROUND);
		graphics.fill(this.contentLeft, this.viewportTop, this.right - BORDER, this.bottom - BORDER, COLOR_BACKGROUND);
		int x = this.search.getX() - SEARCH_ICON;
		int y = this.top + BORDER + (TOP_BAR - SEARCH_HEIGHT) / 2;
		int width = this.search.getWidth() + SEARCH_ICON + 4;
		graphics.fill(x, y, x + width, y + SEARCH_HEIGHT, COLOR_INPUT_BORDER);
		graphics.fill(x, y, x + width - 1, y + SEARCH_HEIGHT - 1, COLOR_INPUT);
		icon(graphics, ICON_SEARCH, x + 4, y + (SEARCH_HEIGHT - ICON) / 2, COLOR_TEXT);
	}

	void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		this.hits.clear();
		this.sliderHits.clear();
		this.tooltip = null;
		List<List<Page>> groups = this.groups.get().stream().filter(group -> !group.isEmpty()).toList();
		Page footer = this.footer.get();
		List<Page> pages = new ArrayList<>(groups.stream().flatMap(List::stream).toList());
		if (footer != null) {
			pages.add(footer);
		}
		if (pages.stream().noneMatch(page -> page.id().equals(this.selected))) {
			this.selected = pages.isEmpty() ? null : pages.getFirst().id();
		}
		int hoverX = this.dropdown != null ? Integer.MIN_VALUE : mouseX;
		int hoverY = this.dropdown != null ? Integer.MIN_VALUE : mouseY;
		extractSidebar(graphics, groups, hoverX, hoverY);
		if (footer != null) {
			extractFooter(graphics, footer, hoverX, hoverY);
		}
		extractContent(graphics, pages, hoverX, hoverY);
		if (this.dropdown != null) {
			extractDropdown(graphics, mouseX, mouseY);
		}
		if (this.tooltip != null) {
			MenuTooltip.extract(graphics, this.tooltip, this.tooltipX, this.tooltipY, uiScale());
		}
	}

	// Left column: page boxes, then links
	private void extractSidebar(GuiGraphicsExtractor graphics, List<List<Page>> groups, int mouseX, int mouseY) {
		int x = this.left + BORDER + SIDEBAR_PADDING;
		int width = this.sidebarRight - x;
		graphics.text(this.font, Component.translatable("screen.mccinfo.events.title"), x, this.top + BORDER + (TOP_BAR - this.font.lineHeight) / 2 + 1, COLOR_WHITE, true);
		int y = this.viewportTop + SIDEBAR_GAP;
		for (List<Page> group : groups) {
			for (Page page : group) {
				boolean hovered = hovered(x, y, width, SIDEBAR_LINE, mouseX, mouseY);
				float fade = fade("page:" + page.id(), hovered);
				boolean current = page.id().equals(this.selected);
				labelWithIcon(graphics, page.icon(), page.name(), x, y + 3, current ? COLOR_SELECTED : mix(COLOR_TEXT, COLOR_WHITE, fade));
				this.hits.add(new Hit(x, y, width, SIDEBAR_LINE, () -> select(page.id())));
				if (hovered) {
					NeuItems.pointer(graphics);
				}
				y += SIDEBAR_LINE;
			}
			y += SIDEBAR_GAP;
			graphics.fill(this.left + BORDER, y, this.sidebarRight, y + 2, COLOR_OUTLINE);
			y += SIDEBAR_GAP + 2;
		}
		for (Link link : this.links.get()) {
			boolean hovered = hovered(x, y, width, SIDEBAR_LINE, mouseX, mouseY);
			int color = mix(COLOR_TEXT, COLOR_WHITE, fade("link:" + link.name().getString(), hovered));
			int end = labelWithIcon(graphics, link.icon(), link.name(), x, y + 3, color);
			icon(graphics, ICON_LINK, end + 2, y + 3, color);
			this.hits.add(new Hit(x, y, width, SIDEBAR_LINE, () -> {
				NeuItems.clickSound();
				link.action().run();
			}));
			if (hovered) {
				NeuItems.pointer(graphics);
			}
			y += SIDEBAR_LINE;
		}
	}

	// Admin shortcut at the bottom
	private void extractFooter(GuiGraphicsExtractor graphics, Page page, int mouseX, int mouseY) {
		int x = this.left + BORDER + SIDEBAR_PADDING;
		int width = this.sidebarRight - x;
		int y = this.bottom - BORDER - SIDEBAR_GAP - SIDEBAR_LINE;
		int lineY = y - SIDEBAR_GAP - 2;
		graphics.fill(this.left + BORDER, lineY, this.sidebarRight, lineY + 2, COLOR_OUTLINE);
		boolean hovered = hovered(x, y, width, SIDEBAR_LINE, mouseX, mouseY);
		float fade = fade("page:" + page.id(), hovered);
		int color = page.id().equals(this.selected) ? COLOR_SELECTED : mix(COLOR_TEXT, COLOR_WHITE, fade);
		labelWithIcon(graphics, page.icon(), page.name(), x, y + 3, color);
		this.hits.add(new Hit(x, y, width, SIDEBAR_LINE, () -> select(page.id())));
		if (hovered) {
			NeuItems.pointer(graphics);
		}
	}

	// Right side: search and the chosen page
	private void extractContent(GuiGraphicsExtractor graphics, List<Page> pages, int mouseX, int mouseY) {
		int x = this.contentLeft + CONTENT_PADDING;
		int width = this.right - BORDER - CONTENT_PADDING - x;
		int viewportBottom = this.bottom - BORDER;
		List<Section> sections = shownSections(pages);
		if (sections.isEmpty()) {
			Component empty = Component.translatable(this.query.isBlank() ? "screen.mccinfo.settings.empty" : "screen.mccinfo.settings.no_results");
			graphics.centeredText(this.font, empty, x + width / 2, (this.viewportTop + viewportBottom - this.font.lineHeight) / 2, COLOR_DESCRIPTION);
			this.maxScroll = 0;
			this.scroll = 0;
			this.scrollTarget = 0;
			return;
		}
		List<ShownSection> shown = reveal(sections);
		Map<Card, Layout> layouts = new HashMap<>();
		int total = CONTENT_PADDING / 2;
		int jumpOffset = -1;
		for (ShownSection section : shown) {
			if (this.jumpSection != null && section.section().name().getString().equals(this.jumpSection)) {
				jumpOffset = total - CONTENT_PADDING / 2;
			}
			total += scaled(SECTION_HEIGHT, section.reveal());
			for (ShownCard entry : section.cards()) {
				Card card = entry.card();
				Layout layout = layout(card, width - CHILD_INDENT * card.level());
				layouts.put(card, layout);
				total += scaled(layout.height() + CARD_GAP, entry.reveal());
			}
		}
		this.maxScroll = Math.max(0, total + CONTENT_PADDING / 2 - (viewportBottom - this.viewportTop));
		if (this.jumpSection != null) {
			this.jumpSection = null;
			if (jumpOffset >= 0) {
				this.scrollTarget = jumpOffset;
				this.scroll = Math.clamp(jumpOffset, 0, this.maxScroll);
			}
		}
		this.scrollTarget = Math.clamp(this.scrollTarget, 0, this.maxScroll);
		if (this.draggingScrollbar || Math.abs(this.scrollTarget - this.scroll) < 0.5F) {
			this.scroll = this.scrollTarget;
		} else {
			this.scroll += (this.scrollTarget - this.scroll) * this.scrollStep;
		}
		this.scroll = Math.clamp(this.scroll, 0, this.maxScroll);
		boolean inside = mouseY >= this.viewportTop && mouseY < viewportBottom;
		int hoverX = inside ? mouseX : Integer.MIN_VALUE;
		int hoverY = inside ? mouseY : Integer.MIN_VALUE;

		graphics.enableScissor(this.contentLeft, this.viewportTop, this.right - BORDER, viewportBottom);
		int y = this.viewportTop + CONTENT_PADDING / 2 - Math.round(this.scroll);
		for (ShownSection section : shown) {
			int headerHeight = scaled(SECTION_HEIGHT, section.reveal());
			if (headerHeight > 0) {
				boolean partial = section.reveal() < 1.0F;
				if (partial) {
					graphics.enableScissor(this.contentLeft, y, this.right - BORDER, y + headerHeight);
				}
				extractSectionHeader(graphics, section.section().name(), x, width, y);
				if (partial) {
					fadeOver(graphics, this.contentLeft, y, this.right - BORDER, y + headerHeight, section.reveal());
					graphics.disableScissor();
				}
			}
			y += headerHeight;
			List<ShownCard> cards = section.cards();
			int index = 0;
			while (index < cards.size()) {
				ShownCard entry = cards.get(index);
				Card card = entry.card();
				Layout layout = layouts.get(card);
				if (entry.reveal() >= 1.0F) {
					int visible = layout.height() + CARD_GAP;
					if (y + visible > this.viewportTop && y - CARD_GAP < viewportBottom) {
						if (card.child()) {
							extractChildArrows(graphics, x, y, layout.height(), cards, index);
							extractCard(graphics, card, layout, x + CHILD_INDENT * card.level(), y, width - CHILD_INDENT * card.level(), hoverX, hoverY);
						} else {
							extractCard(graphics, card, layout, x, y, width, hoverX, hoverY);
						}
					}
					y += visible;
					index++;
					continue;
				}
				int end = index;
				int full = 0;
				int visible = 0;
				while (end < cards.size() && cards.get(end).reveal() < 1.0F) {
					Layout part = layouts.get(cards.get(end).card());
					full += part.height() + CARD_GAP;
					visible += scaled(part.height() + CARD_GAP, cards.get(end).reveal());
					end++;
				}
				if (visible > 0 && y + visible > this.viewportTop && y < viewportBottom) {
					this.interactive = false;
					int cardY = y - (full - visible);
					graphics.enableScissor(this.contentLeft, y - CARD_GAP, this.right - BORDER, y + Math.max(0, visible - CARD_GAP));
					int arrowY = cardY;
					for (int i = index; i < end; i++) {
						Layout part = layouts.get(cards.get(i).card());
						if (cards.get(i).card().child()) {
							extractChildArrows(graphics, x, arrowY, part.height(), cards, i);
						}
						arrowY += part.height() + CARD_GAP;
					}
					graphics.disableScissor();
					graphics.enableScissor(this.contentLeft, y, this.right - BORDER, y + Math.max(0, visible - CARD_GAP));
					for (int i = index; i < end; i++) {
						ShownCard sliding = cards.get(i);
						Layout part = layouts.get(sliding.card());
						if (sliding.card().child()) {
							int level = sliding.card().level();
							extractCard(graphics, sliding.card(), part, x + CHILD_INDENT * level, cardY, width - CHILD_INDENT * level, Integer.MIN_VALUE, Integer.MIN_VALUE);
						} else {
							extractCard(graphics, sliding.card(), part, x, cardY, width, Integer.MIN_VALUE, Integer.MIN_VALUE);
							fadeOver(graphics, x, cardY, x + width, cardY + part.height(), sliding.reveal());
						}
						cardY += part.height() + CARD_GAP;
					}
					graphics.disableScissor();
					this.interactive = true;
				}
				y += visible;
				index = end;
			}
		}
		graphics.disableScissor();

		if (this.maxScroll > 0) {
			int trackX = this.right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			int thumbY = thumbY(this.scroll);
			graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR);
		}
	}

	private int trackHeight() {
		return this.bottom - BORDER - this.viewportTop;
	}

	private int thumbHeight() {
		int trackHeight = trackHeight();
		return Math.max(MIN_THUMB, trackHeight * trackHeight / (trackHeight + this.maxScroll));
	}

	private int thumbY(float scroll) {
		return this.viewportTop + Math.round((trackHeight() - thumbHeight()) * scroll / this.maxScroll);
	}

	private boolean overScrollbar(double mouseX, double mouseY) {
		return this.maxScroll > 0 && mouseX >= this.right - BORDER - SCROLLBAR_GRAB && mouseX < this.right
				&& mouseY >= this.viewportTop && mouseY < this.bottom - BORDER;
	}

	boolean dragScrollbar(double mouseY) {
		if (!this.draggingScrollbar) {
			return false;
		}
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			this.scrollTarget = Math.clamp((float) ((mouseY - this.dragOffset - this.viewportTop) * this.maxScroll / range), 0, this.maxScroll);
			this.scroll = this.scrollTarget;
		}
		return true;
	}

	boolean stopScrollbarDrag() {
		boolean was = this.draggingScrollbar;
		this.draggingScrollbar = false;
		return was;
	}

	private static int scaled(int height, float reveal) {
		return reveal >= 1.0F ? height : Math.round(height * eased(reveal));
	}

	private static float eased(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	// Veil fading over a card that appears
	private void fadeOver(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float reveal) {
		int alpha = Math.clamp(Math.round((1.0F - eased(reveal)) * 255.0F), 0, 255);
		if (alpha > 0) {
			graphics.nextStratum();
			graphics.fill(x0, y0, x1, y1, alpha << 24 | COLOR_BACKGROUND & 0xFFFFFF);
		}
	}

	private float advance(String key, boolean present, boolean reset) {
		if (reset) {
			this.reveals.put(key, 1.0F);
			return 1.0F;
		}
		float value = this.reveals.getOrDefault(key, 0.0F);
		value = present ? Math.min(1.0F, value + this.revealStep) : Math.max(0.0F, value - this.revealStep);
		if (value <= 0.0F && !present) {
			this.reveals.remove(key);
		} else {
			this.reveals.put(key, value);
		}
		return value;
	}

	// Previous order with the new cards
	private static List<String> mergeOrder(List<String> previous, List<String> current, java.util.function.Predicate<String> keep) {
		List<String> order = new ArrayList<>(current);
		for (int i = 0; i < previous.size(); i++) {
			String key = previous.get(i);
			if (order.contains(key) || !keep.test(key)) {
				continue;
			}
			int at = 0;
			for (int j = i - 1; j >= 0; j--) {
				int index = order.indexOf(previous.get(j));
				if (index >= 0) {
					at = index + 1;
					break;
				}
			}
			order.add(at, key);
		}
		return order;
	}

	private List<ShownSection> reveal(List<Section> sections) {
		String signature = this.selected + "|" + this.query;
		boolean reset = !signature.equals(this.revealSignature);
		this.revealSignature = signature;
		if (reset) {
			this.reveals.clear();
			this.sectionCache.clear();
			this.cardCache.clear();
			this.cardOrders.clear();
			this.sectionOrder = List.of();
		}
		Map<String, Section> current = new LinkedHashMap<>();
		for (Section section : sections) {
			current.put(section.name().getString(), section);
		}
		this.sectionCache.putAll(current);
		List<String> order = mergeOrder(this.sectionOrder, List.copyOf(current.keySet()), name -> this.reveals.containsKey("s:" + name));
		List<ShownSection> shown = new ArrayList<>();
		List<String> keptSections = new ArrayList<>();
		for (String name : order) {
			Section section = current.containsKey(name) ? current.get(name) : this.sectionCache.get(name);
			List<Card> cards = current.containsKey(name) ? section.cards() : List.of();
			Map<String, Card> present = new LinkedHashMap<>();
			for (Card card : cards) {
				present.put(card.key(), card);
				this.cardCache.put(name + "/" + card.key(), card);
			}
			List<String> keys = mergeOrder(this.cardOrders.getOrDefault(name, List.of()), List.copyOf(present.keySet()),
					key -> this.reveals.containsKey("c:" + name + "/" + key));
			List<ShownCard> shownCards = new ArrayList<>();
			List<String> keptCards = new ArrayList<>();
			for (String key : keys) {
				float value = advance("c:" + name + "/" + key, present.containsKey(key), reset);
				Card card = present.containsKey(key) ? present.get(key) : this.cardCache.get(name + "/" + key);
				if (value > 0.0F && card != null) {
					shownCards.add(new ShownCard(card, value));
					keptCards.add(key);
				} else {
					this.cardCache.remove(name + "/" + key);
				}
			}
			this.cardOrders.put(name, keptCards);
			float value = advance("s:" + name, current.containsKey(name), reset);
			if (value > 0.0F && section != null) {
				shown.add(new ShownSection(section, value, shownCards));
				keptSections.add(name);
			} else {
				this.sectionCache.remove(name);
				this.cardOrders.remove(name);
			}
		}
		this.sectionOrder = keptSections;
		return shown;
	}

	private List<Section> shownSections(List<Page> pages) {
		String lower = this.query.strip().toLowerCase(Locale.ROOT);
		if (lower.isEmpty()) {
			return pages.stream().filter(page -> page.id().equals(this.selected)).findFirst()
					.map(page -> page.sections().stream().filter(section -> !section.cards().isEmpty()).toList()).orElse(List.of());
		}
		Map<String, Section> merged = new LinkedHashMap<>();
		for (Page page : pages) {
			for (Section section : page.sections()) {
				List<Card> cards = section.cards().stream().filter(card -> card.matches(lower)).toList();
				if (!cards.isEmpty()) {
					merged.computeIfAbsent(section.name().getString(), name -> new Section(section.name(), new ArrayList<>())).cards().addAll(cards);
				}
			}
		}
		return List.copyOf(merged.values());
	}

	// Line goes on only if a card of the same level follows
	private static boolean hasNextSibling(List<ShownCard> cards, int index, int level) {
		for (int i = index + 1; i < cards.size(); i++) {
			int other = cards.get(i).card().level();
			if (other <= level) {
				return other == level;
			}
		}
		return false;
	}

	private static void extractChildArrows(GuiGraphicsExtractor graphics, int x, int y, int height, List<ShownCard> cards, int index) {
		int level = cards.get(index).card().level();
		for (int parent = 1; parent < level; parent++) {
			if (hasNextSibling(cards, index, parent)) {
				int lineX = x + CHILD_INDENT * (parent - 1) + CHILD_INDENT / 2 - 1;
				graphics.fill(lineX, y - CARD_GAP, lineX + 1, y + height, COLOR_DESCRIPTION);
			}
		}
		extractChildArrow(graphics, x + CHILD_INDENT * (level - 1), y, height, hasNextSibling(cards, index, level));
	}

	// Arrow from the parent card
	private static void extractChildArrow(GuiGraphicsExtractor graphics, int x, int y, int height, boolean continues) {
		int lineX = x + CHILD_INDENT / 2 - 1;
		int middle = y + height / 2;
		int tip = x + CHILD_INDENT - 3;
		graphics.fill(lineX, y - CARD_GAP, lineX + 1, continues ? y + height : middle + 1, COLOR_DESCRIPTION);
		graphics.fill(lineX, middle, tip, middle + 1, COLOR_DESCRIPTION);
		for (int i = 1; i <= ARROW_HEAD; i++) {
			graphics.fill(tip - i, middle - i, tip - i + 1, middle + i + 1, COLOR_DESCRIPTION);
		}
		graphics.fill(tip, middle, tip + 1, middle + 1, COLOR_DESCRIPTION);
	}

	private void extractSectionHeader(GuiGraphicsExtractor graphics, Component name, int x, int width, int y) {
		int textWidth = this.font.width(name);
		int lineLength = Math.max(0, (width - textWidth - SECTION_LABEL_GAP * 2) / 2);
		int textY = y + (SECTION_HEIGHT - this.font.lineHeight) / 2 + 1;
		int lineY = textY + this.font.lineHeight / 2 - 1;
		graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_CARD_BORDER);
		graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_CARD_BORDER);
		graphics.text(this.font, name, x + lineLength + SECTION_LABEL_GAP, textY, COLOR_TEXT, true);
	}

	private int controlWidth(Control control) {
		return switch (control) {
			case Toggle toggle -> ToggleSwitch.WIDTH;
			case Action action -> Math.max(action.minWidth(), this.font.width(action.label().get()) + BUTTON_PADDING * 2);
			case Dropdown dropdown -> Math.max(dropdown.width(), MIN_FLEX_WIDTH);
			case Input input -> INPUT_WIDTH;
			case IconButton button -> ICON_BUTTON_WIDTH;
			case Slider slider -> SLIDER_WIDTH;
			case Meter meter -> SLIDER_WIDTH;
			case Break lineBreak -> 0;
		};
	}

	private static int controlHeight(Control control) {
		return control instanceof IconButton || control instanceof Meter ? ICON_BUTTON_HEIGHT : CONTROL_HEIGHT;
	}

	private static int controlsHeight(Card card) {
		int height = 0;
		for (Control control : card.controls()) {
			height = Math.max(height, controlHeight(control));
		}
		return height;
	}

	private static int span(Control control) {
		return control instanceof Meter meter ? Math.max(1, meter.span()) : 1;
	}

	private static boolean flex(Control control) {
		return control instanceof Input || control instanceof Dropdown dropdown && dropdown.width() == 0;
	}

	private record WrappedText(String text, int width, List<FormattedCharSequence> lines) {
	}

	private static final int MAX_WRAPPED = 512;
	private final Map<String, WrappedText> wrapped = new HashMap<>();

	// Description wrapped once per width
	private List<FormattedCharSequence> descriptionLines(Card card, int width) {
		String text = card.description().getString();
		if (text.isEmpty()) {
			return List.of();
		}
		WrappedText cached = this.wrapped.get(card.key());
		if (cached == null || cached.width() != width || !cached.text().equals(text)) {
			if (this.wrapped.size() >= MAX_WRAPPED) {
				this.wrapped.clear();
			}
			cached = new WrappedText(text, width, this.font.split(card.description(), width));
			this.wrapped.put(card.key(), cached);
		}
		return cached.lines();
	}

	private int chipWidth(Chip chip) {
		return CHIP_PADDING * 3 + this.font.width(chip.label()) + CROSS_GLYPH.length;
	}

	private Layout layout(Card card, int width) {
		if (card.row() != null) {
			int count = card.row().size();
			int partWidth = (width - CARD_GAP * (count - 1)) / count;
			List<Layout> parts = card.row().stream().map(part -> layout(part, partWidth, true)).toList();
			return new Layout(parts.stream().mapToInt(Layout::height).max().orElse(0), 0, List.of(), List.of(), List.of(), parts);
		}
		return layout(card, width, false);
	}

	// Controls on the right, or under the text when too wide
	private Layout layout(Card card, int width, boolean forceBelow) {
		int inner = width - CARD_PADDING * 2;
		int controlsWidth = 0;
		int fixedWidth = 0;
		int flexCount = 0;
		for (Control control : card.controls()) {
			controlsWidth += controlWidth(control) + (controlsWidth > 0 ? CONTROL_GAP : 0);
			if (flex(control)) {
				flexCount++;
			} else {
				fixedWidth += controlWidth(control);
			}
		}
		boolean below = forceBelow || card.wide() || controlsWidth > inner * BELOW_RATIO;
		int flexWidth = flexCount == 0 ? 0
				: Math.max(MIN_FLEX_WIDTH, (inner - fixedWidth - CONTROL_GAP * (card.controls().size() - 1)) / flexCount);
		int textWidth = below || card.controls().isEmpty() ? inner : width - CARD_PADDING - CONTROL_MARGIN * 2 - controlsWidth;
		List<FormattedCharSequence> lines = descriptionLines(card, textWidth);
		int textHeight = this.font.lineHeight + (lines.isEmpty() ? 0 : TITLE_GAP + lines.size() * TEXT_LINE - 1);
		List<Placed> placed = new ArrayList<>();
		int block;
		int titleY = CARD_PADDING;
		if (card.controls().isEmpty()) {
			block = textHeight;
		} else if (card.wide()) {
			List<List<Control>> rows = new ArrayList<>();
			rows.add(new ArrayList<>());
			for (Control control : card.controls()) {
				if (control instanceof Break) {
					rows.add(new ArrayList<>());
				} else {
					rows.getLast().add(control);
				}
			}
			int columns = 1;
			for (List<Control> row : rows) {
				columns = Math.max(columns, row.stream().mapToInt(SettingsPanel::span).sum());
			}
			int column = (inner - CONTROL_GAP * (columns - 1)) / columns;
			int rowY = CARD_PADDING + textHeight + ROW_GAP;
			for (List<Control> row : rows) {
				int controlX = CARD_PADDING;
				int rowHeight = 0;
				for (Control control : row) {
					int span = span(control);
					int controlWidth = column * span + CONTROL_GAP * (span - 1);
					placed.add(new Placed(control, controlX, rowY, controlWidth));
					controlX += controlWidth + CONTROL_GAP;
					rowHeight = Math.max(rowHeight, controlHeight(control));
				}
				rowY += rowHeight + CONTROL_GAP;
			}
			block = rowY - CONTROL_GAP - CARD_PADDING;
		} else if (below) {
			int controlX = CARD_PADDING;
			int controlY = CARD_PADDING + textHeight + ROW_GAP;
			for (Control control : card.controls()) {
				int controlWidth = flex(control) ? flexWidth : controlWidth(control);
				placed.add(new Placed(control, controlX, controlY, controlWidth));
				controlX += controlWidth + CONTROL_GAP;
			}
			block = textHeight + ROW_GAP + controlsHeight(card);
		} else {
			block = Math.max(textHeight, controlsHeight(card));
			titleY += (block - textHeight) / 2;
			int controlX = width - CONTROL_MARGIN - controlsWidth;
			for (Control control : card.controls()) {
				int controlWidth = controlWidth(control);
				int controlY = CARD_PADDING + (block - controlHeight(control)) / 2;
				placed.add(new Placed(control, controlX, controlY, controlWidth));
				controlX += controlWidth + CONTROL_GAP;
			}
		}
		int height = CARD_PADDING * 2 + block;
		List<PlacedChip> chips = new ArrayList<>();
		List<Chip> cardChips = card.chips() == null ? List.of() : card.chips().get();
		if (!cardChips.isEmpty()) {
			int chipX = CARD_PADDING;
			int chipY = CARD_PADDING + block + ROW_GAP;
			for (Chip chip : cardChips) {
				int chipWidth = chipWidth(chip);
				if (chipX > CARD_PADDING && chipX + chipWidth > width - CARD_PADDING) {
					chipX = CARD_PADDING;
					chipY += CHIP_HEIGHT + CHIP_GAP;
				}
				chips.add(new PlacedChip(chip, chipX, chipY, chipWidth));
				chipX += chipWidth + CHIP_GAP;
			}
			height = chipY + CHIP_HEIGHT + CARD_PADDING;
		}
		return new Layout(height, titleY, lines, placed, chips, null);
	}

	private void extractCard(GuiGraphicsExtractor graphics, Card card, Layout layout, int x, int y, int width, int mouseX, int mouseY) {
		if (layout.parts() != null) {
			int count = layout.parts().size();
			int partWidth = (width - CARD_GAP * (count - 1)) / count;
			for (int i = 0; i < count; i++) {
				extractCard(graphics, card.row().get(i), layout.parts().get(i), x + i * (partWidth + CARD_GAP), y, partWidth, layout.height(), mouseX, mouseY);
			}
			return;
		}
		extractCard(graphics, card, layout, x, y, width, layout.height(), mouseX, mouseY);
	}

	private void extractCard(GuiGraphicsExtractor graphics, Card card, Layout layout, int x, int y, int width, int height, int mouseX, int mouseY) {
		graphics.fill(x, y, x + width, y + height, card.warning() ? COLOR_WARNING_BORDER : COLOR_CARD_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_CARD);
		int textX = x + CARD_PADDING;
		int textY = y + layout.titleY();
		graphics.text(this.font, card.title().copy().withStyle(ChatFormatting.BOLD), textX, textY, COLOR_WHITE, true);
		textY += this.font.lineHeight + TITLE_GAP;
		for (FormattedCharSequence line : layout.lines()) {
			graphics.text(this.font, line, textX, textY, COLOR_DESCRIPTION, true);
			textY += TEXT_LINE;
		}
		int controlShift = height - layout.height();
		for (int i = 0; i < layout.controls().size(); i++) {
			Placed placed = layout.controls().get(i);
			extractControl(graphics, card.key() + "#" + i, placed.control(), x + placed.dx(), y + placed.dy() + controlShift, placed.width(), mouseX, mouseY);
		}
		for (int i = 0; i < layout.chips().size(); i++) {
			PlacedChip placed = layout.chips().get(i);
			extractChip(graphics, card.key() + "@" + placed.chip().label().getString(), placed.chip(), x + placed.dx(), y + placed.dy(), placed.width(), mouseX, mouseY);
		}
	}

	private static void extractMeter(GuiGraphicsExtractor graphics, double level, int x, int y, int width, int height) {
		int filled = (int) Math.round(width * Math.clamp(level, 0.0, 1.0));
		for (int column = 0; column < filled; column += 2) {
			int color = mix(COLOR_METER_LOW, COLOR_METER_HIGH, (float) column / Math.max(1, width - 1));
			graphics.fill(x + column, y, x + Math.min(filled, column + 2), y + height, color);
		}
	}

	private void extractBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float fade) {
		graphics.fill(x, y, x + width, y + height, COLOR_CARD_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, mix(COLOR_CONTROL, COLOR_CONTROL_HOVER, fade));
	}

	// Toggle, button, field, dropdown, icon button, slider or meter
	private void extractControl(GuiGraphicsExtractor graphics, String key, Control control, int x, int y, int width, int mouseX, int mouseY) {
		boolean hovered = hovered(x, y, width, controlHeight(control), mouseX, mouseY);
		float fade = fade(key, hovered);
		int textY = y + (CONTROL_HEIGHT - this.font.lineHeight) / 2 + 1;
		switch (control) {
			case Toggle toggle -> {
				float target = toggle.value().getAsBoolean() ? 1.0F : 0.0F;
				float knob = this.knobs.getOrDefault(key, target);
				knob += Math.signum(target - knob) * Math.min(Math.abs(target - knob), this.knobStep);
				this.knobs.put(key, knob);
				ToggleSwitch.draw(graphics, x, y, knob, true);
				addHit(x, y, width, CONTROL_HEIGHT, () -> {
					NeuItems.clickSound();
					toggle.onChange().accept(!toggle.value().getAsBoolean());
				});
			}
			case Input input -> {
				EditBox box = input.box();
				graphics.fill(x, y, x + width, y + CONTROL_HEIGHT, COLOR_INPUT_BORDER);
				graphics.fill(x, y, x + width - 1, y + CONTROL_HEIGHT - 1, COLOR_INPUT);
				box.setX(x + 5);
				box.setY(y + (CONTROL_HEIGHT - 8) / 2);
				box.setWidth(width - 10);
				box.setTextColor(box.isFocused() ? COLOR_INPUT_FOCUSED : COLOR_INPUT_TEXT);
				box.extractRenderState(graphics, mouseX, mouseY, 0.0F);
				addHit(x, y, width, CONTROL_HEIGHT, () -> {
					this.focus.accept(box);
					box.onClick(new net.minecraft.client.input.MouseButtonEvent(this.clickX, this.clickY,
							new net.minecraft.client.input.MouseButtonInfo(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
				});
			}
			case Action action -> {
				extractBox(graphics, x, y, width, CONTROL_HEIGHT, fade);
				Component label = action.label().get();
				graphics.text(this.font, label, x + (width - this.font.width(label)) / 2, textY, mix(COLOR_TEXT, COLOR_WHITE, fade), true);
				addHit(x, y, width, CONTROL_HEIGHT, () -> {
					NeuItems.clickSound();
					action.run().run();
				});
			}
			case IconButton button -> {
				boolean active = button.active().getAsBoolean();
				extractBox(graphics, x, y, width, ICON_BUTTON_HEIGHT, active ? fade : 0.0F);
				int color = active ? 0xFFFFFFFF : ICON_BUTTON_INACTIVE;
				int spriteX = x + (width - ICON_BUTTON_SPRITE) / 2;
				int spriteY = y + (ICON_BUTTON_HEIGHT - ICON_BUTTON_SPRITE) / 2;
				if (button.sheet()) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, button.sprite(), ICON_BUTTON_SHEET, ICON_BUTTON_SHEET, button.state().getAsBoolean() ? ICON_BUTTON_SPRITE : 0, 0,
							spriteX, spriteY, ICON_BUTTON_SPRITE, ICON_BUTTON_SPRITE, color);
				} else {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, button.sprite(), spriteX, spriteY, ICON_BUTTON_SPRITE, ICON_BUTTON_SPRITE, color);
				}
				if (hovered) {
					this.tooltip = button.tooltip().get();
					this.tooltipX = mouseX;
					this.tooltipY = mouseY;
				}
				if (active) {
					addHit(x, y, width, ICON_BUTTON_HEIGHT, () -> {
						NeuItems.clickSound();
						button.run().run();
					});
				}
			}
			case Meter meter -> {
				int height = ICON_BUTTON_HEIGHT;
				graphics.fill(x, y, x + width, y + height, COLOR_CARD_BORDER);
				graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_CONTROL);
				extractMeter(graphics, meter.level().getAsDouble(), x + 2, y + 2, width - 4, height - 4);
				hovered = false;
			}
			case Break lineBreak -> hovered = false;
			case Slider slider -> {
				boolean active = slider.active().getAsBoolean();
				boolean dragged = this.draggedSlider != null && this.draggedSlider.slider() == slider;
				double min = slider.min();
				double range = Math.max(1.0E-6, slider.max().getAsDouble() - min);
				double value = slider.value().getAsDouble();
				double fraction = Math.clamp((value - min) / range, 0.0, 1.0);
				int trackY = SliderKnob.trackY(y, CONTROL_HEIGHT);
				int trackBottom = trackY + SliderKnob.TRACK_HEIGHT;
				graphics.fill(x, trackY, x + width, trackBottom, COLOR_CARD_BORDER);
				graphics.fill(x + 1, trackY + 1, x + width - 1, trackBottom - 1, mix(COLOR_CONTROL, COLOR_CONTROL_HOVER, active ? fade : 0.0F));
				int inner = width - 2 - SLIDER_KNOB;
				int knobX = x + 1 + (int) Math.round(inner * fraction);
				if (active) {
					graphics.fill(x + 1, trackY + 1, knobX, trackBottom - 1, COLOR_SLIDER_FILL);
				}
				if (slider.level() != null) {
					double level = Math.clamp(slider.level().getAsDouble(), 0.0, 1.0);
					if (level > 0.0) {
						graphics.fill(x + 1, trackBottom - 4, x + 1 + (int) Math.round((width - 2) * level), trackBottom - 1, COLOR_SLIDER_LEVEL);
					}
				}
				SliderKnob.draw(graphics, knobX, trackY, active);
				Component label = slider.label().apply(value);
				graphics.text(this.font, label, x + (width - this.font.width(label)) / 2, textY, active ? COLOR_WHITE : COLOR_DESCRIPTION, true);
				if (hovered && slider.tooltip() != null) {
					Component tip = slider.tooltip().get();
					if (tip != null) {
						this.tooltip = tip;
						this.tooltipX = mouseX;
						this.tooltipY = mouseY;
					}
				}
				if (active && this.interactive) {
					int top = Math.max(y, this.viewportTop);
					int bottom = Math.min(y + CONTROL_HEIGHT, this.bottom - BORDER);
					if (bottom > top) {
						SliderHit hit = new SliderHit(slider, x, top, width, bottom - top);
						this.sliderHits.add(hit);
						addHit(x, y, width, CONTROL_HEIGHT, () -> {
							NeuItems.clickSound();
							this.draggedSlider = hit;
							dragControl(this.clickX);
						});
					}
				} else {
					hovered = false;
				}
			}
			case Dropdown dropdown -> {
				extractBox(graphics, x, y, width, CONTROL_HEIGHT, fade);
				int color = mix(COLOR_TEXT, COLOR_WHITE, fade);
				int chevronX = x + width - CHOICE_PADDING - CHEVRON_GLYPH[0].length();
				graphics.enableScissor(x + 1, y, chevronX - 2, y + CONTROL_HEIGHT);
				graphics.text(this.font, dropdown.label().get(), x + CHOICE_PADDING, textY, color, true);
				graphics.disableScissor();
				pixels(graphics, CHEVRON_GLYPH, chevronX, y + (CONTROL_HEIGHT - CHEVRON_GLYPH.length) / 2, color);
				addHit(x, y, width, CONTROL_HEIGHT, () -> {
					NeuItems.clickSound();
					int listWidth = width;
					for (Component choice : dropdown.choices()) {
						listWidth = Math.max(listWidth, this.font.width(choice) + CHOICE_PADDING * 2);
					}
					this.dropdown = new OpenDropdown(dropdown, x, y + CONTROL_HEIGHT, listWidth);
				});
			}
		}
		if (hovered) {
			NeuItems.pointer(graphics);
		}
	}

	private void extractChip(GuiGraphicsExtractor graphics, String key, Chip chip, int x, int y, int width, int mouseX, int mouseY) {
		boolean hovered = hovered(x, y, width, CHIP_HEIGHT, mouseX, mouseY);
		float fade = fade(key, hovered);
		extractBox(graphics, x, y, width, CHIP_HEIGHT, fade);
		int color = mix(COLOR_TEXT, COLOR_WHITE, fade);
		graphics.text(this.font, chip.label(), x + CHIP_PADDING, y + (CHIP_HEIGHT - this.font.lineHeight) / 2 + 1, color, true);
		pixels(graphics, CROSS_GLYPH, x + width - CHIP_PADDING - CROSS_GLYPH.length, y + (CHIP_HEIGHT - CROSS_GLYPH.length) / 2, color);
		addHit(x, y, width, CHIP_HEIGHT, () -> {
			NeuItems.clickSound();
			chip.remove().run();
		});
		if (hovered) {
			NeuItems.pointer(graphics);
		}
	}

	// Open dropdown list over everything
	private void extractDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		OpenDropdown open = this.dropdown;
		List<Component> choices = open.dropdown().choices();
		int height = choices.size() * CHOICE_HEIGHT;
		graphics.fill(open.x(), open.y(), open.x() + open.width(), open.y() + height + 1, COLOR_CARD_BORDER);
		int hovered = open.indexAt(mouseX, mouseY);
		for (int i = 0; i < choices.size(); i++) {
			int y = open.y() + i * CHOICE_HEIGHT;
			float fade = fade("choice:" + i, i == hovered);
			graphics.fill(open.x() + 1, y, open.x() + open.width() - 1, y + CHOICE_HEIGHT, mix(COLOR_DROPDOWN, COLOR_CONTROL_HOVER, fade));
			graphics.text(this.font, choices.get(i), open.x() + CHOICE_PADDING, y + (CHOICE_HEIGHT - this.font.lineHeight) / 2 + 1, mix(COLOR_TEXT, COLOR_WHITE, fade), true);
		}
		if (hovered >= 0) {
			NeuItems.pointer(graphics);
		}
	}

	private void addHit(int x, int y, int width, int height, Runnable action) {
		if (!this.interactive) {
			return;
		}
		int top = Math.max(y, this.viewportTop);
		int bottom = Math.min(y + height, this.bottom - BORDER);
		if (bottom > top) {
			this.hits.add(new Hit(x, top, width, bottom - top, action));
		}
	}

	void show(String page, String section) {
		this.selected = page;
		this.scroll = 0;
		this.scrollTarget = 0;
		this.jumpSection = section;
	}

	private void select(String id) {
		if (!id.equals(this.selected) || !this.query.isEmpty()) {
			NeuItems.clickSound();
			this.onNavigate.run();
		}
		this.selected = id;
		this.scroll = 0;
		this.scrollTarget = 0;
		if (!this.query.isEmpty()) {
			this.search.setValue("");
		}
	}

	boolean dropdownOpen() {
		return this.dropdown != null;
	}

	boolean closeDropdown() {
		if (this.dropdown == null) {
			return false;
		}
		this.dropdown = null;
		return true;
	}

	// Clicks: open dropdown first, then scrollbar, then the hit areas
	boolean mouseClicked(double mouseX, double mouseY) {
		if (this.dropdown != null) {
			int index = this.dropdown.indexAt(mouseX, mouseY);
			if (index >= 0) {
				NeuItems.clickSound();
				this.dropdown.dropdown().select().accept(index);
			}
			this.dropdown = null;
			return true;
		}
		if (overScrollbar(mouseX, mouseY)) {
			int thumbY = thumbY(this.scroll);
			int thumbHeight = thumbHeight();
			this.dragOffset = mouseY >= thumbY && mouseY < thumbY + thumbHeight ? mouseY - thumbY : thumbHeight / 2.0;
			this.draggingScrollbar = true;
			dragScrollbar(mouseY);
			return true;
		}
		this.clickX = mouseX;
		this.clickY = mouseY;
		for (Hit hit : List.copyOf(this.hits)) {
			if (hit.contains(mouseX, mouseY)) {
				hit.action().run();
				return true;
			}
		}
		return mouseX >= this.left && mouseX < this.right && mouseY >= this.top && mouseY < this.bottom;
	}

	boolean dragControl(double mouseX) {
		SliderHit hit = this.draggedSlider;
		if (hit == null) {
			return false;
		}
		Slider slider = hit.slider();
		double fraction = Math.clamp((mouseX - hit.x() - 1 - SLIDER_KNOB / 2.0) / (hit.width() - 2 - SLIDER_KNOB), 0.0, 1.0);
		double value = slider.clamp(slider.min() + fraction * (slider.max().getAsDouble() - slider.min()));
		if (value != slider.value().getAsDouble()) {
			slider.set().accept(value);
		}
		return true;
	}

	boolean stopControlDrag() {
		SliderHit hit = this.draggedSlider;
		this.draggedSlider = null;
		if (hit != null) {
			hit.slider().save().run();
		}
		return hit != null;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.dropdown == null && this.draggedSlider == null) {
			for (SliderHit hit : this.sliderHits) {
				if (hit.contains(mouseX, mouseY)) {
					Slider slider = hit.slider();
					double current = slider.clamp(slider.value().getAsDouble());
					double value = slider.clamp(current + Math.signum(scrollY) * slider.step());
					if (value != slider.value().getAsDouble()) {
						slider.set().accept(value);
						slider.save().run();
					}
					return true;
				}
			}
		}
		if (mouseX < this.contentLeft || mouseX >= this.right || mouseY < this.viewportTop || mouseY >= this.bottom || this.maxScroll == 0) {
			return false;
		}
		this.dropdown = null;
		this.scrollTarget = Math.clamp(this.scrollTarget - (float) scrollY * SCROLL_STEP, 0, this.maxScroll);
		return true;
	}

	private static boolean hovered(int x, int y, int width, int height, int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private int labelWithIcon(GuiGraphicsExtractor graphics, int icon, Component name, int x, int y, int color) {
		int textX = x;
		if (icon != NO_ICON) {
			icon(graphics, icon, x, y, color);
			textX += ICON + ICON_GAP;
		}
		graphics.text(this.font, name, textX, y, color, true);
		return textX + this.font.width(name);
	}

	// Icon from icons.png with a text-like shadow
	private static void icon(GuiGraphicsExtractor graphics, int index, int x, int y, int color) {
		int u = index % ICONS_PER_ROW * ICON;
		int v = index / ICONS_PER_ROW * ICON;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x + 1, y + 1, u, v, ICON, ICON, ICONS_SIZE, ICONS_SIZE, shadow(color));
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, u, v, ICON, ICON, ICONS_SIZE, ICONS_SIZE, color);
	}

	private static int shadow(int color) {
		return color & 0xFF000000 | (color >> 16 & 0xFF) / 4 << 16 | (color >> 8 & 0xFF) / 4 << 8 | (color & 0xFF) / 4;
	}

	// Small glyph drawn pixel by pixel
	private static void pixels(GuiGraphicsExtractor graphics, String[] glyph, int x, int y, int color) {
		for (int row = 0; row < glyph.length; row++) {
			for (int column = 0; column < glyph[row].length(); column++) {
				if (glyph[row].charAt(column) == '#') {
					graphics.fill(x + column, y + row, x + column + 1, y + row + 1, color);
				}
			}
		}
	}
}
