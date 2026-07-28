package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void shoplogger$maybeSuppressScreen(Screen screen, CallbackInfo ci) {
		if (SilentScreenCoordinator.isArmed() && screen instanceof AbstractContainerScreen<?> containerScreen) {
			SilentScreenCoordinator.onScreenSuppressed(containerScreen.getMenu());
			ci.cancel();
		}
	}
}
