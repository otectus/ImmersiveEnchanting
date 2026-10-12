package com.otectus.immersiveenchanting.client;

import com.otectus.immersiveenchanting.network.OfferDifficultyS2C;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** The latest offer complexity tiers, and the reasons locked offers were refused, the server sent for the open menu. */
public final class OfferPreview {
    private static int containerId = -1;
    private static byte[] tiers;
    private static Component[] denials;

    static void set(OfferDifficultyS2C msg) {
        containerId = msg.containerId();
        tiers = msg.tiers().clone();
        denials = msg.denials() == null ? new Component[tiers.length] : msg.denials().clone();
    }

    @Nullable
    public static byte[] get(int menuContainerId) {
        return menuContainerId == containerId ? tiers : null;
    }

    /** The reason offer {@code offer} of the open menu is locked, or null when it is open (or unknown). */
    @Nullable
    public static Component denial(int menuContainerId, int offer) {
        if (menuContainerId != containerId || denials == null || offer < 0 || offer >= denials.length) return null;
        return denials[offer];
    }

    /** Which offers of the open menu are locked; null when nothing is known. */
    @Nullable
    public static boolean[] locked(int menuContainerId) {
        if (menuContainerId != containerId || denials == null) return null;
        boolean[] locked = new boolean[denials.length];
        for (int i = 0; i < denials.length; i++) locked[i] = denials[i] != null;
        return locked;
    }

    static void clear() {
        containerId = -1;
        tiers = null;
        denials = null;
    }

    private OfferPreview() {}
}
