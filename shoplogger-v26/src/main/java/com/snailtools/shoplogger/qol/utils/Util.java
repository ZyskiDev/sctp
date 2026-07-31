package com.snailtools.shoplogger.qol.utils;


import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class Util {

    public static boolean hasCustomItemModel(ItemStack stack, String targetModel) {
        Identifier itemModel = stack.get(DataComponents.ITEM_MODEL);
        return itemModel != null && itemModel.toString().equals(targetModel);
    }
}
