package leroidesafk.mccinfo.client.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Real item amounts on container slots
public final class BetterItemAmounts {
	private static final int MENU_SLOT = 8;
	private static final String AMOUNT_KEY = "mcc:amount";
	private static final Pattern REMAINING_PATTERN = Pattern.compile("^Bait Remaining:\\s*(\\d[\\d,]*)$");
	private static final String[] UNITS = {"k", "M", "B", "T"};
	private static final long PLAIN_LIMIT = 1_000L;
	// Colors of the amount, the max and the level 0
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int BOTTOM_COLOR = 0xFFB7F3FF;
	private static final int SHADOW_COLOR = 0xFF3F4982;
	private static final int MAX_TEXT_COLOR = 0xFFBAC1C1;
	private static final int MAX_BOTTOM_COLOR = 0xFF7480AF;
	private static final int MAX_SHADOW_COLOR = 0xFF333170;
	private static final int ZERO_TEXT_COLOR = 0xFFFFD69E;
	private static final int ZERO_BOTTOM_COLOR = 0xFFFF6B79;
	private static final int ZERO_SHADOW_COLOR = 0xFF722754;
	private static final int ZERO_MAX_TEXT_COLOR = 0xFFBF8170;
	private static final int ZERO_MAX_BOTTOM_COLOR = 0xFFA04E63;
	private static final int ZERO_MAX_SHADOW_COLOR = 0xFF33112B;
	private static final int FRAME_COLOR = 0x8C000000;
	// Text position in the slot
	private static final int TEXT_TOP = 8;
	private static final int RIGHT_EDGE = 17;
	private static final int GLYPH_TOP = 2;
	private static final int GLYPH_HEIGHT = 6;
	private static final int BOTTOM_ROWS_TOP = 5;
	private static final int BOTTOM_ROWS_END = 7;
	private static final float LEVEL_TEXEL_PIXELS = 2.0F;
	private static final int LEVEL_SPLIT_TOP = 14;

	// Menus from the relay
	private static volatile List<Menu> menus = List.of();
	private static Screen titleScreen;
	private static String cleanTitle = "";

	private BetterItemAmounts() {
	}

	public static boolean enabled() {
		return ModConfig.get().betterItemAmounts && Permissions.has(Permissions.Perm.UTILS) && OverlayStyle.onMccIsland();
	}

	// Amount to show, -1 keeps the vanilla count
	public static long amountFor(Slot slot, ItemStack stack, String countText) {
		if (countText != null || slot == null || stack.isEmpty() || !enabled() || slot.container instanceof Inventory || !isAmountMenu(slot.container)) {
			return -1L;
		}
		return amount(stack);
	}

	private static boolean isAmountMenu(Container container) {
		if (container.getContainerSize() > MENU_SLOT && NeuItems.isListedContainer(container.getItem(MENU_SLOT))) {
			return true;
		}
		for (Menu menu : menus) {
			if (menu.matches(container)) {
				return true;
			}
		}
		return false;
	}

	// Menus without the NEU list
	static void setMenus(JsonArray array) {
		List<Menu> read = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			String title = text(object, "title");
			int size = number(object, "size");
			int slot = number(object, "slot");
			String name = text(object, "name");
			if (title != null || slot >= 0 && name != null) {
				read.add(new Menu(title, size, name == null ? -1 : slot, name));
			}
		}
		menus = List.copyOf(read);
	}

	static void clearMenus() {
		menus = List.of();
	}

	private static String text(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			return null;
		}
		String text = NeuItems.cleanName(object.get(key).getAsString());
		return text.isEmpty() ? null : text;
	}

	private static int number(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) {
			return -1;
		}
		return object.get(key).getAsInt();
	}

	private static String screenTitle() {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen != titleScreen) {
			titleScreen = screen;
			cleanTitle = screen == null ? "" : NeuItems.cleanName(screen.getTitle().getString());
		}
		return cleanTitle;
	}

	// Menu found by its title, size or slot name
	private record Menu(String title, int size, int slot, String name) {
		boolean matches(Container container) {
			if (this.size >= 0 && container.getContainerSize() != this.size) {
				return false;
			}
			if (this.title != null && !screenTitle().contains(this.title)) {
				return false;
			}
			if (this.name == null) {
				return true;
			}
			if (this.slot >= container.getContainerSize()) {
				return false;
			}
			ItemStack stack = container.getItem(this.slot);
			return !stack.isEmpty() && NeuItems.cleanName(stack.getHoverName().getString()).contains(this.name);
		}
	}

	// Bait left, else mcc:amount, else the stack count
	static long amount(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				Matcher matcher = REMAINING_PATTERN.matcher(line.getString().strip());
				if (matcher.matches()) {
					try {
						return Long.parseLong(matcher.group(1).replace(",", ""));
					} catch (NumberFormatException ignored) {
						break;
					}
				}
			}
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			Optional<Long> amount = data.copyTag().getCompound("PublicBukkitValues").flatMap(values -> values.getLong(AMOUNT_KEY));
			if (amount.isPresent()) {
				return amount.get();
			}
		}
		return stack.getCount();
	}

	// Shortened to 3 characters
	public static String format(long amount) {
		if (amount < PLAIN_LIMIT) {
			return Long.toString(amount);
		}
		long unit = 1000L;
		int index = 0;
		while (index < UNITS.length - 1 && amount >= unit * 1000L) {
			unit *= 1000L;
			index++;
		}
		long whole = amount / unit;
		if (whole >= 100L && index < UNITS.length - 1) {
			return "0." + amount / (unit * 100L) + UNITS[index + 1];
		}
		if (whole >= 10L) {
			return whole + UNITS[index];
		}
		long tenth = amount / (unit / 10L) % 10L;
		return whole + (tenth == 0L ? "" : "." + tenth) + UNITS[index];
	}

	public static void extract(GuiGraphicsExtractor graphics, Font font, long amount, int x, int y) {
		extractParts(graphics, font, x, y, 1.0F, TEXT_TOP + BOTTOM_ROWS_TOP, new Part(format(amount), TEXT_COLOR, BOTTOM_COLOR, SHADOW_COLOR));
	}

	// Perk levels
	public enum PerkLevelStyle {
		LEVEL("screen.mccinfo.settings.perk_level_style.level"),
		FRACTION("screen.mccinfo.settings.perk_level_style.fraction");

		final String translationKey;

		PerkLevelStyle(String translationKey) {
			this.translationKey = translationKey;
		}
	}

	public static PerkLevelStyle perkLevelStyle() {
		PerkLevelStyle style = ModConfig.get().perkLevelStyle;
		return style == null ? PerkLevelStyle.LEVEL : style;
	}

	public static Component perkLevelStyleName(PerkLevelStyle style) {
		return Component.translatable(style.translationKey);
	}

	public static boolean perkLevelsShown() {
		return ModConfig.get().perkLevels && Permissions.has(Permissions.Perm.UTILS) && OverlayStyle.onMccIsland();
	}

	// Level alone or small level/max
	public static void extractLevel(GuiGraphicsExtractor graphics, Font font, int level, int max, int x, int y) {
		if (perkLevelStyle() == PerkLevelStyle.LEVEL) {
			extractParts(graphics, font, x, y, 1.0F, TEXT_TOP + BOTTOM_ROWS_TOP, level == 0
					? new Part("0", ZERO_TEXT_COLOR, ZERO_BOTTOM_COLOR, ZERO_SHADOW_COLOR)
					: new Part(Integer.toString(level), TEXT_COLOR, BOTTOM_COLOR, SHADOW_COLOR));
			return;
		}
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		float scale = SlotIndicators.texelPixels(LEVEL_TEXEL_PIXELS, guiScale) / guiScale;
		if (level == 0) {
			extractParts(graphics, font, x, y, scale, LEVEL_SPLIT_TOP, new Part("0", ZERO_TEXT_COLOR, ZERO_BOTTOM_COLOR, ZERO_SHADOW_COLOR),
					new Part("/" + max, ZERO_MAX_TEXT_COLOR, ZERO_MAX_BOTTOM_COLOR, ZERO_MAX_SHADOW_COLOR));
			return;
		}
		extractParts(graphics, font, x, y, scale, LEVEL_SPLIT_TOP, new Part(Integer.toString(level), TEXT_COLOR, BOTTOM_COLOR, SHADOW_COLOR),
				new Part("/" + max, MAX_TEXT_COLOR, MAX_BOTTOM_COLOR, MAX_SHADOW_COLOR));
	}

	// Text, shadow and two colored bottom rows
	private static void extractParts(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale, int splitTop, Part... parts) {
		Component[] texts = new Component[parts.length];
		int width = 0;
		for (int i = 0; i < parts.length; i++) {
			texts[i] = OverlayStyle.hudText(parts[i].text());
			width += font.width(texts[i]);
		}
		int textX = -width;
		int textY = -BOTTOM_ROWS_TOP;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + RIGHT_EDGE, y + splitTop);
		graphics.pose().scale(scale, scale);
		extractFrame(graphics, textX - 1, textY + GLYPH_TOP - 1, textX + width, textY + GLYPH_TOP + GLYPH_HEIGHT + 1);
		extractTexts(graphics, font, texts, parts, textX, textY, false);
		graphics.pose().popMatrix();
		graphics.enableScissor(x + RIGHT_EDGE - Mth.ceil((width + 1) * scale), y + splitTop, x + RIGHT_EDGE + 1,
				y + splitTop + Mth.ceil((BOTTOM_ROWS_END - BOTTOM_ROWS_TOP) * scale));
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + RIGHT_EDGE, y + splitTop);
		graphics.pose().scale(scale, scale);
		extractTexts(graphics, font, texts, parts, textX, textY, true);
		graphics.pose().popMatrix();
		graphics.disableScissor();
	}

	private static void extractTexts(GuiGraphicsExtractor graphics, Font font, Component[] texts, Part[] parts, int textX, int textY, boolean bottomRows) {
		int partX = textX;
		for (int i = 0; i < parts.length; i++) {
			if (bottomRows) {
				graphics.text(font, texts[i], partX, textY, parts[i].bottom(), false);
			} else {
				graphics.text(font, texts[i], partX, textY + 1, parts[i].shadow(), false);
				graphics.text(font, texts[i], partX, textY, parts[i].top(), false);
			}
			partX += font.width(texts[i]);
		}
	}

	private record Part(String text, int top, int bottom, int shadow) {
	}

	// Frame with cut corners
	private static void extractFrame(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
		graphics.fill(left + 1, top, right - 1, bottom, FRAME_COLOR);
		graphics.fill(left, top + 1, left + 1, bottom - 1, FRAME_COLOR);
		graphics.fill(right - 1, top + 1, right, bottom - 1, FRAME_COLOR);
	}
}
