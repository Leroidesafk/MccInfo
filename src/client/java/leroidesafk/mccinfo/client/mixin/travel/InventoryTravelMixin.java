package leroidesafk.mccinfo.client.mixin.travel;

import leroidesafk.mccinfo.client.travel.TravelCommand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class InventoryTravelMixin {
	// Inventory kept as it is while /travel uses the menus
	@Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
	private void mccinfo$keepInventoryDuringTravel(int slot, ItemStack stack, CallbackInfo info) {
		if (TravelCommand.freezes((Inventory) (Object) this, slot, stack)) {
			info.cancel();
		}
	}
}
