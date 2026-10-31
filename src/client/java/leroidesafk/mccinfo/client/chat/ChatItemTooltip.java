package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.mixin.accessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

public final class ChatItemTooltip {
	private static final String MARKER = "mccinfo_chat_item";
	private static final int BOX_SIZE = 20;
	private static final int ITEM_OFFSET = 2;
	private static final int TEXT_GAP = 4;
	private static final int SECOND_LINE = 11;
	private static final int BOTTOM_MARGIN = 2;

	private ChatItemTooltip() {
	}

	static ItemStack mark(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(MARKER, true));
		return stack;
	}

	public static boolean show(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
		// Only items shared in the chat
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.copyTag().contains(MARKER)) {
			return false;
		}
		List<Component> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
		if (lines.isEmpty()) {
			return false;
		}
		// Header, image, then the other lines
		List<ClientTooltipComponent> components = new ArrayList<>();
		components.add(new Header(stack, lines.get(0).getVisualOrderText(), lines.size() > 1 ? lines.get(1).getVisualOrderText() : null));
		stack.getTooltipImage().ifPresent(image -> components.add(ClientTooltipComponent.create(image)));
		for (int i = 2; i < lines.size(); i++) {
			components.add(ClientTooltipComponent.create(lines.get(i).getVisualOrderText()));
		}
		((GuiGraphicsExtractorAccessor) graphics).mccinfo$setTooltipForNextFrameInternal(font, components, x, y,
				DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE), false);
		return true;
	}

	// Item box beside the name and type
	private record Header(ItemStack stack, FormattedCharSequence name, FormattedCharSequence second) implements ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			return BOX_SIZE + BOTTOM_MARGIN;
		}

		@Override
		public int getWidth(Font font) {
			int text = Math.max(font.width(this.name), this.second == null ? 0 : font.width(this.second));
			return BOX_SIZE + TEXT_GAP + text;
		}

		@Override
		public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
			int textX = x + BOX_SIZE + TEXT_GAP;
			graphics.text(font, this.name, textX, y + 1, 0xFFFFFFFF, true);
			if (this.second != null) {
				graphics.text(font, this.second, textX, y + 1 + SECOND_LINE, 0xFFFFFFFF, true);
			}
		}

		@Override
		public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
			OverlayStyle.fillRounded(graphics, x, y, BOX_SIZE, BOX_SIZE, NeuItems.BOX_COLOR);
			graphics.item(this.stack, x + ITEM_OFFSET, y + ITEM_OFFSET);
		}
	}
}
