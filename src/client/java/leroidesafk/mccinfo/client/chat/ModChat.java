package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.ChatComponentAccessor;
import leroidesafk.mccinfo.client.mixin.accessor.ChatScreenAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.scores.PlayerTeam;

public final class ModChat {
	private static final int COLOR = 0xFFC31E;
	private static final int COLOR_ERROR = 0xD05050;
	private static final GuiMessageTag TAG = new GuiMessageTag(0xFF000000 | COLOR, null, Component.literal("MccInfo"), "MccInfo");
	private static final int STAFF_COLOR = 0xBB1EFF;
	private static final GuiMessageTag STAFF_TAG = new GuiMessageTag(0xFF000000 | STAFF_COLOR, null, Component.literal("Mod"), "Mod");
	// Chat channels
	public static final int NORMAL = 0;
	public static final int MCCINFO = 1;
	public static final int STAFF = 2;
	private static final int NORMAL_INDICATOR = 0xFFFFFFFF;
	private static final Identifier ICON = Mccinfo.id("textures/gui/ui/mccinfochat.png");
	private static final Identifier HUD_FONT = Identifier.fromNamespaceAndPath("mcc", "hud");
	private static final FontDescription ICON_FONT = new FontDescription.Resource(Mccinfo.id("mod_chat"));
	// Moderation icons
	private static final String DELETE_ICON = "\ue001";
	private static final String BAN_ICON = "\ue002";
	private static final String UNBAN_ICON = "\ue003";
	private static final int ACTION_COLOR = 0xC8C8C8;
	private static final int ACTION_HOVER_COLOR = 0xFFFFFF;
	private static final int ACTION_MARGIN = 2;
	private static final Identifier ACTION_DELETE = Mccinfo.id("chat_delete");
	private static final Identifier ACTION_BAN = Mccinfo.id("chat_ban");
	private static final Identifier ACTION_UNBAN = Mccinfo.id("chat_unban");
	private static final int MAX_TRACKED = 400;

	private record ModMessage(String id, String uuid, String text, boolean staff, Component base) {
	}

	public record BanMessage(String text, long time) {
	}

	public record ChatBan(String uuid, String name, long date, String by, java.util.List<BanMessage> messages) {
	}

	public record VoiceBan(long date, String by) {
	}

	public record OnlinePlayer(String uuid, String name, String role) {
	}

	public record Sanction(String uuid, String name, boolean online, ChatBan chat, VoiceBan voice) {
	}

	private static java.util.List<OnlinePlayer> onlinePlayers = java.util.List.of();
	private static java.util.List<Sanction> sanctions = java.util.List.of();

	private static final java.util.Map<Component, ModMessage> MESSAGES = new java.util.IdentityHashMap<>();
	private static final java.util.Set<String> DELETED = new java.util.HashSet<>();
	private static final java.util.Map<String, ChatBan> BANS = new java.util.LinkedHashMap<>();

	private static final Component PREFIX = Component.literal("").withStyle(Style.EMPTY.withColor(COLOR).withFont(new FontDescription.Resource(Mccinfo.id("mod_chat"))));
	private static final Component STAFF_PREFIX = Component.literal("\ue000").withStyle(Style.EMPTY.withColor(STAFF_COLOR).withFont(new FontDescription.Resource(Mccinfo.id("mod_chat"))));
	// Chat buttons
	private static final int ICON_TEXTURE = 20;
	private static final int ICON_CELL = 10;
	private static final int ICON_WIDTH = 9;
	private static final int ICON_HEIGHT = 7;
	private static final int ICON_V = 2;
	private static final int BUTTON_HEIGHT = 9;
	private static final int PADDING = 2;
	private static final int ICON_GAP = 4;
	private static final int TRIDENT_BOTTOM = 26;
	private static final int STACK_GAP = 2;
	private static final boolean TRIDENT = FabricLoader.getInstance().isModLoaded("trident");
	// Shared names and items
	private static final int MAX_DISPLAY_LENGTH = 32768;
	private static final int MAX_DEPTH = 8;
	private static final int MAX_ITEM_LENGTH = 32768;
	private static final java.util.regex.Pattern ITEM_TOKEN = java.util.regex.Pattern.compile("<item(?::(\\d{1,2}))?>");
	private static final int MIN_ITEM_COUNT = 2;
	private static final int MAX_ITEM_COUNT = 99;
	private static final int ITEM_BOX_ALPHA = 0x80;
	private static final int ITEM_BOX_TOP = 2;
	private static final int ITEM_BOX_HEIGHT = 10;
	private static final int ITEM_SLOT = 0xE010;
	private static final float ITEM_SCALE = 0.5F;
	private static final java.util.Map<ItemStackTemplate, ItemStack> ITEM_STACKS = new java.util.WeakHashMap<>();
	private static final int INPUT_COLOR = 0xFFE0E0E0;
	private static final int HINT_COLOR = 0x707070;
	private static final int UNREAD_COLOR = 0xFFD83A3A;
	private static final int MAX_UNREAD = 99;
	private static final float NOTIFY_PITCH = 1.6F;

	private static int active;
	private static boolean open;
	private static int unread;
	private static int staffUnread;
	private static Button button;
	private static Button staffButton;
	private static long lastToggle;
	private static final long TOGGLE_DEBOUNCE_MILLIS = 250L;

	private ModChat() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen chatScreen) {
				setOpen(true);
				if (enabled()) {
					button = new Button(height, false);
					Screens.getWidgets(screen).add(button);
					staffButton = staffEnabled() ? new Button(height, true) : null;
					if (staffButton != null) {
						Screens.getWidgets(screen).add(staffButton);
					}
					styleInput(chatScreen);
				}
			}
		});
		// Leave a channel no longer allowed, follow the chat screen
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (active == MCCINFO && !enabled() || active == STAFF && !staffEnabled()) {
				setActive(NORMAL);
			}
			setOpen(client.gui.screen() instanceof ChatScreen);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			active = NORMAL;
			unread = 0;
			staffUnread = 0;
			button = null;
			staffButton = null;
		});
	}

	static boolean available() {
		return MccIsland.isConnected() && Permissions.has(Permissions.Perm.UTILS);
	}

	static boolean enabled() {
		return available() && ModConfig.get().modChat;
	}

	static boolean staffEnabled() {
		return enabled() && Permissions.isStaff();
	}

	static boolean boxEnabled() {
		return enabled() && ModConfig.get().modChatBox;
	}

	private static boolean boxShows(int channel) {
		return includes(ModConfig.get().modChatBoxChannel, channel);
	}

	private static boolean includes(String setting, int channel) {
		if (channel == STAFF && !Permissions.isStaff()) {
			return false;
		}
		String shown = Permissions.isStaff() && setting != null ? setting : "mccinfo";
		return switch (shown) {
			case "mccinfo" -> channel == MCCINFO;
			case "mod" -> channel == STAFF;
			default -> true;
		};
	}

	static boolean boxStaffOnly() {
		return Permissions.isStaff() && "mod".equals(ModConfig.get().modChatBoxChannel);
	}

	public static void settingsChanged() {
		if (active == MCCINFO && !enabled() || active == STAFF && !staffEnabled()) {
			setActive(NORMAL);
			return;
		}
		if (!ModConfig.get().modChat) {
			unread = 0;
			staffUnread = 0;
		}
		refresh();
	}

	private static void setOpen(boolean value) {
		if (open != value) {
			open = value;
			refresh();
		}
	}

	private static void refresh() {
		((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).mccinfo$refreshTrimmedMessages();
	}

	public static boolean isActive() {
		return active != NORMAL;
	}

	public static boolean visible(GuiMessage message) {
		int channel = channel(message);
		if (channel != NORMAL && !ModConfig.get().modChat || channel == STAFF && !Permissions.isStaff()) {
			return false;
		}
		return channel == active;
	}

	public static int channel(GuiMessage message) {
		return TAG.equals(message.tag()) ? MCCINFO : STAFF_TAG.equals(message.tag()) ? STAFF : NORMAL;
	}

	public static boolean isMod(GuiMessage message) {
		return channel(message) != NORMAL;
	}

	public static GuiMessageTag indicator(GuiMessageTag tag) {
		if (TAG.equals(tag) || STAFF_TAG.equals(tag)) {
			return tag;
		}
		return tag == null ? new GuiMessageTag(NORMAL_INDICATOR, null, null, null) : new GuiMessageTag(NORMAL_INDICATOR, tag.icon(), tag.text(), tag.logTag());
	}

	private static void setActive(int value) {
		if (active == value) {
			return;
		}
		active = value;
		if (active == MCCINFO) {
			unread = 0;
		} else if (active == STAFF) {
			staffUnread = 0;
		}
		Mccinfo.LOGGER.info("MccInfo chat: {}", active == MCCINFO ? "on" : active == STAFF ? "mod" : "off");
		Minecraft client = Minecraft.getInstance();
		refresh();
		client.gui.hud.getChat().resetChatScroll();
		if (client.gui.screen() instanceof ChatScreen chatScreen) {
			styleInput(chatScreen);
		}
	}

	private static void styleInput(ChatScreen screen) {
		EditBox input = ((ChatScreenAccessor) screen).mccinfo$getInput();
		if (input == null) {
			return;
		}
		input.setTextColor(INPUT_COLOR);
		input.setHint(active == MCCINFO ? Component.translatable("screen.mccinfo.mod_chat.hint").withColor(HINT_COLOR)
				: active == STAFF ? Component.translatable("screen.mccinfo.mod_chat.staff_hint").withColor(HINT_COLOR) : Component.empty());
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || button == null || !enabled()) {
			return false;
		}
		boolean staffClick = staffButton != null && staffEnabled() && staffButton.isMouseOver(event.x(), event.y());
		if (!staffClick && !button.isMouseOver(event.x(), event.y())) {
			return false;
		}
		// Ignore repeated clicks
		long now = System.currentTimeMillis();
		boolean pressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		if (!pressed || now - lastToggle < TOGGLE_DEBOUNCE_MILLIS) {
			Mccinfo.LOGGER.info("MccInfo chat: repeated click ignored");
			return true;
		}
		lastToggle = now;
		NeuItems.clickSound();
		int target = staffClick ? STAFF : MCCINFO;
		setActive(active == target ? NORMAL : target);
		return true;
	}

	public static boolean intercept(String message, boolean addToHistory) {
		String trimmed = Emojis.expand(message).strip();
		if (active == NORMAL || !enabled() || trimmed.isEmpty() || trimmed.startsWith("/")) {
			return false;
		}
		if (addToHistory) {
			Minecraft.getInstance().gui.hud.getChat().addRecentChat(trimmed);
		}
		Mccinfo.LOGGER.info("MccInfo chat: message sent to the relay");
		send(trimmed, active == STAFF);
		return true;
	}

	private static void send(String text, boolean staff) {
		String trimmed = text.strip();
		if (trimmed.isEmpty()) {
			return;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "mod_chat");
		message.addProperty("text", trimmed);
		if (staff) {
			message.addProperty("channel", "staff");
		}
		String display = ownDisplay();
		if (display != null) {
			message.addProperty("display", display);
		}
		// Shared item from the clipboard
		if (ITEM_TOKEN.matcher(trimmed).find()) {
			JsonElement item = ItemClipboard.item();
			if (item == null) {
				error(Component.translatable("message.mccinfo.mod_chat.no_item"));
				return;
			}
			String json = item.toString();
			if (json.length() > MAX_ITEM_LENGTH) {
				error(Component.translatable("message.mccinfo.mod_chat.item_too_big"));
				return;
			}
			message.addProperty("item", json);
		}
		if (!SpotSync.sendVoice(message)) {
			error(Component.translatable("message.mccinfo.mod_chat.offline"));
		}
	}

	public static void receive(JsonObject object) {
		if (!enabled() || !object.has("text") || !object.has("name")) {
			return;
		}
		String name = object.get("name").getAsString();
		Minecraft client = Minecraft.getInstance();
		boolean own = client.player != null && client.player.getGameProfile().name().equals(name);
		Component display = object.has("display") && object.get("display").isJsonPrimitive() ? readDisplay(object.get("display").getAsString(), name) : null;
		String text = object.get("text").getAsString();
		String id = object.has("id") && object.get("id").isJsonPrimitive() ? object.get("id").getAsString() : null;
		String uuid = object.has("uuid") && object.get("uuid").isJsonPrimitive() ? object.get("uuid").getAsString() : null;
		boolean staff = object.has("staff") && object.get("staff").getAsBoolean();
		boolean staffChannel = object.has("channel") && "staff".equals(object.get("channel").getAsString());
		if (staffChannel && !Permissions.isStaff()) {
			return;
		}
		// Icon, name, then the message
		MutableComponent line = Component.empty()
				.append(staffChannel ? STAFF_PREFIX : PREFIX)
				.append(Component.literal(" "))
				.append(display != null ? display : Component.literal(name).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
				.append(withItems(text, object.has("item") && object.get("item").isJsonPrimitive() ? readItem(object.get("item").getAsString()) : ItemStack.EMPTY,
						display != null && Emojis.hasPlus(display, name)));
		ModMessage message = new ModMessage(staffChannel ? null : id, uuid, text, staff, line);
		Component shown = decorate(message);
		int channel = staffChannel ? STAFF : MCCINFO;
		if (boxEnabled() && boxShows(channel)) {
			ModChatOverlay.add(line);
		}
		track(shown, message);
		addToChat(shown, staffChannel ? STAFF_TAG : TAG);
		// Unread count and sound, not for own messages
		if (own) {
			return;
		}
		if (staffChannel && active != STAFF) {
			staffUnread = Math.min(staffUnread + 1, MAX_UNREAD);
		} else if (!staffChannel && active != MCCINFO) {
			unread = Math.min(unread + 1, MAX_UNREAD);
		}
		if (ModConfig.get().modChatNotify && includes(ModConfig.get().modChatNotifyChannel, channel)) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, NOTIFY_PITCH));
		}
	}

	public static void receiveError(JsonObject object) {
		String reason = object.has("reason") ? object.get("reason").getAsString() : "";
		error(Component.translatable(switch (reason) {
			case "cooldown" -> "message.mccinfo.mod_chat.cooldown";
			case "banned" -> "message.mccinfo.mod_chat.banned";
			default -> "message.mccinfo.mod_chat.refused";
		}));
	}

	private static void track(Component shown, ModMessage message) {
		// Forget messages gone from the chat
		if (MESSAGES.size() >= MAX_TRACKED) {
			java.util.Set<Component> alive = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
			for (GuiMessage existing : chatMessages()) {
				alive.add(existing.content());
			}
			MESSAGES.keySet().retainAll(alive);
		}
		MESSAGES.put(shown, message);
	}

	private static final java.util.Set<Component> UNTRACKED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	private static ModMessage lookup(GuiMessage message) {
		if (channel(message) == NORMAL) {
			return null;
		}
		Component content = message.content();
		ModMessage found = MESSAGES.get(content);
		if (found != null || UNTRACKED.contains(content)) {
			return found;
		}
		// Content replaced by another mod
		String text = content.getString();
		for (ModMessage candidate : MESSAGES.values()) {
			if (text.contains(candidate.base().getString()) && (found == null || candidate.base().getString().length() > found.base().getString().length())) {
				found = candidate;
			}
		}
		if (found != null) {
			MESSAGES.put(content, found);
		} else {
			if (UNTRACKED.size() >= MAX_TRACKED) {
				UNTRACKED.clear();
			}
			UNTRACKED.add(content);
		}
		return found;
	}

	private static java.util.List<GuiMessage> chatMessages() {
		return ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).mccinfo$getAllMessages();
	}

	private static Component decorate(ModMessage message) {
		return message.base();
	}

	private record Action(String icon, Identifier id, String argument, String tooltip) {
	}

	private static java.util.List<Action> actions(ModMessage message) {
		java.util.List<Action> actions = new java.util.ArrayList<>();
		if (!DELETED.contains(message.id())) {
			actions.add(new Action(DELETE_ICON, ACTION_DELETE, message.id(), "screen.mccinfo.mod_chat.delete"));
		}
		if (!message.staff() && message.uuid() != null) {
			boolean banned = BANS.containsKey(message.uuid());
			actions.add(new Action(banned ? UNBAN_ICON : BAN_ICON, banned ? ACTION_UNBAN : ACTION_BAN, message.uuid(),
					banned ? "screen.mccinfo.mod_chat.unban" : "screen.mccinfo.mod_chat.ban"));
		}
		return actions;
	}

	public static void moderationIcons(ChatComponent.ChatGraphicsAccess graphics, GuiMessage.Line line, int textTop, float opacity) {
		if (!open || !line.endOfEntry() || !Permissions.isStaff() || channel(line.parent()) != MCCINFO) {
			return;
		}
		ModMessage message = lookup(line.parent());
		if (message == null || message.id() == null) {
			return;
		}
		java.util.List<Action> actions = actions(message);
		if (actions.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		float scale = client.options.chatScale().get().floatValue();
		int bandRight = Mth.ceil(ChatComponent.getWidth(client.options.chatWidth().get()) / scale) + 8;
		int[] widths = new int[actions.size()];
		int total = 0;
		for (int i = 0; i < actions.size(); i++) {
			widths[i] = client.font.width(action(actions.get(i), ACTION_COLOR));
			total += widths[i];
		}
		int x = bandRight - ACTION_MARGIN - total;
		MutableComponent icons = Component.empty();
		int iconX = x;
		for (int i = 0; i < actions.size(); i++) {
			boolean hovered = ChatCopy.mouseOver(iconX, textTop - 1, iconX + widths[i], textTop + 9);
			icons.append(action(actions.get(i), hovered ? ACTION_HOVER_COLOR : ACTION_COLOR));
			iconX += widths[i];
		}
		graphics.updatePose(pose -> pose.translate(x, 0.0F));
		graphics.handleMessage(textTop, opacity, icons.getVisualOrderText());
		graphics.updatePose(pose -> pose.translate(-x, 0.0F));
	}

	private static Component action(Action action, int color) {
		return Component.literal(action.icon()).withStyle(Style.EMPTY.withFont(ICON_FONT).withColor(color).withoutShadow()
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(action.tooltip())))
				.withClickEvent(new net.minecraft.network.chat.ClickEvent.Custom(action.id(), java.util.Optional.of(net.minecraft.nbt.StringTag.valueOf(action.argument())))));
	}

	public static boolean handleClick(net.minecraft.network.chat.ClickEvent event) {
		if (!(event instanceof net.minecraft.network.chat.ClickEvent.Custom(Identifier id, java.util.Optional<net.minecraft.nbt.Tag> payload))
				|| !id.getNamespace().equals(Mccinfo.MOD_ID)) {
			return false;
		}
		if (!Permissions.isStaff() || payload.isEmpty() || !(payload.get() instanceof net.minecraft.nbt.StringTag(String argument))) {
			return true;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "mod_chat_mod");
		message.addProperty("quiet", true);
		if (id.equals(ACTION_DELETE)) {
			message.addProperty("action", "delete");
			message.addProperty("id", argument);
		} else if (id.equals(ACTION_BAN) || id.equals(ACTION_UNBAN)) {
			message.addProperty("action", id.equals(ACTION_BAN) ? "ban" : "unban");
			message.addProperty("uuid", argument);
		} else {
			return false;
		}
		NeuItems.clickSound();
		if (!SpotSync.sendVoice(message)) {
			error(Component.translatable("message.mccinfo.mod_chat.offline"));
		}
		return true;
	}

	public static boolean unban(String uuid) {
		return moderate("unban", uuid);
	}

	public static boolean moderate(String action, String uuid) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "mod_chat_mod");
		message.addProperty("quiet", true);
		message.addProperty("action", action);
		message.addProperty("uuid", uuid);
		return SpotSync.sendVoice(message);
	}

	public static void receiveDelete(JsonObject object) {
		if (!object.has("id")) {
			return;
		}
		String id = object.get("id").getAsString();
		// Staff keeps it, shown in red
		if (Permissions.isStaff()) {
			DELETED.add(id);
			return;
		}
		boolean changed = false;
		for (java.util.Iterator<GuiMessage> iterator = chatMessages().iterator(); iterator.hasNext(); ) {
			GuiMessage existing = iterator.next();
			ModMessage message = lookup(existing);
			if (message != null && id.equals(message.id())) {
				iterator.remove();
				MESSAGES.remove(existing.content());
				ModChatOverlay.remove(message.base());
				changed = true;
			}
		}
		if (changed) {
			refresh();
		}
	}

	public static void receiveSanctions(JsonObject object) {
		BANS.clear();
		java.util.List<OnlinePlayer> online = new java.util.ArrayList<>();
		if (object.has("online") && object.get("online").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("online")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("uuid")) {
					JsonObject player = element.getAsJsonObject();
					online.add(new OnlinePlayer(player.get("uuid").getAsString(), string(player, "name", "?"), string(player, "role", "player")));
				}
			}
		}
		java.util.List<Sanction> list = new java.util.ArrayList<>();
		if (object.has("banned") && object.get("banned").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("banned")) {
				if (!element.isJsonObject() || !element.getAsJsonObject().has("uuid")) {
					continue;
				}
				JsonObject player = element.getAsJsonObject();
				String uuid = player.get("uuid").getAsString();
				String name = string(player, "name", "?");
				// Chat ban and its last messages
				ChatBan chat = null;
				if (player.has("chat") && player.get("chat").isJsonObject()) {
					JsonObject entry = player.getAsJsonObject("chat");
					java.util.List<BanMessage> messages = new java.util.ArrayList<>();
					if (entry.has("messages") && entry.get("messages").isJsonArray()) {
						for (JsonElement value : entry.getAsJsonArray("messages")) {
							if (value.isJsonObject() && value.getAsJsonObject().has("text")) {
								JsonObject text = value.getAsJsonObject();
								messages.add(new BanMessage(text.get("text").getAsString(), text.has("time") && !text.get("time").isJsonNull() ? text.get("time").getAsLong() : 0L));
							}
						}
					}
					chat = new ChatBan(uuid, name, entry.has("date") ? entry.get("date").getAsLong() : 0L, string(entry, "by", "?"), java.util.List.copyOf(messages));
					BANS.put(uuid, chat);
				}
				VoiceBan voice = null;
				if (player.has("voice") && player.get("voice").isJsonObject()) {
					JsonObject entry = player.getAsJsonObject("voice");
					voice = new VoiceBan(entry.has("date") ? entry.get("date").getAsLong() : 0L, string(entry, "by", "?"));
				}
				list.add(new Sanction(uuid, name, player.has("online") && player.get("online").getAsBoolean(), chat, voice));
			}
		}
		list.sort(java.util.Comparator.comparing((Sanction sanction) -> !sanction.online())
				.thenComparing(sanction -> sanction.name().toLowerCase(java.util.Locale.ROOT)));
		onlinePlayers = java.util.List.copyOf(online);
		sanctions = java.util.List.copyOf(list);
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
	}

	public static java.util.List<OnlinePlayer> onlinePlayers() {
		return onlinePlayers;
	}

	public static java.util.List<Sanction> sanctions() {
		return sanctions;
	}

	public static java.util.List<ChatBan> bans() {
		return java.util.List.copyOf(BANS.values());
	}

	public static boolean deletedForStaff(GuiMessage message) {
		ModMessage entry = lookup(message);
		return entry != null && entry.id() != null && DELETED.contains(entry.id());
	}

	static String copyText(GuiMessage message) {
		if (channel(message) == NORMAL) {
			return null;
		}
		ModMessage entry = lookup(message);
		if (entry == null) {
			return ChatCopy.clean(message.content().getString());
		}
		return ChatCopy.clean(ITEM_TOKEN.matcher(entry.text()).replaceAll(" "));
	}

	private static String ownDisplay() {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection == null || client.player == null || client.level == null) {
			return null;
		}
		Component display = tabLine(connection, client.player.getGameProfile().name());
		// Fallback to the player entry
		if (display == null) {
			PlayerInfo info = connection.getPlayerInfo(client.player.getUUID());
			if (info == null) {
				return null;
			}
			display = info.getTabListDisplayName() != null ? info.getTabListDisplayName()
					: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
		}
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess());
		String json = encode(ops, clean(display, 0));
		if (json == null) {
			json = encode(ops, flatten(display));
		}
		if (json == null) {
			Mccinfo.LOGGER.warn("MccInfo chat: could not send the MCC Island name ({})", display.getString());
		}
		return json;
	}

	static Component tabLine(ClientPacketListener connection, String name) {
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			Component line = info.getTabListDisplayName();
			if (line != null && line.visit((style, text) -> text.equals(name) ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty(), Style.EMPTY).isPresent()) {
				return line;
			}
		}
		return null;
	}

	public static int dumpTab(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection == null || client.player == null || client.level == null) {
			return 0;
		}
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess());
		com.google.gson.JsonArray players = new com.google.gson.JsonArray();
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", info.getProfile().name());
			entry.addProperty("self", info.getProfile().id().equals(client.player.getUUID()));
			Component tabName = info.getTabListDisplayName();
			entry.add("tabListDisplayName", tabName == null ? null : ComponentSerialization.CODEC.encodeStart(ops, tabName).result().orElse(null));
			PlayerTeam team = info.getTeam();
			if (team != null) {
				entry.addProperty("team", team.getName());
				entry.add("teamPrefix", ComponentSerialization.CODEC.encodeStart(ops, team.getPlayerPrefix()).result().orElse(null));
				entry.add("teamSuffix", ComponentSerialization.CODEC.encodeStart(ops, team.getPlayerSuffix()).result().orElse(null));
			}
			Component decorated = tabName != null ? tabName : PlayerTeam.formatNameForTeam(team, Component.literal(info.getProfile().name()));
			entry.addProperty("decoratedText", decorated.getString());
			entry.add("decorated", ComponentSerialization.CODEC.encodeStart(ops, decorated).result().orElse(null));
			players.add(entry);
		}
		String own = ownDisplay();
		JsonObject file = new JsonObject();
		file.addProperty("sentDisplayLength", own == null ? -1 : own.length());
		file.add("players", players);
		java.nio.file.Path path = client.gameDirectory.toPath().resolve(Mccinfo.MOD_ID).resolve("tab.json");
		try {
			java.nio.file.Files.createDirectories(path.getParent());
			java.nio.file.Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(file));
		} catch (java.io.IOException e) {
			source.sendError(Component.literal("Could not write " + path));
			return 0;
		}
		source.sendFeedback(Component.literal("Tab saved to " + path + " (" + players.size() + " players, name sent: "
				+ (own == null ? "none" : own.length() + " characters") + ")"));
		return 1;
	}

	private static String encode(RegistryOps<JsonElement> ops, Component component) {
		String json = ComponentSerialization.CODEC.encodeStart(ops, component).result().map(JsonElement::toString).orElse(null);
		return json != null && json.length() <= MAX_DISPLAY_LENGTH ? json : null;
	}

	private static MutableComponent flatten(Component component) {
		MutableComponent result = Component.empty();
		component.visit((style, text) -> {
			result.append(Component.literal(text).setStyle(style.withClickEvent(null).withHoverEvent(null).withInsertion(null)));
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return result;
	}

	private static Component readDisplay(String json, String name) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || json.length() > MAX_DISPLAY_LENGTH) {
			return null;
		}
		try {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess());
			Component display = ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(null);
			if (display == null || !display.getString().contains(name)) {
				Mccinfo.LOGGER.warn("MccInfo chat: MCC Island name of {} refused ({})", name, display == null ? "unreadable" : display.getString());
				return null;
			}
			return clean(display, 0);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static MutableComponent clean(Component component, int depth) {
		net.minecraft.network.chat.ComponentContents contents = component.getContents();
		// No alignment dots
		if (contents instanceof net.minecraft.network.chat.contents.PlainTextContents plain && plain.text().indexOf('.') >= 0) {
			contents = net.minecraft.network.chat.contents.PlainTextContents.create(plain.text().replace(".", ""));
		}
		MutableComponent copy = MutableComponent.create(contents)
				.setStyle(component.getStyle().withClickEvent(null).withHoverEvent(null).withInsertion(null));
		if (depth < MAX_DEPTH) {
			for (Component sibling : component.getSiblings()) {
				copy.append(clean(sibling, depth + 1));
			}
		}
		return copy;
	}

	private static ItemStack readItem(String json) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || json.length() > MAX_ITEM_LENGTH) {
			return ItemStack.EMPTY;
		}
		try {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess());
			return ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	private static Component withItems(String text, ItemStack stack, boolean emojis) {
		MutableComponent result = Component.empty().withStyle(ChatFormatting.WHITE);
		java.util.regex.Matcher matcher = ITEM_TOKEN.matcher(text);
		int last = 0;
		while (matcher.find()) {
			int count = matcher.group(1) == null ? 1 : Integer.parseInt(matcher.group(1));
			// Invalid tokens stay text
			if (stack.isEmpty() || matcher.group(1) != null && (count < MIN_ITEM_COUNT || count > MAX_ITEM_COUNT)) {
				continue;
			}
			Emojis.appendText(result, text.substring(last, matcher.start()), emojis);
			result.append(itemPiece(stack, count));
			last = matcher.end();
		}
		Emojis.appendText(result, text.substring(last), emojis);
		return result;
	}

	private static Component itemPiece(ItemStack stack, int count) {
		ItemStack shown = ChatItemTooltip.mark(stack.copyWithCount(1));
		MutableComponent piece = Component.empty().withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)
				.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(shown))));
		if (count > 1) {
			piece.append(Component.literal(String.valueOf(count)));
		}
		piece.append(Component.literal(Character.toString(ITEM_SLOT)).withStyle(Style.EMPTY.withFont(ICON_FONT)));
		return piece;
	}

	public static void chatItems(ChatComponent.ChatGraphicsAccess access, GuiMessage.Line line, int textTop, float opacity) {
		if (!isMod(line.parent())) {
			return;
		}
		GuiGraphicsExtractor graphics = access instanceof leroidesafk.mccinfo.client.mixin.accessor.ChatFocusedGraphicsAccessor focused ? focused.mccinfo$getGraphics()
				: access instanceof leroidesafk.mccinfo.client.mixin.accessor.ChatBackgroundGraphicsAccessor background ? background.mccinfo$getGraphics() : null;
		if (graphics != null) {
			extractItems(graphics, line.content(), 0, textTop, opacity);
		}
	}

	public static void extractItems(GuiGraphicsExtractor graphics, FormattedCharSequence line, float x, float y, float alpha) {
		Font font = Minecraft.getInstance().font;
		java.util.List<Object[]> slots = new java.util.ArrayList<>();
		float[] cursor = {0.0F};
		line.accept((index, style, codepoint) -> {
			if (codepoint == ITEM_SLOT && style.getHoverEvent() instanceof HoverEvent.ShowItem(ItemStackTemplate template)) {
				slots.add(new Object[] {cursor[0], template});
			}
			cursor[0] += font.width(FormattedCharSequence.codepoint(codepoint, style));
			return true;
		});
		if (slots.isEmpty() || alpha <= 0.0F) {
			return;
		}
		for (Object[] slot : slots) {
			ItemStack stack = ITEM_STACKS.computeIfAbsent((ItemStackTemplate) slot[1], ItemStackTemplate::create);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + (float) slot[0], y - 1.0F);
			graphics.pose().scale(ITEM_SCALE, ITEM_SCALE);
			leroidesafk.mccinfo.client.gui.ScreenOpenAnimation.withAlpha(Mth.clamp(alpha, 0.0F, 1.0F), () -> graphics.fakeItem(stack, 0, 0));
			graphics.pose().popMatrix();
		}
	}

	public static void itemBackgrounds(ChatComponent.ChatGraphicsAccess graphics, GuiMessage.Line line, int textTop, float opacity) {
		if (!isMod(line.parent())) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		java.util.List<int[]> spans = new java.util.ArrayList<>();
		float[] x = {0.0F};
		Object[] open = {null};
		int[] start = {0};
		line.content().accept((index, style, codepoint) -> {
			Object hover = style.getHoverEvent() instanceof HoverEvent.ShowItem ? style.getHoverEvent() : null;
			int left = Math.round(x[0]);
			if (hover != open[0]) {
				if (open[0] != null) {
					spans.add(new int[] {start[0], left});
				}
				open[0] = hover;
				start[0] = left;
			}
			x[0] += font.width(FormattedCharSequence.codepoint(codepoint, style));
			return true;
		});
		if (open[0] != null) {
			spans.add(new int[] {start[0], Math.round(x[0])});
		}
		// Dark box behind each item
		int color = Math.round(ITEM_BOX_ALPHA * Mth.clamp(opacity, 0.0F, 1.0F)) << 24;
		for (int[] span : spans) {
			int left = span[0] - 1;
			int right = span[1] + 1;
			int top = textTop - ITEM_BOX_TOP;
			int bottom = top + ITEM_BOX_HEIGHT;
			graphics.fill(left, top + 1, left + 1, bottom - 1, color);
			graphics.fill(left + 1, top, right - 1, bottom, color);
			graphics.fill(right - 1, top + 1, right, bottom - 1, color);
		}
	}

	private static void error(Component text) {
		add(text.copy().withColor(COLOR_ERROR));
	}

	private static void add(Component line) {
		int channel = active == STAFF ? STAFF : MCCINFO;
		if (boxEnabled() && boxShows(channel)) {
			ModChatOverlay.add(line);
		}
		addToChat(line, channel == STAFF ? STAFF_TAG : TAG);
	}

	// Tag kept if another mod changes it
	private static GuiMessageTag forcedTag;

	private static void addToChat(Component line, GuiMessageTag tag) {
		forcedTag = tag;
		try {
			Minecraft.getInstance().gui.hud.getChat().addPlayerMessage(line, null, tag);
		} finally {
			forcedTag = null;
		}
	}

	public static GuiMessageTag keepTag(GuiMessageTag tag) {
		return forcedTag != null ? forcedTag : tag;
	}

	private static final class Button extends AbstractWidget {
		private final boolean staff;

		Button(int screenHeight, boolean staff) {
			super(PADDING, screenHeight - TRIDENT_BOTTOM - (TRIDENT ? BUTTON_HEIGHT + STACK_GAP : 0), 0, BUTTON_HEIGHT, Component.empty());
			this.staff = staff;
			setWidth(Minecraft.getInstance().font.width(label()) + ICON_WIDTH + PADDING * 2 + ICON_GAP);
		}

		private Component label() {
			return hud(this.staff ? "MOD" : "MCCINFO");
		}

		private int unreadCount() {
			return this.staff ? staffUnread : unread;
		}

		private int unreadWidth(Font font) {
			return unreadCount() > 0 ? font.width(hud(String.valueOf(unreadCount()))) + PADDING * 2 + STACK_GAP : 0;
		}

		private static Component hud(String text) {
			return Component.literal(text).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(HUD_FONT)));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			Font font = Minecraft.getInstance().font;
			Component label = label();
			setWidth(font.width(label) + ICON_WIDTH + PADDING * 2 + ICON_GAP);
			if (this.staff && button != null) {
				setX(button.getX() + button.getWidth() + STACK_GAP + button.unreadWidth(font));
			}
			int x = getX();
			int y = getY();
			int color = this.staff ? STAFF_COLOR : COLOR;
			boolean selected = ModChat.active == (this.staff ? STAFF : MCCINFO);
			int background = selected ? shade(color, isHovered() ? 0.9F : 0.8F, 0xFF) : shade(color, isHovered() ? 0.5F : 0.25F, 0x80);
			OverlayStyle.fillRounded(graphics, x, y, this.width, this.height, background);
			graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, x + PADDING, y + 1, this.staff ? ICON_CELL : 0.0F, ICON_V, ICON_WIDTH, ICON_HEIGHT, ICON_TEXTURE, ICON_TEXTURE);
			graphics.text(font, label, x + PADDING + ICON_WIDTH + ICON_GAP, y, 0xFFFFFFFF, false);
			int count = unreadCount();
			// Unread badge
			if (count > 0) {
				Component countText = hud(String.valueOf(count));
				int countX = x + this.width + STACK_GAP;
				OverlayStyle.fillRounded(graphics, countX, y, font.width(countText) + PADDING * 2, this.height, UNREAD_COLOR);
				graphics.text(font, countText, countX + PADDING, y, 0xFFFFFFFF, false);
			}
		}

		private static int shade(int color, float factor, int alpha) {
			int r = Math.round((color >> 16 & 0xFF) * factor);
			int g = Math.round((color >> 8 & 0xFF) * factor);
			int b = Math.round((color & 0xFF) * factor);
			return alpha << 24 | r << 16 | g << 8 | b;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
