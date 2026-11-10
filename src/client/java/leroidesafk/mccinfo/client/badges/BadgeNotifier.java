package leroidesafk.mccinfo.client.badges;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.chat.MccApi;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// Toast when a game with badges to do gets popular
public final class BadgeNotifier {
	public static final String MODE_SUGGESTED = "suggested";
	public static final String MODE_GAMES = "games";
	private static final long CHECK_MILLIS = 60_000L;
	private static final long BADGE_REFRESH_MILLIS = 5 * 60_000L;
	private static final String POPULARITY_SELECTION = "popularity(queueType: \"%s\")";
	private static final java.util.regex.Pattern QUEUE = java.util.regex.Pattern.compile("[a-z0-9_]{1,64}");
	private static final float SOUND_PITCH = 1.5F;

	// Queues already notified, last popularity of each queue
	private static final Set<String> REACHED = new HashSet<>();
	private static final Map<String, String> CURRENT = new java.util.concurrent.ConcurrentHashMap<>();
	private static long lastFullCheck;
	private static long lastCheck;
	private static boolean checking;

	private BadgeNotifier() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	// Current popularity of each queue, for the Badge Tracker
	static String popularity(String queue) {
		return queue == null ? null : CURRENT.get(queue);
	}

	static void refreshAll() {
		long now = System.currentTimeMillis();
		if (!Badges.allowed() || !MccApi.ready() || now - lastFullCheck < CHECK_MILLIS) {
			return;
		}
		lastFullCheck = now;
		for (Badges.Mode mode : Badges.modes()) {
			String queue = mode.queue();
			if (queue != null && QUEUE.matcher(queue).matches()) {
				MccApi.query(String.format(POPULARITY_SELECTION, queue)).thenAccept(reply -> {
					if (reply != null && reply.answered() && reply.value().isJsonPrimitive()) {
						CURRENT.put(queue, reply.value().getAsString().toLowerCase(Locale.ROOT));
					}
				});
			}
		}
	}

	private static boolean active() {
		return ModConfig.get().badgeNotify && Badges.allowed() && MccApi.state() == MccApi.KeyState.VALID && MccIsland.isConnected();
	}

	// Popularity check every minute
	private static void tick() {
		if (!active()) {
			REACHED.clear();
			return;
		}
		Badges.refresh(BADGE_REFRESH_MILLIS);
		long now = System.currentTimeMillis();
		if (checking || now - lastCheck < CHECK_MILLIS) {
			return;
		}
		Map<String, Badges.Mode> queues = targets();
		if (queues.isEmpty()) {
			return;
		}
		lastCheck = now;
		checking = true;
		Map<String, CompletableFuture<MccApi.Reply>> replies = new LinkedHashMap<>();
		for (String queue : queues.keySet()) {
			replies.put(queue, MccApi.query(String.format(POPULARITY_SELECTION, queue)));
		}
		CompletableFuture.allOf(replies.values().toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
			checking = false;
			if (!active()) {
				return;
			}
			for (Map.Entry<String, CompletableFuture<MccApi.Reply>> entry : replies.entrySet()) {
				MccApi.Reply reply = entry.getValue().getNow(null);
				if (reply == null || !reply.answered() || !reply.value().isJsonPrimitive()) {
					continue;
				}
				update(entry.getKey(), queues.get(entry.getKey()), reply.value().getAsString());
			}
		}));
	}

	// Queues of the chosen games or of the first suggested badge
	private static Map<String, Badges.Mode> targets() {
		ModConfig config = ModConfig.get();
		List<Badges.Mode> modes = new ArrayList<>();
		if (MODE_GAMES.equals(config.badgeNotifyMode)) {
			for (String id : config.badgeNotifyGames) {
				Badges.Mode mode = Badges.mode(id);
				if (mode != null) {
					modes.add(mode);
				}
			}
		} else {
			List<Badges.Badge> top = Badges.suggested(1);
			if (!top.isEmpty()) {
				modes.addAll(Badges.modesOf(top.get(0)));
			}
		}
		Map<String, Badges.Mode> queues = new LinkedHashMap<>();
		for (Badges.Mode mode : modes) {
			if (mode.queue() != null && QUEUE.matcher(mode.queue()).matches()) {
				queues.put(mode.queue(), mode);
			}
		}
		REACHED.retainAll(queues.keySet());
		return queues;
	}

	// Notify once when a queue reaches the minimum popularity
	private static void update(String queue, Badges.Mode mode, String popularity) {
		List<String> levels = Badges.popularityLevels();
		CURRENT.put(queue, popularity.toLowerCase(Locale.ROOT));
		int level = levels.indexOf(popularity.toLowerCase(Locale.ROOT));
		int threshold = levels.indexOf(ModConfig.get().badgeNotifyPopularity);
		if (level < 0 || threshold < 0) {
			return;
		}
		threshold = Math.max(threshold, Math.min(1, levels.size() - 1));
		if (level < threshold) {
			REACHED.remove(queue);
			return;
		}
		if (REACHED.add(queue)) {
			notify(mode, popularity);
		}
	}

	// Toast and sound, with the first suggested badge of the mode
	private static void notify(Badges.Mode mode, String popularity) {
		Badges.Badge badge = null;
		for (Badges.Badge suggested : Badges.suggested(Integer.MAX_VALUE)) {
			if (Badges.modesOf(suggested).contains(mode)) {
				badge = suggested;
				break;
			}
		}
		Minecraft client = Minecraft.getInstance();
		Component title = Component.translatable("toast.mccinfo.badges.title", mode.name(), Badges.levelName(popularity.toLowerCase(Locale.ROOT)));
		Component line = badge == null ? Component.translatable("screen.mccinfo.badges.title") : line(badge);
		client.gui.toastManager().addToast(new PopularityToast(Badges.modeIcon(mode), title, line));
		if (ModConfig.get().badgeNotifySound) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, SOUND_PITCH));
		}
	}

	private static Component line(Badges.Badge badge) {
		Integer left = Badges.gamesLeft(badge);
		return left == null ? Component.literal(badge.name()) : Component.translatable("toast.mccinfo.badges.line", badge.name(), left);
	}

	static String words(String key) {
		StringBuilder text = new StringBuilder();
		for (String word : key.split("_")) {
			if (!word.isEmpty()) {
				text.append(text.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return text.toString();
	}

	// Toast with the mode icon
	private static final class PopularityToast implements Toast {
		private static final int MIN_WIDTH = 160;
		private static final int MAX_WIDTH = 300;
		private static final int TEXT_RIGHT = 8;
		private static final int HEIGHT = 26;
		private static final int BACKGROUND_COLOR = 0xC0111111;
		private static final int TEXT_LEFT = 26;
		private static final int TITLE_COLOR = 0xFFFFD24A;
		private static final long DISPLAY_MILLIS = 8000L;

		private final ItemStack icon;
		private final Component title;
		private final Component line;
		private final int width;
		private Visibility wantedVisibility = Visibility.HIDE;

		private PopularityToast(ItemStack icon, Component title, Component line) {
			this.icon = icon;
			this.title = title;
			this.line = line;
			Font font = Minecraft.getInstance().font;
			int text = Math.max(font.width(title), font.width(line));
			this.width = Math.clamp(TEXT_LEFT + text + TEXT_RIGHT, MIN_WIDTH, MAX_WIDTH);
		}

		@Override
		public int width() {
			return this.width;
		}

		@Override
		public int height() {
			return HEIGHT;
		}

		@Override
		public Visibility getWantedVisibility() {
			return this.wantedVisibility;
		}

		@Override
		public void update(ToastManager manager, long visibleMillis) {
			this.wantedVisibility = visibleMillis > DISPLAY_MILLIS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleMillis) {
			OverlayStyle.fillRounded(graphics, 1, 2, this.width - 3, HEIGHT - 2, BACKGROUND_COLOR);
			if (!this.icon.isEmpty()) {
				graphics.fakeItem(this.icon, 5, 6);
			}
			graphics.text(font, this.title, TEXT_LEFT, 6, TITLE_COLOR);
			graphics.text(font, font.substrByWidth(this.line, this.width - TEXT_LEFT - TEXT_RIGHT).getString(), TEXT_LEFT, 15, 0xFFFFFFFF);
		}
	}
}
