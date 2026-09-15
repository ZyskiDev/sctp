package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In 26.2, server-driven container screens (e.g. from a
 * ClientboundOpenScreenPacket) no longer go through Minecraft#setScreenAndShow
 * at all — MenuScreens.create()'s registered ScreenConstructor.fromPacket()
 * default method now calls Minecraft.gui.setScreen(Screen) directly instead.
 * This is a real behavior change from 26.1 (confirmed by disassembling both
 * versions' jars), not just a rename, which is why this mixin targets Gui
 * instead of Minecraft here — see shoplogger-v26's MinecraftClientMixin for
 * the 26.1.x equivalent.
 */
@Mixin(Gui.class)
public abstract class GuiScreenMixin {

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void shoplogger$maybeSuppressScreen(Screen screen, CallbackInfo ci) {
		if (SilentScreenCoordinator.isArmed() && screen instanceof AbstractContainerScreen<?> containerScreen) {
			SilentScreenCoordinator.onScreenSuppressed(containerScreen.getMenu());
			ci.cancel();
		}
	}
}
