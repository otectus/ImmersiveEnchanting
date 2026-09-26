package com.otectus.immersiveenchanting.enchanting;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.server.permission.PermissionAPI;

/**
 * Whether a player must perform a ritual. The client evaluates the synced parts ({@link #requiredForPlayer}) to decide
 * whether to intercept; the server evaluates everything, including the bypass permission the client cannot see, and
 * performs the normal enchant itself for exempt players.
 */
public final class RitualPolicy {

    /** The parts both sides can evaluate: master switch, creative bypass, item or book. */
    public static boolean requiredForPlayer(Player player, ItemStack target) {
        if (!ServerConfig.get(ServerConfig.ENABLED)) return false;
        if (player.getAbilities().instabuild && ServerConfig.get(ServerConfig.CREATIVE_BYPASS)) return false;
        boolean book = target.is(Items.BOOK);
        return book ? ServerConfig.get(ServerConfig.RITUALS_FOR_BOOKS) : ServerConfig.get(ServerConfig.RITUALS_FOR_ITEMS);
    }

    public static boolean requiresRitual(ServerPlayer player, EnchantingContextAdapter adapter, AbstractContainerMenu menu) {
        if (!requiredForPlayer(player, adapter.targetItem(menu))) return false;
        if (ServerConfig.get(ServerConfig.ALLOW_SERVER_BYPASS_PERMISSION)) {
            try {
                if (PermissionAPI.getPermission(player, Permissions.BYPASS_RITUAL)) return false;
            } catch (RuntimeException e) {
                ImmersiveEnchanting.debug("bypass permission check failed for {}: {}", player.getGameProfile().getName(), e.toString());
            }
        }
        return true;
    }

    private RitualPolicy() {}
}
