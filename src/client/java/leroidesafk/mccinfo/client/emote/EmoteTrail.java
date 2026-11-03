package leroidesafk.mccinfo.client.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class EmoteTrail {
	// Band life, look and size
	private static final long LIFE_NANOS = 400_000_000L;
	private static final long STEP_NANOS = 10_000_000L;
	private static final float ALPHA = 0.25F;
	private static final double DRIFT_SPEED = 0.8;
	private static final float EDGE_LENGTH = 0.6F;
	private static final int EDGES = 2;
	private static final Identifier TEXTURE = Mccinfo.id("textures/emote/trail.png");
	private static final Map<EmoteModel.Bone, List<Vector3f[]>> EDGE_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	private record Point(Vec3[] tips, Vec3[] tops, Vec3 back, long nanos) {
	}

	private final ArrayDeque<Point> points = new ArrayDeque<>();

	// Outer edges of the limb
	static List<Vector3f[]> edges(EmoteModel.Bone bone) {
		return EDGE_CACHE.computeIfAbsent(bone, key -> {
			Vector3f min = new Vector3f(Float.MAX_VALUE);
			Vector3f max = new Vector3f(-Float.MAX_VALUE);
			for (EmoteModel.Face face : key.faces) {
				for (Vector3f corner : face.corners()) {
					min.min(corner);
					max.max(corner);
				}
			}
			if (min.x > max.x) {
				return List.of();
			}
			Vector3f size = new Vector3f(max).sub(min);
			int axis = size.x >= size.y && size.x >= size.z ? 0 : size.y >= size.z ? 1 : 2;
			boolean tipAtMax = Math.abs(max.get(axis)) > Math.abs(min.get(axis));
			float tip = tipAtMax ? max.get(axis) : min.get(axis);
			float top = tip + (tipAtMax ? -1.0F : 1.0F) * size.get(axis) * EDGE_LENGTH;
			int first = axis == 0 ? 1 : 0;
			int second = axis == 2 ? 1 : 2;
			List<Vector3f[]> edges = new ArrayList<>();
			for (float a : new float[] {min.get(first), max.get(first)}) {
				for (float b : new float[] {min.get(second), max.get(second)}) {
					Vector3f tipPoint = new Vector3f();
					tipPoint.setComponent(axis, tip).setComponent(first, a).setComponent(second, b);
					Vector3f topPoint = new Vector3f(tipPoint).setComponent(axis, top);
					edges.add(new Vector3f[] {tipPoint, topPoint});
				}
			}
			Vector3f pivot = key.pivot;
			edges.sort(Comparator.comparingDouble(edge -> -(Math.pow(edge[0].x + pivot.x, 2) + Math.pow(edge[0].z + pivot.z, 2))));
			return List.copyOf(edges.subList(0, Math.min(EDGES, edges.size())));
		});
	}

	// New point of the band
	void add(EmoteModel.Bone bone, Matrix4f toWorld, Vec3 back, long now) {
		Point last = this.points.peekLast();
		if (last != null && now - last.nanos() < STEP_NANOS) {
			return;
		}
		List<Vector3f[]> edges = edges(bone);
		if (edges.isEmpty()) {
			return;
		}
		Vec3[] tips = new Vec3[edges.size()];
		Vec3[] tops = new Vec3[edges.size()];
		for (int i = 0; i < edges.size(); i++) {
			tips[i] = world(toWorld, edges.get(i)[0]);
			tops[i] = world(toWorld, edges.get(i)[1]);
		}
		this.points.addLast(new Point(tips, tops, back, now));
	}

	private static Vec3 world(Matrix4f matrix, Vector3f point) {
		Vector3f at = matrix.transformPosition(new Vector3f(point));
		return new Vec3(at.x, at.y, at.z);
	}

	// Fading and drifting band
	void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, long now) {
		while (!this.points.isEmpty() && now - this.points.peekFirst().nanos() > LIFE_NANOS) {
			this.points.removeFirst();
		}
		int count = this.points.size();
		if (count < 2) {
			return;
		}
		int edges = this.points.peekLast().tips().length;
		Vector3f[][] tips = new Vector3f[count][];
		Vector3f[][] tops = new Vector3f[count][];
		float[] strength = new float[count];
		int index = 0;
		for (Point point : this.points) {
			double age = (now - point.nanos()) / 1.0E9;
			Vec3 drift = point.back().scale(DRIFT_SPEED * age).subtract(camera);
			tips[index] = relative(point.tips(), drift, edges);
			tops[index] = relative(point.tops(), drift, edges);
			strength[index] = Math.max(0.0F, 1.0F - (float) ((now - point.nanos()) / (double) LIFE_NANOS));
			index++;
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(TEXTURE), (pose, buffer) -> {
			for (int edge = 0; edge < edges; edge++) {
				for (int i = 0; i + 1 < count; i++) {
					if (tips[i] == null || tips[i + 1] == null) {
						continue;
					}
					int colorA = color(strength[i]);
					int colorB = color(strength[i + 1]);
					float vA = (float) i / count;
					float vB = (float) (i + 1) / count;
					Vector3f tipA = tips[i][edge];
					Vector3f topA = tops[i][edge];
					Vector3f tipB = tips[i + 1][edge];
					Vector3f topB = tops[i + 1][edge];
					vertex(buffer, pose, tipA, 0.5F, vA, colorA);
					vertex(buffer, pose, topA, 1.0F, vA, colorA);
					vertex(buffer, pose, topB, 1.0F, vB, colorB);
					vertex(buffer, pose, tipB, 0.5F, vB, colorB);
					vertex(buffer, pose, tipB, 0.5F, vB, colorB);
					vertex(buffer, pose, topB, 1.0F, vB, colorB);
					vertex(buffer, pose, topA, 1.0F, vA, colorA);
					vertex(buffer, pose, tipA, 0.5F, vA, colorA);
				}
			}
		});
	}

	private static Vector3f[] relative(Vec3[] points, Vec3 offset, int edges) {
		if (points.length != edges) {
			return null;
		}
		Vector3f[] result = new Vector3f[edges];
		for (int i = 0; i < edges; i++) {
			Vec3 at = points[i].add(offset);
			result[i] = new Vector3f((float) at.x, (float) at.y, (float) at.z);
		}
		return result;
	}

	private static int color(float strength) {
		return Math.round(ALPHA * strength * 255.0F) << 24 | 0xFFFFFF;
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vector3f position, float u, float v, int color) {
		buffer.addVertex(pose, position.x, position.y, position.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(EmoteRender.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
