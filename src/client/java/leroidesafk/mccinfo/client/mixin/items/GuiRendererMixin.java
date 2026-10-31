package leroidesafk.mccinfo.client.mixin.items;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import leroidesafk.mccinfo.client.items.GrayscaleItems;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	// Grey pipeline for marked items
	@ModifyExpressionValue(method = "submitBlitFromItemAtlas", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/RenderPipelines;GUI_TEXTURED_PREMULTIPLIED_ALPHA:Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
	private RenderPipeline mccinfo$grayscale(RenderPipeline original, @Local(argsOnly = true) GuiItemRenderState itemState) {
		return GrayscaleItems.isMarked(itemState) ? GrayscaleItems.PIPELINE : original;
	}
}
