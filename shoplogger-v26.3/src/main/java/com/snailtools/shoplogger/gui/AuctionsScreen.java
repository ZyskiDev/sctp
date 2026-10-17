package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.auction.AuctionClient;
import com.snailtools.shoplogger.auction.AuctionHost;
import com.snailtools.shoplogger.auction.DutchClock;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiChip;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Auctions you run in-game (Settings › Advanced turns this on): the ones you
 * host, plus any added with a code from the auction admin page. Pick one to
 * see every lot (click a lot to sell it, mark it unsold or undo) and what to
 * pay each seller. The /sold, /unsold, /info and /auction commands use the
 * auction picked here when it's running.
 */
public class AuctionsScreen extends UiScreen {

	private enum Tab { LOTS, PAYOUTS }

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

	private List<AuctionClient.Auction> auctions = List.of();
	private AuctionClient.Auction selected;
	private List<AuctionClient.Lot> lots = List.of();
	private Tab tab = Tab.LOTS;
	private String message;
	private int messageColor = Theme.MUTED;
	private boolean loading = true;
	private boolean requested = false;
	private UiField codeField;
	private ItemListWidget list;
	private int infoY;

	public AuctionsScreen(Screen parent) {
		super("Auctions", parent, Section.AUCTIONS);
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY(), w = contentW();

		// add by code, right; auction picker, left
		int codeW = Math.min(150, w / 3), addW = 92;
		codeField = new UiField(font, x + w - codeW - addW - 6, y, codeW, 20, "Code from the admin page", false);
		addRenderableOnly(codeField.frame());
		addRenderableWidget(codeField.box);
		addRenderableWidget(new UiButton(x + w - addW, y, addW, 20, "Add auction", UiButton.Style.SECONDARY, this::addCode)
				.tooltip("Auctions you host show up by themselves. Add someone else's with its code from the auction admin page."));
		if (!auctions.isEmpty()) {
			addRenderableWidget(new UiChip<>(x, y, Math.max(120, w - codeW - addW - 18), 20, "Auction", auctions, selected,
					a -> a.title + " · " + when(a.auctionAt) + (a.isHost ? " · host" : ""),
					a -> { selected = a; AuctionHost.setSelectedId(a.id); loadLots(); }));
		}
		y += 26;
		infoY = y;
		y += 24;

		int tx = x;
		tx += tab(tx, y, Tab.LOTS, "Lots") + 4;
		tx += tab(tx, y, Tab.PAYOUTS, "Payouts") + 4;
		addRenderableWidget(new UiButton(x + w - 60, y, 60, 18, "Refresh", UiButton.Style.GHOST, this::loadLots));
		y += 24;

		list = new ItemListWidget(minecraft, x - 6, y, w + 12, contentBottom() - y - 12);
		addRenderableWidget(list);
		fillList();

		if (!requested) {
			requested = true;
			loadAuctions(null);
		}
	}

	private int tab(int x, int y, Tab t, String label) {
		int w = font.width(label) + 16;
		addRenderableWidget(new UiButton(x, y, w, 18, label, UiButton.Style.TAB, () -> { tab = t; rebuild(); }).selected(() -> tab == t));
		return w;
	}

	private static String when(String iso) {
		try { return WHEN.format(Instant.parse(iso)); } catch (Exception e) { return ""; }
	}

	// ---- loading ----

	private void loadAuctions(String preferId) {
		loading = true;
		AuctionClient.auctions().whenComplete((r, ex) -> minecraft.execute(() -> {
			loading = false;
			if (ex != null) { say(AuctionClient.messageOf(ex), Theme.BAD); return; }
			auctions = r.auctions == null ? List.of() : r.auctions;
			String want = preferId != null ? preferId : AuctionHost.selectedId();
			selected = null;
			for (AuctionClient.Auction a : auctions) if (a.id.equals(want)) selected = a;
			if (selected == null) for (AuctionClient.Auction a : auctions) if (a.live) { selected = a; break; }
			if (selected == null && !auctions.isEmpty()) selected = auctions.get(0);
			if (selected != null) AuctionHost.setSelectedId(selected.id);
			if (auctions.isEmpty()) say("You don't host any auctions yet. Add one with the code from the auction admin page.", Theme.MUTED);
			rebuild();
			loadLots();
		}));
	}

	private void loadLots() {
		if (selected == null) return;
		String id = selected.id;
		AuctionClient.items(id).whenComplete((r, ex) -> minecraft.execute(() -> {
			if (selected == null || !selected.id.equals(id)) return;
			if (ex != null) { say(AuctionClient.messageOf(ex), Theme.BAD); return; }
			lots = r.items == null ? List.of() : r.items;
			if (r.auction != null) selected = r.auction;
			fillList();
		}));
	}

	private void addCode() {
		String code = codeField.value().trim();
		if (code.isEmpty()) { say("Type the code from the auction admin page first.", Theme.WARN); return; }
		AuctionClient.addCode(code).whenComplete((r, ex) -> minecraft.execute(() -> {
			if (ex != null) { say(AuctionClient.messageOf(ex), Theme.BAD); return; }
			codeField.box.setValue("");
			say("Added " + r.auction.title + ".", Theme.ACCENT);
			loadAuctions(r.auction.id);
		}));
	}

	private void say(String text, int color) {
		message = text;
		messageColor = color;
	}

	// ---- the list ----

	private void fillList() {
		if (list == null) return;
		list.clearAllEntries();
		if (selected == null) return;
		if (tab == Tab.LOTS) {
			for (AuctionClient.Lot l : lots) {
				String name = (l.quantity > 1 ? l.quantity + "× " : "") + l.itemName;
				String prices = l.hostPicks ? "host picks the price"
						: selected.dutch() ? "start " + AuctionHost.fmtDia(l.startDia) + " · lowest " + AuctionHost.fmtDia(l.minDia)
						: "start " + AuctionHost.fmtDia(l.startDia);
				String sub = l.seller + " · " + prices;
				ItemListWidget.ItemEntry e = l.texture != null
						? ItemListWidget.forRare(name, sub, l.texture, () -> openLot(l))
						: ItemListWidget.forVanilla(name, null, sub, () -> openLot(l));
				switch (l.status) {
					case "sold" -> e.withBadge("Sold " + AuctionHost.fmtDia(l.soldDia == null ? 0 : l.soldDia), Theme.ACCENT);
					case "unsold" -> e.withBadge("Unsold", Theme.WARN);
					default -> e.withBadge("Waiting", Theme.MUTED);
				}
				list.addItemEntry(e);
			}
		} else {
			// what each seller gets: sold lots only
			Map<String, int[]> bySeller = new LinkedHashMap<>(); // sold, sales, cut, payout, unsold
			for (AuctionClient.Lot l : lots) {
				int[] t = bySeller.computeIfAbsent(l.seller, k -> new int[5]);
				if ("sold".equals(l.status)) {
					t[0]++;
					t[1] += l.soldDia == null ? 0 : l.soldDia;
					t[2] += l.cutDia == null ? 0 : l.cutDia;
					t[3] += l.payoutDia == null ? 0 : l.payoutDia;
				} else if ("unsold".equals(l.status)) t[4]++;
			}
			List<Map.Entry<String, int[]>> rows = new ArrayList<>(bySeller.entrySet());
			rows.sort((a, b) -> b.getValue()[3] - a.getValue()[3]);
			for (Map.Entry<String, int[]> r : rows) {
				int[] t = r.getValue();
				String sub = t[0] + " sold for " + AuctionHost.fmtDia(t[1]) + " · cut " + AuctionHost.fmtDia(t[2]) + (t[4] > 0 ? " · " + t[4] + " to return" : "");
				list.addItemEntry(ItemListWidget.forVanilla(r.getKey(), "minecraft:player_head", sub, () -> {})
						.withBadge(t[3] > 0 ? "Pay " + AuctionHost.fmtDia(t[3]) : "Nothing to pay", t[3] > 0 ? Theme.ACCENT : Theme.MUTED));
			}
		}
	}

	private void openLot(AuctionClient.Lot l) {
		minecraft.setScreenAndShow(new AuctionLotScreen(this, selected, l));
	}

	/** Called by the lot screen after a change. */
	void lotChanged() {
		loadLots();
	}

	// ---- drawing ----

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), w = contentW();
		if (loading) { Draw.text(g, font, "Loading…", x, infoY + 4, Theme.MUTED); return; }
		if (selected != null) {
			String state = selected.live ? "running now" : "finished".equals(selected.status) ? "finished" : "starts " + when(selected.auctionAt);
			String info = (selected.dutch() ? "Dutch" : "Regular") + " · " + selected.world + " · " + state + (selected.hostName != null ? " · host " + selected.hostName : "");
			Draw.text(g, font, Draw.trim(font, info, w / 2), x, infoY + 4, selected.live ? Theme.ACCENT : Theme.MUTED);
			int sold = 0, unsold = 0, sales = 0, cut = 0;
			for (AuctionClient.Lot l : lots) {
				if ("sold".equals(l.status)) { sold++; sales += l.soldDia == null ? 0 : l.soldDia; cut += l.cutDia == null ? 0 : l.cutDia; }
				else if ("unsold".equals(l.status)) unsold++;
			}
			String totals = lots.size() + " lots · " + sold + " sold · " + unsold + " unsold · sales " + AuctionHost.fmtDia(sales) + " · cut " + AuctionHost.fmtDia(cut);
			Draw.right(g, font, Draw.trim(font, totals, w / 2 - 6), x + w, infoY + 4, Theme.TEXT);
		}
		if (message != null) Draw.text(g, font, Draw.trim(font, message, w), x, contentBottom() - 9, messageColor);
		else if (selected != null) Draw.text(g, font, Draw.trim(font, "Hold a lot's item: /sold <price>, /unsold, /info. Hold a shulker: /auction <player>.", w), x, contentBottom() - 9, Theme.FAINT);
	}
}
