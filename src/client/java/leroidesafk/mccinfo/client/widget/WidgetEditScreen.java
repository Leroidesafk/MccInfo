package leroidesafk.mccinfo.client.widget;

import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.emote.EmotePreviewRenderer;
import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.MenuTooltip;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Wheel editor: owned items on the left, the wheel on the right
public final class WidgetEditScreen extends Screen implements MenuBackdrop.Overlay {
	// Layout
	private static final int MARGIN = 24;
	private static final int BORDER = 3;
	private static final int DIVIDER = 3;
	private static final int TOP_BAR = 30;
	private static final int CONTENT_PADDING = 10;
	private static final int COLUMNS = 4;
	private static final int ITEM = 90;
	private static final int ITEM_GAP = 10;
	private static final int NAME_HEIGHT = 9;
	private static final int LIST_PANEL = ITEM * COLUMNS + ITEM_GAP * (COLUMNS - 1) + CONTENT_PADDING * 2;
	private static final int SECTION_HEIGHT = 22;
	private static final int SECTION_LABEL_GAP = 7;
	private static final int WHEEL_CELL = 62;
	private static final int WHEEL_GAP = 7;
	private static final int WHEEL_GRID = WHEEL_CELL * 3 + WHEEL_GAP * 2;
	private static final int WHEEL_PANEL = 310;
	private static final int MAX_WIDTH = BORDER * 2 + LIST_PANEL + DIVIDER + WHEEL_PANEL;
	private static final int MAX_HEIGHT = 408;
	private static final int BUTTON = 18;
	private static final int BUTTON_GAP = 4;
	private static final int HEADER_PADDING = 8;
	private static final int HINT_GAP = 12;
	private static final int HINT_LINE = 10;
	private static final int SEARCH_WIDTH = 150;
	private static final int SEARCH_HEIGHT = 17;
	private static final int SEARCH_ICON = 15;
	private static final Identifier ICONS = Mccinfo.id("textures/gui/ui/icons.png");
	private static final int ICONS_SIZE = 40;
	private static final int ICON = 8;
	private static final int ICON_TEXTURE = 16;
	private static final float ICON_FILL = 0.6F;
	private static final int DRAG_THRESHOLD = 3;
	private static final int MAX_WHEELS = 10;
	private static final int SCROLLBAR_WIDTH = DIVIDER;
	private static final int SCROLLBAR_GRAB = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final int REDEEM_ICON = 18;
	private static final int REDEEM_PADDING = 6;
	private static final int REDEEM_ICON_GAP = 4;
	private static final int NO_MOUSE = -10_000;
	// Redeem code dialog
	private static final int DIALOG_WIDTH = 224;
	private static final int DIALOG_BORDER = 1;
	private static final int DIALOG_TITLE_Y = 14;
	private static final int DIALOG_INPUT_Y = 34;
	private static final int DIALOG_INPUT_WIDTH = 140;
	private static final int DIALOG_INPUT_HEIGHT = 17;
	private static final int DIALOG_SECTION_GAP = 12;
	private static final int DIALOG_STATUS_HEIGHT = 15;
	private static final int DIALOG_BUTTON_WIDTH = 92;
	private static final int DIALOG_BUTTON_HEIGHT = 19;
	private static final int DIALOG_BUTTON_GAP = 6;
	private static final int DIALOG_BUTTON_BOTTOM = 14;
	private static final int DIALOG_BASE_HEIGHT = DIALOG_INPUT_Y + DIALOG_INPUT_HEIGHT + DIALOG_SECTION_GAP + DIALOG_BUTTON_HEIGHT + DIALOG_BUTTON_BOTTOM;
	// Colors
	private static final int COLOR_DIM = 0xB0000000;
	private static final int COLOR_DIALOG_BORDER = 0xFF3A3A3A;
	private static final int COLOR_FIELD = 0xFF101010;
	private static final int COLOR_FIELD_BORDER = 0xFF5A5A5A;
	private static final int COLOR_SUCCESS = 0xFF55FF55;
	private static final int COLOR_WARNING = 0xFFFFAA00;
	private static final int COLOR_ERROR = 0xFFFF5555;

	private static final int COLOR_OUTLINE = 0xFF262626;
	private static final int COLOR_OUTLINE_HEADER = 0xFF383838;
	private static final int COLOR_HEADER = 0xFF232323;
	private static final int COLOR_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_CARD = 0xFF202020;
	private static final int COLOR_CARD_BORDER = 0xFF2E2E2E;
	private static final int COLOR_PLACED_BORDER = 0xFF8C8C8C;
	private static final int COLOR_CONTROL = 0xFF2A2A2A;
	private static final int COLOR_CONTROL_HOVER = 0xFF3A3A3A;
	private static final int COLOR_INPUT = 0xFF2C2C2C;
	private static final int COLOR_INPUT_BORDER = 0xFF161616;
	private static final int COLOR_SLOT_EMPTY = 0xFF161616;
	private static final int COLOR_SECTION_LINE = 0xFF3A3A3A;
	private static final int COLOR_DRAG = 0xC0202020;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_MUTED = 0xFF8C8C8C;
	private static final int COLOR_DISABLED = 0xFF4A4A4A;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;
	private static final float HOVER_TINT = 0.18F;
	private static final float UNUSABLE_TINT = 0.5F;

	// List sections
	private enum Category {
		EMOTES("emotes"),
		WIDGETS("widgets"),
		TAGS("tags");

		private final String key;

		Category(String key) {
			this.key = key;
		}

		Component title() {
			return Component.translatable("screen.mccinfo.widgets.edit.category." + this.key);
		}

		static Category of(Widgets.Definition definition) {
			return switch (definition.type()) {
				case "emote" -> EMOTES;
				case "tag" -> TAGS;
				default -> WIDGETS;
			};
		}
	}

	private record Row(Category section, List<Widgets.Definition> items) {
		int height(int item) {
			return this.section != null ? SECTION_HEIGHT : item + NAME_HEIGHT + ITEM_GAP;
		}
	}

	private record Placed(Widgets.Definition definition, int x, int y) {
	}

	private static String query = "";

	private final Screen parent;
	private final long opened = System.nanoTime();
	private final List<List<String>> wheels;
	private final List<Placed> placed = new ArrayList<>();
	private EditBox search;
	private int page;
	private float scale = 1.0F;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private int bodyTop;
	private int listRight;
	private int wheelLeft;
	private float scroll;
	private float scrollTarget;
	private long lastFrame;
	private int maxScroll;
	private boolean draggingScrollbar;
	private double dragOffset;
	// Item pressed, dragged once moved far enough
	private String pressId;
	private int pressFrom = -1;
	private double pressX;
	private double pressY;
	private boolean dragging;
	// Redeem dialog state
	private boolean redeemOpen;
	private boolean redeemWaiting;
	private EditBox codeBox;
	private Component redeemStatus;
	private int redeemStatusColor = COLOR_MUTED;

	public WidgetEditScreen(Screen parent, int page) {
		super(Component.translatable("screen.mccinfo.widgets.edit.title"));
		this.parent = parent;
		this.wheels = Widgets.wheels();
		this.page = Math.clamp(page, 0, this.wheels.size() - 1);
	}

	@Override
	public Screen backdropParent() {
		return this.parent;
	}

	@Override
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		int width = Math.min(MAX_WIDTH, virtualWidth - MARGIN * 2);
		int height = Math.min(MAX_HEIGHT, virtualHeight - MARGIN * 2);
		this.left = (virtualWidth - width) / 2;
		this.top = (virtualHeight - height) / 2;
		this.right = this.left + width;
		this.bottom = this.top + height;
		this.bodyTop = this.top + TOP_BAR;
		this.wheelLeft = this.right - BORDER - Math.min(WHEEL_PANEL, width * WHEEL_PANEL / MAX_WIDTH);
		this.listRight = this.wheelLeft - DIVIDER;
		int searchX = this.left + BORDER + CONTENT_PADDING;
		int searchY = searchY();
		int searchWidth = Math.min(SEARCH_WIDTH, this.listRight - CONTENT_PADDING - searchX);
		this.search = new EditBox(this.font, searchX + SEARCH_ICON, searchY + (SEARCH_HEIGHT - 8) / 2, searchWidth - SEARCH_ICON - 4, 9,
				Component.translatable("screen.mccinfo.settings.search"));
		this.search.setBordered(false);
		this.search.setTextColor(COLOR_WHITE);
		this.search.setMaxLength(32);
		this.search.setHint(Component.translatable("screen.mccinfo.settings.search").withColor(COLOR_MUTED));
		this.search.setValue(query);
		this.search.setResponder(value -> {
			query = value;
			this.scroll = 0.0F;
			this.scrollTarget = 0.0F;
		});
		String code = this.codeBox != null ? this.codeBox.getValue() : "";
		this.codeBox = new EditBox(this.font, dialogInputX() + 5, dialogTop() + DIALOG_INPUT_Y + (DIALOG_INPUT_HEIGHT - 8) / 2,
				DIALOG_INPUT_WIDTH - 10, 9, Component.translatable("screen.mccinfo.widgets.redeem.enter"));
		this.codeBox.setBordered(false);
		this.codeBox.setTextColor(COLOR_WHITE);
		this.codeBox.setMaxLength(RedeemCodes.MAX_LENGTH);
		this.codeBox.setResponder(value -> {
			String cleaned = RedeemCodes.clean(value);
			if (!cleaned.equals(value)) {
				this.codeBox.setValue(cleaned);
			}
		});
		this.codeBox.setHint(Component.translatable("screen.mccinfo.widgets.redeem.enter").withColor(COLOR_MUTED));
		this.codeBox.setValue(code);
		if (this.redeemOpen) {
			this.setFocused(this.codeBox);
		}
	}

	private int dialogLeft() {
		return (int) (this.width / this.scale - DIALOG_WIDTH) / 2;
	}

	private int dialogTop() {
		return (int) (this.height / this.scale - DIALOG_BASE_HEIGHT) / 2;
	}

	private int dialogHeight() {
		return DIALOG_BASE_HEIGHT + (this.redeemStatus != null ? DIALOG_STATUS_HEIGHT : 0);
	}

	private int dialogInputX() {
		return dialogLeft() + (DIALOG_WIDTH - DIALOG_INPUT_WIDTH) / 2;
	}

	private int cancelX() {
		return dialogLeft() + (DIALOG_WIDTH - DIALOG_BUTTON_WIDTH * 2 - DIALOG_BUTTON_GAP) / 2;
	}

	private int confirmX() {
		return cancelX() + DIALOG_BUTTON_WIDTH + DIALOG_BUTTON_GAP;
	}

	private int dialogButtonY() {
		return dialogTop() + dialogHeight() - DIALOG_BUTTON_BOTTOM - DIALOG_BUTTON_HEIGHT;
	}

	// Open, close and send the redeem code
	private void openRedeem() {
		this.redeemOpen = true;
		this.redeemWaiting = false;
		this.redeemStatus = null;
		this.codeBox.setValue("");
		this.setFocused(this.codeBox);
	}

	private void closeRedeem() {
		this.redeemOpen = false;
		this.redeemWaiting = false;
		this.setFocused(null);
	}

	private void submitRedeem() {
		String code = RedeemCodes.clean(this.codeBox.getValue());
		if (code.isEmpty() || this.redeemWaiting) {
			return;
		}
		boolean sent = RedeemCodes.send(code, result -> {
			if (this.minecraft.gui.screen() != this || !this.redeemOpen) {
				return;
			}
			this.redeemWaiting = false;
			showResult(result);
		});
		if (!sent) {
			status(Component.translatable("screen.mccinfo.widgets.redeem.offline"), COLOR_ERROR);
			return;
		}
		this.redeemWaiting = true;
		status(Component.translatable("screen.mccinfo.widgets.redeem.checking"), COLOR_MUTED);
	}

	// Widgets added or the error reason
	private void showResult(RedeemCodes.Result result) {
		if (result.ok()) {
			this.codeBox.setValue("");
			boolean added = "redeemed".equals(result.reason());
			status(Component.translatable(added ? "screen.mccinfo.widgets.redeem.redeemed" : "screen.mccinfo.widgets.redeem.owned",
					String.join(", ", result.rewards())), added ? COLOR_SUCCESS : COLOR_WARNING);
			return;
		}
		String reason = switch (result.reason()) {
			case "expired", "used", "used_up", "too_many", "not_allowed" -> result.reason();
			case "invalid" -> "invalid";
			default -> "error";
		};
		status(Component.translatable("screen.mccinfo.widgets.redeem." + reason), COLOR_ERROR);
	}

	private void status(Component text, int color) {
		this.redeemStatus = text;
		this.redeemStatusColor = color;
	}

	private int searchY() {
		return this.top + (TOP_BAR - SEARCH_HEIGHT) / 2;
	}

	private boolean overSearch(double x, double y) {
		int searchX = this.search.getX() - SEARCH_ICON;
		return inside(x, y, searchX, searchY(), this.search.getWidth() + SEARCH_ICON + 4, SEARCH_HEIGHT);
	}

	private List<String> wheel() {
		return this.wheels.get(this.page);
	}

	private static boolean usable(String id) {
		return Widgets.widget(id) != null;
	}

	private int listLeft() {
		return this.left + BORDER + CONTENT_PADDING;
	}

	private int listWidth() {
		return this.listRight - CONTENT_PADDING - listLeft();
	}

	private int itemSize() {
		return Math.min(ITEM, (listWidth() - ITEM_GAP * (COLUMNS - 1)) / COLUMNS);
	}

	private int listTop() {
		return this.bodyTop + CONTENT_PADDING / 2;
	}

	private int listBottom() {
		return this.bottom - BORDER - CONTENT_PADDING / 2;
	}

	// Owned items by section, filtered by the search
	private List<Row> rows() {
		String filter = query.strip().toLowerCase(Locale.ROOT);
		List<Row> rows = new ArrayList<>();
		for (Category category : Category.values()) {
			List<Widgets.Definition> items = new ArrayList<>();
			for (Widgets.Definition definition : Widgets.owned()) {
				if (Category.of(definition) != category || items.stream().anyMatch(other -> other.id().equals(definition.id()))) {
					continue;
				}
				if (filter.isEmpty() || Widgets.name(definition.id()).getString().toLowerCase(Locale.ROOT).contains(filter)) {
					items.add(definition);
				}
			}
			if (items.isEmpty()) {
				continue;
			}
			rows.add(new Row(category, List.of()));
			for (int i = 0; i < items.size(); i += COLUMNS) {
				rows.add(new Row(null, items.subList(i, Math.min(items.size(), i + COLUMNS))));
			}
		}
		return rows;
	}

	private int contentHeight(List<Row> rows) {
		int item = itemSize();
		int height = 0;
		for (Row row : rows) {
			height += row.height(item);
		}
		return height;
	}

	private int viewTop() {
		return this.bodyTop;
	}

	private int viewBottom() {
		return this.bottom - BORDER;
	}

	private boolean inList(double x, double y) {
		return inside(x, y, this.left + BORDER, viewTop(), this.listRight - this.left - BORDER, viewBottom() - viewTop());
	}

	// Smooth scroll and scrollbar
	private void updateScroll() {
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		if (this.draggingScrollbar || Math.abs(this.scrollTarget - this.scroll) < 0.5F) {
			this.scroll = this.scrollTarget;
		} else {
			this.scroll += (this.scrollTarget - this.scroll) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
	}

	private int trackHeight() {
		return viewBottom() - viewTop();
	}

	private int thumbHeight() {
		int trackHeight = trackHeight();
		return Math.max(MIN_THUMB, trackHeight * trackHeight / (trackHeight + this.maxScroll));
	}

	private int thumbY() {
		return viewTop() + Math.round((trackHeight() - thumbHeight()) * this.scroll / this.maxScroll);
	}

	private void dragScrollbar(double y) {
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			this.scrollTarget = Mth.clamp((float) ((y - this.dragOffset - viewTop()) * this.maxScroll / range), 0.0F, this.maxScroll);
			this.scroll = this.scrollTarget;
		}
	}

	private Component redeemLabel() {
		return Component.translatable("screen.mccinfo.widgets.edit.redeem");
	}

	private int redeemWidth() {
		return REDEEM_PADDING * 2 + this.font.width(redeemLabel()) + REDEEM_ICON_GAP + ICON;
	}

	private int redeemX() {
		return this.listRight - CONTENT_PADDING - redeemWidth();
	}

	private int wheelWidth() {
		return this.right - BORDER - this.wheelLeft;
	}

	private int wheelGridLeft() {
		return this.wheelLeft + (wheelWidth() - WHEEL_GRID) / 2;
	}

	private List<FormattedCharSequence> hint() {
		return this.font.split(Component.translatable("screen.mccinfo.widgets.edit.hint"), wheelWidth() - CONTENT_PADDING * 2);
	}

	private int wheelGridTop() {
		int height = WHEEL_GRID + HINT_GAP + hint().size() * HINT_LINE;
		return this.bodyTop + (this.bottom - BORDER - this.bodyTop - height) / 2;
	}

	private int slotX(int slot) {
		int position = slot < 4 ? slot : slot + 1;
		return wheelGridLeft() + position % 3 * (WHEEL_CELL + WHEEL_GAP);
	}

	private int slotY(int slot) {
		int position = slot < 4 ? slot : slot + 1;
		return wheelGridTop() + position / 3 * (WHEEL_CELL + WHEEL_GAP);
	}

	private int slotAt(double x, double y) {
		for (int slot = 0; slot < Widgets.WHEEL_SLOTS; slot++) {
			if (inside(x, y, slotX(slot), slotY(slot), WHEEL_CELL, WHEEL_CELL)) {
				return slot;
			}
		}
		return -1;
	}

	private Placed placedAt(double x, double y) {
		int item = itemSize();
		for (Placed placed : this.placed) {
			if (inList(x, y) && inside(x, y, placed.x(), placed.y(), item, item)) {
				return placed;
			}
		}
		return null;
	}

	private int addX() {
		return this.wheelLeft + HEADER_PADDING;
	}

	private int deleteX() {
		return addX() + BUTTON + BUTTON_GAP;
	}

	private int previousX() {
		return deleteX() + BUTTON + BUTTON_GAP * 3;
	}

	private int nextX() {
		return this.right - BORDER - HEADER_PADDING - BUTTON;
	}

	private int buttonY() {
		return this.top + (TOP_BAR - BUTTON) / 2;
	}

	private boolean canAdd() {
		return this.wheels.size() < MAX_WHEELS;
	}

	private boolean canDelete() {
		return this.wheels.size() > 1;
	}

	private static boolean inside(double x, double y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!MenuBackdrop.extract(this.parent, graphics, this.width, this.height, a)) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int realX = (int) (mouseX / this.scale);
		int realY = (int) (mouseY / this.scale);
		int virtualX = this.redeemOpen ? NO_MOUSE : realX;
		int virtualY = this.redeemOpen ? NO_MOUSE : realY;
		List<Row> rows = rows();
		updateScroll();
		this.maxScroll = Math.max(0, contentHeight(rows) + CONTENT_PADDING - (listBottom() - listTop()));
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll);
		this.scroll = Mth.clamp(this.scroll, 0.0F, this.maxScroll);
		Component tooltip = null;

		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		graphics.fill(this.left, this.top, this.right, this.bottom, COLOR_OUTLINE);
		graphics.fill(this.left, this.top, this.right, this.bodyTop, COLOR_OUTLINE_HEADER);
		graphics.fill(this.left + BORDER, this.top, this.listRight, this.bodyTop, COLOR_HEADER);
		graphics.fill(this.wheelLeft, this.top, this.right - BORDER, this.bodyTop, COLOR_HEADER);
		graphics.fill(this.left + BORDER, this.bodyTop, this.listRight, this.bottom - BORDER, COLOR_BACKGROUND);
		graphics.fill(this.wheelLeft, this.bodyTop, this.right - BORDER, this.bottom - BORDER, COLOR_BACKGROUND);

		int searchX = this.search.getX() - SEARCH_ICON;
		int searchWidth = this.search.getWidth() + SEARCH_ICON + 4;
		graphics.fill(searchX, searchY(), searchX + searchWidth, searchY() + SEARCH_HEIGHT, COLOR_INPUT_BORDER);
		graphics.fill(searchX, searchY(), searchX + searchWidth - 1, searchY() + SEARCH_HEIGHT - 1, COLOR_INPUT);
		searchIcon(graphics, searchX + 4, searchY() + (SEARCH_HEIGHT - ICON) / 2);
		this.search.extractRenderState(graphics, virtualX, virtualY, a);
		boolean overRedeem = this.pressId == null && inside(virtualX, virtualY, redeemX(), searchY(), redeemWidth(), SEARCH_HEIGHT);
		graphics.fill(redeemX(), searchY(), redeemX() + redeemWidth(), searchY() + SEARCH_HEIGHT, COLOR_INPUT_BORDER);
		graphics.fill(redeemX(), searchY(), redeemX() + redeemWidth() - 1, searchY() + SEARCH_HEIGHT - 1, overRedeem ? COLOR_CONTROL_HOVER : COLOR_INPUT);
		int redeemTextY = searchY() + (SEARCH_HEIGHT - this.font.lineHeight) / 2 + 1;
		int redeemColor = overRedeem ? COLOR_WHITE : COLOR_TEXT;
		graphics.text(this.font, redeemLabel(), redeemX() + REDEEM_PADDING, redeemTextY, redeemColor, true);
		icon(graphics, REDEEM_ICON, redeemX() + redeemWidth() - REDEEM_PADDING - ICON, searchY() + (SEARCH_HEIGHT - ICON) / 2, redeemColor);
		if (overRedeem) {
			NeuItems.pointer(graphics);
		}

		boolean pickable = this.pressId == null && !this.draggingScrollbar && inList(virtualX, virtualY);
		int item = itemSize();
		int x0 = listLeft();
		int width = listWidth();
		int y = listTop() - Math.round(this.scroll);
		this.placed.clear();
		graphics.enableScissor(this.left + BORDER, viewTop(), this.listRight, viewBottom());
		for (Row row : rows) {
			if (y >= viewBottom()) {
				break;
			}
			if (y + row.height(item) <= viewTop()) {
				y += row.height(item);
				continue;
			}
			if (row.section() != null) {
				section(graphics, row.section().title(), x0, width, y);
			} else {
				for (int column = 0; column < row.items().size(); column++) {
					Widgets.Definition definition = row.items().get(column);
					int x = x0 + column * (item + ITEM_GAP);
					this.placed.add(new Placed(definition, x, y));
					boolean over = pickable && inside(virtualX, virtualY, x, y, item, item);
					Component text = extractItem(graphics, definition, x, y, item, over);
					if (text != null) {
						tooltip = text;
					}
				}
			}
			y += row.height(item);
		}
		graphics.disableScissor();
		if (rows.isEmpty()) {
			Component empty = Component.translatable(query.isBlank() ? "screen.mccinfo.widgets.edit.empty" : "screen.mccinfo.widgets.edit.no_results");
			graphics.centeredText(this.font, empty, x0 + width / 2, listTop() + CONTENT_PADDING, COLOR_MUTED);
		}
		if (this.maxScroll > 0) {
			int thumbY = thumbY();
			graphics.fill(this.listRight, thumbY, this.listRight + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR);
		}

		Component header = extractWheelHeader(graphics, virtualX, virtualY);
		if (header != null) {
			tooltip = header;
		}
		int hoveredSlot = slotAt(virtualX, virtualY);
		for (int slot = 0; slot < Widgets.WHEEL_SLOTS; slot++) {
			int x = slotX(slot);
			int slotY = slotY(slot);
			String id = wheel().get(slot);
			boolean filled = usable(id);
			boolean over = slot == hoveredSlot && (this.dragging || filled && pickable);
			if (over) {
				graphics.fill(x - 1, slotY - 1, x + WHEEL_CELL + 1, slotY + WHEEL_CELL + 1, SettingsPanel.ACCENT);
			}
			graphics.fill(x, slotY, x + WHEEL_CELL, slotY + WHEEL_CELL, over ? hoverColor() : filled ? COLOR_CARD : COLOR_SLOT_EMPTY);
			if (filled && !(this.dragging && this.pressFrom == slot)) {
				extractContent(graphics, id, x, slotY, WHEEL_CELL);
				if (over && !this.dragging) {
					tooltip = Widgets.name(id);
					NeuItems.pointer(graphics);
				}
			}
		}
		int hintY = wheelGridTop() + WHEEL_GRID + HINT_GAP;
		for (FormattedCharSequence line : hint()) {
			graphics.text(this.font, line, this.wheelLeft + (wheelWidth() - this.font.width(line)) / 2, hintY, COLOR_MUTED, true);
			hintY += HINT_LINE;
		}

		if (this.dragging && this.pressId != null) {
			graphics.nextStratum();
			int x = virtualX - WHEEL_CELL / 2;
			int dragY = virtualY - WHEEL_CELL / 2;
			graphics.fill(x, dragY, x + WHEEL_CELL, dragY + WHEEL_CELL, COLOR_DRAG);
			extractContent(graphics, this.pressId, x, dragY, WHEEL_CELL);
		} else if (tooltip != null) {
			MenuTooltip.extract(graphics, tooltip, virtualX, virtualY, this.scale);
		}
		if (this.redeemOpen) {
			extractRedeem(graphics, realX, realY, a);
		}
		graphics.pose().popMatrix();
	}

	// Redeem dialog over the editor
	private void extractRedeem(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.nextStratum();
		graphics.fill(0, 0, (int) (this.width / this.scale) + 1, (int) (this.height / this.scale) + 1, COLOR_DIM);
		int x = dialogLeft();
		int y = dialogTop();
		int height = dialogHeight();
		graphics.fill(x, y, x + DIALOG_WIDTH, y + height, COLOR_DIALOG_BORDER);
		graphics.fill(x + DIALOG_BORDER, y + DIALOG_BORDER, x + DIALOG_WIDTH - DIALOG_BORDER, y + height - DIALOG_BORDER, COLOR_BACKGROUND);
		graphics.centeredText(this.font, Component.translatable("screen.mccinfo.widgets.redeem.title"), x + DIALOG_WIDTH / 2, y + DIALOG_TITLE_Y, COLOR_WHITE);
		int inputX = dialogInputX();
		int inputY = y + DIALOG_INPUT_Y;
		boolean filled = !this.codeBox.getValue().isEmpty();
		graphics.fill(inputX, inputY, inputX + DIALOG_INPUT_WIDTH, inputY + DIALOG_INPUT_HEIGHT, filled ? SettingsPanel.ACCENT : COLOR_FIELD_BORDER);
		graphics.fill(inputX + 1, inputY + 1, inputX + DIALOG_INPUT_WIDTH - 1, inputY + DIALOG_INPUT_HEIGHT - 1, COLOR_FIELD);
		this.codeBox.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.redeemStatus != null) {
			String text = this.font.plainSubstrByWidth(this.redeemStatus.getString(), DIALOG_WIDTH - CONTENT_PADDING * 2);
			int statusY = inputY + DIALOG_INPUT_HEIGHT + (DIALOG_SECTION_GAP + DIALOG_STATUS_HEIGHT - this.font.lineHeight) / 2 + 1;
			graphics.centeredText(this.font, Component.literal(text), x + DIALOG_WIDTH / 2, statusY, this.redeemStatusColor);
		}
		dialogButton(graphics, Component.translatable("screen.mccinfo.widgets.redeem.cancel"), cancelX(), false, true, mouseX, mouseY);
		boolean canRedeem = !this.redeemWaiting && !RedeemCodes.clean(this.codeBox.getValue()).isEmpty();
		dialogButton(graphics, Component.translatable("screen.mccinfo.widgets.redeem.confirm"), confirmX(), true, canRedeem, mouseX, mouseY);
	}

	private void dialogButton(GuiGraphicsExtractor graphics, Component label, int x, boolean accent, boolean active, int mouseX, int mouseY) {
		int y = dialogButtonY();
		boolean over = active && inside(mouseX, mouseY, x, y, DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT);
		int border = accent && active ? SettingsPanel.ACCENT : COLOR_FIELD_BORDER;
		int fill = accent && active ? SettingsPanel.ACCENT_DARK : COLOR_CONTROL;
		graphics.fill(x, y, x + DIALOG_BUTTON_WIDTH, y + DIALOG_BUTTON_HEIGHT, border);
		graphics.fill(x + 1, y + 1, x + DIALOG_BUTTON_WIDTH - 1, y + DIALOG_BUTTON_HEIGHT - 1, over ? mix(fill, COLOR_WHITE, 0.12F) : fill);
		graphics.centeredText(this.font, label, x + DIALOG_BUTTON_WIDTH / 2, y + (DIALOG_BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1,
				active ? COLOR_WHITE : COLOR_DISABLED);
		if (over) {
			NeuItems.pointer(graphics);
		}
	}

	private static int mix(int from, int to, float t) {
		int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
		int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return from & 0xFF000000 | r << 16 | g << 8 | b;
	}

	private boolean redeemClicked(MouseButtonEvent event, double x, double y, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return true;
		}
		int inputY = dialogTop() + DIALOG_INPUT_Y;
		if (inside(x, y, dialogInputX(), inputY, DIALOG_INPUT_WIDTH, DIALOG_INPUT_HEIGHT)) {
			this.setFocused(this.codeBox);
			this.codeBox.onClick(new MouseButtonEvent(x, y, event.buttonInfo()), doubleClick);
			return true;
		}
		if (inside(x, y, cancelX(), dialogButtonY(), DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT)) {
			NeuItems.clickSound();
			closeRedeem();
			return true;
		}
		if (inside(x, y, confirmX(), dialogButtonY(), DIALOG_BUTTON_WIDTH, DIALOG_BUTTON_HEIGHT)) {
			if (!this.redeemWaiting && !RedeemCodes.clean(this.codeBox.getValue()).isEmpty()) {
				NeuItems.clickSound();
				submitRedeem();
			}
			return true;
		}
		if (!inside(x, y, dialogLeft(), dialogTop(), DIALOG_WIDTH, dialogHeight())) {
			closeRedeem();
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.redeemOpen) {
			if (event.isEscape()) {
				closeRedeem();
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				submitRedeem();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private Component extractItem(GuiGraphicsExtractor graphics, Widgets.Definition definition, int x, int y, int item, boolean over) {
		boolean inWheel = wheel().contains(definition.id());
		boolean canUse = usable(definition.id());
		boolean hovered = over && canUse;
		graphics.fill(x, y, x + item, y + item, hovered ? SettingsPanel.ACCENT : inWheel ? COLOR_PLACED_BORDER : COLOR_CARD_BORDER);
		graphics.fill(x + 1, y + 1, x + item - 1, y + item - 1, hovered ? hoverColor() : COLOR_CARD);
		extractContent(graphics, definition.id(), x, y, item, listClip());
		if (!canUse) {
			graphics.fill(x + 1, y + 1, x + item - 1, y + item - 1, Math.round(0xFF * UNUSABLE_TINT) << 24 | COLOR_BACKGROUND & 0xFFFFFF);
		}
		Component name = Widgets.name(definition.id());
		String shown = name.getString();
		if (this.font.width(shown) > item) {
			shown = this.font.plainSubstrByWidth(shown, item - this.font.width("...")) + "...";
		}
		graphics.text(this.font, shown, x, y + item + 3, over ? COLOR_WHITE : COLOR_TEXT, true);
		if (!over) {
			return null;
		}
		if (canUse) {
			NeuItems.pointer(graphics);
			return name;
		}
		return Component.empty().append(name).append("\n").append(Widgets.describe(definition).copy().withColor(COLOR_MUTED));
	}

	private void section(GuiGraphicsExtractor graphics, Component label, int x, int width, int y) {
		int textWidth = this.font.width(label);
		int lineLength = Math.max(0, (width - textWidth - SECTION_LABEL_GAP * 2) / 2);
		int textY = y + (SECTION_HEIGHT - this.font.lineHeight) / 2 + 1;
		int lineY = textY + this.font.lineHeight / 2 - 1;
		if (lineLength > 0) {
			graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_SECTION_LINE);
			graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_SECTION_LINE);
		}
		graphics.text(this.font, label, x + lineLength + SECTION_LABEL_GAP, textY, COLOR_TEXT, true);
	}

	private static void searchIcon(GuiGraphicsExtractor graphics, int x, int y) {
		icon(graphics, 0, x, y, COLOR_TEXT);
	}

	private static void icon(GuiGraphicsExtractor graphics, int index, int x, int y, int color) {
		int u = index % (ICONS_SIZE / ICON) * ICON;
		int v = index / (ICONS_SIZE / ICON) * ICON;
		int shadow = color & 0xFF000000 | (color >> 16 & 0xFF) / 4 << 16 | (color >> 8 & 0xFF) / 4 << 8 | (color & 0xFF) / 4;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x + 1, y + 1, u, v, ICON, ICON, ICONS_SIZE, ICONS_SIZE, shadow);
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, u, v, ICON, ICON, ICONS_SIZE, ICONS_SIZE, color);
	}

	// Wheel title with add and delete buttons
	private Component extractWheelHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = buttonY();
		Component result = null;
		if (button(graphics, "+", addX(), y, canAdd(), mouseX, mouseY)) {
			result = Component.translatable("screen.mccinfo.widgets.edit.add");
		}
		if (button(graphics, "-", deleteX(), y, canDelete(), mouseX, mouseY)) {
			result = Component.translatable("screen.mccinfo.widgets.edit.delete");
		}
		button(graphics, "<", previousX(), y, this.page > 0, mouseX, mouseY);
		button(graphics, ">", nextX(), y, this.page < this.wheels.size() - 1, mouseX, mouseY);
		Component title = Component.translatable("screen.mccinfo.widgets.edit.wheel", this.page + 1);
		int center = (previousX() + BUTTON + nextX()) / 2;
		graphics.centeredText(this.font, title, center, this.top + (TOP_BAR - this.font.lineHeight) / 2 + 1, COLOR_WHITE);
		return result;
	}

	private boolean button(GuiGraphicsExtractor graphics, String label, int x, int y, boolean active, int mouseX, int mouseY) {
		boolean over = this.pressId == null && inside(mouseX, mouseY, x, y, BUTTON, BUTTON);
		graphics.fill(x, y, x + BUTTON, y + BUTTON, over && active ? COLOR_CONTROL_HOVER : COLOR_CONTROL);
		graphics.text(this.font, label, x + (BUTTON - this.font.width(label)) / 2 + 1, y + (BUTTON - this.font.lineHeight) / 2 + 1,
				active ? COLOR_WHITE : COLOR_DISABLED, true);
		if (over && active) {
			NeuItems.pointer(graphics);
		}
		return over && active;
	}

	private void extractContent(GuiGraphicsExtractor graphics, String id, int x, int y, int size) {
		extractContent(graphics, id, x, y, size, null);
	}

	private void extractContent(GuiGraphicsExtractor graphics, String id, int x, int y, int size, ScreenRectangle clip) {
		if (Widgets.isEmote(id)) {
			EmotePreviewRenderer.extract(graphics, id, this.opened, Math.round((x + 1) * this.scale), Math.round((y + 1) * this.scale),
					Math.round((x + size - 1) * this.scale), Math.round((y + size - 1) * this.scale), clip);
			return;
		}
		Identifier icon = Widgets.icon(id);
		if (icon != null) {
			int iconScale = Math.max(1, Math.round(size * ICON_FILL / ICON_TEXTURE));
			int iconSize = ICON_TEXTURE * iconScale;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + (size - iconSize) / 2, y + (size - iconSize) / 2);
			graphics.pose().scale(iconScale, iconScale);
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0.0F, 0.0F, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE);
			graphics.pose().popMatrix();
			return;
		}
		String name = this.font.plainSubstrByWidth(Widgets.name(id).getString(), size - 4);
		graphics.text(this.font, name, x + (size - this.font.width(name)) / 2, y + (size - this.font.lineHeight) / 2 + 1, COLOR_TEXT, true);
	}

	private ScreenRectangle listClip() {
		int x0 = Math.round((this.left + BORDER) * this.scale);
		int y0 = Math.round(viewTop() * this.scale);
		return new ScreenRectangle(x0, y0, Math.round(this.listRight * this.scale) - x0, Math.round(viewBottom() * this.scale) - y0);
	}

	private static int hoverColor() {
		int accent = SettingsPanel.ACCENT;
		int red = Math.round((COLOR_CARD >> 16 & 0xFF) * (1 - HOVER_TINT) + (accent >> 16 & 0xFF) * HOVER_TINT);
		int green = Math.round((COLOR_CARD >> 8 & 0xFF) * (1 - HOVER_TINT) + (accent >> 8 & 0xFF) * HOVER_TINT);
		int blue = Math.round((COLOR_CARD & 0xFF) * (1 - HOVER_TINT) + (accent & 0xFF) * HOVER_TINT);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private void save() {
		Widgets.saveWheels(this.wheels);
	}

	// Drop on a slot: swap or replace
	private void place(String id, int target) {
		List<String> wheel = wheel();
		int existing = wheel.indexOf(id);
		if (existing >= 0) {
			Collections.swap(wheel, existing, target);
		} else {
			wheel.set(target, id);
		}
		save();
	}

	// Click: take out of the wheel or put in the first free slot
	private void placeFirstFree(String id) {
		List<String> wheel = wheel();
		int existing = wheel.indexOf(id);
		if (existing >= 0) {
			wheel.set(existing, "");
			save();
			return;
		}
		for (int slot = 0; slot < wheel.size(); slot++) {
			if (!usable(wheel.get(slot))) {
				wheel.set(slot, id);
				save();
				return;
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		if (this.redeemOpen) {
			return redeemClicked(event, x, y, doubleClick);
		}
		if (overSearch(x, y)) {
			this.setFocused(this.search);
			this.search.onClick(new MouseButtonEvent(x, y, event.buttonInfo()), doubleClick);
			return true;
		}
		this.setFocused(null);
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.maxScroll > 0 && x >= this.listRight - SCROLLBAR_GRAB
				&& x < this.listRight + SCROLLBAR_WIDTH + SCROLLBAR_GRAB && y >= viewTop() && y < viewBottom()) {
			int thumbY = thumbY();
			int thumbHeight = thumbHeight();
			this.dragOffset = y >= thumbY && y < thumbY + thumbHeight ? y - thumbY : thumbHeight / 2.0;
			this.draggingScrollbar = true;
			dragScrollbar(y);
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(x, y, redeemX(), searchY(), redeemWidth(), SEARCH_HEIGHT)) {
			NeuItems.clickSound();
			openRedeem();
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			int slot = slotAt(x, y);
			if (slot >= 0 && usable(wheel().get(slot))) {
				NeuItems.clickSound();
				wheel().set(slot, "");
				save();
			}
			return true;
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		if (!inside(x, y, this.left, this.top, this.right - this.left, this.bottom - this.top)) {
			this.onClose();
			return true;
		}
		int buttonY = buttonY();
		if (canAdd() && inside(x, y, addX(), buttonY, BUTTON, BUTTON)) {
			NeuItems.clickSound();
			this.wheels.add(this.page + 1, Widgets.emptyWheel());
			this.page++;
			save();
			return true;
		}
		if (canDelete() && inside(x, y, deleteX(), buttonY, BUTTON, BUTTON)) {
			NeuItems.clickSound();
			this.wheels.remove(this.page);
			this.page = Math.min(this.page, this.wheels.size() - 1);
			save();
			return true;
		}
		if (this.page > 0 && inside(x, y, previousX(), buttonY, BUTTON, BUTTON)) {
			NeuItems.clickSound();
			this.page--;
			return true;
		}
		if (this.page < this.wheels.size() - 1 && inside(x, y, nextX(), buttonY, BUTTON, BUTTON)) {
			NeuItems.clickSound();
			this.page++;
			return true;
		}
		Placed item = placedAt(x, y);
		if (item != null && usable(item.definition().id())) {
			press(item.definition().id(), -1, x, y);
			return true;
		}
		int slot = slotAt(x, y);
		if (slot >= 0 && usable(wheel().get(slot))) {
			press(wheel().get(slot), slot, x, y);
			return true;
		}
		return true;
	}

	private void press(String id, int from, double x, double y) {
		this.pressId = id;
		this.pressFrom = from;
		this.pressX = x;
		this.pressY = y;
		this.dragging = false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingScrollbar) {
			dragScrollbar(event.y() / this.scale);
			return true;
		}
		if (this.pressId != null && !this.dragging) {
			double x = event.x() / this.scale;
			double y = event.y() / this.scale;
			this.dragging = Math.abs(x - this.pressX) > DRAG_THRESHOLD || Math.abs(y - this.pressY) > DRAG_THRESHOLD;
		}
		return this.pressId != null || super.mouseDragged(event, dx, dy);
	}

	// Drop: swap slots, place, or take out when dropped outside
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.draggingScrollbar) {
			this.draggingScrollbar = false;
			return true;
		}
		if (this.pressId == null) {
			return super.mouseReleased(event);
		}
		int target = slotAt(event.x() / this.scale, event.y() / this.scale);
		if (this.dragging) {
			if (target >= 0 && this.pressFrom >= 0) {
				Collections.swap(wheel(), this.pressFrom, target);
				save();
			} else if (target >= 0) {
				place(this.pressId, target);
			} else if (this.pressFrom >= 0) {
				wheel().set(this.pressFrom, "");
				save();
			}
			NeuItems.clickSound();
		} else if (this.pressFrom < 0) {
			NeuItems.clickSound();
			placeFirstFree(this.pressId);
		}
		this.pressId = null;
		this.pressFrom = -1;
		this.dragging = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double x = mouseX / this.scale;
		if (scrollY == 0 || this.redeemOpen) {
			return true;
		}
		if (x < this.listRight + SCROLLBAR_WIDTH) {
			this.scrollTarget = Mth.clamp(this.scrollTarget - (float) scrollY * SCROLL_STEP, 0.0F, this.maxScroll);
		} else if (x >= this.wheelLeft) {
			this.page = Math.clamp(this.page - (int) Math.signum(scrollY), 0, this.wheels.size() - 1);
		}
		return true;
	}

	@Override
	public void tick() {
		if (!Widgets.available()) {
			this.onClose();
		}
	}

	@Override
	public void onClose() {
		if (this.parent instanceof WidgetWheelScreen wheel) {
			wheel.showPage(this.page);
		}
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
