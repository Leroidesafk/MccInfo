package leroidesafk.mccinfo.client.relay;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.chat.Emojis;
import leroidesafk.mccinfo.client.chat.ModChat;
import leroidesafk.mccinfo.client.events.CarnivalTracker;
import leroidesafk.mccinfo.client.fishing.FishCollection;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.items.NeuNpcs;
import leroidesafk.mccinfo.client.widget.RockPaperScissors;
import leroidesafk.mccinfo.client.patchnotes.PatchNotes;
import leroidesafk.mccinfo.client.update.ModUninstaller;
import leroidesafk.mccinfo.client.update.ModUpdater;
import leroidesafk.mccinfo.client.voice.VoiceChat;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// Connection to the relay: perms, chat, voice and every other message
public final class SpotSync {
	private static final int RECONNECT_DELAY_TICKS = 100;
	private static final int SEND_INTERVAL_TICKS = 20;
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final int MAX_MESSAGE_CHARS = 1 << 20;

	private static final Gson GSON = new Gson();

	// Relay address and TLS pin, read from the jar
	private static final String URL_RESOURCE = "/assets/" + Mccinfo.MOD_ID + "/spot_sync.json";
	private static final RelayConfig RELAY = loadRelay();
	private static final String RELAY_URL = RELAY.url();

	private record RelayConfig(String url, String pin) {
	}

	// Messages from the relay
	private record PermsMessage(boolean whitelisted, boolean admin, Boolean moderator, Boolean authenticated, Boolean banned, List<String> perms, Boolean outdated,
			String requiredVersion, Boolean autoUpdate, ModUpdater.UpdateInfo update, String downloadUrl, String downloadLink) {
	}

	private static final java.util.regex.Pattern PLAYER_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,16}");

	public static String cleanName(String name) {
		return name != null && PLAYER_NAME.matcher(name).matches() ? name : null;
	}

	public static String ownName() {
		return cleanName(Minecraft.getInstance().getUser().getName());
	}

	private enum State {
		OFF, NO_URL, CONNECTING, CONNECTED, FAILED
	}

	private static HttpClient http;
	private static WebSocket socket;
	private static State state = State.OFF;
	private static boolean authFailureLogged;
	private static String url;
	private static int generation;
	private static boolean connecting;
	private static int reconnectCooldown;
	private static int sendCooldown;
	private static CompletableFuture<WebSocket> lastSend = CompletableFuture.completedFuture(null);
	private static final List<Runnable> RESEND = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static boolean authenticated;

	private SpotSync() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpotSync::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> disconnect());
	}

	// Data that optional modules send again on each new connection
	public static void onResend(Runnable resend) {
		RESEND.add(resend);
	}

	// Sending, for optional modules
	public static boolean connected() {
		return socket != null;
	}

	public static boolean sendRaw(String text) {
		if (socket == null) {
			return false;
		}
		sendText(Minecraft.getInstance(), text);
		return true;
	}

	public static boolean sendMessage(com.google.gson.JsonObject message) {
		return sendIfConnected(message);
	}

	private static void tick(Minecraft client) {
		String wanted = RELAY_URL;
		if (wanted.isEmpty()) {
			disconnect();
			state = State.NO_URL;
			return;
		}
		// Off MCC Island: disconnect and reset everything
		if (!MccIsland.isConnected()) {
			if (state != State.OFF) {
				disconnect();
				state = State.OFF;
				Permissions.update(false, false, List.of(), false, null);
				Permissions.setModerator(false);
				ModUpdater.offer(false, null, null, true, null);
				NeuItems.clear();
				RockPaperScissors.disconnected();
			}
			return;
		}
		if (state == State.OFF) {
			state = State.CONNECTING;
		}
		if (!wanted.equals(url)) {
			disconnect();
		}
		if (socket == null) {
			if (!connecting && --reconnectCooldown <= 0) {
				connect(client, wanted);
			}
			return;
		}
	}

	private static RelayConfig loadRelay() {
		try (InputStream in = SpotSync.class.getResourceAsStream(URL_RESOURCE)) {
			if (in != null) {
				RelayConfig loaded = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), RelayConfig.class);
				if (loaded != null && loaded.url() != null) {
					return new RelayConfig(loaded.url().trim(), loaded.pin() == null ? "" : loaded.pin().trim());
				}
			}
		} catch (IOException | JsonParseException e) {
			Mccinfo.LOGGER.warn("Unreadable spot relay address ({})", URL_RESOURCE, e);
		}
		return new RelayConfig("", "");
	}

	public static HttpClient.Builder relayHttpClient() {
		HttpClient.Builder builder = HttpClient.newBuilder();
		if (!RELAY.pin().isEmpty()) {
			builder.sslContext(PinnedTls.context(RELAY.pin()));
		}
		return builder;
	}

	private static void connect(Minecraft client, String target) {
		int attempt = ++generation;
		url = target;
		connecting = true;
		reconnectCooldown = RECONNECT_DELAY_TICKS;
		if (state != State.FAILED) {
			state = State.CONNECTING;
		}
		try {
			if (http == null) {
				http = relayHttpClient().build();
			}
			http.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT).buildAsync(URI.create(target), new Listener(client, attempt))
					.whenComplete((opened, error) -> client.execute(() -> onConnected(attempt, opened, error)));
		} catch (IllegalArgumentException e) {
			onConnected(attempt, null, e);
		}
	}

	private static void onConnected(int attempt, WebSocket opened, Throwable error) {
		if (attempt != generation) {
			if (opened != null) {
				opened.abort();
			}
			return;
		}
		connecting = false;
		if (error != null) {
			fail(attempt, error);
			return;
		}
		socket = opened;
		state = State.CONNECTED;
		com.google.gson.JsonObject hello = new com.google.gson.JsonObject();
		hello.addProperty("type", "hello");
		hello.addProperty("version", Permissions.MOD_VERSION);
		sendText(Minecraft.getInstance(), hello.toString());
		authenticated = false;
		RESEND.forEach(Runnable::run);
	}

	private static void fail(int attempt, Throwable error) {
		if (attempt != generation) {
			return;
		}
		if (state != State.FAILED) {
			Mccinfo.LOGGER.warn("Spot sync: could not connect to {} or connection lost ({})", url, String.valueOf(error));
		}
		if (socket != null) {
			socket.abort();
			socket = null;
		}
		connecting = false;
		state = State.FAILED;
		reconnectCooldown = RECONNECT_DELAY_TICKS;
		RockPaperScissors.disconnected();
	}

	public static void reconnect() {
		disconnect();
	}

	private static void disconnect() {
		generation++;
		if (socket != null) {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "");
			socket = null;
		}
		url = null;
		connecting = false;
		reconnectCooldown = 0;
	}

	private static void sendText(Minecraft client, String text) {
		int attempt = generation;
		WebSocket target = socket;
		lastSend = lastSend.handle((previous, error) -> null).thenCompose(ignored -> target.sendText(text, true));
		lastSend.whenComplete((sent, error) -> {
			if (error != null) {
				client.execute(() -> fail(attempt, error));
			}
		});
	}

	public static URI httpUri(String path) {
		try {
			URI relay = URI.create(RELAY_URL);
			String scheme = "wss".equalsIgnoreCase(relay.getScheme()) ? "https" : "http";
			return new URI(scheme, null, relay.getHost(), relay.getPort(), path, relay.getQuery(), null);
		} catch (IllegalArgumentException | java.net.URISyntaxException e) {
			return null;
		}
	}

	// Admin actions
	public static boolean sendAdmin(String action, String name, String perm, String version) {
		return sendAdmin(action, name, perm, version, false);
	}

	public static boolean sendQuietAdmin(String action, String name, String perm, String version) {
		return sendAdmin(action, name, perm, version, true);
	}

	private static boolean sendAdmin(String action, String name, String perm, String version, boolean quiet) {
		if (socket == null) {
			return false;
		}
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "admin");
		message.addProperty("action", action);
		if (quiet) {
			message.addProperty("quiet", true);
		}
		if (name != null) {
			message.addProperty("name", name);
		}
		if (perm != null) {
			message.addProperty("perm", perm);
		}
		if (version != null) {
			message.addProperty("version", version);
		}
		sendText(Minecraft.getInstance(), message.toString());
		return true;
	}

	public static boolean sendPlayerAdmin(String action, String uuid, String name, String perm) {
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "admin");
		message.addProperty("action", action);
		message.addProperty("uuid", uuid);
		message.addProperty("name", name);
		message.addProperty("quiet", true);
		if (perm != null) {
			message.addProperty("perm", perm);
		}
		return sendIfConnected(message);
	}

	public static boolean sendRole(String uuid, String name, String role) {
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "admin");
		message.addProperty("action", "role_set");
		message.addProperty("quiet", true);
		message.addProperty("uuid", uuid);
		message.addProperty("name", name);
		message.addProperty("role", role);
		return sendIfConnected(message);
	}

	public static boolean sendUnban(String uuid, String name) {
		return sendPlayerAdmin("unban", uuid, name, null);
	}

	// Rock paper scissors
	public static void sendRpsInvite(String target, int bet) {
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "rps_invite");
		message.addProperty("target", target);
		message.addProperty("bet", bet);
		sendIfConnected(message);
	}

	public static boolean sendRpsAnswer(String id, boolean accept) {
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "rps_answer");
		message.addProperty("id", id);
		message.addProperty("accept", accept);
		return sendIfConnected(message);
	}

	public static void sendRpsChoice(String match, int round, String choice) {
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "rps_choice");
		message.addProperty("match", match);
		message.addProperty("round", round);
		message.addProperty("choice", choice);
		sendIfConnected(message);
	}

	public static void sendRpsForfeit(String match) {
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "rps_forfeit");
		message.addProperty("match", match);
		sendIfConnected(message);
	}

	public static boolean sendCarnivalGame(long slot, String game) {
		com.google.gson.JsonObject message = new com.google.gson.JsonObject();
		message.addProperty("type", "carnival_game");
		message.addProperty("slot", slot);
		message.addProperty("game", game);
		return sendIfConnected(message);
	}

	// Messages handled by optional modules
	private static final java.util.Map<String, java.util.function.Consumer<com.google.gson.JsonObject>> HANDLERS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.List<Runnable> ADMIN_RESULT_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static void addHandler(String type, java.util.function.Consumer<com.google.gson.JsonObject> handler) {
		HANDLERS.put(type, handler);
	}

	public static void onAdminResult(Runnable listener) {
		ADMIN_RESULT_LISTENERS.add(listener);
	}

	public static boolean sendVoice(com.google.gson.JsonObject message) {
		return sendIfConnected(message);
	}

	public static boolean isAuthenticated() {
		return socket != null && authenticated;
	}

	private static boolean sendIfConnected(com.google.gson.JsonObject message) {
		if (socket == null) {
			return false;
		}
		sendText(Minecraft.getInstance(), message.toString());
		return true;
	}

	// Proves the account to the relay through Mojang, the token is never sent
	private static void authenticate(Minecraft client, String serverId) {
		int attempt = generation;
		var user = client.getUser();
		CompletableFuture.runAsync(() -> {
			try {
				client.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
			} catch (com.mojang.authlib.exceptions.AuthenticationException e) {
				throw new java.util.concurrent.CompletionException(e);
			}
		}, net.minecraft.util.Util.nonCriticalIoPool()).whenComplete((ignored, error) -> client.execute(() -> {
			if (attempt != generation || socket == null) {
				return;
			}
			if (error != null) {
				if (!authFailureLogged) {
					authFailureLogged = true;
					Mccinfo.LOGGER.warn("Sync: identity not verified by Mojang, no permissions ({})", String.valueOf(error));
				}
				return;
			}
			com.google.gson.JsonObject auth = new com.google.gson.JsonObject();
			auth.addProperty("type", "auth");
			auth.addProperty("name", user.getName());
			sendText(client, auth.toString());
		}));
	}

	private static void receive(Minecraft client, String text) {
		String type;
		com.google.gson.JsonObject object;
		try {
			object = GSON.fromJson(text, com.google.gson.JsonObject.class);
			if (object == null) {
				return;
			}
			// No type means spots
			type = object.has("type") && object.get("type").isJsonPrimitive() ? object.get("type").getAsString() : "spots";
		} catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
			return;
		}
		try {
			switch (type) {
				case "challenge" -> {
					if (object.has("serverId")) {
						authenticate(client, object.get("serverId").getAsString());
					}
				}
				case "perms" -> {
					PermsMessage perms = GSON.fromJson(object, PermsMessage.class);
					Permissions.update(perms.whitelisted(), perms.admin(), perms.perms(), Boolean.TRUE.equals(perms.outdated()), perms.requiredVersion());
					Permissions.setModerator(Boolean.TRUE.equals(perms.moderator()));
					Permissions.setDownloadLink(perms.downloadLink());
					if (Boolean.TRUE.equals(perms.authenticated()) && !authenticated) {
						authenticated = true;
						RESEND.forEach(Runnable::run);
					}
					ModUpdater.offer(Boolean.TRUE.equals(perms.outdated()), perms.requiredVersion(), perms.update(), !Boolean.FALSE.equals(perms.autoUpdate()), perms.downloadUrl());
					if (Boolean.TRUE.equals(perms.authenticated()) && perms.banned() != null) {
						ModUninstaller.banned(perms.banned());
					}
				}
				case "admin_result" -> {
					if (client.player != null && object.has("message") && !(object.has("quiet") && object.get("quiet").getAsBoolean())) {
						boolean ok = object.has("ok") && object.get("ok").getAsBoolean();
						client.player.sendSystemMessage(Component.literal("[MccInfo] ").withStyle(net.minecraft.ChatFormatting.GOLD)
								.append(Component.literal(object.get("message").getAsString())
										.withStyle(ok ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED)));
					}
					ADMIN_RESULT_LISTENERS.forEach(Runnable::run);
				}
				case "rps_invite", "rps_sent", "rps_refused", "rps_expired", "rps_error", "rps_round", "rps_result", "rps_end" ->
						RockPaperScissors.receive(type, object);
				case "carnival_game" -> CarnivalTracker.receive(object);
				case "neu_items" -> NeuItems.receive(object);
				case "neu_npcs" -> NeuNpcs.receive(object);
				case "patchnotes" -> PatchNotes.receive(object);
				case "regions" -> Regions.receive(object);
				case "fish_list" -> FishCollection.receive(object);
				case "emojis" -> Emojis.receive(object);
				case "patchnote_preview" -> PatchNotes.preview(object);
				case "voice_secret" -> VoiceChat.receiveSecret(object);
				case "voice_players" -> VoiceChat.receivePlayers(object);
				case "voice_group_add" -> VoiceChat.receiveGroupAdd(object);
				case "voice_group_remove" -> VoiceChat.receiveGroupRemove(object);
				case "voice_group_joined" -> VoiceChat.receiveGroupJoined(object);
				case "voice_status" -> leroidesafk.mccinfo.client.voice.VoiceAdmin.receiveStatus(object);
				case "voice_announce" -> leroidesafk.mccinfo.client.voice.VoiceAdmin.receiveAnnounce(object);
				case "mod_chat" -> ModChat.receive(object);
				case "mod_chat_error" -> ModChat.receiveError(object);
				case "mod_chat_delete" -> ModChat.receiveDelete(object);
				case "mod_sanctions" -> ModChat.receiveSanctions(object);
				default -> {
					java.util.function.Consumer<com.google.gson.JsonObject> handler = HANDLERS.get(type);
					if (handler != null) {
						handler.accept(object);
					}
				}
			}
		} catch (JsonParseException | IllegalStateException | UnsupportedOperationException | ClassCastException e) {
		}
	}

	// Joins split messages and drops the too long ones
	private static final class Listener implements WebSocket.Listener {
		private final Minecraft client;
		private final int attempt;
		private final StringBuilder buffer = new StringBuilder();
		private boolean tooLong;

		private Listener(Minecraft client, int attempt) {
			this.client = client;
			this.attempt = attempt;
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			if (this.buffer.length() + data.length() > MAX_MESSAGE_CHARS) {
				this.tooLong = true;
				this.buffer.setLength(0);
			} else if (!this.tooLong) {
				this.buffer.append(data);
			}
			if (last) {
				if (!this.tooLong) {
					String text = this.buffer.toString();
					this.client.execute(() -> {
						// Ignore messages from an old connection
						if (this.attempt == generation) {
							receive(this.client, text);
						}
					});
				}
				this.tooLong = false;
				this.buffer.setLength(0);
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			this.client.execute(() -> fail(this.attempt, new IllegalStateException("closed by the server (" + statusCode + ")")));
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			this.client.execute(() -> fail(this.attempt, error));
		}
	}
}
