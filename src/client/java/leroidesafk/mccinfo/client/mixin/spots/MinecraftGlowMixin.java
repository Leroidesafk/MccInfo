package leroidesafk.mccinfo.client.mixin.spots;

import leroidesafk.mccinfo.client.ClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftGlowMixin {
	// Glow on entities the mod highlights
	@Inject(at = @At("HEAD"), method = "shouldEntityAppearGlowing", cancellable = true)
	private void mccinfo$glow(Entity entity, CallbackInfoReturnable<Boolean> info) {
		if (ClientHooks.glowColor(entity) != ClientHooks.NO_GLOW) {
			info.setReturnValue(true);
		}
	}
}
