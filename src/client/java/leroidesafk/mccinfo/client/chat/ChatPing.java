package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ChatPing {
	private static final String SEPARATOR = ": ";
	private static final int COLOR = 0xFF9A2E;
	private static final int TEXT_COLOR = 0xFFE751;
	private static final int BACKGROUND_ALPHA = 0x48;
	private static final int OUTLINE_ALPHA = 0xC0;
	private static final int PADDING = 1;
	private static final int EXTRA_BOTTOM = 1;
	private static final float SOUND_PITCH = 1.2F;
	private static final Set<Component> PINGED = Collections.newSetFromMap(new com.google.common.collect.MapMaker().weakKeys().makeMap());

	private ChatPing() {
	}

	private static String marker(String name) {
		return "@" + name;
	}

	public static Component apply(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (!ModConfig.get().chatPing || !Permissions.has(Permissions.Perm.UTILS) || client.player == null || message == null) {
			return message;
		}
		String name = client.player.getGameProfile().name();
		String plain = message.getString();
		int separator = plain.indexOf(SEPARATOR);
		if (name == null || name.isEmpty() || separator < 0) {
			return message;
		}
		List<Integer> mentions = mentions(plain, name, separator + SEPARATOR.length());
		if (mentions.isEmpty()) {
			return message;
		}
		// Mark each mention, add @ if missing
		Set<Integer> inserts = new HashSet<>();
		boolean[] marked = new boolean[plain.length()];
		for (int start : mentions) {
			boolean typed = start > 0 && plain.charAt(start - 1) == '@';
			if (!typed) {
				inserts.add(start);
			}
			for (int i = typed ? start - 1 : start; i < start + name.length(); i++) {
				marked[i] = true;
			}
		}
		// Rebuild the message with the marks
		String marker = marker(name);
		MutableComponent rebuilt = Component.empty();
		int[] offset = {0};
		message.visit((style, text) -> {
			Style markedStyle = style.withInsertion(marker).withColor(TEXT_COLOR);
			StringBuilder piece = new StringBuilder();
			Style pieceStyle = null;
			for (int k = 0; k < text.length(); k++) {
				int global = offset[0] + k;
				if (inserts.contains(global)) {
					flush(rebuilt, piece, pieceStyle);
					rebuilt.append(Component.literal("@").withStyle(markedStyle));
					pieceStyle = null;
				}
				Style charStyle = marked[global] ? markedStyle : style;
				if (charStyle != pieceStyle) {
					flush(rebuilt, piece, pieceStyle);
					pieceStyle = charStyle;
				}
				piece.append(text.charAt(k));
			}
			flush(rebuilt, piece, pieceStyle);
			offset[0] += text.length();
			return Optional.empty();
		}, Style.EMPTY);
		PINGED.add(rebuilt);
		// Sound and window flash
		if (ModConfig.get().pingSound) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, SOUND_PITCH));
		}
		if (ModConfig.get().pingFlash && !client.getWindow().isFocused()) {
			GLFW.glfwRequestWindowAttention(client.getWindow().handle());
		}
		return rebuilt;
	}

	private static void flush(MutableComponent target, StringBuilder piece, Style style) {
		if (!piece.isEmpty()) {
			target.append(Component.literal(piece.toString()).withStyle(style));
			piece.setLength(0);
		}
	}

	private static List<Integer> mentions(String text, String name, int from) {
		List<Integer> found = new ArrayList<>();
		String lower = text.toLowerCase(Locale.ROOT);
		String target = name.toLowerCase(Locale.ROOT);
		int index = lower.indexOf(target, from);
		while (index >= 0) {
			int end = index + target.length();
			if ((index == 0 || !nameChar(text.charAt(index - 1))) && (end >= text.length() || !nameChar(text.charAt(end)))) {
				found.add(index);
			}
			index = lower.indexOf(target, end);
		}
		return found;
	}

	private static boolean nameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	public static void highlight(ChatComponent.ChatGraphicsAccess graphics, GuiMessage.Line line, int textTop, float opacity) {
		Minecraft client = Minecraft.getInstance();
		if (!PINGED.contains(line.parent().content()) || client.player == null) {
			return;
		}
		String marker = marker(client.player.getGameProfile().name());
		Font font = client.font;
		// Marked parts of the line
		List<int[]> spans = new ArrayList<>();
		float[] x = {0.0F};
		int[] open = {-1};
		line.content().accept((index, style, codepoint) -> {
			boolean inside = marker.equals(style.getInsertion());
			int left = Math.round(x[0]);
			if (inside && open[0] < 0) {
				open[0] = left;
			} else if (!inside && open[0] >= 0) {
				spans.add(new int[] {open[0], left});
				open[0] = -1;
			}
			x[0] += font.width(FormattedCharSequence.codepoint(codepoint, style));
			return true;
		});
		if (open[0] >= 0) {
			spans.add(new int[] {open[0], Math.round(x[0])});
		}
		if (spans.isEmpty()) {
			return;
		}
		// Highlight boxes
		double spacing = client.options.chatLineSpacing().get();
		int entryHeight = (int) (9 * (spacing + 1.0));
		int entryBottom = textTop + (int) Math.round(8.0 * (spacing + 1.0) - 4.0 * spacing) + EXTRA_BOTTOM;
		int entryTop = entryBottom - entryHeight - EXTRA_BOTTOM;
		float alpha = Mth.clamp(opacity, 0.0F, 1.0F);
		int background = Math.round(BACKGROUND_ALPHA * alpha) << 24 | COLOR;
		int outline = Math.round(OUTLINE_ALPHA * alpha) << 24 | COLOR;
		for (int[] span : spans) {
			int left = span[0] - PADDING - 1;
			int right = span[1] + PADDING;
			graphics.fill(left + 1, entryTop + 1, right - 1, entryBottom - 1, background);
			graphics.fill(left, entryTop, right, entryTop + 1, outline);
			graphics.fill(left, entryBottom - 1, right, entryBottom, outline);
			graphics.fill(left, entryTop + 1, left + 1, entryBottom - 1, outline);
			graphics.fill(right - 1, entryTop + 1, right, entryBottom - 1, outline);
		}
	}
}
