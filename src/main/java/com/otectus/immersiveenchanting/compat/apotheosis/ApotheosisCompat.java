package com.otectus.immersiveenchanting.compat.apotheosis;

import com.otectus.immersiveenchanting.api.ImmersiveEnchantingApi;

/** Entry point, reached by name from {@code CompatibilityBootstrap} only when Apotheosis is loaded. */
public final class ApotheosisCompat {
    public static void init() throws ReflectiveOperationException {
        ImmersiveEnchantingApi.registerAdapter(new ApotheosisAdapter());
    }

    private ApotheosisCompat() {}
}
