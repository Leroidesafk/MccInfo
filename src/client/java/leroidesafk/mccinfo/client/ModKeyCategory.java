package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.KeyMapping;

public final class ModKeyCategory {
	// Key category in the game Options
	public static final KeyMapping.Category MAIN = KeyMapping.Category.register(Mccinfo.id("main"));

	private ModKeyCategory() {
	}
}
