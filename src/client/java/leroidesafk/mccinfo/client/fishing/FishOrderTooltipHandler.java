package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.gui.EventsScreen;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

public final class FishOrderTooltipHandler {
	private static final String TARGET_ITEM_NAME = "Event Fish Order";

	private static final Style ICON_STYLE = Style.EMPTY
			.withColor(TextColor.fromRgb(0xFFFFFF))
			.withFont(new FontDescription.Resource(FishIconRegistry.ICON_FONT))
			.withItalic(false);

	private FishOrderTooltipHandler() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!EventsScreen.summerHearts() || !stack.getHoverName().getString().equals(TARGET_ITEM_NAME)) {
				return;
			}

			for (int i = 0; i < lines.size(); i++) {
				Component line = lines.get(i);
				MutableComponent rebuilt = rebuildLine(line);
				if (rebuilt != null) {
					lines.set(i, rebuilt);
				}
			}
		});
	}

	private static MutableComponent rebuildLine(Component line) {
		// Styled text runs
		List<String> texts = new ArrayList<>();
		List<Style> styles = new ArrayList<>();

		line.visit((style, string) -> {
			if (!string.isEmpty()) {
				texts.add(string);
				styles.add(style);
			}
			return Optional.empty();
		}, Style.EMPTY);

		int[] runStarts = new int[texts.size()];
		StringBuilder fullText = new StringBuilder();
		for (int i = 0; i < texts.size(); i++) {
			runStarts[i] = fullText.length();
			fullText.append(texts.get(i));
		}
		String text = fullText.toString();

		Matcher matcher = FishIconRegistry.matcher(text);

		MutableComponent rebuilt = Component.empty();
		int last = 0;
		boolean changed = false;

		// Heart before each known fish
		while (matcher.find()) {
			FishIconRegistry.IconColor icon = FishIconRegistry.iconOf(matcher.group(1));
			if (icon == null) {
				continue;
			}

			appendStyledRange(rebuilt, texts, styles, runStarts, last, matcher.start());

			rebuilt.append(Component.literal(icon.glyph).withStyle(ICON_STYLE));
			rebuilt.append(" ");

			Style nameStyle = styleAt(styles, runStarts, matcher.start());
			rebuilt.append(Component.literal(matcher.group()).withStyle(nameStyle));

			last = matcher.end();
			changed = true;
		}

		if (!changed) {
			return null;
		}

		// Rest of the line
		appendStyledRange(rebuilt, texts, styles, runStarts, last, text.length());
		return rebuilt;
	}

	private static void appendStyledRange(MutableComponent rebuilt, List<String> texts, List<Style> styles, int[] runStarts, int from, int to) {
		for (int i = 0; i < texts.size(); i++) {
			int runStart = runStarts[i];
			int runEnd = runStart + texts.get(i).length();
			int segStart = Math.max(from, runStart);
			int segEnd = Math.min(to, runEnd);
			if (segStart < segEnd) {
				String sub = texts.get(i).substring(segStart - runStart, segEnd - runStart);
				rebuilt.append(Component.literal(sub).withStyle(styles.get(i)));
			}
		}
	}

	private static Style styleAt(List<Style> styles, int[] runStarts, int index) {
		for (int i = styles.size() - 1; i >= 0; i--) {
			if (runStarts[i] <= index) {
				return styles.get(i);
			}
		}
		return Style.EMPTY;
	}
}
