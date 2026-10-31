package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.ModKeyCategory;
import leroidesafk.mccinfo.client.chat.EmojiOverlay;
import leroidesafk.mccinfo.client.chat.ModChatOverlay;
import leroidesafk.mccinfo.client.events.CarnivalTicketOverlay;
import leroidesafk.mccinfo.client.events.EventTimerOverlay;
import leroidesafk.mccinfo.client.fishing.FishingMessagesOverlay;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.patchnotes.PatchNotePanel;
import leroidesafk.mccinfo.client.patchnotes.PatchNoteScreen;
import leroidesafk.mccinfo.client.patchnotes.PatchNotes;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.voice.VoiceSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class EventsScreen extends Screen {
	// Key that opens the menu
	public static final KeyMapping OPEN_SCREEN = new KeyMapping(
			"key.mccinfo.open_events",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			ModKeyCategory.MAIN
	);

	// Tabs
	private static final int TAB_WIDTH = 80;
	private static final int TAB_HEIGHT = 18;
	private static final int OVERLAY_TAB_Y = 8;
	private static final int TAB_GAP = 4;
	private static final int TAB_PANEL_GAP = 6;
	private static final int SCREEN_MARGIN = 8;
	private static final int TAB_BORDER = 0xFF3A3A3A;
	private static final int TAB_BORDER_HOVER = 0xFF5A5A5A;
	private static final int TAB_BACKGROUND = 0xFF1A1A1A;
	private static final int TAB_TEXT = 0xFFBDBDBD;
	private static final int TAB_SELECTED = SettingsPanel.ACCENT;
	// Reset positions button
	private static final int BUTTON_WIDTH = 160;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_BORDER = 0xFF3A3A3A;
	private static final int BUTTON_COLOR = 0xFF2A2A2A;
	private static final int BUTTON_COLOR_HOVER = 0xFF3A3A3A;
	private static final int SPACING = 4;
	// Overlays moved in the Overlay tab
	public static final List<MovableOverlay> OVERLAYS = new java.util.ArrayList<>(List.of(EventTimerOverlay.MOVABLE, CarnivalTicketOverlay.MOVABLE, FishingMessagesOverlay.MOVABLE, ModChatOverlay.MOVABLE, EmojiOverlay.MOVABLE));
	private static final int HINT_COLOR = 0xFFFFFFFF;
	private static final float HIDDEN_ALPHA = 0.35F;
	// Overlay title icons
	private static final int HIDE_ICON_SIZE = 5;
	private static final String[] CROSS_ICON = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
	private static final String[] CIRCLE_ICON = {".###.", "#...#", "#...#", "#...#", ".###."};
	private static final String[] MINUS_ICON = {".....", ".....", "#####", ".....", "....."};
	private static final String[] PLUS_ICON = {"..#..", "..#..", "#####", "..#..", "..#.."};
	private static final int TITLE_ICON_STEP = HIDE_ICON_SIZE + OverlayStyle.PADDING * 2;

	// Resize handles
	private static final int RESIZE_HANDLE_SIZE = 6;

	// Corner being dragged
	private enum Corner {
		TOP_LEFT(true, true), TOP_RIGHT(false, true), BOTTOM_LEFT(true, false), BOTTOM_RIGHT(false, false);

		final boolean left;
		final boolean top;

		Corner(boolean left, boolean top) {
			this.left = left;
			this.top = top;
		}
	}

	// Settings and Overlay tabs
	private enum Tab {
		SETTINGS("screen.mccinfo.events.tab_settings"), OVERLAY("screen.mccinfo.events.tab_overlay");

		final String translationKey;

		Tab(String translationKey) {
			this.translationKey = translationKey;
		}
	}

	private Tab tab = Tab.SETTINGS;
	private int permissionChanges = Permissions.changes();
	private final SettingsPages pages = new SettingsPages(this);
	private final SettingsPanel settings = new SettingsPanel(this.pages::groups, this.pages::footer, this::links, this.pages::stopListening, this::setFocused);
	private MovableOverlay dragged;
	private Corner resizeCorner;
	private double dragOffsetX;
	private double dragOffsetY;

	private final Screen parent;

	public EventsScreen() {
		this(null);
	}

	public EventsScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.events.title"));
		this.parent = parent;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	public static EventsScreen voiceChat(boolean settings) {
		EventsScreen screen = new EventsScreen();
		screen.settings.show("voice_chat", settings ? Component.translatable("screen.mccinfo.settings.section.voice_settings").getString() : null);
		return screen;
	}

	public static void addOverlay(MovableOverlay overlay) {
		OVERLAYS.add(overlay);
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(OPEN_SCREEN);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SCREEN.consumeClick()) {
				if (client.gui.screen() == null && MccIsland.isConnected()) {
					client.gui.setScreen(new EventsScreen());
				}
			}
		});
	}

	// Event features: setting and events permission
	private static boolean events() {
		return Permissions.has(Permissions.Perm.EVENTS);
	}

	public static boolean editingOverlays() {
		return net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof EventsScreen screen && screen.tab == Tab.OVERLAY;
	}

	public static boolean halloweenTimer() {
		return ModConfig.get().halloweenTimer && events();
	}

	public static boolean carnivalTickets() {
		return ModConfig.get().carnivalTickets && events();
	}

	public static boolean ticketIcons() {
		return ModConfig.get().ticketIcons && events();
	}

	public static boolean summerHearts() {
		return ModConfig.get().summerHearts && events();
	}

	// Links of the left column
	private static final String DONATE_URL = "https://paypal.me/Leroidesafk";

	private List<SettingsPanel.Link> links() {
		List<SettingsPanel.Link> links = new java.util.ArrayList<>();
		links.add(new SettingsPanel.Link(Component.translatable("screen.mccinfo.donate"), SettingsPanel.ICON_MONEY,
				() -> net.minecraft.util.Util.getPlatform().openUri(DONATE_URL)));
		if (!PatchNotes.all().isEmpty() && !Permissions.isOutdated() && MccIsland.isConnected()) {
			links.add(new SettingsPanel.Link(Component.translatable("screen.mccinfo.patchnotes.title"), SettingsPanel.ICON_PATCH_NOTES, this::openPatchNotes));
		}
		return links;
	}

	private void openPatchNotes() {
		this.minecraft.gui.setScreen(new PatchNoteScreen(this, new PatchNotePanel(PatchNotes.all())));
	}

	@Override
	protected void init() {
		if (this.tab == Tab.SETTINGS) {
			this.addWidget(this.settings.init(virtualWidth(), panelTop(), panelHeight()));
			for (EditBox box : this.pages.footerInputs()) {
				this.addWidget(box);
			}
			return;
		}
	}

	// Default position, size and zoom for every overlay
	private static void resetPositions() {
		OVERLAYS.forEach(overlay -> {
			overlay.setSize(ModConfig.UNSET, ModConfig.UNSET);
			overlay.resetTextScale();
			overlay.resetZoom();
			overlay.setPosition(ModConfig.UNSET, ModConfig.UNSET);
		});
	}

	private int resetButtonX() {
		return virtualWidth() / 2 - BUTTON_WIDTH / 2;
	}

	private int resetButtonY() {
		return virtualHeight() - SCREEN_MARGIN - BUTTON_HEIGHT;
	}

	private boolean overResetButton(double mouseX, double mouseY) {
		return mouseX >= resetButtonX() && mouseX < resetButtonX() + BUTTON_WIDTH && mouseY >= resetButtonY() && mouseY < resetButtonY() + BUTTON_HEIGHT;
	}

	private void extractResetButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		boolean hovered = overResetButton(mouseX, mouseY);
		int x = resetButtonX();
		int y = resetButtonY();
		float fade = this.settings.fade("reset_positions", hovered);
		graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, BUTTON_BORDER);
		graphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, SettingsPanel.mix(BUTTON_COLOR, BUTTON_COLOR_HOVER, fade));
		Component label = Component.translatable("screen.mccinfo.events.reset_positions");
		graphics.text(this.font, label, x + (BUTTON_WIDTH - this.font.width(label)) / 2, y + (BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1,
				SettingsPanel.mix(TAB_TEXT, HINT_COLOR, fade), true);
		if (hovered) {
			NeuItems.pointer(graphics);
		}
	}

	// Settings rebuilt when the permissions change
	@Override
	public void tick() {
		super.tick();
		if (this.permissionChanges != Permissions.changes()) {
			this.permissionChanges = Permissions.changes();
			this.rebuildWidgets();
		}
	}

	private float scale() {
		return SettingsPanel.uiScale();
	}

	private int virtualWidth() {
		return (int) (this.width / scale());
	}

	private int virtualHeight() {
		return (int) (this.height / scale());
	}

	private int panelHeight() {
		return Math.min(SettingsPanel.MAX_HEIGHT, virtualHeight() - TAB_HEIGHT - TAB_PANEL_GAP - SCREEN_MARGIN * 2);
	}

	private int tabY() {
		return OVERLAY_TAB_Y;
	}

	private int panelTop() {
		int top = tabY() + TAB_HEIGHT + TAB_PANEL_GAP;
		return top + Math.max(0, (virtualHeight() - SCREEN_MARGIN - top - panelHeight()) / 2);
	}

	private int tabX(Tab tab) {
		return virtualWidth() / 2 + (tab == Tab.SETTINGS ? -TAB_WIDTH - TAB_GAP / 2 : TAB_GAP / 2);
	}

	private Tab tabAt(double mouseX, double mouseY) {
		for (Tab candidate : Tab.values()) {
			int x = tabX(candidate);
			if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= tabY() && mouseY < tabY() + TAB_HEIGHT) {
				return candidate;
			}
		}
		return null;
	}

	private void extractTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Tab hovered = tabAt(mouseX, mouseY);
		int y = tabY();
		for (Tab candidate : Tab.values()) {
			int x = tabX(candidate);
			boolean selected = candidate == this.tab;
			float fade = this.settings.fade("tab:" + candidate.name(), candidate == hovered && !selected);
			graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, SettingsPanel.mix(TAB_BORDER, TAB_BORDER_HOVER, fade));
			graphics.fill(x + 1, y + 1, x + TAB_WIDTH - 1, y + TAB_HEIGHT - 1, TAB_BACKGROUND);
			Component label = Component.translatable(candidate.translationKey);
			graphics.text(this.font, label, x + (TAB_WIDTH - this.font.width(label)) / 2, y + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1,
					selected ? TAB_SELECTED : SettingsPanel.mix(TAB_TEXT, HINT_COLOR, fade), true);
			if (candidate == hovered && !selected) {
				NeuItems.pointer(graphics);
			}
		}
	}

	// Wheel: text size or zoom of an overlay, or the settings list
	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.tab == Tab.SETTINGS && scrollY != 0 && this.settings.mouseScrolled(x / scale(), y / scale(), scrollY)) {
			return true;
		}
		if (this.tab == Tab.OVERLAY && scrollY != 0) {
			for (MovableOverlay overlay : OVERLAYS) {
				int overlayX = overlay.x(this.width);
				int overlayY = overlay.y(this.height);
				if (overlay.enabled() && (overlay.textScalable() || overlay.zoomable()) && x >= overlayX && x < overlayX + overlay.width()
						&& y >= overlayY && y < overlayY + overlay.height()) {
					if (overlay.zoomable()) {
						overlay.changeZoom((int) Math.signum(scrollY));
					} else {
						overlay.changeTextScale((int) Math.signum(scrollY));
					}
					return true;
				}
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (this.tab == Tab.OVERLAY) {
			this.extractTransparentBackground(graphics);
			this.minecraft.gui.hud.extractDeferredSubtitles();
		} else {
			super.extractBackground(graphics, mouseX, mouseY, a);
			graphics.pose().pushMatrix();
			graphics.pose().scale(scale(), scale());
			this.settings.extractBackground(graphics);
			graphics.pose().popMatrix();
		}
	}

	// Overlay tab: previews, title buttons and resize handles
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.settings.beginFrame();
		float scale = scale();
		int virtualX = (int) (mouseX / scale);
		int virtualY = (int) (mouseY / scale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		extractTabs(graphics, virtualX, virtualY);
		if (this.tab == Tab.SETTINGS) {
			this.settings.search().extractRenderState(graphics, virtualX, virtualY, a);
			this.settings.extractRenderState(graphics, virtualX, virtualY);
		} else {
			extractResetButton(graphics, virtualX, virtualY);
		}
		graphics.pose().popMatrix();
		if (this.tab == Tab.SETTINGS) {
			return;
		}
		boolean anyOverlay = false;
		for (MovableOverlay overlay : OVERLAYS) {
			if (overlay.enabled()) {
				anyOverlay = true;
				int x = overlay.x(this.width);
				int y = overlay.y(this.height);
				overlay.extractPreview(graphics, x, y, overlay.hidden() ? HIDDEN_ALPHA : 1.0F);
				extractTitleIcon(graphics, overlay.hidden() ? CIRCLE_ICON : CROSS_ICON, hideIconX(overlay, x), y);
				if (overlay.textScalable()) {
					extractTitleIcon(graphics, PLUS_ICON, hideIconX(overlay, x) - TITLE_ICON_STEP, y);
					extractTitleIcon(graphics, MINUS_ICON, hideIconX(overlay, x) - TITLE_ICON_STEP * 2, y);
				}
				if (overlay.resizable()) {
					extractResizeHandles(graphics, overlay, x, y);
				}
			}
		}
		graphics.centeredText(this.font,
				Component.translatable(anyOverlay ? "screen.mccinfo.events.overlay_hint" : "screen.mccinfo.events.overlay_none"),
				this.width / 2, Math.round((OVERLAY_TAB_Y + TAB_HEIGHT) * scale) + SPACING * 2, HINT_COLOR);
	}

	private static int hideIconX(MovableOverlay overlay, int x) {
		int handle = overlay.resizable() && overlay.resizableFromTop() ? RESIZE_HANDLE_SIZE : 0;
		return x + overlay.width() - OverlayStyle.PADDING - HIDE_ICON_SIZE - handle;
	}

	private static void extractTitleIcon(GuiGraphicsExtractor graphics, String[] icon, int iconX, int y) {
		int iconY = y + (OverlayStyle.TITLE_HEIGHT - HIDE_ICON_SIZE) / 2;
		for (int row = 0; row < HIDE_ICON_SIZE; row++) {
			for (int column = 0; column < HIDE_ICON_SIZE; column++) {
				if (icon[row].charAt(column) == '#') {
					graphics.fill(iconX + column, iconY + row, iconX + column + 1, iconY + row + 1, OverlayStyle.TEXT_COLOR);
				}
			}
		}
	}

	private static void extractResizeHandles(GuiGraphicsExtractor graphics, MovableOverlay overlay, int x, int y) {
		int right = x + overlay.width();
		int bottom = y + overlay.height();
		graphics.fill(x, bottom - RESIZE_HANDLE_SIZE, x + 1, bottom, OverlayStyle.TEXT_COLOR);
		graphics.fill(x, bottom - 1, x + RESIZE_HANDLE_SIZE, bottom, OverlayStyle.TEXT_COLOR);
		graphics.fill(right - 1, bottom - RESIZE_HANDLE_SIZE, right, bottom, OverlayStyle.TEXT_COLOR);
		graphics.fill(right - RESIZE_HANDLE_SIZE, bottom - 1, right, bottom, OverlayStyle.TEXT_COLOR);
		if (overlay.resizableFromTop()) {
			graphics.fill(x, y, x + 1, y + RESIZE_HANDLE_SIZE, OverlayStyle.TEXT_COLOR);
			graphics.fill(x, y, x + RESIZE_HANDLE_SIZE, y + 1, OverlayStyle.TEXT_COLOR);
			graphics.fill(right - 1, y, right, y + RESIZE_HANDLE_SIZE, OverlayStyle.TEXT_COLOR);
			graphics.fill(right - RESIZE_HANDLE_SIZE, y, right, y + 1, OverlayStyle.TEXT_COLOR);
		}
	}

	private static Corner cornerAt(MovableOverlay overlay, int x, int y, double mouseX, double mouseY) {
		if (!overlay.resizable()) {
			return null;
		}
		boolean left = mouseX < x + RESIZE_HANDLE_SIZE;
		boolean right = mouseX >= x + overlay.width() - RESIZE_HANDLE_SIZE;
		boolean top = overlay.resizableFromTop() && mouseY < y + RESIZE_HANDLE_SIZE;
		boolean bottom = mouseY >= y + overlay.height() - RESIZE_HANDLE_SIZE;
		if (!(left || right) || !(top || bottom)) {
			return null;
		}
		if (top) {
			return left ? Corner.TOP_LEFT : Corner.TOP_RIGHT;
		}
		return left ? Corner.BOTTOM_LEFT : Corner.BOTTOM_RIGHT;
	}

	private static boolean overHideButton(MovableOverlay overlay, int x, int y, double mouseX, double mouseY) {
		return mouseX >= hideIconX(overlay, x) - OverlayStyle.PADDING && mouseX < x + overlay.width()
				&& mouseY >= y && mouseY < y + OverlayStyle.TITLE_HEIGHT;
	}

	private static int textScaleStepAt(MovableOverlay overlay, int x, int y, double mouseX, double mouseY) {
		if (!overlay.textScalable() || mouseY < y || mouseY >= y + OverlayStyle.TITLE_HEIGHT) {
			return 0;
		}
		int plusLeft = hideIconX(overlay, x) - TITLE_ICON_STEP - OverlayStyle.PADDING;
		if (mouseX >= plusLeft && mouseX < plusLeft + TITLE_ICON_STEP) {
			return 1;
		}
		return mouseX >= plusLeft - TITLE_ICON_STEP && mouseX < plusLeft ? -1 : 0;
	}

	// Clicks: tabs, title buttons, resize handles, drag
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.tab == Tab.SETTINGS && this.pages.listening()) {
			this.pages.bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			return true;
		}
		double virtualX = event.x() / scale();
		double virtualY = event.y() / scale();
		Tab clickedTab = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT ? tabAt(virtualX, virtualY) : null;
		if (clickedTab != null) {
			if (clickedTab != this.tab) {
				NeuItems.clickSound();
				this.tab = clickedTab;
				this.pages.stopListening();
				this.dragged = null;
				this.resizeCorner = null;
				this.rebuildWidgets();
			}
			return true;
		}
		if (this.tab == Tab.SETTINGS) {
			EditBox search = this.settings.search();
			if (!this.settings.dropdownOpen() && this.settings.overSearch(virtualX, virtualY)) {
				this.setFocused(search);
				search.onClick(new MouseButtonEvent(virtualX, virtualY, event.buttonInfo()), doubleClick);
				return true;
			}
			this.setFocused(null);
			return event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.settings.mouseClicked(virtualX, virtualY);
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && overResetButton(event.x() / scale(), event.y() / scale())) {
			NeuItems.clickSound();
			resetPositions();
			return true;
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (MovableOverlay overlay : OVERLAYS) {
			int x = overlay.x(this.width);
			int y = overlay.y(this.height);
			if (overlay.enabled() && event.x() >= x && event.x() < x + overlay.width() && event.y() >= y && event.y() < y + overlay.height()) {
				Corner corner = cornerAt(overlay, x, y, event.x(), event.y());
				if (corner == null && overHideButton(overlay, x, y, event.x(), event.y())) {
					overlay.setHidden(!overlay.hidden());
					ModConfig.get().save();
					return true;
				}
				int textScaleStep = corner == null ? textScaleStepAt(overlay, x, y, event.x(), event.y()) : 0;
				if (textScaleStep != 0) {
					overlay.changeTextScale(textScaleStep);
					ModConfig.get().save();
					return true;
				}
				this.dragged = overlay;
				this.resizeCorner = corner;
				this.dragOffsetX = event.x() - x;
				this.dragOffsetY = event.y() - y;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.tab == Tab.SETTINGS && this.pages.listening()) {
			this.pages.bind(event.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			return true;
		}
		if (this.tab == Tab.SETTINGS && event.isEscape() && this.settings.closeDropdown()) {
			return true;
		}
		if (this.tab == Tab.SETTINGS && this.getFocused() instanceof EditBox box && this.pages.footerInputs().contains(box)
				&& (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && this.pages.submitFooter(box)) {
			return true;
		}
		return super.keyPressed(event);
	}

	// Move or resize the grabbed overlay
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.tab == Tab.SETTINGS && (this.settings.dragScrollbar(event.y() / scale()) || this.settings.dragControl(event.x() / scale()))) {
			return true;
		}
		if (this.dragged != null && this.resizeCorner != null) {
			int x = this.dragged.x(this.width);
			int y = this.dragged.y(this.height);
			int right = x + this.dragged.width();
			int bottom = y + this.dragged.height();
			int mouseX = OverlayStyle.clamp((int) Math.round(event.x()), this.width);
			int mouseY = OverlayStyle.clamp((int) Math.round(event.y()), this.height);
			this.dragged.setPosition(x, y);
			this.dragged.setSize(this.resizeCorner.left ? right - mouseX : mouseX - x, this.resizeCorner.top ? bottom - mouseY : mouseY - y);
			this.dragged.setPosition(this.resizeCorner.left ? right - this.dragged.width() : x, this.resizeCorner.top ? bottom - this.dragged.height() : y);
			return true;
		}
		if (this.dragged != null) {
			this.dragged.setPosition(
					OverlayStyle.clamp((int) Math.round(event.x() - this.dragOffsetX), this.width - this.dragged.width()),
					OverlayStyle.clamp((int) Math.round(event.y() - this.dragOffsetY), this.height - this.dragged.height()));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.settings.stopScrollbarDrag() | this.settings.stopControlDrag()) {
			return true;
		}
		if (this.dragged != null) {
			this.dragged = null;
			this.resizeCorner = null;
			ModConfig.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void removed() {
		ModConfig.get().save();
		this.settings.stopControlDrag();
		VoiceSettings.stopMicTest();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
