package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.gui.data.Listing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;

/** Scrollable list of listings — seller, world, price, stock, and (optionally) item name. Click a row to open that seller's profile. */
public class ListingListWidget extends AbstractSelectionList<ListingListWidget.ListingEntry> {

	public ListingListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
		super(client, width, height, y, itemHeight);
	}

	@Override
	public int getRowWidth() {
		return Math.min(420, width - 20);
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
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

	public static final class ListingEntry extends AbstractSelectionList.Entry<ListingEntry> {
		private final Listing listing;
		private final boolean showItemName;
		private final java.util.function.Consumer<String> onClickSeller;

		private ListingEntry(Listing listing, boolean showItemName, java.util.function.Consumer<String> onClickSeller) {
			this.listing = listing;
			this.showItemName = showItemName;
			this.onClickSeller = onClickSeller;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor context, int index, int rowY, boolean hovered, float tickDelta) {
			var font = Minecraft.getInstance().font;
			int x = getX() + 4;
			int y = getY();

			if (hovered) {
				context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
			}

			String type = listing.bulk ? "Bulk" : listing.bundled ? "Bundled" : "Single";
			if (showItemName) {
				context.text(font, listing.itemName, x, y + 2, 0xFFFFFFFF);
				context.text(font, listing.seller + " - " + listing.world + " - " + type, x, y + 12, 0xFF8FA593);
			} else {
				context.text(font, listing.seller, x, y + 2, 0xFFB7E23D);
				context.text(font, listing.world + " - " + type, x, y + 12, 0xFF8FA593);
			}

			String price = listing.priceLabel + " / " + listing.stackSize;
			int priceWidth = font.width(price);
			context.text(font, price, getX() + getWidth() - priceWidth - 6, y + 2, 0xFFD9C89A);

			String stock = "x" + listing.amount + " (" + listing.stacksInStock + ")";
			int stockWidth = font.width(stock);
			context.text(font, stock, getX() + getWidth() - stockWidth - 6, y + 12, 0xFF8FA593);
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (onClickSeller != null) onClickSeller.accept(listing.seller);
			return true;
		}
	}
}
