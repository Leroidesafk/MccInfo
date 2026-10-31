package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.chat.MessageBox;
import leroidesafk.mccinfo.client.gui.MovableOverlay;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.MccServer;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

public final class FishingMessagesOverlay {
	// Messages moved out of the chat into the box
	private static final List<String> PULLED_PHRASES = List.of(
			"all pots are fully repaired!",
			"an error occurred whilst catching a fish.",
			"changed crab pot climate!",
			"crab pot claimed! contents sent to your infinibag.",
			"fishing spot stock replenished!",
			"fishing stock has replenished!",
			" has run out of uses",
			"repaired crab pots!",
			"research meter ready to claim! check your a.n.g.l.r. panel.",
			"special:",
			"that's not a fishing spot! locate one and cast there.",
			"this spot is depleted, so you can no longer fish here.",
			"triggered:",
			"while active, all islands in this climate receive:",
			"you caught:",
			"you earned:",
			"you moved too far away from your currently cast spot, and your cast was canceled",
			"you receive:",
			"you've discovered a ",
			"you've reached fishing level",
			"you've run out of your equipped",
			"your grotto has become unstable, teleporting you back to safety...");

	private static final int TITLE_COLOR = 0x7F1E90FF;
	private static final Identifier TITLE_ICON = Identifier.fromNamespaceAndPath("mcc", "textures/island_interface/fishing/perk_icon/speedy_rod.png");
	private static final int DEFAULT_X = 4;
	private static final int DEFAULT_Y = 30;
	private static final List<Component> PREVIEW_LINES = List.of(Component.literal("You caught: [Plain Goby]"), Component.literal("You earned: 12 Fishing XP"));

	private static final MessageBox BOX = new MessageBox("FISHING MESSAGES", TITLE_COLOR, OverlayStyle.Icon.of(TITLE_ICON), PREVIEW_LINES, DEFAULT_X, DEFAULT_Y) {
		// On in the settings and allowed by the utils permission
		@Override
		public boolean enabled() {
			return ModConfig.get().fishingMessages && Permissions.has(Permissions.Perm.UTILS);
		}

		@Override
		public boolean allowed() {
			return inAllowedInstance();
		}

		@Override
		public int storedX() {
			return ModConfig.get().messagesX;
		}

		@Override
		public int storedY() {
			return ModConfig.get().messagesY;
		}

		@Override
		public int storedWidth() {
			return ModConfig.get().messagesWidth;
		}

		@Override
		public int storedHeight() {
			return ModConfig.get().messagesHeight;
		}

		@Override
		public int storedTextPixels() {
			return ModConfig.get().messagesTextPixels;
		}

		@Override
		public void storePosition(int x, int y) {
			ModConfig.get().messagesX = x;
			ModConfig.get().messagesY = y;
		}

		@Override
		public void storeSize(int width, int height) {
			ModConfig.get().messagesWidth = width;
			ModConfig.get().messagesHeight = height;
		}

		@Override
		public void storeTextPixels(int pixels) {
			ModConfig.get().messagesTextPixels = pixels;
		}

		@Override
		public boolean hidden() {
			return ModConfig.get().messagesHidden;
		}

		@Override
		public void setHidden(boolean hidden) {
			ModConfig.get().messagesHidden = hidden;
		}
	};

	public static final MovableOverlay MOVABLE = BOX;

	private FishingMessagesOverlay() {
	}

	public static void register() {
		OverlayStyle.registerHud(Mccinfo.id("fishing_messages"), BOX::extractHud);
		// Runs on every server message before it reaches the chat
		ClientReceiveMessageEvents.ALLOW_GAME.register(FishingMessagesOverlay::allowGameMessage);
	}

	private static boolean allowGameMessage(Component message, boolean overlay) {
		// Action bar text, not a chat message
		if (overlay) {
			return true;
		}
		Component shown = message;
		// Compact mode: merge the catch messages into one line
		if (CompactFishingMessages.active()) {
			// Already merged into the catch line: drop it
			if (CompactFishingMessages.absorb(message)) {
				return false;
			}
			shown = CompactFishingMessages.compact(message);
		}
		// Box off or not a fishing message: leave it in the chat
		if (!MOVABLE.enabled() || MOVABLE.hidden() || !OverlayStyle.onMccIsland() || !inAllowedInstance() || !isFishingMessage(message)) {
			if (shown == message) {
				CompactFishingMessages.shown(message, false);
				return true;
			}
			// Compacted: show our version in the chat instead
			Minecraft.getInstance().gui.hud.getChat().addServerSystemMessage(shown);
			CompactFishingMessages.shown(shown, false);
			return false;
		}
		// Fishing message: goes in the box, not in the chat
		add(shown);
		CompactFishingMessages.shown(shown, true);
		return false;
	}

	static boolean replace(Component old, Component updated) {
		return BOX.replace(old, updated);
	}

	private static void add(Component message) {
		BOX.add(message);
	}

	// "Only in Fishing Instances" option
	private static boolean inAllowedInstance() {
		return !ModConfig.get().fishingMessagesFishingOnly || MccServer.onFishing();
	}

	// True if the message contains one of the phrases above
	private static boolean isFishingMessage(Component message) {
		String text = message.getString().toLowerCase(Locale.ROOT);
		for (String phrase : PULLED_PHRASES) {
			if (text.contains(phrase)) {
				return true;
			}
		}
		return false;
	}

	public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		return BOX.mouseScrolled(mouseX, mouseY, scrollY);
	}
}
