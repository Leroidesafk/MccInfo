package leroidesafk.mccinfo.client.widget;

import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.gui.ScreenOpenAnimation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public final class RpsScreen extends Screen {
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int LINE_HEIGHT = 12;

	// Duel intro
	private static final float SLIDE_MILLIS = 300.0F;
	private static final float DROP_MILLIS = 350.0F;
	private static final float HOLD_MILLIS = 600.0F;
	private static final float SHRINK_START = SLIDE_MILLIS + DROP_MILLIS + HOLD_MILLIS;
	private static final float SHRINK_MILLIS = 600.0F;
	private static final float FADE_MILLIS = 300.0F;
	private static final float INTRO_SCALE = 3.0F;
	private static final int BANNER_TOP = 8;
	private static final int ROUND_GAP = 6;
	private static final int BUBBLE_GAP = 6;
	private static final int BUBBLE_COLOR = 0xFFFF9B44;
	private static final int BUBBLE_SIZE = 14;

	// Choice wheel
	private static final int INNER_RADIUS = 32;
	private static final int OUTER_RADIUS = 72;
	private static final float GAP_WIDTH = 4.0F;
	private static final int SEGMENT_COLOR = 0xFF262626;
	private static final int SEGMENT_HOVER_COLOR = 0xFF4A4A4A;
	private static final float[] SEGMENT_ANGLES = {-90.0F, 30.0F, 150.0F};
	private static final int ICON_SIZE = 16;
	private static final float ICON_SCALE = 1.5F;
	private static final float ICON_HOVER_SCALE = 2.2F;
	private static final float SCALE_TIME = 60.0F;
	private static final int URGENT_SECONDS = 3;

	// Round result
	private static final int RESULT_OFFSET = 70;
	private static final float RESULT_ICON_SCALE = 3.0F;

	private final float[] iconScale = {ICON_SCALE, ICON_SCALE, ICON_SCALE};
	private long lastFrameMillis = Util.getMillis();
	private final long openedAt;

	public RpsScreen() {
		super(Component.translatable("screen.mccinfo.rps.title"));
		RockPaperScissors.Match match = RockPaperScissors.current();
		long now = Util.getMillis();
		this.openedAt = match != null && match.round > 1 ? now - (long) (SHRINK_START + SHRINK_MILLIS + FADE_MILLIS) : now;
	}

	private int centerX() {
		return this.width / 2;
	}

	private int centerY() {
		return this.height / 2 + 12;
	}

	private int segmentAt(double mouseX, double mouseY) {
		double dx = mouseX - centerX();
		double dy = mouseY - centerY();
		double distance = Math.hypot(dx, dy);
		if (distance < INNER_RADIUS || distance > OUTER_RADIUS) {
			return -1;
		}
		double angle = Math.toDegrees(Math.atan2(dy, dx));
		for (int i = 0; i < SEGMENT_ANGLES.length; i++) {
			double difference = ((angle - SEGMENT_ANGLES[i]) % 360.0 + 540.0) % 360.0 - 180.0;
			if (Math.abs(difference) <= 60.0 && distance * Math.sin(Math.toRadians(60.0 - Math.abs(difference))) >= GAP_WIDTH / 2.0) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		RockPaperScissors.Match match = RockPaperScissors.current();
		if (match == null) {
			return;
		}
		int centerX = centerX();
		float elapsed = Util.getMillis() - this.openedAt;
		String me = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : "?";
		// Intro timeline
		float slide = easeOutCubic(progress(elapsed, 0.0F, SLIDE_MILLIS));
		float drop = elapsed < SLIDE_MILLIS ? 0.0F : easeOutBack(progress(elapsed, SLIDE_MILLIS, DROP_MILLIS));
		float shrink = easeInOutBack(progress(elapsed, SHRINK_START, SHRINK_MILLIS));
		float scale = Mth.lerp(shrink, INTRO_SCALE, 1.0F);
		float bannerY = Mth.lerp(shrink, this.height / 2, BANNER_TOP + DuelBanner.HEIGHT / 2);
		DuelBanner.extract(graphics, this.font, me, match.opponent, centerX, bannerY, scale, slide, drop, this.width);

		float fade = progress(elapsed, SHRINK_START + SHRINK_MILLIS, FADE_MILLIS);
		if (fade <= 0.0F) {
			return;
		}
		// Scores, round and wheel fade in
		ScreenOpenAnimation.withAlpha(fade, () -> {
			int half = Math.round(DuelBanner.halfWidth(this.font, me, match.opponent));
			bubble(graphics, match.myWins, centerX - half - BUBBLE_GAP - BUBBLE_SIZE);
			bubble(graphics, match.theirWins, centerX + half + BUBBLE_GAP);
			centered(graphics, Component.translatable("screen.mccinfo.rps.round", match.round, match.bet).withStyle(ChatFormatting.GRAY),
					centerX, BANNER_TOP + DuelBanner.HEIGHT + ROUND_GAP);
			if (match.phase == RockPaperScissors.Phase.CHOOSING) {
				extractWheel(graphics, match, mouseX, mouseY);
			} else {
				extractChoices(graphics, match);
			}
		});
	}

	private void bubble(GuiGraphicsExtractor graphics, int value, int x) {
		int y = BANNER_TOP + (DuelBanner.HEIGHT - BUBBLE_SIZE) / 2;
		OverlayStyle.fillRounded(graphics, x, y, BUBBLE_SIZE, BUBBLE_SIZE, BUBBLE_COLOR);
		Component text = DuelBanner.hud(Component.literal(String.valueOf(value)));
		graphics.text(this.font, text, x + (BUBBLE_SIZE - (this.font.width(text) - 1)) / 2, y + DuelBanner.digitTop(BUBBLE_SIZE), TEXT_COLOR, false);
	}

	private void centered(GuiGraphicsExtractor graphics, Component text, int x, int y) {
		graphics.centeredText(this.font, DuelBanner.hud(text), x, y, TEXT_COLOR);
	}

	private boolean introDone() {
		return Util.getMillis() - this.openedAt >= SHRINK_START + SHRINK_MILLIS;
	}

	private static float progress(float elapsed, float start, float duration) {
		return Mth.clamp((elapsed - start) / duration, 0.0F, 1.0F);
	}

	private static float easeOutCubic(float t) {
		float inverse = 1.0F - t;
		return 1.0F - inverse * inverse * inverse;
	}

	private static float easeOutBack(float t) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float u = t - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	private static float easeInOutBack(float t) {
		float c2 = 1.70158F * 1.525F;
		return t < 0.5F
				? (float) (Math.pow(2.0F * t, 2.0F) * ((c2 + 1.0F) * 2.0F * t - c2)) / 2.0F
				: (float) (Math.pow(2.0F * t - 2.0F, 2.0F) * ((c2 + 1.0F) * (t * 2.0F - 2.0F) + c2) + 2.0F) / 2.0F;
	}

	private void extractWheel(GuiGraphicsExtractor graphics, RockPaperScissors.Match match, int mouseX, int mouseY) {
		int centerX = centerX();
		int centerY = centerY();
		boolean open = match.remainingMillis() > 0;
		int hovered = open ? segmentAt(mouseX, mouseY) : -1;
		long now = Util.getMillis();
		float progress = 1.0F - (float) Math.exp(-(now - this.lastFrameMillis) / SCALE_TIME);
		this.lastFrameMillis = now;

		RockPaperScissors.Choice[] choices = RockPaperScissors.Choice.values();
		for (int i = 0; i < choices.length; i++) {
			extractSegment(graphics, centerX, centerY, i, i == hovered ? SEGMENT_HOVER_COLOR : SEGMENT_COLOR);

			// Icon grows on hover
			float target = i == hovered ? ICON_HOVER_SCALE : ICON_SCALE;
			this.iconScale[i] += (target - this.iconScale[i]) * progress;
			double radians = Math.toRadians(SEGMENT_ANGLES[i]);
			float middle = (INNER_RADIUS + OUTER_RADIUS) / 2.0F;
			extractIcon(graphics, choices[i], centerX + (float) Math.cos(radians) * middle, centerY + (float) Math.sin(radians) * middle, this.iconScale[i]);
		}

		// Time left
		if (open) {
			int seconds = (int) Math.ceil(match.remainingMillis() / 1000.0);
			Component time = Component.literal(String.valueOf(seconds)).withStyle(seconds <= URGENT_SECONDS ? ChatFormatting.RED : ChatFormatting.WHITE);
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX, centerY);
			graphics.pose().scale(2.0F, 2.0F);
			centered(graphics, time, 0, -this.font.lineHeight / 2);
			graphics.pose().popMatrix();
		} else {
			centered(graphics, Component.translatable("screen.mccinfo.rps.time_up").withStyle(ChatFormatting.RED),
					centerX, centerY + OUTER_RADIUS + 10);
		}
	}

	private void extractSegment(GuiGraphicsExtractor graphics, int centerX, int centerY, int index, int color) {
		double scale = this.minecraft.getWindow().getGuiScale();
		double outer = OUTER_RADIUS * scale;
		double inner = INNER_RADIUS * scale;
		double halfGap = GAP_WIDTH / 2.0 * scale;
		double first = Math.toRadians(SEGMENT_ANGLES[index] - 60.0);
		double last = Math.toRadians(SEGMENT_ANGLES[index] + 60.0);
		double[][] normals = {{-Math.sin(first), Math.cos(first)}, {Math.sin(last), -Math.cos(last)}};

		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale((float) (1.0 / scale), (float) (1.0 / scale));
		// Ring slice, row by row
		for (int row = (int) Math.floor(-outer); row < Math.ceil(outer); row++) {
			double y = row + 0.5;
			if (Math.abs(y) >= outer) {
				continue;
			}
			double outerX = Math.sqrt(outer * outer - y * y);
			double low = -outerX;
			double high = outerX;
			boolean empty = false;
			// Cut the gaps
			for (double[] normal : normals) {
				double bound = halfGap - normal[1] * y;
				if (Math.abs(normal[0]) < 1.0E-9) {
					empty |= bound > 0;
				} else if (normal[0] > 0) {
					low = Math.max(low, bound / normal[0]);
				} else {
					high = Math.min(high, bound / normal[0]);
				}
			}
			if (empty) {
				continue;
			}
			// Hole in the middle
			if (Math.abs(y) < inner) {
				double innerX = Math.sqrt(inner * inner - y * y);
				fillRow(graphics, low, Math.min(high, -innerX), row, color);
				fillRow(graphics, Math.max(low, innerX), high, row, color);
			} else {
				fillRow(graphics, low, high, row, color);
			}
		}
		graphics.pose().popMatrix();
	}

	private static void fillRow(GuiGraphicsExtractor graphics, double from, double to, int row, int color) {
		int start = (int) Math.round(from);
		int end = (int) Math.round(to);
		if (end > start) {
			graphics.fill(start, row, end, row + 1, color);
		}
	}

	private void extractChoices(GuiGraphicsExtractor graphics, RockPaperScissors.Match match) {
		int centerX = centerX();
		int centerY = centerY();
		int labelY = centerY - (int) (ICON_SIZE * RESULT_ICON_SCALE / 2) - LINE_HEIGHT - 4;
		centered(graphics, Component.translatable("screen.mccinfo.rps.you"), centerX - RESULT_OFFSET, labelY);
		centered(graphics, Component.literal(match.opponent).withStyle(ChatFormatting.YELLOW), centerX + RESULT_OFFSET, labelY);
		Component tooLate = Component.translatable("screen.mccinfo.rps.too_late").withStyle(ChatFormatting.RED);
		// Both choices
		if (match.mine != null) {
			extractIcon(graphics, match.mine, centerX - RESULT_OFFSET, centerY, RESULT_ICON_SCALE);
		} else if (match.phase == RockPaperScissors.Phase.RESULT) {
			centered(graphics, tooLate, centerX - RESULT_OFFSET, centerY - this.font.lineHeight / 2);
		}
		if (match.theirs != null) {
			extractIcon(graphics, match.theirs, centerX + RESULT_OFFSET, centerY, RESULT_ICON_SCALE);
		} else if (match.phase == RockPaperScissors.Phase.RESULT) {
			centered(graphics, tooLate, centerX + RESULT_OFFSET, centerY - this.font.lineHeight / 2);
		} else if (match.phase != RockPaperScissors.Phase.ENDED) {
			String dots = ".".repeat((int) (Util.getMillis() / 400 % 4));
			centered(graphics, Component.literal("Waiting" + dots).withStyle(ChatFormatting.GRAY), centerX + RESULT_OFFSET,
					centerY - this.font.lineHeight / 2);
		}
		centered(graphics, Component.literal("VS").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), centerX, centerY - this.font.lineHeight / 2);

		// Round result or final banner
		int textY = centerY + (int) (ICON_SIZE * RESULT_ICON_SCALE / 2) + 12;
		if (match.phase == RockPaperScissors.Phase.RESULT && match.outcome != null) {
			Component outcome = switch (match.outcome) {
				case WIN -> Component.translatable("screen.mccinfo.rps.round_win").withStyle(ChatFormatting.GREEN);
				case LOSE -> Component.translatable("screen.mccinfo.rps.round_lose").withStyle(ChatFormatting.RED);
				default -> Component.translatable("screen.mccinfo.rps.round_draw").withStyle(ChatFormatting.YELLOW);
			};
			centered(graphics, outcome, centerX, textY);
		} else if (match.phase == RockPaperScissors.Phase.ENDED && match.outcome != null) {
			Component banner = switch (match.outcome) {
				case WIN -> Component.translatable("screen.mccinfo.rps.victory", match.bet).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
				case LOSE -> Component.translatable("screen.mccinfo.rps.defeat", match.bet).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
				default -> Component.translatable("screen.mccinfo.rps.cancelled").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
			};
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX, textY);
			graphics.pose().scale(2.0F, 2.0F);
			centered(graphics, banner, 0, 0);
			graphics.pose().popMatrix();
			String reason = match.endReason == null ? "score" : match.endReason;
			if (!"score".equals(reason)) {
				centered(graphics, Component.translatable("screen.mccinfo.rps.reason." + reason).withStyle(ChatFormatting.GRAY),
						centerX, textY + LINE_HEIGHT * 2);
			}
		}
	}

	private static void extractIcon(GuiGraphicsExtractor graphics, RockPaperScissors.Choice choice, float x, float y, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, choice.texture, -ICON_SIZE / 2, -ICON_SIZE / 2, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
		graphics.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		RockPaperScissors.Match match = RockPaperScissors.current();
		if (match != null && introDone() && match.phase == RockPaperScissors.Phase.CHOOSING && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			int segment = segmentAt(event.x(), event.y());
			if (segment >= 0) {
				RockPaperScissors.choose(RockPaperScissors.Choice.values()[segment]);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void removed() {
		super.removed();
		// Leaving forfeits
		if (RockPaperScissors.playing()) {
			RockPaperScissors.forfeit();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
