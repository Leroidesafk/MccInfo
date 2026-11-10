package leroidesafk.mccinfo.client.widget;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.emote.Emotes;
import leroidesafk.mccinfo.client.PrivateKeys;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Widgets {
	// Widget in the wheel and its click
	public record Widget(String id, Component name, Identifier icon, Consumer<Screen> open, boolean emote, boolean animated) {
		public Widget(String id, Component name, Identifier icon, Consumer<Screen> open) {
			this(id, name, icon, open, false, false);
		}
	}

	// Entry of widgets.json
	public record Definition(String id, String name, String type, int players, boolean playerModelAnimated) {
	}

	// Known widgets, owned ones, names and emotes from the relay
	private static final Map<String, Widget> KNOWN = new LinkedHashMap<>();
	public static final int WHEEL_SLOTS = 8;
	private static List<Widget> active = List.of();
	private static List<Definition> owned = List.of();
	private static PrivateKeys.Key wheelKey;
	private static final Map<String, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<String> EMOTES = java.util.concurrent.ConcurrentHashMap.newKeySet();

	// Rock Paper Scissors: Player or Waypoint first
	static {
		KNOWN.put("rps", new Widget("rps", Component.translatable("widget.mccinfo.rps"), RockPaperScissors.ICON,
				parent -> Minecraft.getInstance().gui.setScreen(new DuelModeScreen(parent, "rps", RpsDuelScreen::new))));
	}

	private Widgets() {
	}

	// Wheel key, owned widgets from the relay
	public static void register() {
		wheelKey = PrivateKeys.add("widgets_wheel", "key.mccinfo.widgets_wheel", false, () -> Permissions.has(Permissions.Perm.UTILS), InputConstants.UNKNOWN).in("widgets", null);
		SpotSync.addHandler("widgets", Widgets::receive);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (wheelKey.consumeClick()) {
				if (client.gui.screen() == null && available()) {
					client.gui.setScreen(new WidgetWheelScreen());
				}
			}
		});
		WidgetWaypoints.register();
		RedeemCodes.register();
	}

	public static PrivateKeys.Key wheelKey() {
		return wheelKey;
	}

	public static boolean available() {
		return MccIsland.isConnected() && Permissions.has(Permissions.Perm.UTILS);
	}

	public static Component name(String id) {
		// Name from widgets.json first
		String custom = NAMES.get(id);
		if (custom != null) {
			return Component.literal(custom);
		}
		Widget widget = KNOWN.get(id);
		return widget != null ? widget.name() : Component.literal(id);
	}

	public static boolean isEmote(String id) {
		return EMOTES.contains(id);
	}

	public static boolean known(String id) {
		return KNOWN.containsKey(id);
	}

	public static Identifier icon(String id) {
		Widget widget = KNOWN.get(id);
		return widget != null ? widget.icon() : null;
	}

	public static List<Widget> active() {
		return active;
	}

	public static List<Definition> owned() {
		return owned;
	}

	public static Widget widget(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (Widget widget : active) {
			if (widget.id().equals(id)) {
				return widget;
			}
		}
		return null;
	}

	public static List<String> emptyWheel() {
		return new ArrayList<>(Collections.nCopies(WHEEL_SLOTS, ""));
	}

	// Custom wheels, else owned widgets in relay order
	public static List<List<String>> wheels() {
		List<List<String>> saved = ModConfig.get().widgetWheels;
		List<List<String>> wheels = new ArrayList<>();
		if (saved != null) {
			for (List<String> wheel : saved) {
				List<String> copy = emptyWheel();
				for (int slot = 0; wheel != null && slot < Math.min(WHEEL_SLOTS, wheel.size()); slot++) {
					copy.set(slot, wheel.get(slot) == null ? "" : wheel.get(slot));
				}
				wheels.add(copy);
			}
		} else {
			for (int i = 0; i < active.size(); i += WHEEL_SLOTS) {
				List<String> wheel = emptyWheel();
				for (int slot = 0; slot < WHEEL_SLOTS && i + slot < active.size(); slot++) {
					wheel.set(slot, active.get(i + slot).id());
				}
				wheels.add(wheel);
			}
		}
		if (wheels.isEmpty()) {
			wheels.add(emptyWheel());
		}
		return wheels;
	}

	public static void saveWheels(List<List<String>> wheels) {
		List<List<String>> copy = new ArrayList<>();
		for (List<String> wheel : wheels) {
			copy.add(new ArrayList<>(wheel));
		}
		ModConfig.get().widgetWheels = copy;
		ModConfig.get().save();
	}

	// New widgets go in the first free slot
	private static void placeNew(List<Widget> list) {
		ModConfig config = ModConfig.get();
		if (config.widgetSeen == null) {
			config.widgetSeen = new HashSet<>();
		}
		boolean changed = false;
		if (config.widgetWheels != null) {
			List<List<String>> wheels = wheels();
			for (Widget widget : list) {
				if (config.widgetSeen.contains(widget.id()) || wheels.stream().anyMatch(wheel -> wheel.contains(widget.id()))) {
					continue;
				}
				List<String> target = wheels.stream().filter(wheel -> wheel.contains("")).findFirst().orElse(null);
				if (target == null) {
					target = emptyWheel();
					wheels.add(target);
				}
				target.set(target.indexOf(""), widget.id());
				changed = true;
			}
			if (changed) {
				config.widgetWheels = wheels;
			}
		}
		for (Widget widget : list) {
			changed |= config.widgetSeen.add(widget.id());
		}
		if (changed) {
			config.save();
		}
	}

	private static void receive(JsonObject object) {
		List<Widget> list = new ArrayList<>();
		List<Definition> definitions = read(object.get("widgets"));
		for (Definition definition : definitions) {
			Widget widget = "emote".equals(definition.type()) ? emote(definition) : KNOWN.get(definition.id());
			if (widget != null && !list.contains(widget)) {
				list.add(widget);
			}
		}
		owned = definitions;
		active = List.copyOf(list);
		placeNew(active);
		sendPrefs();
	}

	// Emote played on click
	private static Widget emote(Definition definition) {
		Component name = definition.name() != null && !definition.name().isBlank() ? Component.literal(definition.name()) : Component.literal(definition.id());
		return new Widget(definition.id(), name, null, parent -> {
			Minecraft.getInstance().gui.setScreen(null);
			Emotes.play(definition.id());
		}, true, definition.playerModelAnimated());
	}

	public static List<Definition> read(JsonElement array) {
		List<Definition> list = new ArrayList<>();
		if (array == null || !array.isJsonArray()) {
			return List.of();
		}
		for (JsonElement element : array.getAsJsonArray()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			String id = string(entry, "id");
			String type = string(entry, "type");
			if (id == null || type == null) {
				continue;
			}
			int players = entry.has("players") && entry.get("players").isJsonPrimitive() ? entry.get("players").getAsInt() : 0;
			boolean animated = !entry.has("playerModelAnimated") || entry.get("playerModelAnimated").getAsBoolean();
			// Custom name
			String name = string(entry, "name");
			if (name != null && !name.isBlank()) {
				NAMES.put(id, name);
			} else {
				NAMES.remove(id);
			}
			// Emote widgets
			if ("emote".equals(type)) {
				EMOTES.add(id);
			} else {
				EMOTES.remove(id);
			}
			list.add(new Definition(id, name, type, players, animated));
		}
		return List.copyOf(list);
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
	}

	public static Component describe(Definition definition) {
		Component text = switch (definition.type()) {
			case "duel" -> Component.translatable("screen.mccinfo.widgets.type.duel", definition.players());
			case "emote" -> Component.translatable(definition.playerModelAnimated()
					? "screen.mccinfo.widgets.type.emote_animated" : "screen.mccinfo.widgets.type.emote_still");
			case "tag" -> Component.translatable("screen.mccinfo.widgets.type.tag");
			default -> Component.literal(definition.type());
		};
		// Not usable yet in this version
		return known(definition.id()) || "emote".equals(definition.type()) ? text : Component.translatable("screen.mccinfo.widgets.type.unsupported", text);
	}

	// Tell the relay if duels are accepted
	public static void sendPrefs() {
		JsonObject message = new JsonObject();
		message.addProperty("type", "widget_prefs");
		message.addProperty("duels", ModConfig.get().widgetDuels);
		SpotSync.sendVoice(message);
	}
}
