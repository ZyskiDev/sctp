package com.snailtools.shoplogger;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Simple shared store so the manual (right-click) and automatic (proximity) scan paths write to one place. */
public final class ShopLog {

	private static final Map<String, ShopEntry> ENTRIES = new LinkedHashMap<>();

	// (world|position) pairs scanned since the last successful upload — lets
	// the Worker know which positions are authoritative right now, so it can
	// prune anything previously logged there that's no longer present (sold
	// out, chest emptied, or the chest/shop no longer exists at all). See
	// ShopUploader.
	private static final Set<String> SCANNED_POSITIONS = new LinkedHashSet<>();

	private ShopLog() {}

	public static synchronized void put(ShopEntry entry) {
		ENTRIES.put(entry.key(), entry);
	}

	public static synchronized Collection<ShopEntry> getAll() {
		return List.copyOf(ENTRIES.values());
	}

	public static synchronized int size() {
		return ENTRIES.size();
	}

	public static synchronized void clear() {
		ENTRIES.clear();
	}

	/**
	 * Records a fresh scan of one exact container: freshEntries is treated as
	 * authoritative for that (world, position) — anything previously stored
	 * here that ISN'T in freshEntries is removed first, before freshEntries are
	 * added/updated.
	 *
	 * This matters because ShopLog otherwise never removes anything on its own
	 * — without this, a chest that sold out or went empty would keep
	 * re-uploading its stale old contents on every future upload forever,
	 * since the Worker's position-based pruning (see worker.js) only prunes
	 * what ISN'T re-reported for a scanned position, and the mod would keep
	 * re-reporting the stale data as if it were still current.
	 */
	public static synchronized void replaceForPosition(String world, BlockPos pos, Iterable<ShopEntry> freshEntries) {
		if (world == null || pos == null) return;

		Set<String> freshKeys = new HashSet<>();
		for (ShopEntry e : freshEntries) freshKeys.add(e.key());

		ENTRIES.entrySet().removeIf(en -> {
			ShopEntry existing = en.getValue();
			return world.equals(existing.world())
					&& pos.equals(existing.containerPos())
					&& !freshKeys.contains(existing.key());
		});

		for (ShopEntry e : freshEntries) put(e);

		SCANNED_POSITIONS.add(world + "|" + pos.toShortString());
	}

	public static synchronized List<String> getScannedPositions() {
		return List.copyOf(SCANNED_POSITIONS);
	}

	public static synchronized void clearScannedPositions() {
		SCANNED_POSITIONS.clear();
	}
}
