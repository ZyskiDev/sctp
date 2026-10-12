package com.snailtools.shoplogger.mixin;

import com.snailtools.shoplogger.qol.BuyConfirm;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every click inside a container (normal, shift, number key, drag, double)
 * goes through handleContainerInput on its way to the server — the one place
 * an expensive shop buy can be held back until it's confirmed (see BuyConfirm).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class BuyConfirmMixin {

	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void shoplogger$confirmExpensiveBuy(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (BuyConfirm.shouldBlock(containerId, slotId)) ci.cancel();
	}
}
