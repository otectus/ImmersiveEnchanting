package com.otectus.immersiveenchanting.ritual;

import java.util.Locale;

/** How cleanly a ritual bound its magic, from the final score. */
public enum OutcomeBand {
    /** Full result plus prestige effects; never more than the original roll. */
    PERFECT,
    /** Full result. */
    STABLE,
    /** Degraded to the frayed retention budget. */
    FRAYED,
    /** Degraded to the weak retention budget. */
    WEAK,
    /** Nothing bound. */
    FAILED,
    /** Nothing bound; differs from FAILED only in presentation. */
    SHATTERED;

    public boolean grantsFull() {
        return this == PERFECT || this == STABLE;
    }

    public boolean isFailure() {
        return this == FAILED || this == SHATTERED;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OutcomeBand byId(String id) {
        for (OutcomeBand band : values()) if (band.id().equals(id)) return band;
        return null;
    }
}
