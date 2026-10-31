package leroidesafk.mccinfo.client.mixin.spots;

import leroidesafk.mccinfo.client.spots.SpotPath;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftUseItemMixin {
	// Wait before the next right click
	@Shadow
	private int rightClickDelay;

	// Right click on a duel waypoint, then on a spot marker
	@Inject(at = @At("HEAD"), method = "startUseItem", cancellable = true)
	private void mccinfo$clickLookedAt(CallbackInfo info) {
		int delay = leroidesafk.mccinfo.client.widget.WidgetWaypoints.click();
		if (delay == 0) {
			delay = SpotPath.clickLookedAt();
		}
		if (delay > 0) {
			this.rightClickDelay = delay;
			info.cancel();
		}
	}
}
