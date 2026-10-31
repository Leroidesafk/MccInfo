package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.items.ItemIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// Fish Collection debug screen: equipment, fish per island, perks and the Best Island numbers (not final version)
public final class FishCollectionScreen extends Screen {
	private static final int MAX_WIDTH = 480;
	private static final int MARGIN = 8;
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SPACING = 4;
	private static final int TEXT_HEIGHT = 10;
	private static final int ICON_SIZE = 16;
	private static final int ROW_HEIGHT = ICON_SIZE + 2;
	private static final int EQUIPMENT_LABEL_Y = 18;
	private static final int EQUIPMENT_ICON_Y = EQUIPMENT_LABEL_Y + TEXT_HEIGHT;
	private static final int EQUIPMENT_GROUP_SPACING = 12;
	private static final int HEADER_Y = EQUIPMENT_ICON_Y + ICON_SIZE + SPACING * 2;
	private static final int LIST_Y = HEADER_Y + TEXT_HEIGHT + SPACING;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int CAUGHT_COLOR = 0x55FF55;
	private static final int MISSING_COLOR = 0x555555;
	private static final int DEBUG_COLOR = 0xFFAAAAAA;
	private static final int COLUMNS = FishCollection.Time.values().length;

	// A row is a title, up to 3 fish, a perk row or a debug line
	private record Row(String title, String iconKey, List<FishCollection.Fish> fish, int perkRow, int debugLine) {
		boolean isTitle() {
			return this.fish.isEmpty() && this.perkRow == NONE && this.debugLine == NONE;
		}
	}

	private static final int NONE = -1;

	// Perk icons, one row per hook type
	private static final String[][] PERK_GRID = {
			{"strong hook", "xp magnet", "boosted rod", "strong pot", "elusive chance"},
			{"wise hook", "fish magnet", "speedy rod", "wise pot", "wayfinder data"},
			{"glimmering hook", "pearl magnet", "graceful rod", "glimmering pot", "pearl chance"},
			{"greedy hook", "treasure magnet", "glitched rod", "greedy pot", "treasure chance"},
			{"lucky hook", "spirit magnet", "stable rod", "lucky pot", "spirit chance"},
	};
	private static final String PERKS_TITLE = "Fishing Perks";
	private static final int ARROW_SIZE = 7;

	private record Equipped(String key, String detail) {
	}

	private record Group(Component label, List<Equipped> items) {
	}

	private final Screen parent;
	private final List<Row> allRows = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private int scrollRow;

	public FishCollectionScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.events.fish_collection"));
		this.parent = parent;
		// One title per island, then its fish grouped by rarity
		List<FishCollection.Fish> fish = FishCollection.fish();
		String title = null;
		for (int i = 0; i < fish.size(); ) {
			FishCollection.Fish first = fish.get(i);
			String rowTitle = first.crab() ? first.island() + " Crabs" : first.island();
			if (!rowTitle.equals(title)) {
				title = rowTitle;
				String iconKey = first.crab() ? BestIsland.crabIconKey(first.island()) : first.island().toLowerCase(Locale.ROOT);
				this.allRows.add(new Row(title, iconKey, List.of(), NONE, NONE));
			}
			int end = i + 1;
			while (end < fish.size() && end - i < COLUMNS && fish.get(end).crab() == first.crab()
					&& fish.get(end).island().equals(first.island()) && fish.get(end).rarity() == first.rarity()) {
				end++;
			}
			this.allRows.add(new Row(title, null, fish.subList(i, end), NONE, NONE));
			i = end;
		}
		this.allRows.add(new Row(PERKS_TITLE, null, List.of(), NONE, NONE));
		for (int perkRow = 0; perkRow < PERK_GRID.length; perkRow++) {
			this.allRows.add(new Row(PERKS_TITLE, null, List.of(), perkRow, NONE));
		}
		for (int line = 0; line < BestIsland.debugLines().size(); line++) {
			this.allRows.add(new Row(null, null, List.of(), NONE, line));
		}
		updateRows();
	}

	// Collapsed sections are saved in the config
	private static boolean isCollapsed(Row row) {
		return row.title() != null && ModConfig.get().collapsedSections.contains(row.title());
	}

	private void updateRows() {
		this.rows.clear();
		for (Row row : this.allRows) {
			if (row.isTitle() || !isCollapsed(row)) {
				this.rows.add(row);
			}
		}
	}

	@Override
	// Click on a title collapses or opens its section
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int listWidth = Math.min(this.width, MAX_WIDTH) - MARGIN * 2;
		int left = (this.width - listWidth) / 2;
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.y() < LIST_Y || event.x() < left || event.x() >= left + listWidth) {
			return false;
		}
		int index = this.scrollRow + (int) ((event.y() - LIST_Y) / ROW_HEIGHT);
		if (index >= Math.min(this.rows.size(), this.scrollRow + visibleRows()) || !this.rows.get(index).isTitle()) {
			return false;
		}
		String title = this.rows.get(index).title();
		Set<String> collapsed = ModConfig.get().collapsedSections;
		if (!collapsed.remove(title)) {
			collapsed.add(title);
		}
		ModConfig.get().save();
		updateRows();
		this.scrollRow = Math.clamp(this.scrollRow, 0, maxScrollRow());
		return true;
	}

	@Override
	protected void init() {
		this.scrollRow = Math.clamp(this.scrollRow, 0, maxScrollRow());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(this.width / 2 - BUTTON_WIDTH / 2, this.height - BUTTON_HEIGHT - SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private int visibleRows() {
		return Math.max(1, (this.height - BUTTON_HEIGHT - SPACING * 2 - LIST_Y) / ROW_HEIGHT);
	}

	private int maxScrollRow() {
		return Math.max(0, this.rows.size() - visibleRows());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0) {
			this.scrollRow = Math.clamp(this.scrollRow - (int) Math.signum(scrollY) * 3, 0, maxScrollRow());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	// Weight letters, green when caught
	private static Component weights(FishCollection.Fish fish) {
		MutableComponent weights = Component.empty();
		for (FishCollection.Weight weight : fish.weights()) {
			int color = FishCollection.isCaught(fish, weight) ? CAUGHT_COLOR : MISSING_COLOR;
			weights.append(Component.literal(weight.letter).withStyle(style -> style.withColor(color)));
		}
		return weights;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, Component.empty().append(this.title).append(" " + FishCollection.caughtCount() + "/" + FishCollection.totalCount()),
				this.width / 2, 6, TEXT_COLOR);

		int listWidth = Math.min(this.width, MAX_WIDTH) - MARGIN * 2;
		int left = (this.width - listWidth) / 2;
		int columnWidth = listWidth / COLUMNS;
		extractEquipment(graphics, mouseX, mouseY);
		List<String> debugLines = BestIsland.debugLines();
		for (FishCollection.Time time : FishCollection.Time.values()) {
			graphics.text(this.font, Component.translatable("screen.mccinfo.events.fish_collection." + time.name().toLowerCase())
					.withStyle(style -> style.withUnderlined(true)), left + time.ordinal() * columnWidth, HEADER_Y, TEXT_COLOR, false);
		}

		int textOffset = (ROW_HEIGHT - this.font.lineHeight) / 2 + 1;
		int last = Math.min(this.rows.size(), this.scrollRow + visibleRows());
		for (int i = this.scrollRow; i < last; i++) {
			Row row = this.rows.get(i);
			int y = LIST_Y + (i - this.scrollRow) * ROW_HEIGHT;
			if (row.perkRow() != NONE) {
				extractPerks(graphics, PERK_GRID[row.perkRow()], y, mouseX, mouseY);
				continue;
			}
			if (row.debugLine() != NONE) {
				if (row.debugLine() < debugLines.size()) {
					graphics.text(this.font, this.font.plainSubstrByWidth(debugLines.get(row.debugLine()), listWidth), left, y + textOffset, DEBUG_COLOR, false);
				}
				continue;
			}
			if (row.isTitle()) {
				extractTitle(graphics, row, y, textOffset);
				continue;
			}
			for (int column = 0; column < row.fish().size(); column++) {
				FishCollection.Fish fish = row.fish().get(column);
				int x = left + column * columnWidth;
				Component name = Component.literal(fish.name()).withStyle(style -> style.withColor(fish.rarity().color));
				Component weights = weights(fish);
				// Icon with the name on hover, or the name alone when the icon is unknown
				ItemStack icon = ItemIcons.stack(FishCollection.iconKey(fish));
				if (icon.isEmpty()) {
					graphics.text(this.font, name, x, y + textOffset, TEXT_COLOR, false);
					graphics.text(this.font, weights, x + columnWidth - SPACING * 2 - this.font.width(weights), y + textOffset, TEXT_COLOR, false);
					continue;
				}
				graphics.fakeItem(icon, x, y);
				graphics.text(this.font, weights, x + ICON_SIZE + SPACING, y + textOffset, TEXT_COLOR, false);
				if (isOver(mouseX, mouseY, x, y)) {
					graphics.setTooltipForNextFrame(this.font, name, mouseX, mouseY);
				}
			}
		}
	}

	private void extractTitle(GuiGraphicsExtractor graphics, Row row, int y, int textOffset) {
		boolean collapsed = isCollapsed(row);
		int color = collapsed ? DEBUG_COLOR : TEXT_COLOR;
		Component text = Component.literal(row.title()).withStyle(style -> style.withBold(true));
		ItemStack icon = row.iconKey() == null ? ItemStack.EMPTY : ItemIcons.stack(row.iconKey());
		int iconWidth = icon.isEmpty() ? 0 : ICON_SIZE + SPACING;
		int x = (this.width - ARROW_SIZE - SPACING - iconWidth - this.font.width(text)) / 2;
		extractArrow(graphics, x, y + (ROW_HEIGHT - ARROW_SIZE) / 2, collapsed, color);
		x += ARROW_SIZE + SPACING;
		if (!icon.isEmpty()) {
			graphics.fakeItem(icon, x, y);
		}
		graphics.text(this.font, text, x + iconWidth, y + textOffset, color, false);
	}

	private static void extractArrow(GuiGraphicsExtractor graphics, int x, int y, boolean collapsed, int color) {
		int half = ARROW_SIZE / 2;
		for (int i = 0; i <= half; i++) {
			if (collapsed) {
				graphics.fill(x + 1 + i, y + half - i, x + 2 + i, y + half + i + 1, color);
			} else {
				graphics.fill(x + i, y + 1 + i, x + ARROW_SIZE - i, y + 2 + i, color);
			}
		}
	}

	// Finds the saved perk whose name has every word of the grid name
	private static String resolvePerk(String name) {
		Map<String, Integer> perks = ModConfig.get().perks;
		if (perks.containsKey(name)) {
			return name;
		}
		String[] words = name.split(" ");
		for (String perk : new TreeMap<>(perks).keySet()) {
			boolean matches = !perk.contains("overclock");
			for (String word : words) {
				matches &= perk.contains(word);
			}
			if (matches) {
				return perk;
			}
		}
		return name;
	}

	private void extractPerks(GuiGraphicsExtractor graphics, String[] names, int y, int mouseX, int mouseY) {
		int x = (this.width - (names.length * ROW_HEIGHT - 2)) / 2;
		for (String name : names) {
			String perk = resolvePerk(name);
			ItemStack icon = ItemIcons.stack(perk);
			if (icon.isEmpty()) {
				graphics.centeredText(this.font, Component.literal("?"), x + ICON_SIZE / 2, y + SPACING, MISSING_COLOR | 0xFF000000);
			} else {
				graphics.fakeItem(icon, x, y);
			}
			if (isOver(mouseX, mouseY, x, y)) {
				graphics.setComponentTooltipForNextFrame(this.font, perkTooltip(perk), mouseX, mouseY);
			}
			x += ROW_HEIGHT;
		}
	}

	// Level with the overclock and equipment bonuses
	private static List<Component> perkTooltip(String perk) {
		int level = BestIsland.perk(perk);
		int overclock = BestIsland.overclockBonus(perk);
		int equipment = BestIsland.equipment(perk);
		String name = ItemIcons.name(perk);
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(Component.literal(name == null ? perk : name));
		if (name == null) {
			tooltip.add(Component.translatable("screen.mccinfo.events.fish_collection.perk_unknown").withStyle(style -> style.withColor(DEBUG_COLOR)));
			return tooltip;
		}
		if (overclock > 0) {
			tooltip.add(Component.translatable("screen.mccinfo.events.fish_collection.perk_overclock", overclock).withStyle(style -> style.withColor(DEBUG_COLOR)));
		}
		if (equipment > 0) {
			tooltip.add(Component.translatable("screen.mccinfo.events.fish_collection.perk_equipment", equipment).withStyle(style -> style.withColor(DEBUG_COLOR)));
		}
		tooltip.add(Component.translatable("screen.mccinfo.events.fish_collection.perk_total", level + overclock + equipment).withStyle(style -> style.withColor(CAUGHT_COLOR)));
		return tooltip;
	}

	private static boolean isOver(int mouseX, int mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE;
	}

	private static Group group(String name, List<Equipped> items) {
		return new Group(Component.translatable("screen.mccinfo.events.fish_collection." + name), items);
	}

	private static List<Equipped> single(String key) {
		return key == null ? List.of() : List.of(new Equipped(key, null));
	}

	// Bait, line, overclocks and augments read in the Supplies menu
	private static List<Group> equipment() {
		ModConfig config = ModConfig.get();
		List<Equipped> overclocks = new ArrayList<>();
		for (Map.Entry<String, String> overclock : new TreeMap<>(config.overclocks).entrySet()) {
			overclocks.add(new Equipped(overclock.getKey(), "> " + overclock.getValue()));
		}
		List<Equipped> augments = new ArrayList<>();
		for (String augment : config.augments) {
			augments.add(new Equipped(augment, null));
		}
		return List.of(group("bait", single(config.bait)), group("line", single(config.line)), group("overclocks", overclocks), group("augments", augments));
	}

	private static int itemsWidth(Group group) {
		return Math.max(1, group.items().size()) * ROW_HEIGHT - 2;
	}

	private void extractEquipment(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<Group> groups = equipment();
		int totalWidth = (groups.size() - 1) * EQUIPMENT_GROUP_SPACING;
		for (Group group : groups) {
			totalWidth += Math.max(this.font.width(group.label()), itemsWidth(group));
		}
		int x = (this.width - totalWidth) / 2;
		for (Group group : groups) {
			int groupWidth = Math.max(this.font.width(group.label()), itemsWidth(group));
			graphics.centeredText(this.font, group.label(), x + groupWidth / 2, EQUIPMENT_LABEL_Y, DEBUG_COLOR);
			int itemX = x + (groupWidth - itemsWidth(group)) / 2;
			if (group.items().isEmpty()) {
				graphics.centeredText(this.font, Component.literal("-"), itemX + ICON_SIZE / 2, EQUIPMENT_ICON_Y + SPACING, DEBUG_COLOR);
			}
			for (Equipped item : group.items()) {
				ItemStack icon = ItemIcons.stack(item.key());
				if (icon.isEmpty()) {
					graphics.centeredText(this.font, Component.literal("?"), itemX + ICON_SIZE / 2, EQUIPMENT_ICON_Y + SPACING, TEXT_COLOR);
				} else {
					graphics.fakeItem(icon, itemX, EQUIPMENT_ICON_Y);
				}
				if (isOver(mouseX, mouseY, itemX, EQUIPMENT_ICON_Y)) {
					String name = ItemIcons.name(item.key());
					List<Component> tooltip = new ArrayList<>();
					tooltip.add(Component.literal(name == null ? item.key() : name));
					if (item.detail() != null) {
						tooltip.add(Component.literal(item.detail()).withStyle(style -> style.withColor(DEBUG_COLOR)));
					}
					graphics.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
				}
				itemX += ROW_HEIGHT;
			}
			x += groupWidth + EQUIPMENT_GROUP_SPACING;
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
