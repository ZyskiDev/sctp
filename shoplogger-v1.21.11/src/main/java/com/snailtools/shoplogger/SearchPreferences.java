package com.snailtools.shoplogger;

import com.snailtools.shoplogger.config.Config;

/** Whether /search opens the in-game Listings GUI (default) or prints results to chat. */
public final class SearchPreferences {

	private static final String CONFIG_GUI_SEARCH = "search/useGui";

	private SearchPreferences() {}

	public static boolean isGuiSearch() {
		return Config.getOrCreate(CONFIG_GUI_SEARCH, Boolean.class, true);
	}

	public static void setGuiSearch(boolean value) {
		Config.update(CONFIG_GUI_SEARCH, value);
	}
}
