package leroidesafk.mccinfo;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mccinfo implements ModInitializer {
	// Mod id, also the namespace of its files
	public static final String MOD_ID = "mccinfo";

	// Log of the mod
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Common start, the client starts in MccinfoClient
	@Override
	public void onInitialize() {
		LOGGER.info("MccInfo loaded");
	}

	// Identifier in the mod namespace
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
