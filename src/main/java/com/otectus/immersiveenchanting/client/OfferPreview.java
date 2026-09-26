package com.otectus.immersiveenchanting.client;

import com.otectus.immersiveenchanting.network.OfferDifficultyS2C;

import javax.annotation.Nullable;

/** The latest offer complexity tiers the server sent for the open menu. */
public final class OfferPreview {
    private static int containerId = -1;
    private static byte[] tiers;

    static void set(OfferDifficultyS2C msg) {
        containerId = msg.containerId();
        tiers = msg.tiers().clone();
    }

    @Nullable
    public static byte[] get(int menuContainerId) {
        return menuContainerId == containerId ? tiers : null;
    }

    static void clear() {
        containerId = -1;
        tiers = null;
    }

    private OfferPreview() {}
}
