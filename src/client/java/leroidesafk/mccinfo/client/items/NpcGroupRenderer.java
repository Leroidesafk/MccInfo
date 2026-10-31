package leroidesafk.mccinfo.client.items;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class NpcGroupRenderer extends PictureInPictureRenderer<NpcGroupRenderer.State> {
	// One entity of the NPC and its offset
	record Entry(EntityRenderState renderState, Vector3f translation) {
	}

	// Everything needed to draw the NPC in a box
	record State(List<Entry> entries, Quaternionf rotation, Quaternionf camera, int x0, int y0, int x1, int y1, float scale,
			ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
		State(List<Entry> entries, Quaternionf rotation, Quaternionf camera, int x0, int y0, int x1, int y1, float scale) {
			this(entries, rotation, camera, x0, y0, x1, y1, scale, null, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
		}
	}

	private final EntityRenderDispatcher dispatcher;

	NpcGroupRenderer(EntityRenderDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	@Override
	protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
		// Entities drawn one by one, turned like the preview
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		CameraRenderState camera = new CameraRenderState();
		camera.orientation = state.camera().conjugate(new Quaternionf()).rotateY((float) Math.PI);
		for (Entry entry : state.entries()) {
			pose.pushPose();
			pose.translate(entry.translation().x(), entry.translation().y(), entry.translation().z());
			pose.mulPose(state.rotation());
			this.dispatcher.submit(entry.renderState(), camera, 0.0, 0.0, 0.0, pose, collector);
			pose.popPose();
		}
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "mccinfo_npc";
	}
}
