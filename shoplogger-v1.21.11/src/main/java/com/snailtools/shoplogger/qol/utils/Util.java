package com.snailtools.shoplogger.qol.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class Util {

    public static boolean hasCustomItemModel(ItemStack stack, String targetModel) {
        Identifier itemModel = stack.get(DataComponentTypes.ITEM_MODEL);
        return itemModel != null && itemModel.toString().equals(targetModel);
    }
}
