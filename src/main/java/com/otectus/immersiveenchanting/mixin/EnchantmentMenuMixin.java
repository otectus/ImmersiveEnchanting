package com.otectus.immersiveenchanting.mixin;

import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Settles a ritual whose table is being closed while the lapis and item are still in the menu, before
 * {@code removed} returns them to the player. Menus that override {@code removed} without calling super are caught
 * by the session tick instead.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void immersiveenchanting$settleRitual(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            RitualSessionManager.onMenuRemoved(serverPlayer, (AbstractContainerMenu) (Object) this);
        }
    }
}
