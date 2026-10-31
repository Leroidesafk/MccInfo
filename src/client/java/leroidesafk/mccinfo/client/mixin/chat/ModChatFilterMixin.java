package leroidesafk.mccinfo.client.mixin.chat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import leroidesafk.mccinfo.client.chat.ModChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

@Mixin(ChatComponent.class)
public class ModChatFilterMixin {
	// Copy Chat Messages: hover reset each frame
	@org.spongepowered.asm.mixin.injection.Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
			at = @At("HEAD"))
	private void mccinfo$chatCopyFrame(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int ticks, int mouseX, int mouseY,
			ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
		leroidesafk.mccinfo.client.chat.ChatCopy.beginFrame(graphics, mouseX, mouseY);
	}

	// Only the active chat when the lines are rebuilt
	@WrapOperation(method = "refreshTrimmedMessages", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
	private boolean mccinfo$filterModChat(Predicate<Object> filter, Object message, Operation<Boolean> original) {
		return original.call(filter, message) && ModChat.visible((GuiMessage) message);
	}

	// Mod chat tag put on messages it adds
	@WrapOperation(method = "addMessage", at = @At(value = "NEW", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;"))
	private GuiMessage mccinfo$keepModChatTag(int addedTime, net.minecraft.network.chat.Component content, net.minecraft.network.chat.MessageSignature signature,
			net.minecraft.client.multiplayer.chat.GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, Operation<GuiMessage> original) {
		return original.call(addedTime, content, signature, source, ModChat.keepTag(tag));
	}

	// New message shown only if its chat is active
	@WrapOperation(method = "addMessage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"))
	private void mccinfo$displayOnlyActiveChat(ChatComponent chat, GuiMessage message, Operation<Void> original) {
		if (ModChat.visible(message)) {
			original.call(chat, message);
		}
	}

	// History limit counted per chat
	@WrapOperation(method = "addMessageToQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
	private int mccinfo$countSameChat(List<GuiMessage> messages, Operation<Integer> original, @Local(argsOnly = true) GuiMessage added) {
		int channel = ModChat.channel(added);
		int count = 0;
		for (GuiMessage message : messages) {
			if (ModChat.channel(message) == channel) {
				count++;
			}
		}
		return count;
	}

	// Oldest message of the same chat removed
	@WrapOperation(method = "addMessageToQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;removeLast()Ljava/lang/Object;"))
	private Object mccinfo$removeOldestOfSameChat(List<GuiMessage> messages, Operation<Object> original, @Local(argsOnly = true) GuiMessage added) {
		int channel = ModChat.channel(added);
		for (ListIterator<GuiMessage> iterator = messages.listIterator(messages.size()); iterator.hasPrevious(); ) {
			GuiMessage message = iterator.previous();
			if (ModChat.channel(message) == channel) {
				iterator.remove();
				return message;
			}
		}
		return original.call(messages);
	}
}
