package leroidesafk.mccinfo.client.mixin.gui;

import leroidesafk.mccinfo.client.gui.TabDots;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Tab names with their dots marked hidden
@Mixin(PlayerTabOverlay.class)
public class TabDotsNameMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void mccinfo$hideDots(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
		callback.setReturnValue(TabDots.hide(callback.getReturnValue()));
	}
}
