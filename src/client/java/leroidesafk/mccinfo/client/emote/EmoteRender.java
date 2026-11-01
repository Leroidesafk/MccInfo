package leroidesafk.mccinfo.client.emote;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.RenderTypeAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import java.util.function.Function;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EmoteRender {
	// Full light and hidden bones
	static final int FULL_BRIGHT = 0xF000F0;
	private static final float HIDDEN = 1.0E-6F;
	// Glowing parts: no light, no shading
	private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation(Mccinfo.id("pipeline/emote_glow"))
			.withShaderDefine("ALPHA_CUTOUT", 0.1F)
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
			.build();
	private static final Function<Identifier, RenderType> GLOW = Util.memoize(texture -> RenderTypeAccessor.mccinfo$create("mccinfo_emote_glow",
			RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", texture).useOverlay().createRenderSetup()));

	// Animation and its time
	record Phase(EmoteAnimation animation, double time) {
	}

	private EmoteRender() {
	}

	// Intro, then loop
	static Phase phase(EmoteLibrary.Emote emote, double time) {
		EmoteAnimation intro = emote.intro;
		EmoteAnimation loop = emote.loop;
		double introLength = intro == null ? 0.0 : intro.length;
		if (intro != null && (time < introLength || loop == null)) {
			if (time < introLength || intro.loop == EmoteAnimation.Loop.HOLD) {
				return new Phase(intro, Math.min(time, introLength));
			}
			if (intro.loop == EmoteAnimation.Loop.LOOP && introLength > 0.0) {
				return new Phase(intro, time % introLength);
			}
			return null;
		}
		if (loop == null) {
			return null;
		}
		double relative = Math.max(0.0, time - introLength);
		if (loop.loop == EmoteAnimation.Loop.HOLD) {
			return new Phase(loop, Math.min(relative, loop.length));
		}
		if (loop.loop == EmoteAnimation.Loop.ONCE && relative > loop.length) {
			return null;
		}
		return new Phase(loop, loop.length > 0.0 ? relative % loop.length : 0.0);
	}

	// Bone matrices at a phase
	static Map<EmoteModel.Bone, Matrix4f> pose(EmoteLibrary.Emote emote, Phase phase, Molang.Scope scope) {
		Map<String, float[]> animated = new HashMap<>();
		if (phase != null) {
			phase.animation().sample(phase.time(), scope, animated);
		}
		return emote.model.pose(animated);
	}

	interface EffectSink {
		void accept(EmoteAnimation.Effect effect);
	}

	// Sounds or particles between two times, loops included
	static void effectsBetween(EmoteLibrary.Emote emote, double from, double to, boolean sounds, EffectSink sink) {
		if (to <= from) {
			return;
		}
		EmoteAnimation intro = emote.intro;
		EmoteAnimation loop = emote.loop;
		double introLength = intro == null ? 0.0 : intro.length;
		if (intro != null) {
			for (EmoteAnimation.Effect effect : sounds ? intro.sounds : intro.particles) {
				if (effect.time() > from && effect.time() <= to && effect.time() <= introLength + 1.0E-6) {
					sink.accept(effect);
				}
			}
		}
		if (loop == null || to < introLength) {
			return;
		}
		double relativeFrom = from - introLength;
		double relativeTo = to - introLength;
		if (loop.loop != EmoteAnimation.Loop.LOOP || loop.length <= 0.0) {
			for (EmoteAnimation.Effect effect : sounds ? loop.sounds : loop.particles) {
				if (effect.time() > relativeFrom && effect.time() <= relativeTo) {
					sink.accept(effect);
				}
			}
			return;
		}
		long first = Math.max(0L, (long) Math.floor(relativeFrom / loop.length));
		long last = (long) Math.floor(relativeTo / loop.length);
		for (long cycle = first; cycle <= last && cycle - first < 4; cycle++) {
			for (EmoteAnimation.Effect effect : sounds ? loop.sounds : loop.particles) {
				double at = cycle * loop.length + effect.time();
				if (at > relativeFrom && at <= relativeTo) {
					sink.accept(effect);
				}
			}
		}
	}

	// Glowing and lit parts, player bones skipped
	static void submitModel(SubmitNodeCollector collector, PoseStack poseStack, EmoteLibrary.Emote emote, Map<EmoteModel.Bone, Matrix4f> matrices, int light,
			Quaternionf facing) {
		if (emote.texture == null || emote.model.bones.isEmpty()) {
			return;
		}
		boolean player = EmotePlayerPose.animatesPlayer(emote.model);
		boolean anyGlow = false;
		boolean anyLit = false;
		for (EmoteModel.Bone bone : emote.model.bones) {
			if (bone.faces.isEmpty() || player && EmotePlayerPose.PARTS.contains(bone.name)) {
				continue;
			}
			if (emote.glowing.contains(bone)) {
				anyGlow = true;
			} else {
				anyLit = true;
			}
		}
		if (anyGlow) {
			submitBones(collector, poseStack, GLOW.apply(emote.texture), emote, matrices, FULL_BRIGHT, true, player, facing);
		}
		if (anyLit) {
			submitBones(collector, poseStack, RenderTypes.entityCutoutCull(emote.texture), emote, matrices, light, false, player, facing);
		}
	}

	// Bone faces, billboards facing the camera
	private static void submitBones(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, EmoteLibrary.Emote emote,
			Map<EmoteModel.Bone, Matrix4f> matrices, int light, boolean glow, boolean player, Quaternionf facing) {
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			for (EmoteModel.Bone bone : emote.model.bones) {
				Matrix4f boneMatrix = matrices.get(bone);
				if (boneMatrix == null || bone.faces.isEmpty() || Math.abs(boneMatrix.determinant3x3()) < HIDDEN
						|| emote.glowing.contains(bone) != glow || player && EmotePlayerPose.PARTS.contains(bone.name)) {
					continue;
				}
				Matrix4f matrix = new Matrix4f(pose.pose()).mul(boneMatrix);
				if (facing != null && emote.billboard.contains(bone)) {
					Vector3f pivot = boneMatrix.transformPosition(new Vector3f());
					Vector3f scale = boneMatrix.getScale(new Vector3f());
					matrix = new Matrix4f(pose.pose()).translate(pivot).rotate(facing).scale(scale);
				}
				Matrix3f normals = new Matrix3f(matrix);
				for (EmoteModel.Face face : bone.faces) {
					Vector3f normal = normals.transform(new Vector3f(face.normal()));
					if (normal.lengthSquared() > 1.0E-8F) {
						normal.normalize();
					}
					float[] uv = face.uv();
					vertex(buffer, matrix, face.corners()[0], uv[0], uv[1], light, normal);
					vertex(buffer, matrix, face.corners()[1], uv[0], uv[3], light, normal);
					vertex(buffer, matrix, face.corners()[2], uv[2], uv[3], light, normal);
					vertex(buffer, matrix, face.corners()[3], uv[2], uv[1], light, normal);
				}
			}
		});
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vector3f corner, float u, float v, int light, Vector3f normal) {
		Vector3f position = matrix.transformPosition(new Vector3f(corner));
		buffer.addVertex(position.x, position.y, position.z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(normal.x, normal.y, normal.z);
	}

	// Particle quads facing the camera
	static void submitParticles(SubmitNodeCollector collector, PoseStack poseStack, List<EmoteParticle.Emitter> emitters, Vector3f camera,
			Quaternionf rotation, float partial) {
		Vector3f right = rotation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
		Vector3f up = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
		Vector3f forward = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
		for (EmoteParticle.Emitter emitter : emitters) {
			EmoteParticle definition = emitter.definition();
			if (emitter.particles.isEmpty()) {
				continue;
			}
			collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(definition.texture), (pose, buffer) -> {
				for (EmoteParticle.Particle particle : emitter.particles) {
					emitter.bind(particle);
					Vector3f center = new Vector3f(particle.position).add(new Vector3f(particle.velocity).mul(partial * 0.05F));
					if (definition.localPosition) {
						center.add(emitter.origin);
					}
					center.sub(camera);
					float[] size = definition.size(emitter.scope);
					float[] uv = definition.uv(particle, emitter.scope);
					int color = definition.color(emitter.scope);
					Vector3f axisX = new Vector3f(right);
					Vector3f axisY = new Vector3f(up);
					if (definition.yAxisOnly) {
						axisY.set(0.0F, 1.0F, 0.0F);
						axisX.set(forward.z, 0.0F, -forward.x);
						if (axisX.lengthSquared() < 1.0E-6F) {
							axisX.set(1.0F, 0.0F, 0.0F);
						}
						axisX.normalize();
					}
					if (particle.rotation != 0.0F) {
						float angle = (float) Math.toRadians(particle.rotation);
						float cos = (float) Math.cos(angle);
						float sin = (float) Math.sin(angle);
						Vector3f rotatedX = new Vector3f(axisX).mul(cos).add(new Vector3f(axisY).mul(sin));
						Vector3f rotatedY = new Vector3f(axisY).mul(cos).sub(new Vector3f(axisX).mul(sin));
						axisX = rotatedX;
						axisY = rotatedY;
					}
					axisX.mul(size[0]);
					axisY.mul(size[1]);
					billboard(buffer, pose, new Vector3f(center).sub(axisX).add(axisY), uv[0], uv[1], color);
					billboard(buffer, pose, new Vector3f(center).sub(axisX).sub(axisY), uv[0], uv[3], color);
					billboard(buffer, pose, new Vector3f(center).add(axisX).sub(axisY), uv[2], uv[3], color);
					billboard(buffer, pose, new Vector3f(center).add(axisX).add(axisY), uv[2], uv[1], color);
				}
			});
		}
	}

	private static void billboard(VertexConsumer buffer, PoseStack.Pose pose, Vector3f position, float u, float v, int color) {
		buffer.addVertex(pose, position.x, position.y, position.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
