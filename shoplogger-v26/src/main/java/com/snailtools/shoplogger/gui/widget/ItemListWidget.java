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
		ItemStack stack = ItemStack.EMPTY;
		if (baseItem != null) {
			Identifier id = Identifier.tryParse(baseItem);
			if (id != null) {
				Item item = BuiltInRegistries.ITEM.getValue(id);
				if (item != null) stack = new ItemStack(item);
			}
		}
		return new ItemEntry(name, null, stack, null, onClick);
	}

	public static ItemEntry forRare(String name, String category, String textureUrl, Runnable onClick) {
		return new ItemEntry(name, category, ItemStack.EMPTY, textureUrl, onClick);
	}

	public static final class ItemEntry extends AbstractSelectionList.Entry<ItemEntry> {
		private final String name;
		private final String subtitle;
		private final ItemStack vanillaIcon;
		private final String textureUrl;
		private final Runnable onClick;

		private ItemEntry(String name, String subtitle, ItemStack vanillaIcon, String textureUrl, Runnable onClick) {
			this.name = name;
			this.subtitle = subtitle;
			this.vanillaIcon = vanillaIcon;
			this.textureUrl = textureUrl;
			this.onClick = onClick;
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
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			if (onClick != null) onClick.run();
			return true;
		}
	}
}
