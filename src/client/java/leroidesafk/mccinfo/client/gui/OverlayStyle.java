package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.MccIsland;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

public final class OverlayStyle {
	// Box frame sizes and colors
	public static final int PADDING = 4;
	public static final int FONT_HEIGHT = 7;
	public static final int TITLE_HEIGHT = FONT_HEIGHT + PADDING * 2;

	private static final int TITLE_ICON_SIZE = 8;
	private static final long ICON_FRAME_MILLIS = 150L;

	static final int BACKGROUND_COLOR = 0x80000000;
	public static final int TEXT_COLOR = 0xFFFFFFFF;

	private static final FontDescription MCC_HUD_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcc", "hud"));

	private OverlayStyle() {
	}

	// HUD boxes drawn after the Tab list
	public static void registerHud(Identifier id, HudElement element) {
		HudElementRegistry.attachElementAfter(VanillaHudElements.PLAYER_LIST, id, element);
	}

	public static void extractFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int titleColor, String title, float alpha) {
		extractFrame(graphics, x, y, width, height, titleColor, (Icon) null, title, alpha);
	}

	// Title icon, animated if the texture holds several frames
	public record Icon(Identifier texture, int size, int frames) {
		public static Icon of(Identifier texture) {
			return new Icon(texture, TITLE_ICON_SIZE, 1);
		}
	}

	public static void extractFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int titleColor, Identifier icon, String title, float alpha) {
		extractFrame(graphics, x, y, width, height, titleColor, icon == null ? null : Icon.of(icon), title, alpha);
	}

	// Box with its title bar
	public static void extractFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int titleColor, Icon icon, String title, float alpha) {
		Font font = Minecraft.getInstance().font;
		fillRounded(graphics, x, y, width, height, withAlpha(BACKGROUND_COLOR, alpha));
		fillRounded(graphics, x, y, width, TITLE_HEIGHT, withAlpha(titleColor, alpha));
		Component text = hudText(title);
		if (icon != null) {
			int frame = (int) (System.currentTimeMillis() / ICON_FRAME_MILLIS % icon.frames());
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), x + PADDING, y + (TITLE_HEIGHT - TITLE_ICON_SIZE) / 2, 0.0F, frame * icon.size(),
					TITLE_ICON_SIZE, TITLE_ICON_SIZE, icon.size(), icon.size(), icon.size(), icon.size() * icon.frames(), withAlpha(TEXT_COLOR, alpha));
		}
		graphics.text(font, text, x + (width - font.width(text)) / 2, y + PADDING, withAlpha(TEXT_COLOR, alpha), false);
	}

	public static int withAlpha(int color, float alpha) {
		return Math.round((color >>> 24) * alpha) << 24 | color & 0xFFFFFF;
	}

	public static boolean onMccIsland() {
		return MccIsland.isConnected();
	}

	// MCC Island HUD font when on the server
	public static MutableComponent hudText(String text) {
		MutableComponent component = Component.literal(text);
		if (onMccIsland()) {
			component.withStyle(style -> style.withFont(MCC_HUD_FONT));
		}
		return component;
	}

	// Rectangle with cut corners
	public static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + 1, y, x + width - 1, y + height, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	public static int clamp(int value, int max) {
		return Math.max(0, Math.min(value, max));
	}
}
