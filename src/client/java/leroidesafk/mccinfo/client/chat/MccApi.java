package leroidesafk.mccinfo.client.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MccApi {
	public enum KeyState { NONE, CHECKING, VALID, INVALID, UNREACHABLE }

	public record Profile(List<String> ranks, int crownLevel, int crownEvolution, String faction, int factionLevel, int factionEvolution,
			Integer plusEvolution, int plusDays) {
		static final Profile UNKNOWN = new Profile(List.of(), 0, -1, null, 0, -1, null, 0);
	}

	private record Cached(CompletableFuture<Profile> profile, long at) {
	}

	// MCC Island API, key check and player queries
	private static final URI ENDPOINT = URI.create("https://api.mccisland.net/graphql");
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long CACHE_MILLIS = 15 * 60_000L;
	private static final long FAILED_CACHE_MILLIS = 60_000L;
	private static final long RECHECK_MILLIS = 60_000L;
	private static final String CHECK_QUERY = "query($uuid: UUID!) { player(uuid: $uuid) { uuid } }";
	private static final String PROFILE_QUERY = "query($uuid: UUID!) { player(uuid: $uuid) { ranks crownLevel { levelData { level evolution } } "
			+ "factions { name selected levelData { level evolution } } mccPlusStatus { evolution totalDays } } }";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

	private static volatile KeyState state = KeyState.NONE;
	private static long nextCheck;
	private static int checkGeneration;

	private MccApi() {
	}

	public static void register() {
		leroidesafk.mccinfo.client.relay.SpotSync.onResend(() -> report(true));
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
		UUID own = Minecraft.getInstance().getUser().getProfileId();
		request(key, CHECK_QUERY, own).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			if (generation != checkGeneration) {
				return;
			}
			if (error != null) {
				setState(KeyState.UNREACHABLE);
				Mccinfo.LOGGER.warn("MCC Island API unreachable ({})", String.valueOf(error));
			} else if (response.statusCode() == 401 || response.statusCode() == 403) {
				setState(KeyState.INVALID);
			} else if (response.statusCode() == 200 && !hasErrors(response.body())) {
				setState(KeyState.VALID);
			} else if (response.statusCode() == 200 || response.statusCode() == 400) {
				setState(KeyState.INVALID);
				Mccinfo.LOGGER.warn("MCC Island API key refused ({})", response.body());
			} else {
				setState(KeyState.UNREACHABLE);
				Mccinfo.LOGGER.warn("MCC Island API answered {}", response.statusCode());
			}
		}));
	}

	// Player profile, one request per player at a time, cached
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
		CompletableFuture<Profile> future = request(key(), PROFILE_QUERY, parsed).handle((response, error) -> {
			if (error != null || response.statusCode() != 200) {
				if (response != null && (response.statusCode() == 401 || response.statusCode() == 403)) {
					Minecraft.getInstance().execute(() -> setState(KeyState.INVALID));
				}
				return Profile.UNKNOWN;
			}
			return read(response.body());
		});
		CACHE.put(parsed.toString(), new Cached(future, now));
		return future;
	}

	private static boolean failed(Cached cached) {
		return cached.profile().isDone() && cached.profile().getNow(Profile.UNKNOWN) == Profile.UNKNOWN;
	}

	private static CompletableFuture<HttpResponse<String>> request(String key, String query, UUID uuid) {
		JsonObject body = new JsonObject();
		body.addProperty("query", query);
		JsonObject variables = new JsonObject();
		variables.addProperty("uuid", uuid.toString());
		body.add("variables", variables);
		HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("X-API-Key", key)
				.header("User-Agent", "MccInfo/" + Permissions.MOD_VERSION + " (Minecraft mod)")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).orTimeout(TIMEOUT.toSeconds() + 2, TimeUnit.SECONDS);
	}

	private static boolean hasErrors(String body) {
		try {
			JsonObject object = JsonParser.parseString(body).getAsJsonObject();
			return object.has("errors") && object.get("errors").isJsonArray() && !object.getAsJsonArray("errors").isEmpty();
		} catch (RuntimeException e) {
			return true;
		}
	}

	// Ranks, crown, selected faction and MCC+
	private static Profile read(String body) {
		try {
			JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
			if (data == null || !data.has("player") || !data.get("player").isJsonObject()) {
				return new Profile(List.of(), 0, -1, null, 0, -1, null, 0);
			}
			JsonObject player = data.getAsJsonObject("player");
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
