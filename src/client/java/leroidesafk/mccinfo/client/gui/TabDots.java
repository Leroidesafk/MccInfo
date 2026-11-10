package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Optional;

// Dots of the MCCI tab list, hidden when the setting is on
public final class TabDots {
	public static final String MARKER = "mccinfo:hidden_tab_dots";
	private static final FontDescription HUD_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcc", "hud"));

	private TabDots() {
	}

	// Dots in the hud font marked to be skipped
	public static Component hide(Component name) {
		if (name == null || !ModConfig.get().hideTabDots) {
			return name;
		}
		boolean[] found = {false};
		MutableComponent result = Component.empty();
		name.visit((style, text) -> {
			boolean dots = !text.isEmpty() && HUD_FONT.equals(style.getFont()) && text.chars().allMatch(c -> c == '.');
			found[0] |= dots;
			result.append(Component.literal(text).setStyle(dots ? style.withInsertion(MARKER) : style));
			return Optional.empty();
		}, Style.EMPTY);
		return found[0] ? result : name;
	}

	public static boolean hidden(Style style) {
		return MARKER.equals(style.getInsertion());
	}
}
