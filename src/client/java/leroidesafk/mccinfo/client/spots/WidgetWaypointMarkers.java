package leroidesafk.mccinfo.client.spots;

import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import java.util.List;
import java.util.function.Supplier;

public final class WidgetWaypointMarkers {
	public record Marker(String id, Vec3 position, Identifier icon, boolean joinable, float vanish) {
	}

	// Vanish: distance fades, then the icon grows and shrinks
	public static final long VANISH_MILLIS = 550L;
	private static final float TEXT_FADE = 150.0F / VANISH_MILLIS;
	private static final float GROW = 0.3F;
	private static final float GROW_SCALE = 1.2F;
	// Path color
	private static final int COLOR = 0xD536DB;
	// Size and height above the waypoint
	private static final double MARKER_HEIGHT = 1.5;
	private static final double MIN_PERSPECTIVE_DISTANCE = 1.0;
	private static final float BASE_SIZE = 1.875F;
	private static final int ICON_SIZE = 10;
	private static final int ICON_TEXTURE = 16;
	private static final float HIT_SIZE = 13.5F;
	private static final long LOOK_TIMEOUT_MILLIS = 150L;

	private static Supplier<List<Marker>> source = List::of;
	private static String looked;
	private static long lookedAt;

	private WidgetWaypointMarkers() {
	}

	public static void register(Supplier<List<Marker>> markers) {
		source = markers;
		HudElementRegistry.addFirst(Mccinfo.id("widget_waypoints"), WidgetWaypointMarkers::extractHud);
	}

	public static String looked() {
		return Util.getMillis() - lookedAt <= LOOK_TIMEOUT_MILLIS ? looked : null;
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		List<Marker> markers = source.get();
		if (markers.isEmpty()) {
			return;
		}
		SpotMarkers.Projection projection = SpotMarkers.projection();
		if (projection == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float centerX = graphics.guiWidth() / 2.0F;
		float centerY = graphics.guiHeight() / 2.0F;
		String best = null;
		float bestDistance = Float.MAX_VALUE;
		for (Marker marker : markers) {
			Vec3 point = marker.position().add(0.0, MARKER_HEIGHT, 0.0);
			// Vanishing waypoints drawn apart
			if (marker.vanish() > 0.0F) {
				vanishing(graphics, projection, marker, point, size(projection, point));
				continue;
			}
			// Same code as the spots, no fade and no hover growth
			SpotPath.Target target = marker.id() != null ? new SpotPath.Target(marker.position(), COLOR, marker.icon()) : null;
			float size = size(projection, point);
			SpotMarkers.extract(graphics, projection, point, (g, alpha) -> shape(g, marker.icon(), alpha, 1.0F), target, false,
					new SpotMarkers.Style(false, false, size));
			// Aim within the join distance
			if (marker.joinable() && client.gui.screen() == null) {
				float[] screen = toScreen(graphics, projection, point);
				if (screen != null) {
					float distance = Math.abs(screen[0] - centerX) + Math.abs(screen[1] - centerY);
					float hit = HIT_SIZE * size / BASE_SIZE;
					if (Math.abs(screen[0] - centerX) <= hit && Math.abs(screen[1] - centerY) <= hit && distance < bestDistance) {
						bestDistance = distance;
						best = marker.id();
					}
				}
			}
		}
		looked = best;
		if (best != null) {
			lookedAt = Util.getMillis();
		}
	}

	private static void vanishing(GuiGraphicsExtractor graphics, SpotMarkers.Projection projection, Marker marker, Vec3 point, float size) {
		float textAlpha = 1.0F - Math.clamp(marker.vanish() / TEXT_FADE, 0.0F, 1.0F);
		if (textAlpha > 0.0F) {
			ScreenOpenAnimation.withAlpha(textAlpha, () -> {
				float[] screen = toScreen(graphics, projection, point);
				if (screen != null) {
					graphics.pose().pushMatrix();
					graphics.pose().translate(screen[0], screen[1]);
					graphics.pose().scale(size, size);
					SpotMarkers.extractDistance(graphics, Minecraft.getInstance().font, (int) Math.round(point.distanceTo(projection.cameraPos())), 1.0F);
					graphics.pose().popMatrix();
				}
			});
		}
		float scale = vanishScale(marker.vanish());
		float[] screen = toScreen(graphics, projection, point);
		if (scale <= 0.01F || screen == null) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(screen[0], screen[1]);
		shape(graphics, marker.icon(), 1.0F, scale * size);
		graphics.pose().popMatrix();
	}

	// Fixed size, then real-world proportions inside the join distance
	private static float size(SpotMarkers.Projection projection, Vec3 point) {
		double distance = point.distanceTo(projection.cameraPos());
		double join = leroidesafk.mccinfo.client.widget.WidgetWaypoints.JOIN_DISTANCE;
		return distance >= join ? BASE_SIZE : (float) (BASE_SIZE * join / Math.max(distance, MIN_PERSPECTIVE_DISTANCE));
	}

	private static float[] toScreen(GuiGraphicsExtractor graphics, SpotMarkers.Projection projection, Vec3 point) {
		Vec3 offset = point.subtract(projection.cameraPos());
		Vector4f clip = projection.worldToClip().transform(new Vector4f((float) offset.x, (float) offset.y, (float) offset.z, 1.0F));
		if (clip.w <= 0) {
			return null;
		}
		float x = clip.x / clip.w;
		float y = clip.y / clip.w;
		if (Math.abs(x) > 1 || Math.abs(y) > 1) {
			return null;
		}
		return new float[] {(x + 1) / 2 * graphics.guiWidth(), (1 - y) / 2 * graphics.guiHeight()};
	}

	private static float ease(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private static float vanishScale(float vanish) {
		float t = Math.clamp((vanish - TEXT_FADE) / (1.0F - TEXT_FADE), 0.0F, 1.0F);
		if (t < GROW) {
			return 1.0F + (GROW_SCALE - 1.0F) * ease(t / GROW);
		}
		return GROW_SCALE * (1.0F - ease((t - GROW) / (1.0F - GROW)));
	}

	private static void shape(GuiGraphicsExtractor graphics, Identifier icon, float alpha, float scale) {
		if (icon == null) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-ICON_SIZE / 2.0F, -ICON_SIZE - 1.0F);
		graphics.pose().scale(ICON_SIZE / (float) ICON_TEXTURE, ICON_SIZE / (float) ICON_TEXTURE);
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0.0F, 0.0F, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE,
				OverlayStyle.withAlpha(0xFFFFFFFF, alpha));
		graphics.pose().popMatrix();
	}
}
