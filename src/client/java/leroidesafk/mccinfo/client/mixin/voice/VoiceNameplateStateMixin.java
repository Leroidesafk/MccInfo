package leroidesafk.mccinfo.client.mixin.voice;

import leroidesafk.mccinfo.client.voice.VoiceNameplate;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(TextDisplayEntityRenderState.class)
public class VoiceNameplateStateMixin implements VoiceNameplate {
	// Player kept on the render state of the nameplate
	@Unique
	private UUID mccinfo$voicePlayer;

	@Override
	public UUID mccinfo$voicePlayer() {
		return this.mccinfo$voicePlayer;
	}

	@Override
	public void mccinfo$setVoicePlayer(UUID player) {
		this.mccinfo$voicePlayer = player;
	}
}
