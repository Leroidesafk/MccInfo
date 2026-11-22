package leroidesafk.mccinfo.client.update;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ServiceLoader;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// Version required by the relay and its Download Link
public final class ModUpdater {
	private static final UpdateAction ACTION = ServiceLoader.load(UpdateAction.class, UpdateAction.class.getClassLoader()).findFirst().orElse(null);

	private static String version;
	private static boolean handled;
	private static boolean debugScreen;
	private static URI downloadUrl;

	private ModUpdater() {
	}

	// /mccupdate
	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(literal("mccupdate").executes(context -> {
			if (!MccIsland.isConnected()) {
				context.getSource().sendError(Component.translatable("message.mccinfo.not_on_island"));
				return 0;
			}
			if (version == null) {
				context.getSource().sendFeedback(Component.translatable("screen.mccinfo.update.up_to_date", Permissions.MOD_VERSION));
				return Command.SINGLE_SUCCESS;
			}
			UpdateToast.show();
			return Command.SINGLE_SUCCESS;
		})));
	}

	// Debug commands
	public static int debugUpdate(FabricClientCommandSource source, boolean allowed) {
		if (!MccIsland.isConnected()) {
			source.sendError(Component.translatable("message.mccinfo.not_on_island"));
			return 0;
		}
		if (busy()) {
			source.sendError(Component.translatable("message.mccinfo.debug_unupdate.busy"));
			return 0;
		}
		if (!SpotSync.sendAdmin(allowed ? "debug_update" : "debug_update_denied", null, null, null)) {
			source.sendError(Component.translatable("message.mccinfo.mccinfo.offline"));
			return 0;
		}
		if (ACTION != null) {
			ACTION.reset();
		}
		debugScreen = true;
		return Command.SINGLE_SUCCESS;
	}

	public static int debugCancel(FabricClientCommandSource source) {
		if (busy()) {
			source.sendError(Component.translatable("message.mccinfo.debug_unupdate.busy"));
			return 0;
		}
		debugScreen = false;
		clear();
		SpotSync.reconnect();
		source.sendFeedback(Component.translatable("message.mccinfo.debug_unupdate.done"));
		return Command.SINGLE_SUCCESS;
	}

	// Called with every perms message
	public static void offer(boolean outdated, String requiredVersion, String link, JsonObject perms) {
		if (busy()) {
			return;
		}
		if (!outdated || requiredVersion == null) {
			clear();
			return;
		}
		// Toast only for a new version or a change of button
		boolean accepted = ACTION != null && ACTION.offer(requiredVersion, perms);
		boolean newVersion = !requiredVersion.equals(version) || handled != accepted;
		version = requiredVersion;
		handled = accepted;
		downloadUrl = accepted ? null : downloadLink(link);
		if (debugScreen || newVersion) {
			debugScreen = false;
			UpdateToast.show();
		}
	}

	private static void clear() {
		version = null;
		handled = false;
		downloadUrl = null;
		if (ACTION != null) {
			ACTION.reset();
		}
	}

	private static boolean busy() {
		return ACTION != null && ACTION.busy();
	}

	// Read by the toast
	static String requiredVersion() {
		return version;
	}

	static UpdateAction action() {
		return handled ? ACTION : null;
	}

	static URI downloadUrl() {
		return downloadUrl;
	}

	// Download Link set by an admin, https only
	private static URI downloadLink(String link) {
		if (link == null || link.isBlank() || link.length() > 300) {
			return null;
		}
		try {
			URI uri = new URI(link.strip());
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null;
		} catch (java.net.URISyntaxException e) {
			return null;
		}
	}
}
