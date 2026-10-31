package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class LegacyMigration {
	// Name of the mod before 1.0.9 (not supposed to be uploaded anywhere, but we never knows)
	private static final String OLD_ID = "mccfishheart";
	// Old config file suffix -> new file name in config/mccinfo/
	private static final Map<String, String> CONFIG_FILES = Map.of(".json", "config.json", "_spots.json", "spots.json", "_fish.json", "fish.json",
			"_icons.json", "icons.json");

	private LegacyMigration() {
	}

	// Runs first at startup, before any config is read
	static void run() {
		Path config = FabricLoader.getInstance().getConfigDir();
		try {
			Files.createDirectories(ModConfig.DIRECTORY);
		} catch (IOException e) {
			Mccinfo.LOGGER.warn("Could not create {}", ModConfig.DIRECTORY, e);
		}
		// Move the loose config files (mccinfo.json, mccfishheart_spots.json) into it
		for (String id : List.of(Mccinfo.MOD_ID, OLD_ID)) {
			for (Map.Entry<String, String> entry : CONFIG_FILES.entrySet()) {
				move(config.resolve(id + entry.getKey()), ModConfig.file(entry.getValue()));
			}
		}
		// Rename the old game folder
		Path game = FabricLoader.getInstance().getGameDir();
		move(game.resolve(OLD_ID), game.resolve(Mccinfo.MOD_ID));
	}

	// Move without overwriting
	private static void move(Path from, Path to) {
		if (!Files.isRegularFile(from) && !Files.isDirectory(from) || Files.exists(to)) {
			return;
		}
		try {
			Files.move(from, to);
			Mccinfo.LOGGER.info("Old data moved: {} -> {}", from, to);
		} catch (IOException e) {
			Mccinfo.LOGGER.warn("Could not move {} to {}", from, to, e);
		}
	}
}
