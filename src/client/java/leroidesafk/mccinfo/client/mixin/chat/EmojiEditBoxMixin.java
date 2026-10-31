package leroidesafk.mccinfo.client.mixin.chat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import leroidesafk.mccinfo.client.chat.Emojis;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EditBox.class)
public class EmojiEditBoxMixin {
	// Emojis drawn in the chat field
	@ModifyReturnValue(method = "applyFormat", at = @At("RETURN"))
	private FormattedCharSequence mccinfo$showEmojis(FormattedCharSequence text) {
		return Emojis.decorate((EditBox) (Object) this, text);
	}
}
