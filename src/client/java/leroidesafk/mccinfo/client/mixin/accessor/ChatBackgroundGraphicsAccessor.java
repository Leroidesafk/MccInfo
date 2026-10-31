package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public interface ChatBackgroundGraphicsAccessor {
	// Graphics used to draw the chat background
	@Accessor("graphics")
	GuiGraphicsExtractor mccinfo$getGraphics();
}
