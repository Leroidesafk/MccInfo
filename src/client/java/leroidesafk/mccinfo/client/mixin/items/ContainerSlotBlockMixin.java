package leroidesafk.mccinfo.client.mixin.items;

import leroidesafk.mccinfo.client.items.ExchangeTax;
import leroidesafk.mccinfo.client.quests.QuestLockBar;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class ContainerSlotBlockMixin {
	// No slot under the tax box or the Quest Lock bar
	@Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
	private void mccinfo$getHoveredSlot(double x, double y, CallbackInfoReturnable<Slot> cir) {
		if (ExchangeTax.covers(this, x, y) || QuestLockBar.covers(this, x, y)) {
			cir.setReturnValue(null);
		}
	}
}
