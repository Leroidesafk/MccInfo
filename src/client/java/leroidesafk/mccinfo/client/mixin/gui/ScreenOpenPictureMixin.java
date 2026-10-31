package leroidesafk.mccinfo.client.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PictureInPictureRenderer.class)
public class ScreenOpenPictureMixin {
	// Pictures fade with the screen they were drawn in
	@ModifyExpressionValue(method = "blitTexture", at = @At(value = "CONSTANT", args = "intValue=-1"))
	private int mccinfo$fadePicture(int color, @Local(argsOnly = true) PictureInPictureRenderState renderState) {
		return ScreenOpenAnimation.taggedColor(key(renderState), color);
	}

	// And slide with it, big items already follow their own pose
	@ModifyExpressionValue(method = "blitTexture", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;pose()Lorg/joml/Matrix3x2fc;"))
	private Matrix3x2fc mccinfo$shiftPicture(Matrix3x2fc pose, @Local(argsOnly = true) PictureInPictureRenderState renderState) {
		return renderState instanceof OversizedItemRenderState ? pose : ScreenOpenAnimation.taggedPose(renderState, pose);
	}

	// Big items are tagged by their item state
	private static Object key(PictureInPictureRenderState renderState) {
		return renderState instanceof OversizedItemRenderState oversized ? oversized.guiItemRenderState() : renderState;
	}
}
