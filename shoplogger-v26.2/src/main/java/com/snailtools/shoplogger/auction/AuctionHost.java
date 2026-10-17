package com.snailtools.shoplogger.auction;

import com.snailtools.shoplogger.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared state for running auctions in-game: the Settings toggle, which
 * auction the commands work on, prices in STX/DB/dia, and matching the item
 * in your hand to a lot.
 */
public final class AuctionHost {

	private AuctionHost() {}

	private static final String CONFIG_SHOW = "auctions/showOnDashboard";
	private static final String CONFIG_SELECTED = "auctions/selected";

	/** Settings › Advanced: show the Auctions tile and screen. */
	public static boolean isShown() { return Config.getOrCreate(CONFIG_SHOW, Boolean.class, false); }
	public static void setShown(boolean v) { Config.update(CONFIG_SHOW, v); }

	/** The auction picked on the Auctions screen (commands use it when it's running). */
	public static String selectedId() { return Config.getOrDefault(CONFIG_SELECTED, String.class, null); }
	public static void setSelectedId(String id) { Config.update(CONFIG_SELECTED, id); }

	/**
	 * The auction to use: the one picked on the Auctions screen if it qualifies,
	 * else the first one that does (the list comes newest first). forEntering
	 * picks one that still takes shulkers; otherwise one that's running now.
	 */
	public static CompletableFuture<AuctionClient.Auction> current(boolean forEntering) {
		return AuctionClient.auctions().thenApply(r -> {
			List<AuctionClient.Auction> all = r.auctions == null ? List.of() : r.auctions;
			if (all.isEmpty()) throw new AuctionClient.AuctionException("You don't host any auctions. Add one on the Auctions screen with its code (Settings › Advanced shows the Auctions tile).");
			String sel = selectedId();
			AuctionClient.Auction picked = null;
			for (AuctionClient.Auction a : all) if (a.id.equals(sel) && (forEntering ? a.canEnter : a.live)) picked = a;
			if (picked == null) for (AuctionClient.Auction a : all) if (forEntering ? a.canEnter : a.live) { picked = a; break; }
			if (picked == null) {
				throw new AuctionClient.AuctionException(forEntering
						? "None of your auctions takes entries right now."
						: "None of your auctions is running right now (they start an hour before their time, until they're finished).");
			}
			return picked;
		});
	}

	// ---- prices ----

	private static final Pattern PART = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*([a-z]*)");

	/**
	 * "2stx63db", "84db3dia", "1stx", "58db", "4stx47db2dia", "2 stx 63 db",
	 * "1.5stx" -> diamonds (whole). A bare number is DB, or the next smaller
	 * unit after another part ("2stx63" = 2 STX 63 DB). Null if it can't be read.
	 */
	public static Integer parseDia(String input) {
		if (input == null) return null;
		String s = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
		if (s.isEmpty()) return null;
		Matcher m = PART.matcher(s);
		int pos = 0;
		double total = 0;
		int lastUnit = -1; // 576, 9 or 1
		boolean any = false;
		while (m.find()) {
			if (m.start() != pos) return null; // junk between parts
			pos = m.end();
			double n = Double.parseDouble(m.group(1).replace(',', '.'));
			String u = m.group(2);
			int unit;
			if (u.isEmpty()) unit = lastUnit == -1 ? 9 : lastUnit == 576 ? 9 : 1;
			else if (u.matches("stx|stack|stacks|st|s")) unit = 576;
			else if (u.matches("db|dbs|block|blocks|b")) unit = 9;
			else if (u.matches("dia|dias|diamond|diamonds|d")) unit = 1;
			else return null;
			total += n * unit;
			lastUnit = unit;
			any = true;
		}
		if (!any || pos != s.length()) return null;
		long r = Math.round(total);
		return r > 0 && r < Integer.MAX_VALUE ? (int) r : null;
	}

	/** 351 -> "39 DB", 352 -> "39 DB 1 dia", 600 -> "1 STX 2 DB 6 dia". */
	public static String fmtDia(int d) {
		int stx = d / 576, db = (d % 576) / 9, dia = d % 9;
		List<String> parts = new ArrayList<>();
		if (stx > 0) parts.add(stx + " STX");
		if (db > 0) parts.add(db + " DB");
		if (dia > 0 || parts.isEmpty()) parts.add(dia + " dia");
		return String.join(" ", parts);
	}

	// ---- matching the held item to a lot ----

	public static String norm(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']", "");
	}

	/**
	 * Lots with this item, best first: still waiting before sold/unsold ones,
	 * and the ones whose lot size equals the stack in hand first.
	 */
	public static List<AuctionClient.Lot> matches(List<AuctionClient.Lot> lots, String itemName, int count) {
		String key = norm(itemName);
		List<AuctionClient.Lot> out = new ArrayList<>();
		if (key.isEmpty()) return out;
		for (AuctionClient.Lot l : lots) if (norm(l.itemName).equals(key)) out.add(l);
		out.sort((a, b) -> {
			int wa = "entered".equals(a.status) ? 0 : 1, wb = "entered".equals(b.status) ? 0 : 1;
			if (wa != wb) return wa - wb;
			int qa = a.quantity == count ? 0 : 1, qb = b.quantity == count ? 0 : 1;
			return qa - qb;
		});
		return out;
	}

	/** The Dutch clock price with `lit` of `total` lights on: start with all lit, the lowest limit with none. */
	public static int clockPrice(AuctionClient.Lot lot, int lit, int total) {
		if (total <= 0) return lot.startDia;
		double p = lot.minDia + (lot.startDia - lot.minDia) * (lit / (double) total);
		return (int) Math.round(p);
	}
}
