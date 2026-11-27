package leroidesafk.mccinfo.client.items;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.IconToast;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import leroidesafk.mccinfo.client.chat.ItemClipboard;
import leroidesafk.mccinfo.client.chat.ModChat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CraftTimers {
	public static final String FORGE = "forge";
	public static final String ASSEMBLER = "assembler";
	// Menus found by the name of slot 8
	private static final Map<String, String> STATIONS = Map.of("fusion forge", FORGE, "blueprint assembler", ASSEMBLER);
	private static final int TITLE_SLOT = 8;
	private static final int SCAN_TICKS = 10;
	private static final int CHECK_TICKS = 20;
	private static final long MINUTE = 60_000L;
	private static final long SECOND = 1_000L;
	private static final Pattern REMAINING = Pattern.compile("^(?:(<\\s*1m)|(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\s*remaining$");
	private static final Pattern GLYPHS = Pattern.compile("[\\uE000-\\uF8FF]");
	private static final List<DataComponentType<?>> ICON_COMPONENTS = List.of(DataComponents.ITEM_MODEL, DataComponents.DYED_COLOR,
			DataComponents.CUSTOM_MODEL_DATA, DataComponents.PROFILE);
	private static final float SOUND_PITCH = 1.5F;

	private static int scanTicks;
	private static int checkTicks;

	private CraftTimers() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CraftTimers::tick);
	}

	// Menu scan and finished crafts
	private static void tick(Minecraft client) {
		ModConfig config = ModConfig.get();
		if (!config.craftNotify) {
			return;
		}
		if (++scanTicks >= SCAN_TICKS) {
			scanTicks = 0;
			scan(client, config);
		}
		if (++checkTicks >= CHECK_TICKS) {
			checkTicks = 0;
			notifyFinished(client, config);
		}
	}

	// Time left of each craft in the open menu
	private static void scan(Minecraft client, ModConfig config) {
		if (!(client.gui.screen() instanceof ContainerScreen screen) || !OverlayStyle.onMccIsland() || !Permissions.has(Permissions.Perm.UTILS)) {
			return;
		}
		Container container = screen.getMenu().getContainer();
		if (container.getContainerSize() <= TITLE_SLOT) {
			return;
		}
		String station = STATIONS.get(clean(container.getItem(TITLE_SLOT).getHoverName().getString()));
		if (station == null) {
			return;
		}
		long now = System.currentTimeMillis();
		List<ModConfig.CraftTimer> timers = new ArrayList<>(config.craftTimers);
		boolean changed = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (slot == TITLE_SLOT) {
				continue;
			}
			ItemStack stack = container.getItem(slot);
			int index = find(timers, station, slot);
			ModConfig.CraftTimer old = index < 0 ? null : timers.get(index);
			long[] remaining = stack.isEmpty() ? null : remaining(stack);
			String name = stack.isEmpty() ? "" : clean(stack.getHoverName().getString());
			// No time left: claimed, canceled or done
			if (remaining == null) {
				if (old != null) {
					timers.remove(index);
					changed = true;
				}
				continue;
			}
			// End time narrowed on each scan (idk if it's the best way to do this, but it's ok)
			long earliest = now + remaining[0];
			long end = now + remaining[1];
			ModConfig.CraftTimer timer;
			if (old != null && old.name().equals(name) && earliest <= old.end() && end >= old.earliest()) {
				timer = new ModConfig.CraftTimer(station, slot, name, old.icon(), Math.max(earliest, old.earliest()), Math.min(end, old.end()), false);
			} else {
				timer = new ModConfig.CraftTimer(station, slot, name, icon(stack), earliest, end, false);
			}
			if (!timer.equals(old)) {
				if (index < 0) {
					timers.add(timer);
				} else {
					timers.set(index, timer);
				}
				changed = true;
			}
		}
		if (changed) {
			config.craftTimers = timers;
			config.save();
		}
	}

	// Debug notification with the clipboard item
	public static int debugNotify(FabricClientCommandSource source, String station) {
		Minecraft client = Minecraft.getInstance();
		JsonElement json = ItemClipboard.item();
		ItemStack stack = json == null || client.level == null ? ItemStack.EMPTY
				: ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess()), json).result().orElse(ItemStack.EMPTY);
		if (stack.isEmpty()) {
			source.sendError(Component.literal("No item in the clipboard (copy one with the Copy Item key first)"));
			return 0;
		}
		ModConfig config = ModConfig.get();
		long now = System.currentTimeMillis();
		List<ModConfig.CraftTimer> timers = new ArrayList<>(config.craftTimers);
		timers.add(new ModConfig.CraftTimer(station, -1, clean(stack.getHoverName().getString()), icon(stack), now, now, false));
		config.craftTimers = timers;
		notifyFinished(client, config);
		if (!config.craftNotifyToast && !config.craftNotifyChat) {
			source.sendFeedback(Component.literal("Toast and Chat Message are both turned off, nothing was shown"));
		}
		return 1;
	}

	private static int find(List<ModConfig.CraftTimer> timers, String station, int slot) {
		for (int i = 0; i < timers.size(); i++) {
			if (timers.get(i).station().equals(station) && timers.get(i).slot() == slot) {
				return i;
			}
		}
		return -1;
	}

	// Time left read in the lore
	static long[] remaining(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return null;
		}
		for (Component line : lore.lines()) {
			Matcher matcher = REMAINING.matcher(clean(line.getString()));
			if (!matcher.matches()) {
				continue;
			}
			if (matcher.group(1) != null) {
				return new long[] {0L, MINUTE};
			}
			if (matcher.group(2) == null && matcher.group(3) == null && matcher.group(4) == null) {
				continue;
			}
			long hours = matcher.group(2) == null ? 0L : Long.parseLong(matcher.group(2));
			long minutes = matcher.group(3) == null ? 0L : Long.parseLong(matcher.group(3));
			long seconds = matcher.group(4) == null ? 0L : Long.parseLong(matcher.group(4));
			long low = hours * 60 * MINUTE + minutes * MINUTE + seconds * SECOND;
			return new long[] {low, low + (matcher.group(4) != null ? SECOND : MINUTE)};
		}
		return null;
	}

	private static String clean(String text) {
		return GLYPHS.matcher(text).replaceAll("").strip().toLowerCase(Locale.ROOT);
	}

	// Small copy of the item for the toast icon
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static JsonElement icon(ItemStack stack) {
		ItemStack icon = new ItemStack(stack.getItem());
		for (DataComponentType type : ICON_COMPONENTS) {
			Object value = stack.get(type);
			if (value != null) {
				icon.set(type, value);
			}
		}
		return ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, icon).result().orElse(null);
	}

	private static ItemStack iconStack(JsonElement json) {
		if (json == null) {
			return ItemStack.EMPTY;
		}
		return ItemStack.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(error -> Mccinfo.LOGGER.warn("Unreadable craft timer icon: {}", error))
				.orElse(ItemStack.EMPTY);
	}

	// Toast and chat message of finished crafts
	private static void notifyFinished(Minecraft client, ModConfig config) {
		if (config.craftTimers.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean inWorld = client.player != null;
		List<ModConfig.CraftTimer> kept = new ArrayList<>();
		Map<String, List<ModConfig.CraftTimer>> toasts = new LinkedHashMap<>();
		boolean changed = false;
		boolean sound = false;
		for (ModConfig.CraftTimer timer : config.craftTimers) {
			if (timer.end() > now) {
				kept.add(timer);
				continue;
			}
			changed = true;
			boolean fresh = !timer.chatPending();
			sound |= fresh;
			if (fresh && config.craftNotifyToast) {
				toasts.computeIfAbsent(timer.station() + "\n" + timer.name(), key -> new ArrayList<>()).add(timer);
			}
			boolean chat = config.craftNotifyChat && (fresh || timer.chatPending());
			if (chat && inWorld) {
				client.player.sendSystemMessage(chatLine(timer));
			} else if (chat) {
				// Chat message kept for the next world
				kept.add(new ModConfig.CraftTimer(timer.station(), timer.slot(), timer.name(), timer.icon(), timer.earliest(), timer.end(), true));
			}
		}
		for (List<ModConfig.CraftTimer> group : toasts.values()) {
			ModConfig.CraftTimer first = group.getFirst();
			ItemStack icon = iconStack(first.icon());
			Component item = itemName(first, group.size());
			client.gui.toastManager().addToast(new IconToast(icon, Component.translatable("toast.mccinfo.craft_timer." + first.station()), item));
		}
		// One sound for all finished crafts
		if (sound && config.craftNotifySound) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, SOUND_PITCH));
		}
		if (changed) {
			config.craftTimers = kept;
			config.save();
		}
	}

	private static Component itemName(ModConfig.CraftTimer timer, int count) {
		Component name = displayName(timer);
		return count > 1 ? Component.translatable("toast.mccinfo.craft_timer.count", name, count) : name;
	}

	// NEU name, or the saved name in Title Case
	private static Component displayName(ModConfig.CraftTimer timer) {
		ItemStack known = NeuItems.stackByName(timer.name());
		if (!known.isEmpty()) {
			return Component.literal(GLYPHS.matcher(known.getHoverName().getString()).replaceAll("").strip());
		}
		StringBuilder text = new StringBuilder();
		for (String word : timer.name().split(" ")) {
			if (!word.isEmpty()) {
				text.append(text.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return Component.literal(text.toString());
	}

	// (icon) Your [item] is done in the station
	private static Component chatLine(ModConfig.CraftTimer timer) {
		String id = NeuItems.idByName(timer.name());
		ItemStack item = id == null ? ItemStack.EMPTY : NeuItems.stack(id);
		if (item.isEmpty()) {
			item = iconStack(timer.icon());
		}
		Component shown = itemPiece("[", displayName(timer), "]", item);
		MutableComponent line = Component.translatable("message.mccinfo.craft_timer." + timer.station(), shown).withStyle(ChatFormatting.GREEN);
		if (item.isEmpty()) {
			return line;
		}
		return Component.literal("(").withStyle(ChatFormatting.GREEN).append(ModChat.itemIcon(item)).append(Component.literal(") ").withStyle(ChatFormatting.GREEN)).append(line);
	}

	// Item name with its color and tooltip
	private static Component itemPiece(String open, Component name, String close, ItemStack stack) {
		TextColor color = stack.isEmpty() ? null : stack.getHoverName().getStyle().getColor();
		MutableComponent piece = Component.literal(open).append(name).append(close)
				.withStyle(style -> style.withColor(color != null ? color : TextColor.fromLegacyFormat(ChatFormatting.YELLOW)));
		if (!stack.isEmpty()) {
			piece.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1)))));
		}
		return piece;
	}
}
