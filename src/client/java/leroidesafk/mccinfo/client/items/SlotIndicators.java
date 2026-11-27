package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Small icons on the corners of item slots
public final class SlotIndicators {
	// Icons of infos.png
	public static final int NOT_OWNED = 0;
	public static final int ARROW = 1;
	public static final int MISSING_MATERIALS = 2;
	public static final int MAXED = 3;
	public static final int LOCKED = 4;
	public static final int LOWEST_PRICE = 6;
	public static final int COMPLETE = 5;
	public static final int DONATABLE = 10;

	// Icon sheet, 3 screen pixels per texture pixel at GUI Scale 3
	private static final int DESIGN_GUI_SCALE = 3;
	private static final Identifier TEXTURE = Mccinfo.id("textures/gui/ui/infos.png");
	private static final int ICON_SIZE = 8;
	private static final int TEXTURE_SIZE = 32;
	private static final int ICONS_PER_ROW = TEXTURE_SIZE / ICON_SIZE;
	private static final int TEXEL_PIXELS = 3;
	private static final int TOP_RIGHT_OFFSET_TEXELS = 2;
	private static final int BOTTOM_LEFT_OFFSET_TEXELS = 1;
	private static final int SLOT_SIZE = 16;

	// Lore, menus and blueprint data read for the icons
	private static final String MISSING_MATERIALS_MARKER = "(missing materials)";
	private static final String BULK_MARKER = "bulk";
	private static final List<String> CRAFT_ACTIONS = List.of("click to forge", "click to assemble", "click to craft");
	private static final String STYLE_PERKS = "style perks";
	private static final int TITLE_SLOT = 8;
	private static final String STYLE_LEVEL_MARKER = "reach style level";
	private static final String UNLOCK_MARKER = "to unlock";
	private static final String COSMETIC_COLLECTION = "cosmetic collection";
	private static final String COLLECTION_SUFFIX = " collection";
	private static final String UNLOCKED_PREFIX = "cosmetics unlocked:";
	private static final String CHROMAS_PREFIX = "full chromas unlocked:";
	private static final Pattern FRACTION = Pattern.compile("(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)");
	private static final String BUKKIT_VALUES = "PublicBukkitValues";
	private static final String BLUEPRINT = "mcc_island:blueprint";
	private static final String OWNERSHIP = "mcc_island:ownership";
	private static final String DONATIONS_CURRENT = "mcc_island:donations_current";
	private static final String DONATIONS_LIMIT = "mcc_island:donations_limit";

	// Checkmark of the completed fish
	private static final Identifier CHECK_TEXTURE = Mccinfo.id("textures/gui/check.png");
	private static final int CHECK_TEXTURE_SIZE = 16;
	private static final int CHECK_TEXEL_PIXELS = 2;

	private SlotIndicators() {
	}

	// Icon sticking out of the top right corner
	public static void topRight(GuiGraphicsExtractor graphics, Slot slot, int icon) {
		int guiScale = guiScale();
		float texel = texelPixels(TEXEL_PIXELS, guiScale);
		float offset = TOP_RIGHT_OFFSET_TEXELS * texel;
		draw(graphics, icon, (slot.x + SLOT_SIZE) * guiScale - ICON_SIZE * texel + offset, slot.y * guiScale - offset, texel, guiScale);
	}

	// Icon sticking out of the bottom left corner
	public static void bottomLeft(GuiGraphicsExtractor graphics, Slot slot, int icon) {
		int guiScale = guiScale();
		float texel = texelPixels(TEXEL_PIXELS, guiScale);
		float offset = BOTTOM_LEFT_OFFSET_TEXELS * texel;
		draw(graphics, icon, slot.x * guiScale - offset, (slot.y + SLOT_SIZE) * guiScale - ICON_SIZE * texel + offset, texel, guiScale);
	}

	// Screen pixels per texture pixel, follows the GUI Scale
	public static float texelPixels(float designPixels, int guiScale) {
		float pixels = designPixels * guiScale / DESIGN_GUI_SCALE;
		return pixels >= 1.0F ? Math.round(pixels) : pixels;
	}

	// Icon at its texture size, for other screens
	public static void icon(GuiGraphicsExtractor graphics, int icon, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, (icon % ICONS_PER_ROW) * ICON_SIZE, (icon / ICONS_PER_ROW) * ICON_SIZE, ICON_SIZE, ICON_SIZE,
				TEXTURE_SIZE, TEXTURE_SIZE);
	}

	// Checkmark in the bottom right corner
	public static void check(GuiGraphicsExtractor graphics, Slot slot) {
		int guiScale = guiScale();
		bottomCorner(graphics, slot, new Sheet(CHECK_TEXTURE, CHECK_TEXTURE_SIZE, CHECK_TEXTURE_SIZE, CHECK_TEXEL_PIXELS), 0, true, 0.0F, guiScale);
	}

	// Icon of a sheet in a bottom corner
	public record Sheet(Identifier texture, int size, int iconSize, float designTexel) {
	}

	public static void bottomCorner(GuiGraphicsExtractor graphics, Slot slot, Sheet sheet, int icon, boolean right) {
		int guiScale = guiScale();
		bottomCorner(graphics, slot, sheet, icon, right, BOTTOM_LEFT_OFFSET_TEXELS * texelPixels(TEXEL_PIXELS, guiScale), guiScale);
	}

	private static void bottomCorner(GuiGraphicsExtractor graphics, Slot slot, Sheet sheet, int icon, boolean right, float overhang, int guiScale) {
		float texel = texelPixels(sheet.designTexel(), guiScale);
		int perRow = sheet.size() / sheet.iconSize();
		float size = sheet.iconSize() * texel;
		float x = right ? (slot.x + SLOT_SIZE) * guiScale - size + overhang : slot.x * guiScale - overhang;
		graphics.pose().pushMatrix();
		graphics.pose().translate(Math.round(x) / (float) guiScale, Math.round((slot.y + SLOT_SIZE) * guiScale - size + overhang) / (float) guiScale);
		graphics.pose().scale(texel / guiScale, texel / guiScale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, sheet.texture(), 0, 0, icon % perRow * sheet.iconSize(), icon / perRow * sheet.iconSize(), sheet.iconSize(), sheet.iconSize(),
				sheet.size(), sheet.size());
		graphics.pose().popMatrix();
	}

	// Every item icon of a slot
	public static void extractItemIndicators(GuiGraphicsExtractor graphics, Slot slot) {
		ItemStack stack = slot.getItem();
		if (stack.isEmpty() || !OverlayStyle.onMccIsland() || !Permissions.has(Permissions.Perm.UTILS)) {
			return;
		}
		ModConfig config = ModConfig.get();
		if (config.missingMaterialsIcon && missingMaterials(stack)) {
			bottomLeft(graphics, slot, MISSING_MATERIALS);
		}
		if (config.lockedPerkIcon && lockedStylePerk(slot, stack)) {
			bottomLeft(graphics, slot, LOCKED);
		}
		if (config.completedCollectionIcon) {
			int collectionIcon = completedCollectionIcon(slot, stack);
			if (collectionIcon >= 0) {
				topRight(graphics, slot, collectionIcon);
			}
		}
		// Wardrobe donations: maxed, count or donatable
		leroidesafk.mccinfo.client.chat.MccApi.Donation donation = WardrobeDonations.donation(slot);
		if (donation != null && donation.maxed()) {
			topRight(graphics, slot, MAXED);
		} else if (donation != null && donation.made() >= 1) {
			int guiScale = guiScale();
			float texel = texelPixels(TEXEL_PIXELS, guiScale);
			float offset = TOP_RIGHT_OFFSET_TEXELS * texel;
			float centerX = (Math.round((slot.x + SLOT_SIZE) * guiScale - ICON_SIZE * texel + offset) + ICON_SIZE * texel / 2.0F) / guiScale;
			float centerY = (Math.round(slot.y * guiScale - offset) + ICON_SIZE * texel / 2.0F) / guiScale;
			BetterItemAmounts.extractOnIcon(graphics, Minecraft.getInstance().font, donation.made(), Math.round(centerX), Math.round(centerY));
		} else if (donation != null) {
			topRight(graphics, slot, DONATABLE);
		}
		Optional<CompoundTag> blueprint = blueprint(stack);
		if (blueprint.isEmpty()) {
			return;
		}
		if (blueprint.get().getBoolean(OWNERSHIP).map(owned -> !owned).orElse(false)) {
			if (config.notOwnedIcon) {
				topRight(graphics, slot, NOT_OWNED);
			}
		} else if (config.maxedCosmeticIcon && donationsMaxed(blueprint.get())) {
			topRight(graphics, slot, MAXED);
		}
	}

	// MCC blueprint data of a cosmetic
	private static Optional<CompoundTag> blueprint(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		return data.copyTag().getCompound(BUKKIT_VALUES).flatMap(values -> values.getCompound(BLUEPRINT));
	}

	// Donations at their limit
	private static boolean donationsMaxed(CompoundTag blueprint) {
		Optional<Integer> current = blueprint.getInt(DONATIONS_CURRENT);
		Optional<Integer> limit = blueprint.getInt(DONATIONS_LIMIT);
		return current.isPresent() && limit.isPresent() && limit.get() > 0 && current.get() >= limit.get();
	}


	// Style perk that needs a higher Style Level
	private static boolean lockedStylePerk(Slot slot, ItemStack stack) {
		if (!inStylePerks(slot)) {
			return false;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			String text = line.getString().toLowerCase(Locale.ROOT);
			if (text.contains(STYLE_LEVEL_MARKER) && text.contains(UNLOCK_MARKER)) {
				return true;
			}
		}
		return false;
	}

	// Complete collection: special icon with every Full Chroma, -1 if not complete
	private static int completedCollectionIcon(Slot slot, ItemStack stack) {
		if (!inCosmeticCollection(slot) || !stack.getHoverName().getString().toLowerCase(Locale.ROOT).endsWith(COLLECTION_SUFFIX)) {
			return -1;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return -1;
		}
		boolean unlockedAll = false;
		boolean chromasAll = true;
		for (Component line : lore.lines()) {
			String text = line.getString().strip().toLowerCase(Locale.ROOT);
			if (text.startsWith(UNLOCKED_PREFIX)) {
				unlockedAll = fractionComplete(text);
			} else if (text.startsWith(CHROMAS_PREFIX)) {
				chromasAll = fractionComplete(text);
			}
		}
		if (!unlockedAll) {
			return -1;
		}
		return chromasAll ? COMPLETE : MAXED;
	}

	// "N/M" with N at least M
	private static boolean fractionComplete(String text) {
		Matcher fraction = FRACTION.matcher(text);
		if (!fraction.find()) {
			return false;
		}
		int current = Integer.parseInt(fraction.group(1).replace(",", ""));
		int total = Integer.parseInt(fraction.group(2).replace(",", ""));
		return total > 0 && current >= total;
	}

	// Slot of the Cosmetic Collection menu
	private static boolean inCosmeticCollection(Slot slot) {
		return Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen && slot.container == screen.getMenu().getContainer()
				&& screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(COSMETIC_COLLECTION);
	}

	// Slot of the Style Perks menu, by its title or slot 8
	private static boolean inStylePerks(Slot slot) {
		if (!(Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen) || slot.container != screen.getMenu().getContainer()) {
			return false;
		}
		if (screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(STYLE_PERKS)) {
			return true;
		}
		Container container = screen.getMenu().getContainer();
		return container.getContainerSize() > TITLE_SLOT
				&& container.getItem(TITLE_SLOT).getHoverName().getString().toLowerCase(Locale.ROOT).contains(STYLE_PERKS);
	}

	// "(Missing materials)" on a single craft, on its line or alone after it
	private static boolean missingMaterials(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		String previous = "";
		for (Component line : lore.lines()) {
			String text = line.getString().toLowerCase(Locale.ROOT);
			if (text.contains(MISSING_MATERIALS_MARKER)) {
				String action = text.strip().equals(MISSING_MATERIALS_MARKER) ? previous : text;
				if (isSingleCraft(action)) {
					return true;
				}
			}
			previous = text;
		}
		return false;
	}

	// Forge, assemble or craft, not in bulk
	private static boolean isSingleCraft(String line) {
		if (line.contains(BULK_MARKER)) {
			return false;
		}
		for (String action : CRAFT_ACTIONS) {
			if (line.contains(action)) {
				return true;
			}
		}
		return false;
	}

	// Icon placed in screen pixels
	private static void draw(GuiGraphicsExtractor graphics, int icon, float xPixels, float yPixels, float texel, int guiScale) {
		float u = (icon % ICONS_PER_ROW) * ICON_SIZE;
		float v = (icon / ICONS_PER_ROW) * ICON_SIZE;
		graphics.pose().pushMatrix();
		graphics.pose().translate(Math.round(xPixels) / (float) guiScale, Math.round(yPixels) / (float) guiScale);
		graphics.pose().scale(texel / guiScale, texel / guiScale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, u, v, ICON_SIZE, ICON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
		graphics.pose().popMatrix();
	}

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}
}
