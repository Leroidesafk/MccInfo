package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// Assembler in progress icon drawn above the other items
public final class OnTopItems {
	private static final Identifier IN_PROGRESS = Identifier.parse("mcc:island_interface/assembler/in_progress");
	private static final List<Slot> DEFERRED = new ArrayList<>();
	private static boolean drawing;

	private OnTopItems() {
	}

	private static boolean enabled() {
		return ModConfig.get().assemblerOnTop && Permissions.has(Permissions.Perm.UTILS) && OverlayStyle.onMccIsland();
	}

	private static boolean onTop(ItemStack stack) {
		return !stack.isEmpty() && IN_PROGRESS.equals(stack.get(DataComponents.ITEM_MODEL));
	}

	// Slot kept to be drawn last
	public static boolean defer(Slot slot) {
		if (drawing || !enabled() || !onTop(slot.getItem())) {
			return false;
		}
		DEFERRED.add(slot);
		GrayscaleItems.end();
		return true;
	}

	public static List<Slot> takeDeferred() {
		List<Slot> slots = List.copyOf(DEFERRED);
		DEFERRED.clear();
		return slots;
	}

	public static void drawing(boolean value) {
		drawing = value;
	}
}
