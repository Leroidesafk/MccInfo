package leroidesafk.mccinfo.client.mixin.items;

import leroidesafk.mccinfo.client.fishing.BestIsland;
import leroidesafk.mccinfo.client.events.CarnivalTicketOverlay;
import leroidesafk.mccinfo.client.fishing.FishCollection;
import leroidesafk.mccinfo.client.parkour.ParkourLimits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ContainerSlotIconMixin {
	// Island, caught and best island icons, parkour tint on slots (not sure yet about the parkour one, ping me discord if it's not working)
	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void mccinfo$extractSlotTail(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		CarnivalTicketOverlay.extractIslandIcon(graphics, slot);
		FishCollection.extractCheckIcon(graphics, slot);
		BestIsland.extractBestIcon(graphics, slot);
		ParkourLimits.extractTint(graphics, slot);
	}
}
