package com.otectus.immersiveenchanting.ritual;

import java.util.List;

/**
 * A generated event timeline. Both sides build it from the same {@link RitualParams} and seed.
 *
 * @param countInMs time of the first scorable event; nothing is scorable before it
 * @param endMs     time the last event finishes (the last hold's release, or the last press)
 * @param tickMs    length of one grid step (half a beat)
 */
public record RitualPattern(int generatorVersion, List<RitualEvent> events, int countInMs, int endMs, double tickMs,
                            int anchors) {

    public RitualPattern {
        events = List.copyOf(events);
    }

    public double beatMs() {
        return tickMs * 2.0;
    }

    public int holdCount() {
        int n = 0;
        for (RitualEvent e : events) if (e.type() == EventType.HOLD) n++;
        return n;
    }

    public int chordCount() {
        int n = 0;
        for (RitualEvent e : events) if (e.type() == EventType.CHORD) n++;
        return n;
    }
}
