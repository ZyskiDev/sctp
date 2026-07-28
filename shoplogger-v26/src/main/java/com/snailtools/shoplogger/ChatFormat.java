package com.snailtools.shoplogger;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Shared color scheme for the mod's chat output, so messages are easy to scan at a glance. */
public final class ChatFormat {

	public static final ChatFormatting PREFIX = ChatFormatting.GOLD;
	public static final ChatFormatting SUCCESS = ChatFormatting.GREEN;
	public static final ChatFormatting ERROR = ChatFormatting.RED;
	public static final ChatFormatting INFO = ChatFormatting.YELLOW;
	public static final ChatFormatting NEUTRAL = ChatFormatting.GRAY;
	public static final ChatFormatting RESULT = ChatFormatting.WHITE;
	public static final ChatFormatting PRICE = ChatFormatting.GOLD;
	public static final ChatFormatting STACK = ChatFormatting.AQUA;
	public static final ChatFormatting ITEMS = ChatFormatting.WHITE;

	private ChatFormat() {}

	/** "[ShopLogger] " in PREFIX color, followed by body in bodyColor. */
	public static Component prefixed(ChatFormatting bodyColor, String body) {
		return Component.literal("[ShopLogger] ").withStyle(PREFIX)
				.append(Component.literal(body).withStyle(bodyColor));
	}

	/** Sends a "[ShopLogger] "-prefixed, colored message to the player, if present. */
	public static void send(Minecraft client, ChatFormatting bodyColor, String body) {
		if (client.player != null) {
			client.player.sendSystemMessage(prefixed(bodyColor, body));
		}
	}

	/** Sends a colored message with no "[ShopLogger] " prefix, if the player is present. */
	public static void sendPlain(Minecraft client, ChatFormatting color, String body) {
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal(body).withStyle(color));
		}
	}
}
