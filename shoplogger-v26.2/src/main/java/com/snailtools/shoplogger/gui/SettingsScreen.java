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
 * two side-by-side categories (Scanning / Search & Alerts) rather than one
 * long vertical list — the list outgrew a single column a while ago.
 * Reachable from HomeScreen — the hotkeys themselves keep working unchanged,
 * this is just an additional way to reach the same actions.
 */
public class SettingsScreen extends Screen {

	private final Screen parent;
	private final List<HeaderLabel> headers = new ArrayList<>();

	private record HeaderLabel(String text, int x, int y) {}

	public SettingsScreen(Screen parent) {
		super(Component.literal("Shop Logger Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		headers.clear();
		int centerX = width / 2;
		int colW = 200;
		int colGap = 20;
		int leftX = centerX - colGap / 2 - colW;
		int rightX = centerX + colGap / 2;
		int gap = 24;
		int topY = 56;

		// ---- left column: Scanning ----
		int y = topY;
		headers.add(new HeaderLabel("Scanning", leftX, y));
		y += 16;

		addRenderableWidget(CycleButton.onOffBuilder(ShopAutoScanner.getInstance().isEnabled())
				.create(leftX, y, colW, 20, Component.literal("Chest scanning"),
						(btn, value) -> ShopAutoScanner.getInstance().setEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(ShopMarkerRenderer.getInstance().isEnabled())
				.create(leftX, y, colW, 20, Component.literal("Recently-scanned markers"),
						(btn, value) -> ShopMarkerRenderer.getInstance().setEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(ScanChatLogger.isEnabled())
				.create(leftX, y, colW, 20, Component.literal("Print scans in chat"),
						(btn, value) -> ScanChatLogger.setEnabled(value)));
		y += gap + 12;
		int leftBottom = y;

		// ---- right column: Search & Alerts ----
		y = topY;
		headers.add(new HeaderLabel("Search & Alerts", rightX, y));
		y += 16;

		addRenderableWidget(CycleButton.onOffBuilder(ShopVisitAlert.isEnabled())
				.create(rightX, y, colW, 20, Component.literal("New-item alerts"),
						(btn, value) -> ShopVisitAlert.setEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(ShopVisitAlert.isRaresOnly())
				.create(rightX, y, colW, 20, Component.literal("New-item alerts: rares only"),
						(btn, value) -> ShopVisitAlert.setRaresOnly(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(RareRentalHighlighter.isEnabled())
				.create(rightX, y, colW, 20, Component.literal("Rare rental highlights"),
						(btn, value) -> RareRentalHighlighter.setEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(OwnShopSaleTracker.isMessagesEnabled())
				.create(rightX, y, colW, 20, Component.literal("Own-shop sale alerts"),
						(btn, value) -> OwnShopSaleTracker.setMessagesEnabled(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(WatchlistStore.isMarketplaceAlertsEnabled())
				.create(rightX, y, colW, 20, Component.literal("Watchlist: include marketplace"),
						(btn, value) -> WatchlistStore.setMarketplaceAlertsEnabled(value)));
		y += gap;
		int rightBottom = y;

		// ---- bottom: actions, shared full width across both columns ----
		int bottomY = Math.max(leftBottom, rightBottom) + 12;
		int actionW = colW * 2 + colGap;

		addRenderableWidget(Button.builder(Component.literal("Export now (CSV + Excel)"), btn -> exportBoth())
				.bounds(leftX, bottomY, actionW, 20).build());
		bottomY += gap;

		addRenderableWidget(Button.builder(Component.literal("Upload now to Trading Post"), btn -> ShopUploader.uploadAsync(minecraft, true))
				.bounds(leftX, bottomY, actionW, 20).build());
		bottomY += gap;

		addRenderableWidget(Button.builder(Component.literal("Advanced settings..."), btn -> minecraft.setScreenAndShow(new AdvancedSettingsScreen(this)))
				.bounds(leftX, bottomY, actionW, 20).build());
		bottomY += gap + 12;

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(leftX, bottomY, actionW, 20).build());
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
