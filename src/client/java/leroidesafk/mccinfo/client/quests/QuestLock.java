package leroidesafk.mccinfo.client.quests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.items.SlotIndicators;
import leroidesafk.mccinfo.client.mixin.accessor.ContainerHoveredSlotAccessor;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Locks quest tasks so their quest cannot be rerolled
public final class QuestLock {
	// Games and tasks sent by the relay
	public record Game(String id, String name, Set<String> glyphs, List<String> icons, String logo) {
	}

	public record Glyph(String text, int height, int ascent) {
	}

	public record Task(String id, String game, String text, String description, Pattern pattern) {
	}

	private record Found(String glyph, String text) {
	}

	private record Word(String text, boolean value, boolean glue) {
	}

	// Quest lore, task text matching and tooltip lines
	private static final String MCC = "mcc";
	private static final FontDescription MCC_ICON_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(MCC, "icon"));
	private static final String ICON_FOLDER = "_fonts/icon/";
	private static final String ICON_EXTENSION = ".png";
	private static final String RIGHT_CLICK_ICON = ICON_FOLDER + "click_action_right.png";
	private static final String QUEST_MARKER = "quest";
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
	private static final String ANY_VALUE = "X";
	private static final int VALUE_COLOR = 0xFFFFFF;
	private static final int TASK_COLOR = 0xFEE761;
	private static final int LORE_WIDTH = 170;
	private static final String NUMBER = "[\\d,.]+(?:st|nd|rd|th|[smhdk])?";
	private static final Pattern PERCENT = Pattern.compile("\\d+(?:\\.\\d+)?%");
	private static final String PROGRESS_PREFIX = "progress:";
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final String ACTION_MARK = "> ";
	private static final int CLICK_DARK = 0x505050;
	private static final int CLICK_TEXT = 0xE9D282;
	private static final int CLICK_ACTION = 0xFBE460;
	private static final int ICON_COLOR = 0xFFFFFF;
	private static final int MAX_LENGTH = 200;
	private static final SoundEvent BLOCKED_SOUND = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MCC, "ui.click_dead"));

	// Icon glyphs, unlocked quests and data from the relay
	private static final Map<String, Glyph> ICON_GLYPHS = new ConcurrentHashMap<>();
	private static final Set<String> UNLOCKED = ConcurrentHashMap.newKeySet();
	private static volatile List<Game> games = List.of();
	private static volatile List<Task> tasks = List.of();

	private QuestLock() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !click(current, event));
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>) || !locked(stack)) {
				return;
			}
			String last = lines.isEmpty() ? "" : lines.getLast().getString();
			if (!last.isBlank() && !last.contains(ACTION_MARK)) {
				lines.add(Component.empty());
			}
			lines.add(unlockLine());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> UNLOCKED.clear());
	}

	// Games and tasks from the relay
	public static void receive(JsonObject object) {
		List<Game> receivedGames = new ArrayList<>();
		if (object.has("games") && object.get("games").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("games")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject game = element.getAsJsonObject();
				String id = string(game, "id");
				if (id.isEmpty()) {
					continue;
				}
				String name = string(game, "name");
				receivedGames.add(new Game(id, name.isEmpty() ? id : name, new HashSet<>(strings(game, "glyphs")), strings(game, "icons"), string(game, "logo")));
			}
		}
		List<Task> receivedTasks = new ArrayList<>();
		if (object.has("tasks") && object.get("tasks").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("tasks")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject task = element.getAsJsonObject();
				String id = string(task, "id");
				String text = normalize(string(task, "text"));
				if (id.isEmpty() || text.isEmpty()) {
					continue;
				}
				receivedTasks.add(new Task(id, string(task, "game"), text, string(task, "description"), pattern(text)));
			}
		}
		games = List.copyOf(receivedGames);
		tasks = List.copyOf(receivedTasks);
	}

	public static List<Game> games() {
		return games;
	}

	public static List<Task> tasks(String game) {
		return tasks.stream().filter(task -> task.game().equals(game)).toList();
	}

	public static boolean locked(Game game) {
		return ModConfig.get().questLockGames.contains(game.id());
	}

	public static boolean partlyLocked(Game game) {
		ModConfig config = ModConfig.get();
		return !config.questLockGames.contains(game.id()) && tasks(game.id()).stream().anyMatch(task -> config.questLockTasks.contains(task.id()));
	}

	public static boolean locked(Task task) {
		ModConfig config = ModConfig.get();
		return config.questLockGames.contains(task.game()) || config.questLockTasks.contains(task.id());
	}

	// Lock or unlock a whole game
	public static void toggle(Game game) {
		ModConfig config = ModConfig.get();
		if (!config.questLockGames.remove(game.id())) {
			config.questLockGames.add(game.id());
			tasks(game.id()).forEach(task -> config.questLockTasks.remove(task.id()));
		}
		config.save();
	}

	public static void toggle(Task task) {
		ModConfig config = ModConfig.get();
		if (config.questLockGames.remove(task.game())) {
			tasks(task.game()).stream().filter(other -> other != task).forEach(other -> config.questLockTasks.add(other.id()));
		} else if (!config.questLockTasks.remove(task.id())) {
			config.questLockTasks.add(task.id());
			List<Task> all = tasks(task.game());
			if (all.stream().allMatch(other -> config.questLockTasks.contains(other.id()))) {
				config.questLockGames.add(task.game());
				all.forEach(other -> config.questLockTasks.remove(other.id()));
			}
		}
		config.save();
	}

	public static Glyph logo(Game game) {
		Glyph logo = game.logo().isEmpty() ? null : ICON_GLYPHS.get(ICON_FOLDER + game.logo() + ICON_EXTENSION);
		return logo != null ? logo : smallIcon(game);
	}

	public static Component icon(Glyph glyph) {
		return Component.literal(glyph.text()).withStyle(style -> style.withFont(MCC_ICON_FONT).withColor(ICON_COLOR).withBold(false));
	}

	public static Component label(Task task) {
		MutableComponent label = Component.empty();
		Glyph glyph = smallIcon(gameById(task.game()));
		if (glyph != null) {
			label.append(icon(glyph)).append(" ");
		}
		return label.append(task.description().isEmpty() ? Component.literal(task.text()) : Component.literal(task.description()));
	}

	// Task shown like in its quest
	public static List<Component> lore(Task task) {
		Font font = Minecraft.getInstance().font;
		List<Component> lines = new ArrayList<>();
		MutableComponent line = Component.empty();
		int width = 0;
		Glyph glyph = smallIcon(gameById(task.game()));
		if (glyph != null) {
			line.append(icon(glyph)).append(" ");
			width = font.width(line);
		}
		boolean empty = true;
		int space = font.width(" ");
		for (Word word : words(task.text())) {
			int wordWidth = font.width(word.text());
			boolean glued = word.glue() && !empty;
			if (!empty && !glued && width + space + wordWidth > LORE_WIDTH) {
				lines.add(line);
				line = Component.empty();
				width = 0;
				empty = true;
			}
			if (!empty && !glued) {
				line.append(Component.literal(" ").withColor(TASK_COLOR));
				width += space;
			}
			line.append(Component.literal(word.text()).withColor(word.value() ? VALUE_COLOR : TASK_COLOR));
			width += wordWidth;
			empty = false;
		}
		lines.add(line);
		return lines;
	}

	// Task text split into words and values
	private static List<Word> words(String text) {
		List<Word> words = new ArrayList<>();
		Matcher matcher = PLACEHOLDER.matcher(text);
		int start = 0;
		boolean glue = false;
		while (matcher.find()) {
			glue = literalWords(text.substring(start, matcher.start()), words, glue);
			String value = matcher.group(1).strip();
			words.add(new Word(value.isEmpty() ? ANY_VALUE : value, true, glue));
			glue = true;
			start = matcher.end();
		}
		literalWords(text.substring(start), words, glue);
		return words;
	}

	private static boolean literalWords(String segment, List<Word> words, boolean glue) {
		if (segment.isEmpty()) {
			return glue;
		}
		boolean first = !Character.isWhitespace(segment.charAt(0)) && glue;
		for (String part : SPACES.split(segment.strip())) {
			if (!part.isEmpty()) {
				words.add(new Word(part, false, first));
				first = false;
			}
		}
		return !Character.isWhitespace(segment.charAt(segment.length() - 1));
	}

	private static Game gameById(String id) {
		for (Game game : games) {
			if (game.id().equals(id)) {
				return game;
			}
		}
		return null;
	}

	private static Glyph smallIcon(Game game) {
		if (game == null) {
			return null;
		}
		for (String icon : game.icons()) {
			Glyph glyph = ICON_GLYPHS.get(ICON_FOLDER + icon + ICON_EXTENSION);
			if (glyph != null) {
				return glyph;
			}
		}
		return null;
	}

	// Icon sizes read from the MCC font
	public static void fontGlyph(Identifier file, int height, int ascent, int[][] grid) {
		if (ascent < 0 || grid.length != 1 || grid[0].length != 1 || !MCC.equals(file.getNamespace()) || !file.getPath().startsWith(ICON_FOLDER)) {
			return;
		}
		ICON_GLYPHS.put(file.getPath(), new Glyph(Character.toString(grid[0][0]), height, ascent));
	}

	// Lock icon on locked quests
	public static void extractLockIcon(GuiGraphicsExtractor graphics, Slot slot) {
		if (slot.hasItem() && !(slot.container instanceof Inventory) && locked(slot.getItem())) {
			SlotIndicators.topRight(graphics, slot, SlotIndicators.LOCKED);
		}
	}

	// Shift-Click blocked on locked quests, Right-Click unlocks
	private static boolean click(Screen screen, MouseButtonEvent event) {
		Slot slot = ((ContainerHoveredSlotAccessor) screen).mccinfo$getHoveredSlot();
		if (slot == null || !slot.hasItem() || slot.container instanceof Inventory || !locked(slot.getItem())) {
			return false;
		}
		boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		if (!shift && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			UNLOCKED.add(key(slot.getItem(), found(slot.getItem())));
			NeuItems.clickSound();
			return true;
		}
		if (!shift) {
			return false;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BLOCKED_SOUND, 1.0F));
		return true;
	}

	// Quest with a locked task, not unlocked by hand
	private static boolean locked(ItemStack stack) {
		ModConfig config = ModConfig.get();
		if (!config.questLock || config.questLockGames.isEmpty() && config.questLockTasks.isEmpty() || !OverlayStyle.onMccIsland()
				|| !Permissions.has(Permissions.Perm.UTILS) || !isQuest(stack)) {
			return false;
		}
		List<Found> found = found(stack);
		if (found.isEmpty() || UNLOCKED.contains(key(stack, found))) {
			return false;
		}
		for (Found task : found) {
			if (wanted(task, config)) {
				return true;
			}
		}
		return false;
	}

	private static boolean wanted(Found found, ModConfig config) {
		Task task = null;
		for (Task candidate : tasks) {
			if (candidate.pattern().matcher(found.text()).matches()) {
				task = candidate;
				break;
			}
		}
		if (task != null && config.questLockTasks.contains(task.id())) {
			return true;
		}
		String game = gameOf(found.glyph());
		if (game == null && task != null) {
			game = task.game();
		}
		return game != null && config.questLockGames.contains(game);
	}

	private static String gameOf(String glyph) {
		for (Game game : games) {
			if (game.glyphs().contains(glyph)) {
				return game.id();
			}
			for (String icon : game.icons()) {
				Glyph resolved = ICON_GLYPHS.get(ICON_FOLDER + icon + ICON_EXTENSION);
				if (resolved != null && glyph.equals(resolved.text())) {
					return game.id();
				}
			}
		}
		return null;
	}

	private static boolean isQuest(ItemStack stack) {
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model != null && MCC.equals(model.getNamespace()) && model.getPath().contains(QUEST_MARKER)) {
			return true;
		}
		return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(QUEST_MARKER);
	}

	// Tasks of a quest, with their game icon
	private static List<Found> found(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<Found> found = new ArrayList<>();
		if (lore == null) {
			return found;
		}
		String glyph = null;
		StringBuilder text = null;
		for (Component line : lore.lines()) {
			String icon = leadingIcon(line);
			String plain = normalize(line.getString());
			boolean bar = PERCENT.matcher(plain).matches();
			if (icon != null && !plain.isEmpty() && !bar) {
				if (text != null) {
					found.add(new Found(glyph, text.toString()));
				}
				glyph = icon;
				text = new StringBuilder(plain);
			} else if (text != null) {
				if (plain.isEmpty() || bar || plain.toLowerCase(Locale.ROOT).startsWith(PROGRESS_PREFIX)) {
					found.add(new Found(glyph, text.toString()));
					text = null;
				} else {
					text.append(' ').append(plain);
				}
			}
		}
		if (text != null) {
			found.add(new Found(glyph, text.toString()));
		}
		return found;
	}

	private static String leadingIcon(Component line) {
		String[] icon = {null};
		line.visit((style, text) -> {
			if (text.isEmpty()) {
				return Optional.empty();
			}
			if (MCC_ICON_FONT.equals(style.getFont())) {
				icon[0] = Character.toString(text.codePointAt(0));
			}
			return Optional.of(Unit.INSTANCE);
		}, Style.EMPTY);
		return icon[0];
	}

	private static String key(ItemStack stack, List<Found> found) {
		StringBuilder key = new StringBuilder(stack.getHoverName().getString());
		for (Found task : found) {
			key.append('|').append(task.text());
		}
		return key.toString();
	}

	private static Component unlockLine() {
		MutableComponent line = Component.empty();
		Glyph glyph = ICON_GLYPHS.get(RIGHT_CLICK_ICON);
		if (glyph != null) {
			line.append(icon(glyph));
		}
		return line.append(Component.literal(" > ").withColor(CLICK_DARK))
				.append(Component.translatable("tooltip.mccinfo.quest_lock.unlock").withColor(CLICK_TEXT))
				.append(Component.translatable("tooltip.mccinfo.quest_lock.unlock_action").withColor(CLICK_ACTION));
	}

	// Task text to a pattern, {} matches any number
	private static Pattern pattern(String text) {
		StringBuilder regex = new StringBuilder();
		Matcher matcher = PLACEHOLDER.matcher(text);
		int start = 0;
		while (matcher.find()) {
			String value = matcher.group(1).strip();
			regex.append(Pattern.quote(text.substring(start, matcher.start()))).append(value.isEmpty() ? NUMBER : Pattern.quote(value));
			start = matcher.end();
		}
		regex.append(Pattern.quote(text.substring(start)));
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
	}

	private static String normalize(String text) {
		return SPACES.matcher(text.replaceAll("[\\uE000-\\uF8FF]", " ")).replaceAll(" ").strip();
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return "";
		}
		String value = element.getAsString().strip();
		return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
	}

	private static List<String> strings(JsonObject object, String key) {
		Set<String> values = new LinkedHashSet<>();
		if (object.has(key) && object.get(key).isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray(key)) {
				if (element.isJsonPrimitive() && !element.getAsString().isEmpty()) {
					values.add(element.getAsString());
				}
			}
		}
		return List.copyOf(values);
	}
}
