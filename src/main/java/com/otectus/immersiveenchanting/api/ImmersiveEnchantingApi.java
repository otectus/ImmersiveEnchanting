package com.otectus.immersiveenchanting.api;

import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Entry points for other mods.
 *
 * <h2>Lifecycle</h2>
 * Register adapters and vanilla-compatible menus from your mod constructor or {@code FMLCommonSetupEvent}, on both
 * sides. Registration closes at {@code FMLLoadCompleteEvent}; later calls are ignored with a warning.
 *
 * <h2>Events</h2>
 * {@link com.otectus.immersiveenchanting.api.event.RitualPlanEvent} and
 * {@link com.otectus.immersiveenchanting.api.event.RitualResultEvent} are posted on the Forge event bus, on the
 * server thread only.
 */
public final class ImmersiveEnchantingApi {

    public static void registerAdapter(EnchantingContextAdapter adapter) {
        AdapterRegistry.register(adapter);
    }

    /**
     * Treats a menu class that subclasses the vanilla {@code EnchantmentMenu}, and keeps its offer selection and
     * enchant button logic, exactly like the vanilla table.
     */
    public static void registerVanillaCompatibleMenu(String menuClassName) {
        AdapterRegistry.registerVanillaCompatibleMenu(menuClassName);
    }

    public static List<EnchantingContextAdapter> adapters() {
        return AdapterRegistry.adapters();
    }

    public static boolean isRitualActive(ServerPlayer player) {
        return RitualSessionManager.get(player).isPresent();
    }

    public static Optional<RitualSessionView> activeSession(ServerPlayer player) {
        return RitualSessionManager.get(player).map(s -> s);
    }

    private ImmersiveEnchantingApi() {}
}
