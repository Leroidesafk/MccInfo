package leroidesafk.mccinfo.client.update;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

// Update box, bottom left, slides in when a new version is required
public final class UpdateToast {
	private static final int WIDTH = 180;
	private static final int BORDER = 2;
	private static final int PADDING = 8;
	private static final int MARGIN = 4;
	private static final int CHAT_INPUT_HEIGHT = 14;
	private static final int HEADER_HEIGHT = 14;
	private static final int HEADER_LABEL_GAP = 4;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_GAP = 6;
	private static final int LINE_GAP = 2;
	private static final float SMALL_SCALE = 0.75F;
	private static final int SMALL_LINE = Math.round(9 * SMALL_SCALE);
	private static final float SLIDE_MS = 250.0F;

	private static final int COLOR_PANEL_BORDER = 0xFF232323;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF181818;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF323232;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_META = 0xFF7A7A7A;
	private static final int COLOR_ERROR = 0xFFD05050;
	private static final int COLOR_SUCCESS = 0xFF55C46A;
	private static final int COLOR_BUTTON_TEXT = 0xFFFFFFFF;
	private static final int COLOR_DISABLED_TEXT = 0xFF8A8A8A;

	// Button colors, also used by other screens
	record Style(int fill, int border, int hoverFill, int hoverBorder) {
	}

	public static final Style INSTALL = new Style(0xFF1E5A2C, 0xFF3FAF55, 0xFF27723A, 0xFF5BD472);
	private static final Style LATER = new Style(0xFF5E1E1E, 0xFFC0393B, 0xFF782828, 0xFFE05557);
	private static final Style DISABLED = new Style(0xFF2A2A2A, 0xFF3A3A3A, 0xFF2A2A2A, 0xFF3A3A3A);

	public record Box(int x, int y, int width, int height) {
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	private static boolean dismissed;
	private static float slide;
	private static long lastFrame = Util.getMillis();
	private static Box panel;
	private static Box install;
	private static Box later;
	private static boolean installEnabled;

	private UpdateToast() {
	}

	public static void register() {
		HudElementRegistry.addLast(Mccinfo.id("update_toast"), UpdateToast::extract);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> dismissed = false);
	}

	static void show() {
		dismissed = false;
		slide = 0.0F;
	}

	// Closed with Later until the next join or a new version
	public static boolean wanted(Minecraft client) {
		return !dismissed && client.level != null && MccIsland.isConnected() && ModUpdater.requiredVersion() != null;
	}

	// Chat open: drawn by the chat screen instead, above it
	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) {
			draw(graphics);
		}
	}

	public static void extractOverChat(GuiGraphicsExtractor graphics) {
		graphics.nextStratum();
		draw(graphics);
	}

	private static void draw(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		long now = Util.getMillis();
		float step = (now - lastFrame) / SLIDE_MS;
		lastFrame = now;
		slide = wanted(client) ? Math.min(1.0F, slide + step) : Math.max(0.0F, slide - step);
		if (slide <= 0.0F || ModUpdater.requiredVersion() == null) {
			panel = null;
			return;
		}
		Font font = client.font;
		int inner = WIDTH - BORDER * 2 - PADDING * 2;
		List<FormattedCharSequence> status = new ArrayList<>();
		int statusColor = COLOR_META;
		Component message = statusMessage();
		if (message != null) {
			status.addAll(font.split(message, Math.round(inner / SMALL_SCALE)));
			statusColor = switch (ModUpdater.state()) {
				case FAILED -> COLOR_ERROR;
				case INSTALLED -> ModUpdater.restartFailed() ? COLOR_ERROR : COLOR_SUCCESS;
				default -> COLOR_META;
			};
		}
		int height = BORDER * 2 + PADDING * 2 + HEADER_HEIGHT + SMALL_LINE + LINE_GAP + BUTTON_HEIGHT + BUTTON_GAP;
		height += status.size() * (SMALL_LINE + LINE_GAP);

		int bottom = graphics.guiHeight() - CHAT_INPUT_HEIGHT - MARGIN;
		int y = Math.max(MARGIN, bottom - height);
		// Slides in from the left
		float eased = 1.0F - (1.0F - slide) * (1.0F - slide) * (1.0F - slide);
		int x = Math.round(-WIDTH + (WIDTH + MARGIN) * eased);
		panel = new Box(x, y, WIDTH, height);

		// Hover only with the chat open
		double mouseX = -1;
		double mouseY = -1;
		if (client.gui.screen() instanceof ChatScreen) {
			mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
			mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
		}

		graphics.fill(x, y, x + WIDTH, y + height, COLOR_PANEL_BORDER);
		graphics.fill(x + BORDER, y + BORDER, x + WIDTH - BORDER, y + height - BORDER, COLOR_PANEL_BACKGROUND);
		int left = x + BORDER + PADDING;
		int top = y + BORDER + PADDING;
		header(graphics, font, Component.translatable("screen.mccinfo.update.toast_title"), left, inner, top);
		top += HEADER_HEIGHT;
		Component versions = Component.translatable("screen.mccinfo.update.toast_versions", Permissions.MOD_VERSION, ModUpdater.requiredVersion());
		int versionsWidth = Math.round(font.width(versions) * SMALL_SCALE);
		small(graphics, font, versions.getVisualOrderText(), left + (inner - versionsWidth) / 2.0F, top, COLOR_TEXT);
		top += SMALL_LINE + LINE_GAP;
		for (FormattedCharSequence line : status) {
			int lineWidth = Math.round(font.width(line) * SMALL_SCALE);
			small(graphics, font, line, left + (inner - lineWidth) / 2.0F, top, statusColor);
			top += SMALL_LINE + LINE_GAP;
		}
		top += BUTTON_GAP - LINE_GAP;
		int buttonWidth = (inner - BUTTON_GAP) / 2;
		// Without autoupdate the button opens the Download Link
		boolean manual = !ModUpdater.allowed();
		installEnabled = manual ? ModUpdater.downloadUrl() != null
				: ModUpdater.available() && (ModUpdater.state() == ModUpdater.State.IDLE || ModUpdater.state() == ModUpdater.State.FAILED);
		install = new Box(left, top, buttonWidth, BUTTON_HEIGHT);
		later = new Box(left + inner - buttonWidth, top, buttonWidth, BUTTON_HEIGHT);
		String installKey = manual ? "screen.mccinfo.update.download" : switch (ModUpdater.state()) {
			case DOWNLOADING -> "screen.mccinfo.update.downloading_short";
			case INSTALLED -> "screen.mccinfo.update.installed_short";
			case FAILED -> "screen.mccinfo.update.retry";
			case IDLE -> "screen.mccinfo.update.install";
		};
		button(graphics, font, install, Component.translatable(installKey), installEnabled ? INSTALL : DISABLED, installEnabled, mouseX, mouseY);
		button(graphics, font, later, Component.translatable("screen.mccinfo.update.later"), LATER, true, mouseX, mouseY);
		if ((installEnabled && install.contains(mouseX, mouseY)) || later.contains(mouseX, mouseY)) {
			NeuItems.pointer(graphics);
		}
	}

	private static Component statusMessage() {
		if (!ModUpdater.allowed()) {
			return Component.translatable(ModUpdater.downloadUrl() != null ? "screen.mccinfo.update.manual" : "screen.mccinfo.update.no_link");
		}
		return switch (ModUpdater.state()) {
			case DOWNLOADING -> Component.translatable("screen.mccinfo.update.downloading");
			case INSTALLED -> Component.translatable(ModUpdater.restartFailed() ? "screen.mccinfo.update.restart_failed" : "screen.mccinfo.update.installed");
			case FAILED -> Component.translatable("screen.mccinfo.update.failed", ModUpdater.error());
			case IDLE -> ModUpdater.available() ? null
					: ModUpdater.older() ? Component.translatable("screen.mccinfo.update.older", ModUpdater.requiredVersion(), Permissions.MOD_VERSION)
					: Component.translatable("screen.mccinfo.update.unavailable");
		};
	}

	// Drawing helpers, shared with other boxes
	public static void button(GuiGraphicsExtractor graphics, Font font, Box box, Component label, Style style, boolean enabled, double mouseX, double mouseY) {
		boolean hovered = enabled && box.contains(mouseX, mouseY);
		graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), hovered ? style.hoverBorder() : style.border());
		graphics.fill(box.x() + 1, box.y() + 1, box.x() + box.width() - 1, box.y() + box.height() - 1, hovered ? style.hoverFill() : style.fill());
		int labelWidth = font.width(label);
		graphics.text(font, label, box.x() + (box.width() - labelWidth) / 2, box.y() + (box.height() - 8) / 2, enabled ? COLOR_BUTTON_TEXT : COLOR_DISABLED_TEXT, true);
	}

	public static void header(GuiGraphicsExtractor graphics, Font font, Component label, int x, int width, int y) {
		int textWidth = Math.round(font.width(label) * SMALL_SCALE);
		int lineLength = Math.max(0, (width - textWidth - HEADER_LABEL_GAP * 2) / 2);
		int textY = y + (HEADER_HEIGHT - SMALL_LINE) / 2;
		int lineY = textY + SMALL_LINE / 2;
		if (lineLength > 0) {
			graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_HEADER_LINE);
		}
		small(graphics, font, label.getVisualOrderText(), x + lineLength + HEADER_LABEL_GAP, textY, COLOR_HEADER_TEXT);
	}

	public static void small(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, float x, float y, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(SMALL_SCALE, SMALL_SCALE);
		graphics.text(font, text, 0, 0, color, true);
		graphics.pose().popMatrix();
	}

	public static boolean covers(double mouseX, double mouseY) {
		return panel != null && panel.contains(mouseX, mouseY);
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (!covers(event.x(), event.y())) {
			return false;
		}
		// Clicks only once fully shown
		if (slide < 1.0F || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (installEnabled && install.contains(event.x(), event.y())) {
			NeuItems.clickSound();
			if (ModUpdater.allowed()) {
				ModUpdater.install();
			} else if (ModUpdater.downloadUrl() != null) {
				Minecraft client = Minecraft.getInstance();
				net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), ModUpdater.downloadUrl());
			}
		} else if (later.contains(event.x(), event.y())) {
			NeuItems.clickSound();
			dismissed = true;
		}
		return true;
	}
}
