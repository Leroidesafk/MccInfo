package leroidesafk.mccinfo.client.mixin.gui;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenOpenBackgroundMixin {
	// Dimmed background stays in place while the screen slides
	@Inject(method = "extractTransparentBackground", at = @At("HEAD"))
	private void mccinfo$pinTransparent(GuiGraphicsExtractor graphics, CallbackInfo info, @Share("pinned") LocalBooleanRef pinned) {
		pinned.set(ScreenOpenAnimation.suspendShift(graphics));
	}

	@Inject(method = "extractTransparentBackground", at = @At("RETURN"))
	private void mccinfo$unpinTransparent(GuiGraphicsExtractor graphics, CallbackInfo info, @Share("pinned") LocalBooleanRef pinned) {
		ScreenOpenAnimation.resumeShift(graphics, pinned.get());
	}

	// Same for the menu background
	@Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"))
	private void mccinfo$pinMenu(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo info,
			@Share("pinned") LocalBooleanRef pinned) {
		pinned.set(ScreenOpenAnimation.suspendShift(graphics));
	}

	@Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("RETURN"))
	private void mccinfo$unpinMenu(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo info,
			@Share("pinned") LocalBooleanRef pinned) {
		ScreenOpenAnimation.resumeShift(graphics, pinned.get());
	}
}
