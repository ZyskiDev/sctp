package com.snailtools.shoplogger;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

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
	private record Tally(ItemStack representative, int count, int batchSize, boolean bulk) {}

	public static List<ShopEntry> build(ScreenHandler handler, ShopSign sign, BlockPos containerPos) {
		if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
			return List.of();
		}
		if (!sign.display() && sign.signPrice() == 0) {
			return List.of(); // price-0 signs aren't real sales — don't log their contents
		}
		if (!ShopDimension.isActive(MinecraftClient.getInstance())) {
			return List.of(); // not the shop dimension — e.g. a lookalike sign+chest in the overworld
		}

		ShopWorld world = WorldSelection.get();
		if (world == null) {
			return List.of(); // callers should already gate on WorldSelection.ensureSet(...) before reaching here
		}

		int invSize = containerHandler.getInventory().size();

		// key = baseId + "|" + displayName
		Map<String, Tally> tallies = new LinkedHashMap<>();

		for (int i = 0; i < invSize && i < handler.slots.size(); i++) {
			ItemStack stack = handler.getSlot(i).getStack();
			if (stack == null || stack.isEmpty()) continue;

			List<ItemStack> shulkerContents = readShulkerContents(stack);
			if (shulkerContents != null && !shulkerContents.isEmpty() && isSingleItemType(shulkerContents)) {
				// Bulk item: the whole shulker is one item type — register its
				// contents instead of the shulker itself.
				for (ItemStack inner : shulkerContents) {
					if (inner == null || inner.isEmpty()) continue;
					addTally(tallies, inner, inner.getCount(), true);
				}
			} else {
				// Empty shulker, or a mixed-contents shulker (e.g. a curated bundle
				// like a "Lunar New Year Box") — list the shulker itself rather than
				// decomposing it. "Bulk" specifically means "entirely one item type."
				addTally(tallies, stack, stack.getCount(), false);
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
					Registries.ITEM.getId(rep.getItem()).toString(),
					t.count(),
					sign.signPrice(),
					batchSize,
					stacksInStock,
					t.bulk(),
					sign.currency(),
					sign.seller(),
					world.label(),
					containerPos,
					now
			));
		}
		return out;
	}

	private static void addTally(Map<String, Tally> tallies, ItemStack stack, int amount, boolean bulk) {
		String baseId = Registries.ITEM.getId(stack.getItem()).toString();
		String displayName = displayNameFor(stack);
		String key = baseId + "|" + displayName;

		Tally existing = tallies.get(key);
		if (existing == null) {
			tallies.put(key, new Tally(stack, amount, amount, bulk));
		} else {
			// Once bulk, stays bulk if any contributing stack came from a shulker.
			// batchSize takes the largest single slot seen — a smaller one is
			// more likely a partially-sold leftover than the shop's real batch size.
			tallies.put(key, new Tally(
					existing.representative(),
					existing.count() + amount,
					Math.max(existing.batchSize(), amount),
					existing.bulk() || bulk));
		}
	}

	/**
	 * An enchanted book's own display name is just "Enchanted Book" — the
	 * enchantment only shows up as a separate tooltip line, not in getName().
	 * Use the enchantment(s) it actually holds instead, e.g. "Sharpness V", so
	 * different enchanted books don't all collapse into one listing and
	 * buyers can tell what they're actually buying without opening the shop.
	 */
	private static String displayNameFor(ItemStack stack) {
		if (stack.getItem() == Items.ENCHANTED_BOOK) {
			ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
			if (enchantments != null && !enchantments.isEmpty()) {
				return enchantments.getEnchantmentEntries().stream()
						.map(e -> Enchantment.getName(e.getKey(), e.getIntValue()).getString())
						.sorted()
						.collect(Collectors.joining(", "));
			}
		}
		return stack.getName().getString();
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

		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container == null) return null;

		return container.streamNonEmpty().toList();
	}
}
