package leroidesafk.mccinfo.client.mixin.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import leroidesafk.mccinfo.client.chat.ModChat;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class ChatIndicatorMixin {
	// White side bar on normal lines, mod chat lines keep theirs
	@WrapOperation(method = "accept", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;tag()Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;"))
	private GuiMessageTag mccinfo$indicator(GuiMessage.Line line, Operation<GuiMessageTag> original) {
		return ModChat.indicator(original.call(line));
	}
}
