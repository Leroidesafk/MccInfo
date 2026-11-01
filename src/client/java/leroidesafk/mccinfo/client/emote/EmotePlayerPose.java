package leroidesafk.mccinfo.client.emote;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class EmotePlayerPose {
	// Player bones
	static final Set<String> PARTS = Set.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
	// Java model sizes
	private static final float MODEL_HEIGHT = 24.0F;
	private static final float LEG_PIVOT = 12.0F;

	private EmotePlayerPose() {
	}

	static boolean animatesPlayer(EmoteModel model) {
		for (String part : PARTS) {
			if (model.byName.containsKey(part)) {
				return true;
			}
		}
		return false;
	}

	// Emote pose on the real player model
	static void apply(PlayerModel model, EmoteModel emoteModel, Map<EmoteModel.Bone, Matrix4f> matrices) {
		if (!animatesPlayer(emoteModel)) {
			return;
		}
		Map<String, ModelPart> parts = new LinkedHashMap<>();
		parts.put("head", model.head);
		parts.put("body", model.body);
		parts.put("right_arm", model.rightArm);
		parts.put("left_arm", model.leftArm);
		parts.put("right_leg", model.rightLeg);
		parts.put("left_leg", model.leftLeg);
		float feet = feet(emoteModel);
		EmoteModel.Bone anchor = anchor(emoteModel);
		Matrix4f rootDelta = (anchor == null ? new Matrix4f() : delta(anchor, matrices)).translate(0.0F, feet, 0.0F);
		write(model.root(), toJava(rootDelta), new Vector3f());
		Matrix4f inverseRoot = new Matrix4f(rootDelta).invert();
		for (Map.Entry<String, ModelPart> entry : parts.entrySet()) {
			EmoteModel.Bone bone = emoteModel.byName.get(entry.getKey());
			if (bone == null) {
				continue;
			}
			Matrix4f relative = new Matrix4f(inverseRoot).mul(delta(bone, matrices).translate(0.0F, feet, 0.0F));
			PartPose rest = entry.getValue().getInitialPose();
			write(entry.getValue(), toJava(relative), new Vector3f(rest.x(), rest.y(), rest.z()));
		}
	}

	// Body motion for the cloak
	static Matrix4f bodyMotion(EmoteModel emoteModel, Map<EmoteModel.Bone, Matrix4f> matrices) {
		EmoteModel.Bone body = emoteModel.byName.get("body");
		return body == null ? null : toJava(delta(body, matrices).translate(0.0F, feet(emoteModel), 0.0F));
	}

	// Rig height from the leg pivots
	private static float feet(EmoteModel model) {
		float total = 0.0F;
		int count = 0;
		for (String name : new String[] {"right_leg", "left_leg"}) {
			EmoteModel.Bone bone = model.byName.get(name);
			if (bone != null) {
				total += bone.pivot.y - LEG_PIVOT;
				count++;
			}
		}
		if (count > 0) {
			return total / count;
		}
		EmoteModel.Bone head = model.byName.get("head");
		return head == null ? 0.0F : head.pivot.y - MODEL_HEIGHT;
	}

	// Parent bone of the body, like skin
	private static EmoteModel.Bone anchor(EmoteModel model) {
		EmoteModel.Bone body = model.byName.get("body");
		EmoteModel.Bone head = model.byName.get("head");
		EmoteModel.Bone source = body != null ? body : head;
		return source == null ? null : source.parent;
	}

	// Bone motion from its rest pose
	private static Matrix4f delta(EmoteModel.Bone bone, Map<EmoteModel.Bone, Matrix4f> matrices) {
		Matrix4f matrix = matrices.get(bone);
		Matrix4f result = matrix == null ? new Matrix4f().translate(bone.pivot) : new Matrix4f(matrix);
		return result.translate(-bone.pivot.x, -bone.pivot.y, -bone.pivot.z);
	}

	// Blockbench space to Java model space
	private static Matrix4f toJava(Matrix4f blockbench) {
		Matrix4f toBlockbench = new Matrix4f().translate(0.0F, MODEL_HEIGHT, 0.0F).scale(-1.0F, -1.0F, 1.0F);
		Matrix4f fromBlockbench = new Matrix4f().scale(-1.0F, -1.0F, 1.0F).translate(0.0F, -MODEL_HEIGHT, 0.0F);
		return fromBlockbench.mul(blockbench).mul(toBlockbench);
	}

	// Part position, rotation and scale
	private static void write(ModelPart part, Matrix4f transform, Vector3f pivot) {
		Vector3f position = transform.transformPosition(new Vector3f(pivot));
		Vector3f scale = transform.getScale(new Vector3f());
		if (scale.x < 1.0E-6F || scale.y < 1.0E-6F || scale.z < 1.0E-6F) {
			part.x = position.x;
			part.y = position.y;
			part.z = position.z;
			part.xScale = scale.x;
			part.yScale = scale.y;
			part.zScale = scale.z;
			return;
		}
		Matrix4f rotation = new Matrix4f(transform).scale(1.0F / scale.x, 1.0F / scale.y, 1.0F / scale.z);
		Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
		part.x = position.x;
		part.y = position.y;
		part.z = position.z;
		part.xRot = angles.x;
		part.yRot = angles.y;
		part.zRot = angles.z;
		part.xScale = scale.x;
		part.yScale = scale.y;
		part.zScale = scale.z;
	}
}
