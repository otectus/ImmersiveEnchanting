package com.otectus.immersiveenchanting.enchanting;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registered adapters, highest priority first. Adapter selection is by the menu's actual type; an adapter that throws
 * is disabled on its own with one warning, leaving the others working.
 */
public final class AdapterRegistry {
    private static final List<EnchantingContextAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final Set<EnchantingContextAdapter> DISABLED = ConcurrentHashMap.newKeySet();
    private static final Set<String> VANILLA_COMPATIBLE = ConcurrentHashMap.newKeySet();
    private static volatile boolean frozen;

    public static synchronized void register(EnchantingContextAdapter adapter) {
        if (frozen) {
            ImmersiveEnchanting.LOGGER.warn("Ignoring Immersive Enchanting adapter {} registered after loading completed; register during common setup.", adapter.id());
            return;
        }
        ADAPTERS.removeIf(a -> a.id().equals(adapter.id()));
        List<EnchantingContextAdapter> sorted = new ArrayList<>(ADAPTERS);
        sorted.add(adapter);
        sorted.sort(Comparator.comparingInt(EnchantingContextAdapter::priority).reversed()
                .thenComparing(a -> a.id().toString()));
        ADAPTERS.clear();
        ADAPTERS.addAll(sorted);
        ImmersiveEnchanting.LOGGER.info("Immersive Enchanting adapter registered: {} (priority {})", adapter.id(), adapter.priority());
    }

    public static void registerVanillaCompatibleMenu(String className) {
        if (frozen) {
            ImmersiveEnchanting.LOGGER.warn("Ignoring vanilla-compatible menu {} registered after loading completed.", className);
            return;
        }
        VANILLA_COMPATIBLE.add(className);
    }

    public static void registerBuiltIn() {
        register(VanillaEnchantingAdapter.INSTANCE);
    }

    public static void freeze() {
        frozen = true;
    }

    public static List<EnchantingContextAdapter> adapters() {
        return List.copyOf(ADAPTERS);
    }

    /** Server-side owner of a menu. */
    public static Optional<EnchantingContextAdapter> find(ServerPlayer player, AbstractContainerMenu menu) {
        if (menu == null) return Optional.empty();
        for (EnchantingContextAdapter adapter : ADAPTERS) {
            if (DISABLED.contains(adapter)) continue;
            try {
                if (adapter.supports(player, menu)) return Optional.of(adapter);
            } catch (Throwable t) {
                disable(adapter, t);
            }
        }
        return Optional.empty();
    }

    /** Side-agnostic owner of a menu, by type only. Used by the client to decide whether to intercept a click. */
    public static Optional<EnchantingContextAdapter> findForMenu(AbstractContainerMenu menu) {
        if (menu == null) return Optional.empty();
        for (EnchantingContextAdapter adapter : ADAPTERS) {
            if (DISABLED.contains(adapter)) continue;
            try {
                if (adapter.supportsMenu(menu)) return Optional.of(adapter);
            } catch (Throwable t) {
                disable(adapter, t);
            }
        }
        return Optional.empty();
    }

    /**
     * The adapter reporting the highest level cap for an enchantment, for commands with no table in context. Ties go
     * to the higher priority; the vanilla adapter answers when no other system raises the cap.
     */
    public static EnchantingContextAdapter widestLevelRange(Enchantment enchantment) {
        EnchantingContextAdapter best = VanillaEnchantingAdapter.INSTANCE;
        int bestMax = enchantment.getMaxLevel();
        for (EnchantingContextAdapter adapter : ADAPTERS) {
            if (DISABLED.contains(adapter)) continue;
            try {
                int max = adapter.maxLevel(enchantment);
                if (max > bestMax) {
                    best = adapter;
                    bestMax = max;
                }
            } catch (Throwable t) {
                disable(adapter, t);
            }
        }
        return best;
    }

    /** Menus registered or configured as vanilla-compatible subclasses of {@link EnchantmentMenu}. */
    public static boolean isVanillaCompatible(AbstractContainerMenu menu) {
        if (!(menu instanceof EnchantmentMenu)) return false;
        String name = menu.getClass().getName();
        return VANILLA_COMPATIBLE.contains(name) || ServerConfig.vanillaCompatibleMenus().contains(name);
    }

    public static void disable(EnchantingContextAdapter adapter, Throwable cause) {
        if (DISABLED.add(adapter)) {
            ImmersiveEnchanting.LOGGER.warn("Immersive Enchanting adapter {} failed and has been disabled; its enchanting system is left untouched.",
                    adapter.id(), cause);
        }
    }

    private AdapterRegistry() {}
}
