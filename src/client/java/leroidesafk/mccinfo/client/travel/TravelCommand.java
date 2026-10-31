package leroidesafk.mccinfo.client.travel;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.relay.Permissions;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class TravelCommand {
	// Navigator and timings
	private static final String NAVIGATOR_ITEM = "Navigator";
	private static final String NAVIGATOR_TITLE = "NAVIGATOR";
	private static final int TIMEOUT_TICKS = 100;
	private static final int CLICK_WINDOW_TICKS = 20;
	private static final int MUTE_AFTER_TICKS = 10;
	private static final String CLICK_SOUND_PREFIX = "ui.click";

	// /travel fishing islands
	private static final List<String> FISHING_ISLANDS = List.of(
			"Verdant Woods", "Floral Forest", "Dark Grove", "Sunken Swamp",
			"Tropical Overgrowth", "Coral Shores", "Twisted Swamp", "Mirrored Oasis",
			"Ancient Sands", "Blazing Canyon", "Ashen Wastes", "Volcanic Springs");

	// /travel destinations of the Navigator
	private static final java.util.Map<String, String> NAVIGATOR_DESTINATIONS = java.util.Map.of(
			"hub", "Island Entrance",
			"parkour_warrior_dojo", "Parkour Warrior Dojo",
			"skybattle", "Sky Battle",
			"battlebox", "Battle Box",
			"dynaball", "Dynaball",
			"tgttos", "To Get To The Other Side",
			"hole_in_the_wall", "Hole in the Wall",
			"rocket_spleef_rush", "Rocket Spleef Rush");

	// Menu to wait for and item to click in it
	private record Step(String title, String item) {
	}

	// Current trip
	private static List<Step> steps;
	private static int ticksLeft;
	private static boolean navigatorUsed;
	private static int clickContainer;
	private static int clickTicks;
	private static boolean lastClicked;
	private static AbstractContainerScreen<?> hidden;
	private static int muteTicks;
	// Inventory items kept while the menus refresh
	private static final java.util.Map<Integer, ItemStack> FROZEN = new java.util.HashMap<>();

	private TravelCommand() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TravelCommand::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			// /travel fishing <island> and the other destinations
			LiteralArgumentBuilder<FabricClientCommandSource> fishing = literal("fishing");
			for (String island : FISHING_ISLANDS) {
				fishing.then(literal(island.toLowerCase(Locale.ROOT).replace(' ', '_'))
						.executes(context -> start(List.of(new Step(NAVIGATOR_TITLE, "Fishing"), new Step("FISHING ISLANDS", island)))));
			}
			LiteralArgumentBuilder<FabricClientCommandSource> travel = literal("travel");
			travel.requires(source -> allowed()).then(fishing);
			NAVIGATOR_DESTINATIONS.forEach((command, item) ->
					travel.then(literal(command).executes(context -> start(List.of(new Step(NAVIGATOR_TITLE, item))))));
			dispatcher.register(travel);
		});
	}

	static boolean allowed() {
		return MccIsland.isConnected() && Permissions.has(Permissions.Perm.SEE_ITEMS);
	}

	private static int start(List<Step> path) {
		if (!allowed()) {
			return 0;
		}
		steps = path;
		ticksLeft = TIMEOUT_TICKS;
		navigatorUsed = false;
		clickContainer = -1;
		clickTicks = 0;
		lastClicked = false;
		hidden = null;
		FROZEN.clear();
		return 1;
	}

	private static void tick(Minecraft client) {
		// Click sounds muted during the trip and a bit after
		if (steps != null) {
			muteTicks = MUTE_AFTER_TICKS;
		} else {
			if (muteTicks > 0) {
				muteTicks--;
			}
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || !allowed()) {
			steps = null;
			hidden = null;
			return;
		}
		if (--ticksLeft <= 0) {
			fail(client, "message.mccinfo.travel.timeout");
			return;
		}
		// Open the Navigator from the hotbar
		if (!navigatorUsed) {
			Screen open = client.gui.screen();
			if (open instanceof ChatScreen) {
				return;
			}
			if (open != null) {
				if (open instanceof AbstractContainerScreen<?>) {
					player.closeContainer();
				} else {
					client.gui.setScreen(null);
				}
				return;
			}
			int slot = navigatorSlot(player.getInventory());
			if (slot < 0) {
				fail(client, "message.mccinfo.travel.no_navigator");
				return;
			}
			Inventory inventory = player.getInventory();
			int previous = inventory.getSelectedSlot();
			inventory.setSelectedSlot(slot);
			client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			inventory.setSelectedSlot(previous);
			navigatorUsed = true;
			return;
		}
		// Click the item of the open step
		AbstractContainerScreen<?> container = hidden != null && player.containerMenu == hidden.getMenu() ? hidden
				: client.gui.screen() instanceof AbstractContainerScreen<?> open ? open : null;
		if (lastClicked && (container == null || container.getMenu().containerId != clickContainer)) {
			steps = null;
			hidden = null;
			return;
		}
		int index = container == null ? -1 : stepOf(container);
		if (index < 0) {
			return;
		}
		AbstractContainerMenu menu = container.getMenu();
		Slot target = find(menu, steps.get(index).item());
		if (target == null) {
			return;
		}
		if (menu.containerId != clickContainer) {
			clickContainer = menu.containerId;
			clickTicks = CLICK_WINDOW_TICKS;
			ticksLeft = TIMEOUT_TICKS;
		}
		// Gives up if the click does nothing
		if (clickTicks-- <= 0) {
			hidden = null;
			player.closeContainer();
			fail(client, "message.mccinfo.travel.no_reaction");
			return;
		}
		client.gameMode.handleContainerInput(menu.containerId, target.index, 0, ContainerInput.PICKUP, player);
		if (index == steps.size() - 1) {
			lastClicked = true;
		}
	}

	// MCC click sounds muted while travelling
	public static boolean mutes(Identifier sound) {
		return (steps != null || muteTicks > 0) && sound.getNamespace().equals("mcc") && sound.getPath().startsWith(CLICK_SOUND_PREFIX);
	}

	// Inventory shown frozen during the trip
	public static boolean freezes(Inventory inventory, int slot, ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (steps == null || !navigatorUsed || player == null || (slot >= Inventory.INVENTORY_SIZE && slot != Inventory.SLOT_OFFHAND) || inventory != player.getInventory()) {
			return false;
		}
		if (hidden == null || player.containerMenu != player.inventoryMenu) {
			FROZEN.put(slot, stack.copy());
			return true;
		}
		return false;
	}

	// Menus of the trip stay hidden
	public static boolean hide(Screen screen) {
		if (steps == null || !(screen instanceof AbstractContainerScreen<?> container) || stepOf(container) < 0) {
			return false;
		}
		hidden = container;
		return true;
	}

	private static int stepOf(AbstractContainerScreen<?> screen) {
		String title = clean(screen.getTitle().getString());
		for (int index = steps.size() - 1; index >= 0; index--) {
			if (steps.get(index).title().equalsIgnoreCase(title)) {
				return index;
			}
		}
		return -1;
	}

	private static int navigatorSlot(Inventory inventory) {
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.COMPASS) && clean(stack.getHoverName().getString()).equalsIgnoreCase(NAVIGATOR_ITEM)) {
				return slot;
			}
		}
		return -1;
	}

	private static Slot find(AbstractContainerMenu menu, String name) {
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory) && named(slot, name)) {
				return slot;
			}
		}
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory && named(FROZEN.getOrDefault(slot.getContainerSlot(), slot.getItem()), name)) {
				return slot;
			}
		}
		return null;
	}

	private static boolean named(Slot slot, String name) {
		return named(slot.getItem(), name);
	}

	private static boolean named(ItemStack stack, String name) {
		return !stack.isEmpty() && clean(stack.getHoverName().getString()).equalsIgnoreCase(name);
	}

	private static String clean(String text) {
		StringBuilder builder = new StringBuilder(text.length());
		// Without the private glyphs of MCC
		text.codePoints().filter(point -> point < 0xE000 || point > 0xF8FF).forEach(builder::appendCodePoint);
		return builder.toString().strip();
	}

	private static void fail(Minecraft client, String key) {
		steps = null;
		if (hidden != null && client.player != null && client.player.containerMenu == hidden.getMenu()) {
			client.player.closeContainer();
		}
		hidden = null;
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[MccInfo] ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable(key).withStyle(ChatFormatting.RED)));
		}
	}
}
