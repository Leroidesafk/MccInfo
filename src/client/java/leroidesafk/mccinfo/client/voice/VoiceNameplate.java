package leroidesafk.mccinfo.client.voice;

import java.util.UUID;

// Nameplate text display that knows its player
public interface VoiceNameplate {
	UUID mccinfo$voicePlayer();

	void mccinfo$setVoicePlayer(UUID player);
}
