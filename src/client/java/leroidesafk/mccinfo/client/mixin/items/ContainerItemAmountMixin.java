package leroidesafk.mccinfo.client.mixin.items;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import leroidesafk.mccinfo.client.fishing.BestIsland;
import leroidesafk.mccinfo.client.items.BetterItemAmounts;
import leroidesafk.mccinfo.client.items.PlayerNumbers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Real amounts and perk levels on container slots
@Mixin(AbstractContainerScreen.class)
public class ContainerItemAmountMixin {
	// Slot being drawn
	@Unique
	private Slot mccinfo$amountSlot;

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void mccinfo$rememberSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		this.mccinfo$amountSlot = slot;
	}

	@WrapOperation(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
	private void mccinfo$betterAmount(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, String countText, Operation<Void> original) {
		// Navigator player count
		long players = countText == null ? PlayerNumbers.playersFor(this.mccinfo$amountSlot, stack) : -1L;
		if (players >= 0L) {
			original.call(graphics, font, stack.copyWithCount(1), x, y, null);
			BetterItemAmounts.extractPlayers(graphics, font, players, x, y);
			return;
		}
		// Perk level or real amount
		int[] perk = countText == null && BetterItemAmounts.perkLevelsShown() ? BestIsland.perkLevel(this.mccinfo$amountSlot, stack) : null;
		if (perk != null) {
			original.call(graphics, font, stack.copyWithCount(1), x, y, null);
			BetterItemAmounts.extractLevel(graphics, font, perk[0], perk[1], x, y);
			return;
		}
		long amount = BetterItemAmounts.amountFor(this.mccinfo$amountSlot, stack, countText);
		if (amount < 0L) {
			original.call(graphics, font, stack, x, y, countText);
			return;
		}
		original.call(graphics, font, stack.copyWithCount(1), x, y, null);
		if (amount > 1L) {
			BetterItemAmounts.extract(graphics, font, amount, x, y);
		}
	}
}
