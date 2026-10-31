package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FishIconRegistry {
	private FishIconRegistry() {
	}

	public static final Identifier ICON_FONT = Mccinfo.id("icons");

	// Heart colors
	public enum IconColor {
		WHITE,
		RED,
		ORANGE,
		YELLOW,
		GREEN,
		BLUE,
		PURPLE,
		PINK;

		private static final char BASE_CODEPOINT = '';

		public final String glyph = String.valueOf((char) (BASE_CODEPOINT + ordinal()));
	}

	// [Fish Name] in text
	private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[([^\\[\\]]+)]");

	private static final Map<String, IconColor> FISH_ICONS = new LinkedHashMap<>();

	// Fish by heart color
	static {
		register("Fern Flounder", IconColor.BLUE);
		register("Coral Cod", IconColor.BLUE);
		register("Glass Pike", IconColor.BLUE);
		register("Mosaic Guppy", IconColor.BLUE);
		register("Queenfish", IconColor.BLUE);
		register("Mirrored Mahi", IconColor.BLUE);

		register("Bluegill", IconColor.WHITE);
		register("Midnight Gourami", IconColor.WHITE);
		register("Painted Discus", IconColor.WHITE);
		register("Pearlescent Betta", IconColor.WHITE);
		register("Disco Discus", IconColor.WHITE);
		register("Wreckfish", IconColor.WHITE);

		register("Reef Anchovy", IconColor.PURPLE);
		register("Crystalline Cod", IconColor.PURPLE);
		register("Shardine", IconColor.PURPLE);
		register("Ocean Moonfish", IconColor.PURPLE);
		register("Nightmare Marlin", IconColor.PURPLE);
		register("Floodfish", IconColor.PURPLE);

		register("Silver Snook", IconColor.PINK);
		register("Ancient Snapper", IconColor.PINK);
		register("Sunken Koi", IconColor.PINK);
		register("Blossom Betta", IconColor.PINK);
		register("Sapphire Salmon", IconColor.PINK);
		register("Cosmic Cod", IconColor.PINK);

		register("Neon Tetra", IconColor.GREEN);
		register("Sinharaja Salmon", IconColor.GREEN);
		register("Daintree Guppy", IconColor.GREEN);
		register("Viney Perch", IconColor.GREEN);
		register("Green Terror Cichlid", IconColor.GREEN);
		register("Torch Tarpon", IconColor.GREEN);

		register("Coal Cod", IconColor.RED);
		register("Emberpike", IconColor.RED);
		register("Volcanic Surgeonfish", IconColor.RED);
		register("Molten Goldfish", IconColor.RED);
		register("Ashen Tilapia", IconColor.RED);
		register("Blazing Betta", IconColor.RED);

		register("Fungal Salmon", IconColor.ORANGE);
		register("Frilled Mackerel", IconColor.ORANGE);
		register("Ancient Fangtooth", IconColor.ORANGE);
		register("Swampy Betta", IconColor.ORANGE);
		register("Glowberry Guppy", IconColor.ORANGE);
		register("Troutarium", IconColor.ORANGE);

		register("Coral Angelfish", IconColor.YELLOW);
		register("Butterfly Fish", IconColor.YELLOW);
		register("Surgeonfish", IconColor.YELLOW);
		register("Braincoral Betta", IconColor.YELLOW);
		register("Parrotfish", IconColor.YELLOW);
		register("Diamond Oarfish", IconColor.YELLOW);
	}

	public static void register(String fishName, IconColor color) {
		FISH_ICONS.put(fishName, color);
	}

	public static IconColor iconOf(String fishName) {
		return FISH_ICONS.get(fishName);
	}

	public static Matcher matcher(String text) {
		return BRACKET_PATTERN.matcher(text);
	}
}
