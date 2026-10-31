package leroidesafk.mccinfo.client.mixin.voice;

import com.mojang.blaze3d.vertex.PoseStack;
import leroidesafk.mccinfo.client.voice.VoiceChat;
import leroidesafk.mccinfo.client.voice.VoiceNameplate;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class VoiceNameplateSubmitMixin {
	// Voice icon next to the nameplate of a known player
	@Inject(
			at = @At("TAIL"),
			method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
	)
	private void mccinfo$voiceIcon(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
		if (state instanceof VoiceNameplate nameplate && nameplate.mccinfo$voicePlayer() != null) {
			VoiceChat.submitNameplateIcon(state, nameplate.mccinfo$voicePlayer(), poseStack, collector, camera);
		}
	}
}
