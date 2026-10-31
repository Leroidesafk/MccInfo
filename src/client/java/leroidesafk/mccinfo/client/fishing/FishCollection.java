package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.items.ItemIcons;
import leroidesafk.mccinfo.client.util.AsyncFiles;
import leroidesafk.mccinfo.client.util.ContainerWatch;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Every fish and weight the player caught, saved in fish.json (replaced soon by the Api Token)
public final class FishCollection {
	// Fish have 4 weights, crabs 3
	public enum Weight {
		AVERAGE, LARGE, MASSIVE, GARGANTUAN,
		COLOSSAL;

		private static final List<Weight> FISH = List.of(AVERAGE, LARGE, MASSIVE, GARGANTUAN);
		private static final List<Weight> CRAB = List.of(AVERAGE, LARGE, COLOSSAL);

		private final String label = name().toLowerCase(Locale.ROOT);
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

	private static final Path SAVE_PATH = ModConfig.file("fish.json");
	private static final Gson GSON = new Gson();

	private static final int ALL_WEIGHTS = (1 << Weight.values().length) - 1;

	// Fish Collection menu: caught weights are written in green
	private static final int TITLE_SLOT = 8;
	private static final String TITLE_NAME = "fish collection";

	private static final Pattern WEIGHT_LINE_PATTERN = Pattern.compile("^\\W*(average|large|massive|gargantuan|colossal)\\b");
	private static final Pattern WEIGHTS_CAUGHT_PATTERN = Pattern.compile("weights caught:\\s*(\\d+)\\s*/");
	private static final int GREEN_MARGIN = 0x30;

	// Catch message
	private static final Pattern CAUGHT_PATTERN = Pattern.compile("You caught: \\[([^\\[\\]]+)]", Pattern.CASE_INSENSITIVE);
	private static final Pattern WEIGHT_PATTERN = Pattern.compile("\\b(average|large|massive|gargantuan|colossal)\\b");

	private static final Identifier CHECK_ICON = Mccinfo.id("textures/gui/check.png");
	private static final int CHECK_ICON_SIZE = 8;
	private static final int SLOT_SIZE = 16;

	private static List<Fish> FISH = List.of();
	private static Map<String, Fish> BY_NAME = Map.of();
	private static Set<String> ISLANDS = Set.of();
	private static Set<String> CRAB_CLIMATES = Set.of();
	// Caught weights per fish, one bit per weight
	private static final Map<String, Integer> CAUGHT = new HashMap<>();

	private static final ContainerWatch WATCH = new ContainerWatch();
	private static Container openCollection;

	private FishCollection() {
	}

	public static void register() {
		load();
		ClientTickEvents.END_CLIENT_TICK.register(FishCollection::readCollection);
		// Every chat message, even the ones hidden by other features
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
		WATCH.reset();
	}

	static Set<String> islands() {
		return ISLANDS;
	}

	static Set<String> crabClimates() {
		return CRAB_CLIMATES;
	}

	private static void load() {
		if (!Files.exists(SAVE_PATH)) {
			return;
		}
		try {
			Map<String, Integer> saved = GSON.fromJson(Files.readString(SAVE_PATH), new TypeToken<Map<String, Integer>>() { }.getType());
			if (saved != null) {
				saved.forEach((name, weights) -> {
					if (name != null && weights != null) {
						CAUGHT.put(name, weights & ALL_WEIGHTS);
					}
				});
			}
		} catch (IOException | JsonParseException e) {
			Mccinfo.LOGGER.warn("Unreadable fish collection ({})", SAVE_PATH, e);
		}
	}

	private static void save() {
		AsyncFiles.write(SAVE_PATH, GSON.toJson(CAUGHT));
	}

	static List<Fish> fish() {
		return FISH;
	}

	static boolean isCaught(Fish fish, Weight weight) {
		return (CAUGHT.getOrDefault(fish.name(), 0) & weight.bit) != 0;
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

	// The weight is either in the fish name or elsewhere in the message
	private static void onChatMessage(String message) {
		Matcher matcher = CAUGHT_PATTERN.matcher(message);
		if (!matcher.find()) {
			return;
		}
		String caught = matcher.group(1).trim().toLowerCase(Locale.ROOT);
		Fish fish = BY_NAME.get(caught);
		Weight weight = null;
		if (fish == null) {
			for (Weight candidate : Weight.values()) {
				if (caught.startsWith(candidate.label + " ")) {
					fish = BY_NAME.get(caught.substring(candidate.label.length() + 1).trim());
					weight = candidate;
				}
			}
		} else {
			Matcher weightMatcher = WEIGHT_PATTERN.matcher(message.toLowerCase(Locale.ROOT));
			if (weightMatcher.find()) {
				weight = Weight.valueOf(weightMatcher.group(1).toUpperCase(Locale.ROOT));
			}
		}
		if (fish == null || weight == null || !fish.weights().contains(weight)) {
			return;
		}
		int weights = CAUGHT.getOrDefault(fish.name(), 0);
		if ((weights & weight.bit) == 0) {
			CAUGHT.put(fish.name(), weights | weight.bit);
			save();
		}
	}

	// Reads the whole menu: it replaces what was saved
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
		openCollection = null;
		if (container.getContainerSize() <= TITLE_SLOT || !name(container.getItem(TITLE_SLOT)).contains(TITLE_NAME)) {
			return;
		}
		openCollection = container;

		boolean changed = false;
		boolean iconsChanged = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			Fish fish = stack.isEmpty() ? null : BY_NAME.get(name(stack));
			if (fish == null) {
				continue;
			}
			iconsChanged |= ItemIcons.remember(iconKey(fish), stack);
			int weights = caughtWeights(stack);
			if (weights < 0) {
				continue;
			}
			Integer previous = CAUGHT.put(fish.name(), weights);
			changed |= previous == null || previous != weights;
		}
		if (changed) {
			save();
		}
		if (iconsChanged) {
			ItemIcons.save();
		}
	}

	static String iconKey(Fish fish) {
		return fish.name().toLowerCase(Locale.ROOT);
	}

	private static int caughtWeights(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return -1;
		}
		int weights = 0;
		int listed = 0;
		int declared = -1;
		for (Component line : lore.lines()) {
			String text = line.getString().toLowerCase(Locale.ROOT);
			Matcher weightMatcher = WEIGHT_LINE_PATTERN.matcher(text);
			if (weightMatcher.find()) {
				Weight weight = Weight.valueOf(weightMatcher.group(1).toUpperCase(Locale.ROOT));
				listed |= weight.bit;
				if (isGreen(line, weight.label)) {
					weights |= weight.bit;
				}
				continue;
			}
			Matcher declaredMatcher = WEIGHTS_CAUGHT_PATTERN.matcher(text);
			if (declaredMatcher.find()) {
				declared = Integer.parseInt(declaredMatcher.group(1));
			}
		}
		if (listed == 0) {
			return -1;
		}
		// The "Weights caught" count wins over the colors
		if (declared == Integer.bitCount(listed)) {
			return listed;
		}
		return declared == 0 ? 0 : weights;
	}

	private static boolean isGreen(Component line, String word) {
		return line.visit((style, string) -> {
			if (!string.toLowerCase(Locale.ROOT).contains(word)) {
				return Optional.<Boolean>empty();
			}
			TextColor color = style.getColor();
			if (color == null) {
				return Optional.of(false);
			}
			int red = color.getValue() >> 16 & 0xFF;
			int green = color.getValue() >> 8 & 0xFF;
			int blue = color.getValue() & 0xFF;
			return Optional.of(green - red >= GREEN_MARGIN && green - blue >= GREEN_MARGIN);
		}, Style.EMPTY).orElse(false);
	}

	// Check icon on fish with every weight caught
	public static void extractCheckIcon(GuiGraphicsExtractor graphics, Slot slot) {
		if (openCollection == null || slot.container != openCollection) {
			return;
		}
		ItemStack stack = slot.getItem();
		Fish fish = stack.isEmpty() ? null : BY_NAME.get(name(stack));
		if (fish != null && (CAUGHT.getOrDefault(fish.name(), 0) & fish.allWeights()) == fish.allWeights()) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, CHECK_ICON, slot.x + SLOT_SIZE - CHECK_ICON_SIZE, slot.y + SLOT_SIZE - CHECK_ICON_SIZE, 0.0F, 0.0F,
					CHECK_ICON_SIZE, CHECK_ICON_SIZE, CHECK_ICON_SIZE, CHECK_ICON_SIZE);
		}
	}

	private static String name(ItemStack stack) {
		return stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
	}
}
