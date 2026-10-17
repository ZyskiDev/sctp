package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.auction.AuctionClient;
import com.snailtools.shoplogger.auction.AuctionHost;
import com.snailtools.shoplogger.auction.DutchClock;
import com.snailtools.shoplogger.gui.ui.Draw;
import com.snailtools.shoplogger.gui.ui.Section;
import com.snailtools.shoplogger.gui.ui.Theme;
import com.snailtools.shoplogger.gui.ui.UiButton;
import com.snailtools.shoplogger.gui.ui.UiField;
import com.snailtools.shoplogger.gui.ui.UiScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One lot on the Auctions screen: sell it (any price format), mark it unsold, undo, or open the Dutch clock. */
public class AuctionLotScreen extends UiScreen {

	private final AuctionsScreen owner;
	private final AuctionClient.Auction auction;
	private AuctionClient.Lot lot;
	private UiField price;
	private UiButton soldBtn;
	private String message;
	private int messageColor = Theme.MUTED;
	private boolean busy;

	public AuctionLotScreen(AuctionsScreen owner, AuctionClient.Auction auction, AuctionClient.Lot lot) {
		super((lot.quantity > 1 ? lot.quantity + "× " : "") + lot.itemName, owner, Section.AUCTIONS);
		this.owner = owner;
		this.auction = auction;
		this.lot = lot;
	}

	@Override
	protected void initContent() {
		int x = contentX(), y = contentY() + 86, w = Math.min(contentW(), 360);
		price = new UiField(font, x, y, w - 112, 20, "Price, e.g. 2stx63db or 58db", false);
		addRenderableOnly(price.frame());
		addRenderableWidget(price.box);
		soldBtn = addRenderableWidget(new UiButton(x + w - 106, y, 106, 20, "Sold", UiButton.Style.PRIMARY, this::sell));
		y += 28;
		int bx = x;
		addRenderableWidget(new UiButton(bx, y, 80, 18, "Unsold", UiButton.Style.SECONDARY, () -> set("unsold", null)));
		bx += 86;
		if (!"entered".equals(lot.status)) {
			addRenderableWidget(new UiButton(bx, y, 130, 18, "Undo: not auctioned", UiButton.Style.GHOST, () -> set("entered", null))
					.tooltip("Back to waiting, as if it hasn't been auctioned yet"));
			bx += 136;
		}
		if (auction.dutch() && "entered".equals(lot.status) && !lot.hostPicks) {
			addRenderableWidget(new UiButton(bx, y, 100, 18, "Dutch clock", UiButton.Style.GHOST, () -> {
				DutchClock.show(lot, auction.title);
				minecraft.setScreenAndShow(null);
			}).tooltip("Show the price from the nearest copper bulb circle while you play. /sold sells at that price."));
		}
		setInitialFocus(price.box);
	}

	private void sell() {
		Integer p = AuctionHost.parseDia(price.value());
		if (p == null) { say("Type a price like 2stx63db, 84db3dia, 1stx or 58db.", Theme.WARN); return; }
		set("sold", p);
	}

	private void set(String status, Integer soldDia) {
		if (busy) return;
		busy = true;
		AuctionClient.result(lot.id, status, soldDia).whenComplete((r, ex) -> minecraft.execute(() -> {
			busy = false;
			if (ex != null) { say(AuctionClient.messageOf(ex), Theme.BAD); return; }
			if (r.item != null) lot = r.item;
			DutchClock.hideIf(lot.id);
			owner.lotChanged();
			minecraft.setScreenAndShow(owner);
		}));
	}

	private void say(String text, int color) { message = text; messageColor = color; }

	@Override
	protected void extractPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = contentX(), y = contentY();
		Draw.text(g, font, "From " + lot.seller, x, y, Theme.TEXT);
		String prices = lot.hostPicks ? "Host picks the price"
				: auction.dutch() ? "Starting bid " + AuctionHost.fmtDia(lot.startDia) + " · lowest limit " + AuctionHost.fmtDia(lot.minDia)
				: "Starting price " + AuctionHost.fmtDia(lot.startDia);
		Draw.text(g, font, prices, x, y + 14, Theme.MUTED);
		if (lot.estimateDia != null) Draw.text(g, font, "SCTP value ≈ " + AuctionHost.fmtDia((int) Math.round(lot.estimateDia)), x, y + 28, Theme.MUTED);
		String status = switch (lot.status) {
			case "sold" -> "Sold for " + AuctionHost.fmtDia(lot.soldDia == null ? 0 : lot.soldDia) + (lot.payoutDia != null ? " · seller gets " + AuctionHost.fmtDia(lot.payoutDia) : "");
			case "unsold" -> "Didn't sell";
			default -> "Not auctioned yet";
		};
		Draw.text(g, font, status, x, y + 42, "sold".equals(lot.status) ? Theme.ACCENT : Theme.TEXT);

		// what the typed price reads as
		String typed = price == null ? "" : price.value().trim();
		if (!typed.isEmpty()) {
			Integer p = AuctionHost.parseDia(typed);
			String read = p == null ? "Can't read that price" : "= " + AuctionHost.fmtDia(p) + " (" + p + " dia)";
			boolean below = p != null && !lot.hostPicks && lot.minDia > 0 && p < lot.minDia;
			Draw.text(g, font, read + (below ? " · below the lowest limit" : ""), x, y + 64, p == null || below ? Theme.WARN : Theme.ACCENT);
			if (soldBtn != null) soldBtn.setMessage(net.minecraft.network.chat.Component.literal(below ? "Sell anyway" : "Sold"));
		}
		if (message != null) Draw.text(g, font, message, x, y + 140, messageColor);
	}
}
