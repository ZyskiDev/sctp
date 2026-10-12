package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.ShopScanner;
import com.snailtools.shoplogger.ShopSign;
import com.snailtools.shoplogger.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

/**
 * Confirm expensive buys: in someone else's shop chest, clicking an item buys
 * it right away (the server swaps it for the sign's price from your
 * inventory). When that price is at or above your threshold, the first click
 * is held back and the slot is outlined with "Click again to buy"; a second
 * click on the same slot within a few seconds goes through. Settings › Alerts
 * turns it off or changes the threshold.
 */
public final class BuyConfirm {

	private BuyConfirm() {}

	private static final String CONFIG_ENABLED = "buyConfirm/enabled";
	private static final String CONFIG_THRESHOLD_DB = "buyConfirm/thresholdDb";
	public static final int[] THRESHOLD_STEPS_DB = {1, 5, 10, 16, 32, 64, 128, 256, 576};
	private static final long CONFIRM_WINDOW_MS = 4000;
	private static final int AMBER = 0xFFF0B429;

	// same rates as ShopEntry / worker.js: what one unit of a sign currency is worth in diamonds
	private static final Map<String, Double> CURRENCY_VALUE = Map.ofEntries(
			Map.entry("diamond", 1.0), Map.entry("diamondblock", 9.0),
			Map.entry("iron", 1.0 / 64), Map.entry("ironingot", 1.0 / 64), Map.entry("ironblock", 9.0 / 64),
			Map.entry("gold", 1.0 / 18), Map.entry("goldingot", 1.0 / 18), Map.entry("goldblock", 9.0 / 18),
			Map.entry("netherite", 18.0), Map.entry("netheriteingot", 18.0), Map.entry("netheriteblock", 162.0)
	);

	private static int pendingSlot = -1;
	private static int pendingContainer = -1;
	private static long pendingAt = 0;

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	public static int thresholdDb() {
		return Config.getOrCreate(CONFIG_THRESHOLD_DB, Integer.class, 32);
	}

	public static void setThresholdDb(int db) {
		Config.update(CONFIG_THRESHOLD_DB, db);
	}

	/** What one click in this shop costs, in diamonds, or -1 when unknown (display signs, odd currencies). */
	private static double clickPriceDia(ShopSign sign) {
		if (sign == null || sign.display()) return -1;
		Double unit = CURRENCY_VALUE.get(sign.currency().toLowerCase(Locale.ROOT));
		return unit == null ? -1 : sign.signPrice() * unit;
	}

	/** The open shop's sign, if this click lands on one of its item slots and should be confirmed first. */
	private static ShopSign guardedSign(Minecraft mc, int containerId, int slotId) {
		if (!isEnabled() || mc.player == null) return null;
		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null || menu.containerId != containerId || !(menu instanceof ChestMenu chest)) return null;
		ShopSign sign = ShopScanner.currentShopSign(menu);
		if (sign == null) return null;
		if (sign.seller().equalsIgnoreCase(mc.getUser().getName())) return null; // your own shop
		if (slotId < 0 || slotId >= chest.getContainer().getContainerSize()) return null; // your inventory, outside clicks
		if (menu.getSlot(slotId).getItem().isEmpty()) return null;
		double price = clickPriceDia(sign);
		return price >= 0 && price >= thresholdDb() * 9.0 ? sign : null;
	}

	/** Called for every container click (see BuyConfirmMixin). Returns true to hold the click back. */
	public static boolean shouldBlock(int containerId, int slotId) {
		Minecraft mc = Minecraft.getInstance();
		if (guardedSign(mc, containerId, slotId) == null) return false;
		long now = System.currentTimeMillis();
		if (pendingSlot == slotId && pendingContainer == containerId && now - pendingAt <= CONFIRM_WINDOW_MS) {
			pendingSlot = -1; // confirmed: let this one through
			return false;
		}
		pendingSlot = slotId;
		pendingContainer = containerId;
		pendingAt = now;
		return true;
	}

	/** Outline the slot waiting for its second click, with a short label above the chest. */
	public static void onScreenRender(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (pendingSlot < 0 || !(screen instanceof AbstractContainerScreen<?> acs)) return;
		Minecraft mc = Minecraft.getInstance();
		AbstractContainerMenu menu = acs.getMenu();
		if (menu.containerId != pendingContainer || System.currentTimeMillis() - pendingAt > CONFIRM_WINDOW_MS || pendingSlot >= menu.slots.size()) {
			pendingSlot = -1;
			return;
		}
		ShopSign sign = ShopScanner.currentShopSign(menu);
		if (sign == null) return;
		int left = field(acs, "leftPos"), top = field(acs, "topPos"), imageWidth = field(acs, "imageWidth");
		Slot slot = menu.getSlot(pendingSlot);
		int x = left + slot.x - 1, y = top + slot.y - 1;
		g.fill(x - 1, y - 1, x + 19, y, AMBER);
		g.fill(x - 1, y + 18, x + 19, y + 19, AMBER);
		g.fill(x - 1, y, x, y + 18, AMBER);
		g.fill(x + 18, y, x + 19, y + 18, AMBER);
		String label = "Click again to buy · " + priceLabel(sign);
		int w = mc.font.width(label);
		int lx = left + (imageWidth - w) / 2, ly = top - 14;
		g.fill(lx - 4, ly - 3, lx + w + 4, ly + 11, 0xE0201A10);
		g.text(mc.font, label, lx, ly, AMBER, true);
	}

	private static String priceLabel(ShopSign sign) {
		String c = sign.currency().toLowerCase(Locale.ROOT);
		String unit = c.equals("diamondblock") ? "DB" : c.equals("diamond") ? "dia" : c;
		return sign.signPrice() + " " + unit;
	}

	private static int field(AbstractContainerScreen<?> screen, String name) {
		try {
			Field f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return (int) f.get(screen);
		} catch (Exception e) {
			return 0;
		}
	}
}
