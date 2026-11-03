package leroidesafk.mccinfo.client.emote;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.mixin.accessor.RenderTypeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class EmoteShockwave {
	// Wave life and size
	static final String EFFECT = "shockwave";
	private static final double LIFE_SECONDS = 1.1;
	private static final double MAX_RADIUS = 11.0;
	private static final double RING_WIDTH = 1.8;
	private static final Identifier SCENE_ID = Mccinfo.id("emote/shockwave_scene");

	// Wave render type
	private static final RenderPipeline PIPELINE = RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withLocation(Mccinfo.id("pipeline/emote_shockwave"))
			.withVertexShader(Mccinfo.id("core/emote_shockwave"))
			.withFragmentShader(Mccinfo.id("core/emote_shockwave"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build();
	private static final RenderType TYPE = RenderTypeAccessor.mccinfo$create("mccinfo_emote_shockwave",
			RenderSetup.builder(PIPELINE).withTexture("Sampler0", SCENE_ID).sortOnUpload().createRenderSetup());

	private static Scene scene;
	private static boolean pending;

	private final Vec3 center;
	private final long bornNanos = System.nanoTime();

	EmoteShockwave(Vec3 center) {
		this.center = center;
	}

	boolean done() {
		return age() >= 1.0;
	}

	private double age() {
		return (System.nanoTime() - this.bornNanos) / 1.0E9 / LIFE_SECONDS;
	}

	// World image copied before the waves
	static boolean prepare() {
		RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTexture source = target.getColorTexture();
		if (source == null) {
			return false;
		}
		if (scene == null) {
			scene = new Scene();
			Minecraft.getInstance().getTextureManager().register(SCENE_ID, scene);
		}
		scene.fit(source);
		pending = true;
		return true;
	}

	static void copyScene() {
		if (!pending || scene == null) {
			return;
		}
		pending = false;
		GpuTexture source = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
		if (source != null) {
			scene.copy(source);
		}
	}

	// Growing and fading ring
	void submit(SubmitNodeCollector collector, PoseStack poseStack, CameraRenderState camera) {
		double age = Math.min(1.0, age());
		double grow = 1.0 - Math.pow(1.0 - age, 2.5);
		double radius = Math.max(0.05, MAX_RADIUS * grow);
		float strength = (float) (Math.pow(1.0 - age, 1.3) * Math.min(1.0, age / 0.06));
		if (strength <= 0.01F) {
			return;
		}
		Vec3 relative = this.center.subtract(camera.pos);
		double distance = relative.length();
		if (distance < 1.0E-3) {
			return;
		}
		double shift = Math.min(radius * 0.8, distance * 0.5);
		double scale = (distance - shift) / distance;
		Vec3 plane = relative.scale(scale);
		float half = (float) (radius * scale);
		float width = (float) Math.min(0.95, RING_WIDTH / radius);
		Vector3f right = camera.orientation.transform(new Vector3f(half, 0.0F, 0.0F));
		Vector3f up = camera.orientation.transform(new Vector3f(0.0F, half, 0.0F));
		Vector3f middle = new Vector3f((float) plane.x, (float) plane.y, (float) plane.z);
		int color = Math.round(strength * 255.0F) << 24 | Math.round((float) age * 255.0F) << 16 | Math.round(width * 255.0F) << 8;
		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
			corner(buffer, pose, middle, right, up, -1.0F, -1.0F, color);
			corner(buffer, pose, middle, right, up, 1.0F, -1.0F, color);
			corner(buffer, pose, middle, right, up, 1.0F, 1.0F, color);
			corner(buffer, pose, middle, right, up, -1.0F, 1.0F, color);
		});
	}

	private static void corner(VertexConsumer buffer, PoseStack.Pose pose, Vector3f middle, Vector3f right, Vector3f up, float u, float v, int color) {
		buffer.addVertex(pose, middle.x + right.x * u + up.x * v, middle.y + right.y * u + up.y * v, middle.z + right.z * u + up.z * v)
				.setUv(u, v).setColor(color);
	}

	// Copy of the world image
	private static final class Scene extends AbstractTexture {
		Scene() {
			this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR,
					FilterMode.LINEAR, false);
		}

		void fit(GpuTexture source) {
			int width = source.getWidth(0);
			int height = source.getHeight(0);
			if (this.texture != null && !this.texture.isClosed() && this.texture.getWidth(0) == width && this.texture.getHeight(0) == height
					&& this.texture.getFormat() == source.getFormat()) {
				return;
			}
			this.releaseTextures();
			GpuDevice device = RenderSystem.getDevice();
			this.texture = device.createTexture("mccinfo emote shockwave scene", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					source.getFormat(), width, height, 1, 1);
			this.textureView = device.createTextureView(this.texture);
		}

		void copy(GpuTexture source) {
			if (this.texture == null || this.texture.getWidth(0) != source.getWidth(0) || this.texture.getHeight(0) != source.getHeight(0)) {
				return;
			}
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, this.texture, 0, 0, 0, 0, 0, source.getWidth(0),
					source.getHeight(0));
		}
	}
}
