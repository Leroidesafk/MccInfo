package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class InventoryButtonsScreen extends Screen {
	// Inventory size
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 166;
	private static final int SIZE = InventoryButtons.SIZE;
	// Edit panel layout
	private static final int PAD = 5;
	private static final int CELL = 20;
	private static final int GRID_COLUMNS = 6;
	private static final int GRID_ROWS = 4;
	private static final int SCROLLBAR = 6;
	private static final int LABEL = 11;
	private static final int FIELD = 16;
	private static final int SPACING = 4;
	private static final int PANEL_WIDTH = PAD * 2 + GRID_COLUMNS * CELL + SCROLLBAR;
	private static final int INNER_WIDTH = PANEL_WIDTH - PAD * 2;
	private static final int COMMAND_Y = PAD + LABEL;
	private static final int BACKGROUND_Y = COMMAND_Y + FIELD + SPACING + LABEL;
	private static final int TYPE_Y = BACKGROUND_Y + SIZE + SPACING + LABEL;
	private static final int SELECTOR_Y = TYPE_Y + SIZE + SPACING + LABEL;
	private static final int GRID_Y = SELECTOR_Y + FIELD + SPACING;
	private static final int REMOVE_Y = GRID_Y + GRID_ROWS * CELL + SPACING;
	private static final int PANEL_HEIGHT = REMOVE_Y + NeuItems.BOX_HEIGHT + PAD;
	// Colors
	private static final int PANEL_COLOR = 0xF0181818;
	private static final int PANEL_BORDER = 0xFF5A5A5A;
	private static final int LABEL_COLOR = 0xFFA0A0A0;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int PLUS_COLOR = 0xFFB0B0B0;
	private static final int SELECTED_COLOR = 0xFFFFFFFF;
	private static final int EDITING_COLOR = 0xFF55FF55;
	private static final int SCROLL_TRACK = 0x80000000;
	private static final int SCROLL_THUMB = 0xFFA0A0A0;
	private static final int MAX_COMMAND = 256;
	private static final int MAX_NAME = 16;
	private static final int COLUMNS_GAP = 4;
	private static final int COLUMNS_VALUE_HEIGHT = 42;
	// Icons of the three icon types
	private static final ItemStack ITEM_TYPE_ICON = modelled(Items.PAPER, Identifier.parse("mcc:island_items/infinibag/material/gloop"));
	private static final ItemStack PLAYER_TYPE_ICON = modelled(Items.PLAYER_HEAD, InventoryButtons.HEAD_MODEL);
	private static final ItemStack UI_TYPE_ICON = modelled(Items.PAPER, Identifier.parse("mcc:island_interface/settings/icon"));
	private static final String[] TYPES = {InventoryButtons.ICON_ITEM, InventoryButtons.ICON_PLAYER, InventoryButtons.ICON_UI};
	private static final List<String> BACKGROUNDS = InventoryButtons.BACKGROUNDS;

	private final Screen parent;
	private int left;
	private int top;
	// Slot being edited, -1 when none
	private int editing = -1;
	private int panelX;
	private int panelY;
	private EditBox command;
	private EditBox selector;
	private String background = InventoryButtons.BACKGROUND_BLUE;
	private String iconType = InventoryButtons.ICON_ITEM;
	private String icon = "";
	private List<String> results = List.of();
	private int scroll;
	private boolean loading;

	private static ItemStack modelled(Item item, Identifier model) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_MODEL, model);
		return stack;
	}

	public InventoryButtonsScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.inventory_buttons.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.left = (this.width - IMAGE_WIDTH) / 2;
		this.top = (this.height - IMAGE_HEIGHT) / 2;
		this.command = new EditBox(this.font, 0, 0, INNER_WIDTH, FIELD, this.command, Component.translatable("screen.mccinfo.inventory_buttons.command"));
		this.command.setMaxLength(MAX_COMMAND);
		this.command.setResponder(this::commandChanged);
		this.selector = new EditBox(this.font, 0, 0, INNER_WIDTH, FIELD, this.selector, Component.translatable("screen.mccinfo.inventory_buttons.item_selector"));
		this.selector.setMaxLength(64);
		this.selector.setResponder(this::selectorChanged);
		this.addRenderableWidget(this.command);
		this.addRenderableWidget(this.selector);
		if (this.editing >= 0) {
			applyType();
			placePanel();
		} else {
			this.command.visible = false;
			this.selector.visible = false;
		}
	}

	private void commandChanged(String value) {
		if (!value.startsWith("/")) {
			this.command.setValue("/" + value);
			return;
		}
		commit();
	}

	private void selectorChanged(String value) {
		if (InventoryButtons.ICON_PLAYER.equals(this.iconType)) {
			String clean = value.replaceAll("[^A-Za-z0-9_]", "");
			if (clean.length() > MAX_NAME) {
				clean = clean.substring(0, MAX_NAME);
			}
			if (!clean.equals(value)) {
				this.selector.setValue(clean);
			}
			return;
		}
		this.scroll = 0;
		this.results = search(value);
	}

	// NEU items matching the search
	private List<String> search(String value) {
		return InventoryButtons.ICON_UI.equals(this.iconType) ? NeuItems.searchUiIds(value) : NeuItems.searchIds(value);
	}

	// Open the panel on a slot
	private void open(int slot) {
		if (this.editing >= 0) {
			closePanel();
		}
		this.editing = slot;
		ModConfig.InventoryButton button = InventoryButtons.find(slot);
		this.loading = true;
		this.background = button == null ? BACKGROUNDS.getFirst()
				: BACKGROUNDS.contains(button.background()) ? button.background() : InventoryButtons.BACKGROUND_NONE;
		this.iconType = button != null && List.of(TYPES).contains(button.iconType()) ? button.iconType() : InventoryButtons.ICON_ITEM;
		this.icon = button != null && button.icon() != null ? button.icon() : "";
		this.command.setValue(button != null ? button.command() : "/");
		applyType();
		this.selector.setValue(InventoryButtons.ICON_PLAYER.equals(this.iconType) ? this.icon : "");
		this.loading = false;
		this.results = search(this.selector.getValue());
		this.scroll = 0;
		placePanel();
		this.setFocused(this.command);
		this.command.setFocused(true);
		this.command.moveCursorToEnd(false);
	}

	private void applyType() {
		boolean player = InventoryButtons.ICON_PLAYER.equals(this.iconType);
		this.selector.setMaxLength(player ? MAX_NAME : 64);
		this.selector.setHint(Component.translatable(player ? "screen.mccinfo.inventory_buttons.player_hint" : "screen.mccinfo.neu_items.search")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	private void closePanel() {
		commit();
		ModConfig.get().save();
		this.editing = -1;
		this.command.visible = false;
		this.selector.visible = false;
		this.command.setFocused(false);
		this.selector.setFocused(false);
		this.setFocused(null);
	}

	// Save the edited button
	private void commit() {
		if (this.editing < 0 || this.loading) {
			return;
		}
		List<ModConfig.InventoryButton> buttons = ModConfig.get().inventoryButtons;
		buttons.removeIf(button -> button.slot() == this.editing);
		String value = this.command.getValue().strip();
		if (value.length() > 1 || !this.icon.isEmpty()) {
			buttons.add(new ModConfig.InventoryButton(this.editing, value, this.background, this.iconType, this.icon));
		}
	}

	// Panel next to the slot, kept on screen
	private void placePanel() {
		InventoryButtons.Position position = InventoryButtons.POSITIONS.get(this.editing);
		int x = this.left + position.x();
		int y = this.top + position.y();
		this.panelX = Math.clamp(x + SIZE / 2 - PANEL_WIDTH / 2, 2, Math.max(2, this.width - PANEL_WIDTH - 2));
		int below = y + SIZE + 2;
		int above = y - 2 - PANEL_HEIGHT;
		if (below + PANEL_HEIGHT <= this.height - 2) {
			this.panelY = below;
		} else if (above >= 2) {
			this.panelY = above;
		} else {
			this.panelY = Math.max(2, this.height - PANEL_HEIGHT - 2);
		}
		this.command.setX(this.panelX + PAD);
		this.command.setY(this.panelY + COMMAND_Y);
		this.selector.setX(this.panelX + PAD);
		this.selector.setY(this.panelY + SELECTOR_Y);
		this.command.visible = true;
		this.selector.visible = true;
	}

	// NEU list columns setting
	private int columnsX() {
		return NeuItems.widestLeft(this.width, this.left + IMAGE_WIDTH) - COLUMNS_GAP - NeuItems.BOX_HEIGHT;
	}

	private int columnsTop() {
		return (this.height - (NeuItems.BOX_HEIGHT * 2 + COLUMNS_VALUE_HEIGHT + NeuItems.GAP * 2)) / 2;
	}

	private int plusY() {
		return columnsTop();
	}

	private int valueY() {
		return plusY() + NeuItems.BOX_HEIGHT + NeuItems.GAP;
	}

	private int minusY() {
		return valueY() + COLUMNS_VALUE_HEIGHT + NeuItems.GAP;
	}

	private int neuColumns() {
		return NeuItems.columns(this.width, this.left + IMAGE_WIDTH);
	}

	private int neuMaxColumns() {
		return NeuItems.maxColumns(this.width, this.left + IMAGE_WIDTH);
	}

	private boolean minusHovered(double x, double y) {
		return NeuItems.previewAvailable() && in(x, y, columnsX(), minusY(), NeuItems.BOX_HEIGHT, NeuItems.BOX_HEIGHT);
	}

	private boolean plusHovered(double x, double y) {
		return NeuItems.previewAvailable() && in(x, y, columnsX(), plusY(), NeuItems.BOX_HEIGHT, NeuItems.BOX_HEIGHT);
	}

	private boolean columnsHovered(double x, double y) {
		return NeuItems.previewAvailable() && in(x, y, columnsX(), plusY(), NeuItems.BOX_HEIGHT, minusY() + NeuItems.BOX_HEIGHT - plusY());
	}

	private boolean columnsClick(double x, double y) {
		int current = neuColumns();
		int max = neuMaxColumns();
		ModConfig config = ModConfig.get();
		if (minusHovered(x, y) && current > 1) {
			config.neuColumns = current - 1;
		} else if (plusHovered(x, y) && current < max) {
			config.neuColumns = current + 1 >= max ? 0 : current + 1;
		} else {
			return false;
		}
		config.save();
		return true;
	}

	private void extractColumns(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!NeuItems.previewAvailable()) {
			return;
		}
		int x = columnsX();
		int right = x + NeuItems.BOX_HEIGHT;
		int current = neuColumns();
		int max = neuMaxColumns();
		boolean minus = minusHovered(mouseX, mouseY);
		boolean plus = plusHovered(mouseX, mouseY);
		NeuItems.extractBox(graphics, this.font, x, plusY(), right, plusY() + NeuItems.BOX_HEIGHT, Component.literal("+"), plus, current < max);
		NeuItems.extractBox(graphics, this.font, x, valueY(), right, valueY() + COLUMNS_VALUE_HEIGHT, Component.literal(String.valueOf(current)), false, true);
		NeuItems.extractBox(graphics, this.font, x, minusY(), right, minusY() + NeuItems.BOX_HEIGHT, Component.literal("-"), minus, current > 1);
		if ((minus && current > 1) || (plus && current < max)) {
			NeuItems.pointer(graphics);
		}
		if (columnsHovered(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("screen.mccinfo.inventory_buttons.neu_columns"), mouseX, mouseY);
		}
	}

	private boolean inPanel(double x, double y) {
		return this.editing >= 0 && x >= this.panelX && x < this.panelX + PANEL_WIDTH && y >= this.panelY && y < this.panelY + PANEL_HEIGHT;
	}

	private int slotAt(double x, double y) {
		for (int slot = 0; slot < InventoryButtons.POSITIONS.size(); slot++) {
			InventoryButtons.Position position = InventoryButtons.POSITIONS.get(slot);
			int bx = this.left + position.x();
			int by = this.top + position.y();
			if (x >= bx && x < bx + SIZE && y >= by && y < by + SIZE) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean in(double x, double y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	private int tileAt(double x, double y, int rowY, int count) {
		for (int index = 0; index < count; index++) {
			if (in(x, y, this.panelX + PAD + index * CELL, this.panelY + rowY, SIZE, SIZE)) {
				return index;
			}
		}
		return -1;
	}

	private int rows() {
		return (this.results.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
	}

	private int maxScroll() {
		return Math.max(0, rows() - GRID_ROWS);
	}

	private int resultAt(double x, double y) {
		int gridX = this.panelX + PAD;
		int gridY = this.panelY + GRID_Y;
		if (!in(x, y, gridX, gridY, GRID_COLUMNS * CELL, GRID_ROWS * CELL)) {
			return -1;
		}
		int column = (int) ((x - gridX) / CELL);
		int row = (int) ((y - gridY) / CELL);
		if (x - gridX - column * CELL >= SIZE || y - gridY - row * CELL >= SIZE) {
			return -1;
		}
		int index = (this.scroll + row) * GRID_COLUMNS + column;
		return index < this.results.size() ? index : -1;
	}

	private boolean playerTileHovered(double x, double y) {
		return !this.selector.getValue().isEmpty() && in(x, y, this.panelX + PAD, this.panelY + GRID_Y, SIZE, SIZE);
	}

	private boolean removeHovered(double x, double y) {
		return in(x, y, this.panelX + PAD, this.panelY + REMOVE_Y, INNER_WIDTH, NeuItems.BOX_HEIGHT);
	}

	// Clicks in the panel
	private boolean panelClick(double x, double y) {
		int background = tileAt(x, y, BACKGROUND_Y, BACKGROUNDS.size());
		if (background >= 0) {
			this.background = BACKGROUNDS.get(background);
			commit();
			return true;
		}
		int type = tileAt(x, y, TYPE_Y, TYPES.length);
		if (type >= 0) {
			if (!TYPES[type].equals(this.iconType)) {
				this.iconType = TYPES[type];
				this.icon = "";
				applyType();
				this.selector.setValue("");
				this.results = search("");
				commit();
			}
			return true;
		}
		if (InventoryButtons.ICON_PLAYER.equals(this.iconType)) {
			if (playerTileHovered(x, y)) {
				this.icon = this.selector.getValue();
				commit();
				return true;
			}
		} else {
			int result = resultAt(x, y);
			if (result >= 0) {
				this.icon = this.results.get(result);
				commit();
				return true;
			}
		}
		if (removeHovered(x, y)) {
			int slot = this.editing;
			this.editing = -1;
			ModConfig.get().inventoryButtons.removeIf(button -> button.slot() == slot);
			closePanel();
			return true;
		}
		return false;
	}

	// Click a slot to edit it
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();
		if (inPanel(x, y)) {
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && panelClick(x, y)) {
				NeuItems.clickSound();
				return true;
			}
			super.mouseClicked(event, doubleClick);
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && columnsClick(x, y)) {
			NeuItems.clickSound();
			return true;
		}
		int slot = slotAt(x, y);
		if (slot >= 0 && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (slot != this.editing) {
				open(slot);
			}
			NeuItems.clickSound();
			return true;
		}
		if (this.editing >= 0) {
			closePanel();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.editing >= 0 && !InventoryButtons.ICON_PLAYER.equals(this.iconType)
				&& in(x, y, this.panelX + PAD, this.panelY + GRID_Y, GRID_COLUMNS * CELL + SCROLLBAR, GRID_ROWS * CELL) && scrollY != 0) {
			this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.editing >= 0) {
			closePanel();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, AbstractContainerScreen.INVENTORY_LOCATION, this.left, this.top, 0.0F, 0.0F,
				IMAGE_WIDTH, IMAGE_HEIGHT, 256, 256);
		graphics.centeredText(this.font, this.title, this.width / 2, this.top - 36, TEXT_COLOR);
		boolean overPanel = inPanel(mouseX, mouseY);
		NeuItems.extractPreview(graphics, this.width, this.height, this.left + IMAGE_WIDTH, -1, -1);
		extractColumns(graphics, mouseX, mouseY);
		int hoveredSlot = overPanel ? -1 : slotAt(mouseX, mouseY);
		for (int slot = 0; slot < InventoryButtons.POSITIONS.size(); slot++) {
			InventoryButtons.Position position = InventoryButtons.POSITIONS.get(slot);
			int x = this.left + position.x();
			int y = this.top + position.y();
			ModConfig.InventoryButton button = InventoryButtons.find(slot);
			if (button == null) {
				OverlayStyle.fillRounded(graphics, x, y, SIZE, SIZE, slot == hoveredSlot ? NeuItems.HOVER_COLOR : NeuItems.BOX_COLOR);
				graphics.centeredText(this.font, Component.literal("+"), x + SIZE / 2, y + (SIZE - this.font.lineHeight) / 2 + 1, PLUS_COLOR);
			} else {
				InventoryButtons.extractBackground(graphics, button, x, y);
			}
		}
		for (int slot = 0; slot < InventoryButtons.POSITIONS.size(); slot++) {
			InventoryButtons.Position position = InventoryButtons.POSITIONS.get(slot);
			int x = this.left + position.x();
			int y = this.top + position.y();
			ModConfig.InventoryButton button = InventoryButtons.find(slot);
			if (button != null) {
				InventoryButtons.extractButton(graphics, button, x, y, slot == hoveredSlot, true);
			}
			if (slot == this.editing) {
				outline(graphics, x, y, SIZE, SIZE, EDITING_COLOR);
			}
		}
		if (hoveredSlot >= 0) {
			NeuItems.pointer(graphics);
			ModConfig.InventoryButton button = InventoryButtons.find(hoveredSlot);
			if (button != null && button.command().length() > 1) {
				graphics.setTooltipForNextFrame(this.font, Component.literal(button.command()), mouseX, mouseY);
			}
		}
		if (this.editing >= 0) {
			graphics.nextStratum();
			extractPanel(graphics, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	// Command, background, icon type and icon search
	private void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = this.panelX;
		int y = this.panelY;
		graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL_BORDER);
		graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, PANEL_COLOR);
		int inner = x + PAD;
		label(graphics, "command", inner, y + COMMAND_Y - LABEL + 1);
		label(graphics, "background", inner, y + BACKGROUND_Y - LABEL + 1);
		label(graphics, "icon_type", inner, y + TYPE_Y - LABEL + 1);
		boolean player = InventoryButtons.ICON_PLAYER.equals(this.iconType);
		label(graphics, player ? "player_selector" : InventoryButtons.ICON_UI.equals(this.iconType) ? "ui_selector" : "item_selector", inner, y + SELECTOR_Y - LABEL + 1);

		int hoveredBackground = tileAt(mouseX, mouseY, BACKGROUND_Y, BACKGROUNDS.size());
		for (int index = 0; index < BACKGROUNDS.size(); index++) {
			tile(graphics, inner + index * CELL, y + BACKGROUND_Y, ItemStack.EMPTY, index == hoveredBackground, false);
			InventoryButtons.extractBackgroundIcon(graphics, BACKGROUNDS.get(index), inner + index * CELL + 1, y + BACKGROUND_Y + 1, SIZE - 2);
			if (BACKGROUNDS.get(index).equals(this.background)) {
				outline(graphics, inner + index * CELL, y + BACKGROUND_Y, SIZE, SIZE, SELECTED_COLOR);
			}
		}
		if (hoveredBackground >= 0) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("screen.mccinfo.inventory_buttons.background." + BACKGROUNDS.get(hoveredBackground)),
					mouseX, mouseY);
		}
		int hoveredType = tileAt(mouseX, mouseY, TYPE_Y, TYPES.length);
		for (int index = 0; index < TYPES.length; index++) {
			ItemStack stack = switch (TYPES[index]) {
				case InventoryButtons.ICON_PLAYER -> PLAYER_TYPE_ICON;
				case InventoryButtons.ICON_UI -> UI_TYPE_ICON;
				default -> ITEM_TYPE_ICON;
			};
			tile(graphics, inner + index * CELL, y + TYPE_Y, stack, index == hoveredType, TYPES[index].equals(this.iconType));
		}
		if (hoveredType >= 0) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("screen.mccinfo.inventory_buttons.icon_type." + TYPES[hoveredType]),
					mouseX, mouseY);
		}
		boolean clickable = hoveredBackground >= 0 || hoveredType >= 0;

		int gridY = y + GRID_Y;
		if (player) {
			String name = this.selector.getValue();
			if (!name.isEmpty()) {
				boolean hovered = playerTileHovered(mouseX, mouseY);
				tile(graphics, inner, gridY, InventoryButtons.head(name), hovered, name.equalsIgnoreCase(this.icon));
				graphics.text(this.font, Component.literal(name), inner + CELL + 2, gridY + (SIZE - this.font.lineHeight) / 2 + 1, TEXT_COLOR, false);
				clickable |= hovered;
			}
		} else {
			int hovered = resultAt(mouseX, mouseY);
			int first = this.scroll * GRID_COLUMNS;
			for (int cell = 0; cell < GRID_COLUMNS * GRID_ROWS; cell++) {
				int index = first + cell;
				int cellX = inner + cell % GRID_COLUMNS * CELL;
				int cellY = gridY + cell / GRID_COLUMNS * CELL;
				if (index < this.results.size()) {
					String id = this.results.get(index);
					tile(graphics, cellX, cellY, NeuItems.stack(id), index == hovered, id.equals(this.icon));
				} else {
					OverlayStyle.fillRounded(graphics, cellX, cellY, SIZE, SIZE, NeuItems.BOX_COLOR);
				}
			}
			int trackX = inner + GRID_COLUMNS * CELL;
			int trackHeight = GRID_ROWS * CELL - (CELL - SIZE);
			graphics.fill(trackX, gridY, trackX + SCROLLBAR - 2, gridY + trackHeight, SCROLL_TRACK);
			int total = Math.max(rows(), GRID_ROWS);
			int thumb = Math.max(4, trackHeight * GRID_ROWS / total);
			int thumbY = maxScroll() == 0 ? 0 : (trackHeight - thumb) * this.scroll / maxScroll();
			graphics.fill(trackX, gridY + thumbY, trackX + SCROLLBAR - 2, gridY + thumbY + thumb, SCROLL_THUMB);
			if (hovered >= 0) {
				ItemStack stack = NeuItems.stack(this.results.get(hovered));
				if (!stack.isEmpty()) {
					graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
				}
				clickable = true;
			}
		}
		boolean remove = removeHovered(mouseX, mouseY);
		NeuItems.extractBox(graphics, this.font, inner, y + REMOVE_Y, inner + INNER_WIDTH, y + REMOVE_Y + NeuItems.BOX_HEIGHT,
				Component.translatable("screen.mccinfo.inventory_buttons.remove").withStyle(ChatFormatting.RED), remove, true);
		if (clickable || remove) {
			NeuItems.pointer(graphics);
		}
	}

	private void label(GuiGraphicsExtractor graphics, String key, int x, int y) {
		graphics.text(this.font, Component.translatable("screen.mccinfo.inventory_buttons." + key), x, y, LABEL_COLOR, false);
	}

	private static void tile(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack, boolean hovered, boolean selected) {
		OverlayStyle.fillRounded(graphics, x, y, SIZE, SIZE, hovered ? NeuItems.HOVER_COLOR : NeuItems.BOX_COLOR);
		if (!stack.isEmpty()) {
			graphics.item(stack, x + 1, y + 1);
		}
		if (selected) {
			outline(graphics, x, y, SIZE, SIZE, SELECTED_COLOR);
		}
	}

	private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	@Override
	public void onClose() {
		if (this.editing >= 0) {
			closePanel();
		}
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
