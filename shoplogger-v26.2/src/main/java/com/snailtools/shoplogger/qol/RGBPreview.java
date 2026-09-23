package com.snailtools.shoplogger.qol;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RGBPreview {

    private static final Pattern HEX_BLOCK = Pattern.compile("&#([0-9a-fA-F]{6})");

    public static int execute(CommandContext<FabricClientCommandSource> ctx) {

        String input = StringArgumentType.getString(ctx, "name");
        Minecraft minecraft = Minecraft.getInstance();
        MutableComponent name = parseName(input);

        if (name == null || name.getString().isEmpty()) {
            minecraft.player.sendSystemMessage(Component.literal("Please input a valid rename string format"));
            return 0;
        }

        if (minecraft.player == null) {
            return 0;
        }

        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            minecraft.player.sendSystemMessage(Component.literal("Please hold an item whilst using this command"));
            return 0;
        }

        held.set(DataComponents.CUSTOM_NAME, name);
        minecraft.player.sendOverlayMessage(name);
        minecraft.player.sendSystemMessage(Component.literal("Preview ' ")
                .append(name.copy())
                .append(Component.literal(" ' applied to held item")));
        minecraft.player.sendSystemMessage(Component.literal("Move or drop the item to remove preview."));
        return 1;
    }


    private static MutableComponent parseName(String command) {
        String input = unwrapQuoted(command == null ? "" : command.trim());
        Matcher matcher = HEX_BLOCK.matcher(input);
        if (!matcher.find()) {
            return null;
        }

        MutableComponent name = Component.empty();
        int color = Integer.parseInt(matcher.group(1), 16);
        int textStart = matcher.end();

        while (matcher.find()) {
            appendStyled(name, input.substring(textStart, matcher.start()), color);
            color = Integer.parseInt(matcher.group(1), 16);
            textStart = matcher.end();
        }

        appendStyled(name, input.substring(textStart), color);
        return name;
    }

    private static void appendStyled(MutableComponent name, String text, int color) {
        if (text.isEmpty()) {
            return;
        }

        name.append(Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false)));
    }

    private static String unwrapQuoted(String input) {
        if (input.length() < 2) {
            return input;
        }

        char first = input.charAt(0);
        char last = input.charAt(input.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return input.substring(1, input.length() - 1);
        }

        return input;
    }




}
