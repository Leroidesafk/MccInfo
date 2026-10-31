package leroidesafk.mccinfo.client.mixin.travel;

import leroidesafk.mccinfo.client.travel.TravelCommand;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class SoundManagerTravelMixin {
	// Menu click sounds muted during /travel
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void mccinfo$muteTravelClicks(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> info) {
		if (TravelCommand.mutes(sound.getIdentifier())) {
			info.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}
}
