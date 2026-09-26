package com.otectus.immersiveenchanting.enchanting;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.data.EnchantmentProfileManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Optional;

/** Decides whether a direct enchant-button packet must be refused because a ritual is required. */
public final class EnchantGuard {
    private static boolean warnedDisabled;

    public static boolean shouldBlock(ServerPlayer player, AbstractContainerMenu menu, int buttonId) {
        if (player == null || menu == null || !ServerConfig.get(ServerConfig.ENABLED)) return false;
        if (!ServerConfig.get(ServerConfig.SERVER_GUARD_VANILLA_ENCHANT_BUTTON)) {
            if (!warnedDisabled) {
                warnedDisabled = true;
                ImmersiveEnchanting.LOGGER.info("Immersive Enchanting server guard is disabled (serverGuardVanillaEnchantButton = false); "
                        + "clients can enchant without a ritual by sending the vanilla button packet.");
            }
            return false;
        }
        Optional<EnchantingContextAdapter> owner = AdapterRegistry.find(player, menu);
        if (owner.isEmpty()) return false;
        EnchantingContextAdapter adapter = owner.get();
        if (!adapter.guardsDirectEnchanting() || !adapter.isOfferButton(menu, buttonId)) return false;
        if (!RitualPolicy.requiresRitual(player, adapter, menu)) return false;
        // Offers whose plan contains an enchantment with ritual behaviour disabled keep the direct path.
        boolean disabledByProfile = adapter.snapshot(player, menu, buttonId)
                .map(s -> s.plannedEnchantments().stream().anyMatch(e -> !EnchantmentProfileManager.resolve(e.enchantment).enabled()))
                .orElse(false);
        if (disabledByProfile) return false;
        ImmersiveEnchanting.debug("rejected direct enchant button {} from {} on {} ({})", buttonId,
                player.getGameProfile().getName(), menu.getClass().getSimpleName(), adapter.id());
        return true;
    }

    private EnchantGuard() {}
}
