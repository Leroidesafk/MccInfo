package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ChatCopy {
	private static final int HOVER_COLOR = 0x28FFFFFF;
	private static final int DELETED_COLOR = 0x60D83A3A;
	private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final String SEPARATOR = ": ";

	private static GuiMessage hovered;
	private static GuiMessage nextHovered;
	private static final Vector2f MOUSE = new Vector2f(Float.NaN, Float.NaN);

	private ChatCopy() {
	}

	static boolean enabled() {
		return ModConfig.get().chatCopy && Permissions.has(Permissions.Perm.UTILS);
	}

	public static void beginFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		hovered = nextHovered;
		nextHovered = null;
		Minecraft client = Minecraft.getInstance();
		if (!enabled()) {
			hovered = null;
		}
		if (!(client.gui.screen() instanceof ChatScreen)) {
			hovered = null;
			MOUSE.set(Float.NaN, Float.NaN);
			return;
		}
		// Mouse in chat coordinates
		float scale = client.options.chatScale().get().floatValue();
		Matrix3x2f pose = new Matrix3x2f(graphics.pose()).scale(scale, scale).translate(4.0F, 0.0F);
		pose.invert().transformPosition(mouseX, mouseY, MOUSE);
	}

	public static void line(ChatComponent.ChatGraphicsAccess graphics, GuiMessage.Line line, int textTop) {
		Minecraft client = Minecraft.getInstance();
		// Line bounds
		double spacing = client.options.chatLineSpacing().get();
		int entryHeight = (int) (9 * (spacing + 1.0));
		int entryBottom = textTop + (int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing);
		int entryTop = entryBottom - entryHeight;
		float scale = client.options.chatScale().get().floatValue();
		int right = Mth.ceil(ChatComponent.getWidth(client.options.chatWidth().get()) / scale) + 8;
		// Deleted message, seen by staff
		if (ModChat.deletedForStaff(line.parent())) {
			graphics.fill(-4, entryTop, right, entryBottom, DELETED_COLOR);
		}
		if (Float.isNaN(MOUSE.x) || !enabled()) {
			return;
		}
		// Hover highlight
		if (MOUSE.x >= -4 && MOUSE.x < right && MOUSE.y >= entryTop && MOUSE.y < entryBottom) {
			nextHovered = line.parent();
		}
		if (line.parent() == hovered) {
			graphics.fill(-4, entryTop, right, entryBottom, HOVER_COLOR);
		}
	}

	static boolean mouseOver(float left, float top, float right, float bottom) {
		return !Float.isNaN(MOUSE.x) && MOUSE.x >= left && MOUSE.x < right && MOUSE.y >= top && MOUSE.y < bottom;
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !client.hasControlDown() || !enabled() || hovered == null) {
			return false;
		}
		String text = text(hovered);
		if (text.isEmpty()) {
			return true;
		}
		client.keyboardHandler.setClipboard(text);
		NeuItems.clickSound();
		return true;
	}

	static String text(GuiMessage message) {
		String mod = ModChat.copyText(message);
		if (mod != null) {
			return mod;
		}
		String plain = clean(textOnly(message.content()));
		// Drop the player name
		int separator = plain.indexOf(SEPARATOR);
		if (separator > 0) {
			String before = plain.substring(0, separator).strip();
			int space = before.lastIndexOf(' ');
			String name = before.substring(space + 1);
			String rest = space < 0 ? "" : before.substring(0, space);
			if (PLAYER_NAME.matcher(name).matches() && (rest.codePoints().noneMatch(Character::isLetterOrDigit) || knownPlayer(name))) {
				return plain.substring(separator + SEPARATOR.length()).strip();
			}
		}
		return plain;
	}

	private static String textOnly(Component component) {
		StringBuilder builder = new StringBuilder();
		// Icons become spaces
		component.visit((style, text) -> {
			if (style.getFont().equals(FontDescription.DEFAULT)) {
				builder.append(text);
			} else {
				builder.append(' ');
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return builder.toString();
	}

	private static boolean knownPlayer(String name) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return false;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			if (info.getProfile().name().toLowerCase(Locale.ROOT).equals(lower)) {
				return true;
			}
		}
		return false;
	}

	static String clean(String text) {
		StringBuilder builder = new StringBuilder(text.length());
		text.codePoints().filter(codepoint -> Character.getType(codepoint) != Character.PRIVATE_USE).forEach(builder::appendCodePoint);
		return builder.toString().replaceAll("\\s+", " ").strip();
	}
}
