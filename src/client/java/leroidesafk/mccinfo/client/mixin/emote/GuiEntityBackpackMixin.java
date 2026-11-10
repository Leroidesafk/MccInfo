package leroidesafk.mccinfo.client.mixin.emote;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import leroidesafk.mccinfo.client.emote.InventoryBackpack;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public class GuiEntityBackpackMixin {
	// Backpack drawn after the player in inventory previews
	@Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
			at = @At("TAIL"))
	private void mccinfo$submitBackpack(GuiEntityRenderState entityState, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo info,
			@Local CameraRenderState camera) {
		InventoryBackpack.submit(entityState.renderState(), camera, poseStack, collector);
	}
}
