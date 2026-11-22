package leroidesafk.mccinfo.client.quests;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.items.SlotIndicators;
import leroidesafk.mccinfo.client.mixin.accessor.ContainerHoveredSlotAccessor;
import leroidesafk.mccinfo.client.mixin.accessor.GuiGraphicsExtractorAccessor;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Bar of games under the Island Rewards menu
public final class QuestLockBar {
	private record Box(int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	private record Cell(QuestLock.Game game, Box box) {
	}

	private record Row(QuestLock.Task task, Box box) {
	}

	// Bar and task list layout and colors
	private static final String TITLE = "ISLAND REWARDS";
	private static final int CELL = 20;
	private static final int LOGO = 16;
	private static final int PADDING = 2;
	private static final int GAP = 4;
	private static final int ROW_HEIGHT = 12;
	private static final int LIST_PADDING = 4;
	private static final int LOCK_SIZE = 8;
	private static final int LOCK_GAP = 3;
	private static final int BASE_ASCENT = 7;
	private static final int BAR_COLOR = 0xC0101010;
	private static final int HOVER_COLOR = 0x30FFFFFF;
	private static final float LOCK_SCALE = 1.5F;
	private static final int PARTLY_LOCKED = 9;
	private static final float LOCK_OVERHANG = 3.0F;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int TEXT_COLOR = 0xFFA8B0B0;
	private static final int LOCKED_TEXT_COLOR = 0xFFFFFFFF;
	private static final float SCREEN_PIXELS = 2.0F;

	// Hit areas of the last frame
	private static List<Cell> cells = List.of();
	private static List<Row> rows = List.of();
	private static Box bar;
	private static Box list;
	private static Screen shownOn;
	private static QuestLock.Game open;
	private static float scale = 1.0F;

	private QuestLockBar() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				open = null;
				ScreenEvents.afterForeground(screen).register((current, graphics, mouseX, mouseY, a) -> extract(current, graphics, mouseX, mouseY));
				ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !click(current, event));
			}
		});
	}

	private static boolean active(Screen screen) {
		return ModConfig.get().questLock && OverlayStyle.onMccIsland() && Permissions.has(Permissions.Perm.UTILS) && !QuestLock.games().isEmpty()
				&& screen.getTitle().getString().toUpperCase(Locale.ROOT).contains(TITLE);
	}

	// Bar of game logos, task list of the hovered game
	private static void extract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		bar = null;
		list = null;
		shownOn = null;
		cells = List.of();
		rows = List.of();
		if (!active(screen)) {
			open = null;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		scale = Math.min(1.0F, SCREEN_PIXELS / Math.max(1, client.getWindow().getGuiScale()));
		double scaledX = mouseX / scale;
		double scaledY = mouseY / scale;
		int screenWidth = (int) (screen.width / scale);
		int screenHeight = (int) (screen.height / scale);
		ContainerHoveredSlotAccessor menu = (ContainerHoveredSlotAccessor) screen;
		List<QuestLock.Game> games = QuestLock.games();
		int width = games.size() * CELL + PADDING * 2;
		int height = CELL + PADDING * 2;
		int x = screenWidth / 2 - width / 2;
		int y = Math.min((int) Math.ceil((menu.mccinfo$getTopPos() + menu.mccinfo$getImageHeight()) / scale) + GAP, screenHeight - height - GAP);
		bar = new Box(x, y, width, height);
		shownOn = screen;
		graphics.nextStratum();
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		OverlayStyle.fillRounded(graphics, x, y, width, height, BAR_COLOR);
		List<Cell> placed = new ArrayList<>();
		for (int i = 0; i < games.size(); i++) {
			QuestLock.Game game = games.get(i);
			Box box = new Box(x + PADDING + i * CELL, y + PADDING, CELL, CELL);
			placed.add(new Cell(game, box));
			if (box.contains(scaledX, scaledY)) {
				open = game;
			}
			if (game == open) {
				OverlayStyle.fillRounded(graphics, box.x(), box.y(), CELL, CELL, HOVER_COLOR);
			}
			extractLogo(graphics, font, game, box.x() + (CELL - LOGO) / 2, box.y() + (CELL - LOGO) / 2);
			int lock = QuestLock.locked(game) ? SlotIndicators.LOCKED : QuestLock.partlyLocked(game) ? PARTLY_LOCKED : -1;
			if (lock >= 0) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(box.x() + CELL - LOCK_SIZE * LOCK_SCALE + LOCK_OVERHANG, box.y() - LOCK_OVERHANG);
				graphics.pose().scale(LOCK_SCALE, LOCK_SCALE);
				SlotIndicators.icon(graphics, lock, 0, 0);
				graphics.pose().popMatrix();
			}
		}
		cells = placed;
		QuestLock.Task hovered = open != null ? extractList(graphics, font, screenWidth, scaledX, scaledY) : null;
		graphics.pose().popMatrix();
		boolean over = bar.contains(scaledX, scaledY) || list != null && list.contains(scaledX, scaledY);
		if (!over) {
			open = null;
		}
		if (over) {
			NeuItems.pointer(graphics);
		}
		if (hovered != null) {
			List<ClientTooltipComponent> lines = QuestLock.lore(hovered).stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
			((GuiGraphicsExtractorAccessor) graphics).mccinfo$setTooltipForNextFrameInternal(font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, true);
		}
	}

	private static void extractLogo(GuiGraphicsExtractor graphics, Font font, QuestLock.Game game, int x, int y) {
		QuestLock.Glyph glyph = QuestLock.logo(game);
		if (glyph == null || glyph.height() <= 0) {
			return;
		}
		float scale = LOGO / (float) glyph.height();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, QuestLock.icon(glyph), 0, glyph.ascent() - BASE_ASCENT, TITLE_COLOR, false);
		graphics.pose().popMatrix();
	}

	// Task list above the hovered game
	private static QuestLock.Task extractList(GuiGraphicsExtractor graphics, Font font, int screenWidth, double mouseX, double mouseY) {
		QuestLock.Game game = open;
		Cell cell = cells.stream().filter(placed -> placed.game() == game).findFirst().orElse(null);
		if (cell == null) {
			return null;
		}
		List<QuestLock.Task> tasks = QuestLock.tasks(game.id());
		Component title = Component.literal(game.name()).withStyle(ChatFormatting.BOLD);
		int inner = font.width(title);
		for (QuestLock.Task task : tasks) {
			inner = Math.max(inner, LOCK_SIZE + LOCK_GAP + font.width(QuestLock.label(task)));
		}
		int width = inner + LIST_PADDING * 2;
		int height = (tasks.size() + 1) * ROW_HEIGHT + LIST_PADDING * 2;
		int x = Math.clamp(cell.box().x() + CELL / 2 - width / 2, GAP, Math.max(GAP, screenWidth - width - GAP));
		int y = bar.y() - height;
		list = new Box(x, y, width, height);
		OverlayStyle.fillRounded(graphics, x, y, width, height, BAR_COLOR);
		graphics.text(font, title, x + LIST_PADDING, y + LIST_PADDING + (ROW_HEIGHT - font.lineHeight) / 2 + 1, TITLE_COLOR, false);
		List<Row> placed = new ArrayList<>();
		QuestLock.Task hovered = null;
		for (int i = 0; i < tasks.size(); i++) {
			QuestLock.Task task = tasks.get(i);
			Box box = new Box(x + 1, y + LIST_PADDING + (i + 1) * ROW_HEIGHT, width - 2, ROW_HEIGHT);
			placed.add(new Row(task, box));
			boolean locked = QuestLock.locked(task);
			if (box.contains(mouseX, mouseY)) {
				hovered = task;
				graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), HOVER_COLOR);
			}
			int left = x + LIST_PADDING;
			if (locked) {
				SlotIndicators.icon(graphics, SlotIndicators.LOCKED, left, box.y() + (ROW_HEIGHT - LOCK_SIZE) / 2);
			}
			graphics.text(font, QuestLock.label(task), left + LOCK_SIZE + LOCK_GAP, box.y() + (ROW_HEIGHT - font.lineHeight) / 2 + 1,
					locked ? LOCKED_TEXT_COLOR : TEXT_COLOR, false);
		}
		rows = placed;
		return hovered;
	}

	// Mouse over the bar or list, no slot under it
	public static boolean covers(Object screen, double mouseX, double mouseY) {
		if (screen != shownOn) {
			return false;
		}
		double x = mouseX / scale;
		double y = mouseY / scale;
		return bar != null && bar.contains(x, y) || list != null && list.contains(x, y);
	}

	// Click a game or a task to lock it
	private static boolean click(Screen screen, MouseButtonEvent event) {
		double x = event.x() / scale;
		double y = event.y() / scale;
		boolean overBar = bar != null && bar.contains(x, y);
		boolean overList = list != null && list.contains(x, y);
		if (!overBar && !overList) {
			return false;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}
		for (Cell cell : cells) {
			if (cell.box().contains(x, y)) {
				NeuItems.clickSound();
				QuestLock.toggle(cell.game());
				return true;
			}
		}
		for (Row row : rows) {
			if (row.box().contains(x, y)) {
				NeuItems.clickSound();
				QuestLock.toggle(row.task());
				return true;
			}
		}
		return true;
	}
}
