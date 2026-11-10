package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.ItemIcons;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Fish Collection menu of the mod
public final class FishCollectionScreen extends Screen implements MenuBackdrop.Overlay {
	// Layout
	private static final int MARGIN = 24;
	private static final int MAX_WIDTH = 480;
	private static final int MAX_HEIGHT = 420;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int STATUS_HEIGHT = 14;
	private static final int SECTION_HEIGHT = 20;
	private static final int SECTION_GAP = 6;
	private static final int TILE_HEIGHT = 24;
	private static final int TILE_GAP = 4;
	private static final int TILE_PADDING = 4;
	private static final int ICON_SIZE = 16;
	private static final int ARROW_SIZE = 7;
	private static final int COLUMNS = FishCollection.Time.values().length;
	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final int SCROLLBAR_GRAB = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;

	// Colors
	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_SECTION = 0xFF232323;
	private static final int COLOR_SECTION_HOVER = 0xFF2A2A2A;
	private static final int COLOR_ROW = 0xFF202020;
	private static final int COLOR_ROW_HOVER = 0xFF2A2A2A;
	private static final int COLOR_ROW_BORDER = 0xFF3A3A3A;
	private static final int COLOR_COMPLETE = 0xFF2F7A2F;
	private static final int COLOR_PARTIAL = 0xFF8A7A2F;
	private static final int COLOR_NONE = 0xFF8A2F2F;
	private static final float TOOLTIP_SCALE = 1.5F;
	private static final float REVEAL_MILLIS = 60.0F;
	// Summary rings
	private static final int STATS_HEIGHT = 40;
	private static final int RING_RADIUS = 15;
	private static final int RING_THICKNESS = 3;
	private static final int RING_MARGIN = 6;
	private static final int COLOR_RING = SettingsPanel.ACCENT;
	private static final int COLOR_RING_TRACK = 0xFF333333;
	private static final String FISH_ICON = "Forest Cod";
	private static final String ELUSIVE_LURE = "A.N.G.L.R. Elusive Lure";
	private static final String UNKNOWN = "?";
	private static final String UNKNOWN_BAIT = "x" + UNKNOWN;
	// Tooltip
	private static final int TOOLTIP_LINE = 12;
	private static final int TOOLTIP_PADDING = 7;
	private static final int TOOLTIP_BORDER = 2;
	private static final int TOOLTIP_OFFSET = 8;
	private static final int TOOLTIP_MARGIN = 4;
	private static final int TOOLTIP_MAX_WIDTH = 200;
	private static final int COLOR_TOOLTIP_BORDER = 0xFF3A3A3A;
	private static final int COLOR_TOOLTIP_BACKGROUND = 0xFF202020;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_MUTED = 0xFF8C8C8C;
	private static final int COLOR_CAUGHT = 0xFF55FF55;
	private static final int COLOR_MISSING = 0xFFFF5555;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;
	private static final int COLOR_BEST_NOW = 0xFF55FF55;
	private static final int COLOR_BEST_NEXT = 0xFFFFFF55;

	// Gear panel
	private static final int SIDE_WIDTH = 172;
	private static final int SIDE_GAP = 6;
	private static final int SLOT = 18;
	private static final int LABEL_HEIGHT = 11;
	private static final int ROW_GAP = 4;
	private static final int GRID_CELL = 20;
	private static final long BEST_REFRESH_MILLIS = 1_000L;
	// Glyphs
	private static final FontDescription MCC_ICON = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcc", "icon"));
	private static final FontDescription MOD_ICON = new FontDescription.Resource(FishIconRegistry.ICON_FONT);
	private static final String SUN = "";
	private static final String MOON = "";
	private static final String TROPHY = "";
	private static final String ELUSIVE_FISH_CHANCE = "elusive fish chance";
	private static final java.util.regex.Pattern SELECTED_BONUS = java.util.regex.Pattern.compile("^\\+([\\d.]+%?)\\s+(.+)$");
	private static final int[] FAMILY_COLORS = {0xFF5555, 0x219BF3, 0x8833FF, 0xFF7E40, 0x23C725};
	private static final java.util.Map<String, String> PERK_GLYPHS = java.util.Map.ofEntries(
			java.util.Map.entry("strong hook", ""), java.util.Map.entry("wise hook", ""), java.util.Map.entry("glimmering hook", ""),
			java.util.Map.entry("greedy hook", ""), java.util.Map.entry("lucky hook", ""),
			java.util.Map.entry("xp magnet", ""), java.util.Map.entry("fish magnet", ""), java.util.Map.entry("pearl magnet", ""),
			java.util.Map.entry("treasure magnet", ""), java.util.Map.entry("spirit magnet", ""),
			java.util.Map.entry("boosted rod", ""), java.util.Map.entry("speedy rod", ""), java.util.Map.entry("graceful rod", ""),
			java.util.Map.entry("glitched rod", ""), java.util.Map.entry("stable rod", ""),
			java.util.Map.entry("strong pot", ""), java.util.Map.entry("wise pot", ""), java.util.Map.entry("glimmering pot", ""),
			java.util.Map.entry("greedy pot", ""), java.util.Map.entry("lucky pot", ""),
			java.util.Map.entry("elusive chance", ""), java.util.Map.entry(ELUSIVE_FISH_CHANCE, ""), java.util.Map.entry("wayfinder data", ""),
			java.util.Map.entry("pearl chance", ""), java.util.Map.entry("treasure chance", ""), java.util.Map.entry("spirit chance", ""));
	private static final String CRAB_TAG = "";
	private static final java.util.Map<FishCollection.Rarity, String> RARITY_GLYPHS = java.util.Map.of(
			FishCollection.Rarity.COMMON, "", FishCollection.Rarity.UNCOMMON, "", FishCollection.Rarity.RARE, "",
			FishCollection.Rarity.EPIC, "", FishCollection.Rarity.LEGENDARY, "", FishCollection.Rarity.MYTHIC, "");
	private static final java.util.Map<FishCollection.Time, String> TIME_TAGS = java.util.Map.of(
			FishCollection.Time.DAY, "", FishCollection.Time.NIGHT, "", FishCollection.Time.ELUSIVE, "");
	private static final java.util.Map<FishCollection.Weight, String> STAR_GLYPHS = java.util.Map.of(
			FishCollection.Weight.AVERAGE, "", FishCollection.Weight.LARGE, "", FishCollection.Weight.MASSIVE, "",
			FishCollection.Weight.GARGANTUAN, "", FishCollection.Weight.COLOSSAL, "");
	private static final String[][] PERK_GRID = {
			{"strong hook", "xp magnet", "boosted rod", "strong pot", "elusive chance"},
			{"wise hook", "fish magnet", "speedy rod", "wise pot", "wayfinder data"},
			{"glimmering hook", "pearl magnet", "graceful rod", "glimmering pot", "pearl chance"},
			{"greedy hook", "treasure magnet", "glitched rod", "greedy pot", "treasure chance"},
			{"lucky hook", "spirit magnet", "stable rod", "lucky pot", "spirit chance"},
	};

	private record Overlay(String text, int x, int y, int color) {
	}

	private record Durability(int x, int y, int remaining, int max) {
	}

	// Island or crab section, one row per rarity
	private record Section(String title, String iconKey, boolean crab, List<FishCollection.Fish[]> rows) {
		int caught() {
			int count = 0;
			for (FishCollection.Fish[] row : this.rows) {
				for (FishCollection.Fish fish : row) {
					if (fish != null) {
						for (FishCollection.Weight weight : fish.weights()) {
							count += FishCollection.isCaught(fish, weight) ? 1 : 0;
						}
					}
				}
			}
			return count;
		}

		int total() {
			int count = 0;
			for (FishCollection.Fish[] row : this.rows) {
				for (FishCollection.Fish fish : row) {
					count += fish == null ? 0 : fish.weights().size();
				}
			}
			return count;
		}
	}

	private final Screen parent;
	private final List<Section> sections = new ArrayList<>();
	private List<FishCollection.Fish> builtFrom;
	private float scale = 1.0F;
	private int sideLeft;
	private final java.util.Map<String, Float> reveals = new java.util.HashMap<>();
	private long revealFrame;
	private Set<String> bestNow = Set.of();
	private Set<String> bestNext = Set.of();
	private Set<String> bestElusive = Set.of();
	private boolean bestNight;
	private long bestAt;
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

	public FishCollectionScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.events.fish_collection"));
		this.parent = parent;
		FishCollection.refreshOnOpen();
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
		this.panelWidth = Math.min(MAX_WIDTH, virtualWidth - MARGIN * 2 - SIDE_WIDTH - SIDE_GAP);
		this.panelHeight = Math.min(MAX_HEIGHT, virtualHeight - MARGIN * 2);
		this.sideLeft = (virtualWidth - SIDE_WIDTH - SIDE_GAP - this.panelWidth) / 2;
		this.left = this.sideLeft + SIDE_WIDTH + SIDE_GAP;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.listBottom = this.top + this.panelHeight - BORDER - PADDING;
		updateListTop();
	}

	private void updateListTop() {
		boolean status = FishCollection.status() != FishCollection.Status.READY;
		this.listTop = this.top + BORDER + PADDING + HEADER_HEIGHT + 2 + (status ? STATUS_HEIGHT : 0);
	}

	// Sections per island, fish in rows by rarity
	private void buildSections() {
		List<FishCollection.Fish> fish = FishCollection.fish();
		if (fish == this.builtFrom) {
			return;
		}
		this.builtFrom = fish;
		this.sections.clear();
		Section section = null;
		FishCollection.Fish[] row = null;
		FishCollection.Rarity rowRarity = null;
		int next = 0;
		for (FishCollection.Fish entry : fish) {
			String title = entry.crab() ? entry.island() + " Crabs" : entry.island();
			if (section == null || !section.title().equals(title)) {
				String iconKey = entry.crab() ? BestIsland.crabIconKey(entry.island()) : entry.island().toLowerCase(Locale.ROOT);
				section = new Section(title, iconKey, entry.crab(), new ArrayList<>());
				this.sections.add(section);
				row = null;
			}
			int column = entry.crab() ? next : entry.time().ordinal();
			if (row == null || entry.rarity() != rowRarity || column >= COLUMNS || row[column] != null) {
				row = new FishCollection.Fish[COLUMNS];
				section.rows().add(row);
				rowRarity = entry.rarity();
				next = 0;
				column = entry.crab() ? 0 : column;
			}
			row[column] = entry;
			next = column + 1;
		}
	}

	private static boolean isCollapsed(Section section) {
		return ModConfig.get().collapsedSections.contains(section.title());
	}

	// Open and close animation of the sections
	private float reveal(Section section) {
		return this.reveals.computeIfAbsent(section.title(), title -> isCollapsed(section) ? 0.0F : 1.0F);
	}

	private void updateReveals() {
		long now = Util.getMillis();
		long elapsed = this.revealFrame == 0 ? 0 : now - this.revealFrame;
		this.revealFrame = now;
		float step = 1.0F - (float) Math.exp(-elapsed / REVEAL_MILLIS);
		for (Section section : this.sections) {
			float target = isCollapsed(section) ? 0.0F : 1.0F;
			float value = reveal(section);
			value += (target - value) * step;
			this.reveals.put(section.title(), Math.abs(target - value) < 0.01F ? target : value);
		}
	}

	private static int bodyHeight(Section section) {
		if (section.rows().isEmpty()) {
			return 0;
		}
		return TILE_GAP + STATS_HEIGHT + TILE_GAP + section.rows().size() * (TILE_HEIGHT + TILE_GAP);
	}

	private int sectionHeight(Section section) {
		return SECTION_HEIGHT + Math.round(bodyHeight(section) * reveal(section));
	}

	private int contentHeight() {
		int height = 0;
		for (Section section : this.sections) {
			height += sectionHeight(section) + SECTION_GAP;
		}
		return Math.max(0, height - SECTION_GAP);
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
		buildSections();
		updateReveals();
		updateBest();
		updateListTop();
		updateScroll();
		int virtualX = (int) (mouseX / this.scale);
		int virtualY = (int) (mouseY / this.scale);
		this.maxScroll = Math.max(0, contentHeight() - (this.listBottom - this.listTop));
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
		Component header = Component.empty().append(this.title).append(" " + FishCollection.caughtCount() + "/" + FishCollection.totalCount());
		extractHeader(graphics, header, x, width, this.top + BORDER + PADDING);
		Component status = statusText();
		if (status != null) {
			int statusY = this.top + BORDER + PADDING + HEADER_HEIGHT + 2;
			graphics.centeredText(this.font, Component.literal(this.font.substrByWidth(status, width).getString()), x + width / 2, statusY,
					FishCollection.status() == FishCollection.Status.LOADING ? COLOR_MUTED : COLOR_MISSING);
		}

		boolean inList = virtualY >= this.listTop && virtualY < this.listBottom && virtualX >= x && virtualX < x + width;
		Component tooltip = null;
		graphics.enableScissor(this.left + BORDER, this.listTop, right - BORDER, this.listBottom);
		int y = this.listTop - Math.round(this.scroll);
		if (this.sections.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("screen.mccinfo.fish_collection.empty"), x + width / 2, y + SECTION_GAP, COLOR_MUTED);
		}
		for (Section section : this.sections) {
			int height = sectionHeight(section);
			if (y + height > this.listTop && y < this.listBottom) {
				boolean hovered = inList && virtualY >= y && virtualY < y + SECTION_HEIGHT;
				extractSection(graphics, section, x, y, width, hovered);
				float shown = reveal(section);
				if (shown > 0.0F && height > SECTION_HEIGHT) {
					int clipTop = y + SECTION_HEIGHT;
					int clipBottom = y + height;
					int bodyY = clipTop;
					boolean interactive = shown >= 1.0F && inList;
					graphics.enableScissor(this.left + BORDER, clipTop, right - BORDER, clipBottom);
					extractStats(graphics, section, x, bodyY + TILE_GAP, width);
					int tileWidth = (width - TILE_GAP * (COLUMNS - 1)) / COLUMNS;
					int rowY = bodyY + TILE_GAP + STATS_HEIGHT + TILE_GAP;
					for (FishCollection.Fish[] row : section.rows()) {
						for (int column = 0; column < COLUMNS; column++) {
							FishCollection.Fish fish = row[column];
							if (fish == null) {
								continue;
							}
							int tileX = x + column * (tileWidth + TILE_GAP);
							boolean over = interactive && virtualX >= tileX && virtualX < tileX + tileWidth && virtualY >= rowY && virtualY < rowY + TILE_HEIGHT;
							extractTile(graphics, fish, tileX, rowY, tileWidth, over);
							if (over) {
								tooltip = tooltip(fish);
							}
						}
						rowY += TILE_HEIGHT + TILE_GAP;
					}
					graphics.disableScissor();
				}
			}
			y += height + SECTION_GAP;
		}
		graphics.disableScissor();
		if (this.maxScroll > 0) {
			int barX = right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			int thumbY = thumbY();
			graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR);
		}
		Component gearTooltip = extractGear(graphics, virtualX, virtualY);
		if (gearTooltip != null) {
			tooltip = gearTooltip;
		}
		if (tooltip != null) {
			extractTooltip(graphics, tooltip, virtualX, virtualY);
		}
		graphics.pose().popMatrix();
	}

	private static Component statusText() {
		return switch (FishCollection.status()) {
			case READY -> null;
			case NO_KEY -> Component.translatable("screen.mccinfo.fish_collection.no_key");
			case LOADING -> Component.translatable("screen.mccinfo.fish_collection.loading");
			case HIDDEN -> Component.translatable("screen.mccinfo.fish_collection.hidden");
			case FAILED -> Component.translatable("screen.mccinfo.fish_collection.failed");
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

	// Section bar: icon, title, count and arrow
	private void extractSection(GuiGraphicsExtractor graphics, Section section, int x, int y, int width, boolean hovered) {
		boolean collapsed = isCollapsed(section);
		graphics.fill(x, y, x + width, y + SECTION_HEIGHT, hovered ? COLOR_SECTION_HOVER : COLOR_SECTION);
		int color = hovered ? COLOR_WHITE : collapsed ? COLOR_MUTED : COLOR_TEXT;
		int textY = y + (SECTION_HEIGHT - this.font.lineHeight) / 2 + 1;
		int itemX = x + TILE_PADDING;
		int arrowX = x + width - TILE_PADDING - ARROW_SIZE;
		extractArrow(graphics, arrowX, y + (SECTION_HEIGHT - ARROW_SIZE) / 2, reveal(section), color);
		// Island or crab pot icon
		ItemStack icon = section.iconKey() == null ? ItemStack.EMPTY : IslandIcons.stack(section.iconKey());
		if (!icon.isEmpty()) {
			graphics.fakeItem(icon, itemX, y + (SECTION_HEIGHT - ICON_SIZE) / 2);
			itemX += ICON_SIZE + TILE_PADDING;
		}
		Component title = sectionTitle(section);
		graphics.text(this.font, title, itemX, textY, color, true);
		// Elusive Lure on the best elusive island
		if (!section.crab() && this.bestElusive.contains(section.title().toLowerCase(Locale.ROOT))) {
			ItemStack lure = NeuItems.stackByName(ELUSIVE_LURE);
			if (!lure.isEmpty()) {
				graphics.fakeItem(lure, itemX + this.font.width(title) + TILE_PADDING, y + (SECTION_HEIGHT - ICON_SIZE) / 2);
			}
		}
		int caught = section.caught();
		int total = section.total();
		String count = caught + "/" + total;
		graphics.text(this.font, count, arrowX - TILE_PADDING - this.font.width(count), textY, caught == total && total > 0 ? COLOR_CAUGHT : COLOR_TEXT, true);
		if (hovered) {
			NeuItems.pointer(graphics);
		}
	}

	// Best islands now, next and for elusive fish
	private void updateBest() {
		long now = Util.getMillis();
		if (now - this.bestAt < BEST_REFRESH_MILLIS && this.bestNight == BestIsland.night()) {
			return;
		}
		this.bestAt = now;
		this.bestNight = BestIsland.night();
		this.bestNow = BestIsland.bestIslands(this.bestNight ? FishCollection.Time.NIGHT : FishCollection.Time.DAY);
		this.bestNext = BestIsland.bestIslands(this.bestNight ? FishCollection.Time.DAY : FishCollection.Time.NIGHT);
		this.bestElusive = BestIsland.bestElusiveIslands();
	}

	private Component sectionTitle(Section section) {
		MutableComponent title = Component.literal(section.title()).withStyle(style -> style.withBold(true));
		if (section.crab()) {
			return title;
		}
		String island = section.title().toLowerCase(Locale.ROOT);
		boolean now = this.bestNow.contains(island);
		boolean next = this.bestNext.contains(island);
		String current = this.bestNight ? MOON : SUN;
		String other = this.bestNight ? SUN : MOON;
		if (now) {
			title.withStyle(style -> style.withColor(COLOR_BEST_NOW & 0xFFFFFF));
			title.append(" ").append(glyph(next ? current + other : current));
		} else if (next) {
			title.withStyle(style -> style.withColor(COLOR_BEST_NEXT & 0xFFFFFF));
			title.append(" ").append(glyph(other));
		}
		return title;
	}

	private static Component glyph(String glyphs) {
		return Component.literal(glyphs).withStyle(style -> style.withFont(MCC_ICON).withColor(0xFFFFFF).withBold(false));
	}

	private static Component tr(String key, Object... args) {
		return Component.translatable("screen.mccinfo.fish_collection." + key, args);
	}

	private static Component grey(Component text) {
		return text.copy().withStyle(style -> style.withColor(COLOR_MUTED & 0xFFFFFF));
	}

	private static String words(String key) {
		StringBuilder result = new StringBuilder();
		for (String word : key.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(word.equals("xp") ? "XP" : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}
		return result.toString();
	}

	private static String number(int value) {
		return String.format(Locale.US, "%,d", value);
	}

	// Perks, overclocks, bait, line and augments
	private Component extractGear(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int right = this.sideLeft + SIDE_WIDTH;
		int bottom = this.top + this.panelHeight;
		graphics.fill(this.sideLeft, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.sideLeft + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		int x = this.sideLeft + BORDER + PADDING;
		int width = SIDE_WIDTH - BORDER * 2 - PADDING * 2;
		int y = this.top + BORDER + PADDING;
		ModConfig config = ModConfig.get();
		List<Overlay> overlays = new ArrayList<>();
		List<Durability> bars = new ArrayList<>();
		Component tooltip = null;
		graphics.enableScissor(this.sideLeft + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER);

		extractHeader(graphics, tr("equipment"), x, width, y);
		y += HEADER_HEIGHT + ROW_GAP;
		boolean noGear = config.bait == null && config.line == null && config.augments.isEmpty() && config.overclocks.isEmpty();
		if (noGear) {
			y = extractHint(graphics, tr("no_gear"), x, width, y);
		} else {
			graphics.text(this.font, tr("overclocks"), x, y, COLOR_MUTED, false);
			y += LABEL_HEIGHT;
			int itemX = x;
			for (String overclock : BestIsland.OVERCLOCKS) {
				String selected = config.overclocks.get(overclock);
				int level = BestIsland.perk(overclock);
				if (selected == null && level == 0) {
					continue;
				}
				ItemStack icon = selected == null || overclock.equals(BestIsland.UNSTABLE_OVERCLOCK) ? ItemStack.EMPTY : ItemIcons.stack(selected);
				if (icon.isEmpty()) {
					icon = ItemIcons.stack(overclock);
				}
				extractIcon(graphics, icon, itemX, y);
				overlays.add(new Overlay(String.valueOf(level), itemX, y, COLOR_WHITE));
				if (isOver(mouseX, mouseY, itemX, y)) {
					tooltip = overclockTooltip(overclock, selected, level);
				}
				itemX += SLOT;
			}
			if (itemX == x) {
				graphics.text(this.font, "-", x, y + 5, COLOR_MUTED, false);
			}
			y += SLOT + ROW_GAP;

			Integer baitUses = config.baitUses;
			Component baitTooltip = extractSupply(graphics, bars, tr("bait"), config.bait, baitUses == null ? UNKNOWN_BAIT : "x" + number(baitUses),
					baitUses != null && baitUses <= 0, null, config.baitBonuses, x, width, y, mouseX, mouseY);
			tooltip = baitTooltip == null ? tooltip : baitTooltip;
			y += LABEL_HEIGHT + SLOT + ROW_GAP;
			Integer lineUses = config.lineUses;
			String lineText = lineUses == null ? UNKNOWN : number(lineUses) + (config.lineMaxUses == null ? "" : "/" + number(config.lineMaxUses));
			int[] lineDurability = lineUses == null || config.lineMaxUses == null || config.lineMaxUses <= 0 ? null : new int[]{lineUses, config.lineMaxUses};
			Component lineTooltip = extractSupply(graphics, bars, tr("line"), config.line, lineText,
					lineUses != null && lineUses <= 0, lineDurability, config.lineBonuses, x, width, y, mouseX, mouseY);
			tooltip = lineTooltip == null ? tooltip : lineTooltip;
			y += LABEL_HEIGHT + SLOT + ROW_GAP;

			graphics.text(this.font, tr("augments"), x, y, COLOR_MUTED, false);
			y += LABEL_HEIGHT;
			int perRow = Math.max(1, width / SLOT);
			for (int i = 0; i < config.augments.size(); i++) {
				String augment = config.augments.get(i);
				String uses = i < config.augmentUses.size() ? config.augmentUses.get(i) : "";
				int augmentX = x + i % perRow * SLOT;
				int augmentY = y + i / perRow * SLOT;
				extractIcon(graphics, ItemIcons.stack(augment), augmentX, augmentY);
				int slash = uses.indexOf('/');
				if (slash > 0) {
					try {
						int max = Integer.parseInt(uses.substring(slash + 1).replace(",", ""));
						if (max > 0) {
							bars.add(new Durability(augmentX, augmentY, Integer.parseInt(uses.substring(0, slash).replace(",", "")), max));
						}
					} catch (NumberFormatException ignored) {
					}
				}
				if (isOver(mouseX, mouseY, augmentX, augmentY)) {
					MutableComponent text = Component.literal(words(augment));
					if (!uses.isEmpty()) {
						text.append("\n").append(grey(tr("uses", uses)));
					}
					if (i < config.augmentBonuses.size() && config.augmentBonuses.get(i) != null) {
						appendBonuses(text, config.augmentBonuses.get(i));
					}
					tooltip = text;
				}
			}
			if (config.augments.isEmpty()) {
				graphics.text(this.font, "-", x, y + 5, COLOR_MUTED, false);
			}
			y += Math.max(1, (config.augments.size() + perRow - 1) / perRow) * SLOT + ROW_GAP;
		}

		y += ROW_GAP;
		extractHeader(graphics, tr("perks"), x, width, y);
		y += HEADER_HEIGHT + ROW_GAP;
		if (config.perks.isEmpty()) {
			extractHint(graphics, tr("no_perks"), x, width, y);
		} else {
			int gridX = x + (width - PERK_GRID[0].length * GRID_CELL + 2) / 2;
			for (String[] row : PERK_GRID) {
				int cellX = gridX;
				for (String perk : row) {
					ItemStack icon = ItemIcons.stack(perk);
					if (icon.isEmpty()) {
						graphics.centeredText(this.font, Component.literal("?"), cellX + ICON_SIZE / 2, y + 5, COLOR_MUTED);
					} else {
						graphics.fakeItem(icon, cellX, y);
						overlays.add(new Overlay(String.valueOf(BestIsland.total(perk)), cellX, y, COLOR_WHITE));
					}
					if (isOver(mouseX, mouseY, cellX, y)) {
						tooltip = perkTooltip(perk, !icon.isEmpty());
					}
					cellX += GRID_CELL;
				}
				y += GRID_CELL;
			}
		}

		graphics.nextStratum();
		for (Overlay overlay : overlays) {
			graphics.text(this.font, overlay.text(), overlay.x() + ICON_SIZE + 1 - this.font.width(overlay.text()), overlay.y() + ICON_SIZE - 7, overlay.color(), true);
		}
		for (Durability bar : bars) {
			float left = Mth.clamp((float) bar.remaining() / bar.max(), 0.0F, 1.0F);
			int filled = Math.round(13.0F * left);
			graphics.fill(bar.x() + 2, bar.y() + 13, bar.x() + 15, bar.y() + 15, 0xFF000000);
			graphics.fill(bar.x() + 2, bar.y() + 13, bar.x() + 2 + filled, bar.y() + 14, 0xFF000000 | Mth.hsvToRgb(left / 3.0F, 1.0F, 1.0F));
		}
		graphics.disableScissor();
		return tooltip;
	}

	private int extractHint(GuiGraphicsExtractor graphics, Component text, int x, int width, int y) {
		for (net.minecraft.util.FormattedCharSequence line : this.font.split(text, width)) {
			graphics.text(this.font, line, x, y, COLOR_MUTED, false);
			y += LABEL_HEIGHT;
		}
		return y + ROW_GAP;
	}

	private void extractIcon(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y) {
		if (icon.isEmpty()) {
			graphics.centeredText(this.font, Component.literal("?"), x + ICON_SIZE / 2, y + 5, COLOR_MUTED);
		} else {
			graphics.fakeItem(icon, x, y);
		}
	}

	private Component extractSupply(GuiGraphicsExtractor graphics, List<Durability> bars, Component label, String name, String count, boolean empty,
			int[] durability, java.util.Map<String, Integer> bonuses, int x, int width, int y, int mouseX, int mouseY) {
		graphics.text(this.font, label, x, y, COLOR_MUTED, false);
		y += LABEL_HEIGHT;
		if (name == null) {
			graphics.text(this.font, "-", x, y + 5, COLOR_MUTED, false);
			return null;
		}
		extractIcon(graphics, ItemIcons.stack(name), x, y);
		int textY = y + (SLOT - this.font.lineHeight) / 2 + 1;
		if (durability != null) {
			bars.add(new Durability(x, y, durability[0], durability[1]));
		}
		String shownCount = durability == null ? count : null;
		int countWidth = shownCount == null ? 0 : this.font.width(shownCount);
		if (shownCount != null) {
			graphics.text(this.font, count, x + width - countWidth, textY, empty ? COLOR_MISSING : count.endsWith(UNKNOWN) ? COLOR_MUTED : COLOR_WHITE, true);
		}
		int nameX = x + SLOT + 2;
		String shown = this.font.plainSubstrByWidth(words(name), Math.max(0, x + width - countWidth - ROW_GAP - nameX));
		graphics.text(this.font, shown, nameX, textY, empty ? COLOR_MUTED : COLOR_TEXT, true);
		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + SLOT) {
			return null;
		}
		MutableComponent tooltip = Component.literal(words(name));
		if (count != null) {
			tooltip.append("\n").append(grey(tr("remaining", count)));
		}
		if (count != null && count.endsWith(UNKNOWN)) {
			tooltip.append("\n").append(grey(tr("open_supplies")));
		}
		appendBonuses(tooltip, bonuses);
		return tooltip;
	}

	private static Component overclockTooltip(String overclock, String selected, int level) {
		MutableComponent tooltip = Component.literal(words(overclock)).append(" ").append(tr("level", level));
		if (selected != null) {
			java.util.regex.Matcher bonus = SELECTED_BONUS.matcher(selected);
			if (bonus.matches()) {
				tooltip.append("\n").append(bonusLine(bonus.group(1), bonus.group(2).trim()));
			} else {
				tooltip.append("\n").append(bonusLine(String.valueOf(level), selected));
			}
		}
		return tooltip;
	}

	private static void appendBonuses(MutableComponent tooltip, java.util.Map<String, Integer> bonuses) {
		bonuses.entrySet().stream()
				.sorted(java.util.Comparator.comparingInt((java.util.Map.Entry<String, Integer> bonus) -> family(bonus.getKey())).thenComparing(java.util.Map.Entry::getKey))
				.forEach(bonus -> tooltip.append("\n").append(bonusLine(String.valueOf(bonus.getValue()), bonus.getKey())));
	}

	private static Component bonusLine(String value, String perk) {
		MutableComponent line = Component.empty();
		String glyph = PERK_GLYPHS.get(perk);
		if (glyph != null) {
			line.append(glyph(glyph, MCC_ICON, 0xFFFFFF));
		}
		int family = family(perk);
		int color = family < 0 ? 0xFFFFFF : FAMILY_COLORS[family];
		return line.append(Component.literal("+" + value).withStyle(style -> style.withColor(0xFFFFFF)))
				.append(Component.literal(" " + words(perk)).withStyle(style -> style.withColor(color)));
	}

	private static int family(String perk) {
		if (perk.equals(ELUSIVE_FISH_CHANCE)) {
			return 0;
		}
		for (int row = 0; row < PERK_GRID.length; row++) {
			for (String name : PERK_GRID[row]) {
				if (name.equals(perk)) {
					return row;
				}
			}
		}
		return -1;
	}

	private static Component perkTooltip(String perk, boolean known) {
		MutableComponent tooltip = Component.empty();
		String glyph = PERK_GLYPHS.get(perk);
		if (glyph != null) {
			tooltip.append(glyph(glyph, MCC_ICON, 0xFFFFFF)).append(" ");
		}
		int family = family(perk);
		tooltip.append(Component.literal(words(perk)).withStyle(style -> style.withColor(family < 0 ? 0xFFFFFF : FAMILY_COLORS[family])));
		if (!known) {
			return tooltip.append("\n").append(grey(tr("perk_unknown")));
		}
		tooltip.append("\n").append(grey(tr("level", BestIsland.perk(perk))));
		int overclock = BestIsland.overclockBonus(perk);
		int equipment = BestIsland.equipment(perk);
		if (overclock > 0) {
			tooltip.append("\n").append(grey(tr("perk_overclock", overclock)));
		}
		if (equipment > 0) {
			tooltip.append("\n").append(grey(tr("perk_equipment", equipment)));
		}
		return tooltip.append("\n").append(tr("perk_total", BestIsland.total(perk)).copy().withStyle(style -> style.withColor(COLOR_CAUGHT & 0xFFFFFF)));
	}

	private static boolean isOver(int mouseX, int mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE;
	}

	private static void extractArrow(GuiGraphicsExtractor graphics, int x, int y, float turn, int color) {
		int half = ARROW_SIZE / 2;
		float center = ARROW_SIZE / 2.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + center, y + center);
		graphics.pose().rotate((float) (-Math.PI / 2.0 * turn));
		graphics.pose().translate(-center, -center);
		for (int i = 0; i <= half; i++) {
			graphics.fill(1 + i, half - i, 2 + i, half + i + 1, color);
		}
		graphics.pose().popMatrix();
	}

	// Caught fish and trophies of a section
	private void extractStats(GuiGraphicsExtractor graphics, Section section, int x, int y, int width) {
		int trophies = 0;
		int totalTrophies = 0;
		int fishCaught = 0;
		int fishTotal = 0;
		for (FishCollection.Fish[] row : section.rows()) {
			for (FishCollection.Fish fish : row) {
				if (fish == null) {
					continue;
				}
				trophies += FishCollection.caughtTrophies(fish);
				totalTrophies += FishCollection.totalTrophies(fish);
				fishTotal++;
				for (FishCollection.Weight weight : fish.weights()) {
					if (FishCollection.isCaught(fish, weight)) {
						fishCaught++;
						break;
					}
				}
			}
		}
		int cardWidth = (width - TILE_GAP) / 2;
		ItemStack speedyRod = ItemIcons.stack("speedy rod");
		if (speedyRod.isEmpty()) {
			speedyRod = NeuItems.stackByName("Speedy Rod Augment");
		}
		extractStat(graphics, x, y, cardWidth, trophies, totalTrophies, ItemStack.EMPTY, tr("angler_trophies"), ItemStack.EMPTY);
		extractStat(graphics, x + cardWidth + TILE_GAP, y, cardWidth, fishCaught, fishTotal, speedyRod, tr("fish_caught"), NeuItems.stackByName(FISH_ICON));
	}

	private void extractStat(GuiGraphicsExtractor graphics, int x, int y, int width, int value, int total, ItemStack bigIcon, Component title, ItemStack smallIcon) {
		graphics.fill(x, y, x + width, y + STATS_HEIGHT, COLOR_ROW_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + STATS_HEIGHT - 1, COLOR_ROW);
		float progress = total <= 0 ? 0.0F : Math.min(1.0F, value / (float) total);
		int centerX = x + RING_MARGIN + RING_RADIUS;
		int centerY = y + STATS_HEIGHT / 2;
		extractRing(graphics, centerX, centerY, progress, progress >= 1.0F ? COLOR_BEST_NOW : COLOR_RING);
		if (bigIcon.isEmpty()) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX - ICON_SIZE / 2.0F, centerY - ICON_SIZE / 2.0F);
			graphics.pose().scale(2.0F, 2.0F);
			graphics.text(this.font, glyph(TROPHY, MCC_ICON, 0xFFFFFF), 0, 0, COLOR_WHITE, false);
			graphics.pose().popMatrix();
		} else {
			graphics.fakeItem(bigIcon, centerX - ICON_SIZE / 2, centerY - ICON_SIZE / 2);
		}
		int textX = centerX + RING_RADIUS + RING_MARGIN;
		graphics.text(this.font, title.copy().withStyle(style -> style.withBold(true)), textX, y + STATS_HEIGHT / 2 - this.font.lineHeight - 1, COLOR_WHITE, true);
		int lineY = y + STATS_HEIGHT / 2 + 2;
		int valueX = textX;
		if (smallIcon.isEmpty()) {
			graphics.text(this.font, glyph(TROPHY, MCC_ICON, 0xFFFFFF), valueX, lineY, COLOR_WHITE, false);
			valueX += this.font.width(glyph(TROPHY, MCC_ICON, 0xFFFFFF));
		} else {
			graphics.pose().pushMatrix();
			graphics.pose().translate(valueX, lineY - 1);
			graphics.pose().scale(0.5F, 0.5F);
			graphics.fakeItem(smallIcon, 0, 0);
			graphics.pose().popMatrix();
			valueX += ICON_SIZE / 2 + 1;
		}
		String count = number(value) + "/" + number(total);
		graphics.text(this.font, count, valueX, lineY, COLOR_WHITE, true);
		int percent = total <= 0 ? 0 : Math.round(value * 100.0F / total);
		graphics.text(this.font, "(" + percent + "%)", valueX + this.font.width(count) + 4, lineY, COLOR_MUTED, true);
	}

	private void extractRing(GuiGraphicsExtractor graphics, int centerX, int centerY, float progress, int color) {
		float pixels = this.scale * Math.max(1, this.minecraft.getWindow().getGuiScale());
		int outer = Math.round(RING_RADIUS * pixels);
		double inner = outer - RING_THICKNESS * pixels;
		double end = progress * Math.PI * 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(1.0F / pixels, 1.0F / pixels);
		for (int dy = -outer; dy < outer; dy++) {
			int runStart = 0;
			int runColor = 0;
			for (int dx = -outer; dx <= outer; dx++) {
				int pixel = 0;
				if (dx < outer) {
					double px = dx + 0.5;
					double py = dy + 0.5;
					double distance = Math.sqrt(px * px + py * py);
					double coverage = clamp(outer - distance + 0.5) * clamp(distance - inner + 0.5);
					if (coverage > 0) {
						double angle = Math.atan2(px, -py);
						if (angle < 0) {
							angle += Math.PI * 2;
						}
						double filled;
						if (progress >= 1.0F) {
							filled = 1.0;
						} else if (progress <= 0.0F) {
							filled = 0.0;
						} else {
							filled = Math.min(clamp((end - angle) * distance + 0.5), clamp(angle * distance + 0.5));
						}
						pixel = blend(COLOR_RING_TRACK, color, filled, coverage);
					}
				}
				if (pixel != runColor) {
					if (runColor != 0) {
						graphics.fill(runStart, dy, dx, dy + 1, runColor);
					}
					runStart = dx;
					runColor = pixel;
				}
			}
		}
		graphics.pose().popMatrix();
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static int blend(int from, int to, double amount, double alpha) {
		int red = (int) Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
		int green = (int) Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
		int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
		int opacity = (int) Math.round(alpha * 255);
		return opacity << 24 | red << 16 | green << 8 | blue;
	}

	// Tooltip in the menu style
	private void extractTooltip(GuiGraphicsExtractor graphics, Component text, int mouseX, int mouseY) {
		List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(text, TOOLTIP_MAX_WIDTH);
		if (lines.isEmpty()) {
			return;
		}
		int textWidth = 0;
		for (net.minecraft.util.FormattedCharSequence line : lines) {
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
		for (net.minecraft.util.FormattedCharSequence line : lines) {
			graphics.text(this.font, line, 0, lineY, COLOR_WHITE, true);
			lineY += TOOLTIP_LINE;
		}
		graphics.pose().popMatrix();
	}

	// Fish tile colored by caught weights
	private void extractTile(GuiGraphicsExtractor graphics, FishCollection.Fish fish, int x, int y, int width, boolean hovered) {
		int caught = 0;
		for (FishCollection.Weight weight : fish.weights()) {
			caught += FishCollection.isCaught(fish, weight) ? 1 : 0;
		}
		int border = caught == 0 ? COLOR_NONE : caught < fish.weights().size() ? COLOR_PARTIAL : COLOR_COMPLETE;
		graphics.fill(x, y, x + width, y + TILE_HEIGHT, border);
		graphics.fill(x + 1, y + 1, x + width - 1, y + TILE_HEIGHT - 1, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
		ItemStack icon = NeuItems.stackByName(fish.name());
		int textX = x + TILE_PADDING;
		if (!icon.isEmpty()) {
			graphics.fakeItem(icon, textX, y + (TILE_HEIGHT - ICON_SIZE) / 2);
			textX += ICON_SIZE + TILE_PADDING;
		}
		Component weights = weights(fish);
		int weightsX = x + width - TILE_PADDING - this.font.width(weights);
		int textY = y + (TILE_HEIGHT - this.font.lineHeight) / 2 + 1;
		Component name = Component.literal(fish.name()).withStyle(style -> style.withColor(fish.rarity().color));
		graphics.text(this.font, this.font.substrByWidth(name, Math.max(0, weightsX - TILE_PADDING - textX)).getString(), textX, textY,
				0xFF000000 | fish.rarity().color, true);
		graphics.text(this.font, weights, weightsX, textY, COLOR_WHITE, true);
	}

	private static Component weights(FishCollection.Fish fish) {
		MutableComponent weights = Component.empty();
		for (FishCollection.Weight weight : fish.weights()) {
			int color = FishCollection.isCaught(fish, weight) ? COLOR_CAUGHT : COLOR_MISSING;
			weights.append(Component.literal(weight.letter).withStyle(style -> style.withColor(color & 0xFFFFFF)));
		}
		return weights;
	}

	private static Component tooltip(FishCollection.Fish fish) {
		MutableComponent tooltip = Component.literal(fish.name()).withStyle(style -> style.withColor(fish.rarity().color));
		MutableComponent tags = Component.empty().append(glyph(RARITY_GLYPHS.get(fish.rarity()), MCC_ICON, 0xFFFFFF));
		if (fish.crab()) {
			tags.append(glyph(CRAB_TAG, MCC_ICON, 0xFFFFFF));
		} else {
			tags.append(glyph(TIME_TAGS.get(fish.time()), MOD_ICON, 0xFFFFFF));
		}
		tooltip.append("\n").append(tags);
		int caught = FishCollection.caughtTrophies(fish);
		int total = FishCollection.totalTrophies(fish);
		int trophyColor = (total > 0 && caught >= total ? COLOR_CAUGHT : COLOR_WHITE) & 0xFFFFFF;
		tooltip.append("\n").append(glyph(TROPHY, MCC_ICON, 0xFFFFFF))
				.append(Component.literal(caught + "/" + total).withStyle(style -> style.withColor(trophyColor)));
		MutableComponent stars = Component.empty();
		for (FishCollection.Weight weight : fish.weights()) {
			stars.append(glyph(STAR_GLYPHS.get(weight), MCC_ICON, FishCollection.isCaught(fish, weight) ? 0xFFFFFF : 0x000000));
		}
		return tooltip.append("\n").append(stars);
	}

	private static Component glyph(String glyph, FontDescription font, int color) {
		return Component.literal(glyph).withStyle(style -> style.withFont(font).withColor(color).withBold(false));
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
		// Section opened or closed
		int contentLeft = contentLeft();
		if (x >= contentLeft && x < contentLeft + contentWidth() && y >= this.listTop && y < this.listBottom) {
			int sectionY = this.listTop - Math.round(this.scroll);
			for (Section section : this.sections) {
				if (y >= sectionY && y < sectionY + SECTION_HEIGHT) {
					Set<String> collapsed = ModConfig.get().collapsedSections;
					if (!collapsed.remove(section.title())) {
						collapsed.add(section.title());
					}
					ModConfig.get().save();
					NeuItems.clickSound();
					return true;
				}
				sectionY += sectionHeight(section) + SECTION_GAP;
			}
			return true;
		}
		boolean inPanels = x >= this.sideLeft && x < right && y >= this.top && y < this.top + this.panelHeight
				&& (x < this.sideLeft + SIDE_WIDTH || x >= this.left);
		if (!inPanels) {
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
		if (!FishCollection.allowed()) {
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
