package leroidesafk.mccinfo.client.mixin.gui;

import leroidesafk.mccinfo.client.gui.TransitionWipe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudTransitionMixin {
	// Current title and its timings
	@Shadow
	private Component title;
	@Shadow
	private int titleTime;
	@Shadow
	private int titleFadeInTime;
	@Shadow
	private int titleFadeOutTime;

	// Transition drawn instead of its title
	@Inject(at = @At("HEAD"), method = "extractTitle", cancellable = true)
	private void mccinfo$transitionWipe(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo info) {
		if (TransitionWipe.render(graphics, this.title, this.titleTime, this.titleFadeInTime, this.titleFadeOutTime)) {
			info.cancel();
		}
	}
}
