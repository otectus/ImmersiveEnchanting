package com.otectus.immersiveenchanting.ritual;

/**
 * One scorable rune. Times are milliseconds from the start of the ritual timeline (the start of the count-in).
 *
 * @param index  position in the pattern, also the event's identity for scoring
 * @param laneB  second lane of a chord, otherwise -1
 * @param endMs  release time of a hold; equal to {@code timeMs} for taps and chords
 */
public record RitualEvent(int index, EventType type, int laneA, int laneB, int timeMs, int endMs) {

    public static RitualEvent tap(int index, int lane, int timeMs) {
        return new RitualEvent(index, EventType.TAP, lane, -1, timeMs, timeMs);
    }

    public static RitualEvent hold(int index, int lane, int timeMs, int endMs) {
        return new RitualEvent(index, EventType.HOLD, lane, -1, timeMs, endMs);
    }

    public static RitualEvent chord(int index, int laneA, int laneB, int timeMs) {
        return new RitualEvent(index, EventType.CHORD, Math.min(laneA, laneB), Math.max(laneA, laneB), timeMs, timeMs);
    }

    public boolean usesLane(int lane) {
        return laneA == lane || laneB == lane;
    }

    public int laneMask() {
        return (1 << laneA) | (laneB >= 0 ? 1 << laneB : 0);
    }

    public int durationMs() {
        return endMs - timeMs;
    }

    RitualEvent withIndex(int newIndex) {
        return new RitualEvent(newIndex, type, laneA, laneB, timeMs, endMs);
    }
}
