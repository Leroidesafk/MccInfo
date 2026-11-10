package leroidesafk.mccinfo.client.fishing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

// Island and crab pot icons from the MCC resource pack
final class IslandIcons {
	private static final String ISLAND = "mcc:island_interface/fishing/island/";
	private static final String CRAB_POT = "mcc:island_interface/fishing/crab_pot/crab_pot_half_full";
	// Item model of each island and crab section
	private static final Map<String, String> MODELS = Map.ofEntries(
			Map.entry("verdant woods", ISLAND + "verdant_woods"),
			Map.entry("floral forest", ISLAND + "floral_forest"),
			Map.entry("dark grove", ISLAND + "dark_grove"),
			Map.entry("sunken swamp", ISLAND + "grotto_temperate"),
			Map.entry("tropical overgrowth", ISLAND + "tropical_overgrowth"),
			Map.entry("coral shores", ISLAND + "coral_shores"),
			Map.entry("twisted swamp", ISLAND + "twisted_swamp"),
			Map.entry("mirrored oasis", ISLAND + "grotto_tropical"),
			Map.entry("ancient sands", ISLAND + "ancient_sands"),
			Map.entry("blazing canyon", ISLAND + "blazing_canyon"),
			Map.entry("ashen wastes", ISLAND + "ashen_wastes"),
			Map.entry("volcanic springs", ISLAND + "grotto_barren"),
			Map.entry("temperate crabs", CRAB_POT),
			Map.entry("tropical crabs", CRAB_POT),
			Map.entry("barren crabs", CRAB_POT));
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	private IslandIcons() {
	}

	// Icon item, made once
	static ItemStack stack(String key) {
		String model = MODELS.get(key);
		if (model == null) {
			return ItemStack.EMPTY;
		}
		return STACKS.computeIfAbsent(key, ignored -> {
			ItemStack stack = new ItemStack(Items.ECHO_SHARD);
			stack.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
			return stack;
		});
	}
}
