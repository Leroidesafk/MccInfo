package leroidesafk.mccinfo.client.mixin.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import leroidesafk.mccinfo.client.emote.Emotes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandRenderer.class)
public class ArmorStandEmoteMixin {
	// Cloak motion computed with the state
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
			at = @At("TAIL"))
	private void mccinfo$emoteExtract(ArmorStand entity, ArmorStandRenderState state, float partialTicks, CallbackInfo info) {
		Emotes.extractPassenger(entity, state, state.x, state.y, state.z, partialTicks);
	}

	// Applied before the armor stand is drawn
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"))
	private void mccinfo$emoteSubmit(ArmorStandRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
		Emotes.applyPassenger(state, poseStack);
	}
}
