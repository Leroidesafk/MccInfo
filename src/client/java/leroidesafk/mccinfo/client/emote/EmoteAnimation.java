package leroidesafk.mccinfo.client.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EmoteAnimation {
	// Play once, hold the last frame or loop
	enum Loop {
		ONCE,
		HOLD,
		LOOP
	}

	// Sound or particle at a time
	record Effect(double time, String effect, String locator) {
	}

	// Keyframe with its values before and after
	private record Key(double time, Molang.Expr[] pre, Molang.Expr[] post, String lerp) {
	}

	// Rotation, position or scale over time
	private static final class Channel {
		private final List<Key> keys;
		private final Molang.Expr[] constant;

		private Channel(List<Key> keys, Molang.Expr[] constant) {
			this.keys = keys;
			this.constant = constant;
		}

		private void sample(double time, Molang.Scope scope, float[] out, int offset) {
			if (this.constant != null) {
				write(this.constant, scope, out, offset);
				return;
			}
			if (this.keys.isEmpty()) {
				return;
			}
			Key first = this.keys.getFirst();
			if (time <= first.time()) {
				write(first.pre(), scope, out, offset);
				return;
			}
			Key last = this.keys.getLast();
			if (time >= last.time()) {
				write(last.post(), scope, out, offset);
				return;
			}
			int index = 0;
			while (index + 1 < this.keys.size() && this.keys.get(index + 1).time() <= time) {
				index++;
			}
			Key a = this.keys.get(index);
			Key b = this.keys.get(index + 1);
			if ("step".equals(a.lerp())) {
				write(a.post(), scope, out, offset);
				return;
			}
			double progress = (time - a.time()) / Math.max(1.0E-6, b.time() - a.time());
			// Smooth curve between keyframes
			if ("catmullrom".equals(a.lerp()) || "catmullrom".equals(b.lerp())) {
				Key before = index > 0 ? this.keys.get(index - 1) : a;
				Key after = index + 2 < this.keys.size() ? this.keys.get(index + 2) : b;
				for (int i = 0; i < 3; i++) {
					double p0 = (index > 0 ? before.post() : a.post())[i].eval(scope);
					double p1 = a.post()[i].eval(scope);
					double p2 = b.pre()[i].eval(scope);
					double p3 = (index + 2 < this.keys.size() ? after.pre() : b.pre())[i].eval(scope);
					out[offset + i] = (float) catmullRom(p0, p1, p2, p3, progress);
				}
				return;
			}
			for (int i = 0; i < 3; i++) {
				double from = a.post()[i].eval(scope);
				double to = b.pre()[i].eval(scope);
				out[offset + i] = (float) (from + (to - from) * progress);
			}
		}

		private static void write(Molang.Expr[] values, Molang.Scope scope, float[] out, int offset) {
			for (int i = 0; i < 3; i++) {
				out[offset + i] = (float) values[i].eval(scope);
			}
		}

		private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
			double t2 = t * t;
			double t3 = t2 * t;
			return 0.5 * (2.0 * p1 + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
		}
	}

	private record BoneChannels(Channel rotation, Channel position, Channel scale) {
	}

	final double length;
	final Loop loop;
	final List<Effect> sounds;
	final List<Effect> particles;
	private final Map<String, BoneChannels> bones;

	private EmoteAnimation(double length, Loop loop, Map<String, BoneChannels> bones, List<Effect> sounds, List<Effect> particles) {
		this.length = length;
		this.loop = loop;
		this.bones = bones;
		this.sounds = sounds;
		this.particles = particles;
	}

	// First animation of the file
	static EmoteAnimation parse(JsonObject root) {
		JsonObject animations = root.has("animations") && root.get("animations").isJsonObject() ? root.getAsJsonObject("animations") : null;
		if (animations == null || animations.isEmpty()) {
			return null;
		}
		JsonElement first = animations.entrySet().iterator().next().getValue();
		if (!first.isJsonObject()) {
			return null;
		}
		JsonObject animation = first.getAsJsonObject();
		Loop loop = Loop.ONCE;
		if (animation.has("loop") && animation.get("loop").isJsonPrimitive()) {
			JsonElement value = animation.get("loop");
			if (value.getAsJsonPrimitive().isBoolean()) {
				loop = value.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
			} else if ("hold_on_last_frame".equals(value.getAsString())) {
				loop = Loop.HOLD;
			} else if ("true".equals(value.getAsString())) {
				loop = Loop.LOOP;
			}
		}
		Map<String, BoneChannels> bones = new HashMap<>();
		double last = 0.0;
		if (animation.has("bones") && animation.get("bones").isJsonObject()) {
			for (Map.Entry<String, JsonElement> bone : animation.getAsJsonObject("bones").entrySet()) {
				if (!bone.getValue().isJsonObject()) {
					continue;
				}
				JsonObject channels = bone.getValue().getAsJsonObject();
				Channel rotation = channel(channels.get("rotation"), 0.0);
				Channel position = channel(channels.get("position"), 0.0);
				Channel scale = channel(channels.get("scale"), 1.0);
				for (Channel channel : new Channel[] {rotation, position, scale}) {
					if (channel != null && !channel.keys.isEmpty()) {
						last = Math.max(last, channel.keys.getLast().time());
					}
				}
				bones.put(bone.getKey().toLowerCase(Locale.ROOT), new BoneChannels(rotation, position, scale));
			}
		}
		List<Effect> sounds = effects(animation.get("sound_effects"));
		List<Effect> particles = effects(animation.get("particle_effects"));
		double length = animation.has("animation_length") ? animation.get("animation_length").getAsDouble() : last;
		return new EmoteAnimation(Math.max(length, 0.0), loop, Map.copyOf(bones), sounds, particles);
	}

	// Keyframes or a fixed value
	private static Channel channel(JsonElement element, double fallback) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonObject() && !element.getAsJsonObject().has("pre") && !element.getAsJsonObject().has("post")) {
			List<Key> keys = new ArrayList<>();
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				double time;
				try {
					time = Double.parseDouble(entry.getKey());
				} catch (NumberFormatException e) {
					continue;
				}
				JsonElement value = entry.getValue();
				if (value.isJsonObject()) {
					JsonObject object = value.getAsJsonObject();
					Molang.Expr[] post = triple(object.has("post") ? object.get("post") : object.get("pre"), fallback);
					Molang.Expr[] pre = object.has("pre") ? triple(object.get("pre"), fallback) : post;
					String lerp = object.has("lerp_mode") ? object.get("lerp_mode").getAsString().toLowerCase(Locale.ROOT) : "linear";
					keys.add(new Key(time, pre, post, lerp));
				} else {
					Molang.Expr[] values = triple(value, fallback);
					keys.add(new Key(time, values, values, "linear"));
				}
			}
			keys.sort(Comparator.comparingDouble(Key::time));
			return new Channel(List.copyOf(keys), null);
		}
		return new Channel(List.of(), triple(element, fallback));
	}

	private static Molang.Expr[] triple(JsonElement element, double fallback) {
		if (element != null && element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			Molang.Expr[] values = new Molang.Expr[3];
			for (int i = 0; i < 3; i++) {
				values[i] = i < array.size() ? Molang.of(array.get(i), fallback) : Molang.of(null, fallback);
			}
			return values;
		}
		Molang.Expr single = Molang.of(element, fallback);
		return new Molang.Expr[] {single, single, single};
	}

	// Sound and particle keyframes
	private static List<Effect> effects(JsonElement element) {
		List<Effect> list = new ArrayList<>();
		if (element == null || !element.isJsonObject()) {
			return list;
		}
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			double time;
			try {
				time = Double.parseDouble(entry.getKey());
			} catch (NumberFormatException e) {
				continue;
			}
			JsonElement value = entry.getValue();
			List<JsonElement> items = new ArrayList<>();
			if (value.isJsonArray()) {
				value.getAsJsonArray().forEach(items::add);
			} else {
				items.add(value);
			}
			for (JsonElement item : items) {
				if (!item.isJsonObject() || !item.getAsJsonObject().has("effect")) {
					continue;
				}
				JsonObject object = item.getAsJsonObject();
				String locator = object.has("locator") && object.get("locator").isJsonPrimitive() ? object.get("locator").getAsString() : null;
				list.add(new Effect(time, object.get("effect").getAsString().toLowerCase(Locale.ROOT), locator));
			}
		}
		list.sort(Comparator.comparingDouble(Effect::time));
		return List.copyOf(list);
	}

	// Bone values at a time
	void sample(double time, Molang.Scope scope, Map<String, float[]> out) {
		scope.animTime = time;
		for (Map.Entry<String, BoneChannels> entry : this.bones.entrySet()) {
			float[] values = out.computeIfAbsent(entry.getKey(), key -> new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1});
			BoneChannels channels = entry.getValue();
			if (channels.rotation() != null) {
				channels.rotation().sample(time, scope, values, 0);
			}
			if (channels.position() != null) {
				channels.position().sample(time, scope, values, 3);
			}
			if (channels.scale() != null) {
				channels.scale().sample(time, scope, values, 6);
			}
		}
	}
}
