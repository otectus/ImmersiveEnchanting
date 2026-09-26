package com.otectus.immersiveenchanting.compat.easymagic;

import com.otectus.immersiveenchanting.api.ImmersiveEnchantingApi;

/** Entry point, reached by name from {@code CompatibilityBootstrap} only when Easy Magic is loaded. */
public final class EasyMagicCompat {
    public static void init() {
        ImmersiveEnchantingApi.registerAdapter(new EasyMagicAdapter());
    }

    private EasyMagicCompat() {}
}
