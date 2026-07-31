package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WorldSelection;
import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ListingListWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game equivalent of the website homepage's listings browser
 * (index.html) — every current listing across the whole marketplace, with
 * the same search/world/type/sort filters, rather than scoped to one item
 * like ItemDetailScreen's table. Also what /search opens now (with the
 * query pre-filled), replacing that command's old chat-output behavior.
 */
public class ListingsScreen extends Screen {

	private static final String ALL_WORLDS = "All";
	private static final String ANY_TYPE = "Any type";
	private static final String[] SORTS = { "Most recent", "Price low-high", "Price high-low", "Stock high-low", "Item A-Z" };
	// The real dataset can be ~18,000 rows — re-filtering/re-sorting that on
	// every single keystroke was the "it's slow" complaint. Wait for a short
	// pause in typing instead of reacting to every character.
	private static final long SEARCH_DEBOUNCE_MS = 300;
	// Building a real interactive widget per row is much heavier than a
	// chat-printed text line (which is why /search felt so much faster) —
	// capping how many we ever construct is what actually fixes that, not
	// trying to make the widgets themselves cheaper.
	private static final int MAX_RESULTS = 200;

	private final Screen parent;
	private final String initialQuery;

	private TextFieldWidget searchBox;
	private CyclingButtonWidget<String> worldFilter;
	private CyclingButtonWidget<String> typeFilter;
	private CyclingButtonWidget<String> sortMode;
	private ListingListWidget list;

	private List<Listing> allListings = List.of();
	private boolean loading = true;
	private boolean loadFailed = false;
	private long searchChangedAtMillis = -1;
	private int totalMatches = 0;
	private boolean truncated = false;

	public ListingsScreen(Screen parent, String initialQuery) {
		super(Text.literal("Trading Post Listings"));
		this.parent = parent;
		this.initialQuery = initialQuery;
	}

	@Override
	protected void init() {
		int top = 30;
		searchBox = new TextFieldWidget(textRenderer, 10, top, width - 20, 20, Text.literal("Search item or seller..."));
		if (initialQuery != null && !initialQuery.isBlank()) {
			searchBox.setText(initialQuery);
		}
		searchBox.setChangedListener(s -> searchChangedAtMillis = System.currentTimeMillis());
		addDrawableChild(searchBox);
		top += 24;

		String defaultWorld = ALL_WORLDS;
		ShopWorld detected = WorldSelection.get();
		if (detected != null) defaultWorld = detected.label();

		int colW = (width - 20 - 16) / 3;
		worldFilter = CyclingButtonWidget.builder((String v) -> Text.literal("World: " + v), defaultWorld)
				.values(ALL_WORLDS, "Firefly", "Honeybee")
				.omitKeyText()
				.build(10, top, colW, 20, Text.empty(), (btn, value) -> refreshList());
		addDrawableChild(worldFilter);

		typeFilter = CyclingButtonWidget.builder((String v) -> Text.literal(v), ANY_TYPE)
				.values(ANY_TYPE, "Bulk", "Bundled", "Single")
				.omitKeyText()
				.build(10 + colW + 8, top, colW, 20, Text.empty(), (btn, value) -> refreshList());
		addDrawableChild(typeFilter);

		sortMode = CyclingButtonWidget.builder((String v) -> Text.literal(v), SORTS[0])
				.values(SORTS)
				.omitKeyText()
				.build(10 + 2 * (colW + 8), top, colW, 20, Text.empty(), (btn, value) -> refreshList());
		addDrawableChild(sortMode);
		top += 24;

		list = new ListingListWidget(client, width, height - top - 30, top, 22);
		addDrawableChild(list);

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> close())
				.dimensions(10, height - 26, 60, 20).build());

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
		WebDataClient.fetchListings().thenAccept(all -> client.execute(() -> {
			allListings = all;
			loading = false;
			refreshList();
		})).exceptionally(ex -> {
			client.execute(() -> {
				loading = false;
				loadFailed = true;
			});
			return null;
		});
	}

	private boolean isUnfiltered() {
		return searchBox.getText().trim().isEmpty()
				&& ALL_WORLDS.equals(worldFilter.getValue())
				&& ANY_TYPE.equals(typeFilter.getValue());
	}

	private void refreshList() {
		list.clearAllEntries();
		totalMatches = 0;
		truncated = false;

		// Never bulk-render the entire ~18,000-row dataset — that's exactly
		// the "too many heavy widgets at once" cost that made this feel slow.
		// Require an actual search or filter first, same as how /search only
		// ever showed you a handful of matches, never everything.
		if (isUnfiltered()) return;

		String q = searchBox.getText().trim().toLowerCase(Locale.ROOT);
		String world = worldFilter.getValue();
		String type = typeFilter.getValue();

		List<Listing> filtered = new ArrayList<>();
		for (Listing l : allListings) {
			if (!q.isEmpty() && !(l.itemName.toLowerCase(Locale.ROOT).contains(q) || l.seller.toLowerCase(Locale.ROOT).contains(q))) continue;
			if (!ALL_WORLDS.equals(world) && !world.equalsIgnoreCase(l.world)) continue;
			if ("Bulk".equals(type) && !l.bulk) continue;
			if ("Bundled".equals(type) && !l.bundled) continue;
			if ("Single".equals(type) && (l.bulk || l.bundled)) continue;
			filtered.add(l);
		}

		String sort = sortMode.getValue();
		filtered.sort((a, b) -> switch (sort) {
			case "Price low-high" -> Double.compare(a.pricePerItemInDiamonds(), b.pricePerItemInDiamonds());
			case "Price high-low" -> Double.compare(b.pricePerItemInDiamonds(), a.pricePerItemInDiamonds());
			case "Stock high-low" -> Integer.compare(b.amount, a.amount);
			case "Item A-Z" -> a.itemName.compareToIgnoreCase(b.itemName);
			default -> parseInstant(b.lastSeen).compareTo(parseInstant(a.lastSeen));
		});

		totalMatches = filtered.size();
		truncated = totalMatches > MAX_RESULTS;
		int limit = Math.min(totalMatches, MAX_RESULTS);
		for (int i = 0; i < limit; i++) {
			Listing l = filtered.get(i);
			list.addListingEntry(ListingListWidget.withItemName(l, seller -> client.setScreen(new SellerProfileScreen(this, seller, l.world))));
		}
	}

	private static Instant parseInstant(String s) {
		try {
			return Instant.parse(s);
		} catch (Exception e) {
			return Instant.EPOCH;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		String status = "Trading Post Listings";
		if (loading) {
			status = "Loading listings...";
		} else if (loadFailed) {
			status = "Failed to load — check your connection and reopen this screen.";
		} else if (isUnfiltered()) {
			status = "Search or filter to browse " + fmtNum(allListings.size()) + " listings";
		} else if (truncated) {
			status = "Showing first " + MAX_RESULTS + " of " + fmtNum(totalMatches) + " matches — refine your search to narrow it down";
		} else {
			status = fmtNum(totalMatches) + " match" + (totalMatches == 1 ? "" : "es");
		}
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 10, 0xFFFFFFFF);
	}

	private static String fmtNum(int n) {
		return String.format(Locale.US, "%,d", n);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
