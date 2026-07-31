package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.gui.data.Listing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;

/** Scrollable list of listings — seller, world, price, stock, and (optionally) item name. Click a row to open that seller's profile. */
public class ListingListWidget extends EntryListWidget<ListingListWidget.ListingEntry> {

	public ListingListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
		super(client, width, height, y, itemHeight);
	}

	@Override
	public int getRowWidth() {
		return Math.min(420, width - 20);
	}

	@Override
	public void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
		// no accessibility narration for this first pass
	}

	public void clearAllEntries() {
		clearEntries();
	}

	public void addListingEntry(ListingEntry entry) {
		addEntry(entry);
	}

	/** Use on a single item's page — item name is already known from context, so it's left off the row. */
	public static ListingEntry of(Listing listing, java.util.function.Consumer<String> onClickSeller) {
		return new ListingEntry(listing, false, onClickSeller);
	}

	/** Use on a browse-everything view (ListingsScreen) — shows the item name on each row since it varies per row. */
	public static ListingEntry withItemName(Listing listing, java.util.function.Consumer<String> onClickSeller) {
		return new ListingEntry(listing, true, onClickSeller);
	}

	public static final class ListingEntry extends EntryListWidget.Entry<ListingEntry> {
		private final Listing listing;
		private final boolean showItemName;
		private final java.util.function.Consumer<String> onClickSeller;

		private ListingEntry(Listing listing, boolean showItemName, java.util.function.Consumer<String> onClickSeller) {
			this.listing = listing;
			this.showItemName = showItemName;
			this.onClickSeller = onClickSeller;
		}

		@Override
		public void render(DrawContext context, int index, int rowY, boolean hovered, float tickDelta) {
			var tr = MinecraftClient.getInstance().textRenderer;
			int x = getX() + 4;
			int y = getY();

			if (hovered) {
				context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
			}

			String type = listing.bulk ? "Bulk" : listing.bundled ? "Bundled" : "Single";
			if (showItemName) {
				context.drawTextWithShadow(tr, listing.itemName, x, y + 2, 0xFFFFFFFF);
				context.drawTextWithShadow(tr, listing.seller + " - " + listing.world + " - " + type, x, y + 12, 0xFF8FA593);
			} else {
				context.drawTextWithShadow(tr, listing.seller, x, y + 2, 0xFFB7E23D);
				context.drawTextWithShadow(tr, listing.world + " - " + type, x, y + 12, 0xFF8FA593);
			}

			String price = listing.priceLabel + " / " + listing.stackSize;
			int priceWidth = tr.getWidth(price);
			context.drawTextWithShadow(tr, price, getX() + getWidth() - priceWidth - 6, y + 2, 0xFFD9C89A);

			String stock = "x" + listing.amount + " (" + listing.stacksInStock + ")";
			int stockWidth = tr.getWidth(stock);
			context.drawTextWithShadow(tr, stock, getX() + getWidth() - stockWidth - 6, y + 12, 0xFF8FA593);
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
			if (onClickSeller != null) onClickSeller.accept(listing.seller);
			return true;
		}
	}
}
