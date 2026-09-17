package com.snailtools.shoplogger.gui.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors 404.html's mergeHistoryArrays: the backend still keys price
 * history by exact (baseItem, name), not by the fuzzy-matched rare item, so
 * a typo'd listing's history lives under its own key — this combines every
 * distinct key's history into one series per (date, world).
 */
public final class HistoryMerge {

	private HistoryMerge() {}

	public static List<HistoryPoint> merge(List<List<HistoryPoint>> arrays) {
		Map<String, HistoryPoint> byKey = new LinkedHashMap<>();
		Map<String, Double> weightedSum = new LinkedHashMap<>();

		for (List<HistoryPoint> arr : arrays) {
			if (arr == null) continue;
			for (HistoryPoint h : arr) {
				String key = h.date + "|" + h.world;
				HistoryPoint g = byKey.get(key);
				if (g == null) {
					g = new HistoryPoint();
					g.date = h.date;
					g.world = h.world;
					g.lowestPrice = h.lowestPrice;
					g.highestPrice = h.highestPrice;
					byKey.put(key, g);
					weightedSum.put(key, 0.0);
				}
				weightedSum.put(key, weightedSum.get(key) + h.avgPrice * h.listingCount);
				g.listingCount += h.listingCount;
				g.lowestPrice = Math.min(g.lowestPrice, h.lowestPrice);
				g.highestPrice = Math.max(g.highestPrice, h.highestPrice);
				g.sellerCount = Math.max(g.sellerCount, h.sellerCount);
				g.totalStock += h.totalStock;
			}
		}

		List<HistoryPoint> out = new ArrayList<>();
		for (Map.Entry<String, HistoryPoint> e : byKey.entrySet()) {
			HistoryPoint g = e.getValue();
			g.avgPrice = g.listingCount > 0 ? weightedSum.get(e.getKey()) / g.listingCount : 0;
			out.add(g);
		}
		return out;
	}
}
