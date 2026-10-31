package leroidesafk.mccinfo.client.widget;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.MccServer;
import leroidesafk.mccinfo.client.relay.Regions;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.SpotSync;
import leroidesafk.mccinfo.client.spots.WidgetWaypointMarkers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class WidgetWaypoints {
	public static final double JOIN_DISTANCE = 7.0;
	private static final int CLICK_DELAY = 4;
	private static final double OTHER_REGION_DISTANCE = 500.0;
	// Hub and fishing islands
	private static final java.util.Set<String> SERVERS = java.util.Set.of("lobby", "fishing");

	private record Waypoint(String id, String widget, String uuid, String name, Vec3 position, String server) {
	}

	private static List<Waypoint> waypoints = List.of();
	// Removed waypoints kept for their vanish animation
	private static final java.util.Map<String, Long> VANISHING_SINCE = new java.util.HashMap<>();
	private static final java.util.Map<String, Waypoint> VANISHING = new java.util.HashMap<>();

	private WidgetWaypoints() {
	}

	static void register() {
		SpotSync.addHandler("widget_waypoints", WidgetWaypoints::receive);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			waypoints = List.of();
			VANISHING.clear();
			VANISHING_SINCE.clear();
		});
		WidgetWaypointMarkers.register(WidgetWaypoints::markers);
	}

	private static void receive(JsonObject object) {
		List<Waypoint> list = new ArrayList<>();
		if (object.has("waypoints") && object.get("waypoints").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("waypoints")) {
				try {
					JsonObject entry = element.getAsJsonObject();
					list.add(new Waypoint(entry.get("id").getAsString(), entry.get("widget").getAsString(), entry.get("uuid").getAsString(),
							entry.get("name").getAsString(), new Vec3(entry.get("x").getAsDouble(), entry.get("y").getAsDouble(), entry.get("z").getAsDouble()),
							entry.get("server").getAsString()));
				} catch (RuntimeException e) {
				}
			}
		}
		// Waypoints gone since the last list start vanishing
		long now = net.minecraft.util.Util.getMillis();
		for (Waypoint old : waypoints) {
			if (list.stream().noneMatch(entry -> entry.id().equals(old.id()))) {
				VANISHING.put(old.id(), old);
				VANISHING_SINCE.put(old.id(), now);
			}
		}
		waypoints = List.copyOf(list);
	}

	private static boolean visible() {
		return Widgets.available() && ModConfig.get().widgetWaypoints && Minecraft.getInstance().player != null;
	}

	private static List<WidgetWaypointMarkers.Marker> markers() {
		if (!visible()) {
			return List.of();
		}
		Minecraft client = Minecraft.getInstance();
		String own = RockPaperScissors.hex(client.player.getUUID());
		String server = MccServer.server();
		List<WidgetWaypointMarkers.Marker> markers = new ArrayList<>();
		long now = net.minecraft.util.Util.getMillis();
		VANISHING_SINCE.entrySet().removeIf(entry -> now - entry.getValue() > WidgetWaypointMarkers.VANISH_MILLIS);
		VANISHING.keySet().retainAll(VANISHING_SINCE.keySet());
		// Current and vanishing waypoints
		List<Waypoint> shown = new ArrayList<>(waypoints);
		shown.addAll(VANISHING.values());
		for (Waypoint waypoint : shown) {
			// Same server, same region or close enough
			if (!waypoint.server().equals(server) || !nearEnough(waypoint.position(), client.player.position())) {
				continue;
			}
			// Own waypoint shown but never clickable
			boolean mine = waypoint.uuid().equals(own);
			Long since = VANISHING_SINCE.get(waypoint.id());
			float vanish = since == null ? 0.0F : (now - since) / (float) WidgetWaypointMarkers.VANISH_MILLIS;
			boolean joinable = !mine && since == null && waypoint.position().distanceTo(client.player.position()) <= JOIN_DISTANCE;
			markers.add(new WidgetWaypointMarkers.Marker(since == null && !mine ? waypoint.id() : null, waypoint.position(), Widgets.icon(waypoint.widget()), joinable, vanish));
		}
		return markers;
	}

	private static boolean nearEnough(Vec3 waypoint, Vec3 player) {
		return Regions.same(waypoint, player) || waypoint.distanceTo(player) <= OTHER_REGION_DISTANCE;
	}

	public static boolean canPlace() {
		return MccIsland.isConnected() && SERVERS.contains(MccServer.server());
	}

	public static boolean place(String widget) {
		Minecraft client = Minecraft.getInstance();
		String server = MccServer.server();
		if (client.player == null || !canPlace()) {
			return false;
		}
		Vec3 position = client.player.position();
		JsonObject message = new JsonObject();
		message.addProperty("type", "widget_waypoint");
		message.addProperty("widget", widget);
		message.addProperty("x", position.x);
		message.addProperty("y", position.y);
		message.addProperty("z", position.z);
		message.addProperty("server", server);
		return SpotSync.sendVoice(message);
	}

	public static int click() {
		String id = WidgetWaypointMarkers.looked();
		Minecraft client = Minecraft.getInstance();
		if (id == null || client.player == null || client.gui.screen() != null) {
			return 0;
		}
		Waypoint waypoint = waypoints.stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
		if (waypoint == null) {
			return 0;
		}
		// Too far: SpotPath handles the click (path)
		if (waypoint.position().distanceTo(client.player.position()) > JOIN_DISTANCE) {
			return 0;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "widget_waypoint_join");
		message.addProperty("id", waypoint.id());
		if (!SpotSync.sendVoice(message)) {
			client.player.sendOverlayMessage(Component.translatable("message.mccinfo.mccinfo.offline").withStyle(ChatFormatting.RED));
		}
		return CLICK_DELAY;
	}
}
