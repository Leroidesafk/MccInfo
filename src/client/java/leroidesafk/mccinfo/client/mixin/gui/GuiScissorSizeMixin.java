package leroidesafk.mccinfo.client.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Scissor never 0 wide or high, crash when the window shrinks
@Mixin(GuiRenderer.class)
public class GuiScissorSizeMixin {
	@WrapOperation(method = "enableScissor(Lnet/minecraft/client/gui/navigation/ScreenRectangle;Lcom/mojang/blaze3d/systems/RenderPass;)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V"))
	private void mccinfo$keepScissorSize(RenderPass renderPass, int x, int y, int width, int height, Operation<Void> original) {
		original.call(renderPass, x, y, Math.max(1, width), Math.max(1, height));
	}
}
