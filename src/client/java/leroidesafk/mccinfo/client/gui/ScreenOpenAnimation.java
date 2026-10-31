package leroidesafk.mccinfo.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.TiledBlitRenderState;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.Map;
import java.util.WeakHashMap;

public final class ScreenOpenAnimation {
	// Windows over the menu slide up and fade in
	private static final long DURATION_MILLIS = 300L;
	private static final float START_OFFSET = 12.0F;

	// Alpha and offset kept for items and pictures drawn later
	private record Frame(float alpha, float offset) {
	}

	private static final Map<Screen, Long> OPENED = new WeakHashMap<>();
	private static final Map<Object, Frame> TAGGED = new WeakHashMap<>();
	private static float alpha = 1.0F;
	private static float offset;
	private static boolean shifted;

	private ScreenOpenAnimation() {
	}

	// Start of a frame: eased progress since opening
	public static boolean begin(Screen screen, GuiGraphicsExtractor graphics) {
		if (!MenuBackdrop.drawnOver(screen)) {
			return false;
		}
		long now = Util.getMillis();
		long start = OPENED.computeIfAbsent(screen, key -> now);
		float progress = (now - start) / (float) DURATION_MILLIS;
		if (progress >= 1.0F) {
			return false;
		}
		float remaining = 1.0F - progress;
		float eased = 1.0F - remaining * remaining * remaining;
		alpha = eased;
		offset = START_OFFSET * (1.0F - eased);
		graphics.pose().pushMatrix();
		graphics.pose().translateLocal(0.0F, offset);
		shifted = true;
		return true;
	}

	public static void end(GuiGraphicsExtractor graphics) {
		if (shifted) {
			graphics.pose().popMatrix();
		}
		alpha = 1.0F;
		offset = 0.0F;
		shifted = false;
	}

	public static int mouseY(int mouseY) {
		return Math.round(mouseY - offset);
	}

	// Menu behind drawn without the slide
	public static boolean suspendShift(GuiGraphicsExtractor graphics) {
		if (!shifted) {
			return false;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translateLocal(0.0F, -offset);
		shifted = false;
		return true;
	}

	public static void resumeShift(GuiGraphicsExtractor graphics, boolean suspended) {
		if (suspended) {
			graphics.pose().popMatrix();
			shifted = true;
		}
	}

	// Fade a part of the drawing
	public static void withAlpha(float factor, Runnable action) {
		float saved = alpha;
		alpha = saved * factor;
		try {
			action.run();
		} finally {
			alpha = saved;
		}
	}

	public static float suspendAlpha() {
		float saved = alpha;
		alpha = 1.0F;
		return saved;
	}

	public static void restoreAlpha(float saved) {
		alpha = saved;
	}

	// Items and pictures keep the fade of the moment they were added
	public static void tag(Object state) {
		if (alpha < 1.0F || offset != 0.0F) {
			TAGGED.put(state, new Frame(alpha, shifted ? offset : 0.0F));
		}
	}

	public static int taggedColor(Object state, int color) {
		Frame frame = TAGGED.get(state);
		return frame == null ? color : premultiplied(color, frame.alpha());
	}

	public static Matrix3x2fc taggedPose(Object state, Matrix3x2fc pose) {
		Frame frame = TAGGED.get(state);
		return frame == null || frame.offset() == 0.0F ? pose : new Matrix3x2f(pose).translateLocal(0.0F, frame.offset());
	}

	public static int fade(int color) {
		return alpha >= 1.0F ? color : straight(color, alpha);
	}

	// Fade every rectangle and texture
	public static GuiElementRenderState fade(GuiElementRenderState element) {
		if (alpha >= 1.0F) {
			return element;
		}
		if (element instanceof ColoredRectangleRenderState rect) {
			return new ColoredRectangleRenderState(rect.pipeline(), rect.textureSetup(), rect.pose(), rect.x0(), rect.y0(), rect.x1(), rect.y1(),
					fade(rect.pipeline(), rect.col1()), fade(rect.pipeline(), rect.col2()), rect.scissorArea(), rect.bounds());
		}
		if (element instanceof BlitRenderState blit) {
			return new BlitRenderState(blit.pipeline(), blit.textureSetup(), blit.pose(), blit.x0(), blit.y0(), blit.x1(), blit.y1(),
					blit.u0(), blit.u1(), blit.v0(), blit.v1(), fade(blit.pipeline(), blit.color()), blit.scissorArea(), blit.bounds());
		}
		if (element instanceof TiledBlitRenderState tiled) {
			return new TiledBlitRenderState(tiled.pipeline(), tiled.textureSetup(), tiled.pose(), tiled.tileWidth(), tiled.tileHeight(),
					tiled.x0(), tiled.y0(), tiled.x1(), tiled.y1(), tiled.u0(), tiled.u1(), tiled.v0(), tiled.v1(),
					fade(tiled.pipeline(), tiled.color()), tiled.scissorArea(), tiled.bounds());
		}
		return element;
	}

	private static int fade(RenderPipeline pipeline, int color) {
		return pipeline == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA ? premultiplied(color, alpha) : straight(color, alpha);
	}

	private static int straight(int color, float factor) {
		return Math.round((color >>> 24) * factor) << 24 | color & 0xFFFFFF;
	}

	private static int premultiplied(int color, float factor) {
		int a = Math.round((color >>> 24) * factor);
		int r = Math.round((color >> 16 & 0xFF) * factor);
		int g = Math.round((color >> 8 & 0xFF) * factor);
		int b = Math.round((color & 0xFF) * factor);
		return a << 24 | r << 16 | g << 8 | b;
	}
}
