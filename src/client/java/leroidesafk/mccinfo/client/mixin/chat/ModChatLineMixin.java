package leroidesafk.mccinfo.client.mixin.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import leroidesafk.mccinfo.client.chat.ChatCopy;
import leroidesafk.mccinfo.client.chat.ModChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class ModChatLineMixin {
	// Copy hover, item backgrounds, then items and moderation icons over the line
	@WrapOperation(method = "accept", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
	private boolean mccinfo$itemBackgrounds(ChatComponent.ChatGraphicsAccess graphics, int textTop, float opacity, FormattedCharSequence message,
			Operation<Boolean> original, @Local(argsOnly = true) GuiMessage.Line line) {
		ChatCopy.line(graphics, line, textTop);
		ModChat.itemBackgrounds(graphics, line, textTop, opacity);
		boolean hovered = original.call(graphics, textTop, opacity, message);
		ModChat.chatItems(graphics, line, textTop, opacity);
		ModChat.moderationIcons(graphics, line, textTop, opacity);
		return hovered;
	}
}
