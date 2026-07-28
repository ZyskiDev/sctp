package com.snailtools.shoplogger;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * When enabled via its hotkey, echoes every item found during a chest scan to
 * chat, grouped by price and then by stack size, e.g.:
 *
 * PRICE: 2 db
 * STACKED: 64
 * cobblestone, stone, dirt, gravel
 * STACKED: 25
 * seeds, paper, sugarcane
 */
public final class ScanChatLogger {

	private static final String DIAMOND = "minecraft:diamond";
	private static final String DIAMOND_BLOCK = "minecraft:diamond_block";

	private static boolean enabled = false;

	private ScanChatLogger() {}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	/** Call right after a container scan with whatever entries were just found (may be empty). */
	public static void maybePrint(MinecraftClient client, List<ShopEntry> entries) {
		if (!enabled || client.player == null || entries.isEmpty()) return;

		// priceLabel -> stackSize -> item names, in first-seen order.
		// Diamonds/diamond blocks are the currency, not merchandise — never echo them here.
		Map<String, Map<Integer, List<String>>> byPrice = new LinkedHashMap<>();
		for (ShopEntry e : entries) {
			if (DIAMOND.equals(e.baseItem()) || DIAMOND_BLOCK.equals(e.baseItem())) continue;
			byPrice.computeIfAbsent(e.priceLabel(), k -> new LinkedHashMap<>())
					.computeIfAbsent(e.stackSize(), k -> new ArrayList<>())
					.add(e.itemName());
		}
		if (byPrice.isEmpty()) return;

		for (Map.Entry<String, Map<Integer, List<String>>> priceGroup : byPrice.entrySet()) {
			ChatFormat.sendPlain(client, ChatFormat.PRICE, "PRICE: " + priceGroup.getKey());
			for (Map.Entry<Integer, List<String>> stackGroup : priceGroup.getValue().entrySet()) {
				ChatFormat.sendPlain(client, ChatFormat.STACK, "STACKED: " + stackGroup.getKey());
				ChatFormat.sendPlain(client, ChatFormat.ITEMS, String.join(", ", stackGroup.getValue()));
			}
		}
	}
}
