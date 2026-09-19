package com.snailtools.mapartscanner;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import com.mojang.brigadier.context.CommandContext;

/**
 * Temporary, standalone mapart scanner — deliberately separate from the real
 * Shop Logger mod. Scanning starts OFF every launch; press \ to toggle it.
 */
public class MapartScannerClient implements ClientModInitializer {

	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mapartscanner", "main"));

	private final MapartScanner scanner = new MapartScanner();
	private KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.mapartscanner.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_BACKSLASH,
				KEY_CATEGORY
		));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("mapartworld")
						.then(ClientCommands.literal("firefly").executes(ctx -> setWorld(ctx, "Firefly")))
						.then(ClientCommands.literal("honeybee").executes(ctx -> setWorld(ctx, "Honeybee")))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) scanner.toggle(client);
			scanner.tick(client);
		});
	}

	private static int setWorld(CommandContext<FabricClientCommandSource> ctx, String world) {
		MapartWorld.set(world);
		ctx.getSource().sendFeedback(Component.literal("[Mapart] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("World set to " + world + ".").withStyle(ChatFormatting.GREEN)));
		return 1;
	}
}
