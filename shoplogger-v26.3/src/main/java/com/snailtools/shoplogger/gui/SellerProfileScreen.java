package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.SharedShop;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ListingListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** In-game equivalent of a /s/&lt;world&gt;/&lt;username&gt; seller page: avatar, bio/shops if set, and their current listings. */
public class SellerProfileScreen extends Screen {

	private static final String ALL_WORLDS = "All";

	private final Screen parent;
	private final String username;
	private String worldFilter;

	private List<Listing> allListings = List.of();
	private SharedShop profile;
	private ListingListWidget listingList;

	public SellerProfileScreen(Screen parent, String username, String defaultWorld) {
		super(Component.literal(username));
		this.parent = parent;
		this.username = username;
		this.worldFilter = defaultWorld != null ? defaultWorld : ALL_WORLDS;
	}

	@Override
	protected void init() {
		int top = 34;

		addRenderableWidget(CycleButton.builder((String v) -> Component.literal("World: " + v), worldFilter)
				.withValues(ALL_WORLDS, "Firefly", "Honeybee")
				.displayOnlyValue()
				.create(width - 170, 6, 160, 20, Component.empty(), (btn, value) -> {
					worldFilter = value;
					refreshListingList();
				}));

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(10, height - 26, 60, 20).build());

		listingList = new ListingListWidget(minecraft, width, height - top - 34, top, 22);
		addRenderableWidget(listingList);

		loadData();
	}

	private void loadData() {
		WebDataClient.fetchListings().thenAccept(all -> minecraft.execute(() -> {
			List<Listing> mine = new ArrayList<>();
			for (Listing l : all) {
				if (username.equalsIgnoreCase(l.seller)) mine.add(l);
			}
			allListings = mine;
			refreshListingList();
		})).exceptionally(ex -> null);

		WebDataClient.fetchSharedShops().thenAccept(shops -> minecraft.execute(() -> {
			for (SharedShop s : shops) {
				if (username.equalsIgnoreCase(s.username)) {
					profile = s;
					break;
				}
			}
		})).exceptionally(ex -> null);
	}

	private void refreshListingList() {
		listingList.clearAllEntries();
		for (Listing l : allListings) {
			if (!ALL_WORLDS.equals(worldFilter) && !worldFilter.equalsIgnoreCase(l.world)) continue;
			listingList.addListingEntry(ListingListWidget.of(l, seller -> {}));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

		String avatarUrl = "https://mc-heads.net/avatar/" + username + "/32";
		Identifier avatar = RemoteTextureCache.get(Minecraft.getInstance(), avatarUrl, () -> {});
		if (avatar != null) {
			context.blit(RenderPipelines.GUI_TEXTURED, avatar, 10, 6, 0, 0, 32, 32, 32, 32);
		}
		context.text(font, username, 48, 6, 0xFFFFFFFF);
		if (profile != null && profile.bio != null && !profile.bio.isEmpty()) {
			context.text(font, trimToWidth(profile.bio, width - 220), 48, 18, 0xFF8FA593);
		}

		super.extractRenderState(context, mouseX, mouseY, delta);
	}

	private String trimToWidth(String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		while (s.length() > 3 && font.width(s + "...") > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}
}
