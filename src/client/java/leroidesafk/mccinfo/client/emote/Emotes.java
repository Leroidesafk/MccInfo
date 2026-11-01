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

	// Emote played by a player
	private static final class Playing {
		final UUID uuid;
		final String emote;
		final long startNanos;
		final boolean local;
		final Molang.Scope scope = new Molang.Scope();
		final List<EmoteParticle.Emitter> emitters = new ArrayList<>();
		final Map<EmoteParticle.Emitter, String> locators = new ConcurrentHashMap<>();
		double lastTime;
		boolean armed;
		boolean musicStarted;
		volatile boolean stopped;

		Playing(UUID uuid, String emote, double elapsed, boolean local) {
			this.uuid = uuid;
			this.emote = emote;
			this.startNanos = System.nanoTime() - (long) (elapsed * 1.0E9);
			this.local = local;
			this.lastTime = elapsed - 1.0E-4;
		}

		double time() {
			return (System.nanoTime() - this.startNanos) / 1.0E9;
		}
	}

	// Emotes by player and the current world
	private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();
	private static ClientLevel level;

	private Emotes() {
	}

	// Relay messages, tick, world render and wheel preview
	public static void register() {
		SpotSync.addHandler("emotes", EmoteLibrary::receive);
		SpotSync.addHandler("emote_state", object -> Minecraft.getInstance().execute(() -> receiveState(object)));
		ClientTickEvents.END_CLIENT_TICK.register(Emotes::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Emotes::collectSubmits);
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

	private static void start(UUID uuid, String emote, double elapsed, boolean local) {
		stop(uuid);
		PLAYING.put(uuid, new Playing(uuid, emote, elapsed, local));
	}

	private static void stop(UUID uuid) {
		Playing old = PLAYING.remove(uuid);
		if (old != null) {
			old.stopped = true;
		}
	}

	// Click to stop, music, sounds and particles
	private static void tick(Minecraft client) {
		if (client.level != level) {
			level = client.level;
			PLAYING.values().forEach(playing -> playing.stopped = true);
			PLAYING.clear();
		}
		if (level == null) {
			return;
		}
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
			boolean sounds = ModConfig.get().emoteSounds;
			if (!sounds) {
				playing.musicStarted = false;
			} else if (!playing.musicStarted && emote.music != null) {
				playing.musicStarted = true;
				client.getSoundManager().play(new EmoteSound(emote.id, emote.music, () -> position(playing), () -> alive(playing), true));
			}
			Matrix4f root = root(player.position(), player.getYHeadRot());
			Map<EmoteModel.Bone, Matrix4f> matrices = EmoteRender.pose(emote, phase, playing.scope);
			double from = playing.lastTime;
			EmoteRender.effectsBetween(emote, from, time, true, effect -> {
				if (sounds && emote.sounds.contains(effect.effect())) {
					client.getSoundManager().play(new EmoteSound(emote.id, effect.effect(), () -> position(playing), () -> alive(playing), false));
				}
			});
			EmoteRender.effectsBetween(emote, from, time, false, effect -> {
				EmoteParticle definition = emote.particles.get(effect.effect());
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

	private static boolean alive(Playing playing) {
		return !playing.stopped && ModConfig.get().emoteSounds;
	}

	private static Vec3 position(Playing playing) {
		if (playing.stopped || level == null) {
			return null;
		}
		Player player = level.getPlayerByUUID(playing.uuid);
		return player == null ? null : player.position();
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
		PoseSource source = PREVIEW_POSES.get(state);
		if (source == null) {
			if (PLAYING.isEmpty() || level == null || !(level.getEntity(state.id) instanceof Player player)) {
				return;
			}
			Playing playing = PLAYING.get(player.getUUID());
			EmoteLibrary.Emote emote = playing == null ? null : EmoteLibrary.get(playing.emote);
			EmoteRender.Phase phase = emote == null ? null : EmoteRender.phase(emote, playing.time());
			if (phase == null) {
				return;
			}
			source = new PoseSource(emote, EmoteRender.pose(emote, phase, playing.scope));
		}
		EmotePlayerPose.apply(model, source.emote().model, source.matrices());
	}

	// Cloaks riding an emoting player (It's currently the neatest and prettiest way I've found)
	private static final Map<Object, Matrix4f> PASSENGER_MOTION = Collections.synchronizedMap(new WeakHashMap<>());

	public static void extractPassenger(Entity entity, Object state, double x, double y, double z, float partial) {
		PASSENGER_MOTION.remove(state);
		if (PLAYING.isEmpty() || !(entity.getRootVehicle() instanceof Player player) || player == entity) {
			return;
		}
		Playing playing = PLAYING.get(player.getUUID());
		EmoteLibrary.Emote emote = playing == null ? null : EmoteLibrary.get(playing.emote);
		EmoteRender.Phase phase = emote == null ? null : EmoteRender.phase(emote, playing.time());
		if (phase == null || !EmotePlayerPose.animatesPlayer(emote.model)) {
			return;
		}
		Matrix4f body = EmotePlayerPose.bodyMotion(emote.model, EmoteRender.pose(emote, phase, playing.scope));
		if (body == null) {
			return;
		}
		Vec3 position = player.getPosition(partial);
		float bodyRot = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Matrix4f model = new Matrix4f().translate((float) (position.x - x), (float) (position.y - y), (float) (position.z - z))
				.rotateY((float) Math.toRadians(180.0F - bodyRot)).scale(-1.0F, -1.0F, 1.0F).scale(PLAYER_SCALE).translate(0.0F, -1.501F, 0.0F)
				.scale(MODEL_SCALE);
		PASSENGER_MOTION.put(state, new Matrix4f(model).mul(body).mul(new Matrix4f(model).invert()));
	}

	public static void applyPassenger(Object state, PoseStack poseStack) {
		Matrix4f motion = PASSENGER_MOTION.get(state);
		if (motion != null) {
			poseStack.mulPose(motion);
		}
	}

	// Emote models and particles in the world
	private static void collectSubmits(LevelRenderContext context) {
		if (PLAYING.isEmpty() || level == null) {
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
		for (Playing playing : PLAYING.values()) {
			EmoteLibrary.Emote emote = EmoteLibrary.get(playing.emote);
			Player player = emote == null ? null : level.getPlayerByUUID(playing.uuid);
			if (player == null) {
				continue;
			}
			EmoteRender.Phase phase = EmoteRender.phase(emote, playing.time());
			if (phase != null) {
				Vec3 position = player.getPosition(partial);
				float yaw = player.getViewYRot(partial);
				poseStack.pushPose();
				poseStack.translate(position.x - camera.pos.x, position.y - camera.pos.y, position.z - camera.pos.z);
				poseStack.mulPose(Axis.YP.rotationDegrees(MODEL_TURN - yaw));
				poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
				int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(player.getEyePosition(partial)));
				EmoteRender.submitModel(context.submitNodeCollector(), poseStack, emote, EmoteRender.pose(emote, phase, playing.scope), light,
						new Quaternionf().rotateY((float) Math.toRadians(yaw - MODEL_TURN)).mul(camera.orientation).rotateY((float) Math.PI));
				poseStack.popPose();
			}
			EmoteRender.submitParticles(context.submitNodeCollector(), poseStack, playing.emitters, cameraPosition, camera.orientation, partial);
		}
	}
}
