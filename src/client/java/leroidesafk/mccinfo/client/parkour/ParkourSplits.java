package leroidesafk.mccinfo.client.parkour;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.MccServer;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.mixin.accessor.BossHealthOverlayAccessor;
import leroidesafk.mccinfo.client.util.AsyncFiles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.util.Unit;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Module times of the Parkour Warrior Dojo
public final class ParkourSplits {
	private static final String DOJO = "dojo";
	private static final String MCC = "mcc";
	// Sounds that start and stop the timer
	private static final String SOUND_GO = "games.global.countdown.go";
	private static final String SOUND_ROUND_END = "games.global.timer.round_end";
	private static final String SOUND_QUEUE_TELEPORT = "ui.queue_teleport";
	private static final List<String> SOUND_STOPS = List.of("games.parkour_warrior.mode_swap", "games.parkour_warrior.restart_course");
	// Module and medal titles
	private static final Pattern LEVEL_TITLE = Pattern.compile("\\[(.*)]");
	private static final Pattern TIME_LEFT = Pattern.compile("(?:\\d+d\\s*|\\d+h\\s*|\\d+m\\s*)+");
	private static final Pattern TIME_PART = Pattern.compile("(\\d+)([dhm])");
	private static final long TITLE_DELAY_MILLIS = 1500L;
	private static final int MEDAL_TITLE_MAX = 4;
	private static final String DEFAULT_LEVEL = "M1-1";
	private static final String DAILY = "daily";
	private static final String DAILY_CHALLENGE = "daily challenge";
	private static final String UNKNOWN_COURSE = "unknown";
	// Saved splits
	private static final Path PATH = ModConfig.file("parkour_splits.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	// Timer bar at the top of the screen
	private static final int BAR_WIDTH = 130;
	private static final int BAR_HEIGHT = 12;
	private static final int NAME_WIDTH = 25;
	private static final int BAR_TOP = 18;
	private static final int BOSS_BAR_STEP = 19;
	private static final int BAR_TOP_NO_BOSS_BAR = 1;
	private static final int BAR_COLOR = 0x80000000;
	private static final int NAME_COLOR = 0x41000000;
	private static final FontDescription ICON_FONT = new FontDescription.Resource(Mccinfo.id("icons"));
	private static final String CHECK_ICON = "\uE133";
	private static final String DOWN_ICON = "\uE137";
	private static final String UP_ICON = "\uE138";
	private static final int ICON_COLOR = 0xFFFFFF;
	private static final FontDescription BOSS_BAR_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(MCC, "boss_bar"));
	private static final String MCC_BARS = "";
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int TEXT_PADDING = 2;
	private static final int IMPROVEMENT_GAP = 8;
	private static final String MEDAL_TEXTURE = "_fonts/icon/medals.png";

	// Best, average and last times of a module
	public record Split(long best, double avg, List<Long> times) {
		private Split addTime(long time) {
			List<Long> list = new ArrayList<>(this.times);
			list.add(time);
			double average = list.stream().mapToLong(Long::longValue).average().orElse(0.0);
			return new Split(Math.min(this.best, time), average, List.copyOf(list));
		}
	}

	private static final class Course {
		String name;
		Long expires;
		Map<String, Split> splits = new HashMap<>();
		Map<String, String> names = new HashMap<>();

		private Course(String name, Long expires) {
			this.name = name;
			this.expires = expires;
		}
	}

	private static final class Saved {
		List<Course> courses = new ArrayList<>();
		long savedAt;
		int version = 1;
	}

	// Module being timed
	private static final class Timer {
		private final Course course;
		private long lastSplit = System.currentTimeMillis();
		private String levelName = DEFAULT_LEVEL;
		private String levelId = "";
		private boolean between = true;

		private Timer(Course course) {
			this.course = course;
		}

		private long millis() {
			return System.currentTimeMillis() - this.lastSplit;
		}

		private double seconds() {
			return millis() / 1000.0;
		}

		private Double improvement() {
			Split split = this.course.splits.get(this.levelId);
			if (split == null) {
				return null;
			}
			double reference = ModConfig.get().parkourSplitAverage ? split.avg() : split.best();
			return seconds() - reference / 1000.0;
		}
	}

	private static final Map<String, Course> COURSES = new HashMap<>();
	private static Long currentExpiry;
	private static Timer timer;
	private static String medalGlyph = "";
	private static boolean barLogged;

	private ParkourSplits() {
	}

	public static void register() {
		load();
		HudElementRegistry.addLast(Mccinfo.id("parkour_splits"), ParkourSplits::extract);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> timer = null);
	}

	public static void fontGlyph(Identifier file, int[][] grid) {
		if (grid.length != 1 || !MCC.equals(file.getNamespace()) || !MEDAL_TEXTURE.equals(file.getPath())) {
			return;
		}
		StringBuilder glyph = new StringBuilder();
		for (int point : grid[0]) {
			glyph.appendCodePoint(point);
		}
		medalGlyph = glyph.toString();
	}

	private static boolean enabled() {
		return ModConfig.get().parkourSplits && MccIsland.isConnected() && DOJO.equals(MccServer.server());
	}

	// Timer started or stopped by the game sounds
	public static void onSound(Identifier sound) {
		if (!MCC.equals(sound.getNamespace())) {
			return;
		}
		if (!enabled()) {
			timer = null;
			return;
		}
		String path = sound.getPath();
		boolean roundEnd = path.equals(SOUND_ROUND_END);
		if (roundEnd || path.equals(SOUND_QUEUE_TELEPORT) || SOUND_STOPS.stream().anyMatch(path::contains)) {
			if (roundEnd && timer != null) {
				saveSplit(timer);
			}
			timer = null;
		} else if (path.equals(SOUND_GO)) {
			timer = new Timer(course(ParkourLimits.scoreboardCourse()));
		}
	}

	// Medal title ends a module (with its improvement), level title starts one
	public static Component onSubtitle(Component text) {
		Timer current = timer;
		if (current == null || !enabled()) {
			return text;
		}
		String string = text.getString();
		Component shown = text;
		if (string.length() < MEDAL_TITLE_MAX && string.contains(medalGlyph)) {
			if (ModConfig.get().parkourSplitImprovements) {
				shown = text.copy().append(improvementText(current.improvement()));
			}
			saveSplit(current);
			current.lastSplit = System.currentTimeMillis();
			current.between = true;
		}
		if (string.startsWith("[")) {
			Matcher matcher = LEVEL_TITLE.matcher(string);
			if (matcher.find()) {
				current.lastSplit = System.currentTimeMillis() - TITLE_DELAY_MILLIS;
				current.levelName = matcher.group(1);
				current.between = false;
				StringBuilder id = new StringBuilder();
				for (Component sibling : text.getSiblings()) {
					TextColor color = sibling.getStyle().getColor();
					if (color != null) {
						id.append(color);
					}
				}
				current.levelId = id.append(current.levelName).toString();
			}
		}
		return shown;
	}

	// Course reset time read from the entities
	public static void onEntityData(Entity entity) {
		if (!(entity instanceof Display.TextDisplay) || !enabled()) {
			return;
		}
		Component name = entity.getCustomName();
		if (name == null) {
			return;
		}
		Matcher matcher = TIME_LEFT.matcher(name.getString());
		if (!matcher.find()) {
			return;
		}
		long seconds = 0;
		Matcher part = TIME_PART.matcher(matcher.group().replace(" ", ""));
		while (part.find()) {
			long value = Long.parseLong(part.group(1));
			seconds += switch (part.group(2)) {
				case "d" -> value * 86_400L;
				case "h" -> value * 3_600L;
				default -> value * 60L;
			};
		}
		if (seconds > 0) {
			currentExpiry = System.currentTimeMillis() + seconds * 1000L;
		}
	}

	private static Course course(String courseName) {
		String name = courseName == null || courseName.isBlank() ? UNKNOWN_COURSE
				: courseName.toLowerCase(Locale.ROOT).contains(DAILY_CHALLENGE) ? DAILY : courseName;
		Course course = COURSES.get(name);
		if (course == null || (course.expires != null && System.currentTimeMillis() >= course.expires)) {
			course = new Course(name, currentExpiry);
			COURSES.put(name, course);
		} else if (course.expires == null) {
			course.expires = currentExpiry;
		}
		return course;
	}

	// Time saved at the end of a module
	private static void saveSplit(Timer current) {
		long millis = current.millis();
		if (ModConfig.get().parkourSplitChat) {
			sendSplitMessage(current);
		}
		Split split = current.course.splits.get(current.levelId);
		current.course.splits.put(current.levelId, split == null ? new Split(millis, millis, List.of(millis)) : split.addTime(millis));
		current.course.names.put(current.levelId, current.levelName);
		save();
	}

	// Split time in the chat
	private static void sendSplitMessage(Timer current) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		MutableComponent message = Component.literal("[").withStyle(ChatFormatting.GREEN).append(icon(CHECK_ICON)).append("] ")
				.append(Component.translatable("message.mccinfo.parkour.split_complete", current.levelName))
				.append(Component.literal(String.format(Locale.ROOT, "%.3fs", current.seconds())).withStyle(ChatFormatting.WHITE));
		if (ModConfig.get().parkourSplitImprovements) {
			message.append(Component.empty().withStyle(ChatFormatting.WHITE).append(improvementText(current.improvement())));
		}
		client.player.sendSystemMessage(message);
	}

	private static Component improvementText(Double improvement) {
		double value = improvement == null ? 0.0 : improvement;
		String time = String.format(Locale.ROOT, "%.2f", value);
		ChatFormatting color;
		Component icon;
		if (value > 0) {
			color = ChatFormatting.RED;
			icon = icon(UP_ICON);
			time = "+" + time;
		} else if (value < 0) {
			color = ChatFormatting.GREEN;
			icon = icon(DOWN_ICON);
		} else {
			color = ChatFormatting.YELLOW;
			icon = Component.literal("-").withStyle(color);
		}
		return Component.literal(" (").withStyle(ChatFormatting.WHITE)
				.append(icon)
				.append(Component.literal(" " + time).withStyle(color))
				.append(Component.literal(")").withStyle(ChatFormatting.WHITE));
	}

	private static Component icon(String glyph) {
		return Component.literal(glyph).withStyle(style -> style.withFont(ICON_FONT).withColor(ICON_COLOR).withoutShadow());
	}

	// Placed next to the MCC bars
	private static float mccBarLeft(Font font, Component name, int screenWidth) {
		float[] before = {0.0F};
		boolean[] found = {false};
		StringBuilder parts = new StringBuilder();
		name.visit((style, text) -> {
			parts.append(" [").append(style.getFont()).append(':');
			text.codePoints().forEach(point -> parts.append(' ').append(Integer.toHexString(point)));
			parts.append(" = ").append(font.getSplitter().stringWidth(FormattedText.of(text, style))).append(']');
			int index = -1;
			for (int i = 0; i < text.length() && index < 0; i++) {
				if (MCC_BARS.indexOf(text.charAt(i)) >= 0) {
					index = i;
				}
			}
			if (index >= 0 && BOSS_BAR_FONT.equals(style.getFont())) {
				before[0] += font.getSplitter().stringWidth(FormattedText.of(text.substring(0, index), style));
				found[0] = true;
				return Optional.of(Unit.INSTANCE);
			}
			before[0] += font.getSplitter().stringWidth(FormattedText.of(text, style));
			return Optional.empty();
		}, Style.EMPTY);
		if (!found[0]) {
			return Float.NaN;
		}
		float left = screenWidth / 2 - font.width(name) / 2 + before[0];
		if (!barLogged) {
			barLogged = true;
			Mccinfo.LOGGER.info("Parkour splits: MCC bar at x={} (screen {}, gui scale {}, title {} / {}, before bar {}), title:{}", left, screenWidth,
					Minecraft.getInstance().getWindow().getGuiScale(), font.width(name), font.getSplitter().stringWidth(name), before[0], parts);
		}
		return left;
	}

	public static void clear() {
		COURSES.clear();
		save();
	}

	private static void load() {
		if (!Files.exists(PATH)) {
			return;
		}
		try {
			Saved saved = GSON.fromJson(Files.readString(PATH), Saved.class);
			if (saved == null || saved.courses == null) {
				return;
			}
			for (Course course : saved.courses) {
				if (course == null || course.name == null) {
					continue;
				}
				if (course.splits == null) {
					course.splits = new HashMap<>();
				}
				if (course.names == null) {
					course.names = new HashMap<>();
				}
				COURSES.put(course.name, course);
			}
		} catch (IOException | JsonParseException e) {
			Mccinfo.LOGGER.warn("Unreadable parkour splits ({})", PATH, e);
		}
	}

	private static void save() {
		Saved saved = new Saved();
		saved.courses = new ArrayList<>(COURSES.values());
		saved.savedAt = System.currentTimeMillis();
		AsyncFiles.write(PATH, GSON.toJson(saved));
	}

	// Timer drawing
	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Timer current = timer;
		Minecraft client = Minecraft.getInstance();
		if (current == null || !ModConfig.get().parkourSplitTimer || !enabled()) {
			return;
		}
		Font font = client.font;
		Map<?, LerpingBossEvent> events = ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).mccinfo$getEvents();
		float left = graphics.guiWidth() / 2 - (BAR_WIDTH + 1) / 2;
		int y = events.isEmpty() ? BAR_TOP_NO_BOSS_BAR : BAR_TOP;
		int index = 0;
		for (LerpingBossEvent event : events.values()) {
			float mccBar = mccBarLeft(font, event.getName(), graphics.guiWidth());
			if (!Float.isNaN(mccBar)) {
				left = mccBar;
				y = BAR_TOP + index * BOSS_BAR_STEP;
				break;
			}
			index++;
		}
		int x = (int) Math.floor(left);
		graphics.pose().pushMatrix();
		graphics.pose().translate(left - x, 0.0F);
		extractBar(graphics, font, current, x, y);
		graphics.pose().popMatrix();
	}

	private static void extractBar(GuiGraphicsExtractor graphics, Font font, Timer current, int x, int y) {
		OverlayStyle.fillRounded(graphics, x, y, BAR_WIDTH, BAR_HEIGHT, BAR_COLOR);
		graphics.fill(x, y + 1, x + 1, y + BAR_HEIGHT - 1, NAME_COLOR);
		graphics.fill(x + 1, y, x + NAME_WIDTH, y + BAR_HEIGHT, NAME_COLOR);
		Component level = OverlayStyle.hudText(current.levelName.toUpperCase(Locale.ROOT));
		graphics.text(font, level, x + 1 + NAME_WIDTH / 2 - font.width(level) / 2, y + TEXT_PADDING, TEXT_COLOR, false);
		Component time = Component.literal(String.format(Locale.ROOT, "%.3f", current.seconds()));
		int timeWidth = font.width(time);
		graphics.text(font, time, x + BAR_WIDTH - timeWidth - TEXT_PADDING, y + TEXT_PADDING, TEXT_COLOR, true);
		if (!ModConfig.get().parkourSplitImprovements || current.between) {
			return;
		}
		Double improvement = current.improvement();
		if (improvement == null || improvement < ModConfig.get().parkourImprovementAt) {
			return;
		}
		String text = String.format(Locale.ROOT, "%.2fs", improvement);
		ChatFormatting color = ChatFormatting.GREEN;
		if (improvement > 0) {
			color = ChatFormatting.RED;
			text = "+" + text;
		}
		Component shown = Component.literal(text).withStyle(color);
		graphics.text(font, shown, x + BAR_WIDTH - timeWidth - TEXT_PADDING - font.width(shown) - IMPROVEMENT_GAP, y + TEXT_PADDING, TEXT_COLOR, true);
	}
}
