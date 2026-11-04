package leroidesafk.mccinfo.client.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ChatStyle {
	// Defaults and the MCC Tab spacing before the name
	private static final int WHITE = 0xFFFFFF;
	private static final List<String> DEFAULT_ORDER = List.of("faction", "crown", "rank", "name", "plus");
	private static final String DEFAULT_RANK = "DEFAULT";
	private static final String NAME_GAP = "";
	private static final Identifier PADDING_FONT = Identifier.withDefaultNamespace("padding");

	private record Icon(String key, int evolution, int level, String glyph, Identifier font, Integer color, Integer nameColor, Integer messageColor) {
	}

	// Style received from the relay
	private static List<String> order = DEFAULT_ORDER;
	private static List<Icon> ranks = List.of();
	private static List<Icon> crowns = List.of();
	private static List<Icon> factions = List.of();
	private static List<Icon> plus = List.of();
	private static Integer nameColor;
	private static Integer messageColor;
	private static final Set<String> MISSING_FACTIONS = new HashSet<>();

	private ChatStyle() {
	}

	public static void register() {
		SpotSync.addHandler("chat_style", ChatStyle::receive);
	}

	private static void receive(JsonObject object) {
		JsonObject style = object.has("style") && object.get("style").isJsonObject() ? object.getAsJsonObject("style") : new JsonObject();
		List<String> parts = new ArrayList<>();
		if (style.has("order") && style.get("order").isJsonArray()) {
			style.getAsJsonArray("order").forEach(part -> parts.add(part.getAsString()));
		}
		order = parts.isEmpty() ? DEFAULT_ORDER : List.copyOf(parts);
		ranks = icons(style, "ranks", "rank");
		crowns = icons(style, "crowns", null);
		factions = icons(style, "factions", "faction");
		plus = icons(style, "plus", null);
		nameColor = color(style, "nameColor");
		messageColor = color(style, "messageColor");
		MISSING_FACTIONS.clear();
	}

	private static List<Icon> icons(JsonObject style, String list, String key) {
		List<Icon> icons = new ArrayList<>();
		if (!style.has(list) || !style.get(list).isJsonArray()) {
			return icons;
		}
		JsonArray array = style.getAsJsonArray(list);
		for (JsonElement element : array) {
			if (!element.isJsonObject() || key != null && !element.getAsJsonObject().has(key)) {
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			int evolution = entry.has("evolution") && entry.get("evolution").isJsonPrimitive() ? entry.get("evolution").getAsInt() : 0;
			int level = entry.has("level") && entry.get("level").isJsonPrimitive() ? entry.get("level").getAsInt() : 0;
			String glyph = entry.has("glyph") ? entry.get("glyph").getAsString() : "";
			Identifier font = entry.has("font") ? Identifier.tryParse(entry.get("font").getAsString()) : null;
			icons.add(new Icon(key == null ? "" : entry.get(key).getAsString(), evolution, level, glyph, font, color(entry, "color"), color(entry, "nameColor"), color(entry, "messageColor")));
		}
		return List.copyOf(icons);
	}

	private static Integer color(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			return null;
		}
		TextColor color = TextColor.parseColor(object.get(key).getAsString()).result().orElse(null);
		return color == null ? null : color.getValue();
	}

	// First rank of the list the player has
	private static Icon rank(MccApi.Profile profile) {
		for (Icon icon : ranks) {
			if (profile.ranks().contains(icon.key())) {
				return icon;
			}
		}
		return ranks.stream().filter(icon -> DEFAULT_RANK.equals(icon.key())).findFirst().orElse(null);
	}

	// Highest evolution, then level, reached by the player
	private static Icon best(List<Icon> icons, String key, int evolution, int level) {
		Icon best = null;
		for (Icon icon : icons) {
			if (key != null && !simplify(icon.key()).equals(simplify(key)) || icon.evolution() > evolution || icon.level() > level) {
				continue;
			}
			if (best == null || icon.evolution() > best.evolution() || icon.evolution() == best.evolution() && icon.level() > best.level()) {
				best = icon;
			}
		}
		return best;
	}

	private static String simplify(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
	}

	// Selected faction, unknown names logged once
	private static Icon faction(MccApi.Profile profile) {
		String name = profile.faction();
		if (name == null) {
			return null;
		}
		Icon found = best(factions, name, Math.max(0, profile.factionEvolution()), profile.factionLevel());
		if (found != null || factions.stream().anyMatch(icon -> simplify(icon.key()).equals(simplify(name)))) {
			return found;
		}
		if (MISSING_FACTIONS.add(name)) {
			Mccinfo.LOGGER.info("MccInfo chat: no style for the faction \"{}\" in data/chat/style.json", name);
		}
		return null;
	}

	// Profile sent by the relay
	static MccApi.Profile profile(JsonObject object) {
		try {
			List<String> ranks = new ArrayList<>();
			if (object.has("ranks") && object.get("ranks").isJsonArray()) {
				object.getAsJsonArray("ranks").forEach(rank -> ranks.add(rank.getAsString()));
			}
			return new MccApi.Profile(List.copyOf(ranks), integer(object, "crownLevel", 0), integer(object, "crownEvolution", -1),
					object.has("faction") && object.get("faction").isJsonPrimitive() ? object.get("faction").getAsString() : null,
					integer(object, "factionLevel", 0), integer(object, "factionEvolution", -1),
					object.has("plusEvolution") && object.get("plusEvolution").isJsonPrimitive() ? object.get("plusEvolution").getAsInt() : null,
					integer(object, "plusDays", 0));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static int integer(JsonObject object, String key, int fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
	}

	public static boolean hasPlus(MccApi.Profile profile) {
		return profile.plusEvolution() != null;
	}

	public static int messageColor(MccApi.Profile profile) {
		Icon rank = rank(profile);
		if (rank != null && rank.messageColor() != null) {
			return rank.messageColor();
		}
		return messageColor != null ? messageColor : WHITE;
	}

	// Icons and name in the file order
	public static Component name(String name, MccApi.Profile profile) {
		Icon rank = rank(profile);
		Icon faction = faction(profile);
		int color = rank != null && rank.nameColor() != null ? rank.nameColor()
				: faction != null && faction.nameColor() != null ? faction.nameColor() : nameColor != null ? nameColor : WHITE;
		MutableComponent result = Component.empty();
		boolean first = true;
		for (String part : order) {
			Component piece = switch (part) {
				case "faction" -> glyph(faction);
				case "crown" -> profile.crownEvolution() < 0 ? null : glyph(best(crowns, null, profile.crownEvolution(), profile.crownLevel()));
				case "rank" -> glyph(rank);
				case "plus" -> profile.plusEvolution() == null ? null : glyph(best(plus, null, profile.plusEvolution(), profile.plusDays()));
				case "name" -> Component.literal(name).withColor(color);
				default -> null;
			};
			if (piece == null) {
				continue;
			}
			if (!first && "name".equals(part)) {
				result.append(Component.literal(NAME_GAP).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(PADDING_FONT))));
			}
			result.append(piece);
			first = false;
		}
		return result;
	}

	private static Component glyph(Icon icon) {
		if (icon == null || icon.glyph().isEmpty()) {
			return null;
		}
		Style style = Style.EMPTY.withColor(icon.color() != null ? icon.color() : WHITE);
		if (icon.font() != null) {
			style = style.withFont(new FontDescription.Resource(icon.font()));
		}
		return Component.literal(icon.glyph()).setStyle(style);
	}
}
