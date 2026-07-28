package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void shoplogger$maybeSuppressScreen(Screen screen, CallbackInfo ci) {
		if (SilentScreenCoordinator.isArmed() && screen instanceof HandledScreen<?> handledScreen) {
			SilentScreenCoordinator.onScreenSuppressed(handledScreen.getScreenHandler());
			ci.cancel();
		}
	}
}
