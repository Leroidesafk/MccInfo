package leroidesafk.mccinfo.client.emote;

import leroidesafk.mccinfo.client.relay.ModUsers;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Player badge as a 3D item in emotes
final class EmotePlayerIcon {
	private static final String[] SIDES = {"north", "south", "east", "west", "up", "down"};
	// Faces per bone and icon size
	private static final Map<EmoteModel.Bone, Cached> CACHE = new WeakHashMap<>();

	private record Cached(int width, int height, List<EmoteModel.Face> faces) {
	}

	private EmotePlayerIcon() {
	}

	// Badge of the player, own one in previews
	static ModUsers.Icon icon(Player player) {
		Player target = player != null ? player : Minecraft.getInstance().player;
		return ModUsers.playerIcon(target == null ? null : target.getGameProfile().name());
	}

	static List<EmoteModel.Face> faces(EmoteModel.Bone bone, int width, int height) {
		Cached cached = CACHE.get(bone);
		if (cached == null || cached.width() != width || cached.height() != height) {
			cached = new Cached(width, height, build(bone, width, height));
			CACHE.put(bone, cached);
		}
		return cached.faces();
	}

	// One small cube per icon pixel
	private static List<EmoteModel.Face> build(EmoteModel.Bone bone, int width, int height) {
		List<EmoteModel.Face> faces = new ArrayList<>();
		if (width <= 0 || height <= 0) {
			return faces;
		}
		for (EmoteModel.Box box : bone.boxes) {
			float pixel = Math.min((box.to().x - box.from().x) / width, (box.to().y - box.from().y) / height);
			float left = (box.from().x + box.to().x + width * pixel) / 2.0F;
			float top = (box.from().y + box.to().y + height * pixel) / 2.0F;
			for (int column = 0; column < width; column++) {
				for (int row = 0; row < height; row++) {
					Vector3f from = new Vector3f(left - (column + 1) * pixel, top - (row + 1) * pixel, box.from().z);
					Vector3f to = new Vector3f(left - column * pixel, top - row * pixel, box.to().z);
					float u = (column + 0.5F) / width;
					float v = (row + 0.5F) / height;
					float[] uv = {u, v, u, v};
					for (String side : SIDES) {
						EmoteModel.Face face = EmoteModel.face(side, from, to, box.local(), uv);
						if (face != null) {
							faces.add(face);
						}
					}
				}
			}
		}
		return List.copyOf(faces);
	}
}
