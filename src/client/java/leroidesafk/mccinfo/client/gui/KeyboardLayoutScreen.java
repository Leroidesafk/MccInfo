package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.ModKeyCategory;
import leroidesafk.mccinfo.client.PrivateKeys;
import leroidesafk.mccinfo.client.items.NeuItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public final class KeyboardLayoutScreen extends Screen implements MenuBackdrop.Overlay {
	// Keyboard size and layout
	private static final int MAX_UNIT = 26;
	private static final int MIN_UNIT = 10;
	private static final int GAP = 2;
	private static final int MARGIN = 16;
	private static final int PLATE_PADDING = 6;
	private static final int PLATE_RADIUS = 4;
	private static final int SCREEN_MARGIN = 8;
	private static final int TITLE_TO_TABS = 16;
	private static final int SUMMARY_TO_BOARD = 18;
	private static final int FOOTER = 8 + 12 + 9;
	private static final float MIN_LABEL_SCALE = 0.5F;
	private static final int TAB_WIDTH = 60;
	private static final int TAB_HEIGHT = 18;
	private static final int TAB_GAP = 4;
	private static final int TAB_GROUP_GAP = 16;
	private static final float MOUSE_GAP = 1.0F;
	private static final float MOUSE_WIDTH = 2.2F;
	private static final float MOUSE_HEIGHT = 3.8F;

	// Key colors
	private static final int COLOR_KEY = 0xFF1C1C1F;
	private static final int COLOR_KEY_HOVER = 0xFF2C2C31;
	private static final int COLOR_UNUSED = 0xFF45454B;
	private static final int COLOR_BOUND = 0xFF55C46A;
	private static final int COLOR_MOD = SettingsPanel.ACCENT;
	private static final int COLOR_CONFLICT = 0xFFE04848;
	private static final int COLOR_DISABLED = 0xFF2A2A2E;
	private static final int COLOR_LABEL = 0xFFE6E6E6;
	private static final int COLOR_LABEL_DIM = 0xFF77777E;
	private static final int COLOR_HINT = 0xFFA0A0A8;
	private static final int TAB_BORDER = 0xFF3A3A3A;
	private static final int TAB_BORDER_HOVER = 0xFF5A5A5A;
	private static final int TAB_BACKGROUND = 0xFF1A1A1A;
	private static final int TAB_TEXT = 0xFFBDBDBD;
	private static final int TAB_SELECTED = SettingsPanel.ACCENT;

	// QWERTY or AZERTY
	enum Layout {
		QWERTY,
		AZERTY
	}

	// 100%, 75% or 60% keyboard
	enum Size {
		FULL("100%"),
		SEVENTY_FIVE("75%"),
		SIXTY("60%");

		final String label;

		Size(String label) {
			this.label = label;
		}
	}

	// Rectangle in key units
	private record Rect(float x, float y, float w, float h) {
	}

	// Key: its label and its shape
	private record Cap(InputConstants.Key key, String label, List<Rect> rects) {
		boolean bindable() {
			return this.key != null;
		}
	}

	// Whole board with the mouse
	private record Board(List<Cap> caps, Rect keyboard, Rect mouse, float width, float height) {
	}

	// What a key does
	private record Binding(Component name, Component category, boolean mod) {
	}

	private record Tab(String label, int x, Runnable select, boolean selected) {
	}

	// Key names, AZERTY ones apart
	private static final Map<Integer, String> NAMES = new HashMap<>();
	private static final Map<Integer, String> AZERTY_NAMES = new HashMap<>();

	static {
		NAMES.put(GLFW_KEY_ESCAPE, "Esc");
		NAMES.put(GLFW_KEY_GRAVE_ACCENT, "`");
		NAMES.put(GLFW_KEY_MINUS, "-");
		NAMES.put(GLFW_KEY_EQUAL, "=");
		NAMES.put(GLFW_KEY_BACKSPACE, "Back");
		NAMES.put(GLFW_KEY_TAB, "Tab");
		NAMES.put(GLFW_KEY_LEFT_BRACKET, "[");
		NAMES.put(GLFW_KEY_RIGHT_BRACKET, "]");
		NAMES.put(GLFW_KEY_BACKSLASH, "\\");
		NAMES.put(GLFW_KEY_CAPS_LOCK, "Caps");
		NAMES.put(GLFW_KEY_SEMICOLON, ";");
		NAMES.put(GLFW_KEY_APOSTROPHE, "'");
		NAMES.put(GLFW_KEY_ENTER, "Enter");
		NAMES.put(GLFW_KEY_LEFT_SHIFT, "Shift");
		NAMES.put(GLFW_KEY_RIGHT_SHIFT, "Shift");
		NAMES.put(GLFW_KEY_WORLD_2, "\\");
		NAMES.put(GLFW_KEY_COMMA, ",");
		NAMES.put(GLFW_KEY_PERIOD, ".");
		NAMES.put(GLFW_KEY_SLASH, "/");
		NAMES.put(GLFW_KEY_LEFT_CONTROL, "Ctrl");
		NAMES.put(GLFW_KEY_RIGHT_CONTROL, "Ctrl");
		NAMES.put(GLFW_KEY_LEFT_SUPER, "Win");
		NAMES.put(GLFW_KEY_RIGHT_SUPER, "Win");
		NAMES.put(GLFW_KEY_LEFT_ALT, "Alt");
		NAMES.put(GLFW_KEY_RIGHT_ALT, "Alt");
		NAMES.put(GLFW_KEY_MENU, "Menu");
		NAMES.put(GLFW_KEY_SPACE, "Space");
		NAMES.put(GLFW_KEY_PRINT_SCREEN, "PrtSc");
		NAMES.put(GLFW_KEY_SCROLL_LOCK, "ScrLk");
		NAMES.put(GLFW_KEY_PAUSE, "Pause");
		NAMES.put(GLFW_KEY_INSERT, "Ins");
		NAMES.put(GLFW_KEY_HOME, "Home");
		NAMES.put(GLFW_KEY_PAGE_UP, "PgUp");
		NAMES.put(GLFW_KEY_DELETE, "Del");
		NAMES.put(GLFW_KEY_END, "End");
		NAMES.put(GLFW_KEY_PAGE_DOWN, "PgDn");
		NAMES.put(GLFW_KEY_UP, "↑");
		NAMES.put(GLFW_KEY_DOWN, "↓");
		NAMES.put(GLFW_KEY_LEFT, "←");
		NAMES.put(GLFW_KEY_RIGHT, "→");
		NAMES.put(GLFW_KEY_NUM_LOCK, "Num");
		NAMES.put(GLFW_KEY_KP_DIVIDE, "/");
		NAMES.put(GLFW_KEY_KP_MULTIPLY, "*");
		NAMES.put(GLFW_KEY_KP_SUBTRACT, "-");
		NAMES.put(GLFW_KEY_KP_ADD, "+");
		NAMES.put(GLFW_KEY_KP_ENTER, "Enter");
		NAMES.put(GLFW_KEY_KP_DECIMAL, ".");

		AZERTY_NAMES.put(GLFW_KEY_GRAVE_ACCENT, "²");
		AZERTY_NAMES.put(GLFW_KEY_1, "&");
		AZERTY_NAMES.put(GLFW_KEY_2, "é");
		AZERTY_NAMES.put(GLFW_KEY_3, "\"");
		AZERTY_NAMES.put(GLFW_KEY_4, "'");
		AZERTY_NAMES.put(GLFW_KEY_5, "(");
		AZERTY_NAMES.put(GLFW_KEY_6, "-");
		AZERTY_NAMES.put(GLFW_KEY_7, "è");
		AZERTY_NAMES.put(GLFW_KEY_8, "_");
		AZERTY_NAMES.put(GLFW_KEY_9, "ç");
		AZERTY_NAMES.put(GLFW_KEY_0, "à");
		AZERTY_NAMES.put(GLFW_KEY_MINUS, ")");
		AZERTY_NAMES.put(GLFW_KEY_EQUAL, "=");
		AZERTY_NAMES.put(GLFW_KEY_Q, "A");
		AZERTY_NAMES.put(GLFW_KEY_W, "Z");
		AZERTY_NAMES.put(GLFW_KEY_LEFT_BRACKET, "^");
		AZERTY_NAMES.put(GLFW_KEY_RIGHT_BRACKET, "$");
		AZERTY_NAMES.put(GLFW_KEY_A, "Q");
		AZERTY_NAMES.put(GLFW_KEY_SEMICOLON, "M");
		AZERTY_NAMES.put(GLFW_KEY_APOSTROPHE, "ù");
		AZERTY_NAMES.put(GLFW_KEY_BACKSLASH, "*");
		AZERTY_NAMES.put(GLFW_KEY_WORLD_2, "<");
		AZERTY_NAMES.put(GLFW_KEY_Z, "W");
		AZERTY_NAMES.put(GLFW_KEY_M, ",");
		AZERTY_NAMES.put(GLFW_KEY_COMMA, ";");
		AZERTY_NAMES.put(GLFW_KEY_PERIOD, ":");
		AZERTY_NAMES.put(GLFW_KEY_SLASH, "!");
		AZERTY_NAMES.put(GLFW_KEY_RIGHT_ALT, "AltGr");
		AZERTY_NAMES.put(GLFW_KEY_LEFT_SHIFT, "Maj");
		AZERTY_NAMES.put(GLFW_KEY_RIGHT_SHIFT, "Maj");
		AZERTY_NAMES.put(GLFW_KEY_CAPS_LOCK, "Verr");
		AZERTY_NAMES.put(GLFW_KEY_BACKSPACE, "Retour");
		AZERTY_NAMES.put(GLFW_KEY_ENTER, "Entrée");
		AZERTY_NAMES.put(GLFW_KEY_KP_ENTER, "Entr");
		AZERTY_NAMES.put(GLFW_KEY_ESCAPE, "Échap");
		AZERTY_NAMES.put(GLFW_KEY_SPACE, "Espace");
	}

	private final Screen parent;

	@Override
	public Screen backdropParent() {
		return this.parent;
	}
	private Layout layout;
	private Size size;
	private Board board;
	private final List<Tab> tabs = new ArrayList<>();
	private int unit;
	private int originX;
	private int originY;
	private int titleY;
	private int tabY;
	private int summaryY;

	public KeyboardLayoutScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.keyboard.title"));
		this.parent = parent;
		ModConfig config = ModConfig.get();
		this.layout = parse(Layout.class, config.keyboardLayout, detectLayout());
		this.size = parse(Size.class, config.keyboardSize, Size.FULL);
	}

	// Saved choice, or the default one
	private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
		if (value != null) {
			for (E constant : type.getEnumConstants()) {
				if (constant.name().equals(value)) {
					return constant;
				}
			}
		}
		return fallback;
	}

	// AZERTY from the system keyboard
	private static Layout detectLayout() {
		String name = GLFW.glfwGetKeyName(GLFW_KEY_Q, 0);
		return "a".equalsIgnoreCase(name) ? Layout.AZERTY : Layout.QWERTY;
	}

	@Override
	protected void init() {
		this.board = build(this.size, this.layout);
		int byWidth = (int) ((this.width - MARGIN * 2 - PLATE_PADDING * 4) / this.board.width());
		int header = TITLE_TO_TABS + TAB_HEIGHT + 8 + SUMMARY_TO_BOARD;
		int fixed = header + PLATE_PADDING * 2 + FOOTER;
		int byHeight = (int) ((this.height - SCREEN_MARGIN * 2 - fixed) / this.board.height());
		this.unit = Math.clamp(Math.min(byWidth, byHeight), MIN_UNIT, MAX_UNIT);
		int pixelWidth = Math.round(this.board.width() * this.unit) - GAP;
		int pixelHeight = Math.round(this.board.height() * this.unit) - GAP;
		this.originX = (this.width - pixelWidth) / 2;
		this.titleY = SCREEN_MARGIN;
		this.tabY = this.titleY + TITLE_TO_TABS;
		this.summaryY = this.tabY + TAB_HEIGHT + 8;
		int areaTop = this.summaryY + SUMMARY_TO_BOARD;
		int block = PLATE_PADDING * 2 + pixelHeight + FOOTER;
		this.originY = areaTop + Math.max(0, (this.height - SCREEN_MARGIN - areaTop - block) / 2) + PLATE_PADDING;

		this.tabs.clear();
		int count = Layout.values().length + Size.values().length;
		int total = TAB_WIDTH * count + TAB_GAP * (count - 2) + TAB_GROUP_GAP;
		int x = (this.width - total) / 2;
		for (Layout value : Layout.values()) {
			this.tabs.add(new Tab(value.name(), x, () -> this.layout = value, value == this.layout));
			x += TAB_WIDTH + TAB_GAP;
		}
		x += TAB_GROUP_GAP - TAB_GAP;
		for (Size value : Size.values()) {
			this.tabs.add(new Tab(value.label, x, () -> this.size = value, value == this.size));
			x += TAB_WIDTH + TAB_GAP;
		}
	}

	private Tab tabAt(double mouseX, double mouseY) {
		for (Tab tab : this.tabs) {
			if (mouseX >= tab.x() && mouseX < tab.x() + TAB_WIDTH && mouseY >= this.tabY && mouseY < this.tabY + TAB_HEIGHT) {
				return tab;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Tab tab = event.button() == GLFW_MOUSE_BUTTON_LEFT ? tabAt(event.x(), event.y()) : null;
		if (tab != null && !tab.selected()) {
			tab.select().run();
			ModConfig config = ModConfig.get();
			config.keyboardLayout = this.layout.name();
			config.keyboardSize = this.size.name();
			config.save();
			NeuItems.clickSound();
			this.rebuildWidgets();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	// Every key binding of the game and the mod
	private static Map<InputConstants.Key, List<Binding>> bindings() {
		Map<InputConstants.Key, List<Binding>> result = new LinkedHashMap<>();
		Component modCategory = ModKeyCategory.MAIN.label();
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (mapping.isUnbound()) {
				continue;
			}
			InputConstants.Key key;
			try {
				key = InputConstants.getKey(mapping.saveString());
			} catch (IllegalArgumentException e) {
				continue;
			}
			boolean mod = mapping.getCategory() == ModKeyCategory.MAIN;
			result.computeIfAbsent(key, k -> new ArrayList<>())
					.add(new Binding(Component.translatable(mapping.getName()), mapping.getCategory().label(), mod));
		}
		for (PrivateKeys.Key key : PrivateKeys.ALL) {
			if (!key.usable() || key.key().equals(InputConstants.UNKNOWN)) {
				continue;
			}
			result.computeIfAbsent(key.key(), k -> new ArrayList<>())
					.add(new Binding(Component.translatable(key.translationKey), modCategory, true));
		}
		return result;
	}

	// Free, used, mod or conflict
	private static int stateColor(List<Binding> bound) {
		if (bound == null || bound.isEmpty()) {
			return COLOR_UNUSED;
		}
		if (bound.size() > 1) {
			return COLOR_CONFLICT;
		}
		return bound.getFirst().mod() ? COLOR_MOD : COLOR_BOUND;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.titleY, 0xFFFFFFFF);
		drawTabs(graphics, mouseX, mouseY);

		Map<InputConstants.Key, List<Binding>> bindings = bindings();
		long conflicts = bindings.values().stream().filter(list -> list.size() > 1).count();
		graphics.centeredText(this.font, Component.translatable("screen.mccinfo.keyboard.summary", conflicts), this.width / 2, this.summaryY, COLOR_HINT);

		drawPlate(graphics, this.board.keyboard(), PLATE_RADIUS, PLATE_RADIUS);
		int[] mouse = pixels(this.board.mouse());
		drawPlate(graphics, this.board.mouse(), PLATE_PADDING * 3, mouse[2] / 2);

		Cap hovered = null;
		for (Cap cap : this.board.caps()) {
			boolean hover = contains(cap, mouseX, mouseY);
			if (hover) {
				hovered = cap;
			}
			List<Binding> bound = cap.bindable() ? bindings.get(cap.key()) : null;
			int outline = cap.bindable() ? stateColor(bound) : COLOR_DISABLED;
			for (Rect rect : cap.rects()) {
				int[] px = pixels(rect);
				OverlayStyle.fillRounded(graphics, px[0], px[1], px[2], px[3], outline);
			}
			int fill = hover && cap.bindable() ? COLOR_KEY_HOVER : COLOR_KEY;
			for (Rect rect : cap.rects()) {
				int[] px = pixels(rect);
				graphics.fill(px[0] + 1, px[1] + 1, px[0] + px[2] - 1, px[1] + px[3] - 1, fill);
			}
			drawLabel(graphics, cap, cap.bindable() ? COLOR_LABEL : COLOR_LABEL_DIM);
		}

		List<String> missing = new ArrayList<>();
		for (Map.Entry<InputConstants.Key, List<Binding>> entry : bindings.entrySet()) {
			if (entry.getKey().getType() == InputConstants.Type.KEYSYM && this.board.caps().stream().noneMatch(cap -> entry.getKey().equals(cap.key()))) {
				missing.add(entry.getKey().getDisplayName().getString());
			}
		}
		int[] plate = pixels(this.board.keyboard());
		int legendY = plate[1] + plate[3] + PLATE_PADDING + 8;
		if (!missing.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("screen.mccinfo.keyboard.missing", String.join(", ", missing)),
					this.width / 2, legendY, COLOR_HINT);
			legendY += 12;
		}
		drawLegend(graphics, legendY);

		if (hovered != null) {
			graphics.setComponentTooltipForNextFrame(this.font, tooltip(hovered, hovered.bindable() ? bindings.get(hovered.key()) : null), mouseX, mouseY);
		}
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Tab hovered = tabAt(mouseX, mouseY);
		for (Tab tab : this.tabs) {
			int x = tab.x();
			boolean hover = tab == hovered && !tab.selected();
			graphics.fill(x, this.tabY, x + TAB_WIDTH, this.tabY + TAB_HEIGHT, hover ? TAB_BORDER_HOVER : TAB_BORDER);
			graphics.fill(x + 1, this.tabY + 1, x + TAB_WIDTH - 1, this.tabY + TAB_HEIGHT - 1, TAB_BACKGROUND);
			int textWidth = this.font.width(tab.label());
			graphics.text(this.font, tab.label(), x + (TAB_WIDTH - textWidth) / 2, this.tabY + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1,
					tab.selected() ? TAB_SELECTED : hover ? 0xFFFFFFFF : TAB_TEXT, true);
		}
	}

	private void drawPlate(GuiGraphicsExtractor graphics, Rect rect, int topRadius, int bottomRadius) {
		int[] px = pixels(rect);
		int x = px[0] - PLATE_PADDING;
		int y = px[1] - PLATE_PADDING;
		int width = px[2] + PLATE_PADDING * 2;
		int height = px[3] + PLATE_PADDING * 2;
		for (int row = 0; row < height; row++) {
			int inset = 0;
			if (row < topRadius) {
				inset = inset(topRadius, topRadius - row);
			} else if (row >= height - bottomRadius) {
				inset = inset(bottomRadius, row - (height - bottomRadius) + 1);
			}
			graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, OverlayStyle.BACKGROUND_COLOR);
		}
	}

	private static int inset(int radius, int distance) {
		double dy = distance - 0.5;
		return (int) Math.round(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
	}

	// Tooltip: everything bound to the key
	private List<Component> tooltip(Cap cap, List<Binding> bound) {
		List<Component> lines = new ArrayList<>();
		if (!cap.bindable()) {
			lines.add(Component.literal(cap.label()).withStyle(ChatFormatting.WHITE));
			lines.add(Component.translatable("screen.mccinfo.keyboard.fn").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		lines.add(Component.literal(cap.label()).withStyle(ChatFormatting.WHITE)
				.append(Component.literal("  (" + cap.key().getDisplayName().getString() + ")").withStyle(ChatFormatting.DARK_GRAY)));
		if (bound == null || bound.isEmpty()) {
			lines.add(Component.translatable("screen.mccinfo.keyboard.unused").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		if (bound.size() > 1) {
			lines.add(Component.translatable("screen.mccinfo.keyboard.conflict", bound.size()).withStyle(ChatFormatting.RED));
		}
		for (Binding binding : bound) {
			lines.add(Component.literal("• ").append(binding.name()).withStyle(binding.mod() ? ChatFormatting.AQUA : ChatFormatting.GREEN)
					.append(Component.literal(" · ").append(binding.category()).withStyle(ChatFormatting.DARK_GRAY)));
		}
		return lines;
	}

	// Color legend
	private void drawLegend(GuiGraphicsExtractor graphics, int y) {
		Component[] labels = {
				Component.translatable("screen.mccinfo.keyboard.legend.bound"),
				Component.translatable("screen.mccinfo.keyboard.legend.mod"),
				Component.translatable("screen.mccinfo.keyboard.legend.conflict"),
				Component.translatable("screen.mccinfo.keyboard.legend.unused")};
		int[] colors = {COLOR_BOUND, COLOR_MOD, COLOR_CONFLICT, COLOR_UNUSED};
		int swatch = 8;
		int spacing = 14;
		int total = 0;
		for (Component label : labels) {
			total += swatch + 4 + this.font.width(label) + spacing;
		}
		total -= spacing;
		int x = (this.width - total) / 2;
		for (int i = 0; i < labels.length; i++) {
			OverlayStyle.fillRounded(graphics, x, y, swatch, swatch, colors[i]);
			graphics.fill(x + 1, y + 1, x + swatch - 1, y + swatch - 1, COLOR_KEY);
			x += swatch + 4;
			graphics.text(this.font, labels[i], x, y, COLOR_HINT, false);
			x += this.font.width(labels[i]) + spacing;
		}
	}

	private void drawLabel(GuiGraphicsExtractor graphics, Cap cap, int color) {
		Rect rect = cap.rects().getFirst();
		int[] px = pixels(rect);
		int textWidth = this.font.width(cap.label());
		if (textWidth == 0) {
			return;
		}
		float scale = Math.min(1.0F, Math.min((px[2] - 4) / (float) textWidth, (px[3] - 2) / (float) this.font.lineHeight));
		if (scale < MIN_LABEL_SCALE) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(px[0] + px[2] / 2.0F, px[1] + px[3] / 2.0F);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, cap.label(), -textWidth / 2, -this.font.lineHeight / 2 + 1, color, false);
		graphics.pose().popMatrix();
	}

	private int[] pixels(Rect rect) {
		int x = this.originX + Math.round(rect.x() * this.unit);
		int y = this.originY + Math.round(rect.y() * this.unit);
		int right = this.originX + Math.round((rect.x() + rect.w()) * this.unit) - GAP;
		int bottom = this.originY + Math.round((rect.y() + rect.h()) * this.unit) - GAP;
		return new int[] {x, y, right - x, bottom - y};
	}

	private boolean contains(Cap cap, double mouseX, double mouseY) {
		for (Rect rect : cap.rects()) {
			int[] px = pixels(rect);
			if (mouseX >= px[0] && mouseX < px[0] + px[2] && mouseY >= px[1] && mouseY < px[1] + px[3]) {
				return true;
			}
		}
		return false;
	}

	private static String label(int code, Layout layout) {
		if (layout == Layout.AZERTY && AZERTY_NAMES.containsKey(code)) {
			return AZERTY_NAMES.get(code);
		}
		if (code >= GLFW_KEY_A && code <= GLFW_KEY_Z || code >= GLFW_KEY_0 && code <= GLFW_KEY_9) {
			return String.valueOf((char) code);
		}
		if (code >= GLFW_KEY_F1 && code <= GLFW_KEY_F25) {
			return "F" + (code - GLFW_KEY_F1 + 1);
		}
		if (code >= GLFW_KEY_KP_0 && code <= GLFW_KEY_KP_9) {
			return String.valueOf(code - GLFW_KEY_KP_0);
		}
		return NAMES.getOrDefault(code, "?");
	}

	// Row of keys built left to right
	private static final class Row {
		private final List<Cap> caps;
		private final Layout layout;
		private final float y;
		private float x;

		Row(List<Cap> caps, Layout layout, float x, float y) {
			this.caps = caps;
			this.layout = layout;
			this.x = x;
			this.y = y;
		}

		Row key(int code, float width) {
			this.caps.add(new Cap(InputConstants.Type.KEYSYM.getOrCreate(code), label(code, this.layout), List.of(new Rect(this.x, this.y, width, 1))));
			this.x += width;
			return this;
		}

		Row keys(int... codes) {
			for (int code : codes) {
				key(code, 1);
			}
			return this;
		}

		Row range(int from, int to) {
			for (int code = from; code <= to; code++) {
				key(code, 1);
			}
			return this;
		}

		Row fn(float width) {
			this.caps.add(new Cap(null, "Fn", List.of(new Rect(this.x, this.y, width, 1))));
			this.x += width;
			return this;
		}

		Row gap(float width) {
			this.x += width;
			return this;
		}
	}

	private static Cap single(int code, Layout layout, Rect... rects) {
		return new Cap(InputConstants.Type.KEYSYM.getOrCreate(code), label(code, layout), List.of(rects));
	}

	private static Cap mouse(int button, String label, Rect rect) {
		return new Cap(InputConstants.Type.MOUSE.getOrCreate(button), label, List.of(rect));
	}

	// Board for a size and a layout
	private static Board build(Size size, Layout layout) {
		List<Cap> caps = new ArrayList<>();
		boolean iso = layout == Layout.AZERTY;
		float top = 0;
		if (size == Size.FULL) {
			new Row(caps, layout, 0, 0).keys(GLFW_KEY_ESCAPE).gap(1).range(GLFW_KEY_F1, GLFW_KEY_F4).gap(0.5F).range(GLFW_KEY_F5, GLFW_KEY_F8)
					.gap(0.5F).range(GLFW_KEY_F9, GLFW_KEY_F12).gap(0.25F).keys(GLFW_KEY_PRINT_SCREEN, GLFW_KEY_SCROLL_LOCK, GLFW_KEY_PAUSE);
			top = 1.25F;
		} else if (size == Size.SEVENTY_FIVE) {
			new Row(caps, layout, 0, 0).keys(GLFW_KEY_ESCAPE).range(GLFW_KEY_F1, GLFW_KEY_F12).keys(GLFW_KEY_PRINT_SCREEN, GLFW_KEY_INSERT, GLFW_KEY_DELETE);
			top = 1;
		}
		float r1 = top;
		float r2 = top + 1;
		float r3 = top + 2;
		float r4 = top + 3;
		float r5 = top + 4;

		new Row(caps, layout, 0, r1).keys(size == Size.SIXTY ? GLFW_KEY_ESCAPE : GLFW_KEY_GRAVE_ACCENT)
				.range(GLFW_KEY_1, GLFW_KEY_9).keys(GLFW_KEY_0, GLFW_KEY_MINUS, GLFW_KEY_EQUAL).key(GLFW_KEY_BACKSPACE, 2);
		Row row2 = new Row(caps, layout, 0, r2).key(GLFW_KEY_TAB, 1.5F)
				.keys(GLFW_KEY_Q, GLFW_KEY_W, GLFW_KEY_E, GLFW_KEY_R, GLFW_KEY_T, GLFW_KEY_Y, GLFW_KEY_U, GLFW_KEY_I, GLFW_KEY_O, GLFW_KEY_P,
						GLFW_KEY_LEFT_BRACKET, GLFW_KEY_RIGHT_BRACKET);
		Row row3 = new Row(caps, layout, 0, r3).key(GLFW_KEY_CAPS_LOCK, 1.75F)
				.keys(GLFW_KEY_A, GLFW_KEY_S, GLFW_KEY_D, GLFW_KEY_F, GLFW_KEY_G, GLFW_KEY_H, GLFW_KEY_J, GLFW_KEY_K, GLFW_KEY_L,
						GLFW_KEY_SEMICOLON, GLFW_KEY_APOSTROPHE);
		Row row4 = new Row(caps, layout, 0, r4);
		if (iso) {
			row3.keys(GLFW_KEY_BACKSLASH);
			caps.add(single(GLFW_KEY_ENTER, layout, new Rect(13.5F, r2, 1.5F, 1), new Rect(13.75F, r2 + 0.5F, 1.25F, 1.5F)));
			row4.key(GLFW_KEY_LEFT_SHIFT, 1.25F).keys(GLFW_KEY_WORLD_2);
		} else {
			row2.key(GLFW_KEY_BACKSLASH, 1.5F);
			row3.key(GLFW_KEY_ENTER, 2.25F);
			row4.key(GLFW_KEY_LEFT_SHIFT, 2.25F);
		}
		row4.keys(GLFW_KEY_Z, GLFW_KEY_X, GLFW_KEY_C, GLFW_KEY_V, GLFW_KEY_B, GLFW_KEY_N, GLFW_KEY_M, GLFW_KEY_COMMA, GLFW_KEY_PERIOD, GLFW_KEY_SLASH)
				.key(GLFW_KEY_RIGHT_SHIFT, size == Size.SEVENTY_FIVE ? 1.75F : 2.75F);
		Row row5 = new Row(caps, layout, 0, r5).key(GLFW_KEY_LEFT_CONTROL, 1.25F).key(GLFW_KEY_LEFT_SUPER, 1.25F).key(GLFW_KEY_LEFT_ALT, 1.25F)
				.key(GLFW_KEY_SPACE, 6.25F);

		float keyboardWidth = 15;
		switch (size) {
			case FULL -> {
				row5.key(GLFW_KEY_RIGHT_ALT, 1.25F).key(GLFW_KEY_RIGHT_SUPER, 1.25F).key(GLFW_KEY_MENU, 1.25F).key(GLFW_KEY_RIGHT_CONTROL, 1.25F);
				float nav = 15.25F;
				new Row(caps, layout, nav, r1).keys(GLFW_KEY_INSERT, GLFW_KEY_HOME, GLFW_KEY_PAGE_UP);
				new Row(caps, layout, nav, r2).keys(GLFW_KEY_DELETE, GLFW_KEY_END, GLFW_KEY_PAGE_DOWN);
				new Row(caps, layout, nav + 1, r4).keys(GLFW_KEY_UP);
				new Row(caps, layout, nav, r5).keys(GLFW_KEY_LEFT, GLFW_KEY_DOWN, GLFW_KEY_RIGHT);
				float pad = nav + 3.25F;
				new Row(caps, layout, pad, r1).keys(GLFW_KEY_NUM_LOCK, GLFW_KEY_KP_DIVIDE, GLFW_KEY_KP_MULTIPLY, GLFW_KEY_KP_SUBTRACT);
				new Row(caps, layout, pad, r2).keys(GLFW_KEY_KP_7, GLFW_KEY_KP_8, GLFW_KEY_KP_9);
				new Row(caps, layout, pad, r3).keys(GLFW_KEY_KP_4, GLFW_KEY_KP_5, GLFW_KEY_KP_6);
				new Row(caps, layout, pad, r4).keys(GLFW_KEY_KP_1, GLFW_KEY_KP_2, GLFW_KEY_KP_3);
				new Row(caps, layout, pad, r5).key(GLFW_KEY_KP_0, 2).keys(GLFW_KEY_KP_DECIMAL);
				caps.add(single(GLFW_KEY_KP_ADD, layout, new Rect(pad + 3, r2, 1, 2)));
				caps.add(single(GLFW_KEY_KP_ENTER, layout, new Rect(pad + 3, r4, 1, 2)));
				keyboardWidth = pad + 4;
			}
			case SEVENTY_FIVE -> {
				row5.keys(GLFW_KEY_RIGHT_ALT).fn(1).keys(GLFW_KEY_RIGHT_CONTROL, GLFW_KEY_LEFT, GLFW_KEY_DOWN, GLFW_KEY_RIGHT);
				new Row(caps, layout, 15, r1).keys(GLFW_KEY_HOME);
				new Row(caps, layout, 15, r2).keys(GLFW_KEY_PAGE_UP);
				new Row(caps, layout, 15, r3).keys(GLFW_KEY_PAGE_DOWN);
				new Row(caps, layout, 14, r4).keys(GLFW_KEY_UP, GLFW_KEY_END);
				keyboardWidth = 16;
			}
			case SIXTY -> row5.key(GLFW_KEY_RIGHT_ALT, 1.25F).fn(1.25F).key(GLFW_KEY_MENU, 1.25F).key(GLFW_KEY_RIGHT_CONTROL, 1.25F);
		}
		float keyboardHeight = r5 + 1;

		float mx = keyboardWidth + MOUSE_GAP;
		float mouseHeight = Math.min(MOUSE_HEIGHT, keyboardHeight);
		float my = (keyboardHeight - mouseHeight) / 2;
		caps.add(mouse(GLFW_MOUSE_BUTTON_LEFT, "LMB", new Rect(mx, my, 0.95F, 1.5F)));
		caps.add(mouse(GLFW_MOUSE_BUTTON_MIDDLE, "MMB", new Rect(mx + 0.95F, my + 0.3F, 0.3F, 0.75F)));
		caps.add(mouse(GLFW_MOUSE_BUTTON_RIGHT, "RMB", new Rect(mx + 1.25F, my, 0.95F, 1.5F)));
		caps.add(mouse(GLFW_MOUSE_BUTTON_5, "M5", new Rect(mx, my + 1.75F, 0.4F, 0.6F)));
		caps.add(mouse(GLFW_MOUSE_BUTTON_4, "M4", new Rect(mx, my + 2.4F, 0.4F, 0.6F)));

		return new Board(caps, new Rect(0, 0, keyboardWidth, keyboardHeight), new Rect(mx, my, MOUSE_WIDTH, mouseHeight),
				mx + MOUSE_WIDTH, keyboardHeight);
	}
}
