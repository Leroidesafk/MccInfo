package leroidesafk.mccinfo.client.mixin.spots;

import leroidesafk.mccinfo.client.ClientHooks;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Display.class)
public class DisplayGlowColorMixin {
	// Glow color chosen by the mod, else the team color
	@Inject(at = @At("HEAD"), method = "getTeamColor", cancellable = true)
	private void mccinfo$glowColor(CallbackInfoReturnable<Integer> info) {
		int color = ClientHooks.glowColor((Display) (Object) this);
		if (color != ClientHooks.NO_GLOW) {
			info.setReturnValue(color);
		}
	}
}
