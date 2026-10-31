package leroidesafk.mccinfo.client.events;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CarnivalTracker {
	static final long PERIOD_SECONDS = 15 * 60;
	static final long START_OFFSET_SECONDS = 60;
	private static final long BLACKOUT_SECONDS = 10;
	private static final long ACTIVE_WINDOW_SECONDS = 2 * 60;
	private static final int SCAN_INTERVAL_TICKS = 20;
	private static final double LINE_RANGE = 4.0;
	private static final int MAX_GAME_LENGTH = 40;
	private static final String TRACKER_MARKER = "cursed carnival activity tracker";
	private static final String NEXT_GAME_MARKER = "next game:";
	private static final String ACTIVE_GAME_MARKER = "active game";

	private record Label(Vec3 position, String text) {
	}

	private record Reading(String game, boolean active) {
	}

	private static int ticks;
	// Sent and known next game
	private static long sentSlot = -1;
	private static String sentGame;
	private static long knownSlot = -1;
	private static String knownGame;

	private CarnivalTracker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CarnivalTracker::tick);
	}

	static long nextStart(long epochSeconds) {
		return epochSeconds - Math.floorMod(epochSeconds - START_OFFSET_SECONDS, PERIOD_SECONDS) + PERIOD_SECONDS;
	}

	static String gameFor(long slot) {
		return slot == knownSlot ? knownGame : null;
	}

	public static void receive(JsonObject object) {
		if (object.has("slot") && object.has("game")) {
			knownSlot = object.get("slot").getAsLong();
			knownGame = object.get("game").getAsString();
			Mccinfo.LOGGER.info("Carnival: relay says next game {} for {}", knownGame, knownSlot);
		}
	}

	static void clear() {
		knownSlot = -1;
		knownGame = null;
		sentSlot = -1;
		sentGame = null;
	}

	private static void tick(Minecraft client) {
		if (++ticks < SCAN_INTERVAL_TICKS) {
			return;
		}
		ticks = 0;
		if (client.level == null || client.player == null || !MccIsland.isConnected() || !Permissions.has(Permissions.Perm.EVENTS)) {
			return;
		}
		long now = System.currentTimeMillis() / 1000;
		long slot = nextStart(now);
		long untilStart = slot - now;
		// Skip near the switch
		if (untilStart <= BLACKOUT_SECONDS || untilStart >= PERIOD_SECONDS - BLACKOUT_SECONDS) {
			return;
		}
		// Active game only close to start
		Reading reading = readNextGame(client);
		if (reading == null || (reading.active() && untilStart > ACTIVE_WINDOW_SECONDS)) {
			return;
		}
		String game = reading.game();
		// Already sent or known
		if ((slot == sentSlot && game.equals(sentGame)) || (slot == knownSlot && game.equals(knownGame))) {
			return;
		}
		if (SpotSync.sendCarnivalGame(slot, game)) {
			sentSlot = slot;
			sentGame = game;
			Mccinfo.LOGGER.info("Carnival: sent next game {} for {}", game, slot);
		} else {
			Mccinfo.LOGGER.info("Carnival: read next game {} but the relay is not connected", game);
		}
	}

	private static Reading readNextGame(Minecraft client) {
		// Text and item displays
		List<Label> labels = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			String text = switch (entity) {
				case Display.TextDisplay textDisplay -> textDisplay.getText().getString();
				case Display.ItemDisplay itemDisplay -> label(itemDisplay);
				default -> null;
			};
			if (text != null && !text.isBlank()) {
				labels.add(new Label(entity.position(), clean(text)));
			}
		}
		// Tracker and nearby lines
		for (Label tracker : labels) {
			String lower = tracker.text().toLowerCase(Locale.ROOT);
			if (!lower.contains(TRACKER_MARKER)) {
				continue;
			}
			Reading reading = parse(tracker.text());
			if (reading != null) {
				return reading;
			}
			for (Label line : labels) {
				if (line != tracker && line.position().distanceTo(tracker.position()) <= LINE_RANGE) {
					reading = parse(line.text());
					if (reading != null) {
						return reading;
					}
				}
			}
		}
		return null;
	}

	private static Reading parse(String text) {
		String game = nextGame(text);
		if (game != null) {
			return new Reading(game, false);
		}
		game = activeGame(text);
		return game == null ? null : new Reading(game, true);
	}

	private static String activeGame(String text) {
		String[] lines = text.split("\n");
		for (int i = 0; i < lines.length; i++) {
			if (!lines[i].toLowerCase(Locale.ROOT).contains(ACTIVE_GAME_MARKER)) {
				continue;
			}
			for (int j = i + 1; j < lines.length; j++) {
				String game = lines[j].replaceAll("\\s+", " ").strip();
				if (!game.isEmpty()) {
					return game.length() > MAX_GAME_LENGTH || game.contains(":") ? null : game;
				}
			}
		}
		return null;
	}

	private static String nextGame(String text) {
		int start = text.toLowerCase(Locale.ROOT).indexOf(NEXT_GAME_MARKER);
		if (start < 0) {
			return null;
		}
		String rest = text.substring(start + NEXT_GAME_MARKER.length());
		int end = rest.indexOf('\n');
		String game = (end < 0 ? rest : rest.substring(0, end)).replaceAll("\\s+", " ").strip();
		return game.isEmpty() || game.length() > MAX_GAME_LENGTH ? null : game;
	}

	private static String clean(String text) {
		return text.replaceAll("[\\uE000-\\uF8FF]", "").replace('\r', '\n');
	}

	private static String label(Display.ItemDisplay itemDisplay) {
		net.minecraft.network.chat.Component customName = itemDisplay.getCustomName();
		String itemName = itemDisplay.getItemStack().getHoverName().getString();
		return customName == null ? itemName : customName.getString() + "\n" + itemName;
	}
}
