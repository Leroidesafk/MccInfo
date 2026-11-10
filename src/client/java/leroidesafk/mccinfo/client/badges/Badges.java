package leroidesafk.mccinfo.client.badges;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.chat.MccApi;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// MCC badges: progress from the API, models from the relay
public final class Badges {
	public enum Status { NO_KEY, LOADING, HIDDEN, FAILED, READY }

	// Stage of a badge and its progress
	public record Stage(int stage, int trophies, int bonusTrophies, int obtained, int obtainable) {
		public boolean done() {
			return this.obtainable > 0 && this.obtained >= this.obtainable;
		}
	}

	public record Badge(String name, String goal, boolean statistic, List<Stage> stages) {
		public int completed() {
			int count = 0;
			for (Stage stage : this.stages) {
				count += stage.done() ? 1 : 0;
			}
			return count;
		}

		public boolean complete() {
			return !this.stages.isEmpty() && completed() == this.stages.size();
		}

		public Stage current() {
			for (Stage stage : this.stages) {
				if (!stage.done()) {
					return stage;
				}
			}
			return null;
		}

		public float progress() {
			Stage current = current();
			if (current == null) {
				return 1.0F;
			}
			return current.obtainable() <= 0 ? 0.0F : Math.min(1.0F, (float) current.obtained() / current.obtainable());
		}
	}

	// Model, description and modes from the relay
	public record Model(Identifier model, String description, String perGame, boolean available, List<String> modes) {
	}

	public record Game(String folder, String name) {
	}

	public record Mode(String id, String name, Identifier icon, String queue) {
	}

	public record Level(String id, String name, int color) {
	}

	// Tabs, games, modes and popularity levels
	static final String GENERAL = "general";
	private static final Identifier SUGGESTED_ICON = Identifier.fromNamespaceAndPath("mcc", "island_interface/achievements/icon");
	private static final Identifier GENERAL_ICON = Identifier.fromNamespaceAndPath("mcc", "island_interface/navigator/island");
	private static final List<Game> DEFAULT_GAMES = List.of(new Game("battle_box", "Battle Box"), new Game("dynaball", "Dynaball"),
			new Game("hitw", "Hole in the Wall"), new Game("pkw", "Parkour Warrior"), new Game("rocket_spleef", "Rocket Spleef Rush"),
			new Game("sky_battle", "Sky Battle"), new Game("tgttos", "TGTTOS"));
	private static List<Mode> MODES = List.of();
	private static final List<String> DEFAULT_POPULARITY = List.of("low", "medium", "high", "popular");
	private static List<Game> GAMES = DEFAULT_GAMES;
	private static List<String> POPULARITY = DEFAULT_POPULARITY;
	private static Map<String, Level> LEVELS = Map.of();
	private static final int TINT_PROGRESS = 0xFFAE05;
	private static final int TINT_COMPLETE = 0x55FF55;
	private static final int TINT_NONE = 0x8C8C8C;
	private static final Map<String, String> GAME_ICON_FOLDERS = Map.of("hitw", "hole_in_the_wall", "pkw", "parkour_warrior");
	private static final String BADGE_MODEL_PREFIX = "island_interface/badges/";
	// API queries
	private static final String LIST_SELECTION = "badges { name goal { __typename ... on GeneralGoal { name } ... on Statistic { key } } "
			+ "stages { stage trophies bonusTrophies } }";
	private static final String PROGRESS_SELECTION = "player(uuid: \"%s\") { badges { badge { name } stageProgress { stage progress { obtained obtainable } } }%s }";
	private static final String STATISTIC_SELECTION = " %s: rotationValue(statisticKey: \"%s\", rotation: LIFETIME)";
	private static final java.util.regex.Pattern STATISTIC_KEY = java.util.regex.Pattern.compile("[a-z][a-z0-9_]{0,63}");
	private static final long OPEN_REFRESH_MILLIS = 30_000L;
	private static final int MAX_MODELS = 2000;

	// Models, badges with progress and games played
	private static Map<String, Model> MODELS = Map.of();
	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static List<Badge> definitions;
	private static CompletableFuture<List<Badge>> definitionsLoad;
	private static List<Badge> BADGES = List.of();
	private static Map<String, Integer> GAMES_PLAYED = Map.of();
	private static Status status = Status.NO_KEY;
	private static boolean loading;
	private static long lastRequest;
	private static int generation;

	private Badges() {
	}

	// Badge Tracker key and notifications
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Badges::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (leroidesafk.mccinfo.client.PrivateKeys.BADGE_TRACKER.consumeClick()) {
				MccApi.KeyState state = MccApi.state();
				if (client.gui.screen() == null && allowed() && state != MccApi.KeyState.NONE && state != MccApi.KeyState.INVALID) {
					client.gui.setScreen(new BadgesScreen(null));
				}
			}
		});
		BadgeNotifier.register();
	}

	public static boolean allowed() {
		return Permissions.has(Permissions.Perm.UTILS);
	}

	static Status status() {
		return status;
	}

	// Unfinished badges, fewest games left first
	static List<Badge> suggested(int count) {
		List<Badge> list = new ArrayList<>();
		for (Badge badge : BADGES) {
			if (!badge.complete() && available(badge) && gamesLeft(badge) != null) {
				list.add(badge);
			}
		}
		list.sort(Comparator.comparingInt((Badge badge) -> gamesLeft(badge)).thenComparing(badge -> -badge.progress()));
		return list.subList(0, Math.min(count, list.size()));
	}

	static List<Badge> badges() {
		return BADGES;
	}

	public static List<Game> games() {
		return GAMES;
	}

	public static List<String> popularityLevels() {
		return POPULARITY;
	}

	public static Level level(String id) {
		Level level = LEVELS.get(id);
		return level != null ? level : new Level(id, BadgeNotifier.words(id), 0xFFFFFF);
	}

	public static net.minecraft.network.chat.Component levelName(String id) {
		Level level = level(id);
		return net.minecraft.network.chat.Component.literal(level.name()).withStyle(style -> style.withColor(level.color()));
	}

	public static List<Mode> modes() {
		return MODES;
	}

	public static Mode mode(String id) {
		for (Mode mode : MODES) {
			if (mode.id().equals(id)) {
				return mode;
			}
		}
		return null;
	}

	static List<Mode> modesOf(Badge badge) {
		Model model = model(badge.name());
		List<Mode> list = new ArrayList<>();
		if (model != null) {
			for (String id : model.modes()) {
				Mode mode = mode(id);
				if (mode != null) {
					list.add(mode);
				}
			}
		}
		return list;
	}

	public static ItemStack modeIcon(Mode mode) {
		return mode.icon() == null ? ItemStack.EMPTY : stack(mode.icon());
	}

	public static Game game(String folder) {
		for (Game game : GAMES) {
			if (game.folder().equals(folder)) {
				return game;
			}
		}
		return null;
	}

	static Integer gamesPlayed(String key) {
		return key == null ? null : GAMES_PLAYED.get(key);
	}

	private static void tick(Minecraft client) {
		if (!allowed()) {
			return;
		}
		MccApi.KeyState key = MccApi.state();
		if (key == MccApi.KeyState.VALID || key == MccApi.KeyState.CHECKING) {
			if (status == Status.NO_KEY) {
				status = Status.LOADING;
			}
			return;
		}
		generation++;
		loading = false;
		lastRequest = 0;
		BADGES = List.of();
		status = key == MccApi.KeyState.UNREACHABLE ? Status.FAILED : Status.NO_KEY;
	}

	static void refreshOnOpen() {
		refresh(OPEN_REFRESH_MILLIS);
	}

	// Player progress and games played, merged with the badge list
	static void refresh(long minimumMillis) {
		if (!allowed() || !MccApi.ready() || loading || System.currentTimeMillis() - lastRequest < minimumMillis) {
			return;
		}
		loading = true;
		lastRequest = System.currentTimeMillis();
		if (BADGES.isEmpty()) {
			status = Status.LOADING;
		}
		int current = ++generation;
		String uuid = Minecraft.getInstance().getUser().getProfileId().toString();
		StringBuilder statistics = new StringBuilder();
		java.util.Set<String> keys = new java.util.TreeSet<>();
		for (Model model : MODELS.values()) {
			if (model.perGame() != null && STATISTIC_KEY.matcher(model.perGame()).matches()) {
				keys.add(model.perGame());
			}
		}
		for (String key : keys) {
			statistics.append(String.format(STATISTIC_SELECTION, key, key));
		}
		String statisticsPart = keys.isEmpty() ? "" : " statistics {" + statistics + " }";
		CompletableFuture<MccApi.Reply> progress = MccApi.query(String.format(PROGRESS_SELECTION, uuid, statisticsPart));
		definitions().thenCombine(progress, Badges::merge).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (current != generation) {
				return;
			}
			loading = false;
			if (error != null || result == null) {
				if (BADGES.isEmpty()) {
					status = Status.FAILED;
				}
				return;
			}
			BADGES = result.badges();
			GAMES_PLAYED = result.gamesPlayed();
			status = result.hidden() ? Status.HIDDEN : Status.READY;
		}));
	}

	private record Merged(List<Badge> badges, boolean hidden, Map<String, Integer> gamesPlayed) {
	}

	private static Merged merge(List<Badge> list, MccApi.Reply reply) {
		if (list == null || list.isEmpty()) {
			throw new IllegalStateException("no badge list");
		}
		Map<String, Map<Integer, int[]>> progress = new HashMap<>();
		boolean hidden = true;
		Map<String, Integer> gamesPlayed = new HashMap<>();
		if (reply != null && reply.answered() && reply.value().isJsonObject()) {
			JsonObject player = reply.value().getAsJsonObject();
			if (player.has("statistics") && player.get("statistics").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : player.getAsJsonObject("statistics").entrySet()) {
					if (entry.getValue().isJsonPrimitive()) {
						gamesPlayed.put(entry.getKey(), entry.getValue().getAsInt());
					}
				}
			}
			if (player.has("badges") && player.get("badges").isJsonArray()) {
				hidden = false;
				for (JsonElement element : player.getAsJsonArray("badges")) {
					readProgress(element, progress);
				}
			}
		} else if (reply == null || reply.status() != 200) {
			throw new IllegalStateException("badge progress failed");
		}
		List<Badge> merged = new ArrayList<>();
		for (Badge badge : list) {
			Map<Integer, int[]> own = progress.getOrDefault(key(badge.name()), Map.of());
			List<Stage> stages = new ArrayList<>();
			for (Stage stage : badge.stages()) {
				int[] values = own.getOrDefault(stage.stage(), new int[] {0, 0});
				stages.add(new Stage(stage.stage(), stage.trophies(), stage.bonusTrophies(), values[0], values[1]));
			}
			merged.add(new Badge(badge.name(), badge.goal(), badge.statistic(), List.copyOf(stages)));
		}
		return new Merged(List.copyOf(merged), hidden, Map.copyOf(gamesPlayed));
	}

	private static void readProgress(JsonElement element, Map<String, Map<Integer, int[]>> progress) {
		if (!element.isJsonObject()) {
			return;
		}
		JsonObject entry = element.getAsJsonObject();
		String name = entry.has("badge") && entry.get("badge").isJsonObject() ? string(entry.getAsJsonObject("badge"), "name") : null;
		if (name == null || !entry.has("stageProgress") || !entry.get("stageProgress").isJsonArray()) {
			return;
		}
		Map<Integer, int[]> stages = progress.computeIfAbsent(key(name), ignored -> new HashMap<>());
		for (JsonElement stageElement : entry.getAsJsonArray("stageProgress")) {
			if (!stageElement.isJsonObject()) {
				continue;
			}
			JsonObject stage = stageElement.getAsJsonObject();
			JsonObject values = stage.has("progress") && stage.get("progress").isJsonObject() ? stage.getAsJsonObject("progress") : null;
			if (values == null || !stage.has("stage")) {
				continue;
			}
			stages.put(stage.get("stage").getAsInt(), new int[] {integer(values, "obtained"), integer(values, "obtainable")});
		}
	}

	// Badge list from the API, loaded once
	public static CompletableFuture<List<Badge>> definitions() {
		if (definitions != null) {
			return CompletableFuture.completedFuture(definitions);
		}
		if (definitionsLoad != null && !definitionsLoad.isDone()) {
			return definitionsLoad;
		}
		definitionsLoad = MccApi.query(LIST_SELECTION).thenApply(reply -> {
			if (reply == null || !reply.answered() || !reply.value().isJsonArray()) {
				throw new IllegalStateException("badge list failed");
			}
			List<Badge> list = new ArrayList<>();
			for (JsonElement element : reply.value().getAsJsonArray()) {
				Badge badge = readDefinition(element);
				if (badge != null) {
					list.add(badge);
				}
			}
			List<Badge> result = List.copyOf(list);
			definitions = result;
			return result;
		});
		return definitionsLoad;
	}

	private static Badge readDefinition(JsonElement element) {
		try {
			if (!element.isJsonObject()) {
				return null;
			}
			JsonObject object = element.getAsJsonObject();
			String name = string(object, "name");
			if (name == null) {
				return null;
			}
			String goal = null;
			boolean statistic = false;
			if (object.has("goal") && object.get("goal").isJsonObject()) {
				JsonObject goalObject = object.getAsJsonObject("goal");
				statistic = "Statistic".equals(string(goalObject, "__typename"));
				goal = statistic ? string(goalObject, "key") : string(goalObject, "name");
			}
			List<Stage> stages = new ArrayList<>();
			if (object.has("stages") && object.get("stages").isJsonArray()) {
				for (JsonElement stageElement : object.getAsJsonArray("stages")) {
					if (stageElement.isJsonObject()) {
						JsonObject stage = stageElement.getAsJsonObject();
						stages.add(new Stage(integer(stage, "stage"), integer(stage, "trophies"), integer(stage, "bonusTrophies"), 0, 0));
					}
				}
			}
			stages.sort(Comparator.comparingInt(Stage::stage));
			return new Badge(name.strip(), goal, statistic, List.copyOf(stages));
		} catch (RuntimeException e) {
			Mccinfo.LOGGER.warn("MCC Island API: unreadable badge ({})", e.toString());
			return null;
		}
	}

	// Models sent by the relay
	public static void receive(JsonObject object) {
		if (!object.has("badges") || !object.get("badges").isJsonArray()) {
			return;
		}
		Map<String, Model> models = new LinkedHashMap<>();
		for (JsonElement element : object.getAsJsonArray("badges")) {
			if (models.size() >= MAX_MODELS || !element.isJsonObject()) {
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			String name = string(entry, "name");
			String model = string(entry, "model");
			Identifier id = model == null ? null : Identifier.tryParse(model);
			if (name == null || id == null) {
				continue;
			}
			boolean available = !entry.has("available") || !entry.get("available").isJsonPrimitive() || entry.get("available").getAsBoolean();
			models.put(key(name), new Model(id, string(entry, "description"), string(entry, "perGame"), available, strings(entry, "modes")));
		}
		List<Game> games = new ArrayList<>();
		if (object.has("games") && object.get("games").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("games")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject entry = element.getAsJsonObject();
				String folder = string(entry, "folder");
				String name = string(entry, "name");
				if (folder == null || name == null) {
					continue;
				}
				games.add(new Game(folder, name));
			}
		}
		List<Mode> modes = new ArrayList<>();
		if (object.has("modes") && object.get("modes").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("modes")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject entry = element.getAsJsonObject();
				String id = string(entry, "id");
				String name = string(entry, "name");
				String icon = string(entry, "icon");
				if (id != null && name != null) {
					modes.add(new Mode(id, name, icon == null ? null : Identifier.tryParse(icon), string(entry, "queue")));
				}
			}
		}
		List<String> popularity = new ArrayList<>();
		Map<String, Level> levels = new HashMap<>();
		if (object.has("popularity") && object.get("popularity").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("popularity")) {
				if (element.isJsonPrimitive()) {
					popularity.add(element.getAsString().toLowerCase(Locale.ROOT));
				} else if (element.isJsonObject()) {
					JsonObject entry = element.getAsJsonObject();
					String id = string(entry, "id");
					if (id == null) {
						continue;
					}
					id = id.toLowerCase(Locale.ROOT);
					String name = string(entry, "name");
					String color = string(entry, "color");
					int rgb = 0xFFFFFF;
					if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
						rgb = Integer.parseInt(color.substring(1), 16);
					}
					popularity.add(id);
					levels.put(id, new Level(id, name == null ? BadgeNotifier.words(id) : name, rgb));
				}
			}
		}
		Minecraft.getInstance().execute(() -> {
			MODELS = Map.copyOf(models);
			GAMES = games.isEmpty() ? DEFAULT_GAMES : List.copyOf(games);
			MODES = List.copyOf(modes);
			POPULARITY = popularity.isEmpty() ? DEFAULT_POPULARITY : List.copyOf(popularity);
			LEVELS = Map.copyOf(levels);
			ICONS.clear();
		});
	}

	static Object modelsToken() {
		return MODELS;
	}

	static boolean available(Badge badge) {
		Model model = model(badge.name());
		return model == null || model.available();
	}

	static ItemStack suggestedIcon() {
		return stack(SUGGESTED_ICON);
	}

	static Model model(String name) {
		return MODELS.get(key(name));
	}

	// Badge icon tinted by progress
	static ItemStack icon(Badge badge) {
		Model model = model(badge.name());
		if (model == null) {
			return ItemStack.EMPTY;
		}
		int tint = badge.complete() ? TINT_COMPLETE : badge.completed() > 0 || badge.progress() > 0.0F ? TINT_PROGRESS : TINT_NONE;
		return ICONS.computeIfAbsent(model.model() + "#" + tint, ignored -> {
			ItemStack stack = new ItemStack(Items.LEATHER_HORSE_ARMOR);
			stack.set(DataComponents.ITEM_MODEL, model.model());
			stack.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(tint));
			return stack;
		});
	}

	// Games left for the current stage, from the games played
	static Integer gamesLeft(Badge badge) {
		Stage current = badge.current();
		Model model = model(badge.name());
		if (current == null || current.obtainable() <= 0 || model == null || badge.stages().size() <= 1) {
			return null;
		}
		Integer games = gamesPlayed(model.perGame());
		if (games == null || games <= 0 || current.obtained() <= 0) {
			return null;
		}
		double perGame = (double) current.obtained() / games;
		return (int) Math.ceil((current.obtainable() - current.obtained()) / perGame);
	}

	static Double perGame(Badge badge) {
		Model model = model(badge.name());
		Stage current = badge.current();
		Integer games = model == null ? null : gamesPlayed(model.perGame());
		return current == null || games == null || games <= 0 ? null : (double) current.obtained() / games;
	}

	// Game of a badge and its icon
	static String gameFolder(String name) {
		Model model = model(name);
		if (model == null || !model.model().getPath().startsWith(BADGE_MODEL_PREFIX)) {
			return null;
		}
		String rest = model.model().getPath().substring(BADGE_MODEL_PREFIX.length());
		int slash = rest.indexOf('/');
		return slash > 0 ? rest.substring(0, slash) : null;
	}

	public static ItemStack gameIcon(String folder) {
		if (GENERAL.equals(folder)) {
			return stack(GENERAL_ICON);
		}
		if (folder == null || game(folder) == null) {
			return ItemStack.EMPTY;
		}
		String game = GAME_ICON_FOLDERS.getOrDefault(folder, folder);
		return stack(Identifier.fromNamespaceAndPath("mcc", "island_interface/game/" + game + "/icon"));
	}

	private static ItemStack stack(Identifier model) {
		return ICONS.computeIfAbsent(model.toString(), ignored -> {
			ItemStack stack = new ItemStack(Items.ECHO_SHARD);
			stack.set(DataComponents.ITEM_MODEL, model);
			return stack;
		});
	}

	static String key(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}

	private static String string(JsonObject object, String field) {
		return object.has(field) && object.get(field).isJsonPrimitive() ? object.get(field).getAsString() : null;
	}

	private static List<String> strings(JsonObject object, String field) {
		List<String> list = new ArrayList<>();
		if (object.has(field) && object.get(field).isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray(field)) {
				if (element.isJsonPrimitive()) {
					list.add(element.getAsString());
				}
			}
		}
		return List.copyOf(list);
	}

	private static int integer(JsonObject object, String field) {
		return object.has(field) && object.get(field).isJsonPrimitive() ? object.get(field).getAsInt() : 0;
	}
}
