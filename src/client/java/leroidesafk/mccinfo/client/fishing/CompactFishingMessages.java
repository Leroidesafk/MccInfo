package leroidesafk.mccinfo.client.fishing;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.mixin.accessor.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Merges the catch messages into one line with item icons
public final class CompactFishingMessages {
	// Elusive icon of the fish icon font
	private static final String ELUSIVE_GLYPH = "\uE10A";
	// Catch, trigger and earned messages
	private static final Pattern CAUGHT_PATTERN = Pattern.compile("You caught: \\[([^\\[\\]]+)]");
	private static final Pattern TRIGGER_PATTERN = Pattern.compile("(?:Triggered|Special):(.*)$");
	private static final Pattern EARNED_PATTERN = Pattern.compile("You earned: (.+)$");
	private static final Pattern BONUS_PATTERN = Pattern.compile("\\s*\\(\\+[\\d.]+%\\s*Bonus\\)\\s*$");
	private static final String BONUS_ITEM = "cleansing_powder";
	private static final Pattern AMOUNT_PATTERN = Pattern.compile("^[\\d,.]+");
	private static final Pattern GLYPHS = Pattern.compile("[-]");
	private static final List<String> TRIGGERS = List.of("Speedy Rod", "Boosted Rod", "Graceful Rod", "Stable Rod", "Glitched Rod",
			"XP Magnet", "Fish Magnet", "Pearl Magnet", "Treasure Magnet", "Spirit Magnet", "Elusive Catch", "Supply Preserve");
	private static final String ELUSIVE_CATCH = "Elusive Catch";
	private static final String AUGMENT_SUFFIX = " Augment";
	// Messages within 3 seconds of a catch join its line
	private static final long SESSION_MILLIS = 3_000L;
	private static final int RECENT_CHAT_LINES = 10;
	private static final int WHITE = 0xFFFFFF;
	private static final int XP_COLOR = 0xAAAAAA;

	private static Component current;
	private static boolean inBox;
	private static long lastMillis;

	private record Segment(Style style, String text) {
	}

	private CompactFishingMessages() {
	}

	static boolean active() {
		return ModConfig.get().compactFishingMessages && OverlayStyle.onMccIsland() && Permissions.has(Permissions.Perm.UTILS);
	}

	// "You caught": the glyph after "(" becomes the item icon
	static Component compact(Component message) {
		String text = message.getString();
		Matcher caught = CAUGHT_PATTERN.matcher(text);
		if (!caught.find()) {
			return message;
		}
		current = null;
		Component icon = itemIcon(NeuItems.stackByName(caught.group(1)));
		if (icon == null) {
			return message;
		}
		MutableComponent result = Component.empty();
		StringBuilder before = new StringBuilder();
		boolean replaced = false;
		for (Segment segment : segments(message)) {
			if (!replaced && isGlyph(segment.text()) && before.toString().stripTrailing().endsWith("(")) {
				result.append(icon.copy().setStyle(segment.style().withFont(null).withColor(WHITE)));
				replaced = true;
			} else {
				result.append(Component.literal(segment.text()).setStyle(segment.style()));
			}
			before.append(segment.text());
		}
		return replaced ? result : message;
	}

	// Remembers the catch line, in the chat or in the Fishing Messages box
	static void shown(Component message, boolean box) {
		if (CAUGHT_PATTERN.matcher(message.getString()).find()) {
			current = message;
			inBox = box;
			lastMillis = System.currentTimeMillis();
		}
	}

	// Adds a trigger or earning to the catch line, the full message on hover
	static boolean absorb(Component message) {
		if (current == null || System.currentTimeMillis() - lastMillis > SESSION_MILLIS) {
			current = null;
			return false;
		}
		String text = message.getString();
		Matcher trigger = TRIGGER_PATTERN.matcher(text);
		Matcher earned = EARNED_PATTERN.matcher(text);
		Component addition;
		if (trigger.find()) {
			addition = triggerIcon(message, trigger.group(1));
		} else if (earned.find()) {
			addition = earnedAddition(GLYPHS.matcher(earned.group(1)).replaceAll("").strip());
		} else {
			return false;
		}
		Style hover = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(message));
		MutableComponent updated = current.copy().append(Component.literal(" ")).append(addition.copy().withStyle(style -> style.applyTo(hover)));
		if (!replace(current, updated)) {
			current = null;
			return false;
		}
		current = updated;
		lastMillis = System.currentTimeMillis();
		return true;
	}

	// Swaps the catch line, found by identity or else by text in the last lines
	private static boolean replace(Component old, Component updated) {
		if (inBox) {
			return FishingMessagesOverlay.replace(old, updated);
		}
		ChatComponentAccessor chat = (ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat();
		List<GuiMessage> messages = chat.mccinfo$getAllMessages();
		int index = -1;
		for (int i = 0; i < messages.size(); i++) {
			if (messages.get(i).content() == old) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			String text = old.getString();
			for (int i = 0; i < Math.min(messages.size(), RECENT_CHAT_LINES); i++) {
				if (messages.get(i).content().getString().contains(text)) {
					index = i;
					break;
				}
			}
		}
		if (index < 0) {
			return false;
		}
		GuiMessage message = messages.get(index);
		messages.set(index, new GuiMessage(message.addedTime(), updated, message.signature(), message.source(), message.tag()));
		chat.mccinfo$refreshTrimmedMessages();
		return true;
	}

	// An earning with a "(+X% Bonus)" shows the Cleansing Powder icon
	private static Component earnedAddition(String earned) {
		Matcher bonus = BONUS_PATTERN.matcher(earned);
		if (bonus.find()) {
			Component icon = itemIcon(NeuItems.stack(BONUS_ITEM));
			Matcher amount = AMOUNT_PATTERN.matcher(earned);
			if (icon != null && amount.find()) {
				return Component.empty().append(Component.literal("+" + amount.group() + " ").withColor(XP_COLOR))
						.append(icon.copy().withColor(WHITE));
			}
		}
		return Component.literal("+" + earned).withColor(XP_COLOR);
	}

	// Augment icon, else item icon, else MCC's own glyph
	private static Component triggerIcon(Component message, String rest) {
		String name = null;
		for (String known : TRIGGERS) {
			if (rest.contains(known)) {
				name = known;
				break;
			}
		}
		if (name == null) {
			name = GLYPHS.matcher(rest).replaceAll("").replaceAll("[!.]+$", "").strip();
		}
		if (name.equals(ELUSIVE_CATCH)) {
			return Component.literal(ELUSIVE_GLYPH)
					.setStyle(Style.EMPTY.withColor(WHITE).withFont(new FontDescription.Resource(FishIconRegistry.ICON_FONT)));
		}
		Component icon = itemIcon(NeuItems.stackByName(name + AUGMENT_SUFFIX));
		if (icon == null) {
			icon = itemIcon(NeuItems.stackByName(name));
		}
		if (icon != null) {
			return icon.copy().withColor(WHITE);
		}
		for (Segment segment : segments(message)) {
			if (isGlyph(segment.text())) {
				return Component.literal(segment.text().strip()).setStyle(segment.style());
			}
		}
		return Component.literal(name).withColor(XP_COLOR);
	}

	// Item texture as a text sprite
	public static Component itemIcon(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (stack.isEmpty() || minecraft.level == null) {
			return null;
		}
		ItemStackRenderState state = new ItemStackRenderState();
		minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, minecraft.level, null, 0);
		Material.Baked material = state.pickParticleMaterial(RandomSource.create(0L));
		if (material == null) {
			return null;
		}
		TextureAtlasSprite sprite = material.sprite();
		Identifier atlas = TextureAtlas.LOCATION_ITEMS.equals(sprite.atlasLocation()) ? AtlasIds.ITEMS
				: TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation()) ? AtlasIds.BLOCKS : null;
		Identifier name = sprite.contents().name();
		if (atlas == null || name.equals(MissingTextureAtlasSprite.getLocation())) {
			return null;
		}
		return Component.object(new AtlasSprite(atlas, name), stack.getHoverName());
	}

	private static List<Segment> segments(Component message) {
		List<Segment> segments = new ArrayList<>();
		message.visit((style, text) -> {
			segments.add(new Segment(style, text));
			return Optional.empty();
		}, Style.EMPTY);
		return segments;
	}

	private static boolean isGlyph(String text) {
		String stripped = text.strip();
		return !stripped.isEmpty() && GLYPHS.matcher(stripped).replaceAll("").isEmpty();
	}
}
