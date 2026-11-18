package leroidesafk.mccinfo.client.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.chat.MccApi;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.util.ContainerWatch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lowest price icon on the Island Exchange listings
public final class ExchangeDeals {
	// Island Exchange menu, API query and listing slots
	private static final String TITLE = "island exchange";
	private static final int TITLE_SLOT = 8;
	private static final String SELECTION = "activeIslandExchangeListings { amount cost asset { name } }";
	private static final long CACHE_MILLIS = 60_000L;
	private static final Pattern PRICE_PATTERN = Pattern.compile("^Listed Price: \\D*([\\d,]+)");
	private static final String TOKEN_SUFFIX = " token";
	private static final List<int[]> LISTING_ROWS = List.of(new int[]{10, 16}, new int[]{19, 25}, new int[]{28, 34}, new int[]{37, 43},
			new int[]{46, 52});

	// Item name and amount of a listing
	private record Listing(String name, int amount) {
	}

	// Lowest price per listing, refreshed once a minute
	private static final ContainerWatch WATCH = new ContainerWatch();
	private static Map<Listing, Long> deals = new HashMap<>();
	private static boolean loaded;
	private static boolean inFlight;
	private static long expires;

	private ExchangeDeals() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ExchangeDeals::tick);
	}

	// Setting, utils permission, valid Api Key and MCC Island
	public static boolean allowed() {
		return ModConfig.get().exchangeLowestPrice && Permissions.has(Permissions.Perm.UTILS) && MccApi.ready() && OverlayStyle.onMccIsland();
	}

	// Icon on the listings at the lowest price
	public static void extractDealIcon(GuiGraphicsExtractor graphics, Slot slot) {
		if (!loaded || !allowed()) {
			return;
		}
		ContainerScreen screen = exchangeScreen(Minecraft.getInstance());
		if (screen == null || slot.container != screen.getMenu().getContainer() || !inListings(slot.getContainerSlot())) {
			return;
		}
		ItemStack stack = slot.getItem();
		Long price = price(stack);
		if (price == null) {
			return;
		}
		Long best = deals.get(listing(stack.getHoverName().getString(), stack.getCount()));
		if (best != null && best.longValue() == price) {
			SlotIndicators.topRight(graphics, slot, SlotIndicators.LOWEST_PRICE);
		}
	}

	// While the menu is open: API query when due, visible listings on change
	private static void tick(Minecraft client) {
		if (!allowed()) {
			return;
		}
		ContainerScreen screen = exchangeScreen(client);
		if (screen == null) {
			WATCH.reset();
			return;
		}
		if (!inFlight && System.currentTimeMillis() >= expires) {
			fetch(client);
		}
		Container container = screen.getMenu().getContainer();
		if (WATCH.changed(container)) {
			addVisible(container);
		}
	}

	// Active listings from the API
	private static void fetch(Minecraft client) {
		inFlight = true;
		MccApi.query(SELECTION).whenComplete((reply, error) -> {
			Map<Listing, Long> parsed = reply != null && reply.answered() ? parse(reply.value()) : null;
			client.execute(() -> receive(client, parsed));
		});
	}

	// Prices kept on success, icons hidden on failure
	private static void receive(Minecraft client, Map<Listing, Long> parsed) {
		inFlight = false;
		expires = System.currentTimeMillis() + CACHE_MILLIS;
		if (parsed == null) {
			loaded = false;
			return;
		}
		deals = parsed;
		loaded = true;
		ContainerScreen screen = exchangeScreen(client);
		if (screen != null) {
			addVisible(screen.getMenu().getContainer());
		}
	}

	// Cheapest cost per item and amount
	private static Map<Listing, Long> parse(JsonElement value) {
		if (value == null || !value.isJsonArray()) {
			return null;
		}
		Map<Listing, Long> parsed = new HashMap<>();
		for (JsonElement element : value.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject listing = element.getAsJsonObject();
			JsonObject asset = listing.has("asset") && listing.get("asset").isJsonObject() ? listing.getAsJsonObject("asset") : null;
			if (asset == null || !asset.has("name") || !listing.has("cost")) {
				continue;
			}
			int amount = listing.has("amount") ? listing.get("amount").getAsInt() : 1;
			parsed.merge(listing(asset.get("name").getAsString(), amount), listing.get("cost").getAsLong(), Math::min);
		}
		return parsed;
	}

	// Listings of the menu, in case they are cheaper
	private static void addVisible(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (!inListings(slot)) {
				continue;
			}
			ItemStack stack = container.getItem(slot);
			Long price = price(stack);
			if (price != null) {
				deals.merge(listing(stack.getHoverName().getString(), stack.getCount()), price, Math::min);
			}
		}
	}

	// Name without case, brackets or " Token"
	private static Listing listing(String name, int amount) {
		String clean = name.strip().toLowerCase(Locale.ROOT);
		if (clean.startsWith("[") && clean.endsWith("]")) {
			clean = clean.substring(1, clean.length() - 1).strip();
		}
		if (clean.endsWith(TOKEN_SUFFIX)) {
			clean = clean.substring(0, clean.length() - TOKEN_SUFFIX.length()).strip();
		}
		return new Listing(clean, amount);
	}

	// Last "Listed Price:" line of the lore
	private static Long price(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return null;
		}
		List<Component> lines = lore.lines();
		for (int i = lines.size() - 1; i >= 0; i--) {
			Matcher matcher = PRICE_PATTERN.matcher(lines.get(i).getString().strip());
			if (matcher.find()) {
				try {
					return Long.parseLong(matcher.group(1).replace(",", ""));
				} catch (NumberFormatException e) {
					return null;
				}
			}
		}
		return null;
	}

	// Listing slots, like Trident
	private static boolean inListings(int slot) {
		for (int[] row : LISTING_ROWS) {
			if (slot >= row[0] && slot <= row[1]) {
				return true;
			}
		}
		return false;
	}

	// Island Exchange by its title or slot 8
	private static ContainerScreen exchangeScreen(Minecraft client) {
		if (!(client.gui.screen() instanceof ContainerScreen screen)) {
			return null;
		}
		if (screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(TITLE)) {
			return screen;
		}
		Container container = screen.getMenu().getContainer();
		boolean named = container.getContainerSize() > TITLE_SLOT
				&& container.getItem(TITLE_SLOT).getHoverName().getString().toLowerCase(Locale.ROOT).contains(TITLE);
		return named ? screen : null;
	}
}
