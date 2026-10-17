package com.snailtools.shoplogger.gui.data;

import com.snailtools.shoplogger.config.Config;

/**
 * Remembers the filter and sort choices on the mod's screens (Listings,
 * Marketplace, item pages, seller profiles) in the config, so they're the same
 * next time. Sorting is "Auto" until the player picks one: newest first with
 * an empty search box, cheapest first (then newest) once something's typed.
 */
public final class FilterMemory {

	private FilterMemory() {}

	public static final String AUTO_SORT = "Auto";

	public static String get(String screen, String key, String fallback) {
		String v = Config.getOrDefault("filters/" + screen + "/" + key, String.class, null);
		return v == null || v.isEmpty() ? fallback : v;
	}

	/** Only accepts a remembered value that's still one of the choices. */
	public static String get(String screen, String key, String fallback, java.util.List<String> allowed) {
		String v = get(screen, key, fallback);
		return allowed.contains(v) ? v : fallback;
	}

	public static void set(String screen, String key, String value) {
		Config.update("filters/" + screen + "/" + key, value);
	}

	/** The sort to use for an "Auto" choice. */
	public static boolean autoIsCheapest(String query) {
		return query != null && !query.trim().isEmpty();
	}
}
