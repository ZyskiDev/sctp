package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.gui.RemoteTextureCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Scrollable, clickable list of catalog items — used for both the vanilla
 * and rare item libraries. Vanilla entries render the real in-game item
 * icon (looked up by baseItem); rare entries render their custom texture,
 * fetched and cached the first time they're shown (see RemoteTextureCache).
 */
public class ItemListWidget extends EntryListWidget<ItemListWidget.ItemEntry> {

	private static final Identifier PLACEHOLDER_ICON = Identifier.of("minecraft", "textures/item/barrier.png");

	public ItemListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
		super(client, width, height, y, itemHeight);
	}

	@Override
	public int getRowWidth() {
		return Math.min(360, width - 20);
	}

	@Override
	public void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
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
				RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
				Item item = Registries.ITEM.get(key);
				if (item != null) stack = new ItemStack(item);
			}
		}
		return new ItemEntry(name, null, stack, null, onClick);
	}

	public static ItemEntry forRare(String name, String category, String textureUrl, Runnable onClick) {
		return new ItemEntry(name, category, ItemStack.EMPTY, textureUrl, onClick);
	}

	public static final class ItemEntry extends EntryListWidget.Entry<ItemEntry> {
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
		public void render(DrawContext context, int index, int rowY, boolean hovered, float tickDelta) {
			int x = getX() + 4;
			int y = getY();
			int iconSize = 16;

			if (!vanillaIcon.isEmpty()) {
				context.drawItem(vanillaIcon, x, y + 2);
			} else {
				Identifier tex = RemoteTextureCache.get(MinecraftClient.getInstance(), textureUrl, () -> {});
				if (tex != null) {
					context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, x, y + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
				} else {
					context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, PLACEHOLDER_ICON, x, y + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
				}
			}

			int textX = x + iconSize + 6;
			var client = MinecraftClient.getInstance();
			context.drawTextWithShadow(client.textRenderer, name, textX, y + 2, 0xFFFFFFFF);
			if (subtitle != null) {
				context.drawTextWithShadow(client.textRenderer, subtitle, textX, y + 12, 0xFF8FA593);
			}

			if (hovered) {
				context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
			if (onClick != null) onClick.run();
			return true;
		}
	}
}
