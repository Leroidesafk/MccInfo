package leroidesafk.mccinfo.client.mixin.voice;

import leroidesafk.mccinfo.client.voice.VoiceChat;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiVoiceScreenMixin {
	// Simple Voice Chat screens replaced by the mod ones
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void mccinfo$replaceVoiceScreens(@Nullable Screen screen, CallbackInfo info) {
		Gui gui = (Gui) (Object) this;
		Screen replacement = VoiceChat.replaceScreen(gui.screen(), screen);
		if (replacement != null) {
			info.cancel();
			gui.setScreen(replacement);
		}
	}
}
