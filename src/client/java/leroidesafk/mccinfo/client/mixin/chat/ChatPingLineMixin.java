package leroidesafk.mccinfo.client.mixin.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import leroidesafk.mccinfo.client.chat.ChatPing;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class ChatPingLineMixin {
	// Highlight behind lines that ping you
	@WrapOperation(method = "accept", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
	private boolean mccinfo$highlightPing(ChatComponent.ChatGraphicsAccess graphics, int textTop, float opacity, FormattedCharSequence message,
			Operation<Boolean> original, @Local(argsOnly = true) GuiMessage.Line line) {
		ChatPing.highlight(graphics, line, textTop, opacity);
		return original.call(graphics, textTop, opacity, message);
	}
}
