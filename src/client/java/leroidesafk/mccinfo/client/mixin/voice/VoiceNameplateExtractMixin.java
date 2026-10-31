package leroidesafk.mccinfo.client.mixin.voice;

import leroidesafk.mccinfo.client.voice.VoiceChat;
import leroidesafk.mccinfo.client.voice.VoiceNameplate;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public class VoiceNameplateExtractMixin {
	// Player of the nameplate, found once per frame
	@Inject(
			at = @At("TAIL"),
			method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V"
	)
	private void mccinfo$findVoicePlayer(Display.TextDisplay entity, TextDisplayEntityRenderState state, float partialTicks, CallbackInfo info) {
		((VoiceNameplate) state).mccinfo$setVoicePlayer(VoiceChat.nameplateOwner(entity));
	}
}
