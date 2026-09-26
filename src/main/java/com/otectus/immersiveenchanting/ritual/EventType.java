package com.otectus.immersiveenchanting.ritual;

import java.util.Locale;

/** The three scoring primitives. Echoes, cascades and the other motifs are compositions of these. */
public enum EventType {
    TAP,
    HOLD,
    CHORD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EventType byId(String id) {
        for (EventType type : values()) {
            if (type.id().equals(id)) return type;
        }
        return null;
    }
}
