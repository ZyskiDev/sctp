package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.gui.data.Listing;
import com.snailtools.shoplogger.gui.data.SharedShop;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import com.snailtools.shoplogger.gui.widget.ListingListWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
		super(Text.literal(username));
		this.parent = parent;
		this.username = username;
		this.worldFilter = defaultWorld != null ? defaultWorld : ALL_WORLDS;
	}

	@Override
	protected void init() {
		int top = 34;

		addDrawableChild(CyclingButtonWidget.builder((String v) -> Text.literal("World: " + v), worldFilter)
				.values(ALL_WORLDS, "Firefly", "Honeybee")
				.omitKeyText()
				.build(width - 170, 6, 160, 20, Text.empty(), (btn, value) -> {
					worldFilter = value;
					refreshListingList();
				}));

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> close())
				.dimensions(10, height - 26, 60, 20).build());

		listingList = new ListingListWidget(client, width, height - top - 34, top, 22);
		addDrawableChild(listingList);

		loadData();
	}

	private void loadData() {
		WebDataClient.fetchListings().thenAccept(all -> client.execute(() -> {
			List<Listing> mine = new ArrayList<>();
			for (Listing l : all) {
				if (username.equalsIgnoreCase(l.seller)) mine.add(l);
			}
			allListings = mine;
			refreshListingList();
		})).exceptionally(ex -> null);

		WebDataClient.fetchSharedShops().thenAccept(shops -> client.execute(() -> {
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
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {

		String avatarUrl = "https://mc-heads.net/avatar/" + username + "/32";
		Identifier avatar = RemoteTextureCache.get(MinecraftClient.getInstance(), avatarUrl, () -> {});
		if (avatar != null) {
			context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, avatar, 10, 6, 0, 0, 32, 32, 32, 32);
		}
		context.drawTextWithShadow(textRenderer, username, 48, 6, 0xFFFFFFFF);
		if (profile != null && profile.bio != null && !profile.bio.isEmpty()) {
			context.drawTextWithShadow(textRenderer, trimToWidth(profile.bio, width - 220), 48, 18, 0xFF8FA593);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	private String trimToWidth(String s, int maxWidth) {
		if (textRenderer.getWidth(s) <= maxWidth) return s;
		while (s.length() > 3 && textRenderer.getWidth(s + "...") > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
