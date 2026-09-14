package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.gui.RemoteTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Scrollable, clickable list of catalog items — used for both the vanilla
 * and rare item libraries. Vanilla entries render the real in-game item
 * icon (looked up by baseItem); rare entries render their custom texture,
 * fetched and cached the first time they're shown (see RemoteTextureCache).
 */
public class ItemListWidget extends AbstractSelectionList<ItemListWidget.ItemEntry> {

	private static final Identifier PLACEHOLDER_ICON = Identifier.fromNamespaceAndPath("minecraft", "textures/item/barrier.png");

	public ItemListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
		super(client, width, height, y, itemHeight);
	}

	@Override
	public int getRowWidth() {
		return Math.min(360, width - 20);
	}

	@Override
	protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		// no accessibility narration for this first pass
	}

	public void clearAllEntries() {
		clearEntries();
	}

	public void addItemEntry(ItemEntry entry) {
		addEntry(entry);
	}

	public static ItemEntry forVanilla(String name, String baseItem, Runnable onClick) {
		return forVanilla(name, baseItem, null, onClick);
	}

	public static ItemEntry forVanilla(String name, String baseItem, String subtitle, Runnable onClick) {
		return forVanilla(name, baseItem, subtitle, onClick, null);
	}

	/** onOpenPage, if given, draws a secondary "View" button at the row's right edge — see ItemEntry. */
	public static ItemEntry forVanilla(String name, String baseItem, String subtitle, Runnable onClick, Runnable onOpenPage) {
		ItemStack stack = ItemStack.EMPTY;
		if (baseItem != null) {
			Identifier id = Identifier.tryParse(baseItem);
			if (id != null) {
				Item item = BuiltInRegistries.ITEM.getValue(id);
				if (item != null) stack = new ItemStack(item);
			}
		}
		return new ItemEntry(name, subtitle, stack, null, onClick, onOpenPage);
	}

	public static ItemEntry forRare(String name, String category, String textureUrl, Runnable onClick) {
		return forRare(name, category, textureUrl, onClick, null);
	}

	/** onOpenPage, if given, draws a secondary "View" button at the row's right edge — see ItemEntry. */
	public static ItemEntry forRare(String name, String category, String textureUrl, Runnable onClick, Runnable onOpenPage) {
		return new ItemEntry(name, category, ItemStack.EMPTY, textureUrl, onClick, onOpenPage);
	}

	public static final class ItemEntry extends AbstractSelectionList.Entry<ItemEntry> {
		// Reserved column at the row's right edge for the secondary "Search"
		// button — jumps straight to the item's detail page (current listings,
		// price history) instead of whatever the whole-row click does
		// (add/remove/open options), same pattern as ListingListWidget's own
		// [TP] button column.
		private static final int OPEN_PAGE_BUTTON_WIDTH = 50;

		private final String name;
		private final String subtitle;
		private final ItemStack vanillaIcon;
		private final String textureUrl;
		private final Runnable onClick;
		private final Runnable onOpenPage;

		private ItemEntry(String name, String subtitle, ItemStack vanillaIcon, String textureUrl, Runnable onClick, Runnable onOpenPage) {
			this.name = name;
			this.subtitle = subtitle;
			this.vanillaIcon = vanillaIcon;
			this.textureUrl = textureUrl;
			this.onClick = onClick;
			this.onOpenPage = onOpenPage;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor context, int index, int rowY, boolean hovered, float tickDelta) {
			int x = getX() + 4;
			int y = getY();
			int iconSize = 16;

			if (!vanillaIcon.isEmpty()) {
				context.item(vanillaIcon, x, y + 2);
			} else {
				Identifier tex = RemoteTextureCache.get(Minecraft.getInstance(), textureUrl, () -> {});
				if (tex != null) {
					context.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
				} else {
					context.blit(RenderPipelines.GUI_TEXTURED, PLACEHOLDER_ICON, x, y + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
				}
			}

			int textX = x + iconSize + 6;
			var client = Minecraft.getInstance();
			context.text(client.font, name, textX, y + 2, 0xFFFFFFFF);
			if (subtitle != null) {
				context.text(client.font, subtitle, textX, y + 12, 0xFF8FA593);
			}

			if (hovered) {
				context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
			}

			if (onOpenPage != null) {
				int btnX = getX() + getWidth() - OPEN_PAGE_BUTTON_WIDTH;
				context.fill(btnX, getY(), getX() + getWidth(), getY() + getHeight(), 0xFF2E6B45);
				context.centeredText(client.font, "Search", btnX + OPEN_PAGE_BUTTON_WIDTH / 2, getY() + getHeight() / 2 - 4, 0xFFFFFFFF);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (onOpenPage != null) {
				int btnX = getX() + getWidth() - OPEN_PAGE_BUTTON_WIDTH;
				if (event.x() >= btnX && event.x() < getX() + getWidth()
						&& event.y() >= getY() && event.y() < getY() + getHeight()) {
					onOpenPage.run();
					return true;
				}
			}
			if (onClick != null) onClick.run();
			return true;
		}
	}
}
