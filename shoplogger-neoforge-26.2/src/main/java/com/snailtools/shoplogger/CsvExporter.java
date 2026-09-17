package com.snailtools.shoplogger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;

public class CsvExporter {

	public static Path export(Collection<ShopEntry> entries, Path outFile) throws IOException {
		Files.createDirectories(outFile.getParent());

		try (Writer w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
			w.write("Item Name,Base Item,Bulk,Price,Stack Size,Amount In Stock,Stacks In Stock,Currency,Seller,World,Position,Last Seen\n");
			for (ShopEntry e : entries) {
				w.write(csv(e.itemName())); w.write(',');
				w.write(csv(e.baseItem())); w.write(',');
				w.write(e.bulk() ? "Yes" : "No"); w.write(',');
				w.write(csv(e.priceLabel())); w.write(',');
				w.write(String.valueOf(e.stackSize())); w.write(',');
				w.write(String.valueOf(e.amountAvailable())); w.write(',');
				w.write(String.valueOf(e.stacksInStock())); w.write(',');
				w.write(csv(e.currency())); w.write(',');
				w.write(csv(e.seller())); w.write(',');
				w.write(csv(e.world())); w.write(',');
				w.write(csv(e.containerPos().toShortString())); w.write(',');
				w.write(csv(Instant.ofEpochMilli(e.lastSeenEpochMillis()).toString()));
				w.write('\n');
			}
		}
		return outFile;
	}

	private static String csv(String s) {
		if (s == null) return "";
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}
}
