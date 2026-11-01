package leroidesafk.mccinfo.client.mixin.emote;

import leroidesafk.mccinfo.client.emote.Emotes;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelEmoteMixin {
	// Emote pose after the vanilla animation
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void mccinfo$emotePose(AvatarRenderState state, CallbackInfo info) {
		Emotes.applyPose((PlayerModel) (Object) this, state);
	}
}
