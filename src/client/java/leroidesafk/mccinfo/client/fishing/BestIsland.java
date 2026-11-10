package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.items.ItemIcons;
import leroidesafk.mccinfo.client.util.ContainerWatch;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.BossHealthOverlayAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Island with the best chance of a fish not caught yet
public final class BestIsland {
	// Fishing Perks menu
	private static final String PERKS_TITLE = "fishing perks";
	private static final int TITLE_SLOT = 8;
	private static final Pattern PERK_PATTERN = Pattern.compile("^(.+?)\\s*\\((\\d+)\\s*/\\s*\\d+\\)$");

	// Overclocks, in the Supply Preserve menu
	private static final String OVERCLOCKS_TITLE = "supply preserve perk";
	private static final int OVERCLOCKS_SLOT = 1;
	private static final String OVERCLOCKED_PERK_HEADER = "overclocked perk";
	private static final Pattern SELECTED_PERK_PATTERN = Pattern.compile(">\\s*(.+)$");
	static final String HOOK_OVERCLOCK = "hook overclock";
	static final String MAGNET_OVERCLOCK = "magnet overclock";
	static final String ROD_OVERCLOCK = "rod overclock";
	static final String UNSTABLE_OVERCLOCK = "unstable overclock";
	static final List<String> OVERCLOCKS = List.of(HOOK_OVERCLOCK, MAGNET_OVERCLOCK, ROD_OVERCLOCK, UNSTABLE_OVERCLOCK);

	// Supplies menu
	private static final int BAIT_SLOT = 19;
	private static final int LINE_SLOT = 37;
	private static final int[] AUGMENT_SLOTS = {30, 31, 32, 33, 34, 39, 40, 41, 42, 43};
	private static final String EMPTY_SLOT_SUFFIX = " slot";
	private static final Pattern BONUS_PATTERN = Pattern.compile("\\+\\s*(\\d+)[^a-z]*([a-z]+(?: [a-z]+)*)");
	private static final Pattern GUARANTEE_PATTERN = Pattern.compile("^(\\d+)%\\s+([a-z]+(?: [a-z]+)* chance)$");
	private static final Pattern BAIT_REMAINING_PATTERN = Pattern.compile("bait remaining:\\s*([\\d,]+)");
	private static final Pattern USES_PATTERN = Pattern.compile("uses remaining:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)");
	private static final String RARITY_ROD = "rarity rod";
	private static final String ELUSIVE_FISH_CHANCE = "elusive fish chance";
	private static final List<String> NO_FISH_CHANCES = List.of("pearl chance", "spirit chance", "treasure chance");
	private static final int GUARANTEED = 100;

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
	private static final int TIME_TICKS = 20;

	private static final Set<String> GROTTO_ISLANDS = Set.of("sunken swamp", "mirrored oasis", "volcanic springs");

	// Catch odds
	private static final double FISH_PER_CATCH = 0.4;
	private static final double GROTTO_REGULAR_SHARE = 0.5;
	private static final double HOOK_MULTIPLIER_PER_POINT = 0.1;
	private static final double ROD_MULTIPLIER_PER_POINT = 0.01;
	private static final double ELUSIVE_CHANCE_PER_POINT = 0.005;
	private static final double RARITY_ROD_MULTIPLIER = 3.0;
	private static final double LARGE_BASE = 0.075;
	private static final double MASSIVE_BASE = 0.02;
	private static final double GARGANTUAN_BASE = 0.002;
	private static final double UNCOMMON_BASE = 0.25;
	private static final double RARE_BASE = 0.15;
	private static final double EPIC_BASE = 0.04;
	private static final double LEGENDARY_BASE = 0.01;
	private static final double MYTHIC_BASE = 0.002;

	// Catch and supply messages
	private static final Pattern CAUGHT_PATTERN = Pattern.compile("You caught: \\[([^\\[\\]]+)]");
	private static final String TICKET = "ticket";
	private static final String RUN_OUT = "run out of your equipped";
	private static final String SUPPLY_PRESERVE = "supply preserve";
	private static final long PRESERVE_MILLIS = 1_500L;

	// Best island icon
	private static final Identifier BEST_ICON = Mccinfo.id("textures/gui/best.png");
	private static final int ICON_SIZE = 8;
	private static final int ICON_TEXTURE = 16;
	private static final int CHANCE_LABEL_COLOR = 0xFFFF55;
	private static final int CHANCE_VALUE_COLOR = 0xFFFFFF;

	private static final int SLOT_SIZE = 16;

	private static final ContainerWatch WATCH = new ContainerWatch();

	private static boolean night;
	private static int timeTicks;
	private static Container openIslands;
	private static Set<String> bestIslands = Set.of();
	private static Map<String, Double> dayChances = Map.of();
	private static Map<String, Double> nightChances = Map.of();

	private static long lastCatch;
	private static boolean usedBait;
	private static boolean usedLine;
	private static long preservedAt;

	private BestIsland() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BestIsland::tick);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addChances(stack, lines));
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
	}

	static boolean night() {
		return night;
	}

	// Open fishing menu, read once per change
	private static void tick(Minecraft client) {
		if (!FishCollection.allowed()) {
			openIslands = null;
			bestIslands = Set.of();
			WATCH.reset();
			return;
		}
		if (++timeTicks >= TIME_TICKS) {
			timeTicks = 0;
			readBossBars(client);
		}
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
			readLockedIslands(container);
			openIslands = container;
			dayChances = chances(FishCollection.Time.DAY);
			nightChances = chances(FishCollection.Time.NIGHT);
			bestIslands = bestIslands(container, night ? nightChances : dayChances);
		}
	}

	// Locked islands, saved in the config
	private static void readLockedIslands(Container container) {
		Set<String> locked = new HashSet<>();
		boolean found = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			String name = stack.isEmpty() ? null : name(stack);
			if (name != null && FishCollection.islands().contains(name)) {
				found = true;
				if (isLocked(stack)) {
					locked.add(name);
				}
			}
		}
		ModConfig config = ModConfig.get();
		if (found && !locked.equals(config.lockedIslands)) {
			config.lockedIslands = locked;
			config.save();
		}
	}

	// Icon key of a crab section
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

	// Bait, line and augments with their bonuses and uses
	private static void readEquipment(Container container) {
		Map<String, Integer> bonuses = new HashMap<>();
		Map<String, Integer> baitBonuses = new HashMap<>();
		Map<String, Integer> lineBonuses = new HashMap<>();
		List<String> augments = new ArrayList<>();
		List<String> augmentUses = new ArrayList<>();
		boolean iconsChanged = false;
		List<Map<String, Integer>> augmentBonuses = new ArrayList<>();
		for (int slot : AUGMENT_SLOTS) {
			String augment = equipped(container, slot);
			if (augment != null) {
				ItemStack stack = container.getItem(slot);
				Map<String, Integer> own = new HashMap<>();
				addBonuses(stack, own);
				own.forEach((perk, value) -> bonuses.merge(perk, value, Integer::sum));
				augmentBonuses.add(own);
				augments.add(augment);
				int[] uses = uses(stack);
				augmentUses.add(uses == null ? "" : uses[0] + "/" + uses[1]);
				iconsChanged |= ItemIcons.remember(augment, stack);
			}
		}
		String bait = equipped(container, BAIT_SLOT);
		String line = equipped(container, LINE_SLOT);
		Integer baitUses = null;
		int[] lineUses = null;
		if (bait != null) {
			ItemStack stack = container.getItem(BAIT_SLOT);
			addBonuses(stack, baitBonuses);
			baitUses = baitRemaining(stack);
			iconsChanged |= ItemIcons.remember(bait, stack);
		}
		if (line != null) {
			ItemStack stack = container.getItem(LINE_SLOT);
			addBonuses(stack, lineBonuses);
			lineUses = uses(stack);
			iconsChanged |= ItemIcons.remember(line, stack);
		}
		ModConfig config = ModConfig.get();
		config.equipmentBonuses = bonuses;
		config.augments = augments;
		config.augmentUses = augmentUses;
		config.augmentBonuses = augmentBonuses;
		config.bait = bait;
		config.baitUses = baitUses;
		config.baitBonuses = baitBonuses;
		config.line = line;
		config.lineUses = lineUses == null ? null : lineUses[0];
		config.lineMaxUses = lineUses == null ? null : lineUses[1];
		config.lineBonuses = lineBonuses;
		config.save();
		if (iconsChanged) {
			ItemIcons.save();
		}
	}

	private static String equipped(Container container, int slot) {
		if (slot >= container.getContainerSize() || container.getItem(slot).isEmpty()) {
			return null;
		}
		String name = name(container.getItem(slot));
		return name.endsWith(EMPTY_SLOT_SUFFIX) ? null : name;
	}

	private static List<String> loreLines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> lines = new ArrayList<>();
		if (lore != null) {
			for (Component line : lore.lines()) {
				lines.add(line.getString().trim().toLowerCase(Locale.ROOT));
			}
		}
		return lines;
	}

	private static void addBonuses(ItemStack stack, Map<String, Integer> bonuses) {
		for (String line : loreLines(stack)) {
			Matcher matcher = BONUS_PATTERN.matcher(line);
			if (matcher.find()) {
				bonuses.merge(matcher.group(2), Integer.parseInt(matcher.group(1)), Integer::sum);
				continue;
			}
			Matcher guarantee = GUARANTEE_PATTERN.matcher(line);
			if (guarantee.find()) {
				bonuses.merge(guarantee.group(2), Integer.parseInt(guarantee.group(1)), Integer::sum);
			}
		}
	}

	private static Integer baitRemaining(ItemStack stack) {
		for (String line : loreLines(stack)) {
			Matcher matcher = BAIT_REMAINING_PATTERN.matcher(line);
			if (matcher.find()) {
				return Integer.parseInt(matcher.group(1).replace(",", ""));
			}
		}
		return null;
	}

	private static int[] uses(ItemStack stack) {
		for (String line : loreLines(stack)) {
			Matcher matcher = USES_PATTERN.matcher(line);
			if (matcher.find()) {
				return new int[] {Integer.parseInt(matcher.group(1).replace(",", "")), Integer.parseInt(matcher.group(2).replace(",", ""))};
			}
		}
		return null;
	}

	static boolean baitActive() {
		ModConfig config = ModConfig.get();
		return config.bait != null && (config.baitUses == null || config.baitUses > 0);
	}

	static boolean lineActive() {
		ModConfig config = ModConfig.get();
		return config.line != null && (config.lineUses == null || config.lineUses > 0);
	}

	static int equipment(String perk) {
		ModConfig config = ModConfig.get();
		int bonus = config.equipmentBonuses.getOrDefault(perk, 0);
		if (baitActive()) {
			bonus += config.baitBonuses.getOrDefault(perk, 0);
		}
		if (lineActive()) {
			bonus += config.lineBonuses.getOrDefault(perk, 0);
		}
		return bonus;
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

	static int total(String perk) {
		return perk(perk) + overclockBonus(perk) + equipment(perk);
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
		int augmentChance = equipment(ELUSIVE_FISH_CHANCE);
		if (augmentChance >= GUARANTEED) {
			return 1.0;
		}
		int elusivePerk = 0;
		for (Map.Entry<String, Integer> perk : ModConfig.get().perks.entrySet()) {
			if (perk.getKey().contains(ELUSIVE_PERK) && !perk.getKey().contains("overclock")) {
				elusivePerk = perk.getValue();
			}
		}
		return Math.min(1.0, (elusivePerk * ELUSIVE_CHANCE_PER_POINT + augmentChance / 100.0)
				* (1 + strongRodPoints() * ROD_MULTIPLIER_PER_POINT));
	}

	private static double hookMultiplier(String hook) {
		return 1 + hookPoints(hook) * HOOK_MULTIPLIER_PER_POINT;
	}

	private static Map<FishCollection.Weight, Double> weightChances() {
		double weightMultiplier = hookMultiplier(STRONG_HOOK);
		Map<FishCollection.Weight, Double> weights = new EnumMap<>(FishCollection.Weight.class);
		weights.put(FishCollection.Weight.LARGE, weightMultiplier * LARGE_BASE);
		weights.put(FishCollection.Weight.MASSIVE, weightMultiplier * MASSIVE_BASE);
		weights.put(FishCollection.Weight.GARGANTUAN, weightMultiplier * GARGANTUAN_BASE);
		weights.put(FishCollection.Weight.AVERAGE, 1 - weightMultiplier * (LARGE_BASE + MASSIVE_BASE + GARGANTUAN_BASE));
		return weights;
	}

	private static Map<FishCollection.Rarity, Double> rarityChances() {
		double rarityMultiplier = hookMultiplier(WISE_HOOK);
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
		return rarities;
	}

	// Chance per catch of a fish not caught yet, per island
	static Map<String, Double> chances(FishCollection.Time now) {
		double elusiveChance = elusiveChance();
		double fishPerCatch = FISH_PER_CATCH;
		for (String chance : NO_FISH_CHANCES) {
			if (equipment(chance) >= GUARANTEED) {
				fishPerCatch = 0.0;
			}
		}
		double regularPerCatch = (1 - elusiveChance) * fishPerCatch;
		double elusivePerCatch = elusiveChance * fishPerCatch;
		Map<FishCollection.Weight, Double> weights = weightChances();
		Map<FishCollection.Rarity, Double> rarities = rarityChances();

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

	static Map<String, Double> elusiveChances() {
		Map<FishCollection.Weight, Double> weights = weightChances();
		Map<FishCollection.Rarity, Double> rarities = rarityChances();
		Map<String, Double> chances = new HashMap<>();
		for (FishCollection.Fish fish : FishCollection.fish()) {
			if (fish.crab() || fish.time() != FishCollection.Time.ELUSIVE) {
				continue;
			}
			double perCatch = rarities.get(fish.rarity());
			for (FishCollection.Weight weight : fish.weights()) {
				if (!FishCollection.isCaught(fish, weight)) {
					chances.merge(fish.island().toLowerCase(Locale.ROOT), perCatch * weights.get(weight), Double::sum);
				}
			}
		}
		return chances;
	}

	// Best islands, locked ones skipped
	static Set<String> bestElusiveIslands() {
		List<String> open = new ArrayList<>();
		for (String island : FishCollection.islands()) {
			if (!ModConfig.get().lockedIslands.contains(island)) {
				open.add(island);
			}
		}
		return pick(open, elusiveChances());
	}

	private static Set<String> bestIslands(Container container, Map<String, Double> chances) {
		List<String> open = new ArrayList<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && !isLocked(stack)) {
				open.add(name(stack));
			}
		}
		return pick(open, chances);
	}

	static Set<String> bestIslands(FishCollection.Time time) {
		List<String> open = new ArrayList<>();
		for (String island : FishCollection.islands()) {
			if (!ModConfig.get().lockedIslands.contains(island)) {
				open.add(island);
			}
		}
		return pick(open, chances(time));
	}

	// Best normal island and best Grotto
	private static Set<String> pick(Collection<String> islands, Map<String, Double> chances) {
		String best = null;
		double bestChance = 0.0;
		String bestGrotto = null;
		double bestGrottoChance = 0.0;
		for (String island : islands) {
			double chance = chances.getOrDefault(island, 0.0);
			if (GROTTO_ISLANDS.contains(island)) {
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

	// Bait and line uses: -1 per catch, given back by Supply Preserve
	private static void onChatMessage(String message) {
		if (!FishCollection.allowed()) {
			return;
		}
		long now = System.currentTimeMillis();
		Matcher caught = CAUGHT_PATTERN.matcher(message);
		if (caught.find()) {
			if (caught.group(1).toLowerCase(Locale.ROOT).contains(TICKET)) {
				return;
			}
			if (now - preservedAt <= PRESERVE_MILLIS) {
				preservedAt = 0;
				return;
			}
			useSupplies(now);
			return;
		}
		String lower = message.toLowerCase(Locale.ROOT);
		if (lower.contains(SUPPLY_PRESERVE) && (lower.contains("triggered:") || lower.contains("special:"))) {
			if (now - lastCatch <= PRESERVE_MILLIS) {
				restoreSupplies();
			} else {
				preservedAt = now;
			}
			return;
		}
		if (lower.contains(RUN_OUT)) {
			ModConfig config = ModConfig.get();
			if (lower.contains("bait") && config.bait != null) {
				config.baitUses = 0;
				config.save();
			} else if (lower.contains("line") && config.line != null) {
				config.lineUses = 0;
				config.save();
			}
		}
	}

	private static void useSupplies(long now) {
		ModConfig config = ModConfig.get();
		usedBait = config.bait != null && config.baitUses != null && config.baitUses > 0;
		usedLine = config.line != null && config.lineUses != null && config.lineUses > 0;
		if (usedBait) {
			config.baitUses--;
		}
		if (usedLine) {
			config.lineUses--;
		}
		lastCatch = now;
		if (usedBait || usedLine) {
			config.save();
		}
	}

	private static void restoreSupplies() {
		ModConfig config = ModConfig.get();
		if (usedBait && config.baitUses != null) {
			config.baitUses++;
		}
		if (usedLine && config.lineUses != null) {
			config.lineUses++;
		}
		usedBait = false;
		usedLine = false;
		lastCatch = 0;
		config.save();
	}

	// Day and Night chances in the island tooltip
	private static void addChances(ItemStack stack, List<Component> lines) {
		if (!FishCollection.allowed()) {
			return;
		}
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
		if (!FishCollection.allowed() || !ModConfig.get().bestIslandIcon) {
			return;
		}
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
