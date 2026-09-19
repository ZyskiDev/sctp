package com.snailtools.mapartscanner;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which Snailcraft world (Firefly/Honeybee) uploads are tagged with. Set via
 * /mapartworld; falls back to whatever the real Shop Logger mod already
 * stored (it writes FIREFLY/HONEYBEE to shoplogger-world.txt), so testers
 * who have both installed don't have to set it twice.
 */
final class MapartWorld {

	private static final Path OWN = FabricLoader.getInstance().getConfigDir().resolve("mapartscanner-world.txt");
	private static final Path SHOP_LOGGER = FabricLoader.getInstance().getConfigDir().resolve("shoplogger-world.txt");

	private MapartWorld() {}

	/** "Firefly", "Honeybee" or null when unknown. */
	static String get() {
		String own = read(OWN);
		return own != null ? own : read(SHOP_LOGGER);
	}

	static void set(String label) {
		try {
			Files.writeString(OWN, label);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String read(Path file) {
		try {
			if (!Files.exists(file)) return null;
			String v = Files.readString(file).trim();
			if (v.equalsIgnoreCase("firefly")) return "Firefly";
			if (v.equalsIgnoreCase("honeybee")) return "Honeybee";
		} catch (IOException e) {
			// treat an unreadable file as "not set"
		}
		return null;
	}
}
