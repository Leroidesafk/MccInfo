package leroidesafk.mccinfo.client.emote;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import leroidesafk.mccinfo.client.mixin.accessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;

public final class EmotePreviewRenderer extends PictureInPictureRenderer<EmotePreviewRenderer.State> {
	// Scene size and view angle
	private static final float SCENE_WIDTH = 4.4F;
	private static final float SCENE_CENTER = 1.45F;
	private static final float TURN = 25.0F;

	// Player, emote pose and cell
	record State(EntityRenderState player, EmoteLibrary.Emote emote, Map<EmoteModel.Bone, Matrix4f> matrices, int x0, int y0, int x1, int y1,
			float scale, ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
		State(EntityRenderState player, EmoteLibrary.Emote emote, Map<EmoteModel.Bone, Matrix4f> matrices, int x0, int y0, int x1, int y1, float scale) {
			this(player, emote, matrices, x0, y0, x1, y1, scale, null, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
		}
	}

	private final EntityRenderDispatcher dispatcher;

	public EmotePreviewRenderer(EntityRenderDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	// Emote preview in a cell, animated since the screen opened
	public static void extract(GuiGraphicsExtractor graphics, String id, long since, int x0, int y0, int x1, int y1) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		EmoteLibrary.Emote emote = EmoteLibrary.get(id);
		EntityRenderState player = client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 1.0F);
		player.shadowPieces.clear();
		player.outlineColor = 0;
		player.lightCoords = EmoteRender.FULL_BRIGHT;
		player.nameTag = null;
		if (player instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.walkAnimationPos = 0.0F;
			living.walkAnimationSpeed = 0.0F;
		}
		Map<EmoteModel.Bone, Matrix4f> matrices = Map.of();
		if (emote != null) {
			double time = (System.nanoTime() - since) / 1.0E9;
			matrices = EmoteRender.pose(emote, EmoteRender.phase(emote, time), new Molang.Scope());
			Emotes.previewPose(player, emote, matrices);
		}
		float scale = (x1 - x0) / SCENE_WIDTH;
		((GuiGraphicsExtractorAccessor) graphics).mccinfo$getGuiRenderState()
				.addPicturesInPictureState(new State(player, emote, matrices, x0, y0, x1, y1, scale));
	}

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	// Player facing the viewer, emote seen from behind
	@Override
	protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		Quaternionf view = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(TURN));
		CameraRenderState camera = new CameraRenderState();
		camera.orientation = new Quaternionf().rotateY((float) Math.PI);
		pose.pushPose();
		pose.translate(0.0F, SCENE_CENTER, 0.0F);
		pose.mulPose(view);
		this.dispatcher.submit(state.player(), camera, 0.0, 0.0, 0.0, pose, collector);
		if (state.emote() != null) {
			pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			EmoteRender.submitModel(collector, pose, state.emote(), state.matrices(), EmoteRender.FULL_BRIGHT, new Quaternionf().rotateY((float) Math.toRadians(-TURN)));
		}
		pose.popPose();
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "mccinfo_emote_preview";
	}
}
