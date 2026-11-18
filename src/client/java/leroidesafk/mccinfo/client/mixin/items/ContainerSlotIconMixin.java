package leroidesafk.mccinfo.client.mixin.items;

import leroidesafk.mccinfo.client.fishing.BestIsland;
import leroidesafk.mccinfo.client.events.CarnivalTicketOverlay;
import leroidesafk.mccinfo.client.fishing.FishCollection;
import leroidesafk.mccinfo.client.items.CosmeticPreview;
import leroidesafk.mccinfo.client.items.ExchangeDeals;
import leroidesafk.mccinfo.client.items.GrayscaleItems;
import leroidesafk.mccinfo.client.items.SlotIndicators;
import leroidesafk.mccinfo.client.parkour.ParkourLimits;
import leroidesafk.mccinfo.client.quests.QuestLock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Icons and tints on the slots of every container
@Mixin(AbstractContainerScreen.class)
public class ContainerSlotIconMixin {
	// Before the item: locked cosmetic frame and parkour tint
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void mccinfo$extractSlotHead(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		CosmeticPreview.extractLockedFrame(graphics, slot);
		ParkourLimits.beginTint(slot);
	}

	// After the item: end of the tint, then every icon
	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void mccinfo$extractSlotTail(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		GrayscaleItems.end();
		CarnivalTicketOverlay.extractIslandIcon(graphics, slot);
		FishCollection.extractCheckIcon(graphics, slot);
		BestIsland.extractBestIcon(graphics, slot);
		BestIsland.extractUpgradeIcon(graphics, slot);
		SlotIndicators.extractItemIndicators(graphics, slot);
		ExchangeDeals.extractDealIcon(graphics, slot);
		QuestLock.extractLockIcon(graphics, slot);
	}
}
