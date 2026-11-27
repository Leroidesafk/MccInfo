package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.chat.MccApi;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// New cosmetic chance of the crates in vendor shops
public final class CrateChances {
	private static final String VENDOR = "vendor";
	private static final String LOOT_PREVIEW = "loot_preview";
	private static final String BLUEPRINT = "blueprint_";
	private static final String COSMETIC_FOLDER = "cos/";
	private static final Pattern TOKEN_SUFFIX = Pattern.compile("_token(_\\d+)?$");
	private static final Pattern GLYPHS = Pattern.compile("[\\uE000-\\uF8FF]");
	private static final long REFRESH_MILLIS = 60_000L;
	private static final int LABEL_COLOR = 0xFFFF55;
	private static final int VALUE_COLOR = 0xFFFFFF;

	private record Offer(String crate, String currency, int price, double chance) {
		double perItem() {
			return this.chance / this.price;
		}
	}

	private static Map<String, Boolean> owned = Map.of();
	private static long fetchedAt;
	private static boolean pending;
	private static Container openMenu;
	private static Map<String, Offer> offers = Map.of();
	private static Set<String> best = Set.of();
	private static Map<String, Boolean> computedWith;

	private CrateChances() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CrateChances::tick);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addChance(stack, lines));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			owned = Map.of();
			fetchedAt = 0L;
		});
	}

	static boolean enabled() {
		return ModConfig.get().crateChances && Permissions.has(Permissions.Perm.UTILS) && MccApi.ready() && OverlayStyle.onMccIsland();
	}

	private static void tick(Minecraft client) {
		if (!enabled() || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			clearMenu();
			return;
		}
		Container container = screen.getMenu().slots.isEmpty() ? null : screen.getMenu().slots.getFirst().container;
		NeuCrafts.Craft shop = container == null ? null : vendor(screen.getTitle().getString());
		if (shop == null) {
			clearMenu();
			return;
		}
		refresh();
		if (container != openMenu || computedWith != owned) {
			openMenu = container;
			computedWith = owned;
			compute(shop);
		}
	}

	private static void clearMenu() {
		openMenu = null;
		offers = Map.of();
		best = Set.of();
		computedWith = null;
	}

	// Owned cosmetics from the API, at most once a minute
	private static void refresh() {
		long now = System.currentTimeMillis();
		if (pending || fetchedAt != 0L && now - fetchedAt < REFRESH_MILLIS) {
			return;
		}
		pending = true;
		fetchedAt = now;
		MccApi.ownedCosmetics().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			pending = false;
			if (result != null) {
				owned = result;
			}
		}));
	}

	// NEU vendor found by the menu title
	private static NeuCrafts.Craft vendor(String title) {
		String wanted = clean(title);
		for (NeuCrafts.Craft craft : NeuCrafts.crafts()) {
			NeuCrafts.Layout layout = NeuCrafts.layout(craft);
			if (VENDOR.equals(craft.type()) && layout != null && layout.title() != null && !layout.title().isBlank() && wanted.contains(clean(layout.title()))) {
				return craft;
			}
		}
		return null;
	}

	// Chance of each crate and best one per currency
	private static void compute(NeuCrafts.Craft shop) {
		if (owned.isEmpty()) {
			offers = Map.of();
			best = Set.of();
			return;
		}
		Map<String, NeuCrafts.Craft> previews = new HashMap<>();
		for (NeuCrafts.Craft craft : NeuCrafts.crafts()) {
			if (LOOT_PREVIEW.equals(craft.type()) && craft.result() != null) {
				previews.putIfAbsent(craft.result(), craft);
			}
		}
		Map<String, Offer> found = new HashMap<>();
		Map<String, Offer> bestByCurrency = new HashMap<>();
		for (NeuCrafts.Reward reward : shop.items()) {
			NeuCrafts.Craft preview = reward.item() == null ? null : previews.get(reward.item());
			if (preview == null || !reward.sold() || reward.cost().size() != 1 || reward.cost().getFirst().count() <= 0) {
				continue;
			}
			Double chance = newChance(preview);
			ItemStack crate = NeuItems.stack(reward.item());
			if (chance == null || crate.isEmpty()) {
				continue;
			}
			NeuCrafts.Cost cost = reward.cost().getFirst();
			Offer offer = new Offer(clean(crate.getHoverName().getString()), cost.item(), cost.count(), chance);
			found.put(offer.crate(), offer);
			Offer current = bestByCurrency.get(offer.currency());
			if (chance > 0.0 && (current == null || offer.perItem() > current.perItem())) {
				bestByCurrency.put(offer.currency(), offer);
			}
		}
		offers = Map.copyOf(found);
		Set<String> names = new HashSet<>();
		bestByCurrency.values().forEach(offer -> names.add(offer.crate()));
		best = Set.copyOf(names);
	}

	// Chances of the cosmetics not owned
	private static Double newChance(NeuCrafts.Craft preview) {
		double total = 0.0;
		boolean any = false;
		for (NeuCrafts.Reward reward : preview.items()) {
			String name = cosmeticName(reward.item());
			if (name == null || reward.chance() == null) {
				continue;
			}
			any = true;
			if (!owned.getOrDefault(name, false)) {
				total += reward.chance() / 100.0;
			}
		}
		return any ? Math.min(1.0, total) : null;
	}

	// Cosmetic of a loot item, without blueprint or token
	private static String cosmeticName(String id) {
		if (id == null || !id.startsWith(COSMETIC_FOLDER)) {
			return null;
		}
		int slash = id.lastIndexOf('/') + 1;
		String file = id.substring(slash);
		if (file.startsWith(BLUEPRINT)) {
			file = file.substring(BLUEPRINT.length());
		}
		String base = id.substring(0, slash) + TOKEN_SUFFIX.matcher(file).replaceFirst("$1");
		ItemStack stack = NeuItems.stack(base);
		if (stack.isEmpty()) {
			stack = NeuItems.stack(id);
		}
		return stack.isEmpty() ? null : clean(stack.getHoverName().getString());
	}

	private static String clean(String text) {
		return GLYPHS.matcher(text).replaceAll("").strip().toLowerCase(Locale.ROOT);
	}

	private static void addChance(ItemStack stack, List<Component> lines) {
		if (offers.isEmpty() || lines.isEmpty() || !enabled()) {
			return;
		}
		Offer offer = offers.get(clean(stack.getHoverName().getString()));
		if (offer != null) {
			lines.add(1, chanceLine(offer));
		}
	}

	// New Cosmetic: X% (1/x per Y Currency)
	private static Component chanceLine(Offer offer) {
		String value = String.format(Locale.ROOT, "%.2f%%", offer.chance() * 100);
		if (offer.chance() > 0.0) {
			value += String.format(Locale.ROOT, " (1/%s per %d %s)", ratio(1.0 / offer.chance()), offer.price(), currencyName(offer.currency()));
		}
		String text = value;
		return Component.literal("New Cosmetic: ").withStyle(style -> style.withColor(LABEL_COLOR).withItalic(false))
				.append(Component.literal(text).withStyle(style -> style.withColor(VALUE_COLOR)));
	}

	private static String ratio(double value) {
		return value >= 10.0 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
	}

	private static String currencyName(String id) {
		ItemStack stack = NeuItems.stack(id);
		return stack.isEmpty() ? id : GLYPHS.matcher(stack.getHoverName().getString()).replaceAll("").strip();
	}

	// Arrow on the best crate
	public static void extractBestIcon(GuiGraphicsExtractor graphics, Slot slot) {
		if (best.isEmpty() || slot.container != openMenu || !slot.hasItem() || !enabled()) {
			return;
		}
		if (best.contains(clean(slot.getItem().getHoverName().getString()))) {
			SlotIndicators.topRight(graphics, slot, SlotIndicators.ARROW);
		}
	}
}
