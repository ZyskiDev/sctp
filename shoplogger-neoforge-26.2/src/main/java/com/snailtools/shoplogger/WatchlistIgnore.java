package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * "Ignore this listing": the grey [Ignore] button on watchlist alerts stops
 * that one exact listing (a shop's listing, or a marketplace post) from ever
 * triggering a watchlist alert again — the item stays on your watchlist, and
 * every other seller's listing of it still alerts as normal. Remembered in
 * the config between sessions; "/watchunignore" forgets them all.
 *
 * Chat buttons can't carry data in a click command, so [Ignore] registers the
 * listing's key here and runs "/watchignore &lt;id&gt;" (same trick as ShopReporter).
 */
public final class WatchlistIgnore {

	private static final String CONFIG_PATH = "watchlist/ignoredListings";
	private static final int MAX_IGNORED = 2000;
	private static final int MAX_PENDING = 300;

	private static Set<String> ignored;
	private static final Map<Integer, String> PENDING = new LinkedHashMap<>();
	private static int nextId = 1;

	private WatchlistIgnore() {}

	private static synchronized Set<String> set() {
		if (ignored == null) {
			ignored = new LinkedHashSet<>();
			String[] saved = Config.get(CONFIG_PATH, String[].class);
			if (saved != null) ignored.addAll(Arrays.asList(saved));
		}
		return ignored;
	}

	private static void save() {
		Config.update(CONFIG_PATH, set().toArray(new String[0]));
	}

	/** Key for a shop listing — the same identity the Worker uses to store it (see ShopReporter.rowKey). */
	public static String shopKey(Map<String, Object> listing) {
		return "shop|" + ShopReporter.rowKey(listing);
	}

	public static String marketKey(String listingId) {
		return "market|" + listingId;
	}

	public static synchronized boolean isIgnored(String key) {
		return key != null && set().contains(key);
	}

	private static synchronized void add(String key) {
		Set<String> s = set();
		s.add(key);
		while (s.size() > MAX_IGNORED) s.remove(s.iterator().next()); // oldest first
		save();
	}

	private static synchronized int clear() {
		int n = set().size();
		set().clear();
		save();
		return n;
	}

	/** The grey [Ignore] chat button for a listing key (see shopKey / marketKey). */
	public static Component buildChatButton(String key) {
		int id;
		synchronized (WatchlistIgnore.class) {
			id = nextId++;
			PENDING.put(id, key);
			if (PENDING.size() > MAX_PENDING) PENDING.remove(PENDING.keySet().iterator().next());
		}
		return Component.literal("[Ignore]").setStyle(Style.EMPTY
				.withColor(ChatFormatting.GRAY)
				.withClickEvent(new ClickEvent.RunCommand("/watchignore " + id))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Stop alerting about this exact listing (other listings of the item still alert). Undo all with /watchunignore"))));
	}

	public static void ignorePending(Minecraft client, int id) {
		String key;
		synchronized (WatchlistIgnore.class) {
			key = PENDING.get(id);
		}
		if (key == null) {
			ChatFormat.send(client, ChatFormat.ERROR, "That Ignore link has expired — wait for the next alert and use its button.");
			return;
		}
		add(key);
		ChatFormat.send(client, ChatFormat.NEUTRAL, "Ignoring that listing from now on. (Undo everything with /watchunignore.)");
	}

	public static void unignoreAll(Minecraft client) {
		int n = clear();
		ChatFormat.send(client, ChatFormat.NEUTRAL, n == 0 ? "You weren't ignoring any listings." : "Stopped ignoring " + n + " listing" + (n == 1 ? "" : "s") + ".");
	}
}
