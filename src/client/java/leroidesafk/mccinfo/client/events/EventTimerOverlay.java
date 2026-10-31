package leroidesafk.mccinfo.client.events;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.EventsScreen;
import leroidesafk.mccinfo.client.gui.MovableOverlay;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Set;

public final class EventTimerOverlay {
	// Event schedule
	private static final int PERIOD_SECONDS = 15 * 60;
	private static final int START_OFFSET_SECONDS = 14 * 60;
	private static final int DURATION_SECONDS = 2 * 60;
	private static final Set<Integer> SOUND_AT_SECONDS = Set.of(30, 20, 10, 5);
	private static final int URGENT_SECONDS = 30;

	private static final int COUNTDOWN_SCALE = 2;

	// Layout
	private static final int WIDTH = 100;
	private static final int GAME_GAP = 3;
	private static final int HEIGHT = OverlayStyle.TITLE_HEIGHT + OverlayStyle.PADDING * 2 + OverlayStyle.FONT_HEIGHT * COUNTDOWN_SCALE + 1
			+ GAME_GAP + OverlayStyle.FONT_HEIGHT;
	private static final int GAME_COLOR = 0xFFFFAA00;
	private static final int UNKNOWN_GAME_COLOR = 0xFF808080;

	private static final int TITLE_COLOR = 0x7F9B30FF;
	private static final int URGENT_TEXT_COLOR = 0xFFFF5555;

	public static final MovableOverlay MOVABLE = new MovableOverlay() {
		@Override
		public boolean enabled() {
			return EventsScreen.halloweenTimer();
		}

		@Override
		public boolean visible() {
			return enabled() && !hidden() && remainingSeconds(System.currentTimeMillis() / 1000) >= 0;
		}

		@Override
		public int width() {
			return WIDTH;
		}

		@Override
		public int height() {
			return HEIGHT;
		}

		@Override
		public int x(int screenWidth) {
			int x = ModConfig.get().timerX;
			return OverlayStyle.clamp(x == ModConfig.UNSET ? (screenWidth - WIDTH) / 2 : x, screenWidth - WIDTH);
		}

		@Override
		public int y(int screenHeight) {
			int y = ModConfig.get().timerY;
			return OverlayStyle.clamp(y == ModConfig.UNSET ? (screenHeight - HEIGHT) / 2 : y, screenHeight - HEIGHT);
		}

		@Override
		public void setPosition(int x, int y) {
			ModConfig.get().timerX = x;
			ModConfig.get().timerY = y;
		}

		@Override
		public boolean hidden() {
			return ModConfig.get().timerHidden;
		}

		@Override
		public void setHidden(boolean hidden) {
			ModConfig.get().timerHidden = hidden;
		}

		@Override
		public void extractPreview(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
			long now = System.currentTimeMillis() / 1000;
			long start = CarnivalTracker.nextStart(now);
			extract(graphics, x, y, (int) (start - now), CarnivalTracker.gameFor(start), alpha);
		}
	};

	private static long lastSoundSecond = -1;

	private EventTimerOverlay() {
	}

	public static void register() {
		OverlayStyle.registerHud(Mccinfo.id("event_timer"), EventTimerOverlay::extractHud);
		ClientTickEvents.END_CLIENT_TICK.register(EventTimerOverlay::tick);
	}

	private static int remainingSeconds(long epochSeconds) {
		long phase = Math.floorMod(epochSeconds - START_OFFSET_SECONDS, PERIOD_SECONDS);
		return phase < DURATION_SECONDS ? (int) (DURATION_SECONDS - phase) : -1;
	}

	private static void tick(Minecraft client) {
		if (!EventsScreen.halloweenTimer() || MOVABLE.hidden() || client.player == null || !OverlayStyle.onMccIsland()) {
			return;
		}
		long now = System.currentTimeMillis() / 1000;
		int remaining = remainingSeconds(now);
		// Countdown clicks
		if (SOUND_AT_SECONDS.contains(remaining) && now != lastSoundSecond) {
			lastSoundSecond = now;
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, 1.0F, 1.0F));
		}
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!EventsScreen.halloweenTimer() || MOVABLE.hidden() || !OverlayStyle.onMccIsland() || EventsScreen.editingOverlays()) {
			return;
		}
		long now = System.currentTimeMillis() / 1000;
		int remaining = remainingSeconds(now);
		if (remaining < 0) {
			return;
		}
		extract(graphics, MOVABLE.x(graphics.guiWidth()), MOVABLE.y(graphics.guiHeight()), remaining, CarnivalTracker.gameFor(now + remaining), 1.0F);
	}

	private static void extract(GuiGraphicsExtractor graphics, int x, int y, int remainingSeconds, String game, float alpha) {
		Font font = Minecraft.getInstance().font;

		OverlayStyle.extractFrame(graphics, x, y, WIDTH, HEIGHT, TITLE_COLOR, "HALLOWEEN EVENT", alpha);

		// Countdown
		Component countdown = OverlayStyle.hudText(String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60));
		int color = remainingSeconds <= URGENT_SECONDS ? URGENT_TEXT_COLOR : OverlayStyle.TEXT_COLOR;
		float textX = x + (WIDTH - font.width(countdown) * COUNTDOWN_SCALE) / 2.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(textX, y + OverlayStyle.TITLE_HEIGHT + OverlayStyle.PADDING);
		graphics.pose().scale(COUNTDOWN_SCALE, COUNTDOWN_SCALE);
		graphics.text(font, countdown, 0, 0, OverlayStyle.withAlpha(color, alpha), false);
		graphics.pose().popMatrix();

		// Next game
		Component name = OverlayStyle.hudText(game == null ? "???" : game);
		int nameY = y + OverlayStyle.TITLE_HEIGHT + OverlayStyle.PADDING + OverlayStyle.FONT_HEIGHT * COUNTDOWN_SCALE + 1 + GAME_GAP;
		graphics.text(font, name, x + (WIDTH - font.width(name)) / 2, nameY, OverlayStyle.withAlpha(game == null ? UNKNOWN_GAME_COLOR : GAME_COLOR, alpha), false);
	}
}
