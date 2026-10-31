package leroidesafk.mccinfo.client.mixin.travel;

import leroidesafk.mccinfo.client.travel.TravelCommand;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiTravelMixin {
	// Menus opened by /travel stay hidden
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void mccinfo$hideTravelMenus(@Nullable Screen screen, CallbackInfo info) {
		if (TravelCommand.hide(screen)) {
			info.cancel();
		}
	}
}
