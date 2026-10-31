package leroidesafk.mccinfo.client.mixin.gui;

import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextRenderState.class)
public class ScreenOpenTextMixin {
	// Text colors made writable
	@Mutable
	@Final
	@Shadow
	private int color;
	@Mutable
	@Final
	@Shadow
	private int backgroundColor;

	// Text and its background faded
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mccinfo$fadeText(CallbackInfo info) {
		this.color = ScreenOpenAnimation.fade(this.color);
		this.backgroundColor = ScreenOpenAnimation.fade(this.backgroundColor);
	}
}
