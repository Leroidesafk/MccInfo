package leroidesafk.mccinfo.client.mixin.items;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import leroidesafk.mccinfo.client.items.GrayscaleItems;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PictureInPictureRenderer.class)
public class PictureInPictureRendererMixin {
	// Grey pipeline for big marked items too
	@ModifyExpressionValue(method = "blitTexture", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/RenderPipelines;GUI_TEXTURED_PREMULTIPLIED_ALPHA:Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
	private RenderPipeline mccinfo$grayscale(RenderPipeline original, @Local(argsOnly = true) PictureInPictureRenderState renderState) {
		return renderState instanceof OversizedItemRenderState oversized && GrayscaleItems.isMarked(oversized.guiItemRenderState())
				? GrayscaleItems.PIPELINE : original;
	}
}
