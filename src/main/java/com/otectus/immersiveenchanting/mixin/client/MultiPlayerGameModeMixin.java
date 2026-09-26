package com.otectus.immersiveenchanting.mixin.client;

import com.otectus.immersiveenchanting.client.EnchantmentScreenHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Safety net for enchanting screens other than the vanilla one (resource-pack or UI mods that subclass or replace
 * {@code EnchantmentScreen} over a supported menu): the screen does its own hit-testing, and when it sends an offer
 * button click for a menu an adapter owns, the ritual starts instead of the packet. The vanilla screen is intercepted
 * earlier, through Forge's screen input event, and never reaches this.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleInventoryButtonClick", at = @At("HEAD"), cancellable = true)
    private void immersiveenchanting$interceptOffer(int containerId, int buttonId, CallbackInfo ci) {
        if (EnchantmentScreenHooks.interceptButtonClick(containerId, buttonId)) ci.cancel();
    }
}
