package leroidesafk.mccinfo.client.mixin.accessor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	// Pixels of the sprite as loaded
	@Accessor("originalImage")
	NativeImage mccinfo$getOriginalImage();
}
