package leroidesafk.mccinfo.client.chat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// Server chats, Focus Reply and chat rooms
public final class ChatChannels {
	// Server chats and their icon in chat.png
	public enum Channel {
		LOCAL("LOCAL", "local", 0, 0, 0x9AC4E2),
		PARTY("PARTY", "party", 13, 0, 0x737ACA),
		PLOBBY("PLOBBY", "plobby", 26, 0, 0xD79130);

		final String label;
		final String command;
		final int u;
		final int v;
		final int color;

		Channel(String label, String command, int u, int v, int color) {
			this.label = label;
			this.command = command;
			this.u = u;
			this.v = v;
			this.color = color;
		}
	}

	// Chat icons of the action bar
	private static final Map<String, Channel> ACTION_BAR_ICONS = Map.of("", Channel.LOCAL, "", Channel.PARTY, "", Channel.PLOBBY);
	private static final net.minecraft.resources.Identifier ICON_FONT = net.minecraft.resources.Identifier.fromNamespaceAndPath("mcc", "icon");

	private static Component actionBar;
	// Focus Reply
	private static final java.util.regex.Pattern PRIVATE_MESSAGE = java.util.regex.Pattern.compile("^\\[PM (?:To|From)\\] [^A-Za-z0-9_]*([A-Za-z0-9_]{3,16})[^:]*: ");
	private static final int FOCUS_LENGTH = 6;
	static final int FOCUS_U = 13;
	static final int FOCUS_V = 13;
	static final int FOCUS_COLOR = 0xFF7EFF;

	// Chat Rooms menu
	private static final String ROOMS_TITLE = "CHAT ROOMS";
	private static final String ROOMS_JOIN = "Join a chat room";
	private static final String ROOM_MODEL = "island_interface/settings/chat_bubble_";
	private static final String ROOM_MUTED = "unavailable";
	private static final java.util.regex.Pattern ROOM_JOINED = java.util.regex.Pattern.compile("^You have joined the '(.+)' chat room\\.$");
	private static final java.util.regex.Pattern ROOM_LEFT = java.util.regex.Pattern.compile("^You have left the '(.+)' chat room\\.$");
	private static final List<String> ROOM_COLORS = List.of("white", "yellow", "blue", "green", "orange", "pink", "teal");
	private static final int[] ROOM_BACKGROUNDS = {0x9AC4E2, 0xFDE17E, 0x7DB9FF, 0x64D460, 0xFFB37D, 0xFF7DAD, 0x00B997};
	private static final int ROOM_COLUMNS = 3;
	private static final int ROOM_ICON = 13;

	private static Channel detected;
	private static String focus;
	private static String room;

	private ChatChannels() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			actionBar = null;
			detected = null;
			focus = null;
			room = null;
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (roomsScreen(screen)) {
				ScreenEvents.afterTick(screen).register(current -> scanRooms((ContainerScreen) current));
			}
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message);
			}
		});
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message);
			}
		});
	}

	static boolean roomsScreen(net.minecraft.client.gui.screens.Screen screen) {
		return screen instanceof ContainerScreen && screen.getTitle().getString().contains(ROOMS_TITLE);
	}

	// Room name of a chat bubble item
	static String roomName(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		net.minecraft.resources.Identifier model = stack.get(DataComponents.ITEM_MODEL);
		String name = stack.getHoverName().getString().strip();
		return model != null && model.getNamespace().equals("mcc") && model.getPath().startsWith(ROOM_MODEL) && !name.isEmpty() && name.indexOf(' ') < 0 ? name : null;
	}

	// Rooms saved once the menu is loaded, muted ones included
	private static void scanRooms(ContainerScreen screen) {
		Container items = screen.getMenu().getContainer();
		List<ModConfig.ChatRoom> found = new java.util.ArrayList<>();
		boolean loaded = false;
		for (int slot = 0; slot < items.getContainerSize(); slot++) {
			ItemStack stack = items.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getHoverName().getString().strip();
			if (name.equals(ROOMS_JOIN)) {
				loaded = true;
			}
			if (roomName(stack) != null) {
				String color = stack.get(DataComponents.ITEM_MODEL).getPath().substring(ROOM_MODEL.length());
				boolean muted = color.equals(ROOM_MUTED);
				found.add(new ModConfig.ChatRoom(name, muted ? knownColor(name) : color, muted));
			}
		}
		ModConfig config = ModConfig.get();
		if (!loaded || found.equals(config.chatRooms)) {
			return;
		}
		config.chatRooms = found;
		config.save();
		if (room != null && rooms().stream().noneMatch(entry -> entry.name().equals(room))) {
			room = null;
		}
	}

	// Color kept while a room is muted
	private static String knownColor(String name) {
		for (ModConfig.ChatRoom entry : ModConfig.get().chatRooms) {
			if (entry.name().equals(name) && ROOM_COLORS.contains(entry.color())) {
				return entry.color();
			}
		}
		return ROOM_COLORS.getFirst();
	}

	// Room joined or left in the chat
	private static void onMessage(Component message) {
		String text = message.getString().strip();
		java.util.regex.Matcher joined = ROOM_JOINED.matcher(text);
		if (joined.matches()) {
			joinRoom(joined.group(1).strip());
			return;
		}
		java.util.regex.Matcher left = ROOM_LEFT.matcher(text);
		if (left.matches()) {
			leaveRoom(left.group(1).strip());
		}
	}

	private static void joinRoom(String name) {
		ModConfig config = ModConfig.get();
		if (name.isEmpty() || config.chatRooms.stream().anyMatch(entry -> entry.name().equals(name))) {
			return;
		}
		List<ModConfig.ChatRoom> rooms = new java.util.ArrayList<>(config.chatRooms);
		rooms.add(new ModConfig.ChatRoom(name, ROOM_COLORS.getFirst(), false));
		config.chatRooms = rooms;
		config.save();
	}

	private static void leaveRoom(String name) {
		ModConfig config = ModConfig.get();
		List<ModConfig.ChatRoom> rooms = new java.util.ArrayList<>(config.chatRooms);
		if (!rooms.removeIf(entry -> entry.name().equals(name))) {
			return;
		}
		config.chatRooms = rooms;
		config.save();
		if (name.equals(room)) {
			room = null;
		}
	}

	// Rooms shown in the chat, not muted
	static List<ModConfig.ChatRoom> rooms() {
		return ModConfig.get().chatRooms.stream().filter(entry -> !entry.muted()).toList();
	}

	private static int roomColorIndex(ModConfig.ChatRoom entry) {
		return Math.max(0, ROOM_COLORS.indexOf(entry.color()));
	}

	// Room color and its icon in chat_colors.png
	static int roomBackground(ModConfig.ChatRoom entry) {
		return ROOM_BACKGROUNDS[roomColorIndex(entry)];
	}

	static int roomU(ModConfig.ChatRoom entry) {
		return roomColorIndex(entry) % ROOM_COLUMNS * ROOM_ICON;
	}

	static int roomV(ModConfig.ChatRoom entry) {
		return roomColorIndex(entry) / ROOM_COLUMNS * ROOM_ICON;
	}

	static String room() {
		return room;
	}

	static void focusRoom(String name) {
		room = name;
		focus = null;
	}

	static void clearRoom() {
		room = null;
	}

	// Current chat read from the action bar icons
	public static void onActionBar(Component component) {
		actionBar = component;
		if (component == null) {
			return;
		}
		component.visit((style, text) -> {
			Channel channel = ACTION_BAR_ICONS.get(text);
			if (channel != null && style.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier id) && id.equals(ICON_FONT)) {
				detected = channel;
				return java.util.Optional.of(channel);
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
	}

	static Channel current() {
		return detected != null ? detected : Channel.LOCAL;
	}

	static String focus() {
		return focus;
	}

	static void focus(String name) {
		focus = name;
		room = null;
	}

	static void clearFocus() {
		focus = null;
	}

	static String focusLabel() {
		return focus == null ? null : focus.length() > FOCUS_LENGTH ? focus.substring(0, FOCUS_LENGTH) + "..." : focus;
	}

	// Player name of a private message
	static String privateMessageName(Component message) {
		java.util.regex.Matcher matcher = PRIVATE_MESSAGE.matcher(message.getString());
		return matcher.find() ? matcher.group(1) : null;
	}

	static List<Channel> others() {
		Channel current = current();
		return java.util.Arrays.stream(Channel.values()).filter(channel -> channel != current).toList();
	}

	// Switch server chat, focus and room forgotten
	static void join(Channel channel) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		focus = null;
		room = null;
		if (current() != channel) {
			client.player.connection.sendCommand("chat " + channel.command);
		}
	}

	public static int dumpActionBar(FabricClientCommandSource source) {
		Component result = saveActionBar();
		if (result == null) {
			source.sendError(Component.literal(actionBar == null ? "No action bar received yet" : "Could not write the action bar file"));
			return 0;
		}
		source.sendFeedback(result);
		return 1;
	}

	// Action bar written to actionbar.json
	public static Component saveActionBar() {
		Minecraft client = Minecraft.getInstance();
		if (actionBar == null) {
			return null;
		}
		DynamicOps<JsonElement> ops = client.level != null ? RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess()) : JsonOps.INSTANCE;
		JsonObject file = new JsonObject();
		file.addProperty("text", actionBar.getString());
		file.addProperty("codepoints", codepoints(actionBar.getString()));
		JsonArray parts = new JsonArray();
		actionBar.visit((style, text) -> {
			JsonObject part = new JsonObject();
			part.addProperty("text", text);
			part.addProperty("codepoints", codepoints(text));
			part.addProperty("font", String.valueOf(style.getFont()));
			part.addProperty("color", style.getColor() == null ? null : style.getColor().serialize());
			parts.add(part);
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		file.add("parts", parts);
		ComponentSerialization.CODEC.encodeStart(ops, actionBar).result().ifPresent(component -> file.add("component", component));
		file.addProperty("detected", detected == null ? "none" : detected.command);
		Path path = client.gameDirectory.toPath().resolve(Mccinfo.MOD_ID).resolve("actionbar.json");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(file));
		} catch (IOException e) {
			Mccinfo.LOGGER.warn("Could not save the action bar ({})", path, e);
			return null;
		}
		return Component.literal("Action bar saved to " + path);
	}

	private static String codepoints(String text) {
		StringBuilder builder = new StringBuilder();
		text.codePoints().forEach(codepoint -> {
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(codepoint < 0x80 ? Character.toString(codepoint) : String.format("U+%04X", codepoint));
		});
		return builder.toString();
	}
}
