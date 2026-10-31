package leroidesafk.mccinfo.client.mixin.chat;

import leroidesafk.mccinfo.client.chat.ChatPing;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatPingMessageMixin {
	// Mentions of your name highlighted in new messages
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component mccinfo$pingMention(Component contents) {
		return ChatPing.apply(contents);
	}
}
