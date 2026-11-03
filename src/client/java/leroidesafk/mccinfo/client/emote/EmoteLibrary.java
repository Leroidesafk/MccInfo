package leroidesafk.mccinfo.client.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class EmoteLibrary {
	// File listed by the relay
	record RemoteFile(String name, long size, String sha256) {
	}

	// Loaded emote
	static final class Emote {
		final String id;
		final EmoteModel model;
		final EmoteAnimation intro;
		final EmoteAnimation loop;
		final Identifier texture;
		final Map<String, EmoteParticle> particles;
		final Set<String> sounds;
		final Map<String, byte[]> soundData;
		final List<Identifier> textures;
		final String music;
		final Set<EmoteModel.Bone> glowing;
		final Set<EmoteModel.Bone> billboard;
		final List<EmoteModel.Bone> trails;
		final Map<String, Double> variables;

		Emote(String id, EmoteModel model, EmoteAnimation intro, EmoteAnimation loop, Identifier texture, Map<String, EmoteParticle> particles,
				Map<String, byte[]> soundData, List<Identifier> textures, boolean glowAll, Set<String> glowBones, Set<String> billboardBones,
				List<String> trailBones, Map<String, Double> variables) {
			this.variables = variables;
			Set<String> sounds = soundData.keySet();
			this.soundData = soundData;
			this.glowing = matching(model, glowAll, glowBones);
			this.billboard = matching(model, false, billboardBones);
			this.trails = trailBones.stream().map(model.byName::get).filter(java.util.Objects::nonNull).toList();
			this.music = sounds.stream().filter(sound -> sound.equals("music") || sound.endsWith(".music")).sorted().findFirst().orElse(null);
			this.id = id;
			this.model = model;
			this.intro = intro;
			this.loop = loop;
			this.texture = texture;
			this.particles = particles;
			this.sounds = sounds;
			this.textures = textures;
		}

		Molang.Scope fill(Molang.Scope scope) {
			this.variables.forEach(scope.variables::putIfAbsent);
			return scope;
		}
	}

	// Files read, before the textures
	private record Parsed(EmoteModel model, EmoteAnimation intro, EmoteAnimation loop, String modelTexture, Map<String, JsonObject> particles,
			Map<String, byte[]> sounds, Map<String, byte[]> images, boolean glowAll, Set<String> glowBones, Set<String> billboardBones,
			List<String> trailBones, Map<String, Double> variables) {
	}

	// Bones named in emote.json, with their children
	private static Set<EmoteModel.Bone> matching(EmoteModel model, boolean all, Set<String> names) {
		Set<EmoteModel.Bone> found = new HashSet<>();
		for (EmoteModel.Bone bone : model.bones) {
			for (EmoteModel.Bone current = bone; current != null; current = current.parent) {
				if (all || names.contains(current.name)) {
					found.add(bone);
					break;
				}
			}
		}
		return Set.copyOf(found);
	}

	private static Set<String> names(JsonElement element) {
		Set<String> names = new HashSet<>();
		if (element != null && element.isJsonArray()) {
			element.getAsJsonArray().forEach(bone -> names.add(bone.getAsString().toLowerCase(Locale.ROOT)));
		}
		return names;
	}

	// Allowed names and limits
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
	private static final Pattern FILE = Pattern.compile("[a-z0-9_.-]{1,64}\\.(json|png|ogg)");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final Map<String, String> SIGNATURES = new ConcurrentHashMap<>();
	private static final Map<String, Emote> LOADED = new ConcurrentHashMap<>();
	private static volatile String key;
	private static volatile boolean cleaned;
	private static HttpClient http;

	private EmoteLibrary() {
	}

	static Emote get(String id) {
		return id == null ? null : LOADED.get(id);
	}

	// Old disk cache
	private static void removeOldCache() {
		Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve(Mccinfo.MOD_ID).resolve("emotes");
		if (!Files.isDirectory(folder)) {
			return;
		}
		try (var stream = Files.walk(folder)) {
			for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		} catch (IOException | RuntimeException e) {
			Mccinfo.LOGGER.warn("Could not remove the old emote cache", e);
		}
	}

	// Emote list from the relay
	static void receive(JsonObject object) {
		if (!cleaned) {
			cleaned = true;
			Util.nonCriticalIoPool().execute(EmoteLibrary::removeOldCache);
		}
		if (object.has("key") && object.get("key").isJsonPrimitive()) {
			key = object.get("key").getAsString();
		}
		if (!object.has("emotes") || !object.get("emotes").isJsonArray()) {
			return;
		}
		Set<String> seen = new HashSet<>();
		for (JsonElement element : object.getAsJsonArray("emotes")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			String id = entry.has("id") ? entry.get("id").getAsString() : "";
			if (!ID.matcher(id).matches() || !entry.has("files") || !entry.get("files").isJsonArray()) {
				continue;
			}
			List<RemoteFile> files = new ArrayList<>();
			for (JsonElement fileElement : entry.getAsJsonArray("files")) {
				if (!fileElement.isJsonObject()) {
					continue;
				}
				JsonObject file = fileElement.getAsJsonObject();
				String name = file.has("name") ? file.get("name").getAsString() : "";
				String sha = file.has("sha256") ? file.get("sha256").getAsString().toLowerCase(Locale.ROOT) : "";
				long size = file.has("size") ? file.get("size").getAsLong() : -1;
				if (FILE.matcher(name).matches() && SHA256.matcher(sha).matches() && size >= 0 && size <= MAX_FILE_BYTES) {
					files.add(new RemoteFile(name, size, sha));
				}
			}
			seen.add(id);
			String signature = files.toString();
			Mccinfo.LOGGER.info("Emote {} from the relay: {} file(s) {}", id, files.size(), files.stream().map(RemoteFile::name).toList());
			if (signature.equals(SIGNATURES.get(id))) {
				continue;
			}
			SIGNATURES.put(id, signature);
			Util.nonCriticalIoPool().execute(() -> fetch(id, List.copyOf(files), signature));
		}
		for (String id : List.copyOf(SIGNATURES.keySet())) {
			if (!seen.contains(id)) {
				SIGNATURES.remove(id);
				Minecraft.getInstance().execute(() -> unload(id));
			}
		}
	}

	// Download, then load
	private static void fetch(String id, List<RemoteFile> files, String signature) {
		try {
			Map<String, byte[]> data = new HashMap<>();
			for (RemoteFile file : files) {
				data.put(file.name(), download(id, file));
			}
			Parsed parsed = parse(id, data, files);
			Minecraft.getInstance().execute(() -> {
				if (signature.equals(SIGNATURES.get(id))) {
					install(id, parsed);
				}
			});
		} catch (IOException | RuntimeException e) {
			Mccinfo.LOGGER.warn("Could not get emote {}", id, e);
			SIGNATURES.remove(id, signature);
		}
	}

	// One checked file
	private static byte[] download(String id, RemoteFile file) throws IOException {
		URI base = SpotSync.httpUri("/emotes/" + id + "/" + file.name());
		String currentKey = key;
		if (base == null || currentKey == null) {
			throw new IOException("No relay address or download key");
		}
		URI uri;
		try {
			String query = (base.getRawQuery() == null ? "" : base.getRawQuery() + "&") + "key=" + currentKey;
			uri = new URI(base.getScheme() + "://" + base.getRawAuthority() + base.getRawPath() + "?" + query);
		} catch (java.net.URISyntaxException e) {
			throw new IOException(e);
		}
		synchronized (EmoteLibrary.class) {
			if (http == null) {
				http = SpotSync.relayHttpClient().connectTimeout(TIMEOUT).build();
			}
		}
		HttpResponse<byte[]> response;
		try {
			response = http.send(HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
		byte[] data = response.body();
		if (response.statusCode() != 200 || data == null || data.length != file.size() || !file.sha256().equals(sha256(data))) {
			throw new IOException("Bad download for " + file.name() + " (HTTP " + response.statusCode() + ")");
		}
		return data;
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// Model, animations, particles and sounds
	private static Parsed parse(String id, Map<String, byte[]> data, List<RemoteFile> files) {
		EmoteModel model = EmoteModel.EMPTY;
		EmoteAnimation intro = null;
		EmoteAnimation loop = null;
		EmoteAnimation single = null;
		Map<String, JsonObject> particles = new HashMap<>();
		Map<String, byte[]> sounds = new HashMap<>();
		Map<String, byte[]> images = new HashMap<>();
		boolean glowAll = false;
		Set<String> glowBones = new HashSet<>();
		Set<String> billboardBones = new HashSet<>();
		List<String> trailBones = new ArrayList<>();
		Map<String, Double> variables = new HashMap<>();
		for (RemoteFile file : files) {
			String name = file.name();
			byte[] bytes = data.get(name);
			if (bytes == null) {
				continue;
			}
			if (name.endsWith(".png")) {
				images.put(base(name), bytes);
				continue;
			}
			if (name.endsWith(".ogg")) {
				sounds.put(base(name), bytes);
				continue;
			}
			JsonObject root;
			try {
				root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (RuntimeException e) {
				Mccinfo.LOGGER.warn("Unreadable emote file {}/{}", id, name, e);
				continue;
			}
			if (name.equals("emote.json")) {
				JsonElement glow = root.get("glow");
				if (glow != null && glow.isJsonPrimitive()) {
					glowAll = glow.getAsBoolean();
				} else {
					glowBones.addAll(names(glow));
				}
				billboardBones.addAll(names(root.get("billboard")));
				trailBones.addAll(names(root.get("trails")).stream().sorted().toList());
				if (root.has("variables") && root.get("variables").isJsonObject()) {
					for (Map.Entry<String, JsonElement> variable : root.getAsJsonObject("variables").entrySet()) {
						if (variable.getValue().isJsonPrimitive() && variable.getValue().getAsJsonPrimitive().isNumber()) {
							variables.put(variable.getKey().trim().toLowerCase(Locale.ROOT), variable.getValue().getAsDouble());
						}
					}
				}
			} else if (root.has("minecraft:geometry")) {
				model = EmoteModel.parse(root);
			} else if (root.has("animations")) {
				EmoteAnimation animation = EmoteAnimation.parse(root);
				if (name.contains(".intro.")) {
					intro = animation;
				} else if (name.contains(".loop.")) {
					loop = animation;
				} else {
					single = animation;
				}
			} else if (root.has("particle_effect")) {
				particles.put(base(name), root);
			}
		}
		if (intro == null && loop == null && single != null) {
			if (single.loop == EmoteAnimation.Loop.LOOP) {
				loop = single;
			} else {
				intro = single;
			}
		}
		String modelTexture = null;
		for (String image : images.keySet().stream().sorted().toList()) {
			if (!particles.containsKey(image)) {
				modelTexture = image;
				break;
			}
		}
		return new Parsed(model, intro, loop, modelTexture, particles, sounds, images, glowAll, Set.copyOf(glowBones), Set.copyOf(billboardBones),
				List.copyOf(trailBones), Map.copyOf(variables));
	}

	private static String base(String name) {
		String withoutExtension = name.substring(0, name.lastIndexOf('.'));
		return withoutExtension.endsWith(".geo") ? withoutExtension.substring(0, withoutExtension.length() - 4) : withoutExtension;
	}

	// Textures and particles
	private static void install(String id, Parsed parsed) {
		unload(id);
		Map<String, Identifier> textures = new HashMap<>();
		for (Map.Entry<String, byte[]> image : parsed.images().entrySet()) {
			try {
				NativeImage pixels = NativeImage.read(image.getValue());
				Identifier location = Mccinfo.id("emote/" + id + "/" + image.getKey().replaceAll("[^a-z0-9_.-]", "_"));
				Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(() -> "Emote " + id + " " + image.getKey(), pixels));
				textures.put(image.getKey(), location);
			} catch (IOException | RuntimeException e) {
				Mccinfo.LOGGER.warn("Unreadable emote image {}/{}.png", id, image.getKey(), e);
			}
		}
		Map<String, EmoteParticle> particles = new HashMap<>();
		for (Map.Entry<String, JsonObject> particle : parsed.particles().entrySet()) {
			Identifier texture = textures.get(particle.getKey());
			if (texture == null) {
				continue;
			}
			EmoteParticle definition = EmoteParticle.parse(particle.getValue(), texture);
			if (definition != null) {
				particles.put(particle.getKey(), definition);
			}
		}
		Identifier modelTexture = parsed.modelTexture() == null ? null : textures.get(parsed.modelTexture());
		LOADED.put(id, new Emote(id, parsed.model(), parsed.intro(), parsed.loop(), modelTexture, Map.copyOf(particles), Map.copyOf(parsed.sounds()),
				List.copyOf(textures.values()), parsed.glowAll(), parsed.glowBones(), parsed.billboardBones(),
				parsed.trailBones(), parsed.variables()));
	}

	private static void unload(String id) {
		Emote old = LOADED.remove(id);
		if (old != null) {
			old.textures.forEach(Minecraft.getInstance().getTextureManager()::release);
		}
	}

	// Sound for the stream
	static byte[] soundData(String id, String sound) {
		Emote emote = LOADED.get(id);
		return emote == null ? null : emote.soundData.get(sound);
	}
}
