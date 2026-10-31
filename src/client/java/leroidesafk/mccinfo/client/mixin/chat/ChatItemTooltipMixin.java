package leroidesafk.mccinfo.client.mixin.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import leroidesafk.mccinfo.client.chat.ChatItemTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public class ChatItemTooltipMixin {
	// Own tooltip for items shared in the chat
	@WrapOperation(method = "componentHoverEffect", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
	private void mccinfo$chatItemTooltip(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, Operation<Void> original) {
		if (!ChatItemTooltip.show(graphics, font, stack, x, y)) {
			original.call(graphics, font, stack, x, y);
		}
	}
}
