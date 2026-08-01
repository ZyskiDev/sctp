package com.snailtools.shoplogger.qol;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;

public class QolHookManager {

    //lil place I can declare a bunch of hooks and just slot new standalone addons into them
    private static ButterflyGarden butterflyGarden;

    public static void onInit(){
        butterflyGarden = new ButterflyGarden();
    }

    public static void onTick(){
        if(butterflyGarden != null) butterflyGarden.tick();
    }

    public static void onMouseEvent(long window, MouseButtonInfo mouseButtonInfo, int action){
        if(butterflyGarden != null) butterflyGarden.onMouseEvent(window, mouseButtonInfo, action);
    }

    public static void onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker tickDelta){
        if(butterflyGarden != null) butterflyGarden.onHUDRender(guiGraphics);
    }

    public static void onScreenRender(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a){
        if(butterflyGarden != null) butterflyGarden.onScreenRender(screen, graphics, mouseX, mouseY, a);
    }
}
