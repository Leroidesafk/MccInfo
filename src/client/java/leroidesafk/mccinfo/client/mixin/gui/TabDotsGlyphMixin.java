package leroidesafk.mccinfo.client.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import leroidesafk.mccinfo.client.gui.TabDots;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Hidden tab dots drawn as empty space
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public class TabDotsGlyphMixin {
	@WrapOperation(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;createGlyph(FFIILnet/minecraft/network/chat/Style;FF)Lnet/minecraft/client/gui/font/TextRenderable$Styled;"))
	private TextRenderable.Styled mccinfo$skipHiddenDots(BakedGlyph glyph, float x, float y, int color, int shadowColor, Style style, float boldOffset,
			float shadowOffset, Operation<TextRenderable.Styled> original) {
		return TabDots.hidden(style) ? null : original.call(glyph, x, y, color, shadowColor, style, boldOffset, shadowOffset);
	}
}
