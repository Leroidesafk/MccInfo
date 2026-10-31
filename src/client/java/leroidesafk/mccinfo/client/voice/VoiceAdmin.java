package leroidesafk.mccinfo.client.voice;

import com.google.gson.JsonObject;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.UUID;

public final class VoiceAdmin {
	// Announcement and microphone icons
	public static final Identifier ANNOUNCE_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/other_volume");
	private static final Identifier MICROPHONE_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/microphone");
	private static final Identifier WHISPER_MICROPHONE_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/microphone_whisper");

	// Announce on, voice chat off, current announcer
	private static boolean announcing;
	private static boolean disabled;
	private static String announcer;

	private VoiceAdmin() {
	}

	public static boolean announcing() {
		return announcing && Permissions.isAdmin();
	}

	// Admin announces to everyone
	public static boolean setAnnouncing(boolean enabled) {
		announcing = enabled;
		return sendAnnounce();
	}

	public static boolean muteOthers() {
		return ModConfig.get().voiceAnnounceMute;
	}

	public static void setMuteOthers(boolean mute) {
		ModConfig config = ModConfig.get();
		config.voiceAnnounceMute = mute;
		config.save();
		if (announcing) {
			sendAnnounce();
		}
	}

	static void resend() {
		if (announcing) {
			sendAnnounce();
		}
	}

	private static boolean sendAnnounce() {
		JsonObject message = new JsonObject();
		message.addProperty("type", "voice_admin");
		message.addProperty("action", "announce");
		message.addProperty("enabled", announcing);
		message.addProperty("muteOthers", muteOthers());
		return SpotSync.sendVoice(message);
	}

	public static boolean disabled() {
		return disabled;
	}

	// Voice chat off for everyone
	public static boolean setDisabled(boolean value) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "voice_admin");
		message.addProperty("action", "disable");
		message.addProperty("quiet", true);
		message.addProperty("enabled", value);
		return SpotSync.sendVoice(message);
	}

	public static void receiveStatus(JsonObject object) {
		disabled = object.has("disabled") && object.get("disabled").getAsBoolean();
	}

	// Who is announcing
	public static void receiveAnnounce(JsonObject object) {
		if (!object.has("uuid")) {
			return;
		}
		String uuid = object.get("uuid").getAsString();
		boolean active = object.has("active") && object.get("active").getAsBoolean();
		if (active) {
			announcer = uuid;
		} else if (uuid.equals(announcer)) {
			announcer = null;
		}
	}

	public static void reset() {
		announcer = null;
	}

	public static boolean hearingAnnouncement() {
		Minecraft client = Minecraft.getInstance();
		return announcer != null && client.player != null && !announcer.equals(hex(client.player.getUUID()));
	}

	public static boolean isAnnouncer(UUID player) {
		return announcer != null && announcer.equals(hex(player));
	}

	// Announcement icon instead of the microphone
	public static Identifier hudIcon(Identifier icon) {
		if (announcing() && (MICROPHONE_ICON.equals(icon) || WHISPER_MICROPHONE_ICON.equals(icon))) {
			return ANNOUNCE_ICON;
		}
		return hearingAnnouncement() ? ANNOUNCE_ICON : icon;
	}

	private static String hex(UUID uuid) {
		return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}
}
