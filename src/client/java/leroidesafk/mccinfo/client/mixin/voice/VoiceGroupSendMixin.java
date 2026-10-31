package leroidesafk.mccinfo.client.mixin.voice;

import leroidesafk.mccinfo.client.voice.VoiceChat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPlayNetworking.class, remap = false)
public class VoiceGroupSendMixin {
	// Voice group packets sent through the relay
	@Inject(method = "send", at = @At("HEAD"), cancellable = true)
	private static void mccinfo$sendVoiceGroup(CustomPacketPayload payload, CallbackInfo ci) {
		if (VoiceChat.interceptGroupPacket(payload)) {
			ci.cancel();
		}
	}
}
