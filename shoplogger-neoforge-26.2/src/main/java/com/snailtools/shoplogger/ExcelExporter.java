package com.snailtools.shoplogger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a minimal, dependency-free .xlsx (an xlsx is just a zip of a few
 * small XML files). No Apache POI, no transitive dependencies to embed —
 * this only uses the JDK's built-in zip support, so it can't hit the
 * NoClassDefFoundError crash POI caused when its dependencies weren't fully
 * bundled into the mod jar.
 */
public class ExcelExporter {

	private static final String[] HEADERS = {
			"Item Name", "Base Item", "Bulk", "Price",
			"Stack Size", "Amount In Stock", "Stacks In Stock", "Currency",
			"Seller", "World", "Position", "Last Seen"
	};

	public static Path export(Collection<ShopEntry> entries, Path outFile) throws IOException {
		Files.createDirectories(outFile.getParent());

		try (OutputStream fos = Files.newOutputStream(outFile);
			 ZipOutputStream zip = new ZipOutputStream(fos)) {

			writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES);
			writeEntry(zip, "_rels/.rels", RELS);
			writeEntry(zip, "xl/workbook.xml", WORKBOOK);
			writeEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
			writeEntry(zip, "xl/worksheets/sheet1.xml", buildSheet(entries));
		}
		return outFile;
	}

	private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static String buildSheet(Collection<ShopEntry> entries) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
		sb.append("<sheetData>");

		sb.append(row(1, headerCells()));

		int rowNum = 2;
		for (ShopEntry e : entries) {
			sb.append(row(rowNum++, new String[]{
					inlineStr(e.itemName()),
					inlineStr(e.baseItem()),
					inlineStr(e.bulk() ? "Yes" : "No"),
					inlineStr(e.priceLabel()),
					number(e.stackSize()),
					number(e.amountAvailable()),
					number(e.stacksInStock()),
					inlineStr(e.currency()),
					inlineStr(e.seller()),
					inlineStr(e.world()),
					inlineStr(e.containerPos().toShortString()),
					inlineStr(Instant.ofEpochMilli(e.lastSeenEpochMillis()).toString())
			}));
		}

		sb.append("</sheetData></worksheet>");
		return sb.toString();
	}

	private static String[] headerCells() {
		String[] cells = new String[HEADERS.length];
		for (int i = 0; i < HEADERS.length; i++) cells[i] = inlineStr(HEADERS[i]);
		return cells;
	}

	private static String row(int rowNum, String[] cellXmlFragments) {
		StringBuilder sb = new StringBuilder("<row r=\"").append(rowNum).append("\">");
		for (int col = 0; col < cellXmlFragments.length; col++) {
			String ref = colLetter(col) + rowNum;
			sb.append(cellXmlFragments[col].replace("{REF}", ref));
		}
		sb.append("</row>");
		return sb.toString();
	}

	private static String inlineStr(String value) {
		return "<c r=\"{REF}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escape(value) + "</t></is></c>";
	}

	private static String number(double value) {
		return "<c r=\"{REF}\"><v>" + value + "</v></c>";
	}

	private static String escape(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;");
	}

	private static String colLetter(int index) {
		StringBuilder sb = new StringBuilder();
		int i = index;
		do {
			sb.insert(0, (char) ('A' + (i % 26)));
			i = i / 26 - 1;
		} while (i >= 0);
		return sb.toString();
	}

	private static final String CONTENT_TYPES =
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
			"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
			"<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
			"<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
			"<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
			"<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
			"</Types>";

	private static final String RELS =
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
			"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
			"<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
			"</Relationships>";

	private static final String WORKBOOK =
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
			"<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
			"xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
			"<sheets><sheet name=\"Shops\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
			"</workbook>";

	private static final String WORKBOOK_RELS =
			"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
			"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
			"<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
			"</Relationships>";
}
