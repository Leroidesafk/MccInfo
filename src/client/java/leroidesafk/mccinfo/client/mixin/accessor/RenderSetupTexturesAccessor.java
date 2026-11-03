package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RenderSetup.class)
public interface RenderSetupTexturesAccessor {
	// Textures of a render setup
	@Accessor("textures")
	Map<String, Object> mccinfo$getTextures();
}
