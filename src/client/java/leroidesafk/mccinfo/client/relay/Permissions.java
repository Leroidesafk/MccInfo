package leroidesafk.mccinfo.client.relay;

import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

// Perms and roles sent by the relay
public final class Permissions {
	public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(Mccinfo.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
	public static final String BASE_VERSION = MOD_VERSION.contains("-") ? MOD_VERSION.substring(0, MOD_VERSION.indexOf('-')) : MOD_VERSION;

	// Perm ids
	public enum Perm {
		SEE_MAP("seemap"),
		SEE_ALL_WAYPOINTS("seeallwaypoint"),
		SEE_ITEMS("seeitems"),
		EVENTS("events"),
		UTILS("utils"),
		AUTO_UPDATE("autoupdate"),
		VOICE_CHAT("voicechat");

		public final String id;

		Perm(String id) {
			this.id = id;
		}

		static Perm byId(String id) {
			for (Perm perm : values()) {
				if (perm.id.equals(id.toLowerCase(Locale.ROOT))) {
					return perm;
				}
			}
			return null;
		}
	}

	private static boolean registered;
	private static boolean admin;
	private static boolean moderator;
	private static boolean outdated;
	private static String requiredVersion;
	private static final float TITLE_SCALE = 4.0F;
	private static final float SUBTITLE_SCALE = 2.0F;
	private static final Set<Perm> PERMS = EnumSet.noneOf(Perm.class);
	private static final Set<String> IDS = new HashSet<>();
	// Perms to ignore, set by optional modules
	private static final List<Predicate<String>> IGNORED = new CopyOnWriteArrayList<>();
	private static int changes;

	private Permissions() {
	}

	public static void register() {
	}

	static void update(boolean registered, boolean admin, List<String> perms, boolean outdated, String requiredVersion) {
		changes++;
		boolean adminChanged = Permissions.admin != admin;
		Set<Perm> before = EnumSet.copyOf(PERMS);
		Permissions.registered = registered;
		Permissions.admin = admin;
		Permissions.outdated = outdated;
		Permissions.requiredVersion = requiredVersion;
		PERMS.clear();
		IDS.clear();
		// An outdated mod gets no perms
		if (perms != null && !outdated) {
			for (String id : perms) {
				if (id != null) {
					IDS.add(id.toLowerCase(Locale.ROOT));
				}
				Perm perm = id == null ? null : Perm.byId(id);
				if (perm != null) {
					PERMS.add(perm);
				}
			}
		}
		if (adminChanged || !before.equals(PERMS)) {
			refreshCommands();
		}
	}

	private static void refreshCommands() {
		if (Minecraft.getInstance().getConnection() == null) {
			return;
		}
		try {
			ClientCommands.refreshCommandCompletions();
		} catch (IllegalStateException e) {
		}
	}

	public static int changes() {
		return changes;
	}

	public static void markChanged() {
		changes++;
	}

	public static boolean has(Perm perm) {
		return PERMS.contains(perm) && !ignored(perm.id);
	}

	public static boolean has(String id) {
		return IDS.contains(id) && !ignored(id);
	}

	// True even when the perm is ignored
	public static boolean received(String id) {
		return IDS.contains(id);
	}

	public static void addIgnored(Predicate<String> ignored) {
		IGNORED.add(ignored);
	}

	private static boolean ignored(String id) {
		for (Predicate<String> ignored : IGNORED) {
			if (ignored.test(id)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isOutdated() {
		return outdated;
	}


	public static boolean isRegistered() {
		return registered && !outdated;
	}

	public static boolean canOpenKeys() {
		return isRegistered() && (admin || !PERMS.isEmpty());
	}

	public static String requiredVersion() {
		return requiredVersion;
	}

	// Roles
	public static boolean isAdmin() {
		return admin;
	}

	public static boolean isModerator() {
		return moderator && !admin;
	}

	public static boolean isStaff() {
		return admin || moderator;
	}

	static void setModerator(boolean value) {
		moderator = value;
	}

	private static String downloadLink;

	public static String downloadLink() {
		return admin ? downloadLink : null;
	}

	static void setDownloadLink(String value) {
		downloadLink = value;
	}
}
