package leroidesafk.mccinfo.client.widget;

import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class WidgetWheelScreen extends Screen implements MenuBackdrop.Host {
	// Sized like the MCCI emote wheel
	private static final int CELL = 85;
	private static final int GAP = 9;
	private static final int GRID = CELL * 3 + GAP * 2;
	private static final int PER_PAGE = 8;
	private static final int ICON_TEXTURE = 16;
	private static final float ICON_SCALE = 3.0F;
	private static final float TEXT_SCALE = 1.5F;
	private static final int TITLE_HEIGHT = 22;
	private static final int TITLE_PADDING = 7;
	private static final int TITLE_GAP = 15;
	private static final int ARROW_SIZE = 22;
	private static final int ARROW_GAP = 15;
	private static final int COLOR_CELL = 0xD8161616;
	// Accent hover
	private static final int HOVER_BORDER = 1;
	private static final float HOVER_TINT = 0.18F;
	private static final int TOOLTIP_PADDING = 4;
	private static final int TOOLTIP_OFFSET = 8;
	private static final int COLOR_TOOLTIP = 0xF0101010;
	private static final int COLOR_TOOLTIP_BORDER = 0xFF3A3A3A;
	private static final int COLOR_CELL_EMPTY = 0xB0161616;
	private static final int COLOR_BOX = 0xE0161616;
	private static final int COLOR_BOX_HOVER = 0xF0303030;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_EMPTY_TEXT = 0xFF8C8C8C;

	private float scale = 1.0F;
	private int gridLeft;
	private int gridTop;
	private int page;

	public WidgetWheelScreen() {
		super(Component.translatable("screen.mccinfo.widgets.title"));
	}

	@Override
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		this.gridLeft = (virtualWidth - GRID) / 2;
		this.gridTop = (virtualHeight - GRID) / 2 + (TITLE_HEIGHT + TITLE_GAP) / 2;
		this.page = Math.clamp(this.page, 0, pages() - 1);
	}

	private int pages() {
		return Math.max(1, (Widgets.active().size() + PER_PAGE - 1) / PER_PAGE);
	}

	// 8 cells around an empty center
	private int cellX(int slot) {
		int position = slot < 4 ? slot : slot + 1;
		return this.gridLeft + position % 3 * (CELL + GAP);
	}

	private int cellY(int slot) {
		int position = slot < 4 ? slot : slot + 1;
		return this.gridTop + position / 3 * (CELL + GAP);
	}

	private int slotAt(double x, double y) {
		for (int slot = 0; slot < PER_PAGE; slot++) {
			if (x >= cellX(slot) && x < cellX(slot) + CELL && y >= cellY(slot) && y < cellY(slot) + CELL) {
				return slot;
			}
		}
		return -1;
	}

	private Widgets.Widget widgetAt(int slot) {
		List<Widgets.Widget> widgets = Widgets.active();
		int index = this.page * PER_PAGE + slot;
		return slot >= 0 && index < widgets.size() ? widgets.get(index) : null;
	}

	private int arrowY() {
		return this.gridTop + (GRID - ARROW_SIZE) / 2;
	}

	private int leftArrowX() {
		return this.gridLeft - ARROW_GAP - ARROW_SIZE;
	}

	private int rightArrowX() {
		return this.gridLeft + GRID + ARROW_GAP;
	}

	private boolean hasPrevious() {
		return this.page > 0;
	}

	private boolean hasNext() {
		return this.page < pages() - 1;
	}

	private static boolean inside(double x, double y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	// No background, the game stays visible
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.page = Math.clamp(this.page, 0, pages() - 1);
		int virtualX = (int) (mouseX / this.scale);
		int virtualY = (int) (mouseY / this.scale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);

		// Title, with the page number if there are several
		Component title = pages() > 1 ? Component.translatable("screen.mccinfo.widgets.title_page", this.page + 1) : this.title;
		int titleWidth = Math.round(this.font.width(title) * TEXT_SCALE) + TITLE_PADDING * 2;
		int titleX = this.gridLeft + (GRID - titleWidth) / 2;
		int titleY = this.gridTop - TITLE_GAP - TITLE_HEIGHT;
		graphics.fill(titleX, titleY, titleX + titleWidth, titleY + TITLE_HEIGHT, COLOR_BOX);
		scaledText(graphics, title, titleX + TITLE_PADDING, titleY + (TITLE_HEIGHT - this.font.lineHeight * TEXT_SCALE) / 2.0F + 1.0F);

		int hovered = slotAt(virtualX, virtualY);
		Widgets.Widget tooltip = null;
		// Cells of the current page
		for (int slot = 0; slot < PER_PAGE; slot++) {
			int x = cellX(slot);
			int y = cellY(slot);
			Widgets.Widget widget = widgetAt(slot);
			if (widget == null) {
				graphics.fill(x, y, x + CELL, y + CELL, COLOR_CELL_EMPTY);
				continue;
			}
			boolean over = slot == hovered;
			// Accent border and tint on hover
			if (over) {
				graphics.fill(x - HOVER_BORDER, y - HOVER_BORDER, x + CELL + HOVER_BORDER, y + CELL + HOVER_BORDER, SettingsPanel.ACCENT);
			}
			graphics.fill(x, y, x + CELL, y + CELL, over ? hoverColor() : COLOR_CELL);
			float size = ICON_TEXTURE * ICON_SCALE;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + (CELL - size) / 2.0F, y + (CELL - size) / 2.0F);
			graphics.pose().scale(ICON_SCALE, ICON_SCALE);
			graphics.blit(RenderPipelines.GUI_TEXTURED, widget.icon(), 0, 0, 0.0F, 0.0F, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE);
			graphics.pose().popMatrix();
			if (over) {
				tooltip = widget;
				NeuItems.pointer(graphics);
			}
		}
		if (Widgets.active().isEmpty()) {
			Component empty = Component.translatable("screen.mccinfo.widgets.empty");
			graphics.text(this.font, empty, this.gridLeft + (GRID - this.font.width(empty)) / 2, this.gridTop + (GRID - this.font.lineHeight) / 2 + 1,
					COLOR_EMPTY_TEXT, true);
		}

		// Arrows only when there is another page
		if (hasPrevious()) {
			arrow(graphics, "<", leftArrowX(), virtualX, virtualY);
		}
		if (hasNext()) {
			arrow(graphics, ">", rightArrowX(), virtualX, virtualY);
		}
		if (tooltip != null) {
			nameTooltip(graphics, Widgets.name(tooltip.id()), virtualX, virtualY);
		}
		graphics.pose().popMatrix();
	}

	private static int hoverColor() {
		int accent = SettingsPanel.ACCENT;
		int red = Math.round((COLOR_CELL >> 16 & 0xFF) * (1 - HOVER_TINT) + (accent >> 16 & 0xFF) * HOVER_TINT);
		int green = Math.round((COLOR_CELL >> 8 & 0xFF) * (1 - HOVER_TINT) + (accent >> 8 & 0xFF) * HOVER_TINT);
		int blue = Math.round((COLOR_CELL & 0xFF) * (1 - HOVER_TINT) + (accent & 0xFF) * HOVER_TINT);
		return COLOR_CELL & 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private void nameTooltip(GuiGraphicsExtractor graphics, Component name, int mouseX, int mouseY) {
		int width = Math.round(this.font.width(name) * TEXT_SCALE) + TOOLTIP_PADDING * 2;
		int height = Math.round(this.font.lineHeight * TEXT_SCALE) + TOOLTIP_PADDING * 2;
		int screenWidth = (int) (this.width / this.scale);
		int x = mouseX + TOOLTIP_OFFSET;
		int y = mouseY - height - TOOLTIP_OFFSET / 2;
		if (x + width > screenWidth) {
			x = mouseX - TOOLTIP_OFFSET - width;
		}
		y = Math.max(0, y);
		graphics.nextStratum();
		graphics.fill(x, y, x + width, y + height, COLOR_TOOLTIP_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_TOOLTIP);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + TOOLTIP_PADDING, y + TOOLTIP_PADDING + 1);
		graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
		graphics.text(this.font, name, 0, 0, SettingsPanel.ACCENT, true);
		graphics.pose().popMatrix();
	}

	private void arrow(GuiGraphicsExtractor graphics, String label, int x, int mouseX, int mouseY) {
		int y = arrowY();
		boolean over = inside(mouseX, mouseY, x, y, ARROW_SIZE, ARROW_SIZE);
		graphics.fill(x, y, x + ARROW_SIZE, y + ARROW_SIZE, over ? COLOR_BOX_HOVER : COLOR_BOX);
		scaledText(graphics, Component.literal(label), x + (ARROW_SIZE - this.font.width(label) * TEXT_SCALE) / 2.0F + 1.0F,
				y + (ARROW_SIZE - this.font.lineHeight * TEXT_SCALE) / 2.0F + 1.0F);
		if (over) {
			NeuItems.pointer(graphics);
		}
	}

	private void scaledText(GuiGraphicsExtractor graphics, Component text, float x, float y) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
		graphics.text(this.font, text, 0, 0, COLOR_TEXT, true);
		graphics.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		if (hasPrevious() && inside(x, y, leftArrowX(), arrowY(), ARROW_SIZE, ARROW_SIZE)) {
			NeuItems.clickSound();
			this.page--;
			return true;
		}
		if (hasNext() && inside(x, y, rightArrowX(), arrowY(), ARROW_SIZE, ARROW_SIZE)) {
			NeuItems.clickSound();
			this.page++;
			return true;
		}
		// Click on a widget opens it over the wheel
		Widgets.Widget widget = widgetAt(slotAt(x, y));
		if (widget != null) {
			NeuItems.clickSound();
			widget.open().accept(this);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	// Wheel changes page
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY > 0 && hasPrevious()) {
			this.page--;
			return true;
		}
		if (scrollY < 0 && hasNext()) {
			this.page++;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
