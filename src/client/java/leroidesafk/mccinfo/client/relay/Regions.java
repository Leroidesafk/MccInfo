package leroidesafk.mccinfo.client.relay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Map zones sent by the relay
public final class Regions {
	public record Region(String name, int x1, int z1, int x2, int z2) {
		boolean contains(Vec3 position) {
			return position.x >= Math.min(this.x1, this.x2) && position.x < Math.max(this.x1, this.x2) + 1
					&& position.z >= Math.min(this.z1, this.z2) && position.z < Math.max(this.z1, this.z2) + 1;
		}

		long area() {
			return (Math.abs((long) this.x2 - this.x1) + 1) * (Math.abs((long) this.z2 - this.z1) + 1);
		}
	}

	private static volatile List<Region> regions = List.of();

	private Regions() {
	}

	static void receive(JsonObject object) {
		if (!object.has("regions") || !object.get("regions").isJsonArray()) {
			return;
		}
		List<Region> received = new ArrayList<>();
		for (JsonElement element : object.getAsJsonArray("regions")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject region = element.getAsJsonObject();
			if (region.has("name") && region.has("x1") && region.has("z1") && region.has("x2") && region.has("z2")) {
				received.add(new Region(region.get("name").getAsString(), region.get("x1").getAsInt(), region.get("z1").getAsInt(),
						region.get("x2").getAsInt(), region.get("z2").getAsInt()));
			}
		}
		regions = List.copyOf(received);
	}

	// Smallest region containing the position
	public static Region at(Vec3 position) {
		Region found = null;
		for (Region region : regions) {
			if (region.contains(position) && (found == null || region.area() < found.area())) {
				found = region;
			}
		}
		return found;
	}

	// Also true when both are outside any region
	public static boolean same(Vec3 first, Vec3 second) {
		return Objects.equals(at(first), at(second));
	}
}
