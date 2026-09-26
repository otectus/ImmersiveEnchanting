package com.otectus.immersiveenchanting.mixin;

import com.otectus.immersiveenchanting.enchanting.EnchantGuard;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server guard: rejects a direct enchant-button packet while a ritual is required for that menu, so a modified client
 * cannot skip the ritual. It sits on the packet path, just before the menu's {@code clickMenuButton}, so it covers
 * menus that override that method, never blocks buttons an adapter does not call offers (rerolls, pages), and never
 * affects server-side code that enchants directly. Conditional on {@code serverGuardVanillaEnchantButton} and on the
 * owning adapter.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleContainerButtonClick", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z"))
    private void immersiveenchanting$guardEnchantButton(ServerboundContainerButtonClickPacket packet, CallbackInfo ci) {
        if (EnchantGuard.shouldBlock(player, player.containerMenu, packet.getButtonId())) ci.cancel();
    }
}
