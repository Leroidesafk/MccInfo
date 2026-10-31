package leroidesafk.mccinfo.client.mixin.gui;

import leroidesafk.mccinfo.client.relay.ModUsers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class TabModUserMixin {
	// Mod user icon instead of the ping bars
	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void mccinfo$modUserIcon(GuiGraphicsExtractor graphics, int slotWidth, int xo, int yo, PlayerInfo info, CallbackInfo callback) {
		net.minecraft.resources.Identifier badge = ModUsers.badge(info);
		if (badge != null) {
			ModUsers.extractIcon(graphics, badge, xo + slotWidth - 1, yo);
			callback.cancel();
		}
	}
}
