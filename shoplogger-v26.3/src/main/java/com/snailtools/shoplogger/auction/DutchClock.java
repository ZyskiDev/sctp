package com.snailtools.shoplogger.auction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Dutch auction popup: after /info on a lot in a Dutch auction, a small
 * panel shows the price right now, read from the nearest circle of copper
 * bulbs (32 bulbs: all lit = the starting bid, none lit = the lowest limit),
 * the circle itself, and the price at each quarter. It stays until that lot is
 * /sold or /unsold. /sold without a price sells at the price shown.
 */
public final class DutchClock {

	private DutchClock() {}

	public static final int LIGHTS = 32;
	private static final int SCAN_RADIUS = 20, SCAN_HEIGHT = 10, SCAN_EVERY_TICKS = 40;
	private static final int AMBER = 0xFFF0B429, OFF = 0xFF3A3A3A, PANEL = 0xE0141018, LINE = 0xFF5A4A2A, TEXT = 0xFFEDE6D6, MUTED = 0xFFA79F8F;

	private static AuctionClient.Lot lot;
	private static String auctionTitle;
	private static List<BlockPos> bulbs = List.of();
	private static int ticks;

	public static void show(AuctionClient.Lot l, String title) {
		lot = l;
		auctionTitle = title;
		ticks = SCAN_EVERY_TICKS; // scan right away
	}

	public static void hideIf(String lotId) {
		if (lot != null && lot.id.equals(lotId)) lot = null;
	}

	public static AuctionClient.Lot lot() { return lot; }

	/** How many bulbs of the circle are lit right now, or -1 when there's no circle nearby. */
	public static int lit() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || bulbs.isEmpty()) return -1;
		int n = 0;
		for (BlockPos p : bulbs) {
			BlockState s = mc.level.getBlockState(p);
			if (s.getBlock() instanceof CopperBulbBlock && s.getValue(BlockStateProperties.LIT)) n++;
		}
		return n;
	}

	/** The price shown right now, or null when there's no popup or no circle. */
	public static Integer currentPrice(String lotId) {
		if (lot == null || !lot.id.equals(lotId)) return null;
		int lit = lit();
		return lit < 0 ? null : AuctionHost.clockPrice(lot, Math.min(lit, LIGHTS), LIGHTS);
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (lot == null) return;
		if (mc.level == null || mc.player == null) { lot = null; return; }
		if (++ticks < SCAN_EVERY_TICKS) return;
		ticks = 0;
		bulbs = findCircle(mc);
	}

	/** The connected group of copper bulbs closest to the player (bulbs up to 2 blocks apart count as connected). */
	private static List<BlockPos> findCircle(Minecraft mc) {
		BlockPos me = mc.player.blockPosition();
		List<BlockPos> all = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
			for (int dy = -SCAN_HEIGHT; dy <= SCAN_HEIGHT; dy++)
				for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
					m.set(me.getX() + dx, me.getY() + dy, me.getZ() + dz);
					if (mc.level.getBlockState(m).getBlock() instanceof CopperBulbBlock) all.add(m.immutable());
				}
		if (all.isEmpty()) return List.of();
		BlockPos nearest = all.get(0);
		for (BlockPos p : all) if (p.distSqr(me) < nearest.distSqr(me)) nearest = p;
		Set<BlockPos> seen = new HashSet<>(List.of(nearest));
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(nearest));
		while (!queue.isEmpty()) {
			BlockPos c = queue.poll();
			for (BlockPos p : all) {
				if (seen.contains(p)) continue;
				if (Math.abs(p.getX() - c.getX()) <= 2 && Math.abs(p.getY() - c.getY()) <= 2 && Math.abs(p.getZ() - c.getZ()) <= 2) {
					seen.add(p);
					queue.add(p);
				}
			}
		}
		// The real circle is exactly 32 bulbs. Extra connected bulbs (like a fake
		// one next to it) are dropped, most oxidized first: the circle itself is
		// the least oxidized.
		List<BlockPos> circle = new ArrayList<>(seen);
		if (circle.size() > LIGHTS) {
			circle.sort(java.util.Comparator.<BlockPos>comparingInt(p -> oxidation(mc.level.getBlockState(p)))
					.thenComparingDouble(p -> p.distSqr(me)));
			circle = new ArrayList<>(circle.subList(0, LIGHTS));
		}
		return circle;
	}

	/** 0 = fresh copper, 1 = exposed, 2 = weathered, 3 = oxidized (waxed counts the same). */
	private static int oxidation(BlockState state) {
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return id.contains("oxidized") ? 3 : id.contains("weathered") ? 2 : id.contains("exposed") ? 1 : 0;
	}

	public static void render(GuiGraphicsExtractor g) {
		if (lot == null) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = 168, h = 158;
		int x = mc.getWindow().getGuiScaledWidth() - w - 6, y = 30;
		g.fill(x, y, x + w, y + h, PANEL);
		g.fill(x, y, x + w, y + 1, LINE);
		g.fill(x, y + h - 1, x + w, y + h, LINE);
		g.fill(x, y, x + 1, y + h, LINE);
		g.fill(x + w - 1, y, x + w, y + h, LINE);

		String name = (lot.quantity > 1 ? lot.quantity + "× " : "") + lot.itemName;
		g.text(font, trim(font, name, w - 12), x + 6, y + 6, TEXT, true);
		g.text(font, trim(font, "from " + lot.seller, w - 12), x + 6, y + 17, MUTED, false);

		int lit = lit();
		int cx = x + 40, cy = y + 66, r = 26;
		for (int i = 0; i < LIGHTS; i++) {
			double a = -Math.PI / 2 + i * (2 * Math.PI / LIGHTS); // from the top, clockwise
			int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(px - 2, py - 2, px + 2, py + 2, lit >= 0 && i < lit ? AMBER : OFF);
		}
		String count = lit < 0 ? "?" : String.valueOf(lit);
		g.text(font, count, cx - font.width(count) / 2, cy - 8, AMBER, true);
		g.text(font, "/" + LIGHTS, cx - font.width("/" + LIGHTS) / 2, cy + 2, MUTED, false);

		// price now + the quarter marks
		int tx = x + 78;
		g.text(font, "Price now", tx, y + 34, MUTED, false);
		String now = lit < 0 ? "no bulbs found" : AuctionHost.fmtDia(AuctionHost.clockPrice(lot, Math.min(lit, LIGHTS), LIGHTS));
		g.text(font, trim(font, now, w - 84), tx, y + 45, lit < 0 ? MUTED : AMBER, true);
		int qy = y + 60;
		for (int q = 4; q >= 0; q--) {
			int on = q * LIGHTS / 4;
			String line = on + " lit: " + AuctionHost.fmtDia(AuctionHost.clockPrice(lot, on, LIGHTS));
			boolean here = lit >= 0 && lit <= on && lit > (q - 1) * LIGHTS / 4;
			g.text(font, trim(font, line, w - 84), tx, qy, here ? TEXT : MUTED, false);
			qy += 10;
		}
		if (lit >= 0 && bulbs.size() < LIGHTS) {
			g.text(font, trim(font, "found " + bulbs.size() + " bulbs, expected " + LIGHTS, w - 12), x + 6, y + 112, 0xFFE2A33D, false);
		}
		g.text(font, trim(font, "/sold sells at this price", w - 12), x + 6, y + h - 32, MUTED, false);
		g.text(font, trim(font, "/unsold if nobody buys", w - 12), x + 6, y + h - 21, MUTED, false);
		if (auctionTitle != null) g.text(font, trim(font, auctionTitle, w - 12), x + 6, y + h - 10 - 1, 0xFF6F6858, false);
	}

	private static String trim(Font font, String s, int max) {
		if (font.width(s) <= max) return s;
		while (s.length() > 1 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
		return s + "…";
	}
}
