package com.snailtools.shoplogger;

import net.minecraft.core.BlockPos;

/**
 * A parsed Snailcraft-style shop sign. Two formats:
 *
 * Priced shop:
 *   1: "[PRICE]"  (literal, case-insensitive)
 *   2: amount of currency for however many of the item the shop stocks per
 *      inventory slot (not necessarily a full stack — see ShopEntryFactory,
 *      which scales this up to a real full-stack price using the actual
 *      quantity found in the container's slots)
 *   3: currency (diamond, diamondblock, ...)
 *   4: seller username
 *
 * Display-only shop (not actually for sale, e.g. showcasing a build):
 *   1: "[DISPLAY]"  (literal, case-insensitive)
 *   2, 3: unused (typically blank)
 *   4: seller username
 */
public record ShopSign(BlockPos signPos, int signPrice, String currency, String seller, boolean display) {

	/** Currency sentinel for display-only signs — see ShopEntry#priceLabel. */
	public static final String DISPLAY_CURRENCY = "display";

	/** Parses 4 raw sign lines as a priced [PRICE] shop. Returns null if the sign doesn't match that format. */
	public static ShopSign parse(BlockPos signPos, String line1, String line2, String line3, String line4) {
		if (line1 == null || !line1.trim().equalsIgnoreCase("[PRICE]")) {
			return null;
		}
		if (line2 == null || line3 == null || line4 == null) {
			return null;
		}

		String amountStr = line2.trim();
		String currency = line3.trim().toLowerCase();
		String seller = line4.trim();

		if (amountStr.isEmpty() || currency.isEmpty() || seller.isEmpty()) {
			return null;
		}

		int amount;
		try {
			amount = Integer.parseInt(amountStr.replaceAll("[^0-9]", ""));
		} catch (NumberFormatException e) {
			return null;
		}

		return new ShopSign(signPos, amount, currency, seller, false);
	}

	/** Parses 4 raw sign lines as a [DISPLAY] shop (no real price — see ShopEntry#priceLabel). */
	public static ShopSign parseDisplay(BlockPos signPos, String line1, String line2, String line3, String line4) {
		if (line1 == null || !line1.trim().equalsIgnoreCase("[DISPLAY]")) {
			return null;
		}
		if (line4 == null) {
			return null;
		}

		String seller = line4.trim();
		if (seller.isEmpty()) {
			return null;
		}

		return new ShopSign(signPos, 0, DISPLAY_CURRENCY, seller, true);
	}
}
