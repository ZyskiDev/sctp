package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

/**
 * Stacks repeated chat (Settings › Look & tools, on by default): when a message is
 * like the one right before it, the older line is replaced by the new one with
 * "×2", "×3"... after it, instead of filling chat with near-copies (selling
 * butterflies shows one line per butterfly, each with a different name).
 *
 * Similar means: the same first two words (so two different players never stack,
 * and "You sold ..." only stacks with "You sold ..."), the rest at least half the
 * same (edit distance), and within a minute. ChatComponentMixin calls this.
 */
public final class ChatStacker {

	private ChatStacker() {}

	private static final String CONFIG_ENABLED = "chat/stackRepeats";
	private static final long WINDOW_MS = 60_000L;
	private static final double MIN_SIMILARITY = 0.5;
	private static final int MAX_COMPARE = 300;

	private static GuiMessage last;    // the chat line we stacked onto (or may stack onto next)
	private static String lastText;
	private static int count = 1;
	private static long lastAt;

	public static boolean isEnabled() {
		return Config.getOrCreate(CONFIG_ENABLED, Boolean.class, true);
	}

	public static void setEnabled(boolean value) {
		Config.update(CONFIG_ENABLED, value);
	}

	/**
	 * Called before a message is added. Returns the message to show: the same one,
	 * or a copy with "×N" when it stacks onto newest (which the caller then removes).
	 */
	public static Component onMessage(Component message, GuiMessage newest) {
		String text = message.getString();
		long now = System.currentTimeMillis();
		if (isEnabled() && last != null && newest == last && now - lastAt <= WINDOW_MS && similar(lastText, text)) {
			count++;
			lastText = text;
			lastAt = now;
			return message.copy().append(Component.literal(" ×" + count).withStyle(ChatFormatting.GRAY));
		}
		count = 1;
		lastText = text;
		lastAt = now;
		return message;
	}

	/** Called after a message was added, with the newest chat line. */
	public static void added(GuiMessage newest) {
		last = newest;
	}

	static boolean similar(String a, String b) {
		if (a == null || b == null) return false;
		if (a.equals(b)) return true;
		String[] wa = a.trim().split("\\s+"), wb = b.trim().split("\\s+");
		if (wa.length < 2 || wb.length < 2 || !wa[0].equals(wb[0]) || !wa[1].equals(wb[1])) return false;
		if (a.length() > MAX_COMPARE || b.length() > MAX_COMPARE) return false;
		int max = Math.max(a.length(), b.length());
		return 1.0 - (double) distance(a, b) / max >= MIN_SIMILARITY;
	}

	/** Levenshtein edit distance. */
	private static int distance(String a, String b) {
		int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) prev[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev; prev = cur; cur = t;
		}
		return prev[b.length()];
	}
}
