package leroidesafk.mccinfo.client.events;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.EventsScreen;
import leroidesafk.mccinfo.client.gui.MovableOverlay;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.MccServer;
import leroidesafk.mccinfo.client.util.ContainerWatch;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CarnivalTicketOverlay {
	// Ticket types
	private enum Ticket {
		ORANGE(0xFFFFAA00, false),
		RED(0xFFFF5555, true),
		GREEN(0xFF55FF55, true),
		BLUE(0xFF5599FF, true),
		PURPLE(0xFFAA55FF, true),
		WHITE(0xFFFFFFFF, true);

		private static final String TICKET_NAME = "carnival ticket";
		private static final String SCRAP_SUFFIX = " scrap";

		private final String key = name().toLowerCase(Locale.ROOT);
		private final boolean scrap;
		private final String itemName;
		private final Identifier texture;
		private final int textColor;

		Ticket(int textColor, boolean scrap) {
			this.textColor = textColor;
			this.scrap = scrap;
			this.itemName = scrap ? this.key + " " + TICKET_NAME + SCRAP_SUFFIX : TICKET_NAME;
			this.texture = Identifier.fromNamespaceAndPath("mcc", "textures/island_items/infinibag/event/halloween_carnival_ticket" + (scrap ? "_scrap_" + this.key : "") + ".png");
		}

		private boolean matches(String name) {
			return name.contains(this.itemName) && (this.scrap || !name.contains(TICKET_NAME + SCRAP_SUFFIX));
		}

		private int count() {
			return ModConfig.get().tickets.getOrDefault(this.key, 0);
		}
	}

	private static final Identifier TITLE_ICON = Ticket.ORANGE.texture;

	private static final int BAG_SLOT = 8;
	private static final List<String> BAG_NAMES = List.of("infibag", "infinibag");

	// Chat messages
	private static final Pattern SCRAP_PATTERN = Pattern.compile(
			"You (?:receive|caught): \\[(Red|Green|Blue|Purple|White) Carnival Ticket Scrap](?:\\s*x\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
	private static final Pattern TICKET_PATTERN = Pattern.compile(
			"You receive: \\[Carnival Ticket](?:\\s*x\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
	private static final int SCRAPS_PER_TICKET = 3; //it can change last time it was 5 scraps :(

	private static final int ICON_SIZE = 12;
	private static final int CELL_SPACING = 4;
	private static final int COUNT_SPACING = 2;
	private static final int MAX_SHOWN_COUNT = 999;
	private static final int FULL_SIZE_CHARS = 2;
	private static final int HEIGHT = OverlayStyle.TITLE_HEIGHT + OverlayStyle.PADDING * 2 + ICON_SIZE + COUNT_SPACING + OverlayStyle.FONT_HEIGHT;
	private static final int DEFAULT_X = 4;

	// Island icon menus
	private static final String ISLANDS_SCREEN_TITLE = "FISHING ISLANDS";
	private static final String DOJO_SCREEN_TITLE = "PARKOUR WARRIOR DOJO";
	private static final String NAVIGATOR_SCREEN_TITLE = "NAVIGATOR";
	private static final int MAX_CACHED_SLOTS = 1024;
	private static final ContainerWatch WATCH = new ContainerWatch();
	private static final Map<ItemStack, List<Ticket>> SLOT_TICKETS = new IdentityHashMap<>();
	private static Screen iconScreen;
	private static int iconStep;
	private static final int SLOT_ICON_SIZE = 8;
	private static final int SLOT_SIZE = 16;
	private static final int TITLE_COLOR = 0x7FFF8C1A;

	// Edit HUD box
	public static final MovableOverlay MOVABLE = new MovableOverlay() {
		@Override
		public boolean enabled() {
			return EventsScreen.carnivalTickets();
		}

		@Override
		public boolean visible() {
			return enabled() && !hidden() && (!ModConfig.get().ticketsFishingOnly || MccServer.onFishing());
		}

		@Override
		public int width() {
			return CarnivalTicketOverlay.width();
		}

		@Override
		public int height() {
			return HEIGHT;
		}

		@Override
		public int x(int screenWidth) {
			int x = ModConfig.get().ticketsX;
			return OverlayStyle.clamp(x == ModConfig.UNSET ? DEFAULT_X : x, screenWidth - width());
		}

		@Override
		public int y(int screenHeight) {
			int y = ModConfig.get().ticketsY;
			return OverlayStyle.clamp(y == ModConfig.UNSET ? (screenHeight - HEIGHT) / 2 : y, screenHeight - HEIGHT);
		}

		@Override
		public void setPosition(int x, int y) {
			ModConfig.get().ticketsX = x;
			ModConfig.get().ticketsY = y;
		}

		@Override
		public boolean hidden() {
			return ModConfig.get().ticketsHidden;
		}

		@Override
		public void setHidden(boolean hidden) {
			ModConfig.get().ticketsHidden = hidden;
		}

		@Override
		public void extractPreview(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
			extract(graphics, x, y, alpha);
		}
	};

	private CarnivalTicketOverlay() {
	}

	public static void register() {
		OverlayStyle.registerHud(Mccinfo.id("carnival_tickets"), CarnivalTicketOverlay::extractHud);
		// Re-read the tickets every tick while the Infinibag is open
		ClientTickEvents.END_CLIENT_TICK.register(CarnivalTicketOverlay::readInfinibag);
		// Also read messages that another feature hid from the chat (Fishing Messages box)
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onChatMessage(message.getString());
			}
		});
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
			if (!overlay) {
				onChatMessage(message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, boundChatType, timeStamp) -> onChatMessage(message.getString()));
	}

	private static void onChatMessage(String message) {
		if (!EventsScreen.carnivalTickets()) {
			return;
		}
		// Crafted tickets: +N tickets, -X of each scrap per ticket
		Matcher crafted = TICKET_PATTERN.matcher(message);
		if (crafted.find()) {
			int tickets = crafted.group(1) == null ? 1 : Integer.parseInt(crafted.group(1));
			Map<String, Integer> saved = ModConfig.get().tickets;
			for (Ticket ticket : Ticket.values()) {
				if (ticket.scrap) {
					saved.put(ticket.key, Math.max(0, ticket.count() - tickets * SCRAPS_PER_TICKET));
				}
			}
			saved.merge(Ticket.ORANGE.key, tickets, Integer::sum);
			ModConfig.get().save();
			return;
		}
		// Received or caught scraps: "You receive: [Red Carnival Ticket Scrap] x4"
		Matcher matcher = SCRAP_PATTERN.matcher(message);
		if (!matcher.find()) {
			return;
		}
		Ticket ticket = Ticket.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
		int amount = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
		ModConfig.get().tickets.merge(ticket.key, amount, Integer::sum);
		ModConfig.get().save();
	}

	private static void readInfinibag(Minecraft client) {
		// Only inside a chest menu
		if (!EventsScreen.carnivalTickets() || !(client.gui.screen() instanceof ContainerScreen screen)) {
			WATCH.reset();
			return;
		}
		Container container = screen.getMenu().getContainer();
		// Skip when nothing changed since the last read
		if (!WATCH.changed(container) || container.getContainerSize() <= BAG_SLOT) {
			return;
		}
		// MCC names the menu with the item in slot 8
		String bagName = name(container.getItem(BAG_SLOT));
		if (BAG_NAMES.stream().noneMatch(bagName::contains)) {
			return;
		}

		// Count every ticket and scrap in the bag
		Map<Ticket, Integer> counts = new EnumMap<>(Ticket.class);
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String itemName = name(stack);
			for (Ticket ticket : Ticket.values()) {
				if (ticket.matches(itemName)) {
					counts.merge(ticket, amount(stack), Integer::sum);
				}
			}
		}
		// No ticket on this page: keep the current saved counts
		if (counts.isEmpty()) {
			return;
		}

		// The bag is the real count: replace the saved values
		Map<String, Integer> saved = ModConfig.get().tickets;
		boolean changed = false;
		for (Ticket ticket : Ticket.values()) {
			int count = counts.getOrDefault(ticket, 0);
			Integer previous = saved.put(ticket.key, count);
			changed |= previous == null || previous != count;
		}
		if (changed) {
			ModConfig.get().save();
		}
	}

	// Island icons on slots
	public static void extractIslandIcon(GuiGraphicsExtractor graphics, Slot slot) {
		if (!EventsScreen.ticketIcons()) {
			return;
		}
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen == null) {
			return;
		}
		if (screen != iconScreen) {
			iconScreen = screen;
			String title = screen.getTitle().getString().toUpperCase(Locale.ROOT);
			iconStep = title.contains(ISLANDS_SCREEN_TITLE) ? SLOT_ICON_SIZE
					: title.contains(DOJO_SCREEN_TITLE) || title.contains(NAVIGATOR_SCREEN_TITLE) ? -SLOT_ICON_SIZE : 0;
			SLOT_TICKETS.clear();
		}
		if (iconStep == 0) {
			return;
		}
		int step = iconStep;
		int iconX = step > 0 ? slot.x : slot.x + SLOT_SIZE - SLOT_ICON_SIZE;
		if (SLOT_TICKETS.size() >= MAX_CACHED_SLOTS) {
			SLOT_TICKETS.clear();
		}
		List<Ticket> tickets = SLOT_TICKETS.computeIfAbsent(slot.getItem(), CarnivalTicketOverlay::scrapTickets);
		for (Ticket ticket : tickets) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, ticket.texture, iconX, slot.y + SLOT_SIZE - SLOT_ICON_SIZE, 0.0F, 0.0F,
					SLOT_ICON_SIZE, SLOT_ICON_SIZE, SLOT_ICON_SIZE, SLOT_ICON_SIZE);
			iconX += step;
		}
	}

	// Scraps from the lore
	private static List<Ticket> scrapTickets(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		List<Ticket> tickets = new ArrayList<>();
		for (Component line : lore.lines()) {
			String text = line.getString().toLowerCase(Locale.ROOT);
			for (Ticket ticket : Ticket.values()) {
				if (ticket.scrap && !tickets.contains(ticket) && text.contains("[" + ticket.itemName + "]")) {
					tickets.add(ticket);
				}
			}
		}
		return List.copyOf(tickets);
	}

	// Item amount
	private static int amount(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			Optional<Integer> amount = data.copyTag().getCompound("PublicBukkitValues").flatMap(values -> values.getInt("mcc:amount"));
			if (amount.isPresent()) {
				return amount.get();
			}
		}
		return stack.getCount();
	}

	private static String name(ItemStack stack) {
		return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!MOVABLE.visible() || EventsScreen.editingOverlays()) {
			return;
		}
		extract(graphics, MOVABLE.x(graphics.guiWidth()), MOVABLE.y(graphics.guiHeight()), 1.0F);
	}

	private static int cellWidth() {
		Font font = Minecraft.getInstance().font;
		return Math.max(ICON_SIZE, font.width(OverlayStyle.hudText("0".repeat(FULL_SIZE_CHARS))));
	}

	private static int width() {
		int cells = Ticket.values().length;
		return OverlayStyle.PADDING * 2 + cells * cellWidth() + (cells - 1) * CELL_SPACING;
	}

	private static String countString(Ticket ticket) {
		int count = ticket.count();
		return count > MAX_SHOWN_COUNT ? "+" + MAX_SHOWN_COUNT : String.valueOf(count);
	}

	private static Component countText(Ticket ticket) {
		return OverlayStyle.hudText(countString(ticket));
	}

	private static void extract(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
		Font font = Minecraft.getInstance().font;
		OverlayStyle.extractFrame(graphics, x, y, width(), HEIGHT, TITLE_COLOR, TITLE_ICON, "TICKETS", alpha);

		// Ticket first, then by count
		Ticket[] sorted = Ticket.values();
		Arrays.sort(sorted, Comparator.comparing((Ticket ticket) -> ticket.scrap).thenComparing(Comparator.comparingInt(Ticket::count).reversed()));

		int cellWidth = cellWidth();
		int cellX = x + OverlayStyle.PADDING;
		int iconY = y + OverlayStyle.TITLE_HEIGHT + OverlayStyle.PADDING;
		int countY = iconY + ICON_SIZE + COUNT_SPACING;
		for (Ticket ticket : sorted) {
			int iconX = cellX + (cellWidth - ICON_SIZE) / 2;
			graphics.blit(RenderPipelines.GUI_TEXTURED, ticket.texture, iconX, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE,
					OverlayStyle.withAlpha(OverlayStyle.TEXT_COLOR, alpha));
			Component count = countText(ticket);
			int countWidth = font.width(count);
			float scale = Math.min(1.0F, (float) cellWidth / countWidth);
			graphics.pose().pushMatrix();
			graphics.pose().translate(cellX + (cellWidth - countWidth * scale) / 2.0F, countY + OverlayStyle.FONT_HEIGHT * (1.0F - scale) / 2.0F);
			graphics.pose().scale(scale, scale);
			graphics.text(font, count, 0, 0, OverlayStyle.withAlpha(ticket.textColor, alpha), false);
			graphics.pose().popMatrix();
			cellX += cellWidth + CELL_SPACING;
		}
	}
}
