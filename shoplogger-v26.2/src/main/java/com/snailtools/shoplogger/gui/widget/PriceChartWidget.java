package com.snailtools.shoplogger.gui.widget;

import com.snailtools.shoplogger.gui.data.HistoryPoint;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

/**
 * Hand-drawn line chart (no charting library exists for Fabric GUIs) —
 * draws simple 1px line segments between points via repeated fill calls,
 * same technique used elsewhere for lightweight in-game plots. Mirrors
 * 404.html's two-world (Firefly/Honeybee) price-history charts.
 */
public final class PriceChartWidget {

	private static final int FIREFLY_COLOR = 0xFFE2A33D;
	private static final int HONEYBEE_COLOR = 0xFFB7E23D;

	private PriceChartWidget() {}

	public static void draw(GuiGraphicsExtractor context, Font font, int x, int y, int w, int h, String title,
			List<HistoryPoint> history, ToDoubleFunction<HistoryPoint> field, double scale) {
		// Reserve a strip above the box for the caller-supplied title
		// ("what this chart shows") instead of leaving the box unlabeled.
		int titleH = 10;
		int boxY = y + titleH;
		int boxH = h - titleH;
		context.text(font, title, x, y, 0xFFD9C89A);

		context.fill(x, boxY, x + w, boxY + boxH, 0x662A3A2F);
		drawRectOutline(context, x, boxY, w, boxH, 0xFF33453A);

		if (history == null || history.isEmpty()) {
			context.centeredText(font, "No price history yet", x + w / 2, boxY + boxH / 2 - 4, 0xFF8FA593);
			return;
		}

		TreeMap<String, Double> firefly = new TreeMap<>();
		TreeMap<String, Double> honeybee = new TreeMap<>();
		for (HistoryPoint hp : history) {
			double v = field.applyAsDouble(hp) * scale;
			if ("Firefly".equalsIgnoreCase(hp.world)) firefly.put(hp.date, v);
			else if ("Honeybee".equalsIgnoreCase(hp.world)) honeybee.put(hp.date, v);
		}

		TreeSet<String> dateSet = new TreeSet<>();
		dateSet.addAll(firefly.keySet());
		dateSet.addAll(honeybee.keySet());
		List<String> dates = new ArrayList<>(dateSet);
		if (dates.isEmpty()) {
			context.centeredText(font, "No price history yet", x + w / 2, boxY + boxH / 2 - 4, 0xFF8FA593);
			return;
		}

		double maxVal = 0;
		for (double v : firefly.values()) maxVal = Math.max(maxVal, v);
		for (double v : honeybee.values()) maxVal = Math.max(maxVal, v);
		if (maxVal <= 0) maxVal = 1;

		// Left margin wide enough for the Y-axis value labels added below.
		int leftPad = 28, rightPad = 4, topPad = 12, bottomPad = 4;
		int plotX = x + leftPad, plotY = boxY + topPad;
		int plotW = w - leftPad - rightPad, plotH = boxH - topPad - bottomPad;
		int labelRightX = plotX - 3;

		drawAxisRow(context, font, labelRightX, plotX, plotY, plotW, maxVal);
		drawAxisRow(context, font, labelRightX, plotX, plotY + plotH / 2, plotW, maxVal / 2);
		drawAxisRow(context, font, labelRightX, plotX, plotY + plotH, plotW, 0);

		drawSeries(context, dates, firefly, plotX, plotY, plotW, plotH, maxVal, FIREFLY_COLOR);
		drawSeries(context, dates, honeybee, plotX, plotY, plotW, plotH, maxVal, HONEYBEE_COLOR);

		context.fill(x + w - 74, boxY + 2, x + w - 68, boxY + 8, FIREFLY_COLOR);
		context.text(font, "FF", x + w - 64, boxY + 2, FIREFLY_COLOR);
		context.fill(x + w - 44, boxY + 2, x + w - 38, boxY + 8, HONEYBEE_COLOR);
		context.text(font, "HB", x + w - 34, boxY + 2, HONEYBEE_COLOR);
	}

	/** Faint gridline plus its numeric value, right-aligned just left of the plot area. */
	private static void drawAxisRow(GuiGraphicsExtractor context, Font font, int labelRightX, int plotX, int rowY, int plotW, double value) {
		context.fill(plotX, rowY, plotX + plotW, rowY + 1, 0x22FFFFFF);
		String label = fmtAxis(value);
		int labelW = font.width(label);
		context.text(font, label, labelRightX - labelW, rowY - 4, 0xFF8FA593);
	}

	private static String fmtAxis(double v) {
		double r = round2(v);
		return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
	}

	private static double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	private static void drawSeries(GuiGraphicsExtractor context, List<String> dates, Map<String, Double> series,
			int px, int py, int pw, int ph, double maxVal, int color) {
		Integer prevX = null, prevY = null;
		int n = dates.size();
		for (int i = 0; i < n; i++) {
			Double v = series.get(dates.get(i));
			if (v == null) {
				prevX = null;
				prevY = null;
				continue;
			}
			int cx = px + (n <= 1 ? pw / 2 : (int) ((double) i / (n - 1) * pw));
			int cy = py + ph - (int) (v / maxVal * ph);
			if (prevX != null) {
				drawLineSegment(context, prevX, prevY, cx, cy, color);
			}
			context.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
			prevX = cx;
			prevY = cy;
		}
	}

	private static void drawLineSegment(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
		int dx = x2 - x1, dy = y2 - y1;
		int steps = Math.max(Math.abs(dx), Math.abs(dy));
		if (steps == 0) {
			context.fill(x1, y1, x1 + 1, y1 + 1, color);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int x = x1 + dx * i / steps;
			int y = y1 + dy * i / steps;
			context.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void drawRectOutline(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}
}
