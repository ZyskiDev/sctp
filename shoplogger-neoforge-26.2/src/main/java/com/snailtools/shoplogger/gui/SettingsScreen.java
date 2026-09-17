package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ChatFormat;
import com.snailtools.shoplogger.CsvExporter;
import com.snailtools.shoplogger.ExcelExporter;
import com.snailtools.shoplogger.OwnShopSaleTracker;
import com.snailtools.shoplogger.RareRentalHighlighter;
import com.snailtools.shoplogger.ScanChatLogger;
import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.ShopLog;
import com.snailtools.shoplogger.ShopMarkerRenderer;
import com.snailtools.shoplogger.ShopUploader;
import com.snailtools.shoplogger.ShopVisitAlert;
import com.snailtools.shoplogger.WatchlistStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Everything that used to be hotkey-only, gathered in one screen. Laid out as
 * two side-by-side categories (Scanning / Search & Alerts) when there's room,
 * collapsing to one column otherwise — see computeLayout(). Reachable from
 * HomeScreen — the hotkeys themselves keep working unchanged, this is just an
 * additional way to reach the same actions.
 */
public class SettingsScreen extends Screen {

	private static final int LEFT_ITEM_COUNT = 3; // Chest scanning, Recently-scanned markers, Print scans in chat
	private static final int RIGHT_ITEM_COUNT = 5; // New-item alerts, rares-only, Rare rental highlights, Own-shop sale alerts, Watchlist marketplace
	private static final int ACTION_BUTTON_COUNT = 4; // Export, Upload, Advanced settings, Back
	private static final int NATURAL_GAP = 24;
	private static final int MIN_GAP = 16; // never shrink spacing below this — rows start overlapping past this point
	private static final int NATURAL_COL_W = 200;
	private static final int NATURAL_COL_GAP = 20;
	private static final int SIDE_MARGIN = 10;
	private static final int BOTTOM_MARGIN = 10;
	private static final int TOP_Y = 56;
	private static final int HEADER_H = 16;

	private final Screen parent;
	private final List<HeaderLabel> headers = new ArrayList<>();

	private record HeaderLabel(String text, int x, int y) {}

	/**
	 * Single source of truth for every widget's position, so the "does this
	 * fit?" check below and the actual placement can never disagree. Two
	 * columns collapse into one (right column's rows continuing under the
	 * left's) when the window's too narrow for both at once; the row gap
	 * itself shrinks (see init()) when the window's too short, rather than
	 * ever letting rows — especially the bottom action buttons — land below
	 * the visible/clickable area, which is what actually happened at some
	 * high GUI-scale / small-window combinations.
	 */
	private record Layout(int leftX, int rightX, int colW, int actionW, boolean singleColumn,
			int leftHeaderY, int[] leftItemY, int rightHeaderY, int[] rightItemY, int[] actionY, int bottom) {}

	public SettingsScreen(Screen parent) {
		super(Component.literal("Shop Logger Settings"));
		this.parent = parent;
	}

	private Layout computeLayout(int gap) {
		boolean singleColumn = width < NATURAL_COL_W * 2 + NATURAL_COL_GAP + SIDE_MARGIN * 2;
		int colW = singleColumn ? Math.max(120, width - SIDE_MARGIN * 2) : NATURAL_COL_W;
		int leftX = singleColumn ? SIDE_MARGIN : (width / 2 - NATURAL_COL_GAP / 2 - NATURAL_COL_W);
		int rightX = singleColumn ? SIDE_MARGIN : (width / 2 + NATURAL_COL_GAP / 2);
		int actionW = singleColumn ? colW : (NATURAL_COL_W * 2 + NATURAL_COL_GAP);

		int y = TOP_Y;
		int leftHeaderY = y;
		y += HEADER_H;
		int[] leftItemY = new int[LEFT_ITEM_COUNT];
		for (int i = 0; i < LEFT_ITEM_COUNT; i++) {
			leftItemY[i] = y;
			y += gap;
		}
		int leftBottom = y + 12;

		// Two columns run side by side from the same starting y; stacked into
		// one column, the right section instead continues right after the left.
		y = singleColumn ? leftBottom : TOP_Y;
		int rightHeaderY = y;
		y += HEADER_H;
		int[] rightItemY = new int[RIGHT_ITEM_COUNT];
		for (int i = 0; i < RIGHT_ITEM_COUNT; i++) {
			rightItemY[i] = y;
			y += gap;
		}
		int rightBottom = y;

		int bottomY = (singleColumn ? rightBottom : Math.max(leftBottom, rightBottom)) + 12;
		int[] actionY = new int[ACTION_BUTTON_COUNT];
		for (int i = 0; i < ACTION_BUTTON_COUNT; i++) {
			actionY[i] = bottomY;
			bottomY += (i == ACTION_BUTTON_COUNT - 2) ? gap + 12 : gap;
		}
		int bottom = actionY[ACTION_BUTTON_COUNT - 1] + 20;

		return new Layout(leftX, rightX, colW, actionW, singleColumn, leftHeaderY, leftItemY, rightHeaderY, rightItemY, actionY, bottom);
	}

	@Override
	protected void init() {
		headers.clear();

		int gap = NATURAL_GAP;
		while (gap > MIN_GAP && computeLayout(gap).bottom() > height - BOTTOM_MARGIN) gap--;
		Layout l = computeLayout(gap);
		int rightX = l.singleColumn() ? l.leftX() : l.rightX();

		// ---- left column: Scanning ----
		headers.add(new HeaderLabel("Scanning", l.leftX(), l.leftHeaderY()));

		addRenderableWidget(CycleButton.onOffBuilder(ShopAutoScanner.getInstance().isEnabled())
				.create(l.leftX(), l.leftItemY()[0], l.colW(), 20, Component.literal("Chest scanning"),
						(btn, value) -> ShopAutoScanner.getInstance().setEnabled(value)));

		addRenderableWidget(CycleButton.onOffBuilder(ShopMarkerRenderer.getInstance().isEnabled())
				.create(l.leftX(), l.leftItemY()[1], l.colW(), 20, Component.literal("Recently-scanned markers"),
						(btn, value) -> ShopMarkerRenderer.getInstance().setEnabled(value)));

		addRenderableWidget(CycleButton.onOffBuilder(ScanChatLogger.isEnabled())
				.create(l.leftX(), l.leftItemY()[2], l.colW(), 20, Component.literal("Print scans in chat"),
						(btn, value) -> ScanChatLogger.setEnabled(value)));

		// ---- right column: Search & Alerts ----
		headers.add(new HeaderLabel("Search & Alerts", rightX, l.rightHeaderY()));

		addRenderableWidget(CycleButton.onOffBuilder(ShopVisitAlert.isEnabled())
				.create(rightX, l.rightItemY()[0], l.colW(), 20, Component.literal("New-item alerts"),
						(btn, value) -> ShopVisitAlert.setEnabled(value)));

		addRenderableWidget(CycleButton.onOffBuilder(ShopVisitAlert.isRaresOnly())
				.create(rightX, l.rightItemY()[1], l.colW(), 20, Component.literal("New-item alerts: rares only"),
						(btn, value) -> ShopVisitAlert.setRaresOnly(value)));

		addRenderableWidget(CycleButton.onOffBuilder(RareRentalHighlighter.isEnabled())
				.create(rightX, l.rightItemY()[2], l.colW(), 20, Component.literal("Rare rental highlights"),
						(btn, value) -> RareRentalHighlighter.setEnabled(value)));

		addRenderableWidget(CycleButton.onOffBuilder(OwnShopSaleTracker.isMessagesEnabled())
				.create(rightX, l.rightItemY()[3], l.colW(), 20, Component.literal("Own-shop sale alerts"),
						(btn, value) -> OwnShopSaleTracker.setMessagesEnabled(value)));

		addRenderableWidget(CycleButton.onOffBuilder(WatchlistStore.isMarketplaceAlertsEnabled())
				.create(rightX, l.rightItemY()[4], l.colW(), 20, Component.literal("Watchlist: include marketplace"),
						(btn, value) -> WatchlistStore.setMarketplaceAlertsEnabled(value)));

		// ---- bottom: actions, shared full width ----
		addRenderableWidget(Button.builder(Component.literal("Export now (CSV + Excel)"), btn -> exportBoth())
				.bounds(l.leftX(), l.actionY()[0], l.actionW(), 20).build());

		addRenderableWidget(Button.builder(Component.literal("Upload now to Trading Post"), btn -> ShopUploader.uploadAsync(minecraft, true))
				.bounds(l.leftX(), l.actionY()[1], l.actionW(), 20).build());

		addRenderableWidget(Button.builder(Component.literal("Advanced settings..."), btn -> minecraft.setScreenAndShow(new AdvancedSettingsScreen(this)))
				.bounds(l.leftX(), l.actionY()[2], l.actionW(), 20).build());

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(l.leftX(), l.actionY()[3], l.actionW(), 20).build());
	}

	private void exportBoth() {
		try {
			Path runDir = minecraft.gameDirectory.toPath();
			Path csvOut = runDir.resolve("shoplogger").resolve("shops.csv");
			Path xlsxOut = runDir.resolve("shoplogger").resolve("shops.xlsx");

			CsvExporter.export(ShopLog.getAll(), csvOut);
			ExcelExporter.export(ShopLog.getAll(), xlsxOut);

			String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
			ChatFormat.send(minecraft, ChatFormat.SUCCESS,
					"Exported " + ShopLog.size() + " entries at " + time + " -> run/shoplogger/");
		} catch (Exception e) {
			ChatFormat.send(minecraft, ChatFormat.ERROR, "Export failed: " + e.getMessage());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
		for (HeaderLabel h : headers) {
			context.text(font, h.text(), h.x(), h.y(), 0xFFB7E23D);
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
