package leroidesafk.mccinfo.client;

import com.noxcrew.noxesium.core.mcc.ClientboundMccServerPacket;
import com.noxcrew.noxesium.core.mcc.MccPackets;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.List;

public final class MccServer {
	private static final MccServer LISTENER = new MccServer();
	private static final String FISHING = "fishing";

	private static volatile String server;
	private static volatile List<String> types = List.of();

	private MccServer() {
	}

	static void register() {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> listen());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			server = null;
			types = List.of();
		});
	}

	// Noxesium server info
	private static void listen() {
		try {
			MccPackets.CLIENTBOUND_MCC_SERVER.addListener(LISTENER, ClientboundMccServerPacket.class, (listener, packet, player) -> {
				types = List.copyOf(packet.types());
				server = packet.server();
			});
		} catch (RuntimeException | LinkageError e) {
			Mccinfo.LOGGER.warn("Noxesium: could not listen to MCC Island server info, fishing detection disabled ({})", e.toString());
		}
	}

	public static boolean onFishing() {
		return FISHING.equals(server) && MccIsland.isConnected();
	}

	public static String server() {
		return server;
	}

	public static List<String> types() {
		return types;
	}
}
