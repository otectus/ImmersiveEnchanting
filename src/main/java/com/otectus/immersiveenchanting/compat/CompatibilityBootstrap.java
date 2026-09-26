package com.otectus.immersiveenchanting.compat;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraftforge.fml.ModList;

/**
 * Loads dedicated integrations for mods that change the enchanting table itself. Each integration lives in its own
 * package and is reached only by name, after its mod is confirmed present, so no core class ever links against an
 * optional mod. A failing integration is disabled with one warning; the rest of the mod, and generic behaviour, carry on.
 *
 * <p>Mods that merely add enchantments need no integration: the table rolls them and the generic profile handles them.
 */
public final class CompatibilityBootstrap {

    public static void init() {
        load("easymagic", "com.otectus.immersiveenchanting.compat.easymagic.EasyMagicCompat");
        load("apotheosis", "com.otectus.immersiveenchanting.compat.apotheosis.ApotheosisCompat");
    }

    private static void load(String modId, String entryClass) {
        if (!ModList.get().isLoaded(modId)) return;
        try {
            Class.forName(entryClass).getMethod("init").invoke(null);
            ImmersiveEnchanting.LOGGER.info("Immersive Enchanting integration for {} enabled", modId);
        } catch (Throwable t) {
            ImmersiveEnchanting.LOGGER.warn("Immersive Enchanting integration for {} failed to initialize and is disabled; "
                    + "that mod's enchanting is left untouched", modId, t);
        }
    }

    private CompatibilityBootstrap() {}
}
