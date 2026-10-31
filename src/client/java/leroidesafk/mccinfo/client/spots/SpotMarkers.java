package leroidesafk.mccinfo.client.spots;

import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class SpotMarkers {
	public static final int SIZE = 6;
	static final int BORDER_COLOR = 0xFF000000;

	private static final float CENTER_SHADE = 0.85F;
	static final double RADIUS = 500.0;
	private static final int TEXT_COLOR = 0xFFAAAAAA;
	private static final String WIDEST_DISTANCE = "000";
	private static final float DIGIT_CENTER_Y = 4.0F;
	private static final double FADE_START = 30.0;
	static final double FADE_END = 20.0;
	private static final float MIN_ALPHA = 0.05F;
	private static final float LOADED_SCALE = 0.6F;
	private static final float SHRINK_SECONDS = 0.25F;
	private static final float MAX_FRAME_SECONDS = 0.05F;
	private static final long SHRINK_FORGET_NANOS = 1_000_000_000L;
	// Shrink progress per spot
	private static final java.util.Map<Vec3, float[]> SHRINKS = new java.util.HashMap<>();
	private static final java.util.Map<Vec3, Long> SHRINK_NANOS = new java.util.HashMap<>();

	private static final Vector4f CLIP = new Vector4f();

	record Projection(Vec3 cameraPos, Matrix4f worldToClip, Vector3f up) {
	}

	@FunctionalInterface
	interface Shape {
		void extract(GuiGraphicsExtractor graphics, float alpha);
	}

	private record Quad(Matrix3x2fc pose, float[] points, int color, ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer vertexConsumer) {
			for (int i = 0; i < 8; i += 2) {
				vertexConsumer.addVertexWith2DPose(this.pose, this.points[i], this.points[i + 1]).setColor(this.color);
			}
		}

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}

	private SpotMarkers() {
	}

	static void fillRing(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	public static void extractSquare(GuiGraphicsExtractor graphics, int rgb, float alpha) {
		int half = SIZE / 2;
		fillRing(graphics, -half - 1, -half - 1, half + 1, half + 1, OverlayStyle.withAlpha(BORDER_COLOR, alpha));
		fillRing(graphics, -half, -half, half, half, OverlayStyle.withAlpha(0xFF000000 | rgb, alpha));
		graphics.fill(-half + 1, -half + 1, half - 1, half - 1, OverlayStyle.withAlpha(0xFF000000 | darken(rgb), alpha));
	}

	static void fillQuad(GuiGraphicsExtractor graphics, int color, float... points) {
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		float left = Float.MAX_VALUE;
		float top = Float.MAX_VALUE;
		float right = -Float.MAX_VALUE;
		float bottom = -Float.MAX_VALUE;
		for (int i = 0; i < 8; i += 2) {
			left = Math.min(left, points[i]);
			right = Math.max(right, points[i]);
			top = Math.min(top, points[i + 1]);
			bottom = Math.max(bottom, points[i + 1]);
		}
		int x = Mth.floor(left);
		int y = Mth.floor(top);
		ScreenRectangle scissorArea = graphics.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle(x, y, Mth.ceil(right) - x, Mth.ceil(bottom) - y).transformMaxBounds(pose);
		graphics.guiRenderState.addGuiElement(new Quad(pose, points.clone(), ScreenOpenAnimation.fade(color), scissorArea,
				scissorArea != null ? scissorArea.intersection(bounds) : bounds));
	}

	static Projection projection() {
		GameRenderState renderState = Minecraft.getInstance().gameRenderer.gameRenderState();
		CameraRenderState camera = renderState.levelRenderState.cameraRenderState;
		if (camera == null || !camera.initialized) {
			return null;
		}
		Matrix4f worldToClip = new Matrix4f(camera.projectionMatrix);
		if (renderState.optionsRenderState.bobView) {
			applyViewBobbing(worldToClip, camera.entityRenderState);
		}
		worldToClip.mul(camera.viewRotationMatrix);
		return new Projection(camera.pos, worldToClip, new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.orientation));
	}

	static void extract(GuiGraphicsExtractor graphics, Projection projection, Vec3 position, Shape shape) {
		extract(graphics, projection, position, shape, null, false);
	}

	// Fade, hover growth and size
	record Style(boolean fade, boolean grow, float size) {
		static final Style SPOT = new Style(true, true, 1.0F);
	}

	static void extract(GuiGraphicsExtractor graphics, Projection projection, Vec3 position, Shape shape, SpotPath.Target clickTarget, boolean loaded) {
		extract(graphics, projection, position, shape, clickTarget, loaded, Style.SPOT);
	}

	static void extract(GuiGraphicsExtractor graphics, Projection projection, Vec3 position, Shape shape, SpotPath.Target clickTarget, boolean loaded,
			Style style) {
		float shrink = shrink(position, loaded);
		// Distance and fade
		Vec3 offset = position.subtract(projection.cameraPos());
		double distance = offset.length();
		if (distance > RADIUS) {
			return;
		}
		float alpha = style.fade() ? (float) Mth.clamp((distance - FADE_END) / (FADE_START - FADE_END), 0.0, 1.0) : 1.0F;
		if (alpha < MIN_ALPHA) {
			return;
		}
		// World to screen
		projection.worldToClip().transform(CLIP.set((float) offset.x, (float) offset.y, (float) offset.z, 1.0F));
		if (CLIP.w <= 0) {
			return;
		}
		float projectedX = CLIP.x / CLIP.w;
		float projectedY = CLIP.y / CLIP.w;
		if (Math.abs(projectedX) > 1 || Math.abs(projectedY) > 1) {
			return;
		}
		float screenX = (projectedX + 1) / 2 * graphics.guiWidth();
		float screenY = (1 - projectedY) / 2 * graphics.guiHeight();
		float[] display = loaded && clickTarget != null ? displayBox(graphics, projection, position) : null;
		float hover = SpotPath.markerScale(position, clickTarget, alpha, distance, screenX - graphics.guiWidth() / 2.0F, screenY - graphics.guiHeight() / 2.0F, display);
		float scale = (style.grow() ? hover : 1.0F) * style.size();
		graphics.pose().pushMatrix();
		graphics.pose().translate(screenX, screenY);
		graphics.pose().scale(scale, scale);
		// Icon, smaller once loaded
		if (shape != null) {
			float iconScale = 1.0F - (1.0F - LOADED_SCALE) * shrink * shrink * (3.0F - 2.0F * shrink);
			graphics.pose().pushMatrix();
			graphics.pose().scale(iconScale, iconScale);
			shape.extract(graphics, alpha);
			graphics.pose().popMatrix();
		}
		extractDistance(graphics, Minecraft.getInstance().font, (int) Math.round(distance), alpha);
		graphics.pose().popMatrix();
	}

	private static float shrink(Vec3 position, boolean loaded) {
		long now = net.minecraft.util.Util.getNanos();
		SHRINK_NANOS.values().removeIf(nanos -> now - nanos > SHRINK_FORGET_NANOS);
		SHRINKS.keySet().retainAll(SHRINK_NANOS.keySet());
		Long last = SHRINK_NANOS.put(position, now);
		float[] progress = SHRINKS.computeIfAbsent(position, key -> new float[] {loaded ? 1.0F : 0.0F});
		float seconds = last == null ? 0.0F : Math.min((now - last) / 1.0E9F, MAX_FRAME_SECONDS);
		progress[0] = Mth.clamp(progress[0] + (loaded ? seconds : -seconds) / SHRINK_SECONDS, 0.0F, 1.0F);
		return progress[0];
	}

	private static float[] displayBox(GuiGraphicsExtractor graphics, Projection projection, Vec3 position) {
		SpotPath.DisplayBox box = SpotPath.displayBox(position);
		if (box == null) {
			return null;
		}
		Vector3f up = projection.up();
		float top = box.bottom() + box.height();
		float[] lower = toScreen(graphics, projection, box.anchor().add(up.x() * box.bottom(), up.y() * box.bottom(), up.z() * box.bottom()));
		float[] upper = toScreen(graphics, projection, box.anchor().add(up.x() * top, up.y() * top, up.z() * top));
		if (lower == null || upper == null) {
			return null;
		}
		float halfHeight = (float) Math.hypot(upper[0] - lower[0], upper[1] - lower[1]) / 2.0F;
		float halfWidth = box.height() > 0.0F ? halfHeight * box.width() / box.height() : 0.0F;
		return new float[] {(lower[0] + upper[0]) / 2.0F - graphics.guiWidth() / 2.0F, (lower[1] + upper[1]) / 2.0F - graphics.guiHeight() / 2.0F,
				halfWidth, halfHeight};
	}

	private static float[] toScreen(GuiGraphicsExtractor graphics, Projection projection, Vec3 position) {
		Vec3 offset = position.subtract(projection.cameraPos());
		Vector4f clip = projection.worldToClip().transform(new Vector4f((float) offset.x, (float) offset.y, (float) offset.z, 1.0F));
		if (clip.w <= 0) {
			return null;
		}
		return new float[] {(clip.x / clip.w + 1) / 2 * graphics.guiWidth(), (1 - clip.y / clip.w) / 2 * graphics.guiHeight()};
	}

	static void extractDistance(GuiGraphicsExtractor graphics, Font font, int distance, float alpha) {
		Component label = OverlayStyle.hudText(Integer.toString(distance));
		int outlineColor = OverlayStyle.withAlpha(BORDER_COLOR, alpha);
		int width = font.width(label) - 1;
		float scale = SIZE / (float) (font.width(OverlayStyle.hudText(WIDEST_DISTANCE)) - 1);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-width / 2.0F, -DIGIT_CENTER_Y);
		// Black outline
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					graphics.text(font, label, dx, dy, outlineColor, false);
				}
			}
		}
		graphics.text(font, label, 0, 0, OverlayStyle.withAlpha(TEXT_COLOR, alpha), false);
	}

	static int darken(int rgb) {
		int red = (int) ((rgb >> 16 & 0xFF) * CENTER_SHADE);
		int green = (int) ((rgb >> 8 & 0xFF) * CENTER_SHADE);
		int blue = (int) ((rgb & 0xFF) * CENTER_SHADE);
		return red << 16 | green << 8 | blue;
	}

	private static void applyViewBobbing(Matrix4f matrix, CameraEntityRenderState entity) {
		if (!entity.isPlayer) {
			return;
		}
		float walk = entity.backwardsInterpolatedWalkDistance * (float) Math.PI;
		float bob = entity.bob;
		matrix.translate(Mth.sin(walk) * bob * 0.5F, -Math.abs(Mth.cos(walk) * bob), 0.0F);
		matrix.rotateZ((float) Math.toRadians(Mth.sin(walk) * bob * 3.0F));
		matrix.rotateX((float) Math.toRadians(Math.abs(Mth.cos(walk - 0.2F) * bob) * 5.0F));
	}
}
