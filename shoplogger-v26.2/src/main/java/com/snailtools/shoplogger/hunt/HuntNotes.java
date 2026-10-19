package com.snailtools.shoplogger.hunt;

import com.snailtools.shoplogger.ShopWorld;
import com.snailtools.shoplogger.WorldSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The hunt's notes on game screens: a gold line above the Butterfly Exchange and
 * your backpack and ender chest (per world). Only for a paired hunter whose
 * puzzle set has a note for that screen. (The mod's own screens draw theirs
 * with card(): the watchlist, the listings search, the item page.)
 */
public final class HuntNotes {

	private HuntNotes() {}

	private static final int MAX_W = 240;

	/** Which note a screen shows, or null. */
	public static String contextOf(Screen screen) {
		if (screen == null) return null;
		String title = screen.getTitle().getString();
		String lower = title.toLowerCase(Locale.ROOT);
		if (title.contains("Butterfly Exchange")) return "butterfly";
		ShopWorld world = WorldSelection.get();
		if (world == null) return null;
		if (lower.contains("backpack")) return "bp:" + world.label();
		if (lower.contains("ender chest")) return "ec:" + world.label();
		return null;
	}

	public static void onScreenRender(Screen screen, GuiGraphicsExtractor g) {
		if (!HuntClient.paired()) return;
		String context = contextOf(screen);
		if (context == null) return;
		String note = HuntClient.note(context);
		if (note == null || note.isBlank()) return;
		Font font = Minecraft.getInstance().font;
		List<String> lines = wrap(font, note, MAX_W);
		int w = 0;
		for (String l : lines) w = Math.max(w, font.width(l));
		int h = lines.size() * 10 + 6;
		int x, y;
		if (screen instanceof AbstractContainerScreen<?> c) {
			x = field(c, "leftPos", 8);
			y = field(c, "topPos", 30) - h - 4;
		} else {
			x = (screen.width - w) / 2 - 4;
			y = 6;
		}
		if (y < 2) y = 2;
		g.fill(x, y, x + w + 8, y + h, 0xE0141018);
		g.fill(x, y, x + 2, y + h, HuntClient.GOLD);
		for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + 5, y + 4 + i * 10, HuntClient.GOLD, true);
	}

	/** A gold note card in a mod screen, its bottom-right corner at (right, bottom). */
	public static void card(GuiGraphicsExtractor g, Font font, String note, int right, int bottom, int maxW) {
		if (note == null || note.isBlank()) return;
		List<String> lines = wrap(font, note, maxW);
		int w = 0;
		for (String l : lines) w = Math.max(w, font.width(l));
		int h = lines.size() * 10 + 8, x = right - w - 14, y = bottom - h;
		g.fill(x, y, x + w + 14, y + h, 0xF0141018);
		g.fill(x, y, x + 2, y + h, HuntClient.GOLD);
		for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + 7, y + 5 + i * 10, HuntClient.GOLD, true);
	}

	/** Word-wraps to a pixel width. */
	public static List<String> wrap(Font font, String text, int maxW) {
		List<String> out = new ArrayList<>();
		for (String para : text.split("\n", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				String next = line.length() == 0 ? word : line + " " + word;
				if (font.width(next) <= maxW || line.length() == 0) {
					line.setLength(0);
					line.append(next);
				} else {
					out.add(line.toString());
					line.setLength(0);
					line.append(word);
				}
			}
			out.add(line.toString());
		}
		return out;
	}

	private static int field(AbstractContainerScreen<?> screen, String name, int fallback) {
		try {
			java.lang.reflect.Field f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.getInt(screen);
		} catch (Exception e) {
			return fallback;
		}
	}
}
