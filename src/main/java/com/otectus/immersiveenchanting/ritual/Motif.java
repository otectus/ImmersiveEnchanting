package com.otectus.immersiveenchanting.ritual;

import java.util.List;

/**
 * A validated rhythmic building block. Patterns are concatenations of motifs rather than independently random notes,
 * so they read as intentional phrases and stay physically playable.
 *
 * <p>Builders emit {@link Proto} events on a grid of ticks (half beats) relative to the motif's first event, and
 * receive a {@code unit}: the spacing, in ticks, between consecutive notes (1 = half beat, 2 = full beat). The
 * generator picks the unit from the tier, so one motif definition serves slow and fast rituals alike.
 *
 * @param id            namespaced id, referenced by pattern sets and profiles
 * @param minTier       lowest difficulty tier the motif may appear at (a pattern set may override it)
 * @param lanes         anchors the motif needs
 * @param lengthBeats   nominal length at unit 2, for documentation and the debug command
 * @param density       nominal events per beat at unit 2
 * @param family        motifs of one family (other than {@link Family#FLOW}) may not follow each other directly
 *                      below {@code repeatTier}
 * @param repeatTier    tier from which the motif may follow another motif of its family
 */
public record Motif(String id, int minTier, int lanes, boolean holds, boolean chords, double lengthBeats, double density,
                    Family family, int repeatTier, Builder builder) {

    public enum Family { FLOW, PULSE, HOLD, CHORD }

    /** One event of a motif, positioned in ticks relative to the motif start. */
    public record Proto(EventType type, int laneA, int laneB, int tick, int lengthTicks) {
        static Proto tap(int lane, int tick) {
            return new Proto(EventType.TAP, lane, -1, tick, 0);
        }

        static Proto hold(int lane, int tick, int lengthTicks) {
            return new Proto(EventType.HOLD, lane, -1, tick, lengthTicks);
        }

        static Proto chord(int laneA, int laneB, int tick) {
            return new Proto(EventType.CHORD, laneA, laneB, tick, 0);
        }
    }

    @FunctionalInterface
    public interface Builder {
        List<Proto> build(RitualRandom rng, int anchors, int tier, int unit);
    }

    public boolean mayFollow(Motif previous, int tier) {
        if (previous == null || family == Family.FLOW || previous.family != family) return true;
        return tier >= repeatTier;
    }

    public boolean eligible(int tier, int anchors, boolean allowHolds, boolean allowChords, int minTierOverride) {
        int gate = minTierOverride >= 0 ? minTierOverride : minTier;
        return tier >= gate && anchors >= lanes && (!holds || allowHolds) && (!chords || allowChords);
    }
}
