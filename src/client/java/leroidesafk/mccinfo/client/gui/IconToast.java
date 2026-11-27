package leroidesafk.mccinfo.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// Toast with an item icon
public final class IconToast implements Toast {
	private static final int MIN_WIDTH = 160;
	private static final int MAX_WIDTH = 300;
	private static final int TEXT_RIGHT = 8;
	private static final int HEIGHT = 26;
	private static final int BACKGROUND_COLOR = 0xC0111111;
	private static final int TEXT_LEFT = 26;
	private static final int TITLE_COLOR = 0xFFFFD24A;
	private static final long DISPLAY_MILLIS = 8000L;

	private final ItemStack icon;
	private final Component title;
	private final Component line;
	private final int width;
	private Visibility wantedVisibility = Visibility.HIDE;

	public IconToast(ItemStack icon, Component title, Component line) {
		this.icon = icon;
		this.title = title;
		this.line = line;
		Font font = Minecraft.getInstance().font;
		int text = Math.max(font.width(title), font.width(line));
		this.width = Math.clamp(TEXT_LEFT + text + TEXT_RIGHT, MIN_WIDTH, MAX_WIDTH);
	}

	@Override
	public int width() {
		return this.width;
	}

	@Override
	public int height() {
		return HEIGHT;
	}

	@Override
	public Visibility getWantedVisibility() {
		return this.wantedVisibility;
	}

	@Override
	public void update(ToastManager manager, long visibleMillis) {
		this.wantedVisibility = visibleMillis > DISPLAY_MILLIS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleMillis) {
		OverlayStyle.fillRounded(graphics, 1, 2, this.width - 3, HEIGHT - 2, BACKGROUND_COLOR);
		if (!this.icon.isEmpty()) {
			graphics.fakeItem(this.icon, 5, 6);
		}
		graphics.text(font, this.title, TEXT_LEFT, 6, TITLE_COLOR);
		graphics.text(font, font.substrByWidth(this.line, this.width - TEXT_LEFT - TEXT_RIGHT).getString(), TEXT_LEFT, 15, 0xFFFFFFFF);
	}
}
