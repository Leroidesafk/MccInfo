package leroidesafk.mccinfo.client.mixin.items;

import leroidesafk.mccinfo.client.items.CosmeticPreview;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class CosmeticBackLayerMixin extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, PlayerModel> {
	private CosmeticBackLayerMixin(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	// Back cosmetic layer of the Cosmetic Preview on every player renderer
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mccinfo$addBackLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo info) {
		this.addLayer(new CosmeticPreview.BackLayer(this));
	}
}
