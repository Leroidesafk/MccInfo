package leroidesafk.mccinfo.client.mixin.chat;

import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class ChatItemAreaMixin {
	@Shadow
	protected abstract void markSize(float left, float top, float right, float bottom);

	// Shared item slot hoverable on its whole width
	@Inject(method = "addEmptyGlyph", at = @At("HEAD"))
	private void mccinfo$itemArea(EmptyArea empty, CallbackInfo callback) {
		if (empty.style().getHoverEvent() instanceof HoverEvent.ShowItem) {
			markSize(empty.activeLeft(), empty.activeTop(), empty.activeRight(), empty.activeBottom());
		}
	}
}
