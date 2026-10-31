package leroidesafk.mccinfo.client.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiRenderer.class)
public class ScreenOpenItemMixin {
	// Items fade with the screen they were drawn in
	@ModifyExpressionValue(method = "submitBlitFromItemAtlas", at = @At(value = "CONSTANT", args = "intValue=-1"))
	private int mccinfo$fadeItem(int color, @Local(argsOnly = true) GuiItemRenderState itemState) {
		return ScreenOpenAnimation.taggedColor(itemState, color);
	}
}
