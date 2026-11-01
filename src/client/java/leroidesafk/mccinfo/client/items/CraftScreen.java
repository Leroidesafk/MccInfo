package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Regions;
import leroidesafk.mccinfo.client.spots.SpotPath;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CraftScreen extends ContainerScreen {
	private static final int ROW_SIZE = 9;
	private static final int MAX_ROWS = 6;
	private static final int TITLE_OVERHANG = 24;
	private static final int BOTTOM_OVERHANG = 25;
	private static final int RESULT_GAP = 12;
	private static final int PLAYER_INVENTORY_HEIGHT = 97;
	private static final int RESULT_SCALE = 3;
	private static final int TAB_COLUMNS = 4;
	private static final int RESULT_SIZE = NeuItems.SQUARE_SIZE * RESULT_SCALE;
	private static final int NPC_WIDTH = RESULT_SIZE;
	private static final int NPC_HEIGHT = RESULT_SIZE * 5 / 3;
	private static final int NPC_ARROW_WIDTH = 14;
	// Path to the NPC
	private static final int NPC_PATH_COLOR = 0xFFAA00;
	private static final Identifier NPC_WAYPOINT_ICON = Mccinfo.id("textures/gui/recipes/vendor.png");
	// Frame around the searched item
	private static final Identifier FOCUS_FRAME = Mccinfo.id("textures/gui/recipes/selected.png");
	private static final int FOCUS_FRAME_SIZE = 20;
	// Rarity filter button
	private static final Identifier FILTER_MODEL = Identifier.fromNamespaceAndPath("mcc", "island_interface/generic/filter");
	private static final String FILTER_MARK = "▪";
	private static final int FILTER_MARK_COLOR = 0x505050;
	private static final int FILTER_SELECTED_COLOR = 0xFEE761;
	private static final int FILTER_OPTION_COLOR = 0x55FF56;
	private static final String BACK = "back";
	private static final String EXIT = "exit";
	private static final List<MenuType<ChestMenu>> TYPES = List.of(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
			MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);

	private final List<NeuCrafts.Craft> crafts;
	private final int index;
	private final int page;
	private final List<String> focus;
	private final Screen parent;
	private final Screen root;
	private final List<String> types;
	private final Set<String> expanded;
	private final Map<Integer, NeuCrafts.Reward> bundleSlots;
	private final Set<Integer> focusSlots;
	private final Map<Integer, ItemStack> badgeSlots;
	private final int filter;
	private final int filterSlot;
	private final int filterCount;
	private int npcIndex;

	private record Tab(String type, int left, int top, int right) {
		private boolean contains(double x, double y) {
			return x >= this.left && x < this.right && y >= this.top && y < this.top + NeuItems.TAB_HEIGHT;
		}
	}

	private CraftScreen(ChestMenu menu, Inventory inventory, Component title, List<NeuCrafts.Craft> crafts, int index, int page, Screen parent,
			List<String> focus, Set<String> expanded, Map<Integer, NeuCrafts.Reward> bundleSlots, Set<Integer> focusSlots, Map<Integer, ItemStack> badgeSlots,
			int filter) {
		super(menu, inventory, title);
		this.badgeSlots = badgeSlots;
		this.filter = filter;
		NeuCrafts.Fixed filterFixed = filterFixed(NeuCrafts.layout(crafts.get(index)));
		this.filterSlot = filterFixed == null ? -1 : filterFixed.slot();
		this.filterCount = filterFixed == null ? 0 : filterOptions(NeuCrafts.decode(filterFixed.item())).size();
		this.focusSlots = focusSlots;
		this.expanded = expanded;
		this.bundleSlots = bundleSlots;
		this.crafts = crafts;
		this.index = index;
		this.page = page;
		this.focus = focus;
		this.parent = parent;
		this.root = parent instanceof CraftScreen craftScreen ? craftScreen.root : parent;
		this.types = crafts.stream().map(NeuCrafts.Craft::type).distinct().toList();
	}

	static CraftScreen create(List<NeuCrafts.Craft> crafts, int index, Screen parent, List<String> focus) {
		Set<String> expanded = new HashSet<>();
		String target = target(crafts.get(index), focus);
		if (target != null) {
			crafts.get(index).items().stream().filter(reward -> reward.bundle() != null && reward.contains(target))
					.forEach(reward -> expanded.add(reward.bundle().id()));
		}
		return create(crafts, index, pageOf(crafts.get(index), target, expanded), parent, focus, expanded, 0);
	}

	// Filter button and its rarities
	private static NeuCrafts.Fixed filterFixed(NeuCrafts.Layout layout) {
		if (layout == null) {
			return null;
		}
		for (NeuCrafts.Fixed fixed : layout.fixed()) {
			if (FILTER_MODEL.equals(NeuCrafts.decode(fixed.item()).get(DataComponents.ITEM_MODEL))) {
				return fixed;
			}
		}
		return null;
	}

	private static List<String> filterOptions(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> options = new ArrayList<>();
		if (lore == null) {
			return options;
		}
		for (Component line : lore.lines()) {
			String text = line.getString();
			int mark = text.indexOf(FILTER_MARK);
			if (mark < 0) {
				continue;
			}
			String label = text.substring(mark + FILTER_MARK.length()).strip();
			if (label.startsWith(">")) {
				label = label.substring(1).strip();
			}
			options.add(label);
		}
		return options;
	}

	private static String rarity(NeuCrafts.Layout layout, int filter) {
		NeuCrafts.Fixed fixed = filter <= 0 ? null : filterFixed(layout);
		if (fixed == null) {
			return null;
		}
		List<String> options = filterOptions(NeuCrafts.decode(fixed.item()));
		return filter < options.size() ? options.get(filter).toLowerCase(Locale.ROOT).replace(' ', '_') : null;
	}

	private static boolean matches(NeuCrafts.Reward reward, String rarity) {
		if (rarity == null || rarity.equals(NeuItems.rarity(reward.item()))) {
			return true;
		}
		return reward.bundle() != null && reward.bundle().items().stream().anyMatch(member -> rarity.equals(NeuItems.rarity(member.item())));
	}

	// Chosen rarity marked in the lore
	private static ItemStack filterStack(ItemStack stack, int filter) {
		ItemStack copy = stack.copy();
		ItemLore lore = copy.get(DataComponents.LORE);
		if (lore == null) {
			return copy;
		}
		List<Component> lines = new ArrayList<>();
		int option = 0;
		for (Component line : lore.lines()) {
			String text = line.getString();
			int mark = text.indexOf(FILTER_MARK);
			if (mark < 0) {
				lines.add(line);
				continue;
			}
			String label = text.substring(mark + FILTER_MARK.length()).strip();
			if (label.startsWith(">")) {
				label = label.substring(1).strip();
			}
			boolean selected = option++ == filter;
			lines.add(Component.empty().withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false))
					.append(Component.literal(" " + FILTER_MARK + " ").withColor(FILTER_MARK_COLOR))
					.append(Component.literal(selected ? "> " + label : label).withColor(selected ? FILTER_SELECTED_COLOR : FILTER_OPTION_COLOR)));
		}
		copy.set(DataComponents.LORE, new ItemLore(lines));
		return copy;
	}

	// Item the screen was opened for
	private static String target(NeuCrafts.Craft craft, List<String> focus) {
		for (String id : focus) {
			if (craft.items().stream().anyMatch(reward -> reward.contains(id))) {
				return id;
			}
		}
		return null;
	}

	// Rewards shown with the filter and opened bundles
	private static List<NeuCrafts.Reward> shown(NeuCrafts.Craft craft, Set<String> expanded, String rarity) {
		List<NeuCrafts.Reward> shown = new ArrayList<>();
		for (NeuCrafts.Reward reward : craft.items()) {
			if (!matches(reward, rarity)) {
				continue;
			}
			shown.add(reward);
			if (reward.bundle() != null && expanded.contains(reward.bundle().id())) {
				reward.bundle().items().stream().filter(member -> matches(member, rarity)).forEach(shown::add);
			}
		}
		return shown;
	}

	// Page with the searched item
	private static int pageOf(NeuCrafts.Craft craft, String focus, Set<String> expanded) {
		NeuCrafts.Layout layout = NeuCrafts.layout(craft);
		if (focus == null || pages(craft, layout, expanded, null) <= 1) {
			return 0;
		}
		List<NeuCrafts.Reward> shown = shown(craft, expanded, null);
		for (int i = 0; i < shown.size(); i++) {
			if (focus.equals(shown.get(i).item())) {
				return i / layout.slots().size();
			}
		}
		return 0;
	}

	// Chest menu filled from the layout
	private static CraftScreen create(List<NeuCrafts.Craft> crafts, int index, int page, Screen parent, List<String> focus, Set<String> expanded,
			int filter) {
		Minecraft client = Minecraft.getInstance();
		NeuCrafts.Craft craft = crafts.get(index);
		String target = target(craft, focus);
		NeuCrafts.Layout layout = NeuCrafts.layout(craft);
		String rarity = rarity(layout, filter);
		int rows = Math.clamp((layout.size() + ROW_SIZE - 1) / ROW_SIZE, 1, MAX_ROWS);
		SimpleContainer container = new SimpleContainer(rows * ROW_SIZE);
		for (NeuCrafts.Fixed fixed : layout.fixed()) {
			if (fixed.slot() >= 0 && fixed.slot() < container.getContainerSize()) {
				ItemStack stack = NeuCrafts.decode(fixed.item());
				container.setItem(fixed.slot(), FILTER_MODEL.equals(stack.get(DataComponents.ITEM_MODEL)) ? filterStack(stack, filter) : stack);
			}
		}
		List<NeuCrafts.Reward> shown = shown(craft, expanded, rarity);
		Map<Integer, NeuCrafts.Reward> bundleSlots = new HashMap<>();
		Set<Integer> focusSlots = new HashSet<>();
		Map<Integer, ItemStack> badgeSlots = new HashMap<>();
		int first = page * layout.slots().size();
		for (int i = 0; first + i < shown.size() && i < layout.slots().size(); i++) {
			int slot = layout.slots().get(i);
			if (slot >= 0 && slot < container.getContainerSize()) {
				NeuCrafts.Reward reward = shown.get(first + i);
				container.setItem(slot, NeuCrafts.rewardStack(reward));
				if (reward.bundle() != null) {
					bundleSlots.put(slot, reward);
				}
				ItemStack badge = reward.item() == null ? null : NeuItems.badge(reward.item());
				if (badge != null) {
					badgeSlots.put(slot, badge);
				}
				if (target != null && (target.equals(reward.item())
						|| reward.bundle() != null && !expanded.contains(reward.bundle().id()) && reward.contains(target))) {
					focusSlots.add(slot);
				}
			}
		}
		if (page > 0) {
			place(container, layout.previous());
		}
		if (page < pages(craft, layout, expanded, rarity) - 1) {
			place(container, layout.next());
		}
		Inventory empty = new Inventory(client.player, new EntityEquipment());
		ChestMenu menu = new ChestMenu(TYPES.get(rows - 1), 0, empty, container, rows);
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot original = menu.slots.get(i);
			Slot filled = new Slot(original.container, original.getContainerSlot(), original.x, original.y) {
				@Override
				public boolean isActive() {
					return hasItem();
				}
			};
			filled.index = original.index;
			menu.slots.set(i, filled);
		}
		return new CraftScreen(menu, empty, NeuCrafts.title(layout), crafts, index, page, parent, focus, Set.copyOf(expanded), Map.copyOf(bundleSlots),
				Set.copyOf(focusSlots), Map.copyOf(badgeSlots), filter);
	}

	private static void place(SimpleContainer container, NeuCrafts.Fixed fixed) {
		if (fixed != null && fixed.slot() >= 0 && fixed.slot() < container.getContainerSize()) {
			container.setItem(fixed.slot(), NeuCrafts.decode(fixed.item()));
		}
	}

	private static int pages(NeuCrafts.Craft craft, NeuCrafts.Layout layout, Set<String> expanded, String rarity) {
		if (layout.slots().isEmpty() || layout.previous() == null || layout.next() == null) {
			return 1;
		}
		return Math.max(1, (shown(craft, expanded, rarity).size() + layout.slots().size() - 1) / layout.slots().size());
	}

	private int pages(NeuCrafts.Layout layout, Set<String> expanded) {
		return pages(this.crafts.get(this.index), layout, expanded, rarity(layout, this.filter));
	}

	private boolean isFilterSlot(Slot slot) {
		return this.filterCount > 1 && slot.getContainerSlot() == this.filterSlot;
	}

	private NeuCrafts.Reward bundleAt(Slot slot) {
		return slot == null || slot.container instanceof Inventory ? null : this.bundleSlots.get(slot.getContainerSlot());
	}

	// Underline under bundle slots
	private void extractBundles(GuiGraphicsExtractor graphics) {
		for (Slot slot : this.menu.slots) {
			NeuCrafts.Reward bundle = bundleAt(slot);
			if (bundle != null) {
				NeuItems.groupUnderline(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, this.expanded.contains(bundle.bundle().id()));
			}
		}
	}

	private boolean isPageSlot(Slot slot, NeuCrafts.Fixed fixed) {
		return fixed != null && slot.getContainerSlot() == fixed.slot();
	}

	private int pageTop() {
		return this.topPos - TITLE_OVERHANG - NeuItems.GAP - NeuItems.BOX_HEIGHT;
	}

	// Craft type tabs under the chest
	private List<Tab> tabs() {
		int top = this.topPos + this.imageHeight - PLAYER_INVENTORY_HEIGHT + BOTTOM_OVERHANG + NeuItems.TABS_GAP;
		int cap = NeuItems.categoryTabWidth(this);
		List<Tab> tabs = new ArrayList<>();
		for (int first = 0; first < this.types.size(); first += TAB_COLUMNS) {
			int count = Math.min(TAB_COLUMNS, this.types.size() - first);
			int width = Math.min(cap, (this.imageWidth + NeuItems.GAP) / count - NeuItems.GAP);
			int left = this.leftPos + (this.imageWidth - count * width - (count - 1) * NeuItems.GAP) / 2;
			for (int column = 0; column < count; column++) {
				int x = left + column * (width + NeuItems.GAP);
				tabs.add(new Tab(this.types.get(first + column), x, top, x + width));
			}
			top += NeuItems.TAB_HEIGHT + NeuItems.GAP;
		}
		return tabs;
	}

	private Tab tabAt(double x, double y) {
		return tabs().stream().filter(tab -> tab.contains(x, y)).findFirst().orElse(null);
	}

	private String type() {
		return this.crafts.get(this.index).type();
	}

	private void extractTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Tab hovered = tabAt(mouseX, mouseY);
		for (Tab tab : tabs()) {
			NeuCrafts.Layout layout = NeuCrafts.layoutForType(tab.type());
			NeuCrafts.Icon icon = layout == null ? null : layout.icon();
			ItemStack item = NeuCrafts.iconStack(icon);
			NeuItems.extractTab(graphics, tab.left(), tab.top(), tab.right() - tab.left(), item, icon == null ? null : icon.texture(),
					tab.equals(hovered), tab.type().equals(type()));
		}
		if (hovered != null) {
			NeuCrafts.Layout layout = NeuCrafts.layoutForType(hovered.type());
			graphics.setTooltipForNextFrame(this.font, Component.literal(layout == null ? hovered.type() : layout.title()), mouseX, mouseY);
			if (!hovered.type().equals(type())) {
				NeuItems.pointer(graphics);
			}
		}
	}

	private int navigationWidth() {
		return NeuItems.navigationWidth(this.font);
	}

	private boolean onPrevious(double x, double y) {
		return this.crafts.size() > 1 && x >= this.leftPos && x < this.leftPos + navigationWidth() && y >= pageTop() && y < pageTop() + NeuItems.BOX_HEIGHT;
	}

	private boolean onNext(double x, double y) {
		int right = this.leftPos + this.imageWidth;
		return this.crafts.size() > 1 && x >= right - navigationWidth() && x < right && y >= pageTop() && y < pageTop() + NeuItems.BOX_HEIGHT;
	}

	private int resultLeft() {
		return this.leftPos - RESULT_GAP - RESULT_SIZE;
	}

	private int resultTop() {
		return this.topPos + (this.imageHeight - PLAYER_INVENTORY_HEIGHT - RESULT_SIZE) / 2;
	}

	// NPCs/entity of the vendor
	private List<NeuNpcs.Npc> npcs() {
		NeuCrafts.Craft craft = this.crafts.get(this.index);
		NeuCrafts.Layout layout = craft.result() != null ? null : NeuCrafts.layout(craft);
		if (layout == null) {
			return List.of();
		}
		return layout.npcs().stream().map(NeuNpcs::get).filter(java.util.Objects::nonNull).toList();
	}

	private NeuNpcs.Npc npc() {
		List<NeuNpcs.Npc> npcs = npcs();
		return npcs.isEmpty() ? null : npcs.get(Math.clamp(this.npcIndex, 0, npcs.size() - 1));
	}

	private int npcSwitchTop() {
		return npcTop() + NPC_HEIGHT + NeuItems.GAP;
	}

	private boolean onNpcSwitch(double x, double y) {
		return y >= npcSwitchTop() && y < npcSwitchTop() + NeuItems.BOX_HEIGHT && npcs().size() > 1;
	}

	private boolean onNpcPrevious(double x, double y) {
		return onNpcSwitch(x, y) && x >= npcLeft() && x < npcLeft() + NPC_ARROW_WIDTH;
	}

	private boolean onNpcNext(double x, double y) {
		return onNpcSwitch(x, y) && x >= npcLeft() + NPC_WIDTH - NPC_ARROW_WIDTH && x < npcLeft() + NPC_WIDTH;
	}

	// Arrows between the NPCs
	private void extractNpcSwitch(GuiGraphicsExtractor graphics, int count, int mouseX, int mouseY) {
		int top = npcSwitchTop();
		int bottom = top + NeuItems.BOX_HEIGHT;
		int left = npcLeft();
		int right = left + NPC_WIDTH;
		int current = Math.clamp(this.npcIndex, 0, count - 1);
		boolean previous = onNpcPrevious(mouseX, mouseY);
		boolean next = onNpcNext(mouseX, mouseY);
		NeuItems.extractBox(graphics, this.font, left, top, left + NPC_ARROW_WIDTH, bottom, Component.literal("<"), previous, current > 0);
		NeuItems.extractBox(graphics, this.font, left + NPC_ARROW_WIDTH + NeuItems.GAP, top, right - NPC_ARROW_WIDTH - NeuItems.GAP, bottom,
				Component.literal((current + 1) + "/" + count), false, true);
		NeuItems.extractBox(graphics, this.font, right - NPC_ARROW_WIDTH, top, right, bottom, Component.literal(">"), next, current < count - 1);
		if (previous && current > 0 || next && current < count - 1) {
			NeuItems.pointer(graphics);
		}
	}

	private int npcLeft() {
		return this.leftPos - RESULT_GAP - NPC_WIDTH;
	}

	private int npcTop() {
		return this.topPos + (this.imageHeight - PLAYER_INVENTORY_HEIGHT - NPC_HEIGHT) / 2;
	}

	private boolean onNpc(double x, double y) {
		return x >= npcLeft() && x < npcLeft() + NPC_WIDTH && y >= npcTop() && y < npcTop() + NPC_HEIGHT;
	}

	// NPC preview and its tooltip
	private void extractNpc(GuiGraphicsExtractor graphics, NeuNpcs.Npc npc, int mouseX, int mouseY) {
		int left = npcLeft();
		int top = npcTop();
		OverlayStyle.fillRounded(graphics, left, top, NPC_WIDTH, NPC_HEIGHT, NeuItems.BOX_COLOR);
		NeuNpcs.extract(graphics, npc, left, top, left + NPC_WIDTH, top + NPC_HEIGHT, mouseX, mouseY);
		if (!onNpc(mouseX, mouseY)) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(npc.name()).withStyle(net.minecraft.ChatFormatting.GOLD));
		Regions.Region region = Regions.at(npc.position());
		if (region != null) {
			lines.add(Component.translatable("screen.mccinfo.npc.zone", Component.literal(region.name()).withStyle(net.minecraft.ChatFormatting.WHITE))
					.withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		if (SpotPath.canFocus() && this.minecraft.player != null) {
			if (SpotPath.canPathTo(npc.position())) {
				int distance = (int) Math.round(this.minecraft.player.position().distanceTo(npc.position()));
				lines.add(Component.translatable("screen.mccinfo.npc.path", distance).withStyle(net.minecraft.ChatFormatting.GRAY));
				NeuItems.pointer(graphics);
			} else {
				lines.add((region != null ? Component.translatable("screen.mccinfo.npc.go_to", region.name()) : Component.translatable("screen.mccinfo.npc.unreachable"))
						.withStyle(net.minecraft.ChatFormatting.RED));
			}
		}
		graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
	}

	private Identifier waypointIcon() {
		NeuCrafts.Layout layout = NeuCrafts.layoutForType(type());
		return layout != null && layout.icon() != null && layout.icon().texture() != null ? layout.icon().texture() : NPC_WAYPOINT_ICON;
	}

	private void pathTo(NeuNpcs.Npc npc) {
		if (this.minecraft.player == null || !SpotPath.canPathTo(npc.position())) {
			return;
		}
		int distance = (int) Math.round(this.minecraft.player.position().distanceTo(npc.position()));
		NeuItems.clickSound();
		NeuCrafts.Layout layout = NeuCrafts.layoutForType(type());
		ItemStack modelIcon = layout != null && layout.icon() != null && layout.icon().model() != null ? NeuCrafts.iconStack(layout.icon()) : null;
		if (modelIcon != null) {
			SpotPath.focus(npc.position(), NPC_PATH_COLOR, modelIcon);
		} else {
			SpotPath.focus(npc.position(), NPC_PATH_COLOR, waypointIcon());
		}
		this.minecraft.player.sendOverlayMessage(Component.translatable("message.mccinfo.npc.path", npc.name(), distance)
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		this.minecraft.gui.setScreen(null);
	}

	private boolean onResult(double x, double y) {
		return x >= resultLeft() && x < resultLeft() + RESULT_SIZE && y >= resultTop() && y < resultTop() + RESULT_SIZE;
	}

	// Frame on the searched item over the slots
	@Override
	protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!this.focusSlots.isEmpty()) {
			graphics.nextStratum();
			int offset = (FOCUS_FRAME_SIZE - 16) / 2;
			for (Slot slot : this.menu.slots) {
				if (!(slot.container instanceof Inventory) && slot.isActive() && this.focusSlots.contains(slot.getContainerSlot())) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, FOCUS_FRAME, slot.x - offset, slot.y - offset, 0.0F, 0.0F, FOCUS_FRAME_SIZE, FOCUS_FRAME_SIZE,
							FOCUS_FRAME_SIZE, FOCUS_FRAME_SIZE);
				}
			}
		}
		super.extractSlots(graphics, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.nextStratum();
		extractBundles(graphics);
		for (Slot slot : this.menu.slots) {
			ItemStack badge = slot.container instanceof Inventory || !slot.isActive() ? null : this.badgeSlots.get(slot.getContainerSlot());
			if (badge != null) {
				NeuItems.extractBadge(graphics, badge, this.leftPos + slot.x - 1 + NeuItems.SQUARE_SIZE - 1, this.topPos + slot.y - 1 + NeuItems.SQUARE_SIZE - 1);
			}
		}
		ItemStack result = this.crafts.get(this.index).result() == null ? ItemStack.EMPTY : NeuItems.stack(this.crafts.get(this.index).result());
		if (!result.isEmpty()) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(resultLeft(), resultTop());
			graphics.pose().scale(RESULT_SCALE, RESULT_SCALE);
			OverlayStyle.fillRounded(graphics, 0, 0, NeuItems.SQUARE_SIZE, NeuItems.SQUARE_SIZE, NeuItems.BOX_COLOR);
			graphics.item(result, 1, 1);
			graphics.pose().popMatrix();
			ItemStack resultBadge = NeuItems.badge(this.crafts.get(this.index).result());
			if (resultBadge != null) {
				graphics.nextStratum();
				NeuItems.extractBadge(graphics, resultBadge, resultLeft() + (NeuItems.SQUARE_SIZE - 1) * RESULT_SCALE,
						resultTop() + (NeuItems.SQUARE_SIZE - 1) * RESULT_SCALE, RESULT_SCALE);
			}
			if (onResult(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(this.font, result, mouseX, mouseY);
			}
		}
		if (this.crafts.size() > 1) {
			int top = pageTop();
			int bottom = top + NeuItems.BOX_HEIGHT;
			int width = navigationWidth();
			int right = this.leftPos + this.imageWidth;
			NeuItems.extractBox(graphics, this.font, this.leftPos, top, this.leftPos + width, bottom,
					Component.translatable("screen.mccinfo.neu_items.previous"), onPrevious(mouseX, mouseY), this.index > 0);
			NeuItems.extractBox(graphics, this.font, this.leftPos + width + NeuItems.GAP, top, right - width - NeuItems.GAP, bottom,
					Component.translatable("screen.mccinfo.neu_items.page", this.index + 1, this.crafts.size()), false, true);
			NeuItems.extractBox(graphics, this.font, right - width, top, right, bottom,
					Component.translatable("screen.mccinfo.neu_items.next"), onNext(mouseX, mouseY), this.index < this.crafts.size() - 1);
		}
		NeuNpcs.Npc npc = npc();
		if (npc != null) {
			extractNpc(graphics, npc, mouseX, mouseY);
			int count = npcs().size();
			if (count > 1) {
				extractNpcSwitch(graphics, count, mouseX, mouseY);
			}
		}
		extractTabs(graphics, mouseX, mouseY);
		if (clickable(mouseX, mouseY, result)) {
			NeuItems.pointer(graphics);
		}
	}

	// Pointer on clickable things
	private boolean clickable(int mouseX, int mouseY, ItemStack result) {
		if ((onPrevious(mouseX, mouseY) && this.index > 0) || (onNext(mouseX, mouseY) && this.index < this.crafts.size() - 1)) {
			return true;
		}
		if (!result.isEmpty() && onResult(mouseX, mouseY)) {
			return NeuCrafts.hasCrafts(this.crafts.get(this.index).result());
		}
		Slot slot = this.hoveredSlot;
		if (slot == null || slot.container instanceof Inventory || !slot.hasItem()) {
			return false;
		}
		NeuCrafts.Layout layout = NeuCrafts.layout(this.crafts.get(this.index));
		if ((this.page > 0 && isPageSlot(slot, layout.previous()))
				|| (this.page < pages(layout, this.expanded) - 1 && isPageSlot(slot, layout.next())) || bundleAt(slot) != null || isFilterSlot(slot)) {
			return true;
		}
		String name = slot.getItem().getHoverName().getString();
		if (BACK.equals(name.strip().toLowerCase(Locale.ROOT)) || EXIT.equals(name.strip().toLowerCase(Locale.ROOT))) {
			return true;
		}
		String id = NeuItems.idByName(name);
		return id != null && NeuCrafts.hasCrafts(id);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Tab tab = tabAt(event.x(), event.y());
		if (tab != null) {
			if (!tab.type().equals(type())) {
				for (int i = 0; i < this.crafts.size(); i++) {
					if (this.crafts.get(i).type().equals(tab.type())) {
						show(i);
						break;
					}
				}
			}
			return true;
		}
		if (onPrevious(event.x(), event.y())) {
			if (this.index > 0) {
				show(this.index - 1);
			}
			return true;
		}
		if (onNext(event.x(), event.y())) {
			if (this.index < this.crafts.size() - 1) {
				show(this.index + 1);
			}
			return true;
		}
		if (onNpcSwitch(event.x(), event.y())) {
			int count = npcs().size();
			int current = Math.clamp(this.npcIndex, 0, count - 1);
			int wanted = onNpcPrevious(event.x(), event.y()) ? current - 1 : onNpcNext(event.x(), event.y()) ? current + 1 : current;
			if (wanted != current && wanted >= 0 && wanted < count) {
				NeuItems.clickSound();
				this.npcIndex = wanted;
			}
			return true;
		}
		NeuNpcs.Npc npc = npc();
		if (npc != null && onNpc(event.x(), event.y())) {
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				pathTo(npc);
			}
			return true;
		}
		String result = this.crafts.get(this.index).result();
		if (result != null && onResult(event.x(), event.y())) {
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
				NeuCrafts.open(this, result, event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void show(int index) {
		NeuItems.clickSound();
		this.minecraft.gui.setScreen(create(this.crafts, index, this.parent, this.focus));
	}

	// Clicks: filter, pages, bundles, back, exit and items
	@Override
	protected void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput) {
		if (slot == null || slot.container instanceof Inventory || !slot.hasItem()) {
			return;
		}
		NeuCrafts.Layout layout = NeuCrafts.layout(this.crafts.get(this.index));
		if (isFilterSlot(slot)) {
			int next = buttonNum == 1 ? (this.filter + this.filterCount - 1) % this.filterCount : (this.filter + 1) % this.filterCount;
			NeuItems.clickSound();
			this.minecraft.gui.setScreen(create(this.crafts, this.index, 0, this.parent, this.focus, this.expanded, next));
			return;
		}
		if (this.page > 0 && isPageSlot(slot, layout.previous())) {
			NeuItems.clickSound();
			this.minecraft.gui.setScreen(create(this.crafts, this.index, this.page - 1, this.parent, this.focus, this.expanded, this.filter));
			return;
		}
		if (this.page < pages(layout, this.expanded) - 1 && isPageSlot(slot, layout.next())) {
			NeuItems.clickSound();
			this.minecraft.gui.setScreen(create(this.crafts, this.index, this.page + 1, this.parent, this.focus, this.expanded, this.filter));
			return;
		}
		NeuCrafts.Reward bundle = bundleAt(slot);
		if (bundle != null) {
			Set<String> expanded = new HashSet<>(this.expanded);
			if (!expanded.remove(bundle.bundle().id())) {
				expanded.add(bundle.bundle().id());
			}
			NeuItems.clickSound();
			int page = Math.min(this.page, pages(layout, expanded) - 1);
			this.minecraft.gui.setScreen(create(this.crafts, this.index, page, this.parent, this.focus, expanded, this.filter));
			return;
		}
		ItemStack stack = slot.getItem();
		String label = stack.getHoverName().getString().strip().toLowerCase(Locale.ROOT);
		if (BACK.equals(label) || EXIT.equals(label)) {
			NeuItems.clickSound();
			this.minecraft.gui.setScreen(this.parent);
			return;
		}
		String id = NeuItems.idByName(stack.getHoverName().getString());
		if (id != null && (containerInput == ContainerInput.PICKUP || containerInput == ContainerInput.QUICK_MOVE)) {
			NeuCrafts.open(this, id, buttonNum == 0);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.root);
	}
}
