package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.relay.Permissions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Emojis {
	private static final FontDescription ICON_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcc", "icon"));
	private static final int CHAT_MAX_LENGTH = 256;
	private static final long PLUS_CHECK_MILLIS = 1000L;
	private static final int ICON_COLOR = 0xFFFFFF;

	public record Emoji(String glyph, FontDescription font, String copy, List<String> lines) {
		Component icon() {
			return Component.literal(this.glyph).withStyle(Style.EMPTY.withFont(this.font).withColor(ICON_COLOR));
		}
	}

	private record Alias(String text, Emoji emoji, boolean named) {
	}

	private static List<Emoji> emojis = List.of();
	private static Map<Integer, Emoji> byGlyph = Map.of();
	private static List<Alias> aliases = List.of();
	private static boolean plus;
	private static long plusCheckedAt;
	private static boolean editing;

	private Emojis() {
	}

	public static void receive(JsonObject object) {
		if (!object.has("emojis") || !object.get("emojis").isJsonArray()) {
			return;
		}
		List<Emoji> received = new ArrayList<>();
		Map<Integer, Emoji> glyphs = new HashMap<>();
		List<Alias> names = new ArrayList<>();
		for (JsonElement element : object.getAsJsonArray("emojis")) {
			try {
				JsonObject entry = element.getAsJsonObject();
				String glyph = entry.get("glyph").getAsString();
				Identifier font = Identifier.tryParse(entry.get("font").getAsString());
				if (glyph.codePointCount(0, glyph.length()) != 1 || font == null || glyphs.containsKey(glyph.codePointAt(0))) {
					continue;
				}
				List<String> lines = new ArrayList<>();
				for (JsonElement line : entry.getAsJsonArray("lines")) {
					lines.add(line.getAsString());
				}
				Emoji emoji = new Emoji(glyph, new FontDescription.Resource(font), entry.get("copy").getAsString(), List.copyOf(lines));
				received.add(emoji);
				glyphs.put(glyph.codePointAt(0), emoji);
				// Each word of the lines is an alias
				for (String line : lines) {
					for (String alias : line.split(" ")) {
						if (!alias.isEmpty()) {
							names.add(new Alias(alias, emoji, alias.length() > 2 && alias.startsWith(":") && alias.endsWith(":")));
						}
					}
				}
			} catch (RuntimeException e) {
				Mccinfo.LOGGER.warn("Unreadable emoji in the relay list: {}", element);
			}
		}
		// Longest aliases first
		names.sort(Comparator.comparingInt((Alias alias) -> alias.text().length()).reversed());
		emojis = List.copyOf(received);
		byGlyph = Map.copyOf(glyphs);
		aliases = List.copyOf(names);
	}

	static List<Emoji> all() {
		return emojis;
	}

	static boolean active() {
		return usable() && ModConfig.get().emojis;
	}

	// MCC+ on MCC Island with utils, box or not
	private static boolean usable() {
		return MccIsland.isConnected() && Permissions.has(Permissions.Perm.UTILS) && !emojis.isEmpty() && hasPlus();
	}

	private static boolean hasPlus() {
		long now = System.currentTimeMillis();
		if (now - plusCheckedAt >= PLUS_CHECK_MILLIS) {
			plusCheckedAt = now;
			plus = computePlus();
		}
		return plus;
	}

	private static boolean computePlus() {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection == null || client.player == null) {
			return false;
		}
		String name = client.player.getGameProfile().name();
		return hasPlus(ModChat.tabLine(connection, name), name);
	}

	static boolean hasPlus(Component line, String name) {
		if (line == null || name == null) {
			return false;
		}
		// MCC+ icon after the name
		boolean[] afterName = {false};
		return line.visit((style, text) -> {
			if (afterName[0] && !text.isEmpty() && ICON_FONT.equals(style.getFont())) {
				return Optional.of(Boolean.TRUE);
			}
			if (text.equals(name)) {
				afterName[0] = true;
			}
			return Optional.empty();
		}, Style.EMPTY).isPresent();
	}

	static EditBox chatInput() {
		return Minecraft.getInstance().gui.screen() instanceof ChatScreen screen ? ((ChatScreenAccessor) screen).mccinfo$getInput() : null;
	}

	public static FormattedCharSequence decorate(EditBox box, FormattedCharSequence text) {
		if (byGlyph.isEmpty() || box != chatInput()) {
			return text;
		}
		return sink -> text.accept((index, style, codePoint) -> {
			Emoji emoji = byGlyph.get(codePoint);
			return sink.accept(index, emoji == null ? style : style.withFont(emoji.font()).withColor(ICON_COLOR), codePoint);
		});
	}

	public static String expand(String text) {
		if (byGlyph.isEmpty() || text == null) {
			return text;
		}
		StringBuilder result = null;
		for (int index = 0; index < text.length(); ) {
			int codePoint = text.codePointAt(index);
			Emoji emoji = byGlyph.get(codePoint);
			if (emoji != null && result == null) {
				result = new StringBuilder(text.substring(0, index));
			}
			if (result != null) {
				if (emoji != null) {
					result.append(emoji.copy());
				} else {
					result.appendCodePoint(codePoint);
				}
			}
			index += Character.charCount(codePoint);
		}
		return result == null ? text : result.toString();
	}

	public static void onChatEdited(EditBox box) {
		if (editing || box == null) {
			return;
		}
		// Auto Emojis
		if (usable() && ModConfig.get().autoEmojis) {
			collapse(box);
		}
		// Room for the expanded emojis
		int extra = expand(box.getValue()).length() - box.getValue().length();
		box.setMaxLength(Math.max(box.getValue().length(), CHAT_MAX_LENGTH - extra));
	}

	private static void collapse(EditBox box) {
		String value = box.getValue();
		int cursor = box.getCursorPosition();
		StringBuilder result = new StringBuilder(value.length());
		int newCursor = cursor;
		boolean changed = false;
		int index = 0;
		while (index < value.length()) {
			Alias match = aliasAt(value, index);
			if (match == null) {
				result.append(value.charAt(index));
				index++;
				continue;
			}
			// Keep the cursor in place
			int end = index + match.text().length();
			if (cursor >= end) {
				newCursor -= match.text().length() - match.emoji().glyph().length();
			} else if (cursor > index) {
				newCursor = result.length() + match.emoji().glyph().length();
			}
			result.append(match.emoji().glyph());
			index = end;
			changed = true;
		}
		if (!changed) {
			return;
		}
		editing = true;
		try {
			box.setMaxLength(CHAT_MAX_LENGTH);
			box.setValue(result.toString());
			box.setCursorPosition(Math.clamp(newCursor, 0, result.length()));
			box.setHighlightPos(box.getCursorPosition());
		} finally {
			editing = false;
		}
	}

	private static Alias aliasAt(String value, int index) {
		for (Alias alias : aliases) {
			if (!value.startsWith(alias.text(), index)) {
				continue;
			}
			// :name: anywhere, others as whole words
			if (alias.named()) {
				return alias;
			}
			int end = index + alias.text().length();
			boolean startOk = index == 0 || Character.isWhitespace(value.charAt(index - 1));
			boolean endOk = end == value.length() || Character.isWhitespace(value.charAt(end));
			if (startOk && endOk) {
				return alias;
			}
		}
		return null;
	}

	static void appendText(MutableComponent result, String text, boolean emojis) {
		if (!emojis || aliases.isEmpty()) {
			result.append(text);
			return;
		}
		StringBuilder plain = new StringBuilder();
		int index = 0;
		while (index < text.length()) {
			Alias match = aliasAt(text, index);
			if (match == null) {
				plain.append(text.charAt(index));
				index++;
				continue;
			}
			if (!plain.isEmpty()) {
				result.append(plain.toString());
				plain.setLength(0);
			}
			result.append(match.emoji().icon());
			index += match.text().length();
		}
		if (!plain.isEmpty()) {
			result.append(plain.toString());
		}
	}

	static void insert(Emoji emoji) {
		EditBox box = chatInput();
		if (box == null) {
			return;
		}
		box.insertText(emoji.glyph());
	}
}
