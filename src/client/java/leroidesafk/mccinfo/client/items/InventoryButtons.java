package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.mixin.accessor.ContainerHoveredSlotAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InventoryButtons {
	// Button size, icon types and backgrounds
	static final int SIZE = 18;
	static final String ICON_ITEM = "item";
	static final String ICON_PLAYER = "player";
	static final String ICON_UI = "ui";
	static final String BACKGROUND_NONE = "none";
	static final String BACKGROUND_BLUE = "blue_button";
	static final List<String> BACKGROUNDS = List.of(BACKGROUND_BLUE, BACKGROUND_NONE);
	// Blue background tiles, joined like connected textures
	private static final Identifier BLUE_TEXTURE = Identifier.fromNamespaceAndPath("mccinfo", "textures/gui/buttons/blue_button.png");
	private static final int TILE = 22;
	// Shown only with the MCC pocket in slot 9
	private static final int POCKET_SLOT = 8;
	private static final Identifier POCKET_MODEL = Identifier.parse("mcc:island_interface/misc/pocket");
	static final Identifier HEAD_MODEL = Identifier.fromNamespaceAndPath("mccinfo", "player_head");
	private static final int TILE_COLUMNS = 12;
	private static final int TEXTURE_WIDTH = 264;
	private static final int TEXTURE_HEIGHT = 88;
	private static final int BLUE_HOVER_COLOR = 0x40FFFFFF;
	private static final int[][] NEIGHBOURS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
	// Tile for each mask of blue neighbours
	private static final int[] CTM = {
		0, 36, 1, 16, 12, 24, 4, 6, 3, 17, 2, 18, 5, 19, 7, 46, 0, 36, 1, 37, 12, 24, 4, 30, 3, 17, 2, 40, 5, 19, 7, 8,
		0, 36, 1, 16, 12, 24, 13, 28, 3, 17, 2, 18, 5, 19, 31, 9, 0, 36, 1, 37, 12, 24, 13, 25, 3, 17, 2, 40, 5, 19, 31, 23,
		0, 36, 1, 16, 12, 24, 4, 6, 3, 17, 2, 18, 15, 43, 29, 21, 0, 36, 1, 37, 12, 24, 4, 30, 3, 17, 2, 40, 15, 43, 29, 34,
		0, 36, 1, 16, 12, 24, 13, 28, 3, 17, 2, 18, 15, 43, 14, 22, 0, 36, 1, 37, 12, 24, 13, 25, 3, 17, 2, 40, 15, 43, 14, 45,
		0, 36, 1, 16, 12, 24, 4, 6, 3, 39, 2, 42, 5, 41, 7, 20, 0, 36, 1, 37, 12, 24, 4, 30, 3, 39, 2, 38, 5, 41, 7, 11,
		0, 36, 1, 16, 12, 24, 13, 28, 3, 39, 2, 42, 5, 41, 31, 35, 0, 36, 1, 37, 12, 24, 13, 25, 3, 39, 2, 38, 5, 41, 31, 33,
		0, 36, 1, 16, 12, 24, 4, 6, 3, 39, 2, 42, 15, 27, 29, 10, 0, 36, 1, 37, 12, 24, 4, 30, 3, 39, 2, 38, 15, 27, 29, 32,
		0, 36, 1, 16, 12, 24, 13, 28, 3, 39, 2, 42, 15, 27, 14, 44, 0, 36, 1, 37, 12, 24, 13, 25, 3, 39, 2, 38, 15, 27, 14, 26
	};

	record Position(int x, int y) {
	}

	static final List<Position> POSITIONS = positions();
	private static final Map<String, ItemStack> HEADS = new HashMap<>();

	private InventoryButtons() {
	}

	// Slots around and inside the inventory
	private static List<Position> positions() {
		List<Position> positions = new ArrayList<>();
		for (int column = 0; column < 9; column++) {
			positions.add(new Position(7 + column * 18, -20));
		}
		int[] sideRows = {7, 25, 43, 61, 83, 101, 119, 141};
		for (int row : sideRows) {
			positions.add(new Position(-20, row));
		}
		for (int row : sideRows) {
			positions.add(new Position(178, row));
		}
		positions.add(new Position(25, 7));
		positions.add(new Position(57, 7));
		positions.add(new Position(25, 61));
		positions.add(new Position(57, 61));
		positions.add(new Position(97, 17));
		positions.add(new Position(115, 17));
		positions.add(new Position(97, 35));
		positions.add(new Position(115, 35));
		positions.add(new Position(153, 27));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				positions.add(new Position(7 + column * 18, 83 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			positions.add(new Position(7 + column * 18, 168));
		}
		return List.copyOf(positions);
	}

	public static void register() {
		// Drawn and clicked in the player inventory only
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen)) {
				return;
			}
			ScreenEvents.afterForeground(screen).register((current, graphics, mouseX, mouseY, a) -> extract(current, graphics, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
				if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
					return true;
				}
				ModConfig.InventoryButton button = hovered(current, event.x(), event.y());
				if (button == null) {
					return true;
				}
				run(button);
				return false;
			});
		});
	}

	private static boolean shown() {
		LocalPlayer player = Minecraft.getInstance().player;
		return MccIsland.isConnected() && player != null
				&& POCKET_MODEL.equals(player.getInventory().getItem(POCKET_SLOT).get(DataComponents.ITEM_MODEL));
	}

	static ModConfig.InventoryButton find(int slot) {
		for (ModConfig.InventoryButton button : ModConfig.get().inventoryButtons) {
			if (button.slot() == slot) {
				return button;
			}
		}
		return null;
	}

	private static ModConfig.InventoryButton hovered(Screen screen, double mouseX, double mouseY) {
		if (!shown()) {
			return null;
		}
		ContainerHoveredSlotAccessor container = (ContainerHoveredSlotAccessor) screen;
		int left = container.mccinfo$getLeftPos();
		int top = container.mccinfo$getTopPos();
		for (ModConfig.InventoryButton button : ModConfig.get().inventoryButtons) {
			if (button.slot() < 0 || button.slot() >= POSITIONS.size()) {
				continue;
			}
			Position position = POSITIONS.get(button.slot());
			int x = left + position.x();
			int y = top + position.y();
			if (mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE) {
				return button;
			}
		}
		return null;
	}

	// Runs the button command
	private static void run(ModConfig.InventoryButton button) {
		LocalPlayer player = Minecraft.getInstance().player;
		String command = button.command().strip();
		if (player == null || command.length() <= 1) {
			return;
		}
		NeuItems.clickSound();
		player.connection.sendCommand(command.substring(1));
	}

	private static void extract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!shown() || ModConfig.get().inventoryButtons.isEmpty()) {
			return;
		}
		ContainerHoveredSlotAccessor container = (ContainerHoveredSlotAccessor) screen;
		ModConfig.InventoryButton hovered = hovered(screen, mouseX, mouseY);
		// Blue backgrounds first, then the icons
		graphics.nextStratum();
		for (ModConfig.InventoryButton button : ModConfig.get().inventoryButtons) {
			if (button.slot() >= 0 && button.slot() < POSITIONS.size()) {
				Position position = POSITIONS.get(button.slot());
				extractBackground(graphics, button, container.mccinfo$getLeftPos() + position.x(), container.mccinfo$getTopPos() + position.y());
			}
		}
		for (ModConfig.InventoryButton button : ModConfig.get().inventoryButtons) {
			if (button.slot() < 0 || button.slot() >= POSITIONS.size()) {
				continue;
			}
			Position position = POSITIONS.get(button.slot());
			extractButton(graphics, button, container.mccinfo$getLeftPos() + position.x(), container.mccinfo$getTopPos() + position.y(),
					button == hovered, false);
		}
		if (hovered != null) {
			NeuItems.pointer(graphics);
		}
	}

	static boolean blue(ModConfig.InventoryButton button) {
		return button != null && BACKGROUND_BLUE.equals(button.background());
	}

	private static int slotAt(int x, int y) {
		for (int slot = 0; slot < POSITIONS.size(); slot++) {
			if (POSITIONS.get(slot).x() == x && POSITIONS.get(slot).y() == y) {
				return slot;
			}
		}
		return -1;
	}

	// Tile from which neighbours are blue
	private static int tile(int slot) {
		Position position = POSITIONS.get(slot);
		int mask = 0;
		for (int direction = 0; direction < NEIGHBOURS.length; direction++) {
			int neighbour = slotAt(position.x() + NEIGHBOURS[direction][0] * SIZE, position.y() + NEIGHBOURS[direction][1] * SIZE);
			if (neighbour >= 0 && blue(find(neighbour))) {
				mask |= 1 << direction;
			}
		}
		return CTM[mask];
	}

	static void extractBackground(GuiGraphicsExtractor graphics, ModConfig.InventoryButton button, int x, int y) {
		if (blue(button)) {
			int tile = tile(button.slot());
			graphics.blit(RenderPipelines.GUI_TEXTURED, BLUE_TEXTURE, x - (TILE - SIZE) / 2, y - (TILE - SIZE) / 2,
					tile % TILE_COLUMNS * TILE, tile / TILE_COLUMNS * TILE, TILE, TILE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
		}
	}

	static void extractBackgroundIcon(GuiGraphicsExtractor graphics, String background, int x, int y, int size) {
		if (BACKGROUND_BLUE.equals(background)) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, BLUE_TEXTURE, x, y, 0.0F, 0.0F, size, size, TILE, TILE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
		}
	}

	static void extractButton(GuiGraphicsExtractor graphics, ModConfig.InventoryButton button, int x, int y, boolean hovered, boolean framed) {
		if (blue(button)) {
			if (hovered) {
				graphics.fill(x, y, x + SIZE, y + SIZE, BLUE_HOVER_COLOR);
			}
		} else if (hovered || framed) {
			OverlayStyle.fillRounded(graphics, x, y, SIZE, SIZE, hovered ? NeuItems.HOVER_COLOR : NeuItems.BOX_COLOR);
		}
		ItemStack icon = icon(button.iconType(), button.icon());
		if (!icon.isEmpty()) {
			graphics.item(icon, x + 1, y + 1);
		}
	}

	static ItemStack icon(String type, String icon) {
		if (icon == null || icon.isBlank()) {
			return ItemStack.EMPTY;
		}
		return ICON_PLAYER.equals(type) ? head(icon) : NeuItems.stack(icon);
	}

	// Player head by name
	static ItemStack head(String name) {
		return HEADS.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> {
			ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
			stack.set(DataComponents.ITEM_MODEL, HEAD_MODEL);
			return stack;
		});
	}
}
