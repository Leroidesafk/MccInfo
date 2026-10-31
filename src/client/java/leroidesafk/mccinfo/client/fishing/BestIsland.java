package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.items.ItemIcons;
import leroidesafk.mccinfo.client.util.ContainerWatch;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.BossHealthOverlayAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Island with the best chance of a new fish, read from the player's perks and supplies
public final class BestIsland {
	// Fishing Perks menu
	private static final String PERKS_TITLE = "fishing perks";
	private static final int TITLE_SLOT = 8;
	private static final Pattern PERK_PATTERN = Pattern.compile("^(.+?)\\s*\\((\\d+)\\s*/\\s*\\d+\\)$");

	// Supplies menu
	private static final String OVERCLOCKS_TITLE = "supply preserve perk";
	private static final int OVERCLOCKS_SLOT = 1;
	private static final String OVERCLOCKED_PERK_HEADER = "overclocked perk";
	private static final Pattern SELECTED_PERK_PATTERN = Pattern.compile(">\\s*(.+)$");
	private static final String HOOK_OVERCLOCK = "hook overclock";
	private static final String ROD_OVERCLOCK = "rod overclock";
	private static final String UNSTABLE_OVERCLOCK = "unstable overclock";

	private static final int BAIT_SLOT = 19;
	private static final int LINE_SLOT = 37;
	private static final int[] AUGMENT_SLOTS = {30, 31, 32, 33, 34, 39, 40, 41, 42, 43};
	private static final String EMPTY_SLOT_MARKER = "supply slot";
	private static final Pattern BONUS_PATTERN = Pattern.compile("\\+\\s*(\\d+)[^a-z]*([a-z]+(?: [a-z]+)*)");
	private static final String ELUSIVE_LURE = "elusive lure";
	private static final String ELUSIVE_SODA = "elusive soda";
	private static final String RARITY_ROD = "rarity rod";
	private static final Set<String> NO_FISH_LURES = Set.of("spirit lure", "treasure lure", "pearl lure");

	private static final String STRONG_HOOK = "strong hook";
	private static final String WISE_HOOK = "wise hook";
	private static final Set<String> STRONG_RODS = Set.of("boosted rod", "strong rod");
	private static final String ELUSIVE_PERK = "elusive";

	// Fishing Islands menu
	private static final int LEVEL_SLOT = 10;
	private static final Pattern LEVEL_PATTERN = Pattern.compile("^fishing level \\d+$");
	private static final String LOCKED_MARKER = "locked!";

	// Day from 8:30 to 20:30, read in the boss bar
	private static final Pattern TIME_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)");
	private static final int DAY_START = 8 * 60 + 30;
	private static final int DAY_END = 20 * 60 + 30;

	private static final Set<String> GROTTO_ISLANDS = Set.of("sunken swamp", "mirrored oasis", "volcanic springs");

	// Catch odds
	private static final double FISH_PER_CATCH = 0.4;
	private static final double GROTTO_REGULAR_SHARE = 0.5;
	private static final double HOOK_MULTIPLIER_PER_POINT = 0.1;
	private static final double ROD_MULTIPLIER_PER_POINT = 0.01;
	private static final double ELUSIVE_CHANCE_PER_POINT = 0.005;
	private static final double ELUSIVE_SODA_CHANCE = 0.1;
	private static final double RARITY_ROD_MULTIPLIER = 3.0;
	private static final double LARGE_BASE = 0.075;
	private static final double MASSIVE_BASE = 0.02;
	private static final double GARGANTUAN_BASE = 0.002;
	private static final double UNCOMMON_BASE = 0.25;
	private static final double RARE_BASE = 0.15;
	private static final double EPIC_BASE = 0.04;
	private static final double LEGENDARY_BASE = 0.01;
	private static final double MYTHIC_BASE = 0.002;

	private static final Identifier BEST_ICON = Mccinfo.id("textures/gui/best.png");
	private static final int ICON_SIZE = 8;
	private static final int ICON_TEXTURE = 16;
	private static final int CHANCE_LABEL_COLOR = 0xFFFF55;
	private static final int CHANCE_VALUE_COLOR = 0xFFFFFF;

	private static final String CRAB_MARKER = "crab";
	private static final int SLOT_SIZE = 16;

	private static final ContainerWatch WATCH = new ContainerWatch();

	private static boolean night;
	private static Container openIslands;
	private static Set<String> bestIslands = Set.of();
	private static Map<String, Double> dayChances = Map.of();
	private static Map<String, Double> nightChances = Map.of();

	private BestIsland() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BestIsland::tick);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addChances(stack, lines));
	}

	private static void tick(Minecraft client) {
		if (!(client.gui.screen() instanceof ContainerScreen screen)) {
			openIslands = null;
			bestIslands = Set.of();
			WATCH.reset();
			return;
		}
		Container container = screen.getMenu().getContainer();
		if (!WATCH.changed(container)) {
			if (openIslands == container) {
				boolean wasNight = night;
				readBossBars(client);
				if (night != wasNight) {
					bestIslands = bestIslands(container, night ? nightChances : dayChances);
				}
			}
			return;
		}
		// Which menu is open
		openIslands = null;
		bestIslands = Set.of();
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		if (title.contains(PERKS_TITLE) || container.getContainerSize() > TITLE_SLOT && name(container.getItem(TITLE_SLOT)).contains(PERKS_TITLE)) {
			readPerks(container);
		} else if (container.getContainerSize() > OVERCLOCKS_SLOT && name(container.getItem(OVERCLOCKS_SLOT)).contains(OVERCLOCKS_TITLE)) {
			readPerks(container);
			readEquipment(container);
		} else if (container.getContainerSize() > LEVEL_SLOT && LEVEL_PATTERN.matcher(name(container.getItem(LEVEL_SLOT))).matches()) {
			readBossBars(client);
			readIslandIcons(container);
			openIslands = container;
			dayChances = chances(FishCollection.Time.DAY);
			nightChances = chances(FishCollection.Time.NIGHT);
			bestIslands = bestIslands(container, night ? nightChances : dayChances);
		}
	}

	// Island and crab icons for the Fish Collection
	private static void readIslandIcons(Container container) {
		boolean changed = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String name = name(stack);
			if (FishCollection.islands().contains(name)) {
				changed |= ItemIcons.remember(name, stack);
				continue;
			}
			for (String climate : FishCollection.crabClimates()) {
				if (name.startsWith(climate) && name.contains(CRAB_MARKER)) {
					changed |= ItemIcons.remember(crabIconKey(climate), stack);
				}
			}
		}
		if (changed) {
			ItemIcons.save();
		}
	}

	static String crabIconKey(String climate) {
		return climate.toLowerCase(Locale.ROOT) + " crabs";
	}

	// Perk levels and their overclock, saved in the config
	private static void readPerks(Container container) {
		Map<String, Integer> perks = ModConfig.get().perks;
		Map<String, String> overclocks = ModConfig.get().overclocks;
		boolean changed = false;
		boolean iconsChanged = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			Matcher matcher = stack.isEmpty() ? null : PERK_PATTERN.matcher(name(stack));
			if (matcher != null && matcher.matches()) {
				int level = Integer.parseInt(matcher.group(2));
				String perk = matcher.group(1).trim();
				Integer previous = perks.put(perk, level);
				changed |= previous == null || previous != level;
				iconsChanged |= ItemIcons.remember(perk, stack);
				String selected = selectedPerk(stack);
				if (selected != null) {
					changed |= !selected.equals(overclocks.put(perk, selected));
				}
			}
		}
		if (changed) {
			ModConfig.get().save();
		}
		if (iconsChanged) {
			ItemIcons.save();
		}
	}

	// Bait, line and augments with their bonuses
	private static void readEquipment(Container container) {
		Map<String, Integer> bonuses = new HashMap<>();
		List<String> augments = new ArrayList<>();
		addBonuses(container, BAIT_SLOT, bonuses);
		addBonuses(container, LINE_SLOT, bonuses);
		boolean iconsChanged = false;
		for (int slot : AUGMENT_SLOTS) {
			addBonuses(container, slot, bonuses);
			String augment = equipped(container, slot);
			if (augment != null) {
				augments.add(augment);
				iconsChanged |= ItemIcons.remember(augment, container.getItem(slot));
			}
		}
		String bait = equipped(container, BAIT_SLOT);
		String line = equipped(container, LINE_SLOT);
		iconsChanged |= bait != null && ItemIcons.remember(bait, container.getItem(BAIT_SLOT));
		iconsChanged |= line != null && ItemIcons.remember(line, container.getItem(LINE_SLOT));
		ModConfig config = ModConfig.get();
		if (!bonuses.equals(config.equipmentBonuses) || !augments.equals(config.augments)
				|| !Objects.equals(bait, config.bait) || !Objects.equals(line, config.line)) {
			config.equipmentBonuses = bonuses;
			config.augments = augments;
			config.bait = bait;
			config.line = line;
			config.save();
		}
		if (iconsChanged) {
			ItemIcons.save();
		}
	}

	private static String equipped(Container container, int slot) {
		if (slot >= container.getContainerSize() || container.getItem(slot).isEmpty()) {
			return null;
		}
		String name = name(container.getItem(slot));
		return name.contains(EMPTY_SLOT_MARKER) ? null : name;
	}

	private static void addBonuses(Container container, int slot, Map<String, Integer> bonuses) {
		ItemLore lore = slot < container.getContainerSize() ? container.getItem(slot).get(DataComponents.LORE) : null;
		if (lore == null) {
			return;
		}
		for (Component line : lore.lines()) {
			Matcher matcher = BONUS_PATTERN.matcher(line.getString().trim().toLowerCase(Locale.ROOT));
			if (matcher.find()) {
				bonuses.merge(matcher.group(2), Integer.parseInt(matcher.group(1)), Integer::sum);
			}
		}
	}

	static int equipment(String perk) {
		return ModConfig.get().equipmentBonuses.getOrDefault(perk, 0);
	}

	static int overclockBonus(String perk) {
		int bonus = 0;
		for (Map.Entry<String, String> overclock : ModConfig.get().overclocks.entrySet()) {
			String selected = overclock.getValue();
			if (selected != null && !selected.isEmpty() && !UNSTABLE_OVERCLOCK.equals(overclock.getKey()) && (perk.equals(selected) || perk.startsWith(selected + " "))) {
				bonus += perk(overclock.getKey());
			}
		}
		return bonus;
	}

	private static int augments(String name) {
		int count = 0;
		for (String augment : ModConfig.get().augments) {
			if (augment.contains(name)) {
				count++;
			}
		}
		return count;
	}

	private static String selectedPerk(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return null;
		}
		boolean listing = false;
		for (Component line : lore.lines()) {
			String text = line.getString().trim().toLowerCase(Locale.ROOT);
			if (text.contains(OVERCLOCKED_PERK_HEADER)) {
				listing = true;
			} else if (listing) {
				if (text.isEmpty()) {
					return null;
				}
				Matcher matcher = SELECTED_PERK_PATTERN.matcher(text);
				if (matcher.find()) {
					return matcher.group(1).trim();
				}
			}
		}
		return null;
	}

	private static boolean isLocked(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (line.getString().toLowerCase(Locale.ROOT).contains(LOCKED_MARKER)) {
					return true;
				}
			}
		}
		return false;
	}

	private static int overclock(String overclock, Set<String> perks) {
		String selected = ModConfig.get().overclocks.get(overclock);
		return selected != null && perks.contains(selected) ? perk(overclock) : 0;
	}

	// Day or night from the clock in the boss bar
	private static void readBossBars(Minecraft client) {
		Map<?, LerpingBossEvent> bossBars = ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).mccinfo$getEvents();
		boolean timeRead = false;
		for (LerpingBossEvent bossBar : bossBars.values()) {
			String text = bossBar.getName().getString().toLowerCase(Locale.ROOT);
			Matcher timeMatcher = TIME_PATTERN.matcher(text);
			if (!timeRead && timeMatcher.find()) {
				int minutes = Integer.parseInt(timeMatcher.group(1)) * 60 + Integer.parseInt(timeMatcher.group(2));
				night = minutes < DAY_START || minutes >= DAY_END;
				timeRead = true;
			}
		}
	}

	static int perk(String name) {
		return ModConfig.get().perks.getOrDefault(name, 0);
	}

	// Points = perk + overclock + equipment
	private static int hookPoints(String hook) {
		return perk(hook) + overclock(HOOK_OVERCLOCK, Set.of(hook)) + equipment(hook);
	}

	private static int strongRodPoints() {
		int points = overclock(ROD_OVERCLOCK, STRONG_RODS);
		for (String rod : STRONG_RODS) {
			points += perk(rod) + equipment(rod);
		}
		return points;
	}

	private static double elusiveChance() {
		if (augments(ELUSIVE_LURE) > 0) {
			return 1.0;
		}
		int elusivePerk = 0;
		for (Map.Entry<String, Integer> perk : ModConfig.get().perks.entrySet()) {
			if (perk.getKey().contains(ELUSIVE_PERK) && !perk.getKey().contains("overclock")) {
				elusivePerk = perk.getValue();
			}
		}
		return Math.min(1.0, (elusivePerk * ELUSIVE_CHANCE_PER_POINT + augments(ELUSIVE_SODA) * ELUSIVE_SODA_CHANCE)
				* (1 + strongRodPoints() * ROD_MULTIPLIER_PER_POINT));
	}

	private static double hookMultiplier(String hook) {
		return 1 + hookPoints(hook) * HOOK_MULTIPLIER_PER_POINT;
	}

	// Shown by the Fish Collection debug
	static List<String> debugLines() {
		ModConfig config = ModConfig.get();
		return List.of(
				String.format(Locale.ROOT, "Strong Hook: %d + %d overclock + %d equip = %d pts (x%.2f)", perk(STRONG_HOOK),
						overclock(HOOK_OVERCLOCK, Set.of(STRONG_HOOK)), equipment(STRONG_HOOK), hookPoints(STRONG_HOOK), hookMultiplier(STRONG_HOOK)),
				String.format(Locale.ROOT, "Wise Hook: %d + %d overclock + %d equip = %d pts (x%.2f)", perk(WISE_HOOK),
						overclock(HOOK_OVERCLOCK, Set.of(WISE_HOOK)), equipment(WISE_HOOK), hookPoints(WISE_HOOK), hookMultiplier(WISE_HOOK)),
				String.format(Locale.ROOT, "Strong Rod: %d pts | Elusive: %.2f%% | %s", strongRodPoints(),
						elusiveChance() * 100, night ? "Night" : "Day"),
				"Overclocks: " + config.overclocks,
				"Equipment: " + config.equipmentBonuses,
				"Augments: " + config.augments);
	}

	// Chance per catch of a fish not caught yet, per island
	private static Map<String, Double> chances(FishCollection.Time now) {
		double weightMultiplier = hookMultiplier(STRONG_HOOK);
		double rarityMultiplier = hookMultiplier(WISE_HOOK);
		double elusiveChance = elusiveChance();
		double fishPerCatch = FISH_PER_CATCH;
		for (String lure : NO_FISH_LURES) {
			if (augments(lure) > 0) {
				fishPerCatch = 0.0;
			}
		}
		double regularPerCatch = (1 - elusiveChance) * fishPerCatch;
		double elusivePerCatch = elusiveChance * fishPerCatch;

		Map<FishCollection.Weight, Double> weights = new EnumMap<>(FishCollection.Weight.class);
		weights.put(FishCollection.Weight.LARGE, weightMultiplier * LARGE_BASE);
		weights.put(FishCollection.Weight.MASSIVE, weightMultiplier * MASSIVE_BASE);
		weights.put(FishCollection.Weight.GARGANTUAN, weightMultiplier * GARGANTUAN_BASE);
		weights.put(FishCollection.Weight.AVERAGE, 1 - weightMultiplier * (LARGE_BASE + MASSIVE_BASE + GARGANTUAN_BASE));

		Map<FishCollection.Rarity, Double> rarities = new EnumMap<>(FishCollection.Rarity.class);
		boolean rarityRod = augments(RARITY_ROD) > 0;
		double epic = rarityMultiplier * EPIC_BASE;
		double legendary = rarityMultiplier * LEGENDARY_BASE * (rarityRod ? RARITY_ROD_MULTIPLIER : 1.0);
		double mythic = rarityMultiplier * MYTHIC_BASE * (rarityRod ? RARITY_ROD_MULTIPLIER : 1.0);
		double uncommon = rarityRod ? 1 - RARE_BASE - epic - legendary - mythic : UNCOMMON_BASE;
		rarities.put(FishCollection.Rarity.UNCOMMON, uncommon);
		rarities.put(FishCollection.Rarity.RARE, RARE_BASE);
		rarities.put(FishCollection.Rarity.EPIC, epic);
		rarities.put(FishCollection.Rarity.LEGENDARY, legendary);
		rarities.put(FishCollection.Rarity.MYTHIC, mythic);
		rarities.put(FishCollection.Rarity.COMMON, rarityRod ? 0.0 : 1 - uncommon - RARE_BASE - epic - legendary - mythic);

		Map<String, Double> chances = new HashMap<>();
		for (FishCollection.Fish fish : FishCollection.fish()) {
			boolean elusive = fish.time() == FishCollection.Time.ELUSIVE;
			String island = fish.island().toLowerCase(Locale.ROOT);
			if (fish.crab() || !elusive && fish.time() != now && !GROTTO_ISLANDS.contains(island)) {
				continue;
			}
			double categoryPerCatch;
			if (GROTTO_ISLANDS.contains(island)) {
				categoryPerCatch = elusive ? elusiveChance : (1 - elusiveChance) * GROTTO_REGULAR_SHARE;
			} else {
				categoryPerCatch = elusive ? elusivePerCatch : regularPerCatch;
			}
			double perCatch = categoryPerCatch * rarities.get(fish.rarity());
			for (FishCollection.Weight weight : fish.weights()) {
				if (!FishCollection.isCaught(fish, weight)) {
					chances.merge(island, perCatch * weights.get(weight), Double::sum);
				}
			}
		}
		return chances;
	}

	// Best normal island and best Grotto, locked islands skipped
	private static Set<String> bestIslands(Container container, Map<String, Double> chances) {
		String best = null;
		double bestChance = 0.0;
		String bestGrotto = null;
		double bestGrottoChance = 0.0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			String island = stack.isEmpty() || isLocked(stack) ? null : name(stack);
			double chance = island == null ? 0.0 : chances.getOrDefault(island, 0.0);
			if (island != null && GROTTO_ISLANDS.contains(island)) {
				if (chance > bestGrottoChance) {
					bestGrotto = island;
					bestGrottoChance = chance;
				}
			} else if (chance > bestChance) {
				best = island;
				bestChance = chance;
			}
		}
		Set<String> found = new HashSet<>();
		if (best != null) {
			found.add(best);
		}
		if (bestGrotto != null) {
			found.add(bestGrotto);
		}
		return found;
	}

	// Day and Night chances in the island tooltip
	private static void addChances(ItemStack stack, List<Component> lines) {
		if (openIslands == null || lines.isEmpty() || isLocked(stack)) {
			return;
		}
		String island = name(stack);
		if (!FishCollection.islands().contains(island)) {
			return;
		}
		if (GROTTO_ISLANDS.contains(island)) {
			lines.add(1, chance("New Fish", dayChances.getOrDefault(island, 0.0)));
			return;
		}
		lines.add(1, chance("Day", dayChances.getOrDefault(island, 0.0)));
		lines.add(2, chance("Night", nightChances.getOrDefault(island, 0.0)));
	}

	private static Component chance(String label, double chance) {
		String value = String.format(Locale.ROOT, "%.3f%%", chance * 100);
		if (chance > 0.0) {
			value += " (1/" + Math.round(1 / chance) + ")";
		}
		String text = value;
		return Component.literal(label + ": ").withStyle(style -> style.withColor(CHANCE_LABEL_COLOR))
				.append(Component.literal(text).withStyle(style -> style.withColor(CHANCE_VALUE_COLOR)));
	}

	// Icon in the corner of the best slots
	public static void extractBestIcon(GuiGraphicsExtractor graphics, Slot slot) {
		if (bestIslands.isEmpty() || slot.container != openIslands || !bestIslands.contains(name(slot.getItem()))) {
			return;
		}
		// Whole screen pixels per texture pixel
		int guiScale = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
		int texel = Math.max(1, ICON_SIZE * guiScale / ICON_TEXTURE);
		int sizePixels = ICON_TEXTURE * texel;
		graphics.pose().pushMatrix();
		graphics.pose().translate(((slot.x + SLOT_SIZE) * guiScale - sizePixels) / (float) guiScale, slot.y);
		graphics.pose().scale(texel / (float) guiScale, texel / (float) guiScale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BEST_ICON, 0, 0, 0.0F, 0.0F, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE);
		graphics.pose().popMatrix();
	}

	private static String name(ItemStack stack) {
		return stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
	}
}
