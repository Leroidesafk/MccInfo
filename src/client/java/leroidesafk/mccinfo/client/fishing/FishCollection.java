package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.chat.MccApi;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.util.ContainerWatch;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FishCollection {
	// Fish have 4 weights, crabs 3
	public enum Weight {
		AVERAGE, LARGE, MASSIVE, GARGANTUAN,
		COLOSSAL;

		private static final List<Weight> FISH = List.of(AVERAGE, LARGE, MASSIVE, GARGANTUAN);
		private static final List<Weight> CRAB = List.of(AVERAGE, LARGE, COLOSSAL);

		private final int bit = 1 << ordinal();
		public final String letter = name().substring(0, 1);
	}

	public enum Rarity {
		COMMON(0xFFFFFF),
		UNCOMMON(0x1EFF00),
		RARE(0x0070DD),
		EPIC(0xA335EE),
		LEGENDARY(0xFF8000),
		MYTHIC(0xF94242);

		public final int color;

		Rarity(int color) {
			this.color = color;
		}
	}

	public enum Time {
		DAY, NIGHT, ELUSIVE
	}

	public enum Status { NO_KEY, LOADING, HIDDEN, FAILED, READY }

	// Fish list sent by the relay
	public record Fish(String island, Rarity rarity, Time time, String name, boolean crab) {
		public List<Weight> weights() {
			return this.crab ? Weight.CRAB : Weight.FISH;
		}

		private int allWeights() {
			int all = 0;
			for (Weight weight : weights()) {
				all |= weight.bit;
			}
			return all;
		}
	}

	private static final Gson GSON = new Gson();

	private static final int TITLE_SLOT = 8;
	private static final String TITLE_NAME = "fish collection";
	private static final long REFRESH_MILLIS = 5 * 60_000L;
	private static final long OPEN_REFRESH_MILLIS = 30_000L;
	private static final long JOIN_MILLIS = 60_000L;

	private static final Identifier CHECK_ICON = Mccinfo.id("textures/gui/check.png");
	private static final int CHECK_ICON_SIZE = 8;
	private static final int SLOT_SIZE = 16;

	private static List<Fish> FISH = List.of();
	private static Map<String, Fish> BY_NAME = Map.of();
	private static Set<String> ISLANDS = Set.of();
	private static Set<String> CRAB_CLIMATES = Set.of();
	// Caught weights and trophies from the API
	private static Map<String, MccApi.FishRecord> RECORDS = Map.of();
	private static Map<String, Integer> CAUGHT = Map.of();
	private static Map<String, Map<String, Integer>> TROPHIES = Map.of();

	private static final ContainerWatch WATCH = new ContainerWatch();
	private static Container openCollection;

	private static Status status = Status.NO_KEY;
	private static boolean loading;
	private static long lastRequest;
	private static int generation;

	private FishCollection() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FishCollection::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (leroidesafk.mccinfo.client.PrivateKeys.FISH_COLLECTION.consumeClick()) {
				MccApi.KeyState state = MccApi.state();
				if (client.gui.screen() == null && allowed() && state != MccApi.KeyState.NONE && state != MccApi.KeyState.INVALID) {
					client.gui.setScreen(new FishCollectionScreen(null));
				}
			}
		});
		MccApi.onBatch(FishCollection::join);
	}

	static boolean allowed() {
		return Permissions.has(Permissions.Perm.UTILS);
	}

	private static void join() {
		if (!allowed()) {
			return;
		}
		if (MccApi.hasKey() && Minecraft.getInstance().player != null && System.currentTimeMillis() - lastRequest >= JOIN_MILLIS) {
			refresh();
		}
	}

	public static void receive(JsonObject object) {
		if (!object.has("fish") || !object.get("fish").isJsonArray()) {
			return;
		}
		List<Fish> received = new ArrayList<>();
		for (JsonElement element : object.getAsJsonArray("fish")) {
			try {
				Fish fish = GSON.fromJson(element, Fish.class);
				if (fish != null && fish.island() != null && fish.rarity() != null && fish.name() != null && (fish.crab() || fish.time() != null)) {
					received.add(fish);
				}
			} catch (JsonParseException e) {
				Mccinfo.LOGGER.warn("Unreadable fish in the relay list: {}", element);
			}
		}
		Map<String, Fish> byName = new HashMap<>();
		Set<String> islands = new HashSet<>();
		Set<String> climates = new HashSet<>();
		for (Fish fish : received) {
			byName.put(fish.name().toLowerCase(Locale.ROOT), fish);
			(fish.crab() ? climates : islands).add(fish.island().toLowerCase(Locale.ROOT));
		}
		FISH = List.copyOf(received);
		BY_NAME = Map.copyOf(byName);
		ISLANDS = Set.copyOf(islands);
		CRAB_CLIMATES = Set.copyOf(climates);
		applyRecords();
		WATCH.reset();
	}

	static Set<String> islands() {
		return ISLANDS;
	}

	static Set<String> crabClimates() {
		return CRAB_CLIMATES;
	}

	static List<Fish> fish() {
		return FISH;
	}

	static Status status() {
		return loading && status != Status.READY ? Status.LOADING : status;
	}

	static boolean isCaught(Fish fish, Weight weight) {
		return (CAUGHT.getOrDefault(fish.name(), 0) & weight.bit) != 0;
	}

	static boolean isComplete(Fish fish) {
		return (CAUGHT.getOrDefault(fish.name(), 0) & fish.allWeights()) == fish.allWeights();
	}

	static int caughtCount() {
		int count = 0;
		for (Fish fish : FISH) {
			count += Integer.bitCount(CAUGHT.getOrDefault(fish.name(), 0) & fish.allWeights());
		}
		return count;
	}

	static int totalCount() {
		int count = 0;
		for (Fish fish : FISH) {
			count += fish.weights().size();
		}
		return count;
	}

	static void refreshOnOpen() {
		if (!allowed()) {
			return;
		}
		if (MccApi.ready() && System.currentTimeMillis() - lastRequest >= OPEN_REFRESH_MILLIS) {
			refresh();
		}
	}

	private static void tick(Minecraft client) {
		if (!allowed()) {
			openCollection = null;
			return;
		}
		readCollection(client);
		MccApi.KeyState key = MccApi.state();
		if (key != MccApi.KeyState.VALID) {
			if (key == MccApi.KeyState.CHECKING) {
				return;
			}
			generation++;
			loading = false;
			lastRequest = 0;
			RECORDS = Map.of();
			CAUGHT = Map.of();
			TROPHIES = Map.of();
			status = key == MccApi.KeyState.UNREACHABLE ? Status.FAILED : Status.NO_KEY;
			return;
		}
		if (client.player != null && !loading && System.currentTimeMillis() - lastRequest >= REFRESH_MILLIS) {
			refresh();
		}
	}

	private static void refresh() {
		if (loading) {
			return;
		}
		loading = true;
		lastRequest = System.currentTimeMillis();
		int current = ++generation;
		MccApi.fishRecords().whenComplete((records, error) -> Minecraft.getInstance().execute(() -> {
			if (current != generation) {
				return;
			}
			loading = false;
			if (error != null || records == null || records.status() == MccApi.FishStatus.FAILED) {
				if (status != Status.READY) {
					status = Status.FAILED;
				}
				return;
			}
			if (records.status() == MccApi.FishStatus.HIDDEN) {
				RECORDS = Map.of();
				CAUGHT = Map.of();
				TROPHIES = Map.of();
				status = Status.HIDDEN;
				return;
			}
			RECORDS = records.weights();
			applyRecords();
			status = Status.READY;
		}));
	}

	private static void applyRecords() {
		Map<String, Integer> caught = new HashMap<>();
		Map<String, Map<String, Integer>> trophies = new HashMap<>();
		RECORDS.forEach((name, record) -> {
			Fish fish = BY_NAME.get(name);
			if (fish == null) {
				return;
			}
			int bits = 0;
			for (Weight weight : fish.weights()) {
				if (record.caught().contains(weight.name())) {
					bits |= weight.bit;
				}
			}
			caught.put(fish.name(), bits);
			trophies.put(fish.name(), record.trophies());
		});
		CAUGHT = Map.copyOf(caught);
		TROPHIES = Map.copyOf(trophies);
	}

	static int trophies(Fish fish, Weight weight) {
		return TROPHIES.getOrDefault(fish.name(), Map.of()).getOrDefault(weight.name(), 0);
	}

	static int caughtTrophies(Fish fish) {
		int total = 0;
		for (Weight weight : fish.weights()) {
			total += isCaught(fish, weight) ? trophies(fish, weight) : 0;
		}
		return total;
	}

	static int totalTrophies(Fish fish) {
		int total = 0;
		for (Weight weight : fish.weights()) {
			total += trophies(fish, weight);
		}
		return total;
	}

	// Fish Collection menu open
	private static void readCollection(Minecraft client) {
		if (!(client.gui.screen() instanceof ContainerScreen screen)) {
			openCollection = null;
			WATCH.reset();
			return;
		}
		Container container = screen.getMenu().getContainer();
		if (!WATCH.changed(container)) {
			return;
		}
		boolean collection = container.getContainerSize() > TITLE_SLOT && name(container.getItem(TITLE_SLOT)).contains(TITLE_NAME);
		openCollection = collection ? container : null;
	}

	// Check icon on fish with every weight caught
	public static void extractCheckIcon(GuiGraphicsExtractor graphics, Slot slot) {
		if (!allowed()) {
			return;
		}
		if (openCollection == null || slot.container != openCollection) {
			return;
		}
		ItemStack stack = slot.getItem();
		Fish fish = stack.isEmpty() ? null : BY_NAME.get(name(stack));
		if (fish != null && isComplete(fish)) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, CHECK_ICON, slot.x + SLOT_SIZE - CHECK_ICON_SIZE, slot.y + SLOT_SIZE - CHECK_ICON_SIZE, 0.0F, 0.0F,
					CHECK_ICON_SIZE, CHECK_ICON_SIZE, CHECK_ICON_SIZE, CHECK_ICON_SIZE);
		}
	}

	private static String name(ItemStack stack) {
		return stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
	}
}
