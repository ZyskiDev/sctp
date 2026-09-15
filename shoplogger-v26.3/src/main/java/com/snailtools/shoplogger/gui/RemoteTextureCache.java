package com.snailtools.shoplogger.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.snailtools.shoplogger.gui.data.WebDataClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Downloads a remote PNG (rare-item icons, seller avatars) and registers it
 * as a Minecraft texture the first time it's needed, then serves it from
 * memory afterward — cleared when the GUI closes (see clear()), same
 * "no cache beyond the current session" approach the website itself uses.
 */
public final class RemoteTextureCache {

	// Item catalog texture paths (data/vanilla-items.json, data/rare-items.json)
	// are site-relative (e.g. "/items/textures/rare_image1.png"), same as an
	// <img src="..."> on the website — resolve them against the site origin
	// before handing them to URI.create, which has no notion of a "current
	// page" to resolve a relative path against and throws on one otherwise.
	private static final String SITE_BASE = "https://sctp.nl";

	private static final Map<String, Identifier> loaded = new HashMap<>();
	private static final Set<String> inFlight = new HashSet<>();
	private static int nextId = 0;

	private RemoteTextureCache() {}

	/** Returns the texture identifier if already loaded; otherwise kicks off a fetch and returns null (call again later, e.g. next frame, once onLoaded has fired). */
	public static Identifier get(Minecraft client, String url, Runnable onLoaded) {
		if (url == null || url.isEmpty()) return null;
		String resolvedUrl = url.startsWith("/") ? SITE_BASE + url : url;
		Identifier cached = loaded.get(url);
		if (cached != null) return cached;

		if (inFlight.add(url)) {
			WebDataClient.fetchBytes(resolvedUrl).thenAccept(bytes -> client.execute(() -> {
				inFlight.remove(url);
				try {
					NativeImage image = NativeImage.read(bytes);
					Identifier id = Identifier.fromNamespaceAndPath("shoplogger", "remote/" + (nextId++));
					client.getTextureManager().register(id, new DynamicTexture(() -> url, image));
					loaded.put(url, id);
					if (onLoaded != null) onLoaded.run();
				} catch (Exception ignored) {
					// broken/missing image — the caller's fallback icon stays in place
				}
			})).exceptionally(ex -> {
				inFlight.remove(url);
				return null;
			});
		}
		return null;
	}

	/** Frees every registered dynamic texture — call when the whole GUI session closes (back to HomeScreen's opener, or the game world). */
	public static void clear(Minecraft client) {
		for (Identifier id : loaded.values()) {
			client.getTextureManager().release(id);
		}
		loaded.clear();
		inFlight.clear();
	}
}
