package com.otectus.immersiveenchanting.api;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Read-only view of a server-side ritual session. */
public interface RitualSessionView {
    UUID sessionId();

    UUID playerId();

    ResourceLocation adapterId();

    int containerId();

    int offerIndex();

    OfferSnapshot snapshot();

    /** Complexity score, 0..100. */
    double complexity();

    /** Difficulty tier, 0..5. */
    int tier();

    long patternSeed();

    ResourceLocation patternSetId();
}
