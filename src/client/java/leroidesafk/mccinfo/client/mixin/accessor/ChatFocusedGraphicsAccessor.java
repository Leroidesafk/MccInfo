package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public interface ChatFocusedGraphicsAccessor {
	// Graphics used to draw the open chat
	@Accessor("graphics")
	GuiGraphicsExtractor mccinfo$getGraphics();
}
