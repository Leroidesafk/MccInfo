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
import java.util.Map;
import java.util.WeakHashMap;

public final class GrayscaleItems {
	// Items drawn in grey
	public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Mccinfo.id("pipeline/gui_grayscale"))
			.withFragmentShader(Mccinfo.id("core/gui_grayscale"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.build();
	// Items tinted in red
	public static final RenderPipeline RED_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(Mccinfo.id("pipeline/gui_red_tint"))
			.withFragmentShader(Mccinfo.id("core/gui_red_tint"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.build();

	// Items created while marking, with their pipeline
	private static final Map<GuiItemRenderState, RenderPipeline> MARKED = Collections.synchronizedMap(new WeakHashMap<>());
	private static RenderPipeline marking;

	private GrayscaleItems() {
	}

	// Draw an item in grey
	static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		marking = PIPELINE;
		try {
			graphics.item(stack, x, y);
		} finally {
			marking = null;
		}
	}

	// Red tint until end()
	public static void beginRed() {
		marking = RED_PIPELINE;
	}

	public static void end() {
		marking = null;
	}

	public static void onCreated(GuiItemRenderState state) {
		if (marking != null) {
			MARKED.put(state, marking);
		}
	}

	public static RenderPipeline pipeline(GuiItemRenderState state, RenderPipeline original) {
		RenderPipeline pipeline = MARKED.get(state);
		return pipeline != null ? pipeline : original;
	}
}
