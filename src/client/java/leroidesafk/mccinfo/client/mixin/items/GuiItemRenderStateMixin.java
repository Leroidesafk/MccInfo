package leroidesafk.mccinfo.client.mixin.items;

import leroidesafk.mccinfo.client.items.GrayscaleItems;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemRenderState.class)
public class GuiItemRenderStateMixin {
	// Item marked when it must be drawn in grey
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mccinfo$init(CallbackInfo ci) {
		GrayscaleItems.onCreated((GuiItemRenderState) (Object) this);
	}
}
