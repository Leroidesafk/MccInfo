package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.items.NeuDrag;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.mixin.accessor.ContainerHoveredSlotAccessor;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

// Item icons of the chat rooms
public final class ChatRoomIcons {
	private static final int SLOT_SIZE = 16;
	private static final int HALF_SLOT = SLOT_SIZE / 2;
	private static final float GROW = 0.3F;
	private static final float GROW_SPEED = 7.0F;
	private static final float MAX_FRAME_SECONDS = 0.1F;
	private static final int ITEM_TEXELS = 16;
	private static final int BUTTON_ICON_SIZE = 13;
	private static final Map<Integer, Float> GROWTH = new HashMap<>();
	private static final FontDescription ICON_FONT = new FontDescription.Resource(Mccinfo.id("icons"));
	private static final String DRAG_ICON = "";
	private static final String MIDDLE_CLICK_ICON = "";
	private static final int ICON_COLOR = 0xFFFFFF;
	private static final int CLICK_DARK = 0x505050;
	private static final int CLICK_TEXT = 0xECD584;
	private static final int CLICK_ACTION = 0xFEE761;

	private static long lastFrame;

	private ChatRoomIcons() {
	}

	public static void register() {
		// Item dropped on a room
		NeuDrag.addTarget(new NeuDrag.Target() {
			@Override
			public boolean accepts(Screen screen) {
				return ModChat.roomChatsEnabled() && ChatChannels.roomsScreen(screen);
			}

			@Override
			public String targetAt(Screen screen, double mouseX, double mouseY) {
				Slot slot = roomSlotAt((ContainerScreen) screen, mouseX, mouseY);
				return slot == null ? null : ChatChannels.roomName(slot.getItem());
			}

			@Override
			public void drop(Screen screen, String target, String id) {
				ModConfig config = ModConfig.get();
				config.chatRoomIcons.put(target, id);
				config.save();
				NeuItems.clickSound();
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (ChatChannels.roomsScreen(screen)) {
				GROWTH.clear();
				lastFrame = 0L;
				ScreenEvents.afterForeground(screen).register((current, graphics, mouseX, mouseY, a) -> extract((ContainerScreen) current, graphics, mouseX, mouseY));
				ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !removeClick((ContainerScreen) current, event));
			}
		});
		// Action lines in the room lore
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			String room = ChatChannels.roomName(stack);
			if (room == null || !ModChat.roomChatsEnabled() || !ChatChannels.roomsScreen(Minecraft.getInstance().gui.screen())) {
				return;
			}
			boolean hasIcon = ModConfig.get().chatRoomIcons.containsKey(room);
			lines.add(actionLine(DRAG_ICON, "Drag an Item to ", hasIcon ? "Change Icon" : "Set Icon"));
			if (hasIcon) {
				lines.add(actionLine(MIDDLE_CLICK_ICON, "Middle-Click to ", "Remove Icon"));
			}
		});
	}

	private static Component actionLine(String icon, String text, String action) {
		MutableComponent line = Component.empty().append(Component.literal(icon).withStyle(style -> style.withFont(ICON_FONT).withColor(ICON_COLOR)));
		return line.append(Component.literal(" > ").withColor(CLICK_DARK))
				.append(Component.literal(text).withColor(CLICK_TEXT))
				.append(Component.literal(action).withColor(CLICK_ACTION));
	}

	// Middle-click removes the icon
	private static boolean removeClick(ContainerScreen screen, MouseButtonEvent event) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || !ModChat.roomChatsEnabled()) {
			return false;
		}
		Slot slot = roomSlotAt(screen, event.x(), event.y());
		String room = slot == null ? null : ChatChannels.roomName(slot.getItem());
		ModConfig config = ModConfig.get();
		if (room == null || config.chatRoomIcons.remove(room) == null) {
			return false;
		}
		config.save();
		NeuItems.clickSound();
		return true;
	}

	static ItemStack icon(String room) {
		String id = ModConfig.get().chatRoomIcons.get(room);
		return id == null ? ItemStack.EMPTY : NeuItems.stack(id);
	}

	// Icon on the room chat button, pixel perfect
	static void extractButtonIcon(GuiGraphicsExtractor graphics, ItemStack icon, int x, int y) {
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		int box = BUTTON_ICON_SIZE * guiScale;
		int texel = Math.max(1, box / ITEM_TEXELS);
		int margin = (box - ITEM_TEXELS * texel) / 2;
		float scale = (float) texel / guiScale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + (float) margin / guiScale, y + (float) margin / guiScale);
		graphics.pose().scale(scale, scale);
		graphics.fakeItem(icon, 0, 0);
		graphics.pose().popMatrix();
	}

	private static Slot roomSlotAt(ContainerScreen screen, double mouseX, double mouseY) {
		ContainerHoveredSlotAccessor accessor = (ContainerHoveredSlotAccessor) screen;
		for (Slot slot : screen.getMenu().slots) {
			int x = accessor.mccinfo$getLeftPos() + slot.x;
			int y = accessor.mccinfo$getTopPos() + slot.y;
			if (mouseX >= x - 1 && mouseX < x + SLOT_SIZE + 1 && mouseY >= y - 1 && mouseY < y + SLOT_SIZE + 1 && ChatChannels.roomName(slot.getItem()) != null) {
				return slot;
			}
		}
		return null;
	}

	// Room under the drag grows, icon in its corner
	private static void extract(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!ModChat.roomChatsEnabled()) {
			return;
		}
		long now = Util.getMillis();
		float seconds = lastFrame == 0L ? 0.0F : Math.min(MAX_FRAME_SECONDS, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		Slot target = NeuDrag.dragging(screen) ? roomSlotAt(screen, mouseX, mouseY) : null;
		ContainerHoveredSlotAccessor accessor = (ContainerHoveredSlotAccessor) screen;
		for (Slot slot : screen.getMenu().slots) {
			String name = ChatChannels.roomName(slot.getItem());
			if (name == null) {
				continue;
			}
			float growth = GROWTH.getOrDefault(slot.index, 0.0F);
			growth = Mth.clamp(growth + (slot == target ? seconds : -seconds) * GROW_SPEED, 0.0F, 1.0F);
			GROWTH.put(slot.index, growth);
			ItemStack icon = icon(name);
			if (growth <= 0.0F && icon.isEmpty()) {
				continue;
			}
			float eased = growth * growth * (3.0F - 2.0F * growth);
			float scale = 1.0F + GROW * eased;
			float centerX = accessor.mccinfo$getLeftPos() + slot.x + HALF_SLOT;
			float centerY = accessor.mccinfo$getTopPos() + slot.y + HALF_SLOT;
			if (growth > 0.0F) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(centerX, centerY);
				graphics.pose().scale(scale, scale);
				graphics.fakeItem(slot.getItem(), -HALF_SLOT, -HALF_SLOT);
				graphics.pose().popMatrix();
			}
			if (!icon.isEmpty()) {
				NeuItems.extractBadge(graphics, icon, centerX + HALF_SLOT * scale, centerY + HALF_SLOT * scale);
			}
		}
	}
}
