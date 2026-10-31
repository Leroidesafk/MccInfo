package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
	// Everything drawn this frame
	@Accessor("guiRenderState")
	GuiRenderState mccinfo$getGuiRenderState();

	// Tooltip with its own background and placement
	@Invoker("setTooltipForNextFrameInternal")
	void mccinfo$setTooltipForNextFrameInternal(net.minecraft.client.gui.Font font, List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components,
			int x, int y, net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner, net.minecraft.resources.Identifier background, boolean focused);
}
