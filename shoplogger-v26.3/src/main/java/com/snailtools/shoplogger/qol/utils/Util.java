package com.snailtools.shoplogger.qol.utils;


import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class Util {

    public static boolean hasCustomItemModel(ItemStack stack, String targetModel) {
        Identifier itemModel = stack.get(DataComponents.ITEM_MODEL);
        return itemModel != null && itemModel.toString().equals(targetModel);
    }

    public boolean hasCustomDataSet(ItemStack stack, String data) {
        CompoundTag publicBukkitValues = getPublicBukkitValues(stack);
        return publicBukkitValues != null && publicBukkitValues.contains(data);
    }

    public static Tag getCustomDataVar(ItemStack stack, String data) {
        CompoundTag publicBukkitValues = getPublicBukkitValues(stack);
        if (publicBukkitValues == null || !publicBukkitValues.contains(data)) return null;
        return publicBukkitValues.get(data);
    }

    public static <T extends Tag> T getCustomDataVar(ItemStack stack, String data, Class<T> type) {
        Tag tag = getCustomDataVar(stack, data);
        if (type.isInstance(tag)) {
            return type.cast(tag);
        }
        return null;
    }

    private static CompoundTag getPublicBukkitValues(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (tag.contains("PublicBukkitValues") && tag.getCompound("PublicBukkitValues").isPresent()) {
            return tag.getCompound("PublicBukkitValues").get();
        }
        return null;
    }
}
