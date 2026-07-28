package com.snailtools.shoplogger;

import net.minecraft.client.MinecraftClient;

/**
 * Shops only exist in the minecraft:new_shops dimension — both Firefly and
 * Honeybee route their shop areas through it. Never scan a lookalike sign +
 * container elsewhere (e.g. a player's own base in the overworld).
 */
public final class ShopDimension {

	private static final String NEW_SHOPS = "minecraft:new_shops";

	private ShopDimension() {}

	public static boolean isActive(MinecraftClient client) {
		return client.world != null
				&& NEW_SHOPS.equals(client.world.getRegistryKey().getValue().toString());
	}
}
