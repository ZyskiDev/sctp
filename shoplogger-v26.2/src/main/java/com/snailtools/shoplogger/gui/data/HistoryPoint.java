package com.snailtools.shoplogger.gui.data;

/** Mirrors one row from GET /items/history?itemKey=... — see worker.js's itemDailyStats table. */
public class HistoryPoint {
	public String world;
	public String date;
	public double avgPrice;
	public double lowestPrice;
	public double highestPrice;
	public int listingCount;
	public int sellerCount;
	public int totalStock;
}
