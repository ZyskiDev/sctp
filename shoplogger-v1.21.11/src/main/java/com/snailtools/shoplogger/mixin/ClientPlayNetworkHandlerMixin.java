package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.SilentScreenCoordinator;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks:
 *  1. Redirect the MinecraftClient#setScreen(...) call inside onOpenScreen so
 *     that, when SilentScreenCoordinator has a listener armed, the container
 *     GUI is simply never shown (the ScreenHandler still gets created/
 *     assigned normally by vanilla code before this call, so slot data still
 *     syncs).
 *  2. After the full inventory sync packet for that screen has been applied,
 *     hand control to the armed listener to read the now-populated slots and
 *     close the (invisible) screen handler again.
 *
 * Manual/player-initiated container opens are completely unaffected — the
 * redirect only triggers while SilentScreenCoordinator.isArmed() is true,
 * which is only ever set right before *we* trigger a silent interaction.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

	@Inject(method = "onInventory", at = @At("TAIL"))
	private void shoplogger$onInventorySynced(InventoryS2CPacket packet, CallbackInfo ci) {
		if (SilentScreenCoordinator.isArmed()) {
			SilentScreenCoordinator.onInventorySynced(packet.syncId(), (ClientPlayNetworkHandler) (Object) this);
		}
	}
}
