package com.snailtools.shoplogger.qol;

import com.snailtools.shoplogger.qol.uitls.Util;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;


public class Cooldowns {


    //adds the countdown to rotating quest scrolls
    public static void addQuestRotation(ItemStack stack, List<Text> lines){
        if(!stack.isEmpty()){
            if(Util.hasCustomItemModel(stack, "minecraft:paper")){
                NbtCompound tag = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
                if (tag.contains("PublicBukkitValues")) {
                    NbtCompound publicBukkitValues = tag.getCompound("PublicBukkitValues").get();
                    if (publicBukkitValues.contains("quests:randomtime")) {
                        long coolDown = publicBukkitValues.getLong("quests:randomtime").get() - System.currentTimeMillis();
                        if (coolDown > 0) {
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
                            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                            MutableText component = Text.literal("Changes In: " + simpleDateFormat.format(new Date(coolDown)));
                            component.setStyle(component.getStyle().withColor(Formatting.GRAY));
                            lines.add(2, component);
                        }
                    }
                }
            }
        }
    }

    //adds cooldowns for rare items that contain the cooldown within the item not player based cooldowns
    public static void addAvailableRareCooldowns(ItemStack stack, List<Text> lines){
        if(!stack.isEmpty()) {
            NbtCompound tag = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            if (tag.contains("PublicBukkitValues")) {
                NbtCompound publicBukkitValues = tag.getCompound("PublicBukkitValues").get();
                if (publicBukkitValues.contains("epicitems:cooldown-end") || publicBukkitValues.contains("epicitems:default")) {
                    long initial = (publicBukkitValues.contains("epicitems:cooldown-end") ? publicBukkitValues.getLong("epicitems:cooldown-end").get() : publicBukkitValues.getLong("epicitems:default").get());
                    long coolDown = initial - System.currentTimeMillis();
                    if (coolDown > 0) {

                        long days = coolDown / (1000 * 60 * 60 * 24);
                        long hours = (coolDown % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
                        long minutes = (coolDown % (1000 * 60 * 60)) / (1000 * 60);
                        long seconds = (coolDown % (1000 * 60)) / 1000;

                        StringBuilder timeFormat = new StringBuilder();

                        if (days > 0) {
                            timeFormat.append(String.format("%dd ", days));
                        }
                        if (hours > 0 || days > 0) {
                            timeFormat.append(String.format("%02dh ", hours));
                        }
                        if (minutes > 0 || hours > 0 || days > 0) {
                            timeFormat.append(String.format("%02dm ", minutes));
                        }
                        if (!timeFormat.isEmpty() || seconds > 0) {
                            timeFormat.append(String.format("%02ds", seconds));
                        }


                        if (timeFormat.isEmpty()) {
                            timeFormat.append("0s");
                        }

                        MutableText component = Text.literal("Ready In: " + timeFormat.toString().trim());
                        component.setStyle(component.getStyle().withColor(Formatting.GRAY));
                        lines.add(1, component);
                    }
                }
            }
        }
    }


}
