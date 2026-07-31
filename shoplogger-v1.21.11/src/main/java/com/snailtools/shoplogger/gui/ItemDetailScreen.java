package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.gui.data.HistoryMerge;
import com.snailtools.shoplogger.gui.data.HistoryPoint;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.MatchUtil;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ListingListWidget;
import com.snailtools.shoplogger.gui.widget.PriceChartWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-game equivalent of a 404.html item page: icon, (for rare items) the
 * info panel, current listings for the auto-detected world, and price
 * charts. Listings/charts are fetched fresh every time this screen opens —
 * same "no cache, just refetch" approach the website itself uses.
 */
public class ItemDetailScreen extends Screen {

	private final Screen parent;
	private final String name;
	private final String baseItem; // null for rare items
	private final String textureUrl; // used for rare items; vanilla items render their real ItemStack instead
	private final boolean isRare;
	private final RareItem rareData; // null for vanilla items

	private static final String ALL_WORLDS = "All";
	private String worldFilter = ALL_WORLDS;
	private List<Listing> allMatches = List.of();
	private ListingListWidget listingList;

	private List<HistoryPoint> avgHistory = List.of();
	private List<HistoryPoint> lowestHistory = List.of();
	private boolean loading = true;

	public ItemDetailScreen(Screen parent, String name, String baseItem, String textureUrl, boolean isRare, RareItem rareData) {
		super(Text.literal(name));
		this.parent = parent;
		this.name = name;
		this.baseItem = baseItem;
		this.textureUrl = textureUrl;
		this.isRare = isRare;
		this.rareData = rareData;
		ShopWorld detected = WorldSelection.get();
		this.worldFilter = detected != null ? detected.label() : ALL_WORLDS;
	}

	private int listTop;
	private int listHeight;
	private int chartsY;
	private int chartHeight;

	@Override
	protected void init() {
		int infoLines = isRare ? 7 : 0;
		listTop = 30 + infoLines * 10 + 4;

		addDrawableChild(CyclingButtonWidget.builder((String v) -> Text.literal("World: " + v), worldFilter)
				.values(ALL_WORLDS, "Firefly", "Honeybee")
				.omitKeyText()
				.build(width - 170, 6, 160, 20, Text.empty(), (btn, value) -> {
					worldFilter = value;
					refreshListingList();
				}));

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> close())
				.dimensions(10, height - 26, 60, 20).build());

		int remaining = height - listTop - 34;
		listHeight = remaining / 2 - 4;
		chartsY = listTop + listHeight + 8;
		chartHeight = remaining / 2 - 8;

		listingList = new ListingListWidget(client, width, listHeight, listTop, 22);
		addDrawableChild(listingList);

		loadData();
	}

	private void loadData() {
		WebDataClient.fetchListings().thenAccept(all -> client.execute(() -> {
			List<Listing> matched = new ArrayList<>();
			for (Listing l : all) {
				if (isRare) {
					if (MatchUtil.isRareNameMatch(l.itemName, name)) matched.add(l);
				} else {
					if (baseItem != null && baseItem.equalsIgnoreCase(l.baseItem) && name.equalsIgnoreCase(l.itemName)) matched.add(l);
				}
			}
			allMatches = matched;
			refreshListingList();
			loadHistory(matched);
		})).exceptionally(ex -> {
			client.execute(() -> loading = false);
			return null;
		});
	}

	private void loadHistory(List<Listing> matched) {
		Set<String> keys = new LinkedHashSet<>();
		if (isRare) {
			for (Listing l : matched) keys.add(MatchUtil.vanillaItemKey(l.baseItem, l.itemName));
			if (keys.isEmpty()) {
				loading = false;
				return;
			}
		} else {
			keys.add(MatchUtil.vanillaItemKey(baseItem, name));
		}

		List<java.util.concurrent.CompletableFuture<List<HistoryPoint>>> futures = new ArrayList<>();
		for (String key : keys) futures.add(WebDataClient.fetchItemHistory(key));

		java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
				.thenAccept(v -> client.execute(() -> {
					List<List<HistoryPoint>> results = new ArrayList<>();
					for (var f : futures) {
						try {
							results.add(f.join());
						} catch (Exception ignored) {
							results.add(List.of());
						}
					}
					List<HistoryPoint> merged = HistoryMerge.merge(results);
					avgHistory = merged;
					lowestHistory = merged;
					loading = false;
				}))
				.exceptionally(ex -> {
					client.execute(() -> loading = false);
					return null;
				});
	}

	private void refreshListingList() {
		listingList.clearAllEntries();
		for (Listing l : allMatches) {
			if (!ALL_WORLDS.equals(worldFilter) && !worldFilter.equalsIgnoreCase(l.world)) continue;
			listingList.addListingEntry(ListingListWidget.of(l, seller -> client.setScreen(new SellerProfileScreen(this, seller, l.world))));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {

		int iconX = 10, iconY = 8;
		if (!isRare && baseItem != null) {
			Identifier id = Identifier.tryParse(baseItem);
			if (id != null) {
				RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
				Item item = Registries.ITEM.get(key);
				if (item != null) context.drawItem(new ItemStack(item), iconX, iconY);
			}
		} else {
			Identifier tex = RemoteTextureCache.get(MinecraftClient.getInstance(), textureUrl, () -> {});
			if (tex != null) {
				context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, iconX, iconY, 0, 0, 16, 16, 16, 16);
			}
		}
		context.drawTextWithShadow(textRenderer, name, iconX + 22, iconY + 4, 0xFFFFFFFF);

		if (isRare && rareData != null) {
			drawRareInfo(context, 30);
		}

		super.render(context, mouseX, mouseY, delta);

		int chartW = (width - 24) / 2;
		if (loading) {
			context.drawCenteredTextWithShadow(textRenderer, "Loading...", width / 2, chartsY - 12, 0xFF8FA593);
		}
		double scale = isRare ? 1.0 : 64.0;
		String unit = isRare ? "dia/item" : "dia/stack of 64";
		PriceChartWidget.draw(context, textRenderer, 10, chartsY, chartW, chartHeight, "Average price (" + unit + ")", avgHistory, h -> h.avgPrice, scale);
		PriceChartWidget.draw(context, textRenderer, 14 + chartW, chartsY, chartW, chartHeight, "Lowest price (" + unit + ")", lowestHistory, h -> h.lowestPrice, scale);
	}

	private int drawRareInfo(DrawContext context, int y) {
		String[] labels = {
				"Category: " + safe(rareData.category),
				"Effect: " + safe(rareData.effect),
				"Release: " + safe(rareData.releaseDate),
				"Obtained from: " + safe(rareData.obtainedFrom),
				"Glow/Particles: " + safe(rareData.glowParticles),
				"Type/Slot: " + safe(rareData.typeSlot),
				"Dyeable: " + safe(rareData.dyeable),
		};
		for (String line : labels) {
			context.drawTextWithShadow(textRenderer, trimToWidth(line, width - 20), 10, y, 0xFF8FA593);
			y += 10;
		}
		return y + 4;
	}

	private String trimToWidth(String s, int maxWidth) {
		if (textRenderer.getWidth(s) <= maxWidth) return s;
		while (s.length() > 3 && textRenderer.getWidth(s + "...") > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	private static String safe(String s) {
		return s == null || s.isEmpty() ? "-" : s;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
