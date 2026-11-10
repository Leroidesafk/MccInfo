package leroidesafk.mccinfo.client.parkour;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.util.AsyncFiles;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Parkour Warrior courses with the weekly XP limit reached, tinted in the Dojo
public final class ParkourLimits {
	private static final Path PATH = ModConfig.file("parkour_limits.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DOJO_TITLE = "PARKOUR WARRIOR DOJO";
	private static final List<String> LIMIT_MESSAGES = List.of("hit the weekly xp limit", "weekly xp limit reached");
	private static final Pattern RESETS_IN = Pattern.compile("resets in((?:\\s*\\d+\\s*[dhms])+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);
	private static final long HOUR_MILLIS = 3_600_000L;
	private static final long SNAP_MILLIS = 120_000L;
	private static final String LIMIT_COURSE = "on this course";
	private static final String COURSE_LINE = "COURSE:";
	private static final String MODEL_PREFIX = "island_interface/game/parkour_warrior/solo/";
	private static final String DAILY = "daily";
	private static final Pattern COURSE = Pattern.compile("Course #(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DAILY_NUMBER = Pattern.compile("Daily Challenge #([\\d,]+)", Pattern.CASE_INSENSITIVE);
	private static final LocalTime RESET_TIME = LocalTime.of(10, 0);
	private static final int SLOT_SIZE = 16;
	private static final int TINT = 0x80FF2020;

	// Course or daily challenge and its reset time
	public static final class Limit {
		String course;
		long number;
		long hitAt;
		long resetAt;

		Limit(String course, long number, long hitAt, long resetAt) {
			this.course = course;
			this.number = number;
			this.hitAt = hitAt;
			this.resetAt = resetAt;
		}
	}

	// parkour_limits.json
	private static final class Saved {
		long lastDaily;
		List<Limit> limits = new ArrayList<>();
	}

	private static Saved saved = new Saved();

	private ParkourLimits() {
	}

	public static void register() {
		load();
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message.getString());
			}
		});
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message.getString());
			}
		});
	}

	private static boolean enabled() {
		return ModConfig.get().parkourLimitTint && MccIsland.isConnected();
	}

	private static void load() {
		if (!Files.exists(PATH)) {
			return;
		}
		try {
			Saved loaded = GSON.fromJson(Files.readString(PATH), Saved.class);
			if (loaded != null) {
				if (loaded.limits == null) {
					loaded.limits = new ArrayList<>();
				}
				long now = System.currentTimeMillis();
				loaded.limits.removeIf(limit -> limit == null || limit.course == null || limit.resetAt <= now);
				saved = loaded;
			}
		} catch (IOException | JsonParseException e) {
			Mccinfo.LOGGER.warn("Unreadable parkour limits ({})", PATH, e);
		}
	}

	private static void save() {
		AsyncFiles.write(PATH, GSON.toJson(saved));
	}

	// Resets at 10:00 UTC, daily or on the 1st of the month
	static long dailyReset(long hitAt) {
		ZonedDateTime time = Instant.ofEpochMilli(hitAt).atZone(ZoneOffset.UTC);
		ZonedDateTime reset = time.toLocalDate().atTime(RESET_TIME).atZone(ZoneOffset.UTC);
		if (!reset.isAfter(time)) {
			reset = reset.plusDays(1);
		}
		return reset.toInstant().toEpochMilli();
	}

	static long monthlyReset(long hitAt) {
		ZonedDateTime time = Instant.ofEpochMilli(hitAt).atZone(ZoneOffset.UTC);
		LocalDate first = time.toLocalDate().withDayOfMonth(1);
		ZonedDateTime reset = first.atTime(RESET_TIME).atZone(ZoneOffset.UTC);
		if (!reset.isAfter(time)) {
			reset = first.plusMonths(1).atTime(RESET_TIME).atZone(ZoneOffset.UTC);
		}
		return reset.toInstant().toEpochMilli();
	}

	// Limit message, course read in the scoreboard
	private static void onMessage(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (LIMIT_MESSAGES.stream().noneMatch(lower::contains) || !lower.contains(LIMIT_COURSE) || !MccIsland.isConnected()) {
			return;
		}
		String course = scoreboardCourse();
		if (course == null) {
			Mccinfo.LOGGER.info("Parkour XP limit message seen, but no COURSE line in the scoreboard: {}", sidebarLines());
			return;
		}
		long now = System.currentTimeMillis();
		long resetIn = resetIn(text);
		Matcher daily = DAILY_NUMBER.matcher(course);
		if (daily.find()) {
			long number = parseNumber(daily.group(1));
			seeDaily(number);
			record(new Limit(DAILY, number, now, resetIn > 0 ? snap(now + resetIn) : dailyReset(now)));
			return;
		}
		Matcher monthly = COURSE.matcher(course);
		if (monthly.find()) {
			record(new Limit(monthly.group(1), 0, now, resetIn > 0 ? snap(now + resetIn) : monthlyReset(now)));
			return;
		}
		Mccinfo.LOGGER.info("Parkour XP limit message seen, unknown course \"{}\"", course);
	}

	// "Resets in" time of the message, then put on the hour
	private static long resetIn(String text) {
		Matcher matcher = RESETS_IN.matcher(text);
		if (!matcher.find()) {
			return 0;
		}
		long millis = 0;
		Matcher part = DURATION_PART.matcher(matcher.group(1));
		while (part.find()) {
			long value = Long.parseLong(part.group(1));
			millis += switch (Character.toLowerCase(part.group(2).charAt(0))) {
				case 'd' -> value * 24 * HOUR_MILLIS;
				case 'h' -> value * HOUR_MILLIS;
				case 'm' -> value * 60_000L;
				default -> value * 1_000L;
			};
		}
		return millis;
	}

	private static long snap(long time) {
		long hour = Math.round(time / (double) HOUR_MILLIS) * HOUR_MILLIS;
		return Math.abs(hour - time) <= SNAP_MILLIS ? hour : time;
	}

	private static List<String> sidebarLines() {
		List<String> lines = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return lines;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			return lines;
		}
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			lines.add(sidebarText(scoreboard, entry));
		}
		return lines;
	}

	private static String sidebarText(Scoreboard scoreboard, PlayerScoreEntry entry) {
		Component line = entry.display() != null ? entry.display()
				: PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
		return line.getString();
	}

	private static void record(Limit limit) {
		saved.limits.removeIf(old -> old.course.equals(limit.course) && old.number == limit.number);
		saved.limits.add(limit);
		save();
	}

	// New daily challenge: older daily limits removed
	private static void seeDaily(long number) {
		if (number <= 0 || number == saved.lastDaily) {
			return;
		}
		saved.lastDaily = number;
		saved.limits.removeIf(limit -> DAILY.equals(limit.course) && limit.number != number);
		save();
	}

	private static long parseNumber(String text) {
		try {
			return Long.parseLong(text.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String scoreboardCourse() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return null;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			return null;
		}
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			String text = sidebarText(scoreboard, entry);
			int index = text.toUpperCase(Locale.ROOT).indexOf(COURSE_LINE);
			if (index >= 0) {
				return text.substring(index + COURSE_LINE.length()).strip();
			}
		}
		return null;
	}

	// Course item found by its model
	private static boolean reached(ItemStack stack) {
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model == null || !model.getPath().startsWith(MODEL_PREFIX)) {
			return false;
		}
		String course = model.getPath().substring(MODEL_PREFIX.length());
		long number = 0;
		if (DAILY.equals(course)) {
			Matcher daily = DAILY_NUMBER.matcher(stack.getHoverName().getString());
			if (!daily.find()) {
				return false;
			}
			number = parseNumber(daily.group(1));
			seeDaily(number);
		}
		long now = System.currentTimeMillis();
		for (Limit limit : saved.limits) {
			if (limit.course.equals(course) && limit.number == number && limit.resetAt > now) {
				return true;
			}
		}
		return false;
	}

	// Red tint on the slot in the Dojo menu
	public static void extractTint(GuiGraphicsExtractor graphics, Slot slot) {
		if (!enabled()) {
			return;
		}
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen == null || !screen.getTitle().getString().toUpperCase(Locale.ROOT).contains(DOJO_TITLE) || !reached(slot.getItem())) {
			return;
		}
		graphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, TINT);
	}
}
