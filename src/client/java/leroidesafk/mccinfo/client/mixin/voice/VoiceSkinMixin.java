package leroidesafk.mccinfo.client.mixin.voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.gui.GameProfileUtils", remap = false)
public class VoiceSkinMixin {
	// Profiles kept to load each skin once
	@Unique
	private static final Map<UUID, ResolvableProfile> MCCINFO$PROFILES = new ConcurrentHashMap<>();

	// Skin of players missing from the Tab list
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void mccinfo$skinOutsideTabList(UUID id, CallbackInfoReturnable<PlayerSkin> cir) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (id == null || connection == null || connection.getPlayerInfo(id) != null) {
			return;
		}
		ResolvableProfile profile = MCCINFO$PROFILES.computeIfAbsent(id, ResolvableProfile::createUnresolved);
		cir.setReturnValue(client.playerSkinRenderCache().getOrDefault(profile).playerSkin());
	}
}
