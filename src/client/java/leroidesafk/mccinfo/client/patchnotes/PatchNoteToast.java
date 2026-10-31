package leroidesafk.mccinfo.client.patchnotes;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.EventsScreen;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.update.UpdateToast;
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

import java.util.List;

// Small box announcing a new patch note, bottom left
public final class PatchNoteToast {
	private static final int WIDTH = 180;
	private static final int BORDER = 2;
	private static final int PADDING = 8;
	private static final int MARGIN = 4;
	private static final int CHAT_INPUT_HEIGHT = 14;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_GAP = 6;
	private static final int LINE_GAP = 2;
	private static final float SMALL_SCALE = 0.75F;
	private static final int SMALL_LINE = Math.round(9 * SMALL_SCALE);
	private static final float SLIDE_MS = 250.0F;
	private static final long VISIBLE_MS = 5000;

	private static final int COLOR_PANEL_BORDER = 0xFF232323;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF181818;
	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_META = 0xFF7A7A7A;
	private static final int COLOR_HEADER_LINE = 0xFF323232;
	private static final int HEADER_GAP = 4;
	private static final int MIN_HEADER_LINE = 12;

	private static PatchNotes.Note note;
	private static boolean dismissed;
	private static boolean recorded;
	private static boolean preview;
	private static long shownAt;
	private static float slide;
	private static long lastFrame = Util.getMillis();
	private static UpdateToast.Box panel;
	private static UpdateToast.Box view;

	private PatchNoteToast() {
	}

	public static void register() {
		HudElementRegistry.addLast(Mccinfo.id("patch_note_toast"), PatchNoteToast::extract);
		// Already shown: not again on the next join
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (recorded) {
				note = null;
			}
		});
	}

	// Preview = sent by an admin to check a patch note before it is published
	static void show(PatchNotes.Note shown, boolean asPreview) {
		note = shown;
		preview = asPreview;
		dismissed = false;
		recorded = false;
		slide = 0.0F;
	}

	// Waits for the update box to be gone
	private static boolean wanted(Minecraft client) {
		return note != null && !dismissed && client.level != null && client.player != null && MccIsland.isConnected()
				&& (preview || !Permissions.isOutdated() && !UpdateToast.wanted(client));
	}

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
		boolean wanted = wanted(client);
		// First frame shown: remember this version as read
		if (wanted && !recorded) {
			recorded = true;
			shownAt = now;
			if (!preview) {
				ModConfig config = ModConfig.get();
				config.lastPatchNote = note.version();
				config.save();
			}
		}
		// Hides itself after 5 seconds
		if (wanted && now - shownAt >= VISIBLE_MS) {
			dismissed = true;
			wanted = false;
		}
		slide = wanted ? Math.min(1.0F, slide + step) : Math.max(0.0F, slide - step);
		if (slide <= 0.0F || note == null) {
			panel = null;
			return;
		}
		Font font = client.font;
		int inner = WIDTH - BORDER * 2 - PADDING * 2;
		Component title = Component.literal(note.title().isEmpty() ? note.version() : note.title());
		int maxTitleWidth = inner - (HEADER_GAP + MIN_HEADER_LINE) * 2;
		List<FormattedCharSequence> titleLines = font.split(title, Math.round(maxTitleWidth / SMALL_SCALE));
		int titleHeight = titleLines.size() * SMALL_LINE + (titleLines.size() - 1) * LINE_GAP;
		Component version = Component.literal(note.version());
		Component date = note.date().isEmpty() ? null : Component.literal(PatchNotePanel.formatDate(note.date()));
		int height = BORDER * 2 + PADDING * 2 + titleHeight + LINE_GAP * 2 + SMALL_LINE + BUTTON_GAP + BUTTON_HEIGHT;

		int bottom = graphics.guiHeight() - CHAT_INPUT_HEIGHT - MARGIN;
		int y = Math.max(MARGIN, bottom - height);
		float eased = 1.0F - (1.0F - slide) * (1.0F - slide) * (1.0F - slide);
		int x = Math.round(-WIDTH + (WIDTH + MARGIN) * eased);
		panel = new UpdateToast.Box(x, y, WIDTH, height);

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
		// Title between two lines
		int widest = 0;
		for (FormattedCharSequence line : titleLines) {
			widest = Math.max(widest, Math.round(font.width(line) * SMALL_SCALE));
		}
		int lineLength = Math.max(0, (inner - widest) / 2 - HEADER_GAP);
		int lineY = top + titleHeight / 2;
		if (lineLength > 0) {
			graphics.fill(left, lineY, left + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(left + inner - lineLength, lineY, left + inner, lineY + 1, COLOR_HEADER_LINE);
		}
		for (FormattedCharSequence line : titleLines) {
			UpdateToast.small(graphics, font, line, left + (inner - font.width(line) * SMALL_SCALE) / 2.0F, top, COLOR_TITLE);
			top += SMALL_LINE + LINE_GAP;
		}
		top += LINE_GAP;
		// Version centered, date on the right
		int versionWidth = Math.round(font.width(version) * SMALL_SCALE);
		UpdateToast.small(graphics, font, version.getVisualOrderText(), left + (inner - versionWidth) / 2.0F, top, COLOR_META);
		if (date != null) {
			int dateWidth = Math.round(font.width(date) * SMALL_SCALE);
			UpdateToast.small(graphics, font, date.getVisualOrderText(), left + inner - dateWidth, top, COLOR_META);
		}
		top += SMALL_LINE + BUTTON_GAP;
		view = new UpdateToast.Box(left, top, inner, BUTTON_HEIGHT);
		UpdateToast.button(graphics, font, view, Component.translatable("screen.mccinfo.patchnotes.view"), UpdateToast.INSTALL, true, mouseX, mouseY);
		if (view.contains(mouseX, mouseY)) {
			NeuItems.pointer(graphics);
		}
	}

	public static boolean covers(double mouseX, double mouseY) {
		return panel != null && panel.contains(mouseX, mouseY);
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		if (!covers(event.x(), event.y())) {
			return false;
		}
		if (slide < 1.0F || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (view.contains(event.x(), event.y())) {
			NeuItems.clickSound();
			dismissed = true;
			Minecraft client = Minecraft.getInstance();
			// A preview not published yet is shown alone
			List<PatchNotes.Note> shown = preview && PatchNotes.all().stream().noneMatch(note -> note.version().equals(PatchNoteToast.note.version()))
					? List.of(PatchNoteToast.note) : PatchNotes.all();
			client.gui.setScreen(new PatchNoteScreen(new EventsScreen(), new PatchNotePanel(shown)));
		}
		return true;
	}
}
