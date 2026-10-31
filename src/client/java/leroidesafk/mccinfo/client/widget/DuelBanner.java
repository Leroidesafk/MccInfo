package leroidesafk.mccinfo.client.widget;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class DuelBanner {
	public static final int HEIGHT = 20;
	private static final float UNIT = HEIGHT / 10.0F;
	private static final float SLANT = HEIGHT * 0.3F;
	private static final int HEAD = 12;
	private static final int HEAD_GAP = 4;
	private static final float SIDE_PADDING = UNIT * 3;
	private static final int ICON_CLEARANCE = 4;
	private static final int HUD_GLYPH_TOP = 2;
	private static final int HUD_GLYPH_HEIGHT = 5;
	private static final int ICON_PIXELS = 10;
	private static final Identifier ICON = Mccinfo.id("textures/gui/utils/duel.png");

	// Side colors
	private static final int BLUE_HIGH = 0xFF347CDE;
	private static final int BLUE_LOW = 0xFF2E57C0;
	private static final int BLUE_CORNER = 0xFF2828AA;
	private static final int RED_HIGH = 0xFFDE4834;
	private static final int RED_LOW = 0xFFAA293C;
	private static final int RED_CORNER = 0xFF931140;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int SHADOW_OUTSIDE = 0xFF3F4D54;

	private static final FontDescription HUD_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcc", "hud"));
	private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();
	private static boolean[][] iconMask;

	private DuelBanner() {
	}

	public static Component hud(Component text) {
		return text.copy().withStyle(style -> style.withFont(HUD_FONT));
	}

	public static float halfWidth(Font font, String left, String right) {
		int name = Math.max(font.width(hud(Component.literal(left))), font.width(hud(Component.literal(right))));
		return SIDE_PADDING + HEAD + HEAD_GAP + name + ICON_CLEARANCE + ICON_PIXELS * UNIT / 2.0F;
	}

	public static void extract(GuiGraphicsExtractor graphics, Font font, String left, String right, float centerX, float centerY, float scale,
			float slide, float iconDrop, int screenWidth) {
		float half = halfWidth(font, left, right);
		float travel = screenWidth / scale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(Math.round(centerX), Math.round(centerY));
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(0.0F, -HEIGHT / 2.0F);
		// Halves slide in from the sides
		float offset = (1.0F - slide) * travel;
		graphics.pose().pushMatrix();
		graphics.pose().translate(-offset, 0.0F);
		side(graphics, font, left, half, false);
		graphics.pose().popMatrix();
		graphics.pose().pushMatrix();
		graphics.pose().translate(offset, 0.0F);
		side(graphics, font, right, half, true);
		graphics.pose().popMatrix();
		// VS drops from the top
		if (iconDrop > 0.0F) {
			float drop = (1.0F - iconDrop) * (centerY / scale + HEIGHT);
			icon(graphics, -drop, half);
		}
		graphics.pose().popMatrix();
	}

	private static void side(GuiGraphicsExtractor graphics, Font font, String name, float half, boolean right) {
		int high = right ? RED_HIGH : BLUE_HIGH;
		int low = right ? RED_LOW : BLUE_LOW;
		int corner = right ? RED_CORNER : BLUE_CORNER;
		float middle = HEIGHT / 2.0F;
		float edge = right ? half : -half;
		float inner = right ? edge - UNIT : edge + UNIT;
		// Top and bottom halves up to the cut
		quad(graphics, high, inner, 0.0F, cutX(0.0F), 0.0F, cutX(middle), middle, inner, middle);
		quad(graphics, low, inner, middle, cutX(middle), middle, cutX(HEIGHT), HEIGHT, inner, HEIGHT);
		// Outer edge with cut corners
		float outer = Math.min(edge, inner);
		float outerEnd = Math.max(edge, inner);
		rect(graphics, outer, UNIT * 2, outerEnd, middle, high);
		rect(graphics, outer, UNIT, outerEnd, UNIT * 2, low);
		rect(graphics, outer, middle, outerEnd, HEIGHT - UNIT * 2, low);
		rect(graphics, outer, HEIGHT - UNIT * 2, outerEnd, HEIGHT - UNIT, corner);
		// Cells next to the corners
		float next = right ? inner - UNIT : inner + UNIT;
		float cornerStart = Math.min(inner, next);
		float cornerEnd = Math.max(inner, next);
		rect(graphics, cornerStart, 0.0F, cornerEnd, UNIT, low);
		rect(graphics, cornerStart, HEIGHT - UNIT, cornerEnd, HEIGHT, corner);

		// Head and name
		float headX = right ? half - SIDE_PADDING - HEAD : -half + SIDE_PADDING;
		float headY = (HEIGHT - HEAD) / 2.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(headX, headY);
		PlayerFaceExtractor.extractRenderState(graphics, PROFILES.computeIfAbsent(name, ResolvableProfile::createUnresolved), 0, 0, HEAD);
		graphics.pose().popMatrix();
		Component text = hud(Component.literal(name));
		float textX = right ? headX - HEAD_GAP - font.width(text) : headX + HEAD + HEAD_GAP;
		graphics.pose().pushMatrix();
		graphics.pose().translate(Math.round(textX), textTop(HEIGHT));
		graphics.text(font, text, 0, 0, TEXT_COLOR, true);
		graphics.pose().popMatrix();
	}

	public static int textTop(int height) {
		return (height - HUD_GLYPH_HEIGHT - 1) / 2 - HUD_GLYPH_TOP;
	}

	public static int digitTop(int height) {
		return (height - HUD_GLYPH_HEIGHT) / 2 - HUD_GLYPH_TOP;
	}

	private static int shadowColor(float x, float y, float half) {
		if (y < 0.0F || y >= HEIGHT || Math.abs(x) >= half) {
			return SHADOW_OUTSIDE;
		}
		return x >= cutX(y) ? RED_CORNER : BLUE_CORNER;
	}

	private static float cutX(float y) {
		return SLANT / 2.0F - y / HEIGHT * SLANT;
	}

	private static void icon(GuiGraphicsExtractor graphics, float drop, float half) {
		boolean[][] mask = mask();
		float size = ICON_PIXELS * UNIT;
		float left = -size / 2.0F;
		float top = drop + (HEIGHT - size) / 2.0F;
		// Shadow one pixel lower, colored by what is under it
		if (mask != null) {
			for (int py = 0; py < ICON_PIXELS; py++) {
				for (int px = 0; px < ICON_PIXELS; px++) {
					if (!mask[py][px]) {
						continue;
					}
					float x0 = left + px * UNIT;
					float y0 = top + (py + 1) * UNIT;
					rect(graphics, x0, y0, x0 + UNIT, y0 + UNIT, shadowColor(x0 + UNIT / 2.0F, y0 + UNIT / 2.0F, half));
				}
			}
		}
		// VS on top
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(UNIT, UNIT);
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, 0, 0, 0.0F, 0.0F, ICON_PIXELS, ICON_PIXELS, ICON_PIXELS, ICON_PIXELS);
		graphics.pose().popMatrix();
	}

	private static boolean[][] mask() {
		if (iconMask != null) {
			return iconMask;
		}
		try (InputStream stream = Minecraft.getInstance().getResourceManager().open(ICON); NativeImage image = NativeImage.read(stream)) {
			boolean[][] mask = new boolean[ICON_PIXELS][ICON_PIXELS];
			for (int y = 0; y < Math.min(ICON_PIXELS, image.getHeight()); y++) {
				for (int x = 0; x < Math.min(ICON_PIXELS, image.getWidth()); x++) {
					mask[y][x] = (image.getPixel(x, y) >>> 24) > 0;
				}
			}
			iconMask = mask;
		} catch (Exception e) {
			Mccinfo.LOGGER.warn("Could not read the duel icon", e);
			iconMask = new boolean[ICON_PIXELS][ICON_PIXELS];
		}
		return iconMask;
	}

	private static void rect(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color) {
		quad(graphics, color, x0, y0, x1, y0, x1, y1, x0, y1);
	}

	private static void quad(GuiGraphicsExtractor graphics, int color, float... points) {
		// Same winding for every quad, or the game skips it
		float area = 0.0F;
		for (int i = 0; i < 8; i += 2) {
			int next = (i + 2) % 8;
			area += points[i] * points[next + 1] - points[next] * points[i + 1];
		}
		if (area > 0.0F) {
			points = new float[] {points[6], points[7], points[4], points[5], points[2], points[3], points[0], points[1]};
		}
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
		ScreenRectangle bounds = new ScreenRectangle(x, y, Math.max(1, Mth.ceil(right) - x), Math.max(1, Mth.ceil(bottom) - y)).transformMaxBounds(pose);
		graphics.guiRenderState.addGuiElement(new Quad(pose, points.clone(), ScreenOpenAnimation.fade(color), scissorArea,
				scissorArea != null ? scissorArea.intersection(bounds) : bounds));
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
}
