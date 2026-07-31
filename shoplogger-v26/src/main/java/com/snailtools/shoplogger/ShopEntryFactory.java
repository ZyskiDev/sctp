package com.snailtools.shoplogger;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Tallies item stacks in a container into one ShopEntry per distinct item type. */
public final class ShopEntryFactory {

	private ShopEntryFactory() {}

	// batchSize = the largest single-slot quantity seen for this item, i.e. how
	// many the shop stocks per slot. The sign's price is per this many items,
	// not necessarily per a full stack — see ShopSign.
	private record Tally(ItemStack representative, int count, int batchSize, boolean bulk, boolean bundled) {}

	public static List<ShopEntry> build(AbstractContainerMenu handler, ShopSign sign, BlockPos containerPos) {
		if (!(handler instanceof ChestMenu containerHandler)) {
			return List.of();
		}
		if (!sign.display() && sign.signPrice() == 0) {
			return List.of(); // price-0 signs aren't real sales — don't log their contents
		}
		if (!ShopDimension.isActive(Minecraft.getInstance())) {
			return List.of(); // not the shop dimension — e.g. a lookalike sign+chest in the overworld
		}

		ShopWorld world = WorldSelection.get();
		if (world == null) {
			return List.of(); // callers should already gate on WorldSelection.ensureSet(...) before reaching here
		}

		int invSize = containerHandler.getContainer().getContainerSize();

		// key = baseId + "|" + displayName
		Map<String, Tally> tallies = new LinkedHashMap<>();

		for (int i = 0; i < invSize && i < handler.slots.size(); i++) {
			ItemStack stack = handler.getSlot(i).getItem();
			if (stack == null || stack.isEmpty()) continue;

			List<ItemStack> shulkerContents = readShulkerContents(stack);
			List<ItemStack> bundleContents = shulkerContents == null ? readBundleContents(stack) : null;

			if (shulkerContents != null && !shulkerContents.isEmpty() && isSingleItemType(shulkerContents)) {
				// Bulk item: the whole shulker is one item type — register its
				// contents instead of the shulker itself.
				for (ItemStack inner : shulkerContents) {
					if (inner == null || inner.isEmpty()) continue;
					addTally(tallies, inner, inner.getCount(), true, false);
				}
			} else if (bundleContents != null && !bundleContents.isEmpty() && isSingleItemType(bundleContents)) {
				// Same idea as a shulker, but marked "bundled" instead of "bulk" so
				// the site can tell the two apart.
				for (ItemStack inner : bundleContents) {
					if (inner == null || inner.isEmpty()) continue;
					addTally(tallies, inner, inner.getCount(), false, true);
				}
			} else {
				// Empty/mixed-contents shulker or bundle (e.g. a curated bundle like
				// a "Lunar New Year Box") — list the container itself rather than
				// decomposing it. "Bulk"/"bundled" specifically mean "entirely one
				// item type."
				addTally(tallies, stack, stack.getCount(), false, false);
			}
		}

		long now = System.currentTimeMillis();
		List<ShopEntry> out = new ArrayList<>();
		for (Tally t : tallies.values()) {
			ItemStack rep = t.representative();

			// The sign's price is exactly what's on the sign, per t.batchSize()
			// items — however many the shop actually stocks per slot. We don't
			// scale it to a full stack; batchSize is just shown alongside it so
			// buyers know what the price buys (e.g. "1 diamond" / "per 32").
			int batchSize = t.batchSize();
			double stacksInStock = Math.round((t.count() / (double) batchSize) * 100.0) / 100.0;

			out.add(new ShopEntry(
					displayNameFor(rep),
					BuiltInRegistries.ITEM.getKey(rep.getItem()).toString(),
					t.count(),
					sign.signPrice(),
					batchSize,
					stacksInStock,
					t.bulk(),
					t.bundled(),
					sign.currency(),
					sign.seller(),
					world.label(),
					containerPos,
					now
			));
		}
		return out;
	}

	private static void addTally(Map<String, Tally> tallies, ItemStack stack, int amount, boolean bulk, boolean bundled) {
		String baseId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String displayName = displayNameFor(stack);
		String key = baseId + "|" + displayName;

		Tally existing = tallies.get(key);
		if (existing == null) {
			tallies.put(key, new Tally(stack, amount, amount, bulk, bundled));
		} else {
			// Once bulk/bundled, stays that way if any contributing stack came
			// from a shulker/bundle. batchSize takes the largest single slot
			// seen — a smaller one is more likely a partially-sold leftover
			// than the shop's real batch size.
			tallies.put(key, new Tally(
					existing.representative(),
					existing.count() + amount,
					Math.max(existing.batchSize(), amount),
					existing.bulk() || bulk,
					existing.bundled() || bundled));
		}
	}

	/**
	 * An enchanted book's own display name is just "Enchanted Book" — the
	 * enchantment only shows up as a separate tooltip line, not in
	 * getHoverName(). Use the enchantment(s) it actually holds instead, e.g.
	 * "Sharpness V", so different enchanted books don't all collapse into one
	 * listing and buyers can tell what they're actually buying without
	 * opening the shop.
	 */
	private static String displayNameFor(ItemStack stack) {
		if (stack.getItem() == Items.ENCHANTED_BOOK) {
			ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
			if (enchantments != null && !enchantments.isEmpty()) {
				return enchantments.entrySet().stream()
						.map(e -> Enchantment.getFullname(e.getKey(), e.getIntValue()).getString())
						.sorted()
						.collect(Collectors.joining(", "));
			}
		}
		return stack.getHoverName().getString();
	}

	/** True if every non-empty stack inside the shulker is the same item. */
	private static boolean isSingleItemType(List<ItemStack> contents) {
		var firstItem = contents.get(0).getItem();
		for (ItemStack s : contents) {
			if (s.getItem() != firstItem) return false;
		}
		return true;
	}

	/** Returns the item stacks inside a shulker box, or null if this isn't a (non-empty) shulker box. */
	private static List<ItemStack> readShulkerContents(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
		if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return null;

		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container == null) return null;

		return container.nonEmptyItemCopyStream().toList();
	}

	/** Returns the item stacks inside a bundle, or null if this isn't a (non-empty) bundle. */
	private static List<ItemStack> readBundleContents(ItemStack stack) {
		if (!(stack.getItem() instanceof BundleItem)) return null;

		BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (contents == null || contents.isEmpty()) return null;

		return contents.itemCopyStream().toList();
	}
}
