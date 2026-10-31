package leroidesafk.mccinfo.client.mixin.chat;

import leroidesafk.mccinfo.client.mixin.accessor.ChatScreenAccessor;
import leroidesafk.mccinfo.client.ClientHooks;
import leroidesafk.mccinfo.client.chat.EmojiOverlay;
import leroidesafk.mccinfo.client.chat.Emojis;
import leroidesafk.mccinfo.client.fishing.FishingMessagesOverlay;
import leroidesafk.mccinfo.client.chat.ModChatOverlay;
import leroidesafk.mccinfo.client.chat.ChatCopy;
import leroidesafk.mccinfo.client.chat.ModChat;
import net.minecraft.network.chat.Style;
import leroidesafk.mccinfo.client.chat.OverlayChatDrag;
import leroidesafk.mccinfo.client.patchnotes.PatchNoteToast;
import leroidesafk.mccinfo.client.update.UpdateToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
	// Clicks go to the mod overlays first
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void mccinfo$mouseClickedHead(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (ClientHooks.chatClicked((ChatScreen) (Object) this, event) || EmojiOverlay.mouseClicked(event) || ModChat.mouseClicked(event) || ChatCopy.mouseClicked(event) || UpdateToast.mouseClicked(event) || PatchNoteToast.mouseClicked(event) || OverlayChatDrag.mouseClicked(event)) {
			cir.setReturnValue(true);
		}
	}

	// Click actions of mod chat messages
	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void mccinfo$modChatAction(Style clicked, boolean allowInsertions, CallbackInfoReturnable<Boolean> cir) {
		if (!allowInsertions && clicked.getClickEvent() != null && ModChat.handleClick(clicked.getClickEvent())) {
			cir.setReturnValue(true);
		}
	}

	// Message sent to the mod chat instead of the server
	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void mccinfo$sendToModChat(String message, boolean addToHistory, CallbackInfo ci) {
		if (ModChat.intercept(message, addToHistory)) {
			ci.cancel();
		}
	}

	// Scroll goes to the mod overlays first
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void mccinfo$mouseScrolledHead(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (UpdateToast.covers(x, y) || PatchNoteToast.covers(x, y) || EmojiOverlay.mouseScrolled(x, y, scrollY) || FishingMessagesOverlay.mouseScrolled(x, y, scrollY) || ModChatOverlay.mouseScrolled(x, y, scrollY) || OverlayChatDrag.mouseScrolled(x, y, scrollY)) {
			cir.setReturnValue(true);
		}
	}

	// Mouse hidden from the chat while over a toast
	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int mccinfo$hideMouseXUnderToast(int mouseX) {
		return mccinfo$underToast() ? -10000 : mouseX;
	}

	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int mccinfo$hideMouseYUnderToast(int mouseY) {
		return mccinfo$underToast() ? -10000 : mouseY;
	}

	private static boolean mccinfo$underToast() {
		net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
		double x = client.mouseHandler.getScaledXPos(client.getWindow());
		double y = client.mouseHandler.getScaledYPos(client.getWindow());
		return UpdateToast.covers(x, y) || PatchNoteToast.covers(x, y);
	}

	// Emoji tooltip and toasts drawn over the chat
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mccinfo$extractToastOverChat(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		EmojiOverlay.extractTooltip(graphics, mouseX, mouseY);
		UpdateToast.extractOverChat(graphics);
		PatchNoteToast.extractOverChat(graphics);
	}

	// Auto Emojis and room left while typing
	@Inject(method = "onEdited", at = @At("TAIL"))
	private void mccinfo$showEmojisInInput(String value, CallbackInfo ci) {
		Emojis.onChatEdited(((ChatScreenAccessor) this).mccinfo$getInput());
	}

	// Emojis sent as their text code
	@ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String mccinfo$sendEmojisAsText(String message) {
		return Emojis.expand(message);
	}
}
