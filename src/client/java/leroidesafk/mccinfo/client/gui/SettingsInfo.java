package leroidesafk.mccinfo.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
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
import java.util.regex.Pattern;

// Texts and pictures of the Settings Info box, sent by the relay
public final class SettingsInfo {
	private static final Pattern IMAGE_NAME = Pattern.compile("^[a-z0-9_.-]{1,64}\\.png$");
	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final int MAX_LINES = 64;
	private static final int MAX_TEXT = 1000;

	// File of a setting, and a loaded picture
	record Entry(String title, List<String> text) {
	}

	record Image(Identifier location, int width, int height, String version) {
	}

	// Received files, picture versions and loaded textures
	private static Map<String, Entry> entries = Map.of();
	private static Map<String, String> versions = Map.of();
	private static final Map<String, Image> IMAGES = new HashMap<>();
	private static final Set<String> LOADING = new HashSet<>();
	private static final Set<String> FAILED = new HashSet<>();
	private static int revision;
	private static HttpClient http;

	private SettingsInfo() {
	}

	public static boolean enabled() {
		return ModConfig.get().settingsInfo && Permissions.has(Permissions.Perm.UTILS);
	}

	static int revision() {
		return revision;
	}

	static Entry entry(String key) {
		return entries.get(key.toLowerCase(Locale.ROOT));
	}

	// Files and picture versions from the relay, changed pictures released
	public static void receive(JsonObject object) {
		Map<String, Entry> received = new HashMap<>();
		if (object.has("entries") && object.get("entries").isJsonObject()) {
			for (Map.Entry<String, JsonElement> item : object.getAsJsonObject("entries").entrySet()) {
				if (!item.getValue().isJsonObject()) {
					continue;
				}
				JsonObject data = item.getValue().getAsJsonObject();
				String title = data.has("title") && data.get("title").isJsonPrimitive() ? data.get("title").getAsString().strip() : "";
				List<String> text = new ArrayList<>();
				if (data.has("text") && data.get("text").isJsonArray()) {
					for (JsonElement line : data.getAsJsonArray("text")) {
						if (line.isJsonPrimitive() && text.size() < MAX_LINES) {
							String value = line.getAsString();
							text.add(value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value);
						}
					}
				}
				received.put(item.getKey().toLowerCase(Locale.ROOT), new Entry(title, List.copyOf(text)));
			}
		}
		Map<String, String> images = new HashMap<>();
		if (object.has("images") && object.get("images").isJsonObject()) {
			for (Map.Entry<String, JsonElement> item : object.getAsJsonObject("images").entrySet()) {
				if (IMAGE_NAME.matcher(item.getKey()).matches() && item.getValue().isJsonPrimitive()) {
					images.put(item.getKey(), item.getValue().getAsString());
				}
			}
		}
		entries = Map.copyOf(received);
		versions = Map.copyOf(images);
		FAILED.clear();
		for (String name : List.copyOf(IMAGES.keySet())) {
			Image image = IMAGES.get(name);
			if (!image.version().equals(versions.get(name))) {
				IMAGES.remove(name);
				Minecraft.getInstance().getTextureManager().release(image.location());
			}
		}
		revision++;
	}

	// Loaded picture, downloaded on first use
	static Image image(String name) {
		Image image = IMAGES.get(name);
		if (image != null) {
			return image;
		}
		String version = versions.get(name);
		if (version != null && !LOADING.contains(name) && !FAILED.contains(name)) {
			load(name, version);
		}
		return null;
	}

	private static void load(String name, String version) {
		URI uri = SpotSync.httpUri("/settings/images/" + name);
		if (uri == null) {
			return;
		}
		synchronized (SettingsInfo.class) {
			if (http == null) {
				http = SpotSync.relayHttpClient().connectTimeout(TIMEOUT).build();
			}
		}
		LOADING.add(name);
		Minecraft client = Minecraft.getInstance();
		http.sendAsync(HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
				.whenComplete((response, error) -> client.execute(() -> {
					LOADING.remove(name);
					if (!version.equals(versions.get(name))) {
						return;
					}
					if (error != null || response.statusCode() != 200 || response.body() == null) {
						FAILED.add(name);
						Mccinfo.LOGGER.warn("Could not get settings image {}", name, error);
						return;
					}
					install(name, version, response.body());
				}));
	}

	// Picture registered as a texture
	private static void install(String name, String version, byte[] data) {
		try {
			NativeImage pixels = NativeImage.read(data);
			Identifier location = Mccinfo.id("settings_info/" + name.replaceAll("[^a-z0-9_.-]", "_"));
			int width = pixels.getWidth();
			int height = pixels.getHeight();
			Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(() -> "Settings info " + name, pixels));
			IMAGES.put(name, new Image(location, width, height, version));
			revision++;
		} catch (IOException | RuntimeException e) {
			FAILED.add(name);
			Mccinfo.LOGGER.warn("Unreadable settings image {}", name, e);
		}
	}
}
