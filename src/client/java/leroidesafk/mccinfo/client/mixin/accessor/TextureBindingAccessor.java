package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface TextureBindingAccessor {
	// Texture of a binding
	@Accessor("location")
	Identifier mccinfo$getLocation();
}
