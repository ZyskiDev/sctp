package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.RareItem;
import com.snailtools.shoplogger.gui.data.VanillaItem;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ItemListWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * In-game equivalent of items/index.html — browse either catalog, search,
 * and (for rare items) filter by category/dyeable. The other website
 * dropdown filters (obtained from, release date, glow/particles, type/slot)
 * are folded into the search box instead of separate widgets here — a
 * Minecraft screen has nowhere near a web page's room for six dropdowns,
 * and most of those fields have far too many distinct values (obtainedFrom
 * alone has 119) to work as a "click to cycle" button anyway.
 */
public class ItemLibraryScreen extends Screen {

	public enum Catalog { VANILLA, RARE }

	private static final String ANY = "Any";

	private final Screen parent;
	private final Catalog catalog;

	private TextFieldWidget searchBox;
	private ItemListWidget list;
	private CyclingButtonWidget<String> categoryFilter;
	private CyclingButtonWidget<String> dyeableFilter;

	private List<VanillaItem> vanillaItems = List.of();
	private List<RareItem> rareItems = List.of();
	private String selectedCategory = ANY;
	private String selectedDyeable = ANY;
	private boolean loading = true;
	private boolean loadFailed = false;
	private long searchChangedAtMillis = -1;
	private static final long SEARCH_DEBOUNCE_MS = 300;

	public ItemLibraryScreen(Screen parent, Catalog catalog) {
		super(Text.literal(catalog == Catalog.VANILLA ? "Vanilla Items" : "Rare Items"));
		this.parent = parent;
		this.catalog = catalog;
	}

	@Override
	protected void init() {
		int top = 30;
		searchBox = new TextFieldWidget(textRenderer, 10, top, width - 20, 20,
				Text.literal(catalog == Catalog.RARE ? "Search items or effects..." : "Search items..."));
		searchBox.setChangedListener(s -> searchChangedAtMillis = System.currentTimeMillis());
		addDrawableChild(searchBox);
		top += 24;

		if (catalog == Catalog.RARE) {
			categoryFilter = CyclingButtonWidget.builder((String v) -> Text.literal("Category: " + v), ANY)
					.values(ANY)
				.omitKeyText()
					.build(10, top, 170, 20, Text.empty(), (btn, value) -> {
						selectedCategory = value;
						refreshList();
					});
			addDrawableChild(categoryFilter);

			dyeableFilter = CyclingButtonWidget.builder((String v) -> Text.literal("Dyeable: " + v), ANY)
					.values(ANY)
				.omitKeyText()
					.build(190, top, 170, 20, Text.empty(), (btn, value) -> {
						selectedDyeable = value;
						refreshList();
					});
			addDrawableChild(dyeableFilter);
			top += 24;
		}

		list = new ItemListWidget(client, width, height - top - 30, top, 22);
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
		if (catalog == Catalog.VANILLA) {
			WebDataClient.fetchVanillaCatalog().thenAccept(items -> client.execute(() -> {
				vanillaItems = items;
				loading = false;
				refreshList();
			})).exceptionally(ex -> {
				client.execute(() -> { loading = false; loadFailed = true; });
				return null;
			});
		} else {
			WebDataClient.fetchRareCatalog().thenAccept(items -> client.execute(() -> {
				rareItems = items;
				loading = false;
				populateRareFilterValues();
				refreshList();
			})).exceptionally(ex -> {
				client.execute(() -> { loading = false; loadFailed = true; });
				return null;
			});
		}
	}

	// CyclingButtonWidget's value list is fixed at build time, but the real
	// category/dyeable values aren't known until the catalog finishes
	// loading — so the filter button built in init() is a placeholder,
	// swapped out for a fully-populated one here once the data arrives.
	private void populateRareFilterValues() {
		TreeSet<String> categories = new TreeSet<>();
		TreeSet<String> dyeables = new TreeSet<>();
		for (RareItem it : rareItems) {
			if (it.category != null) categories.add(it.category);
			if (it.dyeable != null) dyeables.add(it.dyeable);
		}
		List<String> categoryValues = new ArrayList<>();
		categoryValues.add(ANY);
		categoryValues.addAll(categories);

		List<String> dyeableValues = new ArrayList<>();
		dyeableValues.add(ANY);
		dyeableValues.addAll(dyeables);

		remove(categoryFilter);
		categoryFilter = CyclingButtonWidget.builder((String v) -> Text.literal("Category: " + v), ANY)
				.values(categoryValues)
				.omitKeyText()
				.build(10, 54, 170, 20, Text.empty(), (btn, value) -> {
					selectedCategory = value;
					refreshList();
				});
		addDrawableChild(categoryFilter);

		remove(dyeableFilter);
		dyeableFilter = CyclingButtonWidget.builder((String v) -> Text.literal("Dyeable: " + v), ANY)
				.values(dyeableValues)
				.omitKeyText()
				.build(190, 54, 170, 20, Text.empty(), (btn, value) -> {
					selectedDyeable = value;
					refreshList();
				});
		addDrawableChild(dyeableFilter);
	}

	private void refreshList() {
		list.clearAllEntries();
		String q = searchBox.getText().trim().toLowerCase(Locale.ROOT);

		if (catalog == Catalog.VANILLA) {
			for (VanillaItem it : vanillaItems) {
				if (!q.isEmpty() && !it.name.toLowerCase(Locale.ROOT).contains(q)) continue;
				list.addItemEntry(ItemListWidget.forVanilla(it.name, it.baseItem, () ->
						client.setScreen(new ItemDetailScreen(this, it.name, it.baseItem, it.texture, false, null))));
			}
		} else {
			for (RareItem it : rareItems) {
				if (!selectedCategory.equals(ANY) && !selectedCategory.equals(it.category)) continue;
				if (!selectedDyeable.equals(ANY) && !selectedDyeable.equals(it.dyeable)) continue;
				if (!q.isEmpty() && !matchesSearch(it, q)) continue;
				RareItem captured = it;
				list.addItemEntry(ItemListWidget.forRare(it.name, it.category, it.texture, () ->
						client.setScreen(new ItemDetailScreen(this, captured.name, null, captured.texture, true, captured))));
			}
		}
	}

	private static boolean matchesSearch(RareItem it, String q) {
		if (it.name != null && it.name.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.effect != null && it.effect.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.obtainedFrom != null && it.obtainedFrom.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.typeSlot != null && it.typeSlot.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.glowParticles != null && it.glowParticles.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (it.releaseDate != null && it.releaseDate.toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
		if (loading) {
			context.drawCenteredTextWithShadow(textRenderer, "Loading...", width / 2, height / 2, 0xFF8FA593);
		} else if (loadFailed) {
			context.drawCenteredTextWithShadow(textRenderer, "Failed to load — check your connection and reopen this screen.", width / 2, height / 2, 0xFFE2A33D);
		}
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
