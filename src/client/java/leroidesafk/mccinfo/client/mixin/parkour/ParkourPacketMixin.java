package leroidesafk.mccinfo.client.mixin.parkour;

import com.llamalad7.mixinextras.sugar.Local;
import leroidesafk.mccinfo.client.parkour.ParkourSplits;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ParkourPacketMixin {
	// Sounds that start and stop the split timer
	@Inject(method = "handleSoundEvent", at = @At("TAIL"))
	private void mccinfo$parkourSound(ClientboundSoundPacket packet, CallbackInfo info) {
		ParkourSplits.onSound(packet.getSound().value().location());
	}

	// Module and medal titles
	@ModifyArg(method = "setSubtitleText", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;setSubtitle(Lnet/minecraft/network/chat/Component;)V"))
	private Component mccinfo$parkourSubtitle(Component text) {
		return ParkourSplits.onSubtitle(text);
	}

	// Course reset time on the holograms
	@Inject(method = "handleSetEntityData", at = @At("TAIL"))
	private void mccinfo$parkourHologram(ClientboundSetEntityDataPacket packet, CallbackInfo info, @Local Entity entity) {
		ParkourSplits.onEntityData(entity);
	}
}
