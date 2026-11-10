package leroidesafk.mccinfo.client.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// MCC Island API, key check and player queries
public final class MccApi {
	public enum KeyState { NONE, CHECKING, VALID, INVALID, UNREACHABLE }

	public record Profile(List<String> ranks, int crownLevel, int crownEvolution, String faction, int factionLevel, int factionEvolution,
			Integer plusEvolution, int plusDays) {
		static final Profile UNKNOWN = new Profile(List.of(), 0, -1, null, 0, -1, null, 0);
	}

	public enum FishStatus { READY, HIDDEN, FAILED }

	public record FishRecord(Set<String> caught, Map<String, Integer> trophies) {
	}

	public record FishRecords(FishStatus status, Map<String, FishRecord> weights) {
		static final FishRecords FAILED = new FishRecords(FishStatus.FAILED, Map.of());
		static final FishRecords HIDDEN = new FishRecords(FishStatus.HIDDEN, Map.of());
	}

	public record Reply(int status, JsonElement value) {
		public boolean answered() {
			return this.status == 200 && this.value != null && !this.value.isJsonNull();
		}
	}

	private record Pending(String selection, CompletableFuture<Reply> future) {
	}

	private record Cached(CompletableFuture<Profile> profile, long at) {
	}

	private static final URI ENDPOINT = URI.create("https://api.mccisland.net/graphql");
	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final long BATCH_DELAY_MILLIS = 500L;
	private static final String ALIAS = "q";
	private static final long CACHE_MILLIS = 15 * 60_000L;
	private static final long FAILED_CACHE_MILLIS = 60_000L;
	private static final long RECHECK_MILLIS = 60_000L;
	private static final String CHECK_SELECTION = "player(uuid: \"%s\") { uuid }";
	private static final String PROFILE_SELECTION = "player(uuid: \"%s\") { ranks crownLevel { levelData { level evolution } } "
			+ "factions { name selected levelData { level evolution } } mccPlusStatus { evolution totalDays } }";
	private static final String FISH_SELECTION = "player(uuid: \"%s\") { collections { fish { fish { name "
			+ "AVERAGE: trophies(weight: AVERAGE) LARGE: trophies(weight: LARGE) MASSIVE: trophies(weight: MASSIVE) "
			+ "GARGANTUAN: trophies(weight: GARGANTUAN) COLOSSAL: trophies(weight: COLOSSAL) } weights { weight } } } }";
	private static final List<String> FISH_WEIGHTS = List.of("AVERAGE", "LARGE", "MASSIVE", "GARGANTUAN", "COLOSSAL");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<Runnable> JOINERS = new ArrayList<>();
	private static long pendingSince;

	private static volatile KeyState state = KeyState.NONE;
	private static long nextCheck;
	private static int checkGeneration;

	private MccApi() {
	}

	public static void register() {
		leroidesafk.mccinfo.client.relay.SpotSync.onResend(() -> report(true));
		ClientTickEvents.END_CLIENT_TICK.register(client -> flush());
	}

	public static void onBatch(Runnable joiner) {
		JOINERS.add(joiner);
	}

	public static boolean hasKey() {
		return !key().isEmpty();
	}

	// Queries grouped in one request
	public static CompletableFuture<Reply> query(String selection) {
		CompletableFuture<Reply> future = new CompletableFuture<>();
		synchronized (PENDING) {
			if (PENDING.isEmpty()) {
				pendingSince = System.currentTimeMillis();
			}
			PENDING.add(new Pending(selection, future));
		}
		return future;
	}

	private static void flush() {
		synchronized (PENDING) {
			if (PENDING.isEmpty() || System.currentTimeMillis() - pendingSince < BATCH_DELAY_MILLIS) {
				return;
			}
		}
		for (Runnable joiner : JOINERS) {
			joiner.run();
		}
		List<Pending> batch;
		synchronized (PENDING) {
			batch = List.copyOf(PENDING);
			PENDING.clear();
		}
		String key = key();
		if (key.isEmpty()) {
			batch.forEach(pending -> pending.future().complete(new Reply(401, null)));
			return;
		}
		StringBuilder query = new StringBuilder("{ ");
		for (int i = 0; i < batch.size(); i++) {
			query.append(ALIAS).append(i).append(": ").append(batch.get(i).selection()).append(' ');
		}
		query.append('}');
		JsonObject body = new JsonObject();
		body.addProperty("query", query.toString());
		HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("X-API-Key", key)
				.header("User-Agent", "MccInfo/" + Permissions.MOD_VERSION + " (Minecraft mod)")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(TIMEOUT.toSeconds() + 2, TimeUnit.SECONDS).whenComplete((response, error) -> {
			int status = error != null ? 0 : response.statusCode();
			if (error != null) {
				Mccinfo.LOGGER.warn("MCC Island API unreachable ({})", String.valueOf(error));
			}
			JsonObject data = status == 200 ? data(response.body()) : null;
			for (int i = 0; i < batch.size(); i++) {
				batch.get(i).future().complete(new Reply(status, data == null ? null : data.get(ALIAS + i)));
			}
		});
	}

	private static JsonObject data(String body) {
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			return root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String own() {
		return Minecraft.getInstance().getUser().getProfileId().toString();
	}

	private static void setState(KeyState value) {
		boolean changed = (state == KeyState.VALID) != (value == KeyState.VALID);
		state = value;
		report(changed);
	}

	// Tells the relay if the key is valid, never the key
	private static void report(boolean force) {
		if (!force) {
			return;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "api_key");
		message.addProperty("valid", state == KeyState.VALID && !key().isEmpty());
		leroidesafk.mccinfo.client.relay.SpotSync.sendVoice(message);
	}

	public static KeyState state() {
		if (key().isEmpty()) {
			return KeyState.NONE;
		}
		if ((state == KeyState.UNREACHABLE || state == KeyState.NONE) && System.currentTimeMillis() >= nextCheck) {
			check();
		}
		return state;
	}

	public static boolean ready() {
		return state() == KeyState.VALID;
	}

	private static String key() {
		String key = ModConfig.get().apiToken;
		return key == null ? "" : key.strip();
	}

	// Key check with the own player
	public static void check() {
		CACHE.clear();
		String key = key();
		int generation = ++checkGeneration;
		if (key.isEmpty()) {
			setState(KeyState.NONE);
			return;
		}
		setState(KeyState.CHECKING);
		nextCheck = System.currentTimeMillis() + RECHECK_MILLIS;
		query(String.format(CHECK_SELECTION, own())).whenComplete((reply, error) -> Minecraft.getInstance().execute(() -> {
			if (generation != checkGeneration) {
				return;
			}
			int status = reply == null ? 0 : reply.status();
			if (status == 0) {
				setState(KeyState.UNREACHABLE);
			} else if (status == 401 || status == 403) {
				setState(KeyState.INVALID);
			} else if (reply.answered()) {
				setState(KeyState.VALID);
			} else if (status == 200 || status == 400) {
				setState(KeyState.INVALID);
				Mccinfo.LOGGER.warn("MCC Island API key refused ({})", status);
			} else {
				setState(KeyState.UNREACHABLE);
				Mccinfo.LOGGER.warn("MCC Island API answered {}", status);
			}
		}));
	}

	// Player profile, cached
	public static CompletableFuture<Profile> profile(String uuid) {
		UUID parsed = parse(uuid);
		if (parsed == null || !ready()) {
			return CompletableFuture.completedFuture(Profile.UNKNOWN);
		}
		long now = System.currentTimeMillis();
		Cached cached = CACHE.get(parsed.toString());
		if (cached != null && now - cached.at() < (failed(cached) ? FAILED_CACHE_MILLIS : CACHE_MILLIS)) {
			return cached.profile();
		}
		CompletableFuture<Profile> future = query(String.format(PROFILE_SELECTION, parsed)).handle((reply, error) -> {
			if (reply == null || reply.status() != 200) {
				if (reply != null && (reply.status() == 401 || reply.status() == 403)) {
					Minecraft.getInstance().execute(() -> setState(KeyState.INVALID));
				}
				return Profile.UNKNOWN;
			}
			return read(reply.value());
		});
		CACHE.put(parsed.toString(), new Cached(future, now));
		return future;
	}

	// Caught fish weights and trophies of the own player
	public static CompletableFuture<FishRecords> fishRecords() {
		if (!hasKey()) {
			return CompletableFuture.completedFuture(FishRecords.FAILED);
		}
		return query(String.format(FISH_SELECTION, own())).handle((reply, error) -> {
			if (reply == null || reply.status() != 200) {
				if (reply != null && (reply.status() == 401 || reply.status() == 403)) {
					Minecraft.getInstance().execute(() -> setState(KeyState.INVALID));
				}
				return FishRecords.FAILED;
			}
			return readFish(reply.value());
		});
	}

	private static FishRecords readFish(JsonElement value) {
		try {
			if (value == null || !value.isJsonObject()) {
				return FishRecords.FAILED;
			}
			JsonObject player = value.getAsJsonObject();
			if (!player.has("collections") || !player.get("collections").isJsonObject()) {
				return FishRecords.HIDDEN;
			}
			JsonObject collections = player.getAsJsonObject("collections");
			if (!collections.has("fish") || !collections.get("fish").isJsonArray()) {
				return FishRecords.HIDDEN;
			}
			Map<String, FishRecord> weights = new HashMap<>();
			for (JsonElement element : collections.getAsJsonArray("fish")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject record = element.getAsJsonObject();
				JsonObject fish = record.has("fish") && record.get("fish").isJsonObject() ? record.getAsJsonObject("fish") : null;
				if (fish == null || !fish.has("name") || !fish.get("name").isJsonPrimitive()) {
					continue;
				}
				Set<String> caught = new HashSet<>();
				if (record.has("weights") && record.get("weights").isJsonArray()) {
					for (JsonElement weight : record.getAsJsonArray("weights")) {
						if (weight.isJsonObject() && weight.getAsJsonObject().has("weight")) {
							caught.add(weight.getAsJsonObject().get("weight").getAsString().toUpperCase(Locale.ROOT));
						}
					}
				}
				Map<String, Integer> trophies = new HashMap<>();
				for (String weight : FISH_WEIGHTS) {
					if (fish.has(weight) && fish.get(weight).isJsonPrimitive()) {
						trophies.put(weight, fish.get(weight).getAsInt());
					}
				}
				weights.put(fish.get("name").getAsString().strip().toLowerCase(Locale.ROOT), new FishRecord(Set.copyOf(caught), Map.copyOf(trophies)));
			}
			return new FishRecords(FishStatus.READY, Map.copyOf(weights));
		} catch (RuntimeException e) {
			Mccinfo.LOGGER.warn("MCC Island API: unreadable fish collection ({})", e.toString());
			return FishRecords.FAILED;
		}
	}

	private static boolean failed(Cached cached) {
		return cached.profile().isDone() && cached.profile().getNow(Profile.UNKNOWN) == Profile.UNKNOWN;
	}

	// Ranks, crown, selected faction and MCC+
	private static Profile read(JsonElement value) {
		try {
			if (value == null || !value.isJsonObject()) {
				return new Profile(List.of(), 0, -1, null, 0, -1, null, 0);
			}
			JsonObject player = value.getAsJsonObject();
			List<String> ranks = new ArrayList<>();
			if (player.has("ranks") && player.get("ranks").isJsonArray()) {
				for (JsonElement rank : player.getAsJsonArray("ranks")) {
					ranks.add(rank.getAsString());
				}
			}
			int level = 0;
			int evolution = -1;
			if (player.has("crownLevel") && player.get("crownLevel").isJsonObject()) {
				JsonObject levelData = player.getAsJsonObject("crownLevel").getAsJsonObject("levelData");
				if (levelData != null) {
					level = levelData.get("level").getAsInt();
					evolution = levelData.get("evolution").getAsInt();
				}
			}
			String faction = null;
			int factionLevel = 0;
			int factionEvolution = -1;
			if (player.has("factions") && player.get("factions").isJsonArray()) {
				JsonArray factions = player.getAsJsonArray("factions");
				for (JsonElement element : factions) {
					JsonObject entry = element.getAsJsonObject();
					if (entry.has("selected") && entry.get("selected").getAsBoolean()) {
						faction = entry.get("name").getAsString();
						JsonObject factionData = entry.has("levelData") && entry.get("levelData").isJsonObject() ? entry.getAsJsonObject("levelData") : null;
						if (factionData != null) {
							factionLevel = factionData.get("level").getAsInt();
							factionEvolution = factionData.get("evolution").getAsInt();
						}
					}
				}
			}
			JsonObject plusStatus = player.has("mccPlusStatus") && player.get("mccPlusStatus").isJsonObject() ? player.getAsJsonObject("mccPlusStatus") : null;
			Integer plus = plusStatus == null ? null : plusStatus.get("evolution").getAsInt();
			int plusDays = plusStatus == null || !plusStatus.has("totalDays") ? 0 : plusStatus.get("totalDays").getAsInt();
			return new Profile(List.copyOf(ranks), level, evolution, faction, factionLevel, factionEvolution, plus, plusDays);
		} catch (RuntimeException e) {
			Mccinfo.LOGGER.warn("MCC Island API: unreadable player ({})", e.toString());
			return Profile.UNKNOWN;
		}
	}

	// Relay uuid without dashes
	private static UUID parse(String uuid) {
		if (uuid == null) {
			return null;
		}
		String hex = uuid.replace("-", "");
		if (hex.length() != 32) {
			return null;
		}
		try {
			return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
