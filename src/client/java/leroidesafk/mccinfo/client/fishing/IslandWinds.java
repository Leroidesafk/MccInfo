package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.items.SlotIndicators;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;

// Tide and winds icons on the islands of the Navigator
public final class IslandWinds {
	private static final SlotIndicators.Sheet SHEET = new SlotIndicators.Sheet(Mccinfo.id("textures/gui/ui/wind.png"), 48, 12, 2.0F);
	private static final int TITLE_SLOT = 8;
	private static final String ISLANDS_TITLE = "fishing islands";
	private static final String TIDE_PREFIX = "active tide:";
	private static final String WINDS_PREFIX = "active winds:";
	// Same order as wind.png, tides then winds
	private static final List<String> TYPES = List.of("strong", "wise", "glimmering", "greedy", "lucky");
	private static final int WIND_OFFSET = TYPES.size();
	private static final int NONE = -1;

	private IslandWinds() {
	}

	public static void extractIcons(GuiGraphicsExtractor graphics, Slot slot) {
		if (!FishCollection.allowed() || !ModConfig.get().islandWindIcons || slot.container instanceof Inventory) {
			return;
		}
		ItemStack stack = slot.getItem();
		if (stack.isEmpty() || !isIslandsMenu(slot.container)) {
			return;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return;
		}
		// Tide bottom right, winds bottom left
		int tide = NONE;
		int wind = NONE;
		for (Component line : lore.lines()) {
			String text = line.getString().strip().toLowerCase(Locale.ROOT);
			if (text.startsWith(TIDE_PREFIX)) {
				tide = type(text.substring(TIDE_PREFIX.length()));
			} else if (text.startsWith(WINDS_PREFIX)) {
				wind = type(text.substring(WINDS_PREFIX.length()));
			}
		}
		if (tide != NONE) {
			SlotIndicators.bottomCorner(graphics, slot, SHEET, tide, true);
		}
		if (wind != NONE) {
			SlotIndicators.bottomCorner(graphics, slot, SHEET, WIND_OFFSET + wind, false);
		}
	}

	// Fishing Islands menu, by its title or slot 8
	private static boolean isIslandsMenu(Container container) {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen != null && screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(ISLANDS_TITLE)) {
			return true;
		}
		return container.getContainerSize() > TITLE_SLOT
				&& container.getItem(TITLE_SLOT).getHoverName().getString().toLowerCase(Locale.ROOT).contains(ISLANDS_TITLE);
	}

	private static int type(String name) {
		return TYPES.indexOf(name.strip());
	}
}
