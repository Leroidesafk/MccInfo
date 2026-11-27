package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Player count of each game in the Navigator
public final class PlayerNumbers {
	private static final int MENU_SLOT = 8;
	private static final String NAVIGATOR = "navigator";
	private static final Pattern PLAYERS = Pattern.compile("^Players:\\s*(\\d[\\d,]*)$");

	private PlayerNumbers() {
	}

	public static boolean enabled() {
		return ModConfig.get().playerNumbers && Permissions.has(Permissions.Perm.UTILS) && OverlayStyle.onMccIsland();
	}

	// Number from the Players: line of the lore
	public static long playersFor(Slot slot, ItemStack stack) {
		if (slot == null || stack.isEmpty() || !enabled() || slot.container instanceof Inventory || !isNavigator(slot.container)) {
			return -1L;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return -1L;
		}
		for (Component line : lore.lines()) {
			Matcher matcher = PLAYERS.matcher(line.getString().strip());
			if (matcher.matches()) {
				try {
					return Long.parseLong(matcher.group(1).replace(",", ""));
				} catch (NumberFormatException e) {
					return -1L;
				}
			}
		}
		return -1L;
	}

	private static boolean isNavigator(Container container) {
		return container.getContainerSize() > MENU_SLOT && NeuItems.cleanName(container.getItem(MENU_SLOT).getHoverName().getString()).equals(NAVIGATOR);
	}
}
