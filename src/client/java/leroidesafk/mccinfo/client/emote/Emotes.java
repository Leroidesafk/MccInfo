package leroidesafk.mccinfo.client.emote;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Emotes {
	// Tick, model and player sizes
	private static final double TICK_SECONDS = 0.05;
	private static final float MODEL_SCALE = 1.0F / 16.0F;
	private static final float MODEL_TURN = 180.0F;
	private static final float PLAYER_SCALE = 0.9375F;

	// Sound ranges and pose blend
	private static final double SOUND_HEIGHT = 1.0;
	private static final double FULL_VOLUME_RANGE = 4.0;
	private static final double HEARING_RANGE = 24.0;
	private static final double LINK_RANGE = 24.0;
	private static final double UNLINK_RANGE = 28.0;
	private static final float FADE_STEP = 1.0F / 30.0F;
	private static final int MUSIC_RETRY_TICKS = 20;
	private static final double START_DELAY = 0.3;
	private static final long BLEND_NANOS = 300_000_000L;

	// Emote played by a player
	private static final class Playing {
		final UUID uuid;
		final String emote;
		final long startNanos;
		final long createdNanos = System.nanoTime();
		PoseSource pose;
		final boolean local;
		final Molang.Scope scope = new Molang.Scope();
		final List<EmoteParticle.Emitter> emitters = new ArrayList<>();
		final Map<EmoteParticle.Emitter, String> locators = new ConcurrentHashMap<>();
		final Map<EmoteModel.Bone, EmoteTrail> trails = new java.util.IdentityHashMap<>();
		double lastTime;
		boolean armed;
		EmoteSound music;
		int musicRetry;
		boolean present;
		Vec3 at = Vec3.ZERO;
		float near;
		float loudness;
		float gain;
		boolean fresh = true;
		boolean linked;
		boolean centered;
		Playing follows;
		volatile boolean stopped;
		volatile boolean finished;

		Playing(UUID uuid, String emote, double elapsed, boolean local) {
			this.uuid = uuid;
			this.emote = emote;
			this.startNanos = System.nanoTime() - (long) (elapsed * 1.0E9);
			this.local = local;
			this.lastTime = Math.max(0.0, elapsed - START_DELAY) - 1.0E-4;
		}

		double time() {
			return Math.max(0.0, (System.nanoTime() - this.startNanos) / 1.0E9 - START_DELAY);
		}

		boolean waiting() {
			return (System.nanoTime() - this.startNanos) / 1.0E9 < START_DELAY;
		}

		float blendIn() {
			return ease(System.nanoTime() - this.createdNanos);
		}
	}

	// Pose fading out
	private record Fade(PoseSource pose, float strength, long nanos) {
		float weight() {
			return this.strength * (1.0F - ease(System.nanoTime() - this.nanos));
		}

		boolean done() {
			return System.nanoTime() - this.nanos >= BLEND_NANOS;
		}
	}

	private static float ease(long nanos) {
		float progress = Mth.clamp(nanos / (float) BLEND_NANOS, 0.0F, 1.0F);
		return progress * progress * (3.0F - 2.0F * progress);
	}

	private static final Map<UUID, Fade> FADING = new ConcurrentHashMap<>();

	// Emotes by player and the current world
	private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();
	private static final List<EmoteMirage> MIRAGES = new ArrayList<>();
	private static final List<EmoteShockwave> WAVES = new ArrayList<>();
	// Particles of finished emotes
	private static final List<EmoteParticle.Emitter> LINGERING = new ArrayList<>();
	private static final double END_MARGIN = 0.25;
	private static ClientLevel level;

	private Emotes() {
	}

	// Relay messages, tick, world render and wheel preview
	public static void register() {
		SpotSync.addHandler("emotes", EmoteLibrary::receive);
		SpotSync.addHandler("emote_state", object -> Minecraft.getInstance().execute(() -> receiveState(object)));
		ClientTickEvents.END_CLIENT_TICK.register(Emotes::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Emotes::collectSubmits);
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> EmoteShockwave.copyScene());
		net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(
				context -> new EmotePreviewRenderer(context.minecraft().getEntityRenderDispatcher()));
	}

	// Own emote, checked by the relay
	public static void play(String id) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "emote_play");
		message.addProperty("widget", id);
		if (!SpotSync.sendMessage(message)) {
			return;
		}
		start(client.player.getUUID(), id, 0.0, true);
	}

	public static void stopLocal() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "emote_stop");
		SpotSync.sendMessage(message);
		stop(client.player.getUUID());
	}

	public static boolean isPlaying() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && PLAYING.containsKey(client.player.getUUID());
	}

	// Emote started or stopped by a player
	private static void receiveState(JsonObject object) {
		UUID uuid = uuid(object.has("uuid") ? object.get("uuid").getAsString() : null);
		if (uuid == null) {
			return;
		}
		String emote = object.has("emote") && object.get("emote").isJsonPrimitive() ? object.get("emote").getAsString() : null;
		Minecraft client = Minecraft.getInstance();
		boolean local = client.player != null && client.player.getUUID().equals(uuid);
		if (emote == null) {
			stop(uuid);
			return;
		}
		Playing current = PLAYING.get(uuid);
		if (current != null && current.emote.equals(emote) && local) {
			return;
		}
		double elapsed = object.has("elapsed") ? Math.max(0.0, object.get("elapsed").getAsDouble() / 1000.0) : 0.0;
		start(uuid, emote, elapsed, local);
	}

	private static UUID uuid(String text) {
		if (text == null) {
			return null;
		}
		String plain = text.replace("-", "");
		if (plain.length() != 32) {
			return null;
		}
		try {
			return UUID.fromString(plain.substring(0, 8) + "-" + plain.substring(8, 12) + "-" + plain.substring(12, 16) + "-" + plain.substring(16, 20)
					+ "-" + plain.substring(20));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// Start and stop
	private static void start(UUID uuid, String emote, double elapsed, boolean local) {
		stop(uuid);
		PLAYING.put(uuid, new Playing(uuid, emote, elapsed, local));
	}

	private static void stop(UUID uuid) {
		Playing old = PLAYING.remove(uuid);
		if (old != null) {
			// Natural end: sounds and particles keep going
			EmoteLibrary.Emote emote = EmoteLibrary.get(old.emote);
			if (emote != null && EmoteRender.phase(emote, old.time() + END_MARGIN) == null) {
				old.finished = true;
				LINGERING.addAll(old.emitters);
			}
			old.stopped = true;
			if (old.pose != null) {
				FADING.put(uuid, new Fade(old.pose, old.blendIn(), System.nanoTime()));
			}
		}
	}

	// Click to stop, music, sounds and particles
	private static void tick(Minecraft client) {
		if (client.level != level) {
			level = client.level;
			PLAYING.values().forEach(playing -> playing.stopped = true);
			PLAYING.clear();
			MIRAGES.clear();
			WAVES.clear();
			LINGERING.clear();
			FADING.clear();
		}
		MIRAGES.removeIf(EmoteMirage::done);
		WAVES.removeIf(EmoteShockwave::done);
		FADING.values().removeIf(Fade::done);
		if (level == null) {
			return;
		}
		// Finished emotes particles
		for (EmoteParticle.Emitter emitter : List.copyOf(LINGERING)) {
			emitter.tick(TICK_SECONDS);
			if (emitter.done()) {
				LINGERING.remove(emitter);
			}
		}
		mix(client);
		for (Playing playing : List.copyOf(PLAYING.values())) {
			EmoteLibrary.Emote emote = EmoteLibrary.get(playing.emote);
			if (emote == null) {
				continue;
			}
			if (playing.local) {
				boolean down = client.options.keyAttack.isDown() || client.options.keyUse.isDown();
				if (client.gui.screen() != null) {
					playing.armed = false;
				} else if (!down) {
					playing.armed = true;
				} else if (playing.armed) {
					stopLocal();
					continue;
				}
			}
			double time = playing.time();
			EmoteRender.Phase phase = EmoteRender.phase(emote, time);
			if (phase == null) {
				if (playing.local) {
					stopLocal();
				} else {
					stop(playing.uuid);
				}
				continue;
			}
			Player player = level.getPlayerByUUID(playing.uuid);
			if (player == null) {
				playing.lastTime = time;
				playing.emitters.clear();
				continue;
			}
			if (playing.waiting()) {
				continue;
			}
			boolean sounds = ModConfig.get().emoteSounds;
			if (playing.musicRetry > 0) {
				playing.musicRetry--;
			}
			boolean moved = playing.music != null && !playing.music.isStopped() && playing.music.centered() != playing.centered;
			if (moved) {
				client.getSoundManager().stop(playing.music);
				playing.musicRetry = 0;
			}
			if (sounds && emote.music != null && playing.musicRetry == 0
					&& (moved || playing.music == null || playing.music.isStopped() || !client.getSoundManager().isActive(playing.music))) {
				playing.musicRetry = MUSIC_RETRY_TICKS;
				playing.music = new EmoteSound(emote.id, emote.music, time, () -> volume(playing), () -> alive(playing), sourceOf(playing), true);
				client.getSoundManager().play(playing.music);
			}
			Matrix4f root = root(player.position(), player.getYHeadRot());
			Map<EmoteModel.Bone, Matrix4f> matrices = EmoteRender.pose(emote, phase, emote.fill(playing.scope));
			double from = playing.lastTime;
			EmoteRender.effectsBetween(emote, from, time, true, effect -> {
				if (sounds && emote.sounds.contains(effect.effect()) && !effect.effect().equals(emote.music)) {
					client.getSoundManager().play(new EmoteSound(emote.id, effect.effect(), 0.0, () -> volume(playing), () -> lingers(playing), sourceOf(playing), false));
				}
			});
			EmoteRender.effectsBetween(emote, from, time, false, effect -> {
				if (EmoteMirage.EFFECT.equals(effect.effect()) && !emote.particles.containsKey(EmoteMirage.EFFECT)) {
					MIRAGES.add(EmoteMirage.capture(player, emote, matrices));
					return;
				}
				if (EmoteShockwave.EFFECT.equals(effect.effect()) && !emote.particles.containsKey(EmoteShockwave.EFFECT)) {
					Vector3f center = locator(emote, matrices, root, effect.locator());
					WAVES.add(new EmoteShockwave(new Vec3(center.x, center.y, center.z)));
					return;
				}
				// Particle named with or without .particle
				EmoteParticle definition = emote.particles.getOrDefault(effect.effect(), emote.particles.get(effect.effect().replace(".particle", "")));
				if (definition != null) {
					EmoteParticle.Emitter emitter = definition.new Emitter();
					emitter.origin.set(locator(emote, matrices, root, effect.locator()));
					playing.emitters.add(emitter);
					if (effect.locator() != null) {
						playing.locators.put(emitter, effect.locator());
					}
				}
			});
			playing.lastTime = time;
			for (EmoteParticle.Emitter emitter : List.copyOf(playing.emitters)) {
				emitter.origin.set(locator(emote, matrices, root, playing.locators.get(emitter)));
				emitter.tick(TICK_SECONDS);
				if (emitter.done()) {
					playing.emitters.remove(emitter);
					playing.locators.remove(emitter);
				}
			}
		}
	}

	// One music for the same emote nearby
	private static void mix(Minecraft client) {
		Vec3 listener = client.gameRenderer.mainCamera().position();
		List<Playing> order = new ArrayList<>();
		for (Playing playing : PLAYING.values()) {
			Player player = EmoteLibrary.get(playing.emote) == null ? null : level.getPlayerByUUID(playing.uuid);
			playing.present = player != null;
			if (player != null) {
				playing.at = player.position();
				playing.near = hearing(playing.at.distanceTo(listener));
				order.add(playing);
			}
		}
		order.sort(Comparator.comparingLong((Playing playing) -> playing.startNanos).thenComparing(playing -> playing.uuid));
		List<Playing> leaders = new ArrayList<>();
		for (Playing playing : order) {
			Playing leader = null;
			for (Playing candidate : leaders) {
				double range = playing.linked && candidate == playing.follows ? UNLINK_RANGE : LINK_RANGE;
				if (candidate.emote.equals(playing.emote) && candidate.at.distanceTo(playing.at) < range) {
					leader = candidate;
					break;
				}
			}
			playing.linked = leader != null;
			if (leader != null) {
				playing.follows = leader;
			} else {
				leaders.add(playing);
			}
			playing.loudness = playing.near;
			float target = playing.linked ? 0.0F : 1.0F;
			if (playing.fresh) {
				playing.fresh = false;
				playing.gain = target;
			} else if (playing.gain < target) {
				playing.gain = Math.min(target, playing.gain + FADE_STEP);
			} else {
				playing.gain = Math.max(target, playing.gain - FADE_STEP);
			}
		}
		for (Playing playing : order) {
			Playing leader = playing.follows;
			if (leader == null) {
				continue;
			}
			if (!playing.linked && playing.gain >= 1.0F || PLAYING.get(leader.uuid) != leader || !leader.present) {
				playing.follows = null;
				continue;
			}
			leader.loudness = Math.max(leader.loudness, playing.near * (1.0F - playing.gain));
		}
		Playing self = client.player == null ? null : PLAYING.get(client.player.getUUID());
		Playing group = self == null || !self.present ? null : self.follows != null ? self.follows : self;
		for (Playing playing : PLAYING.values()) {
			playing.centered = group != null && (playing == group || playing.follows == group);
		}
	}

	// Volume and sound source
	private static float hearing(double distance) {
		return (float) Mth.clamp(1.0 - (distance - FULL_VOLUME_RANGE) / (HEARING_RANGE - FULL_VOLUME_RANGE), 0.0, 1.0);
	}

	private static double volume(Playing playing) {
		return playing.gain * playing.loudness;
	}

	private static java.util.function.Supplier<Vec3> sourceOf(Playing playing) {
		return playing.centered ? null : () -> source(playing);
	}

	private static Vec3 source(Playing playing) {
		return playing.at.add(0.0, SOUND_HEIGHT, 0.0);
	}

	private static boolean alive(Playing playing) {
		return !playing.stopped && playing.present && ModConfig.get().emoteSounds;
	}

	// Effect sounds outlive a natural end
	private static boolean lingers(Playing playing) {
		return (!playing.stopped || playing.finished) && playing.present && ModConfig.get().emoteSounds;
	}

	// Model space to world
	private static Matrix4f root(Vec3 position, float yaw) {
		return new Matrix4f().translate((float) position.x, (float) position.y, (float) position.z).rotateY((float) Math.toRadians(MODEL_TURN - yaw))
				.scale(MODEL_SCALE);
	}

	private static Vector3f locator(EmoteLibrary.Emote emote, Map<EmoteModel.Bone, Matrix4f> matrices, Matrix4f root, String name) {
		return root.transformPosition(emote.model.locator(name, matrices));
	}

	// Player poses for the previews
	private record PoseSource(EmoteLibrary.Emote emote, Map<EmoteModel.Bone, Matrix4f> matrices) {
	}

	private static final Map<Object, PoseSource> PREVIEW_POSES = Collections.synchronizedMap(new WeakHashMap<>());

	static void previewPose(Object state, EmoteLibrary.Emote emote, Map<EmoteModel.Bone, Matrix4f> matrices) {
		PREVIEW_POSES.put(state, new PoseSource(emote, matrices));
	}

	// Emote pose on a rendered player
	public static void applyPose(PlayerModel model, AvatarRenderState state) {
		PoseSource preview = PREVIEW_POSES.get(state);
		if (preview != null) {
			EmotePlayerPose.apply(model, preview.emote().model, preview.matrices());
			return;
		}
		if (PLAYING.isEmpty() && FADING.isEmpty() || level == null || !(level.getEntity(state.id) instanceof Player player)) {
			return;
		}
		Playing playing = PLAYING.get(player.getUUID());
		EmoteLibrary.Emote emote = playing == null ? null : EmoteLibrary.get(playing.emote);
		EmoteRender.Phase phase = emote == null ? null : EmoteRender.phase(emote, playing.time());
		PoseSource source = null;
		if (phase != null) {
			source = new PoseSource(emote, EmoteRender.pose(emote, phase, emote.fill(playing.scope)));
			playing.pose = source;
		} else if (playing != null) {
			// Last pose until the stop
			source = playing.pose;
		}
		Fade fade = FADING.get(player.getUUID());
		if (source == null && fade == null) {
			return;
		}
		float[][] base = EmotePlayerPose.capture(model);
		if (fade != null) {
			EmotePlayerPose.apply(model, fade.pose().emote().model, fade.pose().matrices());
			EmotePlayerPose.blend(model, base, fade.weight());
			if (source != null) {
				base = EmotePlayerPose.capture(model);
			}
		}
		if (source != null) {
			EmotePlayerPose.apply(model, source.emote().model, source.matrices());
			EmotePlayerPose.blend(model, base, playing.blendIn());
		}
	}

	// Cloaks riding an emoting player (It's currently the neatest and prettiest way I've found)
	private static final Map<Object, Matrix4f> PASSENGER_MOTION = Collections.synchronizedMap(new WeakHashMap<>());

	private static final Map<Object, Boolean> HIDDEN_PASSENGERS = Collections.synchronizedMap(new WeakHashMap<>());

	public static boolean hiddenPassenger(Object state) {
		return HIDDEN_PASSENGERS.containsKey(state);
	}

	static void forgetPassenger(Object state) {
		HIDDEN_PASSENGERS.remove(state);
		PASSENGER_MOTION.remove(state);
	}

	public static void extractPassenger(Entity entity, Object state, double x, double y, double z, float partial) {
		PASSENGER_MOTION.remove(state);
		HIDDEN_PASSENGERS.remove(state);
		Minecraft client = Minecraft.getInstance();
		if (ModConfig.get().hideFirstPersonBackpack && client.options.getCameraType().isFirstPerson() && entity != client.getCameraEntity()
				&& entity.getRootVehicle() == client.getCameraEntity()) {
			HIDDEN_PASSENGERS.put(state, Boolean.TRUE);
			return;
		}
		if (!(entity.getRootVehicle() instanceof Player player) || player == entity) {
			return;
		}
		Matrix4f body = bodyMotion(player);
		if (body == null) {
			return;
		}
		Vec3 position = player.getPosition(partial);
		float bodyRot = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		PASSENGER_MOTION.put(state, passengerMotion(body, (float) (position.x - x), (float) (position.y - y), (float) (position.z - z), bodyRot));
	}

	static Matrix4f passengerMotion(Matrix4f body, float x, float y, float z, float bodyRot) {
		Matrix4f model = new Matrix4f().translate(x, y, z)
				.rotateY((float) Math.toRadians(180.0F - bodyRot)).scale(-1.0F, -1.0F, 1.0F).scale(PLAYER_SCALE).translate(0.0F, -1.501F, 0.0F)
				.scale(MODEL_SCALE);
		return new Matrix4f(model).mul(body).mul(new Matrix4f(model).invert());
	}

	static Matrix4f bodyMotion(Player player) {
		if (PLAYING.isEmpty() && FADING.isEmpty()) {
			return null;
		}
		Matrix4f body = null;
		Fade fade = FADING.get(player.getUUID());
		if (fade != null) {
			Matrix4f motion = EmotePlayerPose.bodyMotion(fade.pose().emote().model, fade.pose().matrices());
			if (motion != null) {
				body = EmotePlayerPose.mix(new Matrix4f(), motion, fade.weight());
			}
		}
		Playing playing = PLAYING.get(player.getUUID());
		EmoteLibrary.Emote emote = playing == null ? null : EmoteLibrary.get(playing.emote);
		EmoteRender.Phase phase = emote == null ? null : EmoteRender.phase(emote, playing.time());
		// Last pose until the stop
		PoseSource last = playing == null ? null : playing.pose;
		Map<EmoteModel.Bone, Matrix4f> matrices = phase != null ? EmoteRender.pose(emote, phase, emote.fill(playing.scope)) : last == null ? null : last.matrices();
		if (matrices != null) {
			Matrix4f motion = EmotePlayerPose.bodyMotion(phase != null ? emote.model : last.emote().model, matrices);
			if (motion != null) {
				body = EmotePlayerPose.mix(body == null ? new Matrix4f() : body, motion, playing.blendIn());
			}
		}
		return body;
	}

	public static void applyPassenger(Object state, PoseStack poseStack) {
		Matrix4f motion = PASSENGER_MOTION.get(state);
		if (motion != null) {
			poseStack.mulPose(motion);
		}
	}

	// Models, mirages, waves, trails and particles
	private static void collectSubmits(LevelRenderContext context) {
		if (PLAYING.isEmpty() && MIRAGES.isEmpty() && WAVES.isEmpty() && LINGERING.isEmpty() || level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || !camera.initialized) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vector3f cameraPosition = new Vector3f((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);
		PoseStack poseStack = context.poseStack();
		long now = System.nanoTime();
		Entity viewer = client.options.getCameraType().isFirstPerson() ? client.getCameraEntity() : null;
		UUID firstPerson = viewer == null ? null : viewer.getUUID();
		for (EmoteMirage mirage : MIRAGES) {
			if (!mirage.owner().equals(firstPerson)) {
				mirage.submit(context.submitNodeCollector(), poseStack, camera);
			}
		}
		if (!WAVES.isEmpty() && EmoteShockwave.prepare()) {
			for (EmoteShockwave wave : WAVES) {
				wave.submit(context.submitNodeCollector(), poseStack, camera);
			}
		}
		for (Playing playing : PLAYING.values()) {
			EmoteLibrary.Emote emote = EmoteLibrary.get(playing.emote);
			Player player = emote == null ? null : level.getPlayerByUUID(playing.uuid);
			if (player == null) {
				continue;
			}
			boolean hidden = player.getUUID().equals(firstPerson);
			float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
			EmoteRender.Phase phase = EmoteRender.phase(emote, playing.time());
			if (phase != null) {
				Vec3 position = player.getPosition(partial);
				Map<EmoteModel.Bone, Matrix4f> matrices = EmoteRender.pose(emote, phase, emote.fill(playing.scope));
				if (!hidden) {
					poseStack.pushPose();
					poseStack.translate(position.x - camera.pos.x, position.y - camera.pos.y, position.z - camera.pos.z);
					poseStack.mulPose(Axis.YP.rotationDegrees(MODEL_TURN - yaw));
					poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
					int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(player.getEyePosition(partial)));
					EmoteRender.submitModel(context.submitNodeCollector(), poseStack, emote, matrices, light,
							new Quaternionf().rotateY((float) Math.toRadians(yaw - MODEL_TURN)).mul(camera.orientation).rotateY((float) Math.PI),
							EmotePlayerIcon.icon(player));
					poseStack.popPose();
				}
				if (!emote.trails.isEmpty()) {
					float bodyRot = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
					Matrix4f body = new Matrix4f().translate((float) position.x, (float) position.y, (float) position.z)
							.rotateY((float) Math.toRadians(MODEL_TURN - bodyRot)).scale(PLAYER_SCALE * MODEL_SCALE);
					double radians = Math.toRadians(bodyRot);
					Vec3 back = new Vec3(Math.sin(radians), 0.0, -Math.cos(radians));
					for (EmoteModel.Bone bone : emote.trails) {
						Matrix4f matrix = matrices.get(bone);
						if (matrix != null) {
							playing.trails.computeIfAbsent(bone, key -> new EmoteTrail()).add(bone, new Matrix4f(body).mul(matrix), back, now);
						}
					}
				}
			}
			if (!hidden) {
				for (EmoteTrail trail : playing.trails.values()) {
					trail.submit(context.submitNodeCollector(), poseStack, camera.pos, now);
				}
			}
			EmoteRender.submitParticles(context.submitNodeCollector(), poseStack, playing.emitters, cameraPosition,
					camera.orientation, partial);
		}
		if (!LINGERING.isEmpty()) {
			EmoteRender.submitParticles(context.submitNodeCollector(), poseStack, LINGERING, cameraPosition, camera.orientation, partial);
		}
	}
}
