package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.PrivateKeys;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.gui.ToggleSwitch;
import leroidesafk.mccinfo.client.relay.Permissions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.ContainerHoveredSlotAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;

public final class NeuItems {
	// List layout
	static final int SQUARE_SIZE = 18;
	private static final int CELL_SIZE = 20;
	private static final int INVENTORY_GAP = 16;
	private static final int MARGIN = 8;
	static final int BOX_HEIGHT = 14;
	static final int GAP = 2;
	private static final int SEARCH_HEIGHT = 16;
	private static final int REMOVED_COLUMNS = 2;
	static final int BOX_PADDING = 4;
	public static final int BOX_COLOR = 0x80000000;
	public static final int HOVER_COLOR = 0xA0606060;
	private static final int OUTDATED_COLOR = 0x80601C1C;
	private static final int OUTDATED_HOVER_COLOR = 0xA0803434;
	private static final int PLUS_SHADOW_COLOR = 0xFF000000;
	private static final int MINUS_COLOR = 0xFFFF5555;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int DISABLED_COLOR = 0xFF808080;
	private static final int MAX_ITEMS = 10_000;
	private static final int MAX_QUERY = 64;
	private static final int GROUP_COLOR = 0xFF3FBF3F;
	private static final int GROUP_OPEN_COLOR = 0xFF8C8C8C;
	private static final long ICON_INTERVAL_MILLIS = 1000L;
	static final int TABS_GAP = 4;
	// MCC click sound
	private static final SoundEvent CLICK_SOUND = SoundEvent.createVariableRangeEvent(Identifier.parse("mcc:ui.click_normal"));
	static final int TAB_HEIGHT = 14;
	private static final float TAB_ICON_SCALE = 0.75F;
	private static final int BADGE_PIXELS = 16;
	private static final int TAB_SELECTED_COLOR = 0xFFFFFFFF;
	private static final int RECIPE_DIM_COLOR = 0xA0000000;
	private static final ChatFormatting GROUP_COUNT_COLOR = ChatFormatting.GOLD;
	private static final Identifier SETTINGS_MODEL = Identifier.parse("mcc:island_interface/settings/icon");
	private static ItemStack settingsIcon;

	// Category tabs
	private enum Category {
		GLOBAL("global", "mcc:island_interface/generic/globe"),
		ITEMS("items", "mcc:island_items/infinibag/material/gloop"),
		COSMETICS("cosmetics", "mcc:island_interface/wardrobe/cosmetic_collection"),
		FISHS("fishs", "mcc:island_items/infinibag/fish/verdant_woods/forest_cod");

		private final String key;
		private final Identifier model;
		private ItemStack icon;

		Category(String key, String model) {
			this.key = key;
			this.model = Identifier.parse(model);
		}

		private Component label() {
			return Component.translatable("screen.mccinfo.neu_items.category." + this.key);
		}

		private ItemStack icon() {
			if (this.icon == null) {
				this.icon = new ItemStack(Items.PAPER);
				this.icon.set(DataComponents.ITEM_MODEL, this.model);
				this.icon.set(DataComponents.ITEM_NAME, label());
			}
			return this.icon;
		}

		private static Category ofType(String type) {
			return switch (type == null ? "" : type) {
				case "cosmetic" -> COSMETICS;
				case "fish" -> FISHS;
				default -> ITEMS;
			};
		}

		private boolean accepts(Category category) {
			return this == GLOBAL || this == category;
		}
	}

	// One NEU item
	private static final class Entry {
		private final String id;
		private final String name;
		private final String lore;
		private final JsonElement json;
		private final Category category;
		private final boolean outdated;
		private ItemStack stack;
		private boolean decoded;
		private ItemStack badge;

		private Entry(String id, String name, String lore, JsonElement json, Category category, boolean outdated) {
			this.id = id;
			this.name = name.toLowerCase(Locale.ROOT);
			this.lore = lore.toLowerCase(Locale.ROOT);
			this.json = json;
			this.category = category;
			this.outdated = outdated;
		}

		private boolean matches(String lower) {
			return this.name.contains(lower) || this.lore.contains(lower);
		}

		private ItemStack stack() {
			if (!this.decoded) {
				ClientPacketListener connection = Minecraft.getInstance().getConnection();
				if (connection == null) {
					return ItemStack.EMPTY;
				}
				this.decoded = true;
				RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, connection.registryAccess());
				this.stack = ItemStack.CODEC.parse(ops, this.json).resultOrPartial(error -> Mccinfo.LOGGER.warn("Unreadable NEU item {}: {}", this.id, error))
						.orElse(ItemStack.EMPTY);
			}
			return this.stack;
		}
	}

	// Clickable area
	private record Box(int left, int top, int right, int bottom) {
		private boolean contains(double x, double y) {
			return x >= this.left && x < this.right && y >= this.top && y < this.bottom;
		}
	}

	// List position, size and buttons
	private record Layout(int left, int top, int columns, int rows, Box previous, Box page, Box next, int gridTop, int tabsTop) {
		private int width() {
			return this.columns * CELL_SIZE - GAP;
		}

		private int perPage() {
			return this.columns * this.rows;
		}

		private boolean contains(double x, double y) {
			return x >= this.left && x < this.left + width() && y >= this.top && y < this.tabsTop + TAB_HEIGHT;
		}

		private int tabLeft(Category tab) {
			return this.left + tab.ordinal() * (width() + GAP) / Category.values().length;
		}

		private int tabRight(Category tab) {
			return this.left + (tab.ordinal() + 1) * (width() + GAP) / Category.values().length - GAP;
		}

		private Category tabAt(double x, double y) {
			if (y < this.tabsTop || y >= this.tabsTop + TAB_HEIGHT) {
				return null;
			}
			for (Category tab : Category.values()) {
				if (x >= tabLeft(tab) && x < tabRight(tab)) {
					return tab;
				}
			}
			return null;
		}
	}

	// Item of a group, maybe its icon
	private record Member(Entry entry, String group, boolean icon, boolean exclusive) {
		private Group subgroup() {
			return this.group == null ? null : GROUP_BY_ID.get(this.group);
		}

		private ItemStack stack(int depth) {
			Group subgroup = subgroup();
			return subgroup != null ? subgroup.icon(depth + 1) : this.entry != null ? this.entry.stack() : ItemStack.EMPTY;
		}
	}

	// Group shown as one tile, opened with a click
	private record Group(String id, String name, boolean collapsed, int order, List<Member> members, List<Member> icons, ItemStack iconStack) {
		private ItemStack icon() {
			return icon(0);
		}

		private ItemStack icon(int depth) {
			if (this.iconStack != null) {
				return this.iconStack;
			}
			if (depth > MAX_GROUP_DEPTH || this.icons.isEmpty()) {
				return ItemStack.EMPTY;
			}
			return this.icons.get((int) (Util.getMillis() / ICON_INTERVAL_MILLIS % this.icons.size())).stack(depth);
		}

		private List<Entry> entries() {
			List<Entry> entries = new ArrayList<>();
			collect(this, entries, new HashSet<>());
			return entries;
		}

		private static void collect(Group group, List<Entry> entries, Set<String> seen) {
			if (!seen.add(group.id())) {
				return;
			}
			for (Member member : group.members()) {
				Group subgroup = member.subgroup();
				if (subgroup != null) {
					collect(subgroup, entries, seen);
				} else if (member.entry() != null) {
					entries.add(member.entry());
				}
			}
		}

		private boolean accepts(Category category) {
			return entries().stream().anyMatch(entry -> category.accepts(entry.category));
		}

		private Category category() {
			List<Entry> entries = entries();
			return entries.isEmpty() ? Category.ITEMS : entries.getFirst().category;
		}

		private boolean outdated() {
			return entries().stream().allMatch(entry -> entry.outdated);
		}

		private boolean variants() {
			return this.members.stream().anyMatch(member -> member.entry() != null && member.entry().id.equals(this.id));
		}
	}

	// Grid tile: an item or a group
	private record Tile(Entry entry, Group group) {
		private boolean isGroup() {
			return this.group != null;
		}

		private String sortName() {
			return this.group != null ? this.group.name() : this.entry.name;
		}

		private int sortCategory() {
			return (this.group != null ? this.group.category() : this.entry.category).ordinal();
		}

		private int sortOrder() {
			return this.group != null ? this.group.order() : Integer.MAX_VALUE;
		}

		private ItemStack stack() {
			return this.group != null ? this.group.icon() : this.entry.stack();
		}

		private boolean outdated() {
			return this.group != null ? this.group.outdated() : this.entry.outdated;
		}

		private ItemStack badge() {
			return this.group != null ? null : this.entry.badge;
		}
	}

	// Items by id and by name, cost icons
	private static final List<Entry> ITEMS = new ArrayList<>();
	private static final List<Entry> UI_ITEMS = new ArrayList<>();
	private static final Map<String, Entry> BY_ID = new HashMap<>();
	private static final Map<String, List<Entry>> BY_NAME = new HashMap<>();
	private static final Map<String, String> COST_ICONS = new HashMap<>();
	// Last hovered item, for Copy Item
	private static final long HOVER_FRESH_MILLIS = 200L;
	private static ItemStack hoveredStack = ItemStack.EMPTY;
	private static long hoveredAt;
	// Groups
	private static final List<Group> GROUPS = new ArrayList<>();
	private static final Map<String, Group> GROUP_BY_ID = new HashMap<>();
	private static final Set<String> NESTED_GROUPS = new HashSet<>();
	private static final int MAX_GROUP_DEPTH = 8;
	// Current search, category and page
	private static List<Tile> filtered = List.of();
	private static final Set<String> EXPANDED = new HashSet<>();
	private static final Set<String> CONTAINERS = new HashSet<>();
	private static final int TITLE_SLOT = 8;
	private static JsonObject cosmeticDefaults;
	private static String query = "";
	private static Category category = Category.GLOBAL;
	private static int page;

	private NeuItems() {
	}

	// Recipe Mode in every inventory, list in the player inventory and chests
	public static void register() {
		net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(context -> new NpcGroupRenderer(context.minecraft().getEntityRenderDispatcher()));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !recipeClick(current, event));
				ScreenEvents.afterForeground(screen).register((current, graphics, mouseX, mouseY, a) -> dimForRecipes(current, graphics));
			}
			if (!(screen instanceof InventoryScreen) && !(screen instanceof ContainerScreen)) {
				return;
			}
			ToggleSwitch toggle = new ToggleSwitch(MARGIN / 2, height - ToggleSwitch.HEIGHT - MARGIN / 2, Component.empty(),
					() -> !ModConfig.get().neuItemsHidden, value -> {
						ModConfig.get().neuItemsHidden = !value;
						ModConfig.get().save();
					});
			int searchWidth = ((ContainerHoveredSlotAccessor) screen).mccinfo$getImageWidth();
			EditBox search = new EditBox(client.font, 0, 0, searchWidth, SEARCH_HEIGHT, Component.translatable("screen.mccinfo.neu_items.search"));
			search.setMaxLength(MAX_QUERY);
			search.setValue(query);
			search.moveCursorToStart(false);
			search.moveCursorToEnd(false);
			search.setHint(Component.translatable("screen.mccinfo.neu_items.search").withStyle(ChatFormatting.DARK_GRAY));
			search.setResponder(value -> {
				query = value;
				page = 0;
				refilter();
			});
			toggle.setTooltip(Tooltip.create(Component.translatable("screen.mccinfo.neu_items.toggle")));
			Screens.getWidgets(screen).add(toggle);
			Screens.getWidgets(screen).add(search);
			placeWidgets(screen, toggle, search);
			ScreenEvents.afterTick(screen).register(current -> placeWidgets(current, toggle, search));
			ScreenEvents.afterForeground(screen).register((current, graphics, mouseX, mouseY, a) -> {
				placeWidgets(current, toggle, search);
				extract(current, graphics, mouseX, mouseY);
				extractSettings(graphics, search, mouseX, mouseY);
				extractCalculation(graphics, search);
			});
			ScreenMouseEvents.allowMouseScroll(screen).register((current, mouseX, mouseY, scrollX, scrollY) -> !scroll(current, mouseX, mouseY, scrollY));
			ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
				if (search.isFocused() && !search.isMouseOver(event.x(), event.y())) {
					search.setFocused(false);
					if (current.getFocused() == search) {
						current.setFocused(null);
					}
				}
				return true;
			});
			ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
				if (!search.visible || !settingsBox(search).contains(event.x(), event.y())) {
					return true;
				}
				clickSound();
				client.gui.setScreen(new InventoryButtonsScreen(current));
				return false;
			});
			ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !click(current, event));
			ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> {
				if (!search.visible || !search.isFocused() || event.key() == GLFW.GLFW_KEY_ESCAPE) {
					return true;
				}
				search.keyPressed(event);
				return false;
			});
		});
	}

	// Recipe Mode click on an item
	private static boolean recipeClick(Screen screen, MouseButtonEvent event) {
		if (ITEMS.isEmpty() || !PrivateKeys.RECIPE_MODE.held()
				|| (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			return false;
		}
		Slot slot = ((ContainerHoveredSlotAccessor) screen).mccinfo$getHoveredSlot();
		if (slot == null || !slot.hasItem()) {
			return false;
		}
		String id = idByName(slot.getItem().getHoverName().getString());
		if (id != null) {
			NeuCrafts.open(screen, id, event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT);
		}
		return true;
	}

	// Dim the inventory in Recipe Mode
	private static void dimForRecipes(Screen screen, GuiGraphicsExtractor graphics) {
		if (ITEMS.isEmpty() || !PrivateKeys.RECIPE_MODE.held() || !(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}
		ContainerHoveredSlotAccessor accessor = (ContainerHoveredSlotAccessor) screen;
		List<Box> holes = new ArrayList<>();
		for (Slot slot : container.getMenu().slots) {
			if (!slot.isActive() || !slot.hasItem()) {
				continue;
			}
			String id = idByName(slot.getItem().getHoverName().getString());
			if (id != null && NeuCrafts.hasCrafts(id)) {
				int x = accessor.mccinfo$getLeftPos() + slot.x - 1;
				int y = accessor.mccinfo$getTopPos() + slot.y - 1;
				holes.add(new Box(x, y, x + SQUARE_SIZE, y + SQUARE_SIZE));
				if (slot == accessor.mccinfo$getHoveredSlot()) {
					pointer(graphics);
				}
			}
		}
		graphics.nextStratum();
		fillAround(graphics, screen.width, screen.height, holes, RECIPE_DIM_COLOR);
	}

	public static ItemStack hovered() {
		return System.currentTimeMillis() - hoveredAt <= HOVER_FRESH_MILLIS ? hoveredStack : ItemStack.EMPTY;
	}

	public static void pointer(GuiGraphicsExtractor graphics) {
		graphics.requestCursor(CursorTypes.POINTING_HAND);
	}

	private static void fillAround(GuiGraphicsExtractor graphics, int width, int height, List<Box> holes, int color) {
		Set<Integer> edges = new TreeSet<>(List.of(0, height));
		holes.forEach(hole -> {
			edges.add(Math.clamp(hole.top(), 0, height));
			edges.add(Math.clamp(hole.bottom(), 0, height));
		});
		Integer previous = null;
		for (int edge : edges) {
			if (previous != null && edge > previous) {
				int top = previous;
				int bottom = edge;
				List<Box> row = holes.stream().filter(hole -> hole.top() < bottom && hole.bottom() > top)
						.sorted(Comparator.comparingInt(Box::left)).toList();
				int x = 0;
				for (Box hole : row) {
					if (hole.left() > x) {
						graphics.fill(x, top, Math.min(hole.left(), width), bottom, color);
					}
					x = Math.max(x, hole.right());
				}
				if (x < width) {
					graphics.fill(x, top, width, bottom, color);
				}
			}
			previous = edge;
		}
	}

	static ItemStack settingsIcon() {
		if (settingsIcon == null) {
			settingsIcon = new ItemStack(Items.PAPER);
			settingsIcon.set(DataComponents.ITEM_MODEL, SETTINGS_MODEL);
		}
		return settingsIcon;
	}

	// Calculator box under the search bar
	private static final int CALC_PADDING = 4;
	private static final int CALC_GAP = 2;
	private static final int CALC_BACKGROUND = 0xC0000000;

	private static void extractCalculation(GuiGraphicsExtractor graphics, EditBox search) {
		if (!search.visible) {
			return;
		}
		Component calculation = Calculator.display(search.getValue());
		if (calculation == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int width = search.getWidth();
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(calculation, width - CALC_PADDING * 2);
		int height = lines.size() * font.lineHeight + CALC_PADDING * 2 - 1;
		int top = search.getY() - CALC_GAP - height;
		OverlayStyle.fillRounded(graphics, search.getX(), top, width, height, CALC_BACKGROUND);
		int y = top + CALC_PADDING;
		for (net.minecraft.util.FormattedCharSequence line : lines) {
			graphics.text(font, line, search.getX() + CALC_PADDING, y, 0xFFFFFFFF, true);
			y += font.lineHeight;
		}
	}

	private static Box settingsBox(EditBox search) {
		return new Box(search.getX() - GAP - SEARCH_HEIGHT, search.getY(), search.getX() - GAP, search.getY() + SEARCH_HEIGHT);
	}

	// Settings button next to the search bar
	private static void extractSettings(GuiGraphicsExtractor graphics, EditBox search, int mouseX, int mouseY) {
		if (!search.visible) {
			return;
		}
		Box box = settingsBox(search);
		boolean hovered = box.contains(mouseX, mouseY);
		graphics.nextStratum();
		OverlayStyle.fillRounded(graphics, box.left(), box.top(), SEARCH_HEIGHT, SEARCH_HEIGHT, hovered ? HOVER_COLOR : BOX_COLOR);
		float icon = 16 * TAB_ICON_SCALE;
		graphics.pose().pushMatrix();
		graphics.pose().translate(box.left() + (SEARCH_HEIGHT - icon) / 2, box.top() + (SEARCH_HEIGHT - icon) / 2);
		graphics.pose().scale(TAB_ICON_SCALE, TAB_ICON_SCALE);
		graphics.item(settingsIcon(), 0, 0);
		graphics.pose().popMatrix();
		if (hovered) {
			pointer(graphics);
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, Component.translatable("screen.mccinfo.inventory_buttons.title"), mouseX, mouseY);
		}
	}

	static List<String> searchUiIds(String search) {
		String lower = search.strip().toLowerCase(Locale.ROOT);
		return UI_ITEMS.stream().filter(entry -> lower.isEmpty() || entry.name.contains(lower)).map(entry -> entry.id).toList();
	}

	static List<String> searchIds(String search) {
		String lower = search.strip().toLowerCase(Locale.ROOT);
		return ITEMS.stream().filter(entry -> lower.isEmpty() || entry.name.contains(lower))
				.sorted(Comparator.comparingInt((Entry entry) -> entry.category.ordinal()).thenComparing(entry -> entry.name))
				.map(entry -> entry.id).toList();
	}

	public static JsonObject cosmeticDefaults() {
		return cosmeticDefaults;
	}

	// Item by NEU id
	public static ItemStack stack(String id) {
		Entry entry = BY_ID.get(id);
		return entry == null ? ItemStack.EMPTY : entry.stack();
	}

	static String costIcon(String id) {
		return COST_ICONS.get(id);
	}

	static List<String> variants(String id) {
		for (Group group : GROUPS) {
			List<String> ids = group.members().stream().filter(member -> member.entry() != null).map(member -> member.entry().id).toList();
			if (ids.contains(group.id()) && ids.contains(id)) {
				List<String> variants = new ArrayList<>();
				variants.add(id);
				ids.stream().filter(other -> !other.equals(id)).forEach(variants::add);
				return variants;
			}
		}
		return List.of(id);
	}

	public static ItemStack stackByName(String name) {
		for (Entry entry : BY_NAME.getOrDefault(name.strip().toLowerCase(Locale.ROOT), List.of())) {
			ItemStack stack = entry.stack();
			if (!stack.isEmpty()) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	public static String idByName(String name) {
		List<Entry> matches = BY_NAME.get(name.strip().toLowerCase(Locale.ROOT));
		return matches != null && matches.size() == 1 ? matches.getFirst().id : null;
	}

	public static void clear() {
		ITEMS.clear();
		UI_ITEMS.clear();
		BY_ID.clear();
		BY_NAME.clear();
		COST_ICONS.clear();
		GROUPS.clear();
		GROUP_BY_ID.clear();
		NESTED_GROUPS.clear();
		CONTAINERS.clear();
		cosmeticDefaults = null;
		NeuCrafts.clear();
		filtered = List.of();
		EXPANDED.clear();
		page = 0;
	}

	// Items and groups from the relay
	public static void receive(JsonObject object) {
		if (object.has("reset") && object.get("reset").getAsBoolean()) {
			ITEMS.clear();
			UI_ITEMS.clear();
			BY_ID.clear();
		BY_NAME.clear();
			COST_ICONS.clear();
			GROUPS.clear();
			GROUP_BY_ID.clear();
			NESTED_GROUPS.clear();
		}
		if (object.has("items") && object.get("items").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("items")) {
				if (ITEMS.size() >= MAX_ITEMS) {
					break;
				}
				if (element.isJsonObject() && element.getAsJsonObject().has("item")) {
					JsonObject item = element.getAsJsonObject();
					String id = item.has("id") ? item.get("id").getAsString() : "?";
					String name = item.has("name") ? item.get("name").getAsString() : id;
					boolean ui = item.has("ui") && item.get("ui").getAsBoolean();
					String lore = !ui && item.has("search") ? item.get("search").getAsString() : "";
					boolean outdated = item.has("outdated") && item.get("outdated").getAsBoolean();
					Entry entry = new Entry(id, name, lore, item.get("item"), Category.ofType(item.has("type") ? item.get("type").getAsString() : null), outdated);
					if (ui) {
						UI_ITEMS.add(entry);
					} else {
						ITEMS.add(entry);
						BY_NAME.computeIfAbsent(entry.name, key -> new ArrayList<>()).add(entry);
					}
					BY_ID.put(id, entry);
					Identifier badge = item.has("badge") && item.get("badge").isJsonPrimitive() ? Identifier.tryParse(item.get("badge").getAsString()) : null;
					if (badge != null) {
						entry.badge = new ItemStack(Items.PAPER);
						entry.badge.set(DataComponents.ITEM_MODEL, badge);
					}
					if (item.has("costIcon") && item.get("costIcon").isJsonPrimitive()) {
						COST_ICONS.put(id, item.get("costIcon").getAsString());
					}
				}
			}
		}
		NeuCrafts.receive(object);
		if (object.has("cosmetics") && object.get("cosmetics").isJsonObject()) {
			cosmeticDefaults = object.getAsJsonObject("cosmetics");
		}
		if (object.has("containers") && object.get("containers").isJsonArray()) {
			CONTAINERS.clear();
			for (JsonElement element : object.getAsJsonArray("containers")) {
				if (element.isJsonPrimitive()) {
					CONTAINERS.add(cleanName(element.getAsString()));
				}
			}
		}
		if (object.has("groups") && object.get("groups").isJsonArray()) {
			Map<String, Entry> byId = new HashMap<>();
			ITEMS.forEach(entry -> byId.put(entry.id, entry));
			GROUPS.clear();
			GROUP_BY_ID.clear();
			NESTED_GROUPS.clear();
			for (JsonElement element : object.getAsJsonArray("groups")) {
				if (element.isJsonObject()) {
					readGroup(element.getAsJsonObject(), byId);
				}
			}
			GROUPS.forEach(group -> group.members().stream().map(Member::group).filter(GROUP_BY_ID::containsKey).forEach(NESTED_GROUPS::add));
			GROUPS.removeIf(group -> group.entries().isEmpty());
		}
		refilter();
	}

	private static void readGroup(JsonObject object, Map<String, Entry> byId) {
		if (!object.has("items") || !object.get("items").isJsonArray()) {
			return;
		}
		String id = object.has("id") ? object.get("id").getAsString() : "?";
		String name = object.has("name") ? object.get("name").getAsString() : id;
		boolean collapsed = object.has("collapsed") && object.get("collapsed").getAsBoolean();
		int order = object.has("order") && object.get("order").isJsonPrimitive() && object.get("order").getAsJsonPrimitive().isNumber() ? object.get("order").getAsInt() : Integer.MAX_VALUE;
		List<Member> members = new ArrayList<>();
		for (JsonElement element : object.getAsJsonArray("items")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject member = element.getAsJsonObject();
			boolean icon = member.has("icon") && member.get("icon").getAsBoolean();
			boolean exclusive = member.has("exclusive") && member.get("exclusive").getAsBoolean();
			if (member.has("group") && member.get("group").isJsonPrimitive()) {
				members.add(new Member(null, member.get("group").getAsString(), icon, exclusive));
				continue;
			}
			Entry entry = member.has("item") ? byId.get(member.get("item").getAsString()) : null;
			if (entry != null) {
				members.add(new Member(entry, null, icon, exclusive));
			}
		}
		if (members.isEmpty()) {
			return;
		}
		List<Member> icons = members.stream().filter(Member::icon).toList();
		ItemStack iconStack = null;
		Identifier model = object.has("icon") && object.get("icon").isJsonPrimitive() ? Identifier.tryParse(object.get("icon").getAsString()) : null;
		if (model != null) {
			iconStack = new ItemStack(Items.PAPER);
			iconStack.set(DataComponents.ITEM_MODEL, model);
			iconStack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)));
		}
		Group group = new Group(id, name.toLowerCase(Locale.ROOT), collapsed, order, List.copyOf(members), icons.isEmpty() ? List.of(members.getFirst()) : icons, iconStack);
		GROUPS.add(group);
		GROUP_BY_ID.put(id, group);
	}

	private static void addMembers(Group group, List<Tile> tiles, Set<String> seen) {
		if (!seen.add(group.id())) {
			return;
		}
		for (Member member : group.members()) {
			Group subgroup = member.subgroup();
			if (subgroup != null) {
				if (subgroup.accepts(category)) {
					tiles.add(new Tile(null, subgroup));
					if (expanded(subgroup)) {
						addMembers(subgroup, tiles, seen);
					}
				}
			} else if (member.entry() != null && category.accepts(member.entry().category)) {
				tiles.add(new Tile(member.entry(), null));
			}
		}
	}

	// Search and category filter, groups opened or closed
	private static void refilter() {
		String lower = query.strip().toLowerCase(Locale.ROOT);
		if (!lower.isEmpty()) {
			Set<Entry> groupMatches = new HashSet<>();
			GROUPS.stream().filter(group -> group.name().contains(lower)).forEach(group -> groupMatches.addAll(group.entries()));
			filtered = ITEMS.stream().filter(entry -> category.accepts(entry.category) && (entry.matches(lower) || groupMatches.contains(entry)))
					.sorted(Comparator.comparingInt(entry -> entry.category.ordinal())).map(entry -> new Tile(entry, null)).toList();
			return;
		}
		Set<Entry> exclusive = new HashSet<>();
		GROUPS.forEach(group -> group.members().stream().filter(member -> member.exclusive() && member.entry() != null).forEach(member -> exclusive.add(member.entry())));
		List<Tile> units = new ArrayList<>();
		for (Entry entry : ITEMS) {
			if (!exclusive.contains(entry) && category.accepts(entry.category)) {
				units.add(new Tile(entry, null));
			}
		}
		GROUPS.stream().filter(group -> !NESTED_GROUPS.contains(group.id()) && group.accepts(category))
				.forEach(group -> units.add(new Tile(null, group)));
		units.sort(Comparator.comparingInt(Tile::sortCategory).thenComparingInt(Tile::sortOrder).thenComparing(Tile::sortName));
		List<Tile> tiles = new ArrayList<>();
		for (Tile unit : units) {
			tiles.add(unit);
			if (unit.isGroup() && expanded(unit.group())) {
				addMembers(unit.group(), tiles, new HashSet<>());
			}
		}
		filtered = List.copyOf(tiles);
	}

	private static boolean expanded(Group group) {
		return EXPANDED.contains(group.id()) == group.collapsed();
	}

	private static boolean available() {
		return !ITEMS.isEmpty() && MccIsland.isConnected() && Permissions.has(Permissions.Perm.SEE_ITEMS);
	}

	public static boolean shown() {
		return available() && !ModConfig.get().neuItemsHidden && eligible(Minecraft.getInstance().gui.screen());
	}

	private static boolean eligible(Screen screen) {
		if (screen instanceof InventoryScreen) {
			return true;
		}
		if (!(screen instanceof ContainerScreen container) || container.getMenu().slots.size() <= TITLE_SLOT) {
			return false;
		}
		ItemStack title = container.getMenu().getSlot(TITLE_SLOT).getItem();
		return !title.isEmpty() && CONTAINERS.contains(cleanName(title.getHoverName().getString()));
	}

	private static String cleanName(String name) {
		return name.replaceAll("[\uE000-\uF8FF]", "").strip().toLowerCase(Locale.ROOT);
	}


	private static void placeWidgets(Screen screen, ToggleSwitch toggle, EditBox search) {
		toggle.visible = available() && eligible(screen);
		search.visible = shown();
		if (!search.visible) {
			search.setFocused(false);
			return;
		}
		ContainerHoveredSlotAccessor container = (ContainerHoveredSlotAccessor) screen;
		search.setWidth(container.mccinfo$getImageWidth());
		search.setX(container.mccinfo$getLeftPos());
		search.setY(screen.height - SEARCH_HEIGHT - MARGIN);
	}

	private static Layout layout(Screen screen) {
		ContainerHoveredSlotAccessor container = (ContainerHoveredSlotAccessor) screen;
		return layout(screen.width, screen.height, container.mccinfo$getLeftPos() + container.mccinfo$getImageWidth());
	}

	static int maxColumns(int screenWidth, int inventoryRight) {
		return (screenWidth - MARGIN - inventoryRight - INVENTORY_GAP + GAP) / CELL_SIZE - REMOVED_COLUMNS;
	}

	static int columns(int screenWidth, int inventoryRight) {
		int max = maxColumns(screenWidth, inventoryRight);
		int chosen = ModConfig.get().neuColumns;
		return chosen > 0 ? Math.min(chosen, max) : max;
	}

	// List left of the inventory, as many columns as fit
	private static Layout layout(int screenWidth, int screenHeight, int inventoryRight) {
		Font font = Minecraft.getInstance().font;
		int columns = columns(screenWidth, inventoryRight);
		if (columns < 1) {
			return null;
		}
		int width = columns * CELL_SIZE - GAP;
		int left = screenWidth - MARGIN - width;
		int previousWidth = Math.max(font.width(Component.translatable("screen.mccinfo.neu_items.previous")),
				font.width(Component.translatable("screen.mccinfo.neu_items.next"))) + BOX_PADDING * 2;
		int nextWidth = previousWidth;
		int pageWidth = font.width(Component.translatable("screen.mccinfo.neu_items.page", 999, 999)) + BOX_PADDING * 2;
		boolean oneRow = previousWidth + pageWidth + nextWidth + GAP * 2 <= width;
		int headerHeight = oneRow ? BOX_HEIGHT : BOX_HEIGHT * 2 + GAP;
		int available = screenHeight - MARGIN * 2 - headerHeight - GAP - TABS_GAP - TAB_HEIGHT;
		int rows = (available + GAP) / CELL_SIZE;
		if (rows < 1) {
			return null;
		}
		int height = headerHeight + GAP + rows * CELL_SIZE - GAP + TABS_GAP + TAB_HEIGHT;
		int top = Math.max(MARGIN, (screenHeight - height) / 2);
		int right = left + width;
		Box previous;
		Box pageBox;
		Box next;
		if (oneRow) {
			previous = new Box(left, top, left + previousWidth, top + BOX_HEIGHT);
			next = new Box(right - nextWidth, top, right, top + BOX_HEIGHT);
			pageBox = new Box(previous.right() + GAP, top, next.left() - GAP, top + BOX_HEIGHT);
		} else {
			pageBox = new Box(left, top, right, top + BOX_HEIGHT);
			int rowTop = top + BOX_HEIGHT + GAP;
			int middle = left + (width - GAP) / 2;
			previous = new Box(left, rowTop, middle, rowTop + BOX_HEIGHT);
			next = new Box(middle + GAP, rowTop, right, rowTop + BOX_HEIGHT);
		}
		int gridTop = top + headerHeight + GAP;
		return new Layout(left, top, columns, rows, previous, pageBox, next, gridTop, gridTop + rows * CELL_SIZE - GAP + TABS_GAP);
	}

	private static int pages(Layout layout) {
		return Math.max(1, (filtered.size() + layout.perPage() - 1) / layout.perPage());
	}

	private static int indexAt(Layout layout, double mouseX, double mouseY) {
		if (mouseX < layout.left() || mouseY < layout.gridTop()) {
			return -1;
		}
		int column = (int) ((mouseX - layout.left()) / CELL_SIZE);
		int row = (int) ((mouseY - layout.gridTop()) / CELL_SIZE);
		if (column >= layout.columns() || row >= layout.rows()
				|| mouseX - layout.left() - column * CELL_SIZE >= SQUARE_SIZE || mouseY - layout.gridTop() - row * CELL_SIZE >= SQUARE_SIZE) {
			return -1;
		}
		int index = page * layout.perPage() + row * layout.columns() + column;
		return index < filtered.size() ? index : -1;
	}

	private static boolean scroll(Screen screen, double mouseX, double mouseY, double scrollY) {
		Layout layout = shown() ? layout(screen) : null;
		if (layout == null || scrollY == 0 || !layout.contains(mouseX, mouseY)) {
			return false;
		}
		int previous = page;
		page = Math.clamp(page - (int) Math.signum(scrollY), 0, pages(layout) - 1);
		if (page != previous) {
			clickSound();
		}
		return true;
	}

	// Clicks: tabs, pages, groups and items
	private static boolean click(Screen screen, MouseButtonEvent event) {
		Layout layout = shown() ? layout(screen) : null;
		if (layout == null || !layout.contains(event.x(), event.y())) {
			return false;
		}
		int index = indexAt(layout, event.x(), event.y());
		Category tab = layout.tabAt(event.x(), event.y());
		if (tab != null) {
			if (tab != category) {
				category = tab;
				page = 0;
				refilter();
				clickSound();
			}
		} else if (index >= 0 && filtered.get(index).isGroup()) {
			String group = filtered.get(index).group().id();
			if (!EXPANDED.remove(group)) {
				EXPANDED.add(group);
			}
			refilter();
			clickSound();
		} else if (index >= 0 && (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
			NeuCrafts.open(screen, filtered.get(index).entry().id, event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT);
		} else if (layout.previous().contains(event.x(), event.y()) && page > 0) {
			page--;
			clickSound();
		} else if (layout.next().contains(event.x(), event.y()) && page < pages(layout) - 1) {
			page++;
			clickSound();
		}
		return true;
	}

	public static void clickSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(CLICK_SOUND, 1.0F));
	}

	static void extractPreview(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, int inventoryRight, int mouseX, int mouseY) {
		Layout layout = available() ? layout(screenWidth, screenHeight, inventoryRight) : null;
		if (layout != null) {
			extract(null, layout, graphics, mouseX, mouseY);
		}
	}

	static int widestLeft(int screenWidth, int inventoryRight) {
		int columns = Math.max(1, maxColumns(screenWidth, inventoryRight));
		return screenWidth - MARGIN - (columns * CELL_SIZE - GAP);
	}

	static boolean previewAvailable() {
		return available();
	}

	private static void extract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Layout layout = shown() ? layout(screen) : null;
		if (layout == null) {
			return;
		}
		extract(screen, layout, graphics, mouseX, mouseY);
	}

	// Tabs, grid, pages and the hovered tooltip
	private static void extract(Screen screen, Layout layout, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.nextStratum();
		Font font = Minecraft.getInstance().font;
		int pages = pages(layout);
		page = Math.clamp(page, 0, pages - 1);
		extractBox(graphics, font, layout.page(), Component.translatable("screen.mccinfo.neu_items.page", page + 1, pages), false, true);
		extractBox(graphics, font, layout.previous(), Component.translatable("screen.mccinfo.neu_items.previous"),
				layout.previous().contains(mouseX, mouseY), page > 0);
		extractBox(graphics, font, layout.next(), Component.translatable("screen.mccinfo.neu_items.next"),
				layout.next().contains(mouseX, mouseY), page < pages - 1);

		int hovered = indexAt(layout, mouseX, mouseY);
		Group hoveredGroup = null;
		if (hovered >= 0 && filtered.get(hovered).isGroup()) {
			hoveredGroup = filtered.get(hovered).group();
			hovered = -1;
		}
		int first = page * layout.perPage();
		List<int[]> pluses = new ArrayList<>();
		List<Badge> badges = new ArrayList<>();
		for (int slot = 0; slot < layout.perPage(); slot++) {
			int x = layout.left() + slot % layout.columns() * CELL_SIZE;
			int y = layout.gridTop() + slot / layout.columns() * CELL_SIZE;
			int index = first + slot;
			Tile tile = index < filtered.size() ? filtered.get(index) : null;
			boolean outdated = tile != null && tile.outdated();
			int background = outdated ? (index == hovered ? OUTDATED_HOVER_COLOR : OUTDATED_COLOR) : (index == hovered ? HOVER_COLOR : BOX_COLOR);
			OverlayStyle.fillRounded(graphics, x, y, SQUARE_SIZE, SQUARE_SIZE, background);
			if (tile != null) {
				boolean open = tile.isGroup() && expanded(tile.group());
				if (tile.isGroup() && tile.group().variants()) {
					pluses.add(new int[] {x, y, open ? 1 : 0});
				} else if (tile.isGroup()) {
					underline(graphics, x, y, SQUARE_SIZE, SQUARE_SIZE, open ? GROUP_OPEN_COLOR : GROUP_COLOR);
				}
				ItemStack stack = tile.stack();
				if (stack.isEmpty()) {
					continue;
				}
				if (open) {
					GrayscaleItems.item(graphics, stack, x + 1, y + 1);
				} else {
					graphics.item(stack, x + 1, y + 1);
				}
				if (tile.badge() != null) {
					badges.add(new Badge(tile.badge(), x, y));
				}
			}
		}
		if (!pluses.isEmpty() || !badges.isEmpty()) {
			graphics.nextStratum();
			pluses.forEach(plus -> plus(graphics, plus[0], plus[1], plus[2] == 1));
			badges.forEach(badge -> badge(graphics, badge));
		}
		Category hoveredTab = layout.tabAt(mouseX, mouseY);
		for (Category tab : Category.values()) {
			int x = layout.tabLeft(tab);
			int width = layout.tabRight(tab) - x;
			int y = layout.tabsTop();
			extractTab(graphics, x, y, width, tab.icon(), null, tab == hoveredTab, tab == category);
		}
		if (hoveredTab != null) {
			graphics.setTooltipForNextFrame(font, hoveredTab.label(), mouseX, mouseY);
		}
		hoveredStack = ItemStack.EMPTY;
		if (hovered >= 0) {
			ItemStack stack = filtered.get(hovered).entry().stack();
			if (!stack.isEmpty()) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
				hoveredStack = stack;
				hoveredAt = System.currentTimeMillis();
			}
		}
		if (hoveredGroup != null) {
			groupTooltip(graphics, font, hoveredGroup, mouseX, mouseY);
		}
		if (hoveredGroup != null || (hovered >= 0 && NeuCrafts.hasCrafts(filtered.get(hovered).entry().id)) || (hoveredTab != null && hoveredTab != category)
				|| (layout.previous().contains(mouseX, mouseY) && page > 0) || (layout.next().contains(mouseX, mouseY) && page < pages - 1)) {
			pointer(graphics);
		}
		ItemStack carried = screen instanceof AbstractContainerScreen<?> container ? container.getMenu().getCarried() : ItemStack.EMPTY;
		if (!carried.isEmpty() && layout.contains(mouseX, mouseY)) {
			graphics.nextStratum();
			graphics.item(carried, mouseX - 8, mouseY - 8);
			graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
		}
	}

	private static void groupTooltip(GuiGraphicsExtractor graphics, Font font, Group group, int mouseX, int mouseY) {
		ItemStack stack = group.icon();
		if (stack.isEmpty()) {
			return;
		}
		List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
		long shown = group.members().stream().filter(member -> member.subgroup() != null ? member.subgroup().accepts(category)
				: member.entry() != null && category.accepts(member.entry().category)).count();
		long others = group.iconStack() != null ? shown : shown - 1;
		if (!lines.isEmpty() && others > 0) {
			lines.set(0, Component.empty().append(lines.getFirst()).append(Component.literal(" (+" + others + ")").withStyle(GROUP_COUNT_COLOR)));
		}
		graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), mouseX, mouseY, stack.get(DataComponents.TOOLTIP_STYLE));
	}

	static int categoryTabWidth(Screen screen) {
		Layout layout = layout(screen);
		return layout == null ? Integer.MAX_VALUE : (layout.width() + GAP) / Category.values().length - GAP;
	}

	static void extractTab(GuiGraphicsExtractor graphics, int x, int y, int width, ItemStack item, Identifier texture, boolean hovered, boolean selected) {
		OverlayStyle.fillRounded(graphics, x, y, width, TAB_HEIGHT, hovered ? HOVER_COLOR : BOX_COLOR);
		if (selected) {
			underline(graphics, x, y, width, TAB_HEIGHT, TAB_SELECTED_COLOR);
		}
		float icon = 16 * TAB_ICON_SCALE;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + (float) Math.floor((width - icon) / 2), y + (float) Math.floor((TAB_HEIGHT - icon) / 2));
		graphics.pose().scale(TAB_ICON_SCALE, TAB_ICON_SCALE);
		if (item != null && !item.isEmpty()) {
			graphics.item(item, 0, 0);
		} else if (texture != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
		}
		graphics.pose().popMatrix();
	}

	static void groupUnderline(GuiGraphicsExtractor graphics, int x, int y, boolean open) {
		underline(graphics, x, y, SQUARE_SIZE, SQUARE_SIZE, open ? GROUP_OPEN_COLOR : GROUP_COLOR);
	}

	private record Badge(ItemStack stack, int x, int y) {
	}

	private static void badge(GuiGraphicsExtractor graphics, Badge badge) {
		extractBadge(graphics, badge.stack(), badge.x() + SQUARE_SIZE - 1, badge.y() + SQUARE_SIZE - 1);
	}

	static void extractBadge(GuiGraphicsExtractor graphics, ItemStack stack, float right, float bottom) {
		extractBadge(graphics, stack, right, bottom, 1);
	}

	// Item in the corner of a slot, pixel accurate
	static void extractBadge(GuiGraphicsExtractor graphics, ItemStack stack, float right, float bottom, int zoom) {
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		float scale = (float) zoom / guiScale;
		float left = (Math.round(right * guiScale) - BADGE_PIXELS * zoom) / (float) guiScale;
		float top = (Math.round(bottom * guiScale) - BADGE_PIXELS * zoom) / (float) guiScale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(scale, scale);
		graphics.item(stack, 0, 0);
		graphics.pose().popMatrix();
	}

	static ItemStack badge(String id) {
		Entry entry = BY_ID.get(id);
		return entry == null ? null : entry.badge;
	}

	private static void plus(GuiGraphicsExtractor graphics, int x, int y, boolean open) {
		int centerX = x + SQUARE_SIZE - 4;
		int centerY = y + SQUARE_SIZE - 4;
		graphics.fill(centerX - 2, centerY - 1, centerX + 3, centerY + 2, PLUS_SHADOW_COLOR);
		if (open) {
			graphics.fill(centerX - 1, centerY, centerX + 2, centerY + 1, MINUS_COLOR);
			return;
		}
		graphics.fill(centerX - 1, centerY - 2, centerX + 2, centerY + 3, PLUS_SHADOW_COLOR);
		graphics.fill(centerX - 1, centerY, centerX + 2, centerY + 1, GROUP_COLOR);
		graphics.fill(centerX, centerY - 1, centerX + 1, centerY + 2, GROUP_COLOR);
	}

	private static void underline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
		graphics.fill(x, y + height - 2, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + height - 2, x + width, y + height - 1, color);
	}

	private static void extractBox(GuiGraphicsExtractor graphics, Font font, Box box, Component text, boolean hovered, boolean enabled) {
		extractBox(graphics, font, box.left(), box.top(), box.right(), box.bottom(), text, hovered, enabled);
	}

	static void extractBox(GuiGraphicsExtractor graphics, Font font, int left, int top, int right, int bottom, Component text, boolean hovered,
			boolean enabled) {
		OverlayStyle.fillRounded(graphics, left, top, right - left, bottom - top, hovered && enabled ? HOVER_COLOR : BOX_COLOR);
		graphics.text(font, text, (left + right - font.width(text)) / 2, top + (bottom - top - font.lineHeight) / 2 + 1,
				enabled ? TEXT_COLOR : DISABLED_COLOR, false);
	}

	static int navigationWidth(Font font) {
		return Math.max(font.width(Component.translatable("screen.mccinfo.neu_items.previous")),
				font.width(Component.translatable("screen.mccinfo.neu_items.next"))) + BOX_PADDING * 2;
	}
}
