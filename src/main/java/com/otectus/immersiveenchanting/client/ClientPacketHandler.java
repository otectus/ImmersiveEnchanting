package com.otectus.immersiveenchanting.client;

import com.otectus.immersiveenchanting.network.OfferDifficultyS2C;
import com.otectus.immersiveenchanting.network.RitualAbortedS2C;
import com.otectus.immersiveenchanting.network.RitualResolvedS2C;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;

/** Client-side packet entry points, reached only through {@code DistExecutor}. Main thread. */
public final class ClientPacketHandler {
    public static void onStarted(RitualStartedS2C msg) {
        ClientRitualController.onStarted(msg);
    }

    public static void onResolved(RitualResolvedS2C msg) {
        ClientRitualController.onResolved(msg);
    }

    public static void onAborted(RitualAbortedS2C msg) {
        ClientRitualController.onAborted(msg);
    }

    public static void onOfferDifficulty(OfferDifficultyS2C msg) {
        OfferPreview.set(msg);
    }

    private ClientPacketHandler() {}
}
