package com.snailtools.shoplogger.qol;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.render.RenderTickCounter;

public class QolHookManager {

    //lil place I can declare a bunch of hooks and just slot new standalone addons into them
    private static ButterflyGarden butterflyGarden;
    private static QuestHelper questHelper;

    public static void onInit(){
        butterflyGarden = new ButterflyGarden();
        questHelper = new QuestHelper();
    }

    public static void onTick(){
        if(butterflyGarden != null) butterflyGarden.tick();
    }

    public static void onMouseEvent(long window, MouseInput mouseButtonInfo, int action){
        if(butterflyGarden != null) butterflyGarden.onMouseEvent(window, mouseButtonInfo, action);
        if(questHelper != null) questHelper.onMouseEvent(window, mouseButtonInfo, action);
    }

    public static void onHudRender(DrawContext drawContext, RenderTickCounter renderTickCounter) {
        if(butterflyGarden != null) butterflyGarden.onHUDRender(drawContext);
        if(questHelper != null) questHelper.onHUDRender(drawContext);
    }

    public static void onScreenRender(Screen screen, DrawContext drawContext, int mouseX, int mouseY, float a){
        if(butterflyGarden != null) {
            butterflyGarden.onScreenRender(screen, drawContext, mouseX, mouseY, a);
        }
        if(questHelper != null) questHelper.onScreenRender(screen, drawContext, mouseX, mouseY, a);
    }

    public static void onKeyEvent(){
        // do resetting ui positions to default coords on certain key combo
    }

}
