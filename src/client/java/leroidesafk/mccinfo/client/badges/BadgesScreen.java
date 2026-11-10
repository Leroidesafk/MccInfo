package leroidesafk.mccinfo.client.badges;

import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Badge Tracker menu
public final class BadgesScreen extends Screen implements MenuBackdrop.Overlay {
	// Layout
	private static final int MARGIN = 24;
	private static final int MAX_WIDTH = 760;
	private static final int MAX_HEIGHT = 440;
	private static final int TWO_COLUMNS_MIN = 440;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int STATUS_HEIGHT = 14;
	private static final int TAB_HEIGHT = 20;
	private static final int TAB_GAP = 4;
	private static final int TAB_PADDING = 6;
	private static final int TABS_BOTTOM_GAP = 8;
	private static final int CARD_GAP = 4;
	private static final int CARD_PADDING = 6;
	private static final int ICON_BOX = 36;
	private static final int ICON_SIZE = 16;
	private static final int TEXT_GAP = 8;
	private static final int MODE_ICON_GAP = 2;
	private static final int TAG_HEIGHT = 11;
	private static final Component EXPIRED = Component.literal("")
			.withStyle(style -> style.withFont(new FontDescription.Resource(leroidesafk.mccinfo.client.fishing.FishIconRegistry.ICON_FONT)));
	private static final int LINE_HEIGHT = 10;
	private static final int BLOCK_GAP = 4;
	private static final int STRIP_HEIGHT = 15;
	private static final int SUGGESTED_COUNT = 10;
	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final int SCROLLBAR_GRAB = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final float TOOLTIP_SCALE = 1.5F;
	private static final int TOOLTIP_LINE = 12;
	private static final int TOOLTIP_PADDING = 7;
	private static final int TOOLTIP_BORDER = 2;
	private static final int TOOLTIP_OFFSET = 8;
	private static final int TOOLTIP_MARGIN = 4;
	private static final int TOOLTIP_MAX_WIDTH = 200;
	// Tabs, glyphs and stage numbers
	private static final String SUGGESTED = "suggested";
	private static final String OTHER = "other";
	private static final String TEMPLATE_COUNT = "{n}";
	private static final String SEPARATOR = " • ";
	private static final String TROPHY = "";
	private static final String BONUS_TROPHY = "\uE023";
	private static final FontDescription MCC_ICON = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcc", "icon"));
	private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV"};

	// Colors
	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_TAB = 0xFF232323;
	private static final int COLOR_TAB_HOVER = 0xFF2A2A2A;
	private static final int COLOR_TAB_BORDER = 0xFF3A3A3A;
	private static final int COLOR_CARD = 0xFF202020;
	private static final int COLOR_STRIP = 0xFF181818;
	private static final int COLOR_ICON_BOX = 0xFF2A2A2A;
	private static final int COLOR_COMPLETE = 0xFF2F7A2F;
	private static final int COLOR_PARTIAL = 0xFF8A7A2F;
	private static final int COLOR_NONE = 0xFF8A2F2F;
	private static final int COLOR_UNAVAILABLE = 0xFF3A3A3A;
	private static final int COLOR_TOOLTIP_BORDER = 0xFF3A3A3A;
	private static final int COLOR_TOOLTIP_BACKGROUND = 0xFF202020;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_MUTED = 0xFF8C8C8C;
	private static final int COLOR_DONE = 0xFF55FF55;
	private static final int COLOR_TODO = 0xFFFFAA00;
	private static final int COLOR_MISSING = 0xFFFF5555;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;

	// Tab kept for the session
	private static String selectedTab = SUGGESTED;

	private record Tab(String id, Component title, ItemStack icon, List<Badges.Badge> badges, boolean counted) {
		int complete() {
			int count = 0;
			for (Badges.Badge badge : this.badges) {
				count += badge.complete() ? 1 : 0;
			}
			return count;
		}
	}

	private record PlacedTab(Tab tab, int x, int y, int width) {
	}

	private record Card(List<FormattedCharSequence> description, Component estimate, int height) {
	}

	private final Screen parent;
	private final List<Tab> tabs = new ArrayList<>();
	private final List<PlacedTab> placedTabs = new ArrayList<>();
	private final Map<Badges.Badge, Card> cards = new HashMap<>();
	private List<Badges.Badge> builtFrom;
	private Object builtModels;
	private int builtWidth;
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listTop;
	private int listBottom;
	private float scroll;
	private float scrollTarget;
	private long lastFrame;
	private int maxScroll;
	private boolean draggingScrollbar;
	private double dragOffset;
	private Component hoverTooltip;

	public BadgesScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.badges.title"));
		this.parent = parent;
		Badges.refreshOnOpen();
		BadgeNotifier.refreshAll();
	}

	@Override
	public Screen backdropParent() {
		return this.parent;
	}

	@Override
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		this.panelWidth = Math.min(MAX_WIDTH, virtualWidth - MARGIN * 2);
		this.panelHeight = Math.min(MAX_HEIGHT, virtualHeight - MARGIN * 2);
		this.left = (virtualWidth - this.panelWidth) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.listBottom = this.top + this.panelHeight - BORDER - PADDING;
		this.builtWidth = -1;
	}

	private int tabsTop() {
		boolean status = Badges.status() != Badges.Status.READY;
		return this.top + BORDER + PADDING + HEADER_HEIGHT + 2 + (status ? STATUS_HEIGHT : 0);
	}

	// Tabs in a regular grid
	private void placeTabs() {
		this.placedTabs.clear();
		int y = tabsTop();
		if (this.tabs.isEmpty()) {
			this.listTop = y + TABS_BOTTOM_GAP;
			return;
		}
		int width = contentWidth();
		int widest = 0;
		for (Tab tab : this.tabs) {
			widest = Math.max(widest, tabWidth(tab));
		}
		int perRow = Math.max(1, Math.min(this.tabs.size(), (width + TAB_GAP) / (widest + TAB_GAP)));
		int rows = (this.tabs.size() + perRow - 1) / perRow;
		perRow = (this.tabs.size() + rows - 1) / rows;
		int cellWidth = (width - TAB_GAP * (perRow - 1)) / perRow;
		for (int i = 0; i < this.tabs.size(); i++) {
			int row = i / perRow;
			int column = i % perRow;
			this.placedTabs.add(new PlacedTab(this.tabs.get(i), contentLeft() + column * (cellWidth + TAB_GAP), y + row * (TAB_HEIGHT + TAB_GAP), cellWidth));
		}
		this.listTop = y + rows * TAB_HEIGHT + (rows - 1) * TAB_GAP + TABS_BOTTOM_GAP;
	}

	private int tabWidth(Tab tab) {
		int width = TAB_PADDING * 2 + this.font.width(tab.title());
		if (!tab.icon().isEmpty()) {
			width += ICON_SIZE + TAB_PADDING / 2;
		}
		if (tab.counted()) {
			width += TAB_PADDING + this.font.width(tab.complete() + "/" + tab.badges().size());
		}
		return width;
	}

	private int columns() {
		return contentWidth() >= TWO_COLUMNS_MIN ? 2 : 1;
	}

	private int cardWidth() {
		int columns = columns();
		return (contentWidth() - CARD_GAP * (columns - 1)) / columns;
	}

	// Tabs and their badges, rebuilt when the data changes
	private void buildTabs() {
		List<Badges.Badge> badges = Badges.badges();
		if (badges == this.builtFrom && Badges.modelsToken() == this.builtModels && contentWidth() == this.builtWidth) {
			return;
		}
		this.builtFrom = badges;
		this.builtModels = Badges.modelsToken();
		this.builtWidth = contentWidth();
		this.tabs.clear();
		this.cards.clear();
		Map<String, List<Badges.Badge>> byGame = new LinkedHashMap<>();
		for (Badges.Game game : Badges.games()) {
			byGame.put(game.folder(), new ArrayList<>());
		}
		List<Badges.Badge> other = new ArrayList<>();
		List<Badges.Badge> general = new ArrayList<>();
		for (Badges.Badge badge : badges) {
			String folder = Badges.gameFolder(badge.name());
			if (Badges.GENERAL.equals(folder)) {
				general.add(badge);
			} else if (folder == null || !byGame.containsKey(folder)) {
				other.add(badge);
			} else {
				byGame.get(folder).add(badge);
			}
		}
		Comparator<Badges.Badge> order = Comparator.comparingInt((Badges.Badge badge) -> badge.complete() ? 1 : Badges.available(badge) ? 0 : 2)
				.thenComparing(badge -> -badge.progress())
				.thenComparing(Badges.Badge::name);
		List<Badges.Badge> suggested = Badges.suggested(SUGGESTED_COUNT);
		this.tabs.add(new Tab(SUGGESTED, Component.translatable("screen.mccinfo.badges.suggested"), Badges.suggestedIcon(), suggested, false));
		for (Badges.Game game : Badges.games()) {
			List<Badges.Badge> list = byGame.get(game.folder());
			if (!list.isEmpty()) {
				list.sort(order);
				this.tabs.add(new Tab(game.folder(), Component.literal(game.name()), Badges.gameIcon(game.folder()), list, true));
			}
		}
		if (!other.isEmpty()) {
			other.sort(order);
			this.tabs.add(new Tab(OTHER, Component.translatable("screen.mccinfo.badges.other"), ItemStack.EMPTY, other, true));
		}
		if (!general.isEmpty()) {
			general.sort(order);
			this.tabs.add(new Tab(Badges.GENERAL, Component.translatable("screen.mccinfo.badges.general"), Badges.gameIcon(Badges.GENERAL), general, true));
		}
		for (Tab tab : this.tabs) {
			for (Badges.Badge badge : tab.badges()) {
				this.cards.computeIfAbsent(badge, this::layout);
			}
		}
	}

	private Tab currentTab() {
		for (Tab tab : this.tabs) {
			if (tab.id().equals(selectedTab)) {
				return tab;
			}
		}
		return this.tabs.isEmpty() ? null : this.tabs.get(0);
	}

	// Card text and height
	private Card layout(Badges.Badge badge) {
		int icons = modeIcons(badge).size();
		int textWidth = cardWidth() - CARD_PADDING * 2 - ICON_BOX - TEXT_GAP - icons * (ICON_SIZE + MODE_ICON_GAP);
		List<FormattedCharSequence> description = this.font.split(description(badge), Math.max(40, textWidth));
		Component estimate = estimate(badge);
		int textHeight = this.font.lineHeight + 3 + (Badges.available(badge) ? 0 : TAG_HEIGHT) + description.size() * LINE_HEIGHT + BLOCK_GAP + (estimate == null ? 0 : LINE_HEIGHT) + LINE_HEIGHT;
		int height = CARD_PADDING + Math.max(ICON_BOX, textHeight) + CARD_PADDING + STRIP_HEIGHT;
		return new Card(description, estimate, height);
	}

	// Mode icons and their popularity
	private static Component modeTooltip(Badges.Mode mode) {
		MutableComponent tooltip = Component.literal(mode.name()).withStyle(style -> style.withBold(true));
		if (mode.queue() == null) {
			return tooltip;
		}
		String popularity = BadgeNotifier.popularity(mode.queue());
		tooltip.append("\n").append(grey(Component.translatable("screen.mccinfo.badges.popularity"))).append(" ")
				.append(popularity == null ? grey(Component.literal("...")) : Badges.levelName(popularity));
		return tooltip;
	}

	private static List<ItemStack> modeIcons(Badges.Badge badge) {
		List<ItemStack> icons = new ArrayList<>();
		for (Badges.Mode mode : Badges.modesOf(badge)) {
			ItemStack icon = Badges.modeIcon(mode);
			if (!icon.isEmpty()) {
				icons.add(icon);
			}
		}
		if (icons.isEmpty()) {
			ItemStack game = Badges.gameIcon(Badges.gameFolder(badge.name()));
			if (!game.isEmpty()) {
				icons.add(game);
			}
		}
		return icons;
	}

	private static Component description(Badges.Badge badge) {
		Badges.Stage current = badge.current();
		Badges.Model model = Badges.model(badge.name());
		String text = model != null && model.description() != null ? model.description() : goalText(badge);
		int index = text.indexOf(TEMPLATE_COUNT);
		if (index < 0) {
			return grey(Component.literal(text));
		}
		if (current == null || current.obtainable() <= 0) {
			return grey(Component.literal(text.replace(TEMPLATE_COUNT + " ", "").replace(TEMPLATE_COUNT, "")));
		}
		Component count = Component.literal(number(current.obtainable() - current.obtained()) + " ").append(Component.translatable("screen.mccinfo.badges.more"))
				.withStyle(style -> style.withColor(COLOR_WHITE & 0xFFFFFF).withBold(true));
		return grey(Component.literal(text.substring(0, index))).copy().append(count)
				.append(grey(Component.literal(text.substring(index + TEMPLATE_COUNT.length()))));
	}

	private static String goalText(Badges.Badge badge) {
		if (badge.goal() == null || badge.goal().isBlank()) {
			return "";
		}
		if (!badge.statistic()) {
			return badge.goal().replaceAll("\\s+", " ").strip();
		}
		StringBuilder words = new StringBuilder();
		for (String word : badge.goal().split("[_\\s]+")) {
			if (!word.isEmpty()) {
				words.append(words.isEmpty() ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word).append(' ');
			}
		}
		return words.toString().strip();
	}

	// Games left estimate
	private static Component estimate(Badges.Badge badge) {
		Integer left = Badges.gamesLeft(badge);
		Double perGame = Badges.perGame(badge);
		if (left == null || perGame == null) {
			return null;
		}
		return Component.translatable("screen.mccinfo.badges.estimate").withStyle(style -> style.withColor(COLOR_WHITE & 0xFFFFFF))
				.append(" ").append(Component.literal(number(left)).withStyle(style -> style.withBold(true)))
				.append(grey(Component.literal(" (")))
				.append(grey(Component.translatable("screen.mccinfo.badges.per_game", String.format(Locale.US, "%,.2f", perGame))))
				.append(grey(Component.literal(")")));
	}

	private int rowHeight(List<Badges.Badge> badges, int start) {
		int height = 0;
		for (int i = start; i < Math.min(badges.size(), start + columns()); i++) {
			height = Math.max(height, this.cards.get(badges.get(i)).height());
		}
		return height;
	}

	private int contentHeight(Tab tab) {
		if (tab == null || tab.badges().isEmpty()) {
			return 0;
		}
		int height = 0;
		for (int i = 0; i < tab.badges().size(); i += columns()) {
			height += rowHeight(tab.badges(), i) + CARD_GAP;
		}
		return height + CARD_GAP;
	}

	private int contentLeft() {
		return this.left + BORDER + PADDING;
	}

	private int contentWidth() {
		return this.panelWidth - BORDER * 2 - PADDING * 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!MenuBackdrop.extract(this.parent, graphics, this.width, this.height, a)) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		buildTabs();
		placeTabs();
		updateScroll();
		Tab tab = currentTab();
		int virtualX = (int) (mouseX / this.scale);
		int virtualY = (int) (mouseY / this.scale);
		this.maxScroll = Math.max(0, contentHeight(tab) - (this.listBottom - this.listTop));
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll);
		this.scroll = Mth.clamp(this.scroll, 0.0F, this.maxScroll);

		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		int right = this.left + this.panelWidth;
		int bottom = this.top + this.panelHeight;
		graphics.fill(this.left, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		int x = contentLeft();
		int width = contentWidth();
		int complete = 0;
		for (Badges.Badge badge : Badges.badges()) {
			complete += badge.complete() ? 1 : 0;
		}
		Component header = Component.empty().append(this.title).append(" " + complete + "/" + Badges.badges().size());
		extractHeader(graphics, header, x, width, this.top + BORDER + PADDING);
		Component status = statusText();
		if (status != null) {
			int statusY = this.top + BORDER + PADDING + HEADER_HEIGHT + 2;
			graphics.centeredText(this.font, Component.literal(this.font.substrByWidth(status, width).getString()), x + width / 2, statusY,
					Badges.status() == Badges.Status.LOADING ? COLOR_MUTED : COLOR_MISSING);
		}
		for (PlacedTab placed : this.placedTabs) {
			extractTab(graphics, placed, placed.tab() == tab, virtualX, virtualY);
		}

		boolean inList = virtualY >= this.listTop && virtualY < this.listBottom && virtualX >= x && virtualX < x + width;
		this.hoverTooltip = null;
		if (tab != null && tab.badges().isEmpty()) {
			graphics.centeredText(this.font, Component.translatable(tab.id().equals(SUGGESTED) ? "screen.mccinfo.badges.no_suggestion" : "screen.mccinfo.badges.empty"),
					x + width / 2, this.listTop + CARD_GAP, COLOR_MUTED);
		}
		if (tab != null) {
			int columns = columns();
			int cardWidth = cardWidth();
			graphics.enableScissor(this.left + BORDER, this.listTop, right - BORDER, this.listBottom);
			int rowY = this.listTop - Math.round(this.scroll);
			List<Badges.Badge> badges = tab.badges();
			for (int i = 0; i < badges.size(); i += columns) {
				int rowHeight = rowHeight(badges, i);
				if (rowY + rowHeight > this.listTop && rowY < this.listBottom) {
					for (int column = 0; column < columns && i + column < badges.size(); column++) {
						Badges.Badge badge = badges.get(i + column);
						int cardX = x + column * (cardWidth + CARD_GAP);
						extractCard(graphics, badge, this.cards.get(badge), cardX, rowY, cardWidth, rowHeight,
								inList ? virtualX : Integer.MIN_VALUE, inList ? virtualY : Integer.MIN_VALUE);
					}
				}
				rowY += rowHeight + CARD_GAP;
			}
			graphics.disableScissor();
		}
		if (this.maxScroll > 0) {
			int barX = right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			int thumbY = thumbY();
			graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR);
		}
		if (this.hoverTooltip != null) {
			extractTooltip(graphics, this.hoverTooltip, virtualX, virtualY);
		}
		graphics.pose().popMatrix();
	}

	private static Component statusText() {
		return switch (Badges.status()) {
			case READY -> null;
			case NO_KEY -> Component.translatable("screen.mccinfo.badges.no_key");
			case LOADING -> Component.translatable("screen.mccinfo.badges.loading");
			case HIDDEN -> Component.translatable("screen.mccinfo.badges.hidden");
			case FAILED -> Component.translatable("screen.mccinfo.badges.failed");
		};
	}

	private void extractHeader(GuiGraphicsExtractor graphics, Component label, int x, int width, int y) {
		int textWidth = this.font.width(label);
		int lineLength = Math.max(0, (width - textWidth - HEADER_LABEL_GAP * 2) / 2);
		int textY = y + (HEADER_HEIGHT - this.font.lineHeight) / 2;
		int lineY = textY + this.font.lineHeight / 2 - 1;
		if (lineLength > 0) {
			graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_HEADER_LINE);
		}
		graphics.text(this.font, label, x + lineLength + HEADER_LABEL_GAP, textY, COLOR_HEADER_TEXT, true);
	}

	private void extractTab(GuiGraphicsExtractor graphics, PlacedTab placed, boolean selected, int mouseX, int mouseY) {
		int x = placed.x();
		int y = placed.y();
		boolean hovered = mouseX >= x && mouseX < x + placed.width() && mouseY >= y && mouseY < y + TAB_HEIGHT;
		graphics.fill(x, y, x + placed.width(), y + TAB_HEIGHT, selected ? SettingsPanel.ACCENT : COLOR_TAB_BORDER);
		graphics.fill(x + 1, y + 1, x + placed.width() - 1, y + TAB_HEIGHT - 1, hovered ? COLOR_TAB_HOVER : COLOR_TAB);
		int textX = x + TAB_PADDING;
		int textY = y + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1;
		Tab tab = placed.tab();
		if (!tab.icon().isEmpty()) {
			graphics.fakeItem(tab.icon(), textX - 2, y + (TAB_HEIGHT - ICON_SIZE) / 2);
			textX += ICON_SIZE + TAB_PADDING / 2;
		}
		int color = selected || hovered ? COLOR_WHITE : COLOR_TEXT;
		graphics.text(this.font, tab.title(), textX, textY, color, true);
		if (tab.counted()) {
			int done = tab.complete();
			String count = done + "/" + tab.badges().size();
			graphics.text(this.font, count, x + placed.width() - TAB_PADDING - this.font.width(count), textY,
					done == tab.badges().size() ? COLOR_DONE : COLOR_MUTED, true);
		}
		if (hovered && !selected) {
			NeuItems.pointer(graphics);
		}
	}

	// Badge card: icon, title, description and stages
	private void extractCard(GuiGraphicsExtractor graphics, Badges.Badge badge, Card card, int x, int y, int width, int height, int mouseX, int mouseY) {
		int completed = badge.completed();
		int border = badge.complete() ? COLOR_COMPLETE : !Badges.available(badge) ? COLOR_UNAVAILABLE : completed > 0 ? COLOR_PARTIAL : COLOR_NONE;
		int bottom = y + height;
		graphics.fill(x, y, x + width, bottom, border);
		graphics.fill(x + 1, y + 1, x + width - 1, bottom - 1, COLOR_CARD);
		graphics.fill(x + 1, bottom - STRIP_HEIGHT, x + width - 1, bottom - 1, COLOR_STRIP);

		int iconX = x + CARD_PADDING;
		int iconY = y + CARD_PADDING;
		graphics.fill(iconX, iconY, iconX + ICON_BOX, iconY + ICON_BOX, COLOR_ICON_BOX);
		ItemStack icon = Badges.icon(badge);
		if (icon.isEmpty()) {
			graphics.centeredText(this.font, Component.literal("?"), iconX + ICON_BOX / 2, iconY + (ICON_BOX - this.font.lineHeight) / 2 + 1, COLOR_MUTED);
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(iconX + (ICON_BOX - ICON_SIZE * 2) / 2.0F, iconY + (ICON_BOX - ICON_SIZE * 2) / 2.0F);
			graphics.pose().scale(2.0F, 2.0F);
			graphics.fakeItem(icon, 0, 0);
			graphics.pose().popMatrix();
		}
		int iconRight = x + width - CARD_PADDING;
		int iconTop = y + CARD_PADDING;
		List<Badges.Mode> modes = Badges.modesOf(badge);
		if (modes.isEmpty()) {
			for (ItemStack fallback : modeIcons(badge)) {
				graphics.fakeItem(fallback, iconRight - ICON_SIZE, iconTop);
			}
		}
		for (Badges.Mode mode : modes) {
			ItemStack modeIcon = Badges.modeIcon(mode);
			if (modeIcon.isEmpty()) {
				continue;
			}
			iconRight -= ICON_SIZE;
			graphics.fakeItem(modeIcon, iconRight, iconTop);
			if (mouseX >= iconRight && mouseX < iconRight + ICON_SIZE && mouseY >= iconTop && mouseY < iconTop + ICON_SIZE) {
				this.hoverTooltip = modeTooltip(mode);
			}
			iconRight -= MODE_ICON_GAP;
		}

		int textX = iconX + ICON_BOX + TEXT_GAP;
		int textY = y + CARD_PADDING;
		graphics.text(this.font, title(badge), textX, textY, COLOR_WHITE, true);
		textY += this.font.lineHeight + 3;
		if (!Badges.available(badge)) {
			graphics.text(this.font, EXPIRED, textX, textY, COLOR_WHITE, false);
			textY += TAG_HEIGHT;
		}
		for (FormattedCharSequence line : card.description()) {
			graphics.text(this.font, line, textX, textY, COLOR_MUTED, true);
			textY += LINE_HEIGHT;
		}
		textY += BLOCK_GAP;
		if (card.estimate() != null) {
			graphics.text(this.font, card.estimate(), textX, textY, COLOR_WHITE, true);
			textY += LINE_HEIGHT;
		}
		graphics.text(this.font, trophyLine(badge), textX, textY, COLOR_WHITE, true);

		int stripY = bottom - STRIP_HEIGHT;
		graphics.enableScissor(x + 1, stripY, x + width - 1, bottom - 1);
		extractStrip(graphics, badge, x + CARD_PADDING, stripY, mouseX, mouseY);
		graphics.disableScissor();
	}

	private static Component title(Badges.Badge badge) {
		MutableComponent title = Component.literal(badge.name()).withStyle(style -> style.withBold(true));
		int count = badge.stages().size();
		if (count <= 1) {
			if (badge.complete()) {
				title.withStyle(style -> style.withColor(COLOR_DONE & 0xFFFFFF));
			}
			return title;
		}
		int completed = badge.completed();
		title.append(": ");
		if (badge.complete()) {
			return title.append(Component.literal(roman(count)).withStyle(style -> style.withColor(COLOR_DONE & 0xFFFFFF)));
		}
		if (completed == 0) {
			return title.append(roman(1));
		}
		return title.append(roman(completed) + " → " + roman(completed + 1));
	}

	private static Component glyph(String glyph) {
		return Component.literal(glyph).withStyle(style -> style.withFont(MCC_ICON).withColor(0xFFFFFF).withBold(false));
	}

	private static MutableComponent trophies(int skill, int bonus, int color) {
		MutableComponent line = Component.empty();
		if (skill > 0 || bonus <= 0) {
			line.append(glyph(TROPHY)).append(" ").append(Component.literal(number(skill)).withStyle(style -> style.withColor(color & 0xFFFFFF).withBold(true)));
		}
		if (bonus > 0) {
			if (skill > 0) {
				line.append("  ");
			}
			line.append(glyph(BONUS_TROPHY)).append(" ").append(Component.literal(number(bonus)).withStyle(style -> style.withColor(color & 0xFFFFFF).withBold(true)));
		}
		return line;
	}

	private static Component trophyLine(Badges.Badge badge) {
		Badges.Stage current = badge.current();
		if (current == null) {
			int skill = 0;
			int bonus = 0;
			for (Badges.Stage stage : badge.stages()) {
				skill += stage.trophies();
				bonus += stage.bonusTrophies();
			}
			return trophies(skill, bonus, COLOR_DONE).append(" ").append(grey(Component.translatable("screen.mccinfo.badges.earned")));
		}
		return trophies(current.trophies(), current.bonusTrophies(), COLOR_WHITE).append(" ").append(grey(Component.translatable("screen.mccinfo.badges.next_tier")));
	}

	// Stages strip with their tooltips
	private void extractStrip(GuiGraphicsExtractor graphics, Badges.Badge badge, int x, int y, int mouseX, int mouseY) {
		int textY = y + (STRIP_HEIGHT - this.font.lineHeight) / 2 + 1;
		Badges.Stage current = badge.current();
		boolean first = true;
		for (Badges.Stage stage : badge.stages()) {
			if (!first) {
				Component separator = grey(Component.literal(SEPARATOR));
				graphics.text(this.font, separator, x, textY, COLOR_WHITE, true);
				x += this.font.width(separator);
			}
			first = false;
			Component part = stagePart(stage, stage == current);
			int partWidth = this.font.width(part);
			graphics.text(this.font, part, x, textY, COLOR_WHITE, true);
			if (mouseX >= x && mouseX < x + partWidth && mouseY >= y && mouseY < y + STRIP_HEIGHT) {
				this.hoverTooltip = stageTooltip(badge, stage);
			}
			x += partWidth;
		}
	}

	private static Component stagePart(Badges.Stage stage, boolean current) {
		if (stage.obtainable() <= 0) {
			return Component.literal(roman(stage.stage())).withStyle(style -> style.withColor(COLOR_MUTED & 0xFFFFFF));
		}
		if (stage.done()) {
			return Component.literal(compact(stage.obtainable())).withStyle(style -> style.withColor(COLOR_DONE & 0xFFFFFF));
		}
		if (current) {
			return Component.literal(number(stage.obtained())).withStyle(style -> style.withColor(COLOR_TODO & 0xFFFFFF))
					.append(grey(Component.literal("/" + compact(stage.obtainable()))));
		}
		return Component.literal(compact(stage.obtainable())).withStyle(style -> style.withColor(COLOR_TODO & 0xFFFFFF));
	}

	private static Component stageTooltip(Badges.Badge badge, Badges.Stage stage) {
		MutableComponent tooltip = Component.literal(badge.name() + (badge.stages().size() > 1 ? " " + roman(stage.stage()) : ""))
				.withStyle(style -> style.withBold(true).withColor((stage.done() ? COLOR_DONE : COLOR_TODO) & 0xFFFFFF));
		if (stage.obtainable() > 0) {
			tooltip.append("\n").append(grey(Component.literal(number(Math.min(stage.obtained(), stage.obtainable())) + "/" + number(stage.obtainable()))));
		}
		if (stage.trophies() > 0 || stage.bonusTrophies() <= 0) {
			tooltip.append("\n").append(glyph(TROPHY)).append(" ")
					.append(Component.literal(number(stage.trophies())).withStyle(style -> style.withColor(COLOR_WHITE & 0xFFFFFF)))
					.append(" ").append(grey(Component.translatable("screen.mccinfo.badges.trophies")));
		}
		if (stage.bonusTrophies() > 0) {
			tooltip.append("\n").append(glyph(BONUS_TROPHY)).append(" ")
					.append(Component.literal(number(stage.bonusTrophies())).withStyle(style -> style.withColor(COLOR_WHITE & 0xFFFFFF)))
					.append(" ").append(grey(Component.translatable("screen.mccinfo.badges.bonus_trophies")));
		}
		return tooltip;
	}

	private static String roman(int value) {
		return value >= 1 && value <= ROMAN.length ? ROMAN[value - 1] : String.valueOf(value);
	}

	private static String number(int value) {
		return String.format(Locale.US, "%,d", value);
	}

	private static String compact(int value) {
		if (value >= 1_000_000) {
			return trim(value / 1_000_000.0) + "M";
		}
		if (value >= 1_000) {
			return trim(value / 1_000.0) + "K";
		}
		return String.valueOf(value);
	}

	private static String trim(double value) {
		String text = String.format(Locale.US, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	private static Component grey(Component text) {
		return text.copy().withStyle(style -> style.withColor(COLOR_MUTED & 0xFFFFFF).withBold(false));
	}

	private void extractTooltip(GuiGraphicsExtractor graphics, Component text, int mouseX, int mouseY) {
		List<FormattedCharSequence> lines = this.font.split(text, TOOLTIP_MAX_WIDTH);
		if (lines.isEmpty()) {
			return;
		}
		int textWidth = 0;
		for (FormattedCharSequence line : lines) {
			textWidth = Math.max(textWidth, this.font.width(line));
		}
		int width = Math.round(textWidth * TOOLTIP_SCALE) + TOOLTIP_PADDING * 2;
		int height = Math.round(((lines.size() - 1) * TOOLTIP_LINE + this.font.lineHeight - 1) * TOOLTIP_SCALE) + TOOLTIP_PADDING * 2;
		int screenWidth = (int) (this.width / this.scale);
		int screenHeight = (int) (this.height / this.scale);
		int x = mouseX + TOOLTIP_OFFSET;
		int y = mouseY - height - TOOLTIP_OFFSET / 2;
		if (x + width > screenWidth - TOOLTIP_MARGIN) {
			x = mouseX - TOOLTIP_OFFSET - width;
		}
		if (y < TOOLTIP_MARGIN) {
			y = mouseY + TOOLTIP_OFFSET * 2;
		}
		x = Math.max(TOOLTIP_MARGIN, Math.min(x, screenWidth - TOOLTIP_MARGIN - width));
		y = Math.max(TOOLTIP_MARGIN, Math.min(y, screenHeight - TOOLTIP_MARGIN - height));
		graphics.nextStratum();
		graphics.fill(x, y, x + width, y + height, COLOR_TOOLTIP_BORDER);
		graphics.fill(x + TOOLTIP_BORDER, y + TOOLTIP_BORDER, x + width - TOOLTIP_BORDER, y + height - TOOLTIP_BORDER, COLOR_TOOLTIP_BACKGROUND);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + TOOLTIP_PADDING, y + TOOLTIP_PADDING);
		graphics.pose().scale(TOOLTIP_SCALE, TOOLTIP_SCALE);
		int lineY = 0;
		for (FormattedCharSequence line : lines) {
			graphics.text(this.font, line, 0, lineY, COLOR_WHITE, true);
			lineY += TOOLTIP_LINE;
		}
		graphics.pose().popMatrix();
	}

	// Smooth scroll and scrollbar
	private void updateScroll() {
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		if (this.draggingScrollbar || Math.abs(this.scrollTarget - this.scroll) < 0.5F) {
			this.scroll = this.scrollTarget;
		} else {
			this.scroll += (this.scrollTarget - this.scroll) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
	}

	private int trackHeight() {
		return this.top + this.panelHeight - BORDER - this.listTop;
	}

	private int thumbHeight() {
		int trackHeight = trackHeight();
		return Math.max(MIN_THUMB, trackHeight * trackHeight / (trackHeight + this.maxScroll));
	}

	private int thumbY() {
		return this.listTop + Math.round((trackHeight() - thumbHeight()) * this.scroll / this.maxScroll);
	}

	private void dragScrollbar(double y) {
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			this.scrollTarget = Mth.clamp((float) ((y - this.dragOffset - this.listTop) * this.maxScroll / range), 0.0F, this.maxScroll);
			this.scroll = this.scrollTarget;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		int right = this.left + this.panelWidth;
		if (this.maxScroll > 0 && x >= right - BORDER - SCROLLBAR_GRAB && x < right && y >= this.listTop && y < this.top + this.panelHeight - BORDER) {
			int thumbY = thumbY();
			int thumbHeight = thumbHeight();
			this.dragOffset = y >= thumbY && y < thumbY + thumbHeight ? y - thumbY : thumbHeight / 2.0;
			this.draggingScrollbar = true;
			dragScrollbar(y);
			return true;
		}
		for (PlacedTab placed : this.placedTabs) {
			if (x >= placed.x() && x < placed.x() + placed.width() && y >= placed.y() && y < placed.y() + TAB_HEIGHT) {
				if (!placed.tab().id().equals(selectedTab)) {
					selectedTab = placed.tab().id();
					this.scroll = 0.0F;
					this.scrollTarget = 0.0F;
					NeuItems.clickSound();
				}
				return true;
			}
		}
		boolean inPanel = x >= this.left && x < right && y >= this.top && y < this.top + this.panelHeight;
		if (!inPanel) {
			this.onClose();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingScrollbar) {
			dragScrollbar(event.y() / this.scale);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.draggingScrollbar) {
			this.draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.maxScroll > 0 && scrollY != 0) {
			this.scrollTarget = Mth.clamp(this.scrollTarget - (float) scrollY * SCROLL_STEP, 0.0F, this.maxScroll);
		}
		return true;
	}

	@Override
	public void tick() {
		if (!Badges.allowed()) {
			this.onClose();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
