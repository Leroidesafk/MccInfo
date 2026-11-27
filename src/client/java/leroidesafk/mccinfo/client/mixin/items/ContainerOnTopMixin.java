package leroidesafk.mccinfo.client.mixin.items;

import leroidesafk.mccinfo.client.items.OnTopItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = AbstractContainerScreen.class, priority = 900)
public abstract class ContainerOnTopMixin {
	@Shadow
	protected abstract void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY);

	// Skip the in progress slot
	@Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
	private void mccinfo$deferOnTop(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (OnTopItems.defer(slot)) {
			ci.cancel();
		}
	}

	// Then draw it above every slot
	@Inject(method = "extractSlots", at = @At("TAIL"))
	private void mccinfo$extractOnTop(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		List<Slot> slots = OnTopItems.takeDeferred();
		if (slots.isEmpty()) {
			return;
		}
		graphics.nextStratum();
		OnTopItems.drawing(true);
		try {
			for (Slot slot : slots) {
				this.extractSlot(graphics, slot, mouseX, mouseY);
			}
		} finally {
			OnTopItems.drawing(false);
		}
	}
}
