package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {
	// New render type from a name and its setup
	@Invoker("create")
	static RenderType mccinfo$create(String name, RenderSetup setup) {
		throw new AssertionError();
	}
}
