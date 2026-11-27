package leroidesafk.mccinfo.client.mixin.gui;

import leroidesafk.mccinfo.client.chat.ChatChannels;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Action bar read for the server chats
@Mixin(Hud.class)
public class HudActionBarMixin {
	@Inject(at = @At("HEAD"), method = "setOverlayMessage")
	private void mccinfo$readActionBar(Component message, boolean animate, CallbackInfo info) {
		ChatChannels.onActionBar(message);
	}
}
