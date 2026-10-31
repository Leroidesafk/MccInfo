package leroidesafk.mccinfo.client.voice;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.MccServer;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.EventsScreen;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

public final class VoiceChat {
	// Simple Voice Chat installed
	private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("voicechat");

	public static boolean present() {
		return PRESENT;
	}
	// Relay connection and state refresh
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static final long RETRY_MILLIS = 15_000L;
	private static final long STATE_REFRESH_MILLIS = 10_000L;
	private static final double NEARBY_RANGE = 64.0;
	private static final int SECRET_BYTES = 16;
	private static final int MAX_STATES = 256;
	private static final int GROUP_TEXT_LENGTH = 512;
	// Group packets sent through the relay
	private static final Set<String> GROUP_PACKETS = Set.of("create_group", "set_group", "leave_group");
	// Simple Voice Chat icons
	private static final Identifier SPEAKER_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/speaker");
	private static final Identifier WHISPER_SPEAKER_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/speaker_whisper");
	private static final Identifier SPEAKER_OFF_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/speaker_off");
	private static final Identifier DISCONNECT_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/disconnected");
	private static final int ICON_TRANSLUCENT = 0x80FFFFFF;
	private static final float NAMEPLATE_SCALE = 0.025F;
	private static final float ICON_SCALE = 10.0F / 8.0F;
	// Current state
	private static boolean injectedStates;

	private static boolean broken;
	private static boolean controlsWarned;
	private static int ticks;
	private static Object requestedFor;
	private static long requestedAt;
	private static String lastState;
	private static long lastStateAt;

	private VoiceChat() {
	}

	public static void register() {
		if (!PRESENT) {
			return;
		}
		VoiceDisclaimerScreen.register();
		ClientTickEvents.END_CLIENT_TICK.register(VoiceChat::tick);
	}

	private static boolean usable(Minecraft client) {
		return !broken && ModConfig.get().voiceChatEnabled && client.player != null && client.level != null && MccIsland.isConnected()
				&& SpotSync.isAuthenticated() && Permissions.has(Permissions.Perm.VOICE_CHAT);
	}

	// Joins the relay voice server, sends the nearby players
	private static void tick(Minecraft client) {
		if (++ticks % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		if (!usable(client)) {
			if (requestedFor != null) {
				JsonObject message = new JsonObject();
				message.addProperty("type", "voice_leave");
				SpotSync.sendVoice(message);
			}
			requestedFor = null;
			lastState = null;
			clearStates();
			VoiceAdmin.reset();
			return;
		}
		Object svc;
		boolean connected;
		boolean active;
		try {
			svc = Svc.client();
			connected = svc != null && Svc.isConnected(svc);
			active = !Svc.isDisabled();
		} catch (ReflectiveOperationException | LinkageError e) {
			fail(e);
			return;
		}
		if (svc == null) {
			requestedFor = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (!connected && (svc != requestedFor || now - requestedAt > RETRY_MILLIS)) {
			JsonObject message = new JsonObject();
			message.addProperty("type", "voice_join");
			if (SpotSync.sendVoice(message)) {
				requestedFor = svc;
				requestedAt = now;
				lastState = null;
				VoiceAdmin.resend();
			}
		}
		sendState(client, now, active);
	}

	// Server, nearby players and active state for the relay
	private static void sendState(Minecraft client, long now, boolean active) {
		String server = MccServer.server();
		JsonArray types = new JsonArray();
		MccServer.types().forEach(types::add);
		List<String> nearby = new ArrayList<>();
		LocalPlayer self = client.player;
		for (Player player : client.level.players()) {
			if (player != self && player.distanceTo(self) <= NEARBY_RANGE) {
				nearby.add(hex(player.getUUID()));
			}
		}
		nearby.sort(null);
		JsonArray players = new JsonArray();
		nearby.forEach(players::add);
		JsonObject message = new JsonObject();
		message.addProperty("type", "voice_state");
		message.addProperty("server", server == null ? "" : server);
		message.add("types", types);
		message.add("players", players);
		message.addProperty("active", active);
		String text = message.toString();
		if (text.equals(lastState) && now - lastStateAt < STATE_REFRESH_MILLIS) {
			return;
		}
		if (SpotSync.sendVoice(message)) {
			lastState = text;
			lastStateAt = now;
		}
	}

	// Relay secret given to Simple Voice Chat as if it came from the server
	public static void receiveSecret(JsonObject object) {
		Minecraft client = Minecraft.getInstance();
		if (!PRESENT || !usable(client)) {
			return;
		}
		try {
			byte[] secret = Base64.getDecoder().decode(object.get("secret").getAsString());
			if (secret.length != SECRET_BYTES) {
				return;
			}
			UUID uuid = fromHex(object.get("uuid").getAsString());
			int port = object.get("port").getAsInt();
			String host = object.has("host") ? object.get("host").getAsString() : "";
			if (host.isEmpty()) {
				URI relay = SpotSync.httpUri("/");
				if (relay == null || relay.getHost() == null) {
					return;
				}
				host = relay.getHost();
			}
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeBytes(secret);
			buf.writeInt(port);
			buf.writeUUID(uuid);
			buf.writeByte(object.get("codec").getAsInt());
			buf.writeInt(object.get("mtu").getAsInt());
			buf.writeDouble(object.get("distance").getAsDouble());
			buf.writeInt(object.get("keepAlive").getAsInt());
			buf.writeBoolean(true);
			buf.writeUtf(host + ":" + port);
			buf.writeBoolean(false);
			Svc.injectSecret(client.player, buf);
			Mccinfo.LOGGER.info("Voice chat: connecting to {}:{}", host, port);
		} catch (ReflectiveOperationException | LinkageError e) {
			fail(e);
		} catch (RuntimeException e) {
			Mccinfo.LOGGER.warn("Voice chat: invalid secret from the relay ({})", e.toString());
		}
	}

	// Voice players shown in the Simple Voice Chat lists
	public static void receivePlayers(JsonObject object) {
		Minecraft client = Minecraft.getInstance();
		if (!PRESENT || !usable(client) || !object.has("players") || !object.get("players").isJsonArray()) {
			return;
		}
		try {
			Map<UUID, Object> states = new HashMap<>();
			for (JsonElement element : object.getAsJsonArray("players")) {
				if (states.size() >= MAX_STATES || !element.isJsonObject()) {
					continue;
				}
				JsonObject player = element.getAsJsonObject();
				UUID uuid = fromHex(player.get("uuid").getAsString());
				String name = player.has("name") ? player.get("name").getAsString() : uuid.toString();
				boolean disabled = player.has("disabled") && player.get("disabled").getAsBoolean();
				UUID group = player.has("group") && player.get("group").isJsonPrimitive() ? fromHex(player.get("group").getAsString()) : null;
				states.put(uuid, Svc.playerState(uuid, name, disabled, group));
			}
			Svc.setStates(states);
			injectedStates = true;
		} catch (ReflectiveOperationException | LinkageError e) {
			fail(e);
		} catch (RuntimeException e) {
			Mccinfo.LOGGER.warn("Voice chat: invalid player list from the relay ({})", e.toString());
		}
	}

	private static void clearStates() {
		if (!injectedStates) {
			return;
		}
		injectedStates = false;
		try {
			Svc.setStates(new HashMap<>());
		} catch (ReflectiveOperationException | LinkageError e) {
			fail(e);
		}
	}

	// MCC Island nameplates: whose name a text display shows
	private record NameText(Component name, String text) {
	}

	private static final Map<Display.TextDisplay, NameText> NAME_TEXTS = new java.util.WeakHashMap<>();

	public static UUID nameplateOwner(Display.TextDisplay display) {
		if (!PRESENT || broken || !ModConfig.get().voiceChatIcons || !display.isCustomNameVisible()) {
			return null;
		}
		Component name = display.getCustomName();
		if (name == null || !usable(Minecraft.getInstance())) {
			return null;
		}
		Entity root = display.getRootVehicle();
		if (!(root instanceof Player player) || player == Minecraft.getInstance().player) {
			return null;
		}
		NameText cached = NAME_TEXTS.get(display);
		if (cached == null || cached.name() != name) {
			cached = new NameText(name, name.getString().strip());
			NAME_TEXTS.put(display, cached);
		}
		return cached.text().equals(player.getGameProfile().name()) ? player.getUUID() : null;
	}

	// Voice icon above the MCC Island nameplate
	public static void submitNameplateIcon(EntityRenderState state, UUID player, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
		Minecraft client = Minecraft.getInstance();
		if (broken || state.nameTag == null || state.nameTagAttachment == null || client.gui.hud.isHidden() || !usable(client)) {
			return;
		}
		Identifier texture;
		try {
			texture = Svc.nameplateIcon(player);
		} catch (ReflectiveOperationException | LinkageError e) {
			fail(e);
			return;
		}
		if (texture == null) {
			return;
		}
		FormattedCharSequence icon = Component.object(new AtlasSprite(AtlasIds.GUI, texture)).getVisualOrderText();
		stack.pushPose();
		stack.translate(state.nameTagAttachment);
		stack.translate(0.0, 0.5, 0.0);
		stack.mulPose(camera.orientation);
		stack.scale(NAMEPLATE_SCALE, -NAMEPLATE_SCALE, NAMEPLATE_SCALE);
		stack.translate(client.font.width(state.nameTag) / 2 + 2.0F, -1.0F, 0.0F);
		stack.scale(ICON_SCALE, ICON_SCALE, 1.0F);
		collector.submitText(stack, 0.0F, 1.0F, icon, false, Font.DisplayMode.NORMAL, state.lightCoords, state.isDiscrete ? ICON_TRANSLUCENT : 0xFFFFFFFF, 0, 0);
		if (!state.isDiscrete) {
			collector.submitText(stack, 0.0F, 1.0F, icon, false, Font.DisplayMode.SEE_THROUGH, state.lightCoords, ICON_TRANSLUCENT, 0, 0);
		}
		stack.popPose();
	}

	// Group actions go to the relay instead of the server
	public static boolean interceptGroupPacket(CustomPacketPayload payload) {
		if (!PRESENT || broken) {
			return false;
		}
		Identifier id = payload.type().id();
		if (!id.getNamespace().equals("voicechat") || !GROUP_PACKETS.contains(id.getPath()) || !usable(Minecraft.getInstance())) {
			return false;
		}
		try {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			payload.getClass().getMethod("toBytes", FriendlyByteBuf.class).invoke(payload, buf);
			JsonObject message = new JsonObject();
			message.addProperty("type", "voice_group");
			switch (id.getPath()) {
				case "create_group" -> {
					message.addProperty("action", "create");
					message.addProperty("name", buf.readUtf(GROUP_TEXT_LENGTH));
					if (buf.readBoolean()) {
						message.addProperty("password", buf.readUtf(GROUP_TEXT_LENGTH));
					}
					message.addProperty("groupType", buf.readShort());
				}
				case "set_group" -> {
					message.addProperty("action", "join");
					message.addProperty("group", hex(buf.readUUID()));
					if (buf.readBoolean()) {
						message.addProperty("password", buf.readUtf(GROUP_TEXT_LENGTH));
					}
				}
				default -> message.addProperty("action", "leave");
			}
			SpotSync.sendVoice(message);
		} catch (ReflectiveOperationException | RuntimeException e) {
			warnControls(e);
		}
		return true;
	}

	// Groups from the relay
	public static void receiveGroupAdd(JsonObject object) {
		if (!PRESENT || broken || !object.has("group") || !object.get("group").isJsonObject()) {
			return;
		}
		JsonObject group = object.getAsJsonObject("group");
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			buf.writeUUID(fromHex(group.get("id").getAsString()));
			buf.writeUtf(group.get("name").getAsString(), GROUP_TEXT_LENGTH);
			buf.writeBoolean(group.has("password") && group.get("password").getAsBoolean());
			buf.writeBoolean(false);
			buf.writeBoolean(false);
			buf.writeShort(group.has("type") ? group.get("type").getAsInt() : 0);
		} catch (RuntimeException e) {
			Mccinfo.LOGGER.warn("Voice chat: invalid group from the relay ({})", e.toString());
			return;
		}
		injectGroupPacket("addGroupChannel", "de.maxhenkel.voicechat.net.AddGroupPacket", buf);
	}

	public static void receiveGroupRemove(JsonObject object) {
		if (!PRESENT || broken || !object.has("id")) {
			return;
		}
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			buf.writeUUID(fromHex(object.get("id").getAsString()));
		} catch (RuntimeException e) {
			return;
		}
		injectGroupPacket("removeGroupChannel", "de.maxhenkel.voicechat.net.RemoveGroupPacket", buf);
	}

	public static void receiveGroupJoined(JsonObject object) {
		if (!PRESENT || broken) {
			return;
		}
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			boolean inGroup = object.has("group") && object.get("group").isJsonPrimitive();
			buf.writeBoolean(inGroup);
			if (inGroup) {
				buf.writeUUID(fromHex(object.get("group").getAsString()));
			}
			buf.writeBoolean(object.has("wrongPassword") && object.get("wrongPassword").getAsBoolean());
		} catch (RuntimeException e) {
			return;
		}
		if (object.has("wrongPassword") && object.get("wrongPassword").getAsBoolean()
				&& Minecraft.getInstance().gui.screen() instanceof VoiceGroupScreen screen) {
			screen.wrongPassword();
		}
		injectGroupPacket("joinedGroupChannel", "de.maxhenkel.voicechat.net.JoinedGroupPacket", buf);
	}

	private static void injectGroupPacket(String channel, String packet, FriendlyByteBuf buf) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		try {
			Svc.inject(client.player, channel, packet, buf);
		} catch (ReflectiveOperationException | LinkageError e) {
			fail(e);
		} catch (RuntimeException e) {
			Mccinfo.LOGGER.warn("Voice chat: could not apply a group update ({})", e.toString());
		}
	}

	// Microphone, speaker and HUD buttons of the menu
	public static boolean muted() {
		return svcFlag(Svc::muted);
	}

	public static boolean canMute() {
		return svcFlag(Svc::canMute);
	}

	public static boolean deafened() {
		return svcFlag(Svc::isDisabled);
	}

	public static boolean canDeafen() {
		return svcFlag(Svc::canEnable);
	}

	public static boolean hudIconsHidden() {
		return svcFlag(() -> !Svc.hudIcons());
	}

	public static void toggleMuted() {
		svcRun(() -> Svc.setMuted(!Svc.muted()));
	}

	public static void toggleDeafened() {
		svcRun(() -> Svc.setDisabled(!Svc.isDisabled()));
	}

	public static void toggleHudIcons() {
		svcRun(() -> Svc.setHudIcons(!Svc.hudIcons()));
	}

	// Player volumes
	public static final UUID OTHERS = new UUID(0L, 0L);

	public record VolumePlayer(UUID id, String name) {
	}

	public static List<VolumePlayer> volumePlayers() {
		List<VolumePlayer> players = new ArrayList<>();
		svcRun(() -> players.addAll(Svc.volumePlayers()));
		players.sort(Comparator.comparing(player -> player.name().toLowerCase(Locale.ROOT)));
		return players;
	}

	public static double volume(UUID player) {
		double[] value = {1.0};
		svcRun(() -> value[0] = Svc.volume(player));
		return value[0];
	}

	public static void setVolume(UUID player, String name, double value) {
		svcRun(() -> Svc.setVolume(player, value, name == null ? "All other volumes" : String.format("Volume of %s", name)));
	}

	public static void saveVolumes() {
		svcRun(Svc::saveVolumes);
	}

	// MccInfo screens instead of the Simple Voice Chat ones
	public static net.minecraft.client.gui.screens.Screen replaceScreen(net.minecraft.client.gui.screens.Screen current, net.minecraft.client.gui.screens.Screen next) {
		if (!PRESENT || next == null || current != null || !MccIsland.isConnected() || !Permissions.has(Permissions.Perm.VOICE_CHAT)) {
			return null;
		}
		return switch (next.getClass().getName()) {
			case "de.maxhenkel.voicechat.gui.VoiceChatScreen" -> EventsScreen.voiceChat(false);
			case "de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen" -> EventsScreen.voiceChat(true);
			case "de.maxhenkel.voicechat.gui.volume.AdjustVolumesScreen" -> new VoiceVolumesScreen(EventsScreen.voiceChat(false));
			case "de.maxhenkel.voicechat.gui.group.GroupScreen", "de.maxhenkel.voicechat.gui.group.JoinGroupScreen" ->
					new VoiceGroupScreen(EventsScreen.voiceChat(false));
			default -> null;
		};
	}

	public static final int GROUP_NORMAL = 0;
	public static final int GROUP_OPEN = 1;
	public static final int GROUP_ISOLATED = 2;
	public static final int GROUP_NAME_LENGTH = 24;
	public static final int GROUP_PASSWORD_LENGTH = 32;
	// Voice groups
	private static final java.util.regex.Pattern GROUP_NAME = java.util.regex.Pattern.compile("^[^\\p{C}\\s][^\\p{C}]{0,23}$");

	public record GroupMember(UUID id, String name, Identifier icon) {
	}

	public record VoiceGroup(UUID id, String name, boolean password, int type, List<GroupMember> members) {
	}

	public static boolean groupsUsable() {
		return PRESENT && usable(Minecraft.getInstance());
	}

	public static boolean validGroupName(String name) {
		return GROUP_NAME.matcher(name).matches();
	}

	public static List<VoiceGroup> groups() {
		List<VoiceGroup> groups = new ArrayList<>();
		svcRun(() -> groups.addAll(Svc.groups()));
		groups.sort(Comparator.comparing(group -> group.name().toLowerCase(Locale.ROOT)));
		return groups;
	}

	public static VoiceGroup currentGroup() {
		VoiceGroup[] group = {null};
		svcRun(() -> group[0] = Svc.currentGroup());
		return group[0];
	}

	public static boolean groupHudShown() {
		return svcFlag(Svc::groupHud);
	}

	public static void toggleGroupHud() {
		svcRun(() -> Svc.setGroupHud(!Svc.groupHud()));
	}

	public static boolean createGroup(String name, String password, int type) {
		JsonObject message = groupMessage("create");
		message.addProperty("name", name);
		if (password != null && !password.isEmpty()) {
			message.addProperty("password", password);
		}
		message.addProperty("groupType", type);
		return SpotSync.sendVoice(message);
	}

	public static boolean joinGroup(UUID group, String password) {
		JsonObject message = groupMessage("join");
		message.addProperty("group", hex(group));
		if (password != null && !password.isEmpty()) {
			message.addProperty("password", password);
		}
		return SpotSync.sendVoice(message);
	}

	public static boolean leaveGroup() {
		return SpotSync.sendVoice(groupMessage("leave"));
	}

	private static JsonObject groupMessage(String action) {
		JsonObject message = new JsonObject();
		message.addProperty("type", "voice_group");
		message.addProperty("action", action);
		return message;
	}

	// Safe calls to Simple Voice Chat
	private interface SvcFlag {
		boolean get() throws ReflectiveOperationException;
	}

	private interface SvcAction {
		void run() throws ReflectiveOperationException;
	}

	private static boolean svcFlag(SvcFlag flag) {
		if (!PRESENT) {
			return false;
		}
		try {
			return flag.get();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warnControls(error);
			return false;
		}
	}

	private static void svcRun(SvcAction action) {
		if (!PRESENT) {
			return;
		}
		try {
			action.run();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warnControls(error);
		}
	}

	private static void warnControls(Throwable error) {
		if (!controlsWarned) {
			controlsWarned = true;
			Mccinfo.LOGGER.warn("Voice chat: could not use the Simple Voice Chat settings ({})", error.toString());
		}
	}

	private static void fail(Throwable error) {
		broken = true;
		Mccinfo.LOGGER.warn("Voice chat: this Simple Voice Chat version is not supported, bridge disabled ({})", error.toString());
	}

	private static String hex(UUID uuid) {
		return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static UUID fromHex(String hex) {
		if (hex.length() != 32) {
			throw new IllegalArgumentException("uuid");
		}
		return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
	}

	// Simple Voice Chat reached by reflection
	private static final class Svc {
		private static Method getClient;
		private static Method getPlayerStateManager;
		private static Method isDisabled;
		private static Method getConnection;
		private static Method isInitialized;
		private static Object secretChannel;
		private static Object netManager;
		private static Method setGroup;
		private static Method updateJoinGroupList;
		private static Method updateGroupList;
		private static Method onClientPacket;
		private static Class<?> secretPacket;
		private static Method fromBytes;
		private static Method getTalkCache;
		private static Method isTalking;
		private static Method isWhispering;
		private static Method isPlayerDisconnected;
		private static Method isPlayerDisabled;
		private static Field states;
		private static java.lang.reflect.Constructor<?> playerState;
		private static Method updateVolumeList;
		private static Object hideIcons;
		private static Object showNametagIcons;
		private static Method configGet;
		private static Method isMuted;
		private static Method setMuted;
		private static Method setDisabled;
		private static Method canEnable;
		private static Object activationType;
		private static Object showHudIcons;
		private static Method configSet;
		private static Method configSave;
		private static Object volumeConfig;
		private static Method getVolume;
		private static Method setVolume;
		private static Method saveVolumes;
		private static Method getPlayerStates;
		private static Method stateUuid;
		private static Method stateName;

		private static void load() throws ReflectiveOperationException {
			if (getClient != null) {
				return;
			}
			Class<?> clientManager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
			Class<?> voicechatClient = Class.forName("de.maxhenkel.voicechat.voice.client.ClientVoicechat");
			Class<?> compatibility = Class.forName("de.maxhenkel.voicechat.intercompatibility.CommonCompatibilityManager");
			Object manager = compatibility.getMethod("getNetManager").invoke(compatibility.getField("INSTANCE").get(null));
			Object channel = manager.getClass().getField("secretChannel").get(manager);
			Method packetMethod = null;
			for (Method method : channel.getClass().getMethods()) {
				if (method.getName().equals("onClientPacket") && method.getParameterCount() == 2 && method.getParameterTypes()[0] == LocalPlayer.class) {
					packetMethod = method;
				}
			}
			if (packetMethod == null) {
				throw new NoSuchMethodException("onClientPacket");
			}
			Class<?> packet = Class.forName("de.maxhenkel.voicechat.net.SecretPacket");
			fromBytes = packet.getMethod("fromBytes", FriendlyByteBuf.class);
			secretPacket = packet;
			secretChannel = channel;
			netManager = manager;
			onClientPacket = packetMethod;
			isInitialized = Class.forName("de.maxhenkel.voicechat.voice.client.ClientVoicechatConnection").getMethod("isInitialized");
			getConnection = voicechatClient.getMethod("getConnection");
			getPlayerStateManager = clientManager.getMethod("getPlayerStateManager");
			isDisabled = Class.forName("de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager").getMethod("isDisabled");
			Class<?> stateManager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager");
			Class<?> talkCache = Class.forName("de.maxhenkel.voicechat.voice.client.TalkCache");
			getTalkCache = voicechatClient.getMethod("getTalkCache");
			isTalking = talkCache.getMethod("isTalking", UUID.class);
			isWhispering = talkCache.getMethod("isWhispering", UUID.class);
			isPlayerDisconnected = stateManager.getMethod("isPlayerDisconnected", UUID.class);
			isPlayerDisabled = stateManager.getMethod("isPlayerDisabled", UUID.class);
			states = stateManager.getDeclaredField("states");
			states.setAccessible(true);
			playerState = Class.forName("de.maxhenkel.voicechat.voice.common.PlayerState").getConstructor(UUID.class, String.class, boolean.class, boolean.class);
			updateVolumeList = Class.forName("de.maxhenkel.voicechat.gui.volume.AdjustVolumeList").getMethod("update");
			Object config = Class.forName("de.maxhenkel.voicechat.VoicechatClient").getField("CLIENT_CONFIG").get(null);
			if (config != null) {
				hideIcons = config.getClass().getField("hideIcons").get(config);
				showNametagIcons = config.getClass().getField("showNametagIcons").get(config);
				configGet = hideIcons.getClass().getMethod("get");
				activationType = config.getClass().getField("microphoneActivationType").get(config);
				showHudIcons = config.getClass().getField("showHudIcons").get(config);
				Class<?> entry = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry");
				configSet = entry.getMethod("set", Object.class);
				configSave = entry.getMethod("save");
			}
			isMuted = stateManager.getMethod("isMuted");
			setMuted = stateManager.getMethod("setMuted", boolean.class);
			setDisabled = stateManager.getMethod("setDisabled", boolean.class);
			canEnable = stateManager.getMethod("canEnable");
			Class<?> voicechatClientClass = Class.forName("de.maxhenkel.voicechat.VoicechatClient");
			volumeConfig = voicechatClientClass.getField("PLAYER_VOLUME_CONFIG").get(null);
			Class<?> volumeBase = Class.forName("de.maxhenkel.voicechat.config.VolumeConfigBase");
			getVolume = volumeBase.getMethod("getVolume", Object.class);
			setVolume = volumeBase.getMethod("setVolume", Object.class, double.class, String[].class);
			saveVolumes = volumeBase.getMethod("save");
			getPlayerStates = stateManager.getMethod("getPlayerStates", boolean.class);
			Class<?> stateClass = Class.forName("de.maxhenkel.voicechat.voice.common.PlayerState");
			stateUuid = stateClass.getMethod("getUuid");
			stateName = stateClass.getMethod("getName");
			setGroup = stateClass.getMethod("setGroup", UUID.class);
			updateJoinGroupList = Class.forName("de.maxhenkel.voicechat.gui.group.JoinGroupList").getMethod("update");
			updateGroupList = Class.forName("de.maxhenkel.voicechat.gui.group.GroupList").getMethod("update");
			getClient = clientManager.getMethod("getClient");
		}

		static Object playerState(UUID uuid, String name, boolean disabled, UUID group) throws ReflectiveOperationException {
			load();
			Object state = playerState.newInstance(uuid, name, disabled, false);
			setGroup.invoke(state, group);
			return state;
		}

		static void setStates(Map<UUID, Object> map) throws ReflectiveOperationException {
			load();
			states.set(getPlayerStateManager.invoke(null), map);
			updateVolumeList.invoke(null);
			updateJoinGroupList.invoke(null);
			updateGroupList.invoke(null);
		}

		static Identifier nameplateIcon(UUID player) throws ReflectiveOperationException {
			load();
			if (configGet != null && ((boolean) configGet.invoke(hideIcons) || !(boolean) configGet.invoke(showNametagIcons))) {
				return null;
			}
			return stateIcon(player);
		}

		static Identifier stateIcon(UUID player) throws ReflectiveOperationException {
			load();
			Object client = getClient.invoke(null);
			if (client == null || !isConnected(client)) {
				return null;
			}
			Object cache = getTalkCache.invoke(client);
			Object manager = getPlayerStateManager.invoke(null);
			if (VoiceAdmin.isAnnouncer(player) && (boolean) isTalking.invoke(cache, player)) {
				return VoiceAdmin.ANNOUNCE_ICON;
			}
			if ((boolean) isWhispering.invoke(cache, player)) {
				return WHISPER_SPEAKER_ICON;
			}
			if ((boolean) isTalking.invoke(cache, player)) {
				return SPEAKER_ICON;
			}
			if ((boolean) isPlayerDisconnected.invoke(manager, player)) {
				return DISCONNECT_ICON;
			}
			if ((boolean) isPlayerDisabled.invoke(manager, player)) {
				return SPEAKER_OFF_ICON;
			}
			return null;
		}

		static Object client() throws ReflectiveOperationException {
			load();
			return getClient.invoke(null);
		}

		static boolean isConnected(Object client) throws ReflectiveOperationException {
			load();
			Object connection = getConnection.invoke(client);
			return connection != null && (boolean) isInitialized.invoke(connection);
		}

		static boolean isDisabled() throws ReflectiveOperationException {
			load();
			return (boolean) isDisabled.invoke(getPlayerStateManager.invoke(null));
		}

		static boolean muted() throws ReflectiveOperationException {
			load();
			return (boolean) isMuted.invoke(getPlayerStateManager.invoke(null));
		}

		static boolean canMute() throws ReflectiveOperationException {
			load();
			return activationType != null && String.valueOf(configGet.invoke(activationType)).equals("VOICE");
		}

		static void setMuted(boolean muted) throws ReflectiveOperationException {
			load();
			setMuted.invoke(getPlayerStateManager.invoke(null), muted);
		}

		static boolean canEnable() throws ReflectiveOperationException {
			load();
			return (boolean) canEnable.invoke(getPlayerStateManager.invoke(null));
		}

		static void setDisabled(boolean disabled) throws ReflectiveOperationException {
			load();
			setDisabled.invoke(getPlayerStateManager.invoke(null), disabled);
		}

		static boolean hudIcons() throws ReflectiveOperationException {
			load();
			return showHudIcons == null || (boolean) configGet.invoke(showHudIcons);
		}

		static void setHudIcons(boolean shown) throws ReflectiveOperationException {
			load();
			if (showHudIcons != null) {
				configSave.invoke(configSet.invoke(showHudIcons, shown));
			}
		}

		static List<VolumePlayer> volumePlayers() throws ReflectiveOperationException {
			load();
			List<VolumePlayer> players = new ArrayList<>();
			for (Object state : (List<?>) getPlayerStates.invoke(getPlayerStateManager.invoke(null), false)) {
				players.add(new VolumePlayer((UUID) stateUuid.invoke(state), String.valueOf(stateName.invoke(state))));
			}
			return players;
		}

		static double volume(UUID player) throws ReflectiveOperationException {
			load();
			return volumeConfig == null ? 1.0 : (double) getVolume.invoke(volumeConfig, player);
		}

		static void setVolume(UUID player, double value, String comment) throws ReflectiveOperationException {
			load();
			if (volumeConfig != null) {
				setVolume.invoke(volumeConfig, player, value, new String[]{comment});
			}
		}

		static void saveVolumes() throws ReflectiveOperationException {
			load();
			if (volumeConfig != null) {
				saveVolumes.invoke(volumeConfig);
			}
		}

		// Groups by reflection
		private static Method getGroupManager;
		private static Method getGroups;
		private static Method getGroupID;
		private static Method groupId;
		private static Method groupName;
		private static Method groupPassword;
		private static Method groupHidden;
		private static Method groupType;
		private static Method stateGroup;
		private static Method stateHasGroup;
		private static List<Object> groupTypes;
		private static Object showGroupHud;

		private static void loadGroups() throws ReflectiveOperationException {
			load();
			if (getGroupManager != null) {
				return;
			}
			Class<?> clientManager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
			Class<?> stateManager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager");
			Class<?> group = Class.forName("de.maxhenkel.voicechat.voice.common.ClientGroup");
			Class<?> state = Class.forName("de.maxhenkel.voicechat.voice.common.PlayerState");
			Class<?> type = Class.forName("de.maxhenkel.voicechat.api.Group$Type");
			getGroups = Class.forName("de.maxhenkel.voicechat.voice.client.ClientGroupManager").getMethod("getGroups");
			getGroupID = stateManager.getMethod("getGroupID");
			groupId = group.getMethod("getId");
			groupName = group.getMethod("getName");
			groupPassword = group.getMethod("hasPassword");
			groupHidden = group.getMethod("isHidden");
			groupType = group.getMethod("getType");
			stateGroup = state.getMethod("getGroup");
			stateHasGroup = state.getMethod("hasGroup");
			groupTypes = List.of(type.getField("NORMAL").get(null), type.getField("OPEN").get(null), type.getField("ISOLATED").get(null));
			Object config = Class.forName("de.maxhenkel.voicechat.VoicechatClient").getField("CLIENT_CONFIG").get(null);
			showGroupHud = config == null ? null : config.getClass().getField("showGroupHud").get(config);
			getGroupManager = clientManager.getMethod("getGroupManager");
		}

		static List<VoiceGroup> groups() throws ReflectiveOperationException {
			loadGroups();
			Map<UUID, List<GroupMember>> members = members();
			List<VoiceGroup> groups = new ArrayList<>();
			for (Object group : (java.util.Collection<?>) getGroups.invoke(getGroupManager.invoke(null))) {
				if (!(boolean) groupHidden.invoke(group)) {
					groups.add(toGroup(group, members));
				}
			}
			return groups;
		}

		static VoiceGroup currentGroup() throws ReflectiveOperationException {
			loadGroups();
			UUID id = (UUID) getGroupID.invoke(getPlayerStateManager.invoke(null));
			if (id == null) {
				return null;
			}
			Object group = getGroupManager.getReturnType().getMethod("getGroup", UUID.class).invoke(getGroupManager.invoke(null), id);
			if (group == null) {
				return null;
			}
			Map<UUID, List<GroupMember>> members = members();
			LocalPlayer self = Minecraft.getInstance().player;
			if (self != null) {
				List<GroupMember> list = members.computeIfAbsent(id, key -> new ArrayList<>());
				if (list.stream().noneMatch(member -> member.id().equals(self.getUUID()))) {
					list.add(new GroupMember(self.getUUID(), self.getGameProfile().name(), selfIcon(self.getUUID())));
					list.sort(Comparator.comparing(member -> member.name().toLowerCase(Locale.ROOT)));
				}
			}
			return toGroup(group, members);
		}

		private static VoiceGroup toGroup(Object group, Map<UUID, List<GroupMember>> members) throws ReflectiveOperationException {
			UUID id = (UUID) groupId.invoke(group);
			int type = Math.max(0, groupTypes.indexOf(groupType.invoke(group)));
			return new VoiceGroup(id, String.valueOf(groupName.invoke(group)), (boolean) groupPassword.invoke(group), type, members.getOrDefault(id, List.of()));
		}

		private static Map<UUID, List<GroupMember>> members() throws ReflectiveOperationException {
			Map<UUID, List<GroupMember>> members = new HashMap<>();
			for (Object state : (List<?>) getPlayerStates.invoke(getPlayerStateManager.invoke(null), true)) {
				if ((boolean) stateHasGroup.invoke(state)) {
					UUID uuid = (UUID) stateUuid.invoke(state);
					members.computeIfAbsent((UUID) stateGroup.invoke(state), key -> new ArrayList<>())
							.add(new GroupMember(uuid, String.valueOf(stateName.invoke(state)), stateIcon(uuid)));
				}
			}
			for (List<GroupMember> list : members.values()) {
				list.sort(Comparator.comparing(member -> member.name().toLowerCase(Locale.ROOT)));
			}
			return members;
		}

		private static Identifier selfIcon(UUID self) throws ReflectiveOperationException {
			Object client = getClient.invoke(null);
			if (client == null || !isConnected(client)) {
				return DISCONNECT_ICON;
			}
			if (isDisabled()) {
				return SPEAKER_OFF_ICON;
			}
			Object cache = getTalkCache.invoke(client);
			if (VoiceAdmin.announcing() && (boolean) isTalking.invoke(cache, self)) {
				return VoiceAdmin.ANNOUNCE_ICON;
			}
			if ((boolean) isWhispering.invoke(cache, self)) {
				return WHISPER_SPEAKER_ICON;
			}
			return (boolean) isTalking.invoke(cache, self) ? SPEAKER_ICON : null;
		}

		static boolean groupHud() throws ReflectiveOperationException {
			loadGroups();
			return showGroupHud == null || (boolean) configGet.invoke(showGroupHud);
		}

		static void setGroupHud(boolean shown) throws ReflectiveOperationException {
			loadGroups();
			if (showGroupHud != null) {
				configSave.invoke(configSet.invoke(showGroupHud, shown));
			}
		}

		static void inject(LocalPlayer player, String channelName, String packetClass, FriendlyByteBuf buf) throws ReflectiveOperationException {
			load();
			Object channel = netManager.getClass().getField(channelName).get(netManager);
			Object packet = Class.forName(packetClass).getConstructor().newInstance();
			packet = packet.getClass().getMethod("fromBytes", FriendlyByteBuf.class).invoke(packet, buf);
			onClientPacket.invoke(channel, player, packet);
		}

		static void injectSecret(LocalPlayer player, FriendlyByteBuf buf) throws ReflectiveOperationException {
			load();
			Object packet = fromBytes.invoke(secretPacket.getConstructor().newInstance(), buf);
			onClientPacket.invoke(secretChannel, player, packet);
		}
	}
}
