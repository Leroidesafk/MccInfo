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
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.sdl.SDLKeyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.blaze3d.platform.InputConstants.*;
import static org.lwjgl.sdl.SDLScancode.*;

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
	private record Binding(Component name, Component category, boolean mod, String combo, Component comboName, boolean debug) {
	}

	private record Tab(String label, int x, Runnable select, boolean selected) {
	}

	// Key names, AZERTY ones apart
	private static final Map<Integer, String> NAMES = new HashMap<>();
	private static final Map<Integer, String> AZERTY_NAMES = new HashMap<>();

	static {
		NAMES.put(KEY_ESCAPE, "Esc");
		NAMES.put(KEY_GRAVE, "`");
		NAMES.put(KEY_MINUS, "-");
		NAMES.put(KEY_EQUALS, "=");
		NAMES.put(KEY_BACKSPACE, "Back");
		NAMES.put(KEY_TAB, "Tab");
		NAMES.put(KEY_LBRACKET, "[");
		NAMES.put(KEY_RBRACKET, "]");
		NAMES.put(KEY_BACKSLASH, "\\");
		NAMES.put(KEY_CAPSLOCK, "Caps");
		NAMES.put(KEY_SEMICOLON, ";");
		NAMES.put(KEY_APOSTROPHE, "'");
		NAMES.put(KEY_RETURN, "Enter");
		NAMES.put(KEY_LSHIFT, "Shift");
		NAMES.put(KEY_RSHIFT, "Shift");
		NAMES.put(SDL_SCANCODE_NONUSBACKSLASH, "\\");
		NAMES.put(KEY_COMMA, ",");
		NAMES.put(KEY_PERIOD, ".");
		NAMES.put(KEY_SLASH, "/");
		NAMES.put(KEY_LCONTROL, "Ctrl");
		NAMES.put(KEY_RCONTROL, "Ctrl");
		NAMES.put(KEY_LGUI, "Win");
		NAMES.put(KEY_RGUI, "Win");
		NAMES.put(KEY_LALT, "Alt");
		NAMES.put(KEY_RALT, "Alt");
		NAMES.put(SDL_SCANCODE_APPLICATION, "Menu");
		NAMES.put(KEY_SPACE, "Space");
		NAMES.put(KEY_PRINTSCREEN, "PrtSc");
		NAMES.put(KEY_SCROLLLOCK, "ScrLk");
		NAMES.put(KEY_PAUSE, "Pause");
		NAMES.put(KEY_INSERT, "Ins");
		NAMES.put(KEY_HOME, "Home");
		NAMES.put(KEY_PAGEUP, "PgUp");
		NAMES.put(KEY_DELETE, "Del");
		NAMES.put(KEY_END, "End");
		NAMES.put(KEY_PAGEDOWN, "PgDn");
		NAMES.put(KEY_UP, "↑");
		NAMES.put(KEY_DOWN, "↓");
		NAMES.put(KEY_LEFT, "←");
		NAMES.put(KEY_RIGHT, "→");
		NAMES.put(KEY_NUMLOCK, "Num");
		NAMES.put(SDL_SCANCODE_KP_DIVIDE, "/");
		NAMES.put(KEY_MULTIPLY, "*");
		NAMES.put(SDL_SCANCODE_KP_MINUS, "-");
		NAMES.put(KEY_ADD, "+");
		NAMES.put(KEY_NUMPADENTER, "Enter");
		NAMES.put(SDL_SCANCODE_KP_PERIOD, ".");

		AZERTY_NAMES.put(KEY_GRAVE, "²");
		AZERTY_NAMES.put(KEY_1, "&");
		AZERTY_NAMES.put(KEY_2, "é");
		AZERTY_NAMES.put(KEY_3, "\"");
		AZERTY_NAMES.put(KEY_4, "'");
		AZERTY_NAMES.put(KEY_5, "(");
		AZERTY_NAMES.put(KEY_6, "-");
		AZERTY_NAMES.put(KEY_7, "è");
		AZERTY_NAMES.put(KEY_8, "_");
		AZERTY_NAMES.put(KEY_9, "ç");
		AZERTY_NAMES.put(KEY_0, "à");
		AZERTY_NAMES.put(KEY_MINUS, ")");
		AZERTY_NAMES.put(KEY_EQUALS, "=");
		AZERTY_NAMES.put(KEY_Q, "A");
		AZERTY_NAMES.put(KEY_W, "Z");
		AZERTY_NAMES.put(KEY_LBRACKET, "^");
		AZERTY_NAMES.put(KEY_RBRACKET, "$");
		AZERTY_NAMES.put(KEY_A, "Q");
		AZERTY_NAMES.put(KEY_SEMICOLON, "M");
		AZERTY_NAMES.put(KEY_APOSTROPHE, "ù");
		AZERTY_NAMES.put(KEY_BACKSLASH, "*");
		AZERTY_NAMES.put(SDL_SCANCODE_NONUSBACKSLASH, "<");
		AZERTY_NAMES.put(KEY_Z, "W");
		AZERTY_NAMES.put(KEY_M, ",");
		AZERTY_NAMES.put(KEY_COMMA, ";");
		AZERTY_NAMES.put(KEY_PERIOD, ":");
		AZERTY_NAMES.put(KEY_SLASH, "!");
		AZERTY_NAMES.put(KEY_RALT, "AltGr");
		AZERTY_NAMES.put(KEY_LSHIFT, "Maj");
		AZERTY_NAMES.put(KEY_RSHIFT, "Maj");
		AZERTY_NAMES.put(KEY_CAPSLOCK, "Verr");
		AZERTY_NAMES.put(KEY_BACKSPACE, "Retour");
		AZERTY_NAMES.put(KEY_RETURN, "Entrée");
		AZERTY_NAMES.put(KEY_NUMPADENTER, "Entr");
		AZERTY_NAMES.put(KEY_ESCAPE, "Échap");
		AZERTY_NAMES.put(KEY_SPACE, "Espace");
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
		int key = SDLKeyboard.SDL_GetKeyFromScancode(KEY_Q, (short) 0, false);
		return key == 'a' ? Layout.AZERTY : Layout.QWERTY;
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
		Tab tab = event.button() == MOUSE_BUTTON_LEFT ? tabAt(event.x(), event.y()) : null;
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
		net.minecraft.client.Options options = Minecraft.getInstance().options;
		InputConstants.Key debugModifier = options.keyDebugModifier.isUnbound() ? InputConstants.UNKNOWN : InputConstants.getKey(options.keyDebugModifier.saveString());
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
			boolean debug = mapping.getCategory() == KeyMapping.Category.DEBUG;
			boolean f3Combo = debug && mapping != options.keyDebugOverlay && mapping != options.keyDebugModifier && !debugModifier.equals(InputConstants.UNKNOWN);
			String combo = f3Combo ? "debug+" + debugModifier.getName() + "+" + key.getName() : key.getName();
			Component comboName = f3Combo ? debugModifier.getDisplayName().copy().append(" + ").append(key.getDisplayName()) : null;
			result.computeIfAbsent(key, k -> new ArrayList<>())
					.add(new Binding(Component.translatable(mapping.getName()), mapping.getCategory().label(), mod, combo, comboName, debug));
		}
		for (PrivateKeys.Key key : PrivateKeys.ALL) {
			if (!key.usable() || key.key().equals(InputConstants.UNKNOWN)) {
				continue;
			}
			result.computeIfAbsent(key.key(), k -> new ArrayList<>())
					.add(new Binding(Component.translatable(key.translationKey), modCategory, true, key.comboId(), key.modifiers() == 0 ? null : key.displayName(), false));
		}
		return result;
	}

	// Biggest group of actions sharing the exact same combination (debug keys never conflict with each other)
	private static int conflictSize(List<Binding> bound) {
		if (bound == null) {
			return 0;
		}
		Map<String, List<Binding>> groups = new HashMap<>();
		for (Binding binding : bound) {
			groups.computeIfAbsent(binding.combo(), k -> new ArrayList<>()).add(binding);
		}
		int worst = 0;
		for (List<Binding> group : groups.values()) {
			if (group.size() > 1 && !group.stream().allMatch(Binding::debug)) {
				worst = Math.max(worst, group.size());
			}
		}
		return worst;
	}

	private static long conflictCount(Map<InputConstants.Key, List<Binding>> bindings) {
		Map<String, List<Binding>> groups = new HashMap<>();
		for (List<Binding> bound : bindings.values()) {
			for (Binding binding : bound) {
				groups.computeIfAbsent(binding.combo(), k -> new ArrayList<>()).add(binding);
			}
		}
		return groups.values().stream().filter(group -> group.size() > 1 && !group.stream().allMatch(Binding::debug)).count();
	}

	// Free, used, mod or conflict
	private static int stateColor(List<Binding> bound) {
		if (bound == null || bound.isEmpty()) {
			return COLOR_UNUSED;
		}
		if (conflictSize(bound) > 1) {
			return COLOR_CONFLICT;
		}
		return bound.stream().anyMatch(Binding::mod) ? COLOR_MOD : COLOR_BOUND;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.titleY, 0xFFFFFFFF);
		drawTabs(graphics, mouseX, mouseY);

		Map<InputConstants.Key, List<Binding>> bindings = bindings();
		long conflicts = conflictCount(bindings);
		graphics.centeredText(this.font, Component.translatable("screen.mccinfo.keyboard.summary", conflicts, Minecraft.getInstance().options.keyDebugModifier.getTranslatedKeyMessage()),
				this.width / 2, this.summaryY, COLOR_HINT);

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
			if (entry.getKey().getType() == InputConstants.Type.KEYBOARD && this.board.caps().stream().noneMatch(cap -> entry.getKey().equals(cap.key()))) {
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
		int conflict = conflictSize(bound);
		if (conflict > 1) {
			lines.add(Component.translatable("screen.mccinfo.keyboard.conflict", conflict).withStyle(ChatFormatting.RED));
		}
		for (Binding binding : bound) {
			MutableComponent line = Component.literal("• ").append(binding.name()).withStyle(binding.mod() ? ChatFormatting.AQUA : ChatFormatting.GREEN);
			if (binding.comboName() != null) {
				line.append(Component.literal(" [").append(binding.comboName()).append("]").withStyle(ChatFormatting.GOLD));
			}
			lines.add(line.append(Component.literal(" · ").append(binding.category()).withStyle(ChatFormatting.DARK_GRAY)));
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
		if (code >= KEY_A && code <= KEY_Z) {
			return String.valueOf((char) ('A' + code - KEY_A));
		}
		if (code >= KEY_1 && code <= KEY_0) {
			return code == KEY_0 ? "0" : String.valueOf(code - KEY_1 + 1);
		}
		if (code >= KEY_F1 && code <= KEY_F12) {
			return "F" + (code - KEY_F1 + 1);
		}
		if (code >= KEY_F13 && code <= KEY_F24) {
			return "F" + (code - KEY_F13 + 13);
		}
		if (code >= KEY_NUMPAD1 && code <= KEY_NUMPAD0) {
			return code == KEY_NUMPAD0 ? "0" : String.valueOf(code - KEY_NUMPAD1 + 1);
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
			this.caps.add(new Cap(InputConstants.Type.KEYBOARD.getOrCreate(code), label(code, this.layout), List.of(new Rect(this.x, this.y, width, 1))));
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
		return new Cap(InputConstants.Type.KEYBOARD.getOrCreate(code), label(code, layout), List.of(rects));
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
			new Row(caps, layout, 0, 0).keys(KEY_ESCAPE).gap(1).range(KEY_F1, KEY_F4).gap(0.5F).range(KEY_F5, KEY_F8)
					.gap(0.5F).range(KEY_F9, KEY_F12).gap(0.25F).keys(KEY_PRINTSCREEN, KEY_SCROLLLOCK, KEY_PAUSE);
			top = 1.25F;
		} else if (size == Size.SEVENTY_FIVE) {
			new Row(caps, layout, 0, 0).keys(KEY_ESCAPE).range(KEY_F1, KEY_F12).keys(KEY_PRINTSCREEN, KEY_INSERT, KEY_DELETE);
			top = 1;
		}
		float r1 = top;
		float r2 = top + 1;
		float r3 = top + 2;
		float r4 = top + 3;
		float r5 = top + 4;

		new Row(caps, layout, 0, r1).keys(size == Size.SIXTY ? KEY_ESCAPE : KEY_GRAVE)
				.range(KEY_1, KEY_9).keys(KEY_0, KEY_MINUS, KEY_EQUALS).key(KEY_BACKSPACE, 2);
		Row row2 = new Row(caps, layout, 0, r2).key(KEY_TAB, 1.5F)
				.keys(KEY_Q, KEY_W, KEY_E, KEY_R, KEY_T, KEY_Y, KEY_U, KEY_I, KEY_O, KEY_P,
						KEY_LBRACKET, KEY_RBRACKET);
		Row row3 = new Row(caps, layout, 0, r3).key(KEY_CAPSLOCK, 1.75F)
				.keys(KEY_A, KEY_S, KEY_D, KEY_F, KEY_G, KEY_H, KEY_J, KEY_K, KEY_L,
						KEY_SEMICOLON, KEY_APOSTROPHE);
		Row row4 = new Row(caps, layout, 0, r4);
		if (iso) {
			row3.keys(KEY_BACKSLASH);
			caps.add(single(KEY_RETURN, layout, new Rect(13.5F, r2, 1.5F, 1), new Rect(13.75F, r2 + 0.5F, 1.25F, 1.5F)));
			row4.key(KEY_LSHIFT, 1.25F).keys(SDL_SCANCODE_NONUSBACKSLASH);
		} else {
			row2.key(KEY_BACKSLASH, 1.5F);
			row3.key(KEY_RETURN, 2.25F);
			row4.key(KEY_LSHIFT, 2.25F);
		}
		row4.keys(KEY_Z, KEY_X, KEY_C, KEY_V, KEY_B, KEY_N, KEY_M, KEY_COMMA, KEY_PERIOD, KEY_SLASH)
				.key(KEY_RSHIFT, size == Size.SEVENTY_FIVE ? 1.75F : 2.75F);
		Row row5 = new Row(caps, layout, 0, r5).key(KEY_LCONTROL, 1.25F).key(KEY_LGUI, 1.25F).key(KEY_LALT, 1.25F)
				.key(KEY_SPACE, 6.25F);

		float keyboardWidth = 15;
		switch (size) {
			case FULL -> {
				row5.key(KEY_RALT, 1.25F).key(KEY_RGUI, 1.25F).key(SDL_SCANCODE_APPLICATION, 1.25F).key(KEY_RCONTROL, 1.25F);
				float nav = 15.25F;
				new Row(caps, layout, nav, r1).keys(KEY_INSERT, KEY_HOME, KEY_PAGEUP);
				new Row(caps, layout, nav, r2).keys(KEY_DELETE, KEY_END, KEY_PAGEDOWN);
				new Row(caps, layout, nav + 1, r4).keys(KEY_UP);
				new Row(caps, layout, nav, r5).keys(KEY_LEFT, KEY_DOWN, KEY_RIGHT);
				float pad = nav + 3.25F;
				new Row(caps, layout, pad, r1).keys(KEY_NUMLOCK, SDL_SCANCODE_KP_DIVIDE, KEY_MULTIPLY, SDL_SCANCODE_KP_MINUS);
				new Row(caps, layout, pad, r2).keys(KEY_NUMPAD7, KEY_NUMPAD8, KEY_NUMPAD9);
				new Row(caps, layout, pad, r3).keys(KEY_NUMPAD4, KEY_NUMPAD5, KEY_NUMPAD6);
				new Row(caps, layout, pad, r4).keys(KEY_NUMPAD1, KEY_NUMPAD2, KEY_NUMPAD3);
				new Row(caps, layout, pad, r5).key(KEY_NUMPAD0, 2).keys(SDL_SCANCODE_KP_PERIOD);
				caps.add(single(KEY_ADD, layout, new Rect(pad + 3, r2, 1, 2)));
				caps.add(single(KEY_NUMPADENTER, layout, new Rect(pad + 3, r4, 1, 2)));
				keyboardWidth = pad + 4;
			}
			case SEVENTY_FIVE -> {
				row5.keys(KEY_RALT).fn(1).keys(KEY_RCONTROL, KEY_LEFT, KEY_DOWN, KEY_RIGHT);
				new Row(caps, layout, 15, r1).keys(KEY_HOME);
				new Row(caps, layout, 15, r2).keys(KEY_PAGEUP);
				new Row(caps, layout, 15, r3).keys(KEY_PAGEDOWN);
				new Row(caps, layout, 14, r4).keys(KEY_UP, KEY_END);
				keyboardWidth = 16;
			}
			case SIXTY -> row5.key(KEY_RALT, 1.25F).fn(1.25F).key(SDL_SCANCODE_APPLICATION, 1.25F).key(KEY_RCONTROL, 1.25F);
		}
		float keyboardHeight = r5 + 1;

		float mx = keyboardWidth + MOUSE_GAP;
		float mouseHeight = Math.min(MOUSE_HEIGHT, keyboardHeight);
		float my = (keyboardHeight - mouseHeight) / 2;
		caps.add(mouse(MOUSE_BUTTON_LEFT, "LMB", new Rect(mx, my, 0.95F, 1.5F)));
		caps.add(mouse(MOUSE_BUTTON_MIDDLE, "MMB", new Rect(mx + 0.95F, my + 0.3F, 0.3F, 0.75F)));
		caps.add(mouse(MOUSE_BUTTON_RIGHT, "RMB", new Rect(mx + 1.25F, my, 0.95F, 1.5F)));
		caps.add(mouse(MOUSE_BUTTON_5, "M5", new Rect(mx, my + 1.75F, 0.4F, 0.6F)));
		caps.add(mouse(MOUSE_BUTTON_4, "M4", new Rect(mx, my + 2.4F, 0.4F, 0.6F)));

		return new Board(caps, new Rect(0, 0, keyboardWidth, keyboardHeight), new Rect(mx, my, MOUSE_WIDTH, mouseHeight),
				mx + MOUSE_WIDTH, keyboardHeight);
	}
}
