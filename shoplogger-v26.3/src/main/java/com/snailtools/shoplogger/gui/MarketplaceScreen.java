package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.MarketplaceListing;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.Blaze3D;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * X menu -> Marketplace. Read-only in-game browser for the website's
 * marketplace (GET /marketplace/listings needs no login) — placing a bid or
 * posting your own listing still needs a real logged-in account, which this
 * mod doesn't have, so every row just opens the browser straight to that
 * specific listing (marketplace/#listing=&lt;id&gt;) instead of trying to
 * replicate the whole bidding flow in-game.
 */
public class MarketplaceScreen extends Screen {

	private static final Map<String, String> CURRENCY_LABELS = Map.of(
			"diamond", "Dia", "diamondblock", "DB", "diamondstack", "STX"
	);

	private final Screen parent;
	private EditBox searchBox;
	private ItemListWidget list;

	private List<MarketplaceListing> listings = List.of();
	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private boolean listingsLoaded = false;
	private boolean catalogLoaded = false;
	private boolean loadFailed = false;
	private long searchChangedAtMillis = -1;
	private static final long SEARCH_DEBOUNCE_MS = 300;

	public MarketplaceScreen(Screen parent) {
		super(Component.literal("Marketplace"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int top = 30;
		searchBox = new EditBox(font, 10, top, width - 20, 20, Component.literal("Search by item name..."));
		searchBox.setResponder(s -> searchChangedAtMillis = System.currentTimeMillis());
		addRenderableWidget(searchBox);
		top += 24;

		list = new ItemListWidget(minecraft, width, height - top - 30, top, 22);
		addRenderableWidget(list);

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(10, height - 26, 60, 20).build());

		loadData();
	}

	@Override
	public void tick() {
		super.tick();
		if (searchChangedAtMillis > 0 && System.currentTimeMillis() - searchChangedAtMillis >= SEARCH_DEBOUNCE_MS) {
			searchChangedAtMillis = -1;
			refreshList();
		}
	}

	private void loadData() {
		WebDataClient.fetchMarketplaceListings().thenAccept(rows -> minecraft.execute(() -> {
			listings = rows;
			listingsLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> { listingsLoaded = true; loadFailed = true; });
			return null;
		});

		WebDataClient.fetchVanillaCatalog().thenAccept(items -> minecraft.execute(() -> {
			vanillaItems = items;
			catalogLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> catalogLoaded = true);
			return null;
		});

		// Rare-item marketplace posts still render fine without their texture
		// loaded (the icon just falls back to the placeholder), so this
		// doesn't gate catalogLoaded/refreshList the way the vanilla fetch does.
		WebDataClient.fetchRareCatalog().thenAccept(items -> minecraft.execute(() -> {
			rareItems = items;
			refreshList();
		})).exceptionally(ex -> null);
	}

	private boolean isLoading() {
		return !listingsLoaded || !catalogLoaded;
	}

	private String baseItemFor(String name) {
		for (VanillaItem it : vanillaItems) {
			if (it.name.equalsIgnoreCase(name)) return it.baseItem;
		}
		return null;
	}

	private String rareTextureFor(String name) {
		for (RareItem it : rareItems) {
			if (it.name.equalsIgnoreCase(name)) return it.texture;
		}
		return null;
	}

	private void refreshList() {
		if (list == null) return;
		list.clearAllEntries();
		String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);

		for (MarketplaceListing l : listings) {
			if (!q.isEmpty() && !l.itemName.toLowerCase(Locale.ROOT).contains(q)) continue;
			String subtitle = subtitleFor(l);
			String baseItem = baseItemFor(l.itemName);
			if (baseItem != null) {
				list.addItemEntry(ItemListWidget.forVanilla(l.itemName, baseItem, subtitle, () -> openOnWebsite(l)));
			} else {
				String texture = rareTextureFor(l.itemName);
				list.addItemEntry(texture != null
						? ItemListWidget.forRare(l.itemName, subtitle, texture, () -> openOnWebsite(l))
						: ItemListWidget.forVanilla(l.itemName, null, subtitle, () -> openOnWebsite(l)));
			}
		}
	}

	private String subtitleFor(MarketplaceListing l) {
		String typeLabel = "selling".equals(l.type) ? "Selling" : "Looking for";
		MarketplaceListing.PriceInfo price = l.priceInfo();
		String priceText = price == null
				? "no price set"
				: formatPrice(price.amount) + " " + CURRENCY_LABELS.getOrDefault(
						price.currency == null ? "" : price.currency.toLowerCase(Locale.ROOT), price.currency);
		return typeLabel + " · " + priceText + " · " + l.world;
	}

	private static String formatPrice(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	private void openOnWebsite(MarketplaceListing l) {
		String url = "https://sctp.nl/marketplace/#listing=" + URLEncoder.encode(l.id, StandardCharsets.UTF_8);
		Blaze3D.openUri(URI.create(url));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
		if (isLoading()) {
			context.centeredText(font, "Loading...", width / 2, height / 2, 0xFF8FA593);
		} else if (loadFailed) {
			context.centeredText(font, "Failed to load — check your connection and reopen this screen.", width / 2, height / 2, 0xFFE2A33D);
		} else if (listings.isEmpty()) {
			context.centeredText(font, "No active marketplace listings right now.", width / 2, height / 2, 0xFF8FA593);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
