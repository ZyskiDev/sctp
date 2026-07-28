package com.snailtools.shoplogger;

/** The two Snailcraft worlds shops can be logged under. Sellers can operate on both. */
public enum ShopWorld {
	FIREFLY,
	HONEYBEE;

	/** Case-insensitive lookup, e.g. for command arguments. Returns null if no match. */
	public static ShopWorld fromString(String s) {
		for (ShopWorld world : values()) {
			if (world.name().equalsIgnoreCase(s)) return world;
		}
		return null;
	}

	/** Display label, e.g. "Firefly". */
	public String label() {
		return name().charAt(0) + name().substring(1).toLowerCase();
	}
}
