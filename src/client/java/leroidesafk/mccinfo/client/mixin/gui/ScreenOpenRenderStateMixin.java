package leroidesafk.mccinfo.client.mixin.gui;

import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public class ScreenOpenRenderStateMixin {
	// Rectangles and textures faded as they are added
	@ModifyVariable(method = "addGuiElement", at = @At("HEAD"), argsOnly = true)
	private GuiElementRenderState mccinfo$fadeElement(GuiElementRenderState element) {
		return ScreenOpenAnimation.fade(element);
	}

	// Items and pictures tagged with the current fade
	@Inject(method = "addItem", at = @At("HEAD"))
	private void mccinfo$tagItem(GuiItemRenderState itemState, CallbackInfo info) {
		ScreenOpenAnimation.tag(itemState);
	}

	@Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
	private void mccinfo$tagPicture(PictureInPictureRenderState state, CallbackInfo info) {
		ScreenOpenAnimation.tag(state);
	}
}
