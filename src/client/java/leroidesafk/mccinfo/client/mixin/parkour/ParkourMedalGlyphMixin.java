package leroidesafk.mccinfo.client.mixin.parkour;

import leroidesafk.mccinfo.client.parkour.ParkourSplits;
import leroidesafk.mccinfo.client.quests.QuestLock;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BitmapProvider.Definition.class)
public class ParkourMedalGlyphMixin {
	// MCC font glyphs: medal for the splits, icon sizes for the Quest Lock
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mccinfo$medalGlyph(Identifier file, int height, int ascent, int[][] codepointGrid, CallbackInfo info) {
		ParkourSplits.fontGlyph(file, codepointGrid);
		QuestLock.fontGlyph(file, height, ascent, codepointGrid);
	}
}
