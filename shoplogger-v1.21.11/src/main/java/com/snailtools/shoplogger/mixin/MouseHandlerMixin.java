package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.qol.QolHookManager;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseHandlerMixin {


    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMousePress(long window, MouseInput mouseButtonInfo, int action, CallbackInfo ci) {
        QolHookManager.onMouseEvent(window, mouseButtonInfo, action);
    }


}
