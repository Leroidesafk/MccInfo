package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class TransitionWipe {
	// Transition styles
	public enum Style {
		DIAMOND("screen.mccinfo.settings.transition_style.diamond"),
		DIAGONAL("screen.mccinfo.settings.transition_style.diagonal"),
		WAVES("screen.mccinfo.settings.transition_style.waves");

		final String translationKey;

		Style(String translationKey) {
			this.translationKey = translationKey;
		}
	}

	// Title glyph sent by the server for a transition
	private static final String GLYPH = "";
	// Timing, colors and sizes
	private static final int COLOR = 0xFF000000;
	private static final int EDGE_COLOR = 0xFF3C3C46;
	private static final long MIN_IN_MS = 250L;
	private static final long OUT_MS = 600L;
	private static final float SPREAD = 0.6F;
	private static final float DIAGONAL_IN_SPREAD = 0.72F;
	private static final float DIAGONAL_EDGE_LAG = 0.1F;
	private static final float DIAGONAL_OUT_SPREAD = 0.45F;
	private static final float CELLS_PER_HEIGHT = 8.75F;
	private static final float OVERLAP = 1.06F;
	private static final int WAVE_COLOR = 0xFF1379F8;
	private static final int WAVE_EDGE_COLOR = 0xFFFFFFFF;
	private static final float WAVE_LIFT = 1.2F;
	private static final float WAVE_BODY_OFFSET = 0.2F;

	// Covering, uncovering or idle
	private enum State { NONE, IN, OUT }

	private static State state = State.NONE;
	private static long start;
	private static long inMs = MIN_IN_MS;

	private TransitionWipe() {
	}

	// Transitions setting and utils permission
	public static boolean enabled() {
		return ModConfig.get().wipeToBlack && Permissions.has(Permissions.Perm.UTILS);
	}

	public static Style style() {
		Style style = ModConfig.get().transitionStyle;
		return style == null ? Style.DIAMOND : style;
	}

	public static Component styleName(Style style) {
		return Component.translatable(style.translationKey);
	}

	public static boolean isTransition(Component title) {
		return title != null && title.getString().equals(GLYPH);
	}

	// Replaces the transition title, true when it is hidden
	public static boolean render(GuiGraphicsExtractor graphics, Component title, int titleTime, int fadeInTime, int fadeOutTime) {
		if (!enabled()) {
			state = State.NONE;
			return false;
		}
		boolean shown = isTransition(title) && titleTime > 0;
		boolean covering = shown && titleTime > fadeOutTime;
		long now = System.currentTimeMillis();
		if (covering && state != State.IN) {
			state = State.IN;
			start = now;
			inMs = Math.max(MIN_IN_MS, fadeInTime * 50L);
		} else if (!covering && state == State.IN) {
			state = State.OUT;
			start = now;
		}
		if (state == State.OUT && now - start >= OUT_MS) {
			state = State.NONE;
		}
		if (state != State.NONE) {
			float progress = state == State.IN ? Math.min(1.0F, (now - start) / (float) inMs) : Math.min(1.0F, (now - start) / (float) OUT_MS);
			draw(graphics, progress, state == State.OUT, style());
		}
		return shown;
	}

	// Full color once covered, else waves or diamonds
	private static void draw(GuiGraphicsExtractor graphics, float progress, boolean out, Style style) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (!out && progress >= 1.0F) {
			graphics.fill(0, 0, width, height, style == Style.WAVES ? WAVE_COLOR : COLOR);
			return;
		}
		if (style == Style.WAVES) {
			graphics.nextStratum();
			waves(graphics, out ? 1.0F - progress : progress, width, height);
			return;
		}
		float cell = Math.max(9.6F, height / CELLS_PER_HEIGHT);
		float half = cell / 2.0F;
		float centerX = width / 2.0F;
		float centerY = height / 2.0F;
		int columns = (int) Math.ceil(centerX / cell) + 1;
		int rows = (int) Math.ceil(centerY / cell) + 1;
		boolean edge = style == Style.DIAGONAL && !out;
		graphics.nextStratum();
		for (int layer = edge ? 0 : 1; layer < 2; layer++) {
			for (int row = -rows; row <= rows; row++) {
				for (int column = -columns; column <= columns; column++) {
					for (int offset = 0; offset < 2; offset++) {
						float x = centerX + column * cell + offset * half;
						float y = centerY + row * cell + offset * half;
						if (x < -half || x > width + half || y < -half || y > height + half) {
							continue;
						}
						float size = size(style, progress, out, layer == 0, x, y, width, height, cell);
						if (size <= 0.0F) {
							continue;
						}
						int color = layer == 0 ? EDGE_COLOR : COLOR;
						if (style == Style.DIAMOND) {
							int alpha = Mth.clamp((int) (size * 255.0F), 0, 255);
							color = (alpha << 24) | (color & 0xFFFFFF);
						}
						diamond(graphics, x, y, size * half * OVERLAP, color);
					}
				}
			}
		}
	}

	// Diamond size from its distance to the center or the corner
	private static float size(Style style, float progress, boolean out, boolean edge, float x, float y, int width, int height, float cell) {
		float local;
		if (style == Style.DIAMOND) {
			float distance = (Math.abs(x - width / 2.0F) + Math.abs(y - height / 2.0F)) / (width / 2.0F + height / 2.0F + cell);
			local = Mth.clamp((progress - distance * SPREAD) / (1.0F - SPREAD), 0.0F, 1.0F);
		} else {
			float distance = Mth.clamp((x + y + cell) / (width + height + 2.0F * cell), 0.0F, 1.0F);
			if (out) {
				local = Mth.clamp((progress - distance * DIAGONAL_OUT_SPREAD) / (1.0F - DIAGONAL_OUT_SPREAD), 0.0F, 1.0F);
			} else {
				float window = 1.0F - DIAGONAL_IN_SPREAD - DIAGONAL_EDGE_LAG;
				float shifted = edge ? progress : progress - DIAGONAL_EDGE_LAG;
				local = Mth.clamp((shifted - distance * DIAGONAL_IN_SPREAD) / window, 0.0F, 1.0F);
			}
		}
		float eased = ease(local);
		return out ? 1.0F - eased : eased;
	}

	// Wave rising column by column with a white edge
	private static void waves(GuiGraphicsExtractor graphics, float t, int width, int height) {
		if (t <= 0.0F) {
			return;
		}
		float dt = (1.0F - Mth.cos(Mth.PI * t)) / 2.0F;
		int alpha = Mth.clamp(Math.round(t * 255.0F), 0, 255) << 24;
		int bodyColor = alpha | (WAVE_COLOR & 0xFFFFFF);
		int edgeColor = alpha | (WAVE_EDGE_COLOR & 0xFFFFFF);
		for (int column = 0; column < width; column++) {
			float u = (column + 0.5F) / width;
			float shape = (Mth.sin(2.0F * Mth.PI * u + dt) + Mth.sin(-2.0F * Mth.PI * u * 0.8F - 0.5F * dt)) / 4.0F + 0.5F;
			float front = dt * shape + dt * WAVE_LIFT;
			float thickness = 0.1F * ((Mth.sin(4.0F * Mth.PI * t * u + 0.1F * t + 0.9F) + Mth.sin(-4.0F * Mth.PI * t * u * 0.8F - 0.05F * t)) / 4.0F * 0.6F + 0.5F) + 0.1F;
			int body = Math.round(Mth.clamp(front - WAVE_BODY_OFFSET, 0.0F, 1.0F) * height);
			int edge = Math.round(Mth.clamp(front - thickness, 0.0F, 1.0F) * height);
			if (edge > body) {
				graphics.fill(column, height - edge, column + 1, height - body, edgeColor);
			}
			if (body > 0) {
				graphics.fill(column, height - body, column + 1, height, bodyColor);
			}
		}
	}

	// Square turned 45 degrees
	private static void diamond(GuiGraphicsExtractor graphics, float x, float y, float radius, int color) {
		float side = radius / Mth.SQRT_OF_TWO;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate(Mth.PI / 4.0F);
		graphics.pose().scale(side, side);
		graphics.fill(-1, -1, 1, 1, color);
		graphics.pose().popMatrix();
	}

	// Cubic ease in and out
	private static float ease(float t) {
		return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0F) / 2.0F;
	}
}
