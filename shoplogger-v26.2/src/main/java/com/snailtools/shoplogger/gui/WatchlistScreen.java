package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.WatchedItem;
import com.snailtools.shoplogger.WatchlistStore;
import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * X menu -> Watchlist. Type to search either catalog (vanilla and rare items
 * both — same two catalogs as the website's Item Library) and click a result
 * to add it; clear the search box to see (and click to remove) your current
 * watchlist instead — one list, two modes, rather than squeezing a second
 * scrollable widget into the same screen. See WatchlistAlert for what
 * actually happens once an item is being watched.
 */
public class WatchlistScreen extends Screen {

	private final Screen parent;
	private EditBox searchBox;
	private ItemListWidget list;

	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private boolean vanillaLoaded = false;
	private boolean rareLoaded = false;
	private boolean loadFailed = false;
	private long searchChangedAtMillis = -1;
	private static final long SEARCH_DEBOUNCE_MS = 300;
	// Matches WatchedItemOptionsScreen's own unit — maxPrice is stored in
	// diamonds, but shown in diamond blocks everywhere in the UI.
	private static final double DIAMONDS_PER_BLOCK = 9.0;

	public WatchlistScreen(Screen parent) {
		super(Component.literal("Watchlist"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int top = 30;
		searchBox = new EditBox(font, 10, top, width - 20, 20,
				Component.literal("Search to add, clear to view watchlist..."));
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
		WebDataClient.fetchVanillaCatalog().thenAccept(items -> minecraft.execute(() -> {
			vanillaItems = items;
			vanillaLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> { vanillaLoaded = true; loadFailed = true; });
			return null;
		});

		WebDataClient.fetchRareCatalog().thenAccept(items -> minecraft.execute(() -> {
			rareItems = items;
			rareLoaded = true;
			refreshList();
		})).exceptionally(ex -> {
			minecraft.execute(() -> { rareLoaded = true; loadFailed = true; });
			return null;
		});
	}

	private boolean isLoading() {
		return !vanillaLoaded || !rareLoaded;
	}

	private VanillaItem findVanillaItem(String name) {
		for (VanillaItem it : vanillaItems) {
			if (it.name.equalsIgnoreCase(name)) return it;
		}
		return null;
	}

	private RareItem findRareItem(String name) {
		for (RareItem it : rareItems) {
			if (it.name.equalsIgnoreCase(name)) return it;
		}
		return null;
	}

	private void refreshList() {
		list.clearAllEntries();
		String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);

		if (q.isEmpty()) {
			for (WatchedItem watched : WatchlistStore.getAll()) {
				String name = watched.itemName;
				String subtitle = watchedSubtitle(watched);
				VanillaItem vMatch = findVanillaItem(name);
				if (vMatch != null) {
					list.addItemEntry(ItemListWidget.forVanilla(name, vMatch.baseItem, subtitle, () -> openOptions(watched)));
					continue;
				}
				RareItem rMatch = findRareItem(name);
				if (rMatch != null) {
					list.addItemEntry(ItemListWidget.forRare(name, subtitle, rMatch.texture, () -> openOptions(watched)));
					continue;
				}
				list.addItemEntry(ItemListWidget.forVanilla(name, null, subtitle, () -> openOptions(watched)));
			}
			return;
		}

		// Searching also surfaces (and lets you re-open the options for)
		// anything you're already watching that matches — clicking one of
		// those opens its options instead of re-adding it, so this one search
		// box doubles as "find something new to watch" AND "find something you
		// already watch", instead of needing a separate filter for the latter.
		for (VanillaItem it : vanillaItems) {
			if (!it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			WatchedItem existing = WatchlistStore.find(it.name);
			list.addItemEntry(existing != null
					? ItemListWidget.forVanilla(labelFor(it.name), it.baseItem, watchedSubtitle(existing), () -> openOptions(existing))
					: ItemListWidget.forVanilla(labelFor(it.name), it.baseItem, () -> addWatched(it.name)));
		}
		for (RareItem it : rareItems) {
			if (!it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			WatchedItem existing = WatchlistStore.find(it.name);
			list.addItemEntry(existing != null
					? ItemListWidget.forRare(labelFor(it.name), watchedSubtitle(existing), it.texture, () -> openOptions(existing))
					: ItemListWidget.forRare(labelFor(it.name), it.category, it.texture, () -> addWatched(it.name)));
		}
	}

	private String labelFor(String name) {
		return WatchlistStore.isWatching(name) ? name + " (watching)" : name;
	}

	/** "Max 2 DB · Skips display/no-price" — shown under an item's name in the watchlist. */
	private String watchedSubtitle(WatchedItem watched) {
		StringBuilder sb = new StringBuilder();
		if (watched.maxPrice != null) {
			double blocks = watched.maxPrice / DIAMONDS_PER_BLOCK;
			sb.append("Max ").append(blocks == Math.floor(blocks) ? String.valueOf((long) blocks) : String.valueOf(blocks)).append(" DB");
		}
		if (watched.excludeNoPriceOrDisplay) {
			if (sb.length() > 0) sb.append(" · ");
			sb.append("Skips display/no-price");
		}
		return sb.length() > 0 ? sb.toString() : "No limits set";
	}

	private void addWatched(String name) {
		WatchlistStore.add(name);
		ChatFormat.send(minecraft, ChatFormat.SUCCESS, "Added " + name + " to your watchlist.");
		// Immediately offer the max-price/display options for the item that
		// was just added, rather than making a second trip back into the
		// (now-empty-search) watchlist view to configure it.
		WatchedItem justAdded = WatchlistStore.find(name);
		if (justAdded != null) {
			openOptions(justAdded);
		} else {
			refreshList();
		}
	}

	private void openOptions(WatchedItem watched) {
		minecraft.setScreenAndShow(new WatchedItemOptionsScreen(this, watched));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
		if (isLoading()) {
			context.centeredText(font, "Loading...", width / 2, height / 2, 0xFF8FA593);
		} else if (loadFailed) {
			context.centeredText(font, "Failed to load — check your connection and reopen this screen.", width / 2, height / 2, 0xFFE2A33D);
		} else if (searchBox.getValue().isBlank() && WatchlistStore.getAll().isEmpty()) {
			context.centeredText(font, "Not watching anything yet — search above to add an item.", width / 2, height / 2, 0xFF8FA593);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
