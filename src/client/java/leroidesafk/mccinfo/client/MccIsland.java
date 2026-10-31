package leroidesafk.mccinfo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

public final class MccIsland {
	private static final String ADDRESS = "mccisland.net";
	private static ServerData checkedServer;
	private static String checkedIp;
	private static boolean connected;

	private MccIsland() {
	}

	// Cached server address check
	public static boolean isConnected() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		if (server == null) {
			return false;
		}
		if (server != checkedServer || server.ip != checkedIp) {
			checkedServer = server;
			checkedIp = server.ip;
			connected = server.ip.toLowerCase(Locale.ROOT).contains(ADDRESS);
		}
		return connected;
	}
}
