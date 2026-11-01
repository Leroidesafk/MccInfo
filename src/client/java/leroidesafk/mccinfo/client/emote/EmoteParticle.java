package leroidesafk.mccinfo.client.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// Particles HAS TO BE MADE with https://snowstorm.app/
final class EmoteParticle {
	// Emitter shapes
	enum Shape {
		POINT,
		SPHERE,
		BOX,
		DISC
	}

	private static final int MAX_PARTICLES = 200;

	final Identifier texture;
	// Emitter rate and lifetime
	private final boolean instant;
	private final Molang.Expr amount;
	private final Molang.Expr rate;
	private final Molang.Expr maxParticles;
	private final boolean looping;
	private final Molang.Expr activeTime;
	private final Molang.Expr sleepTime;
	// Emitter shape and direction
	private final Shape shape;
	private final Molang.Expr[] offset;
	private final Molang.Expr radius;
	private final Molang.Expr[] halfDimensions;
	private final boolean surfaceOnly;
	private final String direction;
	private final Molang.Expr[] directionVector;
	final boolean localPosition;
	// Particle life and motion
	private final Molang.Expr lifetime;
	private final Molang.Expr speed;
	private final Molang.Expr[] speedVector;
	private final Molang.Expr[] acceleration;
	private final Molang.Expr drag;
	private final Molang.Expr initialRotation;
	private final Molang.Expr rotationRate;
	// Look: size, uv, flipbook and tint
	private final Molang.Expr[] size;
	final boolean yAxisOnly;
	private final float textureWidth;
	private final float textureHeight;
	private final Molang.Expr[] uv;
	private final Molang.Expr[] uvSize;
	private final boolean flipbook;
	private final Molang.Expr[] baseUv;
	private final float[] sizeUv;
	private final float[] stepUv;
	private final float framesPerSecond;
	private final Molang.Expr maxFrame;
	private final boolean stretch;
	private final boolean loopFrames;
	private final Molang.Expr[] tint;

	// Components of the particle file
	private EmoteParticle(Identifier texture, JsonObject components) {
		this.texture = texture;
		JsonObject instantRate = object(components, "minecraft:emitter_rate_instant");
		JsonObject steadyRate = object(components, "minecraft:emitter_rate_steady");
		this.instant = steadyRate == null;
		this.amount = Molang.of(instantRate == null ? null : instantRate.get("num_particles"), 10.0);
		this.rate = Molang.of(steadyRate == null ? null : steadyRate.get("spawn_rate"), 1.0);
		this.maxParticles = Molang.of(steadyRate == null ? null : steadyRate.get("max_particles"), 50.0);
		JsonObject once = object(components, "minecraft:emitter_lifetime_once");
		JsonObject looping = object(components, "minecraft:emitter_lifetime_looping");
		this.looping = looping != null;
		JsonObject lifetimeSource = looping != null ? looping : once;
		this.activeTime = Molang.of(lifetimeSource == null ? null : lifetimeSource.get("active_time"), 10.0);
		this.sleepTime = Molang.of(looping == null ? null : looping.get("sleep_time"), 0.0);
		JsonObject point = object(components, "minecraft:emitter_shape_point");
		JsonObject sphere = object(components, "minecraft:emitter_shape_sphere");
		JsonObject box = object(components, "minecraft:emitter_shape_box");
		JsonObject disc = object(components, "minecraft:emitter_shape_disc");
		JsonObject shape = sphere != null ? sphere : box != null ? box : disc != null ? disc : point;
		this.shape = sphere != null ? Shape.SPHERE : box != null ? Shape.BOX : disc != null ? Shape.DISC : Shape.POINT;
		this.offset = triple(shape == null ? null : shape.get("offset"), 0.0);
		this.radius = Molang.of(shape == null ? null : shape.get("radius"), 1.0);
		this.halfDimensions = triple(shape == null ? null : shape.get("half_dimensions"), 0.0);
		this.surfaceOnly = shape != null && shape.has("surface_only") && shape.get("surface_only").getAsBoolean();
		JsonElement direction = shape == null ? null : shape.get("direction");
		this.direction = direction != null && direction.isJsonPrimitive() ? direction.getAsString().toLowerCase(Locale.ROOT) : direction == null ? "outwards" : "vector";
		this.directionVector = triple(direction != null && direction.isJsonArray() ? direction : null, 0.0);
		JsonObject localSpace = object(components, "minecraft:emitter_local_space");
		this.localPosition = localSpace != null && localSpace.has("position") && localSpace.get("position").getAsBoolean();
		JsonObject lifetime = object(components, "minecraft:particle_lifetime_expression");
		this.lifetime = Molang.of(lifetime == null ? null : lifetime.get("max_lifetime"), 1.0);
		JsonElement speed = components.get("minecraft:particle_initial_speed");
		this.speed = Molang.of(speed != null && speed.isJsonPrimitive() ? speed : null, 0.0);
		this.speedVector = speed != null && speed.isJsonArray() ? triple(speed, 0.0) : null;
		JsonObject motion = object(components, "minecraft:particle_motion_dynamic");
		this.acceleration = triple(motion == null ? null : motion.get("linear_acceleration"), 0.0);
		this.drag = Molang.of(motion == null ? null : motion.get("linear_drag_coefficient"), 0.0);
		JsonObject spin = object(components, "minecraft:particle_initial_spin");
		this.initialRotation = Molang.of(spin == null ? null : spin.get("rotation"), 0.0);
		this.rotationRate = Molang.of(spin == null ? null : spin.get("rotation_rate"), 0.0);
		JsonObject billboard = object(components, "minecraft:particle_appearance_billboard");
		this.size = pair(billboard == null ? null : billboard.get("size"), 0.1);
		String facing = billboard != null && billboard.has("facing_camera_mode") ? billboard.get("facing_camera_mode").getAsString() : "rotate_xyz";
		this.yAxisOnly = facing.endsWith("_y");
		JsonObject uv = billboard == null ? null : object(billboard, "uv");
		this.textureWidth = uv != null && uv.has("texture_width") ? uv.get("texture_width").getAsFloat() : 1.0F;
		this.textureHeight = uv != null && uv.has("texture_height") ? uv.get("texture_height").getAsFloat() : 1.0F;
		this.uv = pair(uv == null ? null : uv.get("uv"), 0.0);
		this.uvSize = pair(uv == null || !uv.has("uv_size") ? null : uv.get("uv_size"), this.textureWidth);
		JsonObject flipbook = uv == null ? null : object(uv, "flipbook");
		this.flipbook = flipbook != null;
		this.baseUv = pair(flipbook == null ? null : flipbook.get("base_UV"), 0.0);
		this.sizeUv = floats(flipbook == null ? null : flipbook.get("size_UV"), this.textureWidth, this.textureHeight);
		this.stepUv = floats(flipbook == null ? null : flipbook.get("step_UV"), 0.0F, 0.0F);
		this.framesPerSecond = flipbook != null && flipbook.has("frames_per_second") ? flipbook.get("frames_per_second").getAsFloat() : 8.0F;
		this.maxFrame = Molang.of(flipbook == null ? null : flipbook.get("max_frame"), 1.0);
		this.stretch = flipbook != null && flipbook.has("stretch_to_lifetime") && flipbook.get("stretch_to_lifetime").getAsBoolean();
		this.loopFrames = flipbook != null && flipbook.has("loop") && flipbook.get("loop").getAsBoolean();
		JsonObject tinting = object(components, "minecraft:particle_appearance_tinting");
		this.tint = tint(tinting == null ? null : tinting.get("color"));
	}

	static EmoteParticle parse(JsonObject root, Identifier texture) {
		JsonObject effect = object(root, "particle_effect");
		JsonObject components = effect == null ? null : object(effect, "components");
		return components == null ? null : new EmoteParticle(texture, components);
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
	}

	private static Molang.Expr[] triple(JsonElement element, double fallback) {
		Molang.Expr[] values = new Molang.Expr[3];
		JsonArray array = element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
		for (int i = 0; i < 3; i++) {
			values[i] = Molang.of(array != null && i < array.size() ? array.get(i) : null, fallback);
		}
		return values;
	}

	private static Molang.Expr[] pair(JsonElement element, double fallback) {
		Molang.Expr[] values = new Molang.Expr[2];
		JsonArray array = element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
		for (int i = 0; i < 2; i++) {
			values[i] = Molang.of(array != null && i < array.size() ? array.get(i) : null, fallback);
		}
		return values;
	}

	private static float[] floats(JsonElement element, float first, float second) {
		float[] values = {first, second};
		if (element != null && element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (int i = 0; i < Math.min(2, array.size()); i++) {
				if (array.get(i).isJsonPrimitive() && array.get(i).getAsJsonPrimitive().isNumber()) {
					values[i] = array.get(i).getAsFloat();
				}
			}
		}
		return values;
	}

	// Color array or hex string
	private static Molang.Expr[] tint(JsonElement element) {
		if (element == null) {
			return null;
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			Molang.Expr[] values = new Molang.Expr[4];
			for (int i = 0; i < 4; i++) {
				values[i] = Molang.of(i < array.size() ? array.get(i) : null, 1.0);
			}
			return values;
		}
		if (element.isJsonPrimitive() && element.getAsString().startsWith("#")) {
			String hex = element.getAsString().substring(1);
			try {
				long value = Long.parseLong(hex, 16);
				double alpha = hex.length() == 8 ? (value >> 24 & 0xFF) / 255.0 : 1.0;
				double red = (value >> 16 & 0xFF) / 255.0;
				double green = (value >> 8 & 0xFF) / 255.0;
				double blue = (value & 0xFF) / 255.0;
				return new Molang.Expr[] {scope -> red, scope -> green, scope -> blue, scope -> alpha};
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	// One particle
	static final class Particle {
		final Vector3f position = new Vector3f();
		final Vector3f velocity = new Vector3f();
		final double[] randoms = new double[4];
		double age;
		double lifetime;
		float rotation;
		float rotationRate;
	}

	// Emitter following its locator
	final class Emitter {
		final List<Particle> particles = new ArrayList<>();
		final Molang.Scope scope = new Molang.Scope();
		final Vector3f origin = new Vector3f();
		private double age;
		private double cycle;
		private double spawnDebt;
		private boolean burstDone;
		private boolean finished;

		Emitter() {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			for (int i = 1; i <= 4; i++) {
				this.scope.set("variable.emitter_random_" + i, random.nextDouble());
			}
		}

		EmoteParticle definition() {
			return EmoteParticle.this;
		}

		boolean done() {
			return this.finished && this.particles.isEmpty();
		}

		// Spawns, moves and ages particles
		void tick(double delta) {
			this.age += delta;
			this.cycle += delta;
			this.scope.set("variable.emitter_age", this.age);
			double active = activeTime.eval(this.scope);
			if (!this.finished) {
				if (this.cycle <= active || this.age <= delta) {
					if (instant) {
						if (!this.burstDone) {
							this.burstDone = true;
							int count = (int) Math.round(amount.eval(this.scope));
							for (int i = 0; i < count; i++) {
								spawn();
							}
						}
					} else {
						this.spawnDebt += rate.eval(this.scope) * delta;
						int limit = (int) Math.round(maxParticles.eval(this.scope));
						while (this.spawnDebt >= 1.0) {
							this.spawnDebt -= 1.0;
							if (this.particles.size() < limit) {
								spawn();
							}
						}
					}
				} else if (looping && this.cycle > active + sleepTime.eval(this.scope)) {
					this.cycle = 0.0;
					this.burstDone = false;
				} else if (!looping) {
					this.finished = true;
				}
			}
			Iterator<Particle> iterator = this.particles.iterator();
			while (iterator.hasNext()) {
				Particle particle = iterator.next();
				particle.age += delta;
				if (particle.age >= particle.lifetime) {
					iterator.remove();
					continue;
				}
				bind(particle);
				Vector3f accel = new Vector3f((float) acceleration[0].eval(this.scope), (float) acceleration[1].eval(this.scope),
						(float) acceleration[2].eval(this.scope));
				float dragValue = (float) drag.eval(this.scope);
				accel.sub(new Vector3f(particle.velocity).mul(dragValue));
				particle.velocity.add(accel.mul((float) delta));
				particle.position.add(new Vector3f(particle.velocity).mul((float) delta));
				particle.rotation += particle.rotationRate * (float) delta;
			}
		}

		void bind(Particle particle) {
			this.scope.set("variable.particle_age", particle.age);
			this.scope.set("variable.particle_lifetime", particle.lifetime);
			for (int i = 0; i < 4; i++) {
				this.scope.set("variable.particle_random_" + (i + 1), particle.randoms[i]);
			}
		}

		// New particle in the shape
		private void spawn() {
			if (this.particles.size() >= MAX_PARTICLES) {
				return;
			}
			ThreadLocalRandom random = ThreadLocalRandom.current();
			Particle particle = new Particle();
			for (int i = 0; i < 4; i++) {
				particle.randoms[i] = random.nextDouble();
			}
			bind(particle);
			Vector3f local = new Vector3f((float) offset[0].eval(this.scope), (float) offset[1].eval(this.scope), (float) offset[2].eval(this.scope));
			Vector3f spread = switch (shape) {
				case SPHERE -> {
					Vector3f dir = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian());
					if (dir.lengthSquared() < 1.0E-6F) {
						dir.set(0, 1, 0);
					}
					dir.normalize();
					float r = (float) radius.eval(this.scope);
					yield dir.mul(surfaceOnly ? r : r * (float) Math.cbrt(random.nextDouble()));
				}
				case BOX -> {
					Vector3f half = new Vector3f((float) halfDimensions[0].eval(this.scope), (float) halfDimensions[1].eval(this.scope),
							(float) halfDimensions[2].eval(this.scope));
					yield new Vector3f((float) (random.nextDouble() * 2 - 1) * half.x, (float) (random.nextDouble() * 2 - 1) * half.y,
							(float) (random.nextDouble() * 2 - 1) * half.z);
				}
				case DISC -> {
					double angle = random.nextDouble() * Math.PI * 2;
					float r = (float) radius.eval(this.scope) * (surfaceOnly ? 1.0F : (float) Math.sqrt(random.nextDouble()));
					yield new Vector3f((float) Math.cos(angle) * r, 0.0F, (float) Math.sin(angle) * r);
				}
				default -> new Vector3f();
			};
			particle.position.set(local).add(spread);
			Vector3f dir = switch (direction) {
				case "inwards" -> new Vector3f(spread).negate();
				case "vector" -> new Vector3f((float) directionVector[0].eval(this.scope), (float) directionVector[1].eval(this.scope),
						(float) directionVector[2].eval(this.scope));
				default -> new Vector3f(spread);
			};
			if (dir.lengthSquared() > 1.0E-6F) {
				dir.normalize();
			}
			if (speedVector != null) {
				particle.velocity.set((float) speedVector[0].eval(this.scope), (float) speedVector[1].eval(this.scope), (float) speedVector[2].eval(this.scope));
			} else {
				particle.velocity.set(dir).mul((float) speed.eval(this.scope));
			}
			particle.lifetime = Math.max(0.01, lifetime.eval(this.scope));
			particle.rotation = (float) initialRotation.eval(this.scope);
			particle.rotationRate = (float) rotationRate.eval(this.scope);
			if (!localPosition) {
				particle.position.add(this.origin);
			}
			this.particles.add(particle);
		}
	}

	// Look of a particle at its age
	float[] size(Molang.Scope scope) {
		return new float[] {(float) this.size[0].eval(scope), (float) this.size[1].eval(scope)};
	}

	float[] uv(Particle particle, Molang.Scope scope) {
		float u;
		float v;
		float width;
		float height;
		if (this.flipbook) {
			int frames = Math.max(1, (int) Math.round(this.maxFrame.eval(scope)));
			int frame;
			if (this.stretch) {
				frame = (int) Math.floor(particle.age / Math.max(1.0E-6, particle.lifetime) * frames);
			} else {
				frame = (int) Math.floor(particle.age * this.framesPerSecond);
				frame = this.loopFrames ? frame % frames : frame;
			}
			frame = Math.clamp(frame, 0, frames - 1);
			u = (float) this.baseUv[0].eval(scope) + this.stepUv[0] * frame;
			v = (float) this.baseUv[1].eval(scope) + this.stepUv[1] * frame;
			width = this.sizeUv[0];
			height = this.sizeUv[1];
		} else {
			u = (float) this.uv[0].eval(scope);
			v = (float) this.uv[1].eval(scope);
			width = (float) this.uvSize[0].eval(scope);
			height = (float) this.uvSize[1].eval(scope);
		}
		return new float[] {u / this.textureWidth, v / this.textureHeight, (u + width) / this.textureWidth, (v + height) / this.textureHeight};
	}

	int color(Molang.Scope scope) {
		if (this.tint == null) {
			return 0xFFFFFFFF;
		}
		int red = channel(this.tint[0].eval(scope));
		int green = channel(this.tint[1].eval(scope));
		int blue = channel(this.tint[2].eval(scope));
		int alpha = channel(this.tint[3].eval(scope));
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int channel(double value) {
		return Math.clamp((int) Math.round(value * 255.0), 0, 255);
	}
}
