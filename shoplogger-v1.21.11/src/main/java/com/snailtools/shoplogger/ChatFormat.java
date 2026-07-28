package com.snailtools.shoplogger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Shared color scheme for the mod's chat output, so messages are easy to scan at a glance. */
public final class ChatFormat {

	public static final Formatting PREFIX = Formatting.GOLD;
	public static final Formatting SUCCESS = Formatting.GREEN;
	public static final Formatting ERROR = Formatting.RED;
	public static final Formatting INFO = Formatting.YELLOW;
	public static final Formatting NEUTRAL = Formatting.GRAY;
	public static final Formatting RESULT = Formatting.WHITE;
	public static final Formatting PRICE = Formatting.GOLD;
	public static final Formatting STACK = Formatting.AQUA;
	public static final Formatting ITEMS = Formatting.WHITE;

	private ChatFormat() {}

	/** "[ShopLogger] " in PREFIX color, followed by body in bodyColor. */
	public static Text prefixed(Formatting bodyColor, String body) {
		return Text.literal("[ShopLogger] ").formatted(PREFIX)
				.append(Text.literal(body).formatted(bodyColor));
	}

	/** Sends a "[ShopLogger] "-prefixed, colored message to the player, if present. */
	public static void send(MinecraftClient client, Formatting bodyColor, String body) {
		if (client.player != null) {
			client.player.sendMessage(prefixed(bodyColor, body), false);
		}
	}

	/** Sends a colored message with no "[ShopLogger] " prefix, if the player is present. */
	public static void sendPlain(MinecraftClient client, Formatting color, String body) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(body).formatted(color), false);
		}
	}
}
