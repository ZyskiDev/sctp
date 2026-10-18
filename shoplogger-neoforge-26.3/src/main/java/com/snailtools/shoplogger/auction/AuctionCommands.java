package com.snailtools.shoplogger.auction;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.ShopEntryFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The in-game auction commands, for hosts (and anyone who added the auction
 * with its code). Each loader registers them and passes the raw arguments:
 *   /sold [price]      the held item's lot is sold (no price during a Dutch
 *                      lot with the clock popup = the price it shows)
 *   /unsold            the held item's lot didn't sell
 *   /info              the held item's lot: seller, prices, value; for a Dutch
 *                      auction it also opens the clock popup
 *   /info <price>      sets the lot's price first (Dutch: /info <start> to <lowest>)
 *   /auction <player>  enter the held shulker's rares for that player
 * A "#<lot id>" argument (from the click-to-pick lists) names the lot directly.
 */
public final class AuctionCommands {

	private AuctionCommands() {}

	private static final Pattern MC_NAME = Pattern.compile("^[.*]?[A-Za-z0-9_]{2,16}$");
	private static final long CONFIRM_MS = 15_000;
	private static String pendingBelowLimit; // "lotId|price" of a sale below the lowest limit, waiting for a second /sold
	private static long pendingAt;

	/** Entry point from both loaders' command registration. Always returns 1 (feedback goes to chat). */
	public static int run(String command, String args) {
		String a = args == null ? "" : args.trim();
		try {
			switch (command) {
				case "sold" -> sold(a);
				case "unsold" -> unsold(a);
				case "info" -> info(a);
				case "auction" -> enter(a);
				case "sctpauction" -> helper(a);
				default -> { }
			}
		} catch (RuntimeException e) {
			error("Something went wrong: " + e.getMessage());
		}
		return 1;
	}

	// ---------------- /sold, /unsold ----------------

	private static void sold(String args) {
		String lotId = lotIdIn(args);
		String priceText = args.replaceAll("#\\S+", "").trim();
		Integer price = null;
		if (!priceText.isEmpty()) {
			price = AuctionHost.parseDia(priceText);
			if (price == null) { error("Couldn't read \"" + priceText + "\" as a price. Try 2stx63db, 84db3dia, 1stx or 58db."); return; }
		}
		final Integer typed = price;
		pickLot(lotId, false, "sold" + (priceText.isEmpty() ? "" : " " + priceText), (auction, lot) -> {
			Integer p = typed != null ? typed : DutchClock.currentPrice(lot.id);
			if (p == null) {
				error("Add the price, e.g. /sold 58db" + (auction.dutch() ? ", or run /info first to sell at the Dutch clock's price." : "."));
				return;
			}
			if (!lot.hostPicks && lot.minDia > 0 && p < lot.minDia) {
				String key = lot.id + "|" + p;
				if (!key.equals(pendingBelowLimit) || System.currentTimeMillis() - pendingAt > CONFIRM_MS) {
					pendingBelowLimit = key;
					pendingAt = System.currentTimeMillis();
					warn(AuctionHost.fmtDia(p) + " is below " + lot.seller + "'s lowest limit (" + AuctionHost.fmtDia(lot.minDia) + "). Run the same /sold again within 15 seconds to sell anyway.");
					return;
				}
			}
			pendingBelowLimit = null;
			final int sellAt = p;
			AuctionClient.result(lot.id, "sold", sellAt).whenComplete((r, ex) -> Minecraft.getInstance().execute(() -> {
				if (ex != null) { error(AuctionClient.messageOf(ex)); return; }
				DutchClock.hideIf(lot.id);
				AuctionClient.Lot s = r.item;
				MutableComponent msg = pre(ChatFormat.SUCCESS, "Sold " + lotName(lot) + " from " + lot.seller + " for " + AuctionHost.fmtDia(sellAt) + ".")
						.append(Component.literal(s != null && s.cutDia != null ? " Cut " + AuctionHost.fmtDia(s.cutDia) + ", " + lot.seller + " gets " + AuctionHost.fmtDia(s.payoutDia) + ". " : " ").withStyle(ChatFormatting.GRAY))
						.append(undoButton(lot.id, r.previous));
				send(msg);
			}));
		});
	}

	private static void unsold(String args) {
		pickLot(lotIdIn(args), false, "unsold", (auction, lot) ->
				AuctionClient.result(lot.id, "unsold", null).whenComplete((r, ex) -> Minecraft.getInstance().execute(() -> {
					if (ex != null) { error(AuctionClient.messageOf(ex)); return; }
					DutchClock.hideIf(lot.id);
					send(pre(ChatFormat.INFO, lotName(lot) + " from " + lot.seller + " didn't sell. ").append(undoButton(lot.id, r.previous)));
				})));
	}

	private static Component undoButton(String lotId, AuctionClient.Previous prev) {
		String status = prev == null || prev.status == null ? "entered" : prev.status;
		String cmd = "/sctpauction undo " + lotId + " " + status + (prev != null && prev.soldDia != null ? " " + prev.soldDia : "");
		return Component.literal("[Undo]").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.RunCommand(cmd))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Put this lot back the way it was"))));
	}

	// ---------------- /info ----------------

	private static void info(String args) {
		String lotId = lotIdIn(args);
		String priceText = args.replaceAll("#\\S+", "").trim();
		if (priceText.isEmpty()) { pickLot(lotId, true, "info", AuctionCommands::showInfo); return; }
		Integer[] prices = parsePrices(priceText);
		if (prices == null) { error("Couldn't read \"" + priceText + "\" as a price. Try /info 58db, or for a Dutch auction /info 2stx to 40db (starting bid to lowest limit)."); return; }
		pickLot(lotId, false, "info " + priceText, (auction, lot) -> {
			if (auction.dutch() && prices[1] == null) {
				error("A Dutch auction needs a starting bid and a lowest limit, e.g. /info " + priceText + " to 40db.");
				return;
			}
			int start = prices[0], min = prices[1] != null ? prices[1] : prices[0];
			AuctionClient.price(lot.id, start, min).whenComplete((r, ex) -> Minecraft.getInstance().execute(() -> {
				if (ex != null) { error(AuctionClient.messageOf(ex)); return; }
				send(pre(ChatFormat.SUCCESS, "Price set for " + lotName(lot) + "."));
				showInfo(auction, r.item != null ? r.item : lot);
			}));
		});
	}

	/** "58db" -> {522, null}; "2stx to 40db", "2stx / 40db" or "2stx 40db" -> {start, lowest}. Null if unreadable. */
	private static Integer[] parsePrices(String text) {
		String[] parts = text.split("(?i)\\s+to\\s+|\\s*/\\s*");
		if (parts.length > 2) return null;
		if (parts.length == 2) {
			Integer a = AuctionHost.parseDia(parts[0]), b = AuctionHost.parseDia(parts[1]);
			return a != null && b != null ? new Integer[] { a, b } : null;
		}
		String[] tokens = text.trim().split("\\s+");
		if (tokens.length == 2) {
			Integer a = AuctionHost.parseDia(tokens[0]), b = AuctionHost.parseDia(tokens[1]);
			if (a != null && b != null) return new Integer[] { a, b };
		}
		Integer one = AuctionHost.parseDia(text);
		return one == null ? null : new Integer[] { one, null };
	}

	private static void showInfo(AuctionClient.Auction auction, AuctionClient.Lot lot) {
		{
			List<String> lines = new ArrayList<>();
			String prices;
			if (lot.hostPicks) prices = "Host picks the price";
			else if (!auction.dutch()) prices = "Starting price " + AuctionHost.fmtDia(lot.startDia);
			else prices = "Starting bid " + AuctionHost.fmtDia(lot.startDia) + " · lowest " + AuctionHost.fmtDia(lot.minDia);
			lines.add(prices + (lot.estimateDia != null ? " · SCTP value ≈ " + AuctionHost.fmtDia((int) Math.round(lot.estimateDia)) : ""));
			String status = switch (lot.status) {
				case "sold" -> "Sold for " + AuctionHost.fmtDia(lot.soldDia == null ? 0 : lot.soldDia);
				case "unsold" -> "Didn't sell";
				default -> "Not auctioned yet";
			};
			lines.add(status);
			send(pre(ChatFormat.RESULT, lotName(lot) + " from " + lot.seller));
			for (String l : lines) send(Component.literal("  " + l).withStyle(ChatFormatting.GRAY));
			if (auction.dutch() && "entered".equals(lot.status) && !lot.hostPicks) {
				DutchClock.show(lot, auction.title);
				send(Component.literal("  Dutch clock is on (top right). /sold sells at its price.").withStyle(ChatFormatting.GOLD));
			} else if (lot.hostPicks && "entered".equals(lot.status)) {
				send(Component.literal(auction.dutch()
						? "  Set the price: /info <starting bid> to <lowest>, e.g. /info 2stx to 40db"
						: "  Set the price: /info <price>, e.g. /info 58db").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	// ---------------- picking the lot ----------------

	private interface LotAction { void run(AuctionClient.Auction auction, AuctionClient.Lot lot); }

	/**
	 * Finds the lot for the held item in the running auction (or the lot named
	 * by #id) and runs the action on the game thread. With several possible
	 * lots it lists them with [Pick] buttons that rerun `rerun #id`.
	 */
	private static void pickLot(String lotId, boolean anyStatus, String rerun, LotAction action) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		ItemStack held = mc.player.getMainHandItem();
		if (lotId == null && held.isEmpty()) { error("Hold the item from the lot first."); return; }
		String name = held.isEmpty() ? "" : ShopEntryFactory.displayNameFor(held);
		int count = held.getCount();
		AuctionHost.current(false).thenCompose(auction -> AuctionClient.items(auction.id).thenApply(r -> Map.entry(auction, r.items == null ? List.<AuctionClient.Lot>of() : r.items)))
				.whenComplete((res, ex) -> mc.execute(() -> {
					if (ex != null) { error(AuctionClient.messageOf(ex)); return; }
					AuctionClient.Auction auction = res.getKey();
					List<AuctionClient.Lot> lots = res.getValue();
					if (lotId != null) {
						for (AuctionClient.Lot l : lots) if (l.id.equals(lotId)) { action.run(auction, l); return; }
						error("That lot isn't in " + auction.title + " anymore.");
						return;
					}
					List<AuctionClient.Lot> found = AuctionHost.matches(lots, name, count);
					if (found.isEmpty()) { error("No lot in " + auction.title + " is " + name + "."); return; }
					List<AuctionClient.Lot> open = new ArrayList<>();
					for (AuctionClient.Lot l : found) if ("entered".equals(l.status)) open.add(l);
					List<AuctionClient.Lot> choice = anyStatus ? found : open;
					if (choice.isEmpty()) {
						error(name + " is already " + (found.get(0).status.equals("sold") ? "sold" : "marked unsold") + ". Change it on the Auctions screen if that's wrong.");
						return;
					}
					List<AuctionClient.Lot> sameSize = new ArrayList<>();
					for (AuctionClient.Lot l : choice) if (l.quantity == count) sameSize.add(l);
					if (sameSize.size() == 1) { action.run(auction, sameSize.get(0)); return; }
					if (sameSize.isEmpty() && choice.size() == 1) { action.run(auction, choice.get(0)); return; }
					List<AuctionClient.Lot> list = sameSize.isEmpty() ? choice : sameSize;
					send(pre(ChatFormat.INFO, list.size() + " lots match " + name + ". Which one?"));
					for (AuctionClient.Lot l : list) {
						String line = "  " + lotName(l) + " · " + l.seller + " · " + (l.hostPicks ? "host picks" : AuctionHost.fmtDia(l.startDia)) + ("entered".equals(l.status) ? "" : " · " + l.status) + " ";
						send(Component.literal(line).withStyle(ChatFormatting.GRAY).append(Component.literal("[Pick]").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
								.withClickEvent(new ClickEvent.RunCommand("/" + rerun + " #" + l.id))
								.withHoverEvent(new HoverEvent.ShowText(Component.literal("Use " + l.seller + "'s lot"))))));
					}
				}));
	}

	// ---------------- /auction <player> ----------------

	private static void enter(String args) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String[] parts = args.split("\\s+");
		String player = parts.length > 0 ? parts[0] : "";
		String mode = parts.length > 1 ? parts[1].toLowerCase() : null;
		if (!MC_NAME.matcher(player).matches()) { error("Use /auction <username> while holding their shulker."); return; }
		if (mode != null && !List.of("add", "override", "merge").contains(mode)) { error("Choose add, override or merge."); return; }
		ItemStack held = mc.player.getMainHandItem();
		List<ItemStack> contents = ShopEntryFactory.readShulkerContents(held);
		if (contents == null || contents.isEmpty()) { error("Hold the player's shulker box (with their items in it)."); return; }
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (ItemStack s : contents) counts.merge(ShopEntryFactory.displayNameFor(s), s.getCount(), Integer::sum);
		List<Map<String, Object>> items = new ArrayList<>();
		counts.forEach((n, c) -> items.add(Map.of("name", n, "count", c)));

		AuctionHost.current(true).thenCompose(auction -> AuctionClient.enter(auction.id, player, items, mode).thenApply(r -> Map.entry(auction, r)))
				.whenComplete((res, ex) -> mc.execute(() -> {
					if (ex != null) { error(AuctionClient.messageOf(ex)); return; }
					AuctionClient.Auction auction = res.getKey();
					AuctionClient.EnterResponse r = res.getValue();
					if (r.needsChoice) {
						send(pre(ChatFormat.INFO, player + " already has " + r.existing.size() + " lot" + (r.existing.size() == 1 ? "" : "s") + " in " + auction.title + " (entered on the site):"));
						for (AuctionClient.Lot l : r.existing) send(Component.literal("  " + lotName(l) + " · " + (l.hostPicks ? "host picks" : AuctionHost.fmtDia(l.startDia)) + ("entered".equals(l.status) ? "" : " · " + l.status)).withStyle(ChatFormatting.GRAY));
						send(Component.literal("  This shulker has " + r.incoming.size() + " rare" + (r.incoming.size() == 1 ? "" : "s") + ". ").withStyle(ChatFormatting.GRAY)
								.append(choice("ADD", "/auction " + player + " add", "Keep their entries and add everything in this shulker"))
								.append(Component.literal(" "))
								.append(choice("OVERRIDE", "/auction " + player + " override", "Replace their entries with what's in this shulker"))
								.append(Component.literal(" "))
								.append(choice("MERGE", "/auction " + player + " merge", "Only add the rares they haven't entered yet")));
						return;
					}
					if (r.message != null) send(pre(ChatFormat.INFO, r.message));
					int added = r.added == null ? 0 : r.added.size();
					if (added > 0) {
						send(pre(ChatFormat.SUCCESS, "Entered " + added + " lot" + (added == 1 ? "" : "s") + " for " + player + " in " + auction.title
								+ (r.replaced > 0 ? " (replaced " + r.replaced + ")" : "") + (r.skipped > 0 ? " (" + r.skipped + " already entered)" : "") + ". You pick the prices."));
						StringBuilder names = new StringBuilder();
						for (AuctionClient.Lot l : r.added) names.append(names.length() > 0 ? ", " : "").append(lotName(l));
						send(Component.literal("  " + names).withStyle(ChatFormatting.GRAY));
					}
					if (r.unknown != null && !r.unknown.isEmpty()) {
						warn("Not rares SCTP knows, so not entered: " + String.join(", ", r.unknown));
					}
				}));
	}

	private static Component choice(String label, String command, String hover) {
		return Component.literal("[" + label + "]").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	// ---------------- click targets ----------------

	/** /sctpauction undo <lotId> <status> [soldDia] */
	private static void helper(String args) {
		String[] p = args.split("\\s+");
		if (p.length >= 3 && "undo".equals(p[0])) {
			Integer sold = p.length >= 4 ? Integer.valueOf(p[3]) : null;
			AuctionClient.result(p[1], p[2], sold).whenComplete((r, ex) -> Minecraft.getInstance().execute(() -> {
				if (ex != null) { error(AuctionClient.messageOf(ex)); return; }
				send(pre(ChatFormat.INFO, "Undone: " + (r.item != null ? lotName(r.item) : "the lot") + " is back to " +
						("sold".equals(p[2]) ? "sold" : "unsold".equals(p[2]) ? "unsold" : "not auctioned yet") + "."));
			}));
		}
	}

	// ---------------- helpers ----------------

	private static String lotIdIn(String args) {
		for (String t : args.split("\\s+")) if (t.startsWith("#") && t.length() > 1) return t.substring(1);
		return null;
	}

	private static String lotName(AuctionClient.Lot l) {
		return (l.quantity > 1 ? l.quantity + "× " : "") + l.itemName;
	}

	private static void send(Component c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(c);
	}

	/** "[ShopLogger] "-prefixed text that more can be appended to. */
	private static MutableComponent pre(ChatFormatting color, String text) {
		return Component.empty().append(ChatFormat.prefixed(color, text));
	}

	private static void error(String msg) { ChatFormat.send(Minecraft.getInstance(), ChatFormat.ERROR, msg); }
	private static void warn(String msg) { ChatFormat.send(Minecraft.getInstance(), ChatFormat.INFO, msg); }
}
