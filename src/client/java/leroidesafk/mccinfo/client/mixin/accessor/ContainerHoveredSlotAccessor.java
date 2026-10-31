package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerHoveredSlotAccessor {
	// Slot under the mouse
	@Accessor("hoveredSlot")
	@Nullable Slot mccinfo$getHoveredSlot();

	// Position and size of the container window
	@Accessor("leftPos")
	int mccinfo$getLeftPos();

	@Accessor("imageWidth")
	int mccinfo$getImageWidth();

	@Accessor("topPos")
	int mccinfo$getTopPos();

	@Accessor("imageHeight")
	int mccinfo$getImageHeight();
}
