package leroidesafk.mccinfo.client.items;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class GrayscaleItems {
	// Items drawn in grey
	public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Mccinfo.id("pipeline/gui_grayscale"))
			.withFragmentShader(Mccinfo.id("core/gui_grayscale"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.build();

	// Items created while marking
	private static final Set<GuiItemRenderState> MARKED = Collections.newSetFromMap(new WeakHashMap<>());
	private static boolean marking;

	private GrayscaleItems() {
	}

	// Draw an item in grey
	static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		marking = true;
		try {
			graphics.item(stack, x, y);
		} finally {
			marking = false;
		}
	}

	public static void onCreated(GuiItemRenderState state) {
		if (marking) {
			MARKED.add(state);
		}
	}

	public static boolean isMarked(GuiItemRenderState state) {
		return MARKED.contains(state);
	}
}
