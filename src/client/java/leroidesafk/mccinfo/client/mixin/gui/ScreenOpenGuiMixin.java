package leroidesafk.mccinfo.client.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public class ScreenOpenGuiMixin {
	// Screen drawn with its opening slide and fade, mouse moved to match
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void mccinfo$openAnimation(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, Operation<Void> original) {
		if (!ScreenOpenAnimation.begin(screen, graphics)) {
			original.call(screen, graphics, mouseX, mouseY, a);
			return;
		}
		try {
			original.call(screen, graphics, mouseX, ScreenOpenAnimation.mouseY(mouseY), a);
		} finally {
			ScreenOpenAnimation.end(graphics);
		}
	}
}
