package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.MovableOverlay;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ModChatOverlay {
	// MccInfo box look
	private static final int TITLE_COLOR = 0x7FFFC31E;
	private static final OverlayStyle.Icon TITLE_ICON = new OverlayStyle.Icon(
			Identifier.fromNamespaceAndPath("mcc", "textures/island_items/infinibag/material/style_shard_amber.png"), 16, 8);
	private static final int DEFAULT_X = 4;
	private static final int DEFAULT_Y = 130;
	private static final List<Component> PREVIEW_LINES = List.of(Component.literal("Player: Hello!"), Component.literal("Player: Nice catch"));

	// Mod-only box look
	private static final int STAFF_TITLE_COLOR = 0x7FBB1EFF;
	private static final OverlayStyle.Icon STAFF_TITLE_ICON = new OverlayStyle.Icon(
			Identifier.fromNamespaceAndPath("mcc", "textures/island_items/infinibag/material/style_shard_amethyst.png"), 16, 8);

	// Box with its own config fields
	static final MessageBox BOX = new MessageBox("MCCINFO MESSAGES", TITLE_COLOR, TITLE_ICON, PREVIEW_LINES, DEFAULT_X, DEFAULT_Y) {
		@Override
		public boolean enabled() {
			return ModChat.boxEnabled();
		}

		// Mod Messages when only the Mod chat is shown
		@Override
		protected String title() {
			return ModChat.boxStaffOnly() ? "MOD MESSAGES" : super.title();
		}

		@Override
		protected int titleColor() {
			return ModChat.boxStaffOnly() ? STAFF_TITLE_COLOR : super.titleColor();
		}

		@Override
		protected OverlayStyle.Icon icon() {
			return ModChat.boxStaffOnly() ? STAFF_TITLE_ICON : super.icon();
		}

		@Override
		public int storedX() {
			return ModConfig.get().modChatBoxX;
		}

		@Override
		public int storedY() {
			return ModConfig.get().modChatBoxY;
		}

		@Override
		public int storedWidth() {
			return ModConfig.get().modChatBoxWidth;
		}

		@Override
		public int storedHeight() {
			return ModConfig.get().modChatBoxHeight;
		}

		@Override
		public int storedTextPixels() {
			return ModConfig.get().modChatBoxTextPixels;
		}

		@Override
		public void storePosition(int x, int y) {
			ModConfig.get().modChatBoxX = x;
			ModConfig.get().modChatBoxY = y;
		}

		@Override
		public void storeSize(int width, int height) {
			ModConfig.get().modChatBoxWidth = width;
			ModConfig.get().modChatBoxHeight = height;
		}

		@Override
		public void storeTextPixels(int pixels) {
			ModConfig.get().modChatBoxTextPixels = pixels;
		}

		@Override
		public boolean hidden() {
			return ModConfig.get().modChatBoxHidden;
		}

		@Override
		public void setHidden(boolean hidden) {
			ModConfig.get().modChatBoxHidden = hidden;
		}
	};

	public static final MovableOverlay MOVABLE = BOX;

	private ModChatOverlay() {
	}

	public static void register() {
		OverlayStyle.registerHud(Mccinfo.id("mod_chat_messages"), BOX::extractHud);
	}

	static void add(Component line) {
		BOX.add(line);
	}

	static void remove(Component line) {
		BOX.remove(line);
	}

	public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		return BOX.mouseScrolled(mouseX, mouseY, scrollY);
	}
}
