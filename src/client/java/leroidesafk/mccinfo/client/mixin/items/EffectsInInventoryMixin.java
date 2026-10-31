package leroidesafk.mccinfo.client.mixin.items;

import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {
	// Effects hidden while the NEU item list takes their place
	@Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
	private void mccinfo$canSeeEffects(CallbackInfoReturnable<Boolean> cir) {
		if (NeuItems.shown()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void mccinfo$extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (NeuItems.shown()) {
			ci.cancel();
		}
	}
}
