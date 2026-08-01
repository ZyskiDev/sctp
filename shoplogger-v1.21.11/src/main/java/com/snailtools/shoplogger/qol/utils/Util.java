package com.snailtools.shoplogger.qol.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

public class Util {

    public static boolean hasCustomItemModel(ItemStack stack, String targetModel) {
        Identifier itemModel = stack.get(DataComponentTypes.ITEM_MODEL);
        return itemModel != null && itemModel.toString().equals(targetModel);
    }


    public boolean hasCustomDataSet(ItemStack stack, String data) {
        NbtCompound publicBukkitValues = getPublicBukkitValues(stack);
        return publicBukkitValues != null && publicBukkitValues.contains(data);
    }

    public static NbtElement getCustomDataVar(ItemStack stack, String data) {
        NbtCompound publicBukkitValues = getPublicBukkitValues(stack);
        if (publicBukkitValues == null || !publicBukkitValues.contains(data)) return null;
        return publicBukkitValues.get(data);
    }

    public static <T extends NbtElement> T getCustomDataVar(ItemStack stack, String data, Class<T> type) {
        NbtElement tag = getCustomDataVar(stack, data);
        if (type.isInstance(tag)) {
            return type.cast(tag);
        }
        return null;
    }

    private static NbtCompound getPublicBukkitValues(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        NbtCompound tag = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();

        if (tag.contains("PublicBukkitValues") && tag.getCompound("PublicBukkitValues").isPresent()) {
            return tag.getCompound("PublicBukkitValues").get();
        }
        return null;
    }
}
