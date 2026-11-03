package leroidesafk.mccinfo.client.emote;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.RenderSetupTexturesAccessor;
import leroidesafk.mccinfo.client.mixin.accessor.RenderTypeAccessor;
import leroidesafk.mccinfo.client.mixin.accessor.RenderTypeSetupAccessor;
import leroidesafk.mccinfo.client.mixin.accessor.TextureBindingAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class EmoteMirage {
	// Mirage life and drift
	static final String EFFECT = "mirage";
	private static final double LIFE_SECONDS = 0.6;
	private static final double DRIFT = 1.4;
	private static final float START_ALPHA = 1.0F;

	// Ghost render types
	private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation(Mccinfo.id("pipeline/emote_mirage"))
			.withVertexShader(Mccinfo.id("core/emote_mirage"))
			.withFragmentShader(Mccinfo.id("core/emote_mirage"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build();
	private static final Function<Identifier, RenderType> TYPES = Util.memoize(texture -> RenderTypeAccessor.mccinfo$create("mccinfo_emote_mirage",
			RenderSetup.builder(PIPELINE).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));
	private static final Map<RenderType, Optional<RenderType>> CONVERTED = new ConcurrentHashMap<>();

	private final java.util.UUID owner;
	private final List<EntityRenderState> states;
	private final Vec3 drift;
	private final long bornNanos = System.nanoTime();

	private EmoteMirage(java.util.UUID owner, List<EntityRenderState> states, Vec3 drift) {
		this.owner = owner;
		this.states = states;
		this.drift = drift;
	}

	java.util.UUID owner() {
		return this.owner;
	}

	// Player frozen in the emote pose
	static EmoteMirage capture(Player player, EmoteLibrary.Emote emote, Map<EmoteModel.Bone, Matrix4f> matrices) {
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		List<EntityRenderState> states = new ArrayList<>();
		EntityRenderState body = dispatcher.extractEntity(player, 1.0F);
		Emotes.previewPose(body, emote, matrices);
		states.add(clean(body));
		for (Entity passenger : player.getIndirectPassengers()) {
			states.add(clean(dispatcher.extractEntity(passenger, 1.0F)));
		}
		float yaw = body instanceof LivingEntityRenderState living ? living.bodyRot : player.getYRot();
		double radians = Math.toRadians(yaw);
		return new EmoteMirage(player.getUUID(), List.copyOf(states), new Vec3(Math.sin(radians), 0.0, -Math.cos(radians)).scale(DRIFT));
	}

	private static EntityRenderState clean(EntityRenderState state) {
		state.outlineColor = 0;
		state.nameTag = null;
		state.shadowPieces.clear();
		state.displayFireAnimation = false;
		state.leashStates = null;
		return state;
	}

	boolean done() {
		return age() >= 1.0;
	}

	private double age() {
		return (System.nanoTime() - this.bornNanos) / 1.0E9 / LIFE_SECONDS;
	}

	// Fading and drifting backwards
	void submit(SubmitNodeCollector collector, PoseStack poseStack, CameraRenderState camera) {
		double age = Math.min(1.0, age());
		float alpha = (float) (START_ALPHA * Math.pow(1.0 - age, 1.5));
		if (alpha <= 0.01F) {
			return;
		}
		double travel = 1.0 - (1.0 - age) * (1.0 - age);
		Vec3 offset = this.drift.scale(travel);
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		Ghost ghost = new Ghost(collector, alpha);
		for (EntityRenderState state : this.states) {
			dispatcher.submit(state, camera, state.x + offset.x - camera.pos.x, state.y + offset.y - camera.pos.y, state.z + offset.z - camera.pos.z,
					poseStack, ghost);
		}
	}

	// Translucent copy of a render type
	private static @Nullable RenderType convert(RenderType type) {
		return CONVERTED.computeIfAbsent(type, key -> {
			if (key.isOutline()) {
				return Optional.empty();
			}
			Object binding = ((RenderSetupTexturesAccessor) (Object) ((RenderTypeSetupAccessor) key).mccinfo$getState()).mccinfo$getTextures().get("Sampler0");
			if (binding == null) {
				return Optional.empty();
			}
			Identifier texture = ((TextureBindingAccessor) binding).mccinfo$getLocation();
			if (texture.getPath().contains("glint")) {
				return Optional.empty();
			}
			return Optional.of(TYPES.apply(texture));
		}).orElse(null);
	}

	private static int fade(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * alpha);
		return a << 24 | color & 0xFFFFFF;
	}

	// Collector drawing everything as a ghost
	private record Ghost(SubmitNodeCollector target, float alpha) implements SubmitNodeCollector {
		@Override
		public OrderedSubmitNodeCollector order(int order) {
			return this;
		}

		@Override
		public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
				int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
			RenderType ghost = convert(renderType);
			if (ghost != null) {
				this.target.submitModel(model, state, poseStack, ghost, lightCoords, OverlayTexture.NO_OVERLAY, fade(tintedColor, this.alpha), sprite, 0, null);
			}
		}

		@Override
		public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
				List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
			Map<Identifier, List<BakedQuad>> byAtlas = new LinkedHashMap<>();
			for (BakedQuad quad : quads) {
				byAtlas.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), atlas -> new ArrayList<>()).add(quad);
			}
			int color = fade(-1, this.alpha);
			for (Map.Entry<Identifier, List<BakedQuad>> entry : byAtlas.entrySet()) {
				List<BakedQuad> group = entry.getValue();
				this.target.submitCustomGeometry(poseStack, TYPES.apply(entry.getKey()), (pose, buffer) -> {
					QuadInstance instance = new QuadInstance();
					instance.setColor(color);
					instance.setLightCoords(lightCoords);
					instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
					for (BakedQuad quad : group) {
						buffer.putBakedQuad(pose, quad, instance);
					}
				});
			}
		}

		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
		}

		@Override
		public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
		}

		@Override
		public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
				CameraRenderState camera) {
		}

		@Override
		public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
				int lightCoords, int color, int backgroundColor, int outlineColor) {
		}

		@Override
		public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
		}

		@Override
		public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		}

		@Override
		public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
		}

		@Override
		public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
				int overlayCoords, int outlineColor) {
		}

		@Override
		public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
		}

		@Override
		public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
		}

		@Override
		public void submitQuadParticleGroup(QuadParticleRenderState particles) {
		}

		@Override
		public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
		}

		@Override
		public <T extends net.minecraft.client.renderer.feature.submit.SubmitNode> void submitCustom(
				net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase<T> phase, T node) {
		}
	}
}
