package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.PrivateKeys;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.items.NeuItems;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.ContainerHoveredSlotAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ItemClipboard {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = ModConfig.DIRECTORY.resolve("clipboard.json");

	private ItemClipboard() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			// Copy key in every inventory
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> !(PrivateKeys.COPY_ITEM.matches(event) && copy(current)));
				ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !(PrivateKeys.COPY_ITEM.matchesMouse(event) && copy(current)));
			}
		});
	}

	static JsonElement item() {
		try {
			JsonElement file = com.google.gson.JsonParser.parseString(Files.readString(PATH));
			return file.isJsonObject() && file.getAsJsonObject().has("item") ? file.getAsJsonObject().get("item") : null;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static boolean copy(Screen screen) {
		Minecraft client = Minecraft.getInstance();
		if (!PrivateKeys.COPY_ITEM.usable() || client.level == null || client.player == null) {
			return false;
		}
		// NEU list first, then the slot
		ItemStack hovered = NeuItems.hovered();
		if (hovered.isEmpty()) {
			Slot slot = ((ContainerHoveredSlotAccessor) screen).mccinfo$getHoveredSlot();
			hovered = slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
		}
		if (hovered.isEmpty()) {
			return false;
		}
		// Clipboard file
		ItemStack stack = hovered.copyWithCount(1);
		String name = stack.getHoverName().getString().strip();
		JsonObject file = new JsonObject();
		file.addProperty("name", name);
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		file.addProperty("model", model == null ? null : model.toString());
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess());
		JsonElement item = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
		file.add("item", item);
		JsonArray lore = new JsonArray();
		ItemLore itemLore = stack.get(DataComponents.LORE);
		if (itemLore != null) {
			itemLore.lines().forEach(line -> lore.add(line.getString()));
		}
		file.add("lore", lore);
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(file));
		} catch (IOException e) {
			Mccinfo.LOGGER.warn("Could not write the item clipboard ({})", PATH, e);
			client.player.sendOverlayMessage(Component.translatable("message.mccinfo.copy_item.failed", name).withStyle(ChatFormatting.RED));
			return true;
		}
		client.gui.toastManager().addToast(new CopiedToast(stack));
		return true;
	}

	// Item copied toast
	private static final class CopiedToast implements Toast {
		private static final int WIDTH = 180;
		private static final int HEIGHT = 26;
		private static final int BACKGROUND_COLOR = 0xC0111111;
		private static final int TEXT_LEFT = 26;
		private static final long DISPLAY_MILLIS = 3000L;

		private final ItemStack stack;
		private final Component title = Component.translatable("toast.mccinfo.copy_item.title");
		private final Component name;
		private Visibility wantedVisibility = Visibility.HIDE;

		private CopiedToast(ItemStack stack) {
			this.stack = stack;
			this.name = stack.getHoverName();
		}

		@Override
		public int width() {
			return WIDTH;
		}

		@Override
		public int height() {
			return HEIGHT;
		}

		@Override
		public Visibility getWantedVisibility() {
			return this.wantedVisibility;
		}

		@Override
		public void update(ToastManager manager, long visibleMillis) {
			this.wantedVisibility = visibleMillis > DISPLAY_MILLIS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleMillis) {
			OverlayStyle.fillRounded(graphics, 1, 2, WIDTH - 3, HEIGHT - 2, BACKGROUND_COLOR);
			graphics.fakeItem(this.stack, 5, 6);
			graphics.text(font, this.title, TEXT_LEFT, 6, 0xFFFFFFFF);
			graphics.text(font, font.plainSubstrByWidth(this.name.getString(), WIDTH - TEXT_LEFT - 6), TEXT_LEFT, 15, 0xFFFFFFFF);
		}
	}
}
