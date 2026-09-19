package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.ShopReporter;
import com.snailtools.shoplogger.TeleportHighlight;
import com.snailtools.shoplogger.gui.data.Listing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.core.BlockPos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
		// Reserved column at the row's right edge for the "Teleport" button —
		// price/stock text is right-aligned to the left of it instead of the
		// row's true edge, so nothing overlaps.
		private static final int TELEPORT_BUTTON_WIDTH = 18;
		// Red one-click report button, sits just left of the TP button (or at the
		// row's edge when there's no TP). Marketplace rows have no shop to report.
		private static final int REPORT_BUTTON_WIDTH = 18;
		private static final Pattern POSITION_PATTERN = Pattern.compile("^\\(?(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)?$");

		private final Listing listing;
		private final boolean showItemName;
		private final java.util.function.Consumer<String> onClickSeller;
		private final BlockPos teleportTarget; // null if listing.position isn't parseable coordinates
		private final boolean canReport;
		private boolean reported;

		private ListingEntry(Listing listing, boolean showItemName, java.util.function.Consumer<String> onClickSeller) {
			this.listing = listing;
			this.showItemName = showItemName;
			this.onClickSeller = onClickSeller;
			this.teleportTarget = parsePosition(listing.position);
			this.canReport = !listing.marketplace;
			this.reported = canReport && ShopReporter.alreadyReported(ShopReporter.fromListing(listing));
		}

		private int buttonsWidth() {
			return (teleportTarget != null ? TELEPORT_BUTTON_WIDTH : 0) + (canReport ? REPORT_BUTTON_WIDTH : 0);
		}

		/** Left edge of the report button — it sits directly left of the TP button, if any. */
		private int reportButtonX() {
			return getX() + getWidth() - (teleportTarget != null ? TELEPORT_BUTTON_WIDTH : 0) - REPORT_BUTTON_WIDTH;
		}

		/**
		 * listing.position is BlockPos#toShortString() ("x, y, z") for scanned
		 * listings, but admin-added manual listings can carry arbitrary free
		 * text — return null rather than guessing so the button just doesn't
		 * render for those instead of misbehaving.
		 */
		private static BlockPos parsePosition(String position) {
			if (position == null) return null;
			Matcher m = POSITION_PATTERN.matcher(position.trim());
			if (!m.matches()) return null;
			try {
				return new BlockPos(
						Integer.parseInt(m.group(1)),
						Integer.parseInt(m.group(2)),
						Integer.parseInt(m.group(3)));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		@Override
		public void extractContent(GuiGraphicsExtractor context, int index, int rowY, boolean hovered, float tickDelta) {
			var font = Minecraft.getInstance().font;
			int x = getX() + 4;
			int y = getY();
			int textRightEdge = getX() + getWidth() - (buttonsWidth() > 0 ? buttonsWidth() + 4 : 0);

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
			context.text(font, price, textRightEdge - priceWidth - 6, y + 2, 0xFFD9C89A);

			String stock = "x" + listing.amount + " (" + listing.stacksInStock + ")";
			int stockWidth = font.width(stock);
			context.text(font, stock, textRightEdge - stockWidth - 6, y + 12, 0xFF8FA593);

			if (teleportTarget != null) {
				int btnX = getX() + getWidth() - TELEPORT_BUTTON_WIDTH;
				context.fill(btnX, getY(), getX() + getWidth(), getY() + getHeight(), 0xFF2E6B45);
				context.centeredText(font, "TP", btnX + TELEPORT_BUTTON_WIDTH / 2, getY() + getHeight() / 2 - 4, 0xFFFFFFFF);
			}

			if (canReport) {
				int rx = reportButtonX();
				context.fill(rx, getY(), rx + REPORT_BUTTON_WIDTH, getY() + getHeight(), reported ? 0xFF4A4A4A : 0xFFB53A3A);
				context.centeredText(font, reported ? "\u2713" : "!", rx + REPORT_BUTTON_WIDTH / 2, getY() + getHeight() / 2 - 4, 0xFFFFFFFF);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (canReport) {
				int rx = reportButtonX();
				if (event.x() >= rx && event.x() < rx + REPORT_BUTTON_WIDTH
						&& event.y() >= getY() && event.y() < getY() + getHeight()) {
					if (!reported) {
						reported = ShopReporter.report(Minecraft.getInstance(), ShopReporter.fromListing(listing)) || ShopReporter.alreadyReported(ShopReporter.fromListing(listing));
					}
					return true;
				}
			}
			if (teleportTarget != null) {
				int btnX = getX() + getWidth() - TELEPORT_BUTTON_WIDTH;
				if (event.x() >= btnX && event.x() < getX() + getWidth()
						&& event.y() >= getY() && event.y() < getY() + getHeight()) {
					Minecraft client = Minecraft.getInstance();
					if (client.player != null && client.getConnection() != null) {
						client.getConnection().sendCommand("shop " + listing.seller);
					}
					TeleportHighlight.getInstance().arm(listing.world, teleportTarget);
					return true;
				}
			}
			if (onClickSeller != null) onClickSeller.accept(listing.seller);
			return true;
		}
	}
}
