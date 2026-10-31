package leroidesafk.mccinfo.client.widget;

import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.SpotSync;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Locale;
import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class RockPaperScissors {
	public enum Choice {
		ROCK("rock"), PAPER("paper"), SCISSORS("scissors");

		final String id;
		final Identifier texture;

		Choice(String id) {
			this.id = id;
			this.texture = Mccinfo.id("textures/gui/utils/" + id + ".png");
		}

		static Choice byId(String id) {
			for (Choice choice : values()) {
				if (choice.id.equals(id)) {
					return choice;
				}
			}
			return null;
		}
	}

	static final Identifier ICON = Choice.SCISSORS.texture;

	enum Phase {
		CHOOSING,
		WAITING,
		RESULT,
		ENDED
	}

	enum Outcome {
		WIN, LOSE, DRAW, CANCEL;

		static Outcome byId(String id) {
			return switch (id == null ? "" : id) {
				case "win" -> WIN;
				case "lose" -> LOSE;
				case "draw" -> DRAW;
				default -> CANCEL;
			};
		}
	}

	static final long CHOOSE_MILLIS = 10_000L;
	static final long END_MILLIS = 5_000L;
	private static final long INVITE_TOAST_MILLIS = 8_000L;
	private static final long TOAST_MILLIS = 5_000L;

	static final class Match {
		final String id;
		final String opponent;
		final int bet;
		int round;
		int myWins;
		int theirWins;
		Phase phase = Phase.CHOOSING;
		long phaseStart = Util.getMillis();
		Choice mine;
		Choice theirs;
		Outcome outcome;
		String endReason;

		private Match(String id, String opponent, int bet) {
			this.id = id;
			this.opponent = opponent;
			this.bet = bet;
		}

		long remainingMillis() {
			return Math.max(0L, CHOOSE_MILLIS - (Util.getMillis() - this.phaseStart));
		}

		private void startRound(int round) {
			this.round = round;
			this.phase = Phase.CHOOSING;
			this.phaseStart = Util.getMillis();
			this.mine = null;
			this.theirs = null;
			this.outcome = null;
		}

		private void end(Outcome outcome, String reason) {
			this.phase = Phase.ENDED;
			this.phaseStart = Util.getMillis();
			this.outcome = outcome;
			this.endReason = reason;
		}
	}

	// Current and last match
	private static Match match;
	private static boolean openRequested;
	private static String lastOpponent = "?";
	private static String lastMatchId;

	private RockPaperScissors() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(RockPaperScissors::tick);
		// Invite answer commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(literal("mccrps")
					.then(literal("accept").then(argument("id", StringArgumentType.word())
							.executes(context -> answer(StringArgumentType.getString(context, "id"), true))))
					.then(literal("refuse").then(argument("id", StringArgumentType.word())
							.executes(context -> answer(StringArgumentType.getString(context, "id"), false)))));
		});
	}

	static Match current() {
		return match;
	}

	static boolean playing() {
		return match != null && match.phase != Phase.ENDED;
	}

	private static int answer(String id, boolean accept) {
		if (!SpotSync.sendRpsAnswer(id, accept)) {
			chat(Component.translatable("message.mccinfo.mccinfo.offline").withStyle(ChatFormatting.RED));
			return 0;
		}
		return Command.SINGLE_SUCCESS;
	}

	static void choose(Choice choice) {
		if (match == null || match.phase != Phase.CHOOSING || match.remainingMillis() <= 0) {
			return;
		}
		match.mine = choice;
		match.phase = Phase.WAITING;
		match.phaseStart = Util.getMillis();
		SpotSync.sendRpsChoice(match.id, match.round, choice.id);
	}

	public static void disconnected() {
		if (match == null) {
			return;
		}
		match = null;
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() instanceof RpsScreen) {
			client.gui.setScreen(null);
		}
	}

	static void forfeit() {
		if (match == null) {
			return;
		}
		if (match.phase != Phase.ENDED) {
			SpotSync.sendRpsForfeit(match.id);
		}
		match = null;
	}

	private static void tick(Minecraft client) {
		if (match == null) {
			openRequested = false;
			return;
		}
		// Open the game screen
		if (openRequested) {
			openRequested = false;
			if (!(client.gui.screen() instanceof RpsScreen)) {
				client.gui.setScreen(new RpsScreen());
			}
		}
		// Close after the end
		if (match.phase == Phase.ENDED && Util.getMillis() - match.phaseStart >= END_MILLIS) {
			match = null;
			if (client.gui.screen() instanceof RpsScreen) {
				client.gui.setScreen(null);
			}
		}
	}

	// Relay messages
	public static void receive(String type, JsonObject message) {
		switch (type) {
			case "rps_invite" -> {
				String id = string(message, "id");
				JsonObject from = message.has("from") && message.get("from").isJsonObject() ? message.getAsJsonObject("from") : new JsonObject();
				String name = SpotSync.cleanName(string(from, "name"));
				if (id != null && id.matches("[0-9a-f]{1,32}") && name != null) {
					showInvite(name, integer(message, "bet"), "/mccrps accept " + id, "/mccrps refuse " + id);
				}
			}
			case "rps_sent" -> {
				JsonObject to = message.has("to") && message.get("to").isJsonObject() ? message.getAsJsonObject("to") : new JsonObject();
				chat(Component.translatable("message.mccinfo.rps.sent", name(string(to, "name")), points(integer(message, "bet"))).withStyle(ChatFormatting.GRAY));
			}
			case "rps_refused" -> showRefused(name(string(message, "name")));
			case "rps_expired" -> chat(Component.translatable("message.mccinfo.rps.expired", name(string(message, "name"))).withStyle(ChatFormatting.GRAY));
			case "rps_error" -> chat(Component.literal(String.valueOf(string(message, "message"))).withStyle(ChatFormatting.RED));
			case "rps_round" -> {
				String id = string(message, "match");
				if (id == null) {
					return;
				}
				if (match == null || !match.id.equals(id)) {
					JsonObject opponent = message.has("opponent") && message.get("opponent").isJsonObject() ? message.getAsJsonObject("opponent") : new JsonObject();
					match = new Match(id, name(string(opponent, "name")), integer(message, "bet"));
					lastOpponent = match.opponent;
					lastMatchId = id;
				}
				readScore(message);
				match.startRound(integer(message, "round"));
				openScreen();
			}
			case "rps_result" -> {
				if (match == null || !match.id.equals(string(message, "match"))) {
					return;
				}
				match.mine = Choice.byId(string(message, "you"));
				match.theirs = Choice.byId(string(message, "them"));
				match.outcome = Outcome.byId(string(message, "outcome"));
				readScore(message);
				match.phase = Phase.RESULT;
				match.phaseStart = Util.getMillis();
			}
			case "rps_end" -> {
				Outcome outcome = Outcome.byId(string(message, "outcome"));
				String reason = string(message, "reason");
				int bet = integer(message, "bet");
				// Only the last match
				if (!String.valueOf(lastMatchId).equals(string(message, "match"))) {
					return;
				}
				chat(endMessage(outcome, reason, lastOpponent, bet));
				if (match != null && match.id.equals(string(message, "match"))) {
					match.end(outcome, reason);
				}
			}
			default -> {
			}
		}
	}

	private static void readScore(JsonObject message) {
		if (message.has("score") && message.get("score").isJsonArray() && message.getAsJsonArray("score").size() == 2) {
			match.myWins = message.getAsJsonArray("score").get(0).getAsInt();
			match.theirWins = message.getAsJsonArray("score").get(1).getAsInt();
		}
	}

	static Component endMessage(Outcome outcome, String reason, String opponent, int bet) {
		String key = switch (outcome) {
			case WIN -> "forfeit".equals(reason) ? "message.mccinfo.rps.win_forfeit" : "timeout".equals(reason)
					? "message.mccinfo.rps.win_timeout" : "message.mccinfo.rps.win";
			case LOSE -> "forfeit".equals(reason) ? "message.mccinfo.rps.lose_forfeit" : "timeout".equals(reason)
					? "message.mccinfo.rps.lose_timeout" : "message.mccinfo.rps.lose";
			default -> "message.mccinfo.rps.cancel";
		};
		return prefix().append(Component.translatable(key, opponent, points(bet))
				.withStyle(outcome == Outcome.WIN ? ChatFormatting.GREEN : outcome == Outcome.LOSE ? ChatFormatting.RED : ChatFormatting.GRAY));
	}

	private static void openScreen() {
		openRequested = true;
	}

	private static void showInvite(String from, int bet, String acceptCommand, String refuseCommand) {
		toast(Component.literal(from).withStyle(ChatFormatting.YELLOW),
				Component.translatable("message.mccinfo.rps.invite_toast", points(bet)).withStyle(ChatFormatting.GRAY), INVITE_TOAST_MILLIS);
		// Chat with buttons
		MutableComponent accept = Component.translatable("message.mccinfo.rps.accept").withStyle(style -> style.withColor(ChatFormatting.GREEN)
				.withBold(true).withClickEvent(new ClickEvent.RunCommand(acceptCommand))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.mccinfo.rps.accept_hover", from, points(bet)))));
		MutableComponent refuse = Component.translatable("message.mccinfo.rps.refuse").withStyle(style -> style.withColor(ChatFormatting.RED)
				.withBold(true).withClickEvent(new ClickEvent.RunCommand(refuseCommand)));
		chat(prefix().append(Component.translatable("message.mccinfo.rps.invite", Component.literal(from).withStyle(ChatFormatting.YELLOW), points(bet))
				.withStyle(ChatFormatting.WHITE)).append(" ").append(accept).append(" ").append(refuse));
	}

	private static void showRefused(String name) {
		toast(Component.literal(name).withStyle(ChatFormatting.YELLOW),
				Component.translatable("message.mccinfo.rps.refused").withStyle(ChatFormatting.RED), TOAST_MILLIS);
	}

	private static MutableComponent points(int bet) {
		return Component.translatable("message.mccinfo.rps.points", bet).withStyle(ChatFormatting.GOLD);
	}

	private static MutableComponent prefix() {
		return Component.literal("[RPS] ").withStyle(ChatFormatting.GOLD);
	}

	private static void chat(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(message);
		}
	}

	private static void toast(Component title, Component subtitle, long millis) {
		Minecraft.getInstance().gui.toastManager().addToast(new RpsToast(title, subtitle, millis));
	}

	private static String name(String name) {
		String clean = SpotSync.cleanName(name);
		return clean == null ? "?" : clean;
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
	}

	private static int integer(JsonObject object, String key) {
		try {
			return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static String hex(UUID uuid) {
		return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	// Invite and refusal toast
	private static final class RpsToast implements Toast {
		private static final int WIDTH = 180;
		private static final int HEIGHT = 26;
		private static final int ICON_SIZE = 16;
		private static final int BACKGROUND_COLOR = 0xC0111111;

		private final Component title;
		private final Component subtitle;
		private final long millis;
		private Visibility wantedVisibility = Visibility.HIDE;

		private RpsToast(Component title, Component subtitle, long millis) {
			this.title = title;
			this.subtitle = subtitle;
			this.millis = millis;
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
		public Visibility getWantedVisibility() {
			return this.wantedVisibility;
		}

		@Override
		public void update(ToastManager manager, long visibleMillis) {
			this.wantedVisibility = visibleMillis > this.millis * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleMillis) {
			OverlayStyle.fillRounded(graphics, 1, 2, WIDTH - 3, HEIGHT - 2, BACKGROUND_COLOR);
			graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, 5, 6, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
			graphics.text(font, this.title, 26, 6, 0xFFFFFFFF);
			graphics.text(font, this.subtitle, 26, 15, 0xFFFFFFFF);
		}
	}
}
