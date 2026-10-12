package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.ShopEntryFactory;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Hold X (Open Item Library) and press C: opens the item page for whatever is
 * in your main hand. A rare (matched by name against the rare catalog, the
 * same way listings are) opens its rare page; anything else opens the vanilla
 * item page under the name a shop scan would log it as.
 */
public final class HeldItemPage {

	private HeldItemPage() {}

	public static void open(Minecraft mc) {
		if (mc.player == null) return;
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.isEmpty()) {
			ChatFormat.send(mc, ChatFormat.INFO, "Hold an item to open its page.");
			return;
		}
		String name = ShopEntryFactory.displayNameFor(stack);
		String baseItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		WebDataClient.fetchRareCatalog().handle((rares, ex) -> {
			mc.execute(() -> {
				RareItem rare = findRare(rares, name);
				mc.setScreenAndShow(rare != null
						? new ItemDetailScreen(null, rare.name, null, rare.texture, true, rare)
						: new ItemDetailScreen(null, name, baseItem, null, false, null));
			});
			return null;
		});
	}

	private static RareItem findRare(List<RareItem> rares, String name) {
		if (rares == null) return null; // catalog unreachable: fall back to the vanilla page
		for (RareItem r : rares) {
			if (MatchUtil.isRareNameMatch(name, r.name)) return r;
		}
		return null;
	}
}
