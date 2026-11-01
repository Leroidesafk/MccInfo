package leroidesafk.mccinfo.client.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EmoteModel {
	// Quad with its uv
	record Face(Vector3f[] corners, float[] uv, Vector3f normal) {
	}

	// Bone: pivot, rotation, faces and locators
	static final class Bone {
		final String name;
		final String parentName;
		final Vector3f pivot;
		final Vector3f rotation;
		final List<Face> faces = new ArrayList<>();
		final Map<String, Vector3f> locators = new HashMap<>();
		Bone parent;

		Bone(String name, String parentName, Vector3f pivot, Vector3f rotation) {
			this.name = name;
			this.parentName = parentName;
			this.pivot = pivot;
			this.rotation = rotation;
		}
	}

	// Model without geometry
	static final EmoteModel EMPTY = new EmoteModel(List.of(), 16, 16);

	// Bones parents first, by name and by locator
	final List<Bone> bones;
	final Map<String, Bone> byName = new HashMap<>();
	final Map<String, Bone> locatorBones = new HashMap<>();

	private EmoteModel(List<Bone> bones, float textureWidth, float textureHeight) {
		this.bones = bones;
		for (Bone bone : bones) {
			this.byName.put(bone.name, bone);
			for (String locator : bone.locators.keySet()) {
				this.locatorBones.put(locator, bone);
			}
		}
	}

	// Geometry from the .geo.json, X mirrored to Blockbench space
	static EmoteModel parse(JsonObject root) {
		JsonArray geometries = root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray() ? root.getAsJsonArray("minecraft:geometry") : null;
		if (geometries == null || geometries.isEmpty() || !geometries.get(0).isJsonObject()) {
			return EMPTY;
		}
		JsonObject geometry = geometries.get(0).getAsJsonObject();
		JsonObject description = geometry.has("description") && geometry.get("description").isJsonObject() ? geometry.getAsJsonObject("description") : new JsonObject();
		float textureWidth = description.has("texture_width") ? description.get("texture_width").getAsFloat() : 16.0F;
		float textureHeight = description.has("texture_height") ? description.get("texture_height").getAsFloat() : 16.0F;
		Map<String, Bone> found = new LinkedHashMap<>();
		JsonArray bones = geometry.has("bones") && geometry.get("bones").isJsonArray() ? geometry.getAsJsonArray("bones") : new JsonArray();
		for (JsonElement element : bones) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			String name = lower(object, "name");
			if (name == null) {
				continue;
			}
			Vector3f pivot = mirror(vector(object.get("pivot"), 0.0F));
			Vector3f rotation = vector(object.get("rotation"), 0.0F);
			Bone bone = new Bone(name, lower(object, "parent"), pivot, rotation);
			if (object.has("cubes") && object.get("cubes").isJsonArray()) {
				for (JsonElement cube : object.getAsJsonArray("cubes")) {
					if (cube.isJsonObject()) {
						addCube(bone, cube.getAsJsonObject(), textureWidth, textureHeight);
					}
				}
			}
			if (object.has("locators") && object.get("locators").isJsonObject()) {
				for (Map.Entry<String, JsonElement> locator : object.getAsJsonObject("locators").entrySet()) {
					JsonElement value = locator.getValue();
					JsonElement offset = value.isJsonObject() ? value.getAsJsonObject().get("offset") : value;
					bone.locators.put(locator.getKey().toLowerCase(Locale.ROOT), mirror(vector(offset, 0.0F)).sub(pivot));
				}
			}
			found.put(name, bone);
		}
		List<Bone> ordered = new ArrayList<>();
		for (Bone bone : found.values()) {
			bone.parent = bone.parentName == null ? null : found.get(bone.parentName);
		}
		for (Bone bone : found.values()) {
			addOrdered(bone, ordered);
		}
		return new EmoteModel(List.copyOf(ordered), textureWidth, textureHeight);
	}

	// Parents before children
	private static void addOrdered(Bone bone, List<Bone> ordered) {
		if (ordered.contains(bone)) {
			return;
		}
		Bone parent = bone.parent;
		for (int depth = 0; parent != null && depth < 64; depth++) {
			if (parent == bone) {
				bone.parent = null;
				break;
			}
			parent = parent.parent;
		}
		if (bone.parent != null) {
			addOrdered(bone.parent, ordered);
		}
		ordered.add(bone);
	}

	// Cube faces in bone space
	private static void addCube(Bone bone, JsonObject cube, float textureWidth, float textureHeight) {
		Vector3f origin = vector(cube.get("origin"), 0.0F);
		Vector3f size = vector(cube.get("size"), 0.0F);
		float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F;
		Vector3f first = new Vector3f(-(origin.x + size.x), origin.y, origin.z);
		Vector3f second = new Vector3f(-origin.x, origin.y + size.y, origin.z + size.z);
		Vector3f from = new Vector3f(first).min(second).sub(inflate, inflate, inflate);
		Vector3f to = new Vector3f(first).max(second).add(inflate, inflate, inflate);
		Vector3f cubePivot = cube.has("pivot") ? mirror(vector(cube.get("pivot"), 0.0F)) : new Vector3f();
		Vector3f cubeRotation = vector(cube.get("rotation"), 0.0F);
		Matrix4f local = new Matrix4f().translate(cubePivot.x - bone.pivot.x, cubePivot.y - bone.pivot.y, cubePivot.z - bone.pivot.z)
				.rotateZYX((float) Math.toRadians(cubeRotation.z), (float) Math.toRadians(-cubeRotation.y), (float) Math.toRadians(-cubeRotation.x))
				.translate(-cubePivot.x, -cubePivot.y, -cubePivot.z);
		Map<String, float[]> uvs = faceUvs(cube, size, textureWidth, textureHeight);
		for (Map.Entry<String, float[]> entry : uvs.entrySet()) {
			Vector3f[] corners = corners(entry.getKey(), from, to);
			if (corners == null) {
				continue;
			}
			for (Vector3f corner : corners) {
				local.transformPosition(corner);
			}
			Vector3f normal = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0]));
			if (normal.lengthSquared() > 1.0E-8F) {
				normal.normalize();
			}
			bone.faces.add(new Face(corners, entry.getValue(), normal));
		}
	}

	// Per face or box uv
	private static Map<String, float[]> faceUvs(JsonObject cube, Vector3f size, float textureWidth, float textureHeight) {
		Map<String, float[]> uvs = new LinkedHashMap<>();
		JsonElement uv = cube.get("uv");
		if (uv != null && uv.isJsonObject()) {
			for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
				if (!face.getValue().isJsonObject()) {
					continue;
				}
				JsonObject value = face.getValue().getAsJsonObject();
				Vector3f start = vector(value.get("uv"), 0.0F);
				Vector3f extent = vector(value.get("uv_size"), 0.0F);
				String key = face.getKey().toLowerCase(Locale.ROOT);
				float[] rect = key.equals("up") || key.equals("down")
						? new float[] {start.x + extent.x, start.y + extent.y, start.x, start.y}
						: new float[] {start.x, start.y, start.x + extent.x, start.y + extent.y};
				uvs.put(key, normalize(rect, textureWidth, textureHeight));
			}
			return uvs;
		}
		if (uv != null && uv.isJsonArray()) {
			Vector3f start = vector(uv, 0.0F);
			float u = start.x;
			float v = start.y;
			float w = size.x;
			float h = size.y;
			float d = size.z;
			boolean mirrored = cube.has("mirror") && cube.get("mirror").getAsBoolean();
			uvs.put("up", normalize(new float[] {u + d, v, u + d + w, v + d}, textureWidth, textureHeight));
			uvs.put("down", normalize(new float[] {u + d + w, v + d, u + d + w + w, v}, textureWidth, textureHeight));
			uvs.put("north", normalize(rect(u + d, v + d, w, h, mirrored), textureWidth, textureHeight));
			uvs.put("south", normalize(rect(u + d + w + d, v + d, w, h, mirrored), textureWidth, textureHeight));
			uvs.put(mirrored ? "west" : "east", normalize(rect(u, v + d, d, h, mirrored), textureWidth, textureHeight));
			uvs.put(mirrored ? "east" : "west", normalize(rect(u + d + w, v + d, d, h, mirrored), textureWidth, textureHeight));
		}
		return uvs;
	}

	private static float[] rect(float u, float v, float w, float h, boolean mirrored) {
		return mirrored ? new float[] {u + w, v, u, v + h} : new float[] {u, v, u + w, v + h};
	}

	private static float[] normalize(float[] rect, float textureWidth, float textureHeight) {
		return new float[] {rect[0] / textureWidth, rect[1] / textureHeight, rect[2] / textureWidth, rect[3] / textureHeight};
	}

	// Face corners seen from outside
	private static Vector3f[] corners(String face, Vector3f f, Vector3f t) {
		return switch (face) {
			case "north" -> new Vector3f[] {new Vector3f(t.x, t.y, f.z), new Vector3f(t.x, f.y, f.z), new Vector3f(f.x, f.y, f.z), new Vector3f(f.x, t.y, f.z)};
			case "south" -> new Vector3f[] {new Vector3f(f.x, t.y, t.z), new Vector3f(f.x, f.y, t.z), new Vector3f(t.x, f.y, t.z), new Vector3f(t.x, t.y, t.z)};
			case "east" -> new Vector3f[] {new Vector3f(t.x, t.y, t.z), new Vector3f(t.x, f.y, t.z), new Vector3f(t.x, f.y, f.z), new Vector3f(t.x, t.y, f.z)};
			case "west" -> new Vector3f[] {new Vector3f(f.x, t.y, f.z), new Vector3f(f.x, f.y, f.z), new Vector3f(f.x, f.y, t.z), new Vector3f(f.x, t.y, t.z)};
			case "up" -> new Vector3f[] {new Vector3f(f.x, t.y, f.z), new Vector3f(f.x, t.y, t.z), new Vector3f(t.x, t.y, t.z), new Vector3f(t.x, t.y, f.z)};
			case "down" -> new Vector3f[] {new Vector3f(f.x, f.y, t.z), new Vector3f(f.x, f.y, f.z), new Vector3f(t.x, f.y, f.z), new Vector3f(t.x, f.y, t.z)};
			default -> null;
		};
	}

	// Bone matrices with the animation
	Map<Bone, Matrix4f> pose(Map<String, float[]> animated) {
		Map<Bone, Matrix4f> matrices = new HashMap<>();
		for (Bone bone : this.bones) {
			float[] values = animated.get(bone.name);
			float rotationX = bone.rotation.x;
			float rotationY = bone.rotation.y;
			float rotationZ = bone.rotation.z;
			float offsetX = 0.0F;
			float offsetY = 0.0F;
			float offsetZ = 0.0F;
			float scaleX = 1.0F;
			float scaleY = 1.0F;
			float scaleZ = 1.0F;
			if (values != null) {
				rotationX += values[0];
				rotationY += values[1];
				rotationZ += values[2];
				offsetX = -values[3];
				offsetY = values[4];
				offsetZ = values[5];
				scaleX = values[6];
				scaleY = values[7];
				scaleZ = values[8];
			}
			Matrix4f parent = bone.parent == null ? new Matrix4f() : matrices.get(bone.parent);
			Vector3f parentPivot = bone.parent == null ? new Vector3f() : bone.parent.pivot;
			Matrix4f matrix = new Matrix4f(parent == null ? new Matrix4f() : parent)
					.translate(bone.pivot.x - parentPivot.x + offsetX, bone.pivot.y - parentPivot.y + offsetY, bone.pivot.z - parentPivot.z + offsetZ)
					.rotateZYX((float) Math.toRadians(rotationZ), (float) Math.toRadians(-rotationY), (float) Math.toRadians(-rotationX))
					.scale(scaleX, scaleY, scaleZ);
			matrices.put(bone, matrix);
		}
		return matrices;
	}

	// Locator position in model space
	Vector3f locator(String name, Map<Bone, Matrix4f> matrices) {
		if (name == null || name.isEmpty()) {
			return new Vector3f();
		}
		String key = name.toLowerCase(Locale.ROOT);
		Bone bone = this.locatorBones.get(key);
		if (bone == null) {
			Bone named = this.byName.get(key);
			Matrix4f matrix = named == null ? null : matrices.get(named);
			return matrix == null ? new Vector3f() : matrix.transformPosition(new Vector3f());
		}
		Matrix4f matrix = matrices.get(bone);
		Vector3f local = new Vector3f(bone.locators.get(key));
		return matrix == null ? local : matrix.transformPosition(local);
	}

	private static Vector3f mirror(Vector3f vector) {
		vector.x = -vector.x;
		return vector;
	}

	// Vector from a JSON array
	static Vector3f vector(JsonElement element, float fallback) {
		Vector3f vector = new Vector3f(fallback);
		if (element != null && element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (int i = 0; i < Math.min(3, array.size()); i++) {
				if (array.get(i).isJsonPrimitive() && array.get(i).getAsJsonPrimitive().isNumber()) {
					vector.setComponent(i, array.get(i).getAsFloat());
				}
			}
		} else if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			vector.set(element.getAsFloat());
		}
		return vector;
	}

	private static String lower(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString().toLowerCase(Locale.ROOT) : null;
	}
}
