package com.otectus.immersiveenchanting.ritual;

import java.util.ArrayList;
import java.util.List;

/**
 * A pattern set: how rituals are shaped at each tier. Two forms exist:
 * <ul>
 *   <li><b>tiered</b> - six {@link TierDef}s, one per tier; values interpolate across the score range of that tier;</li>
 *   <li><b>flat</b> - one {@link TierDef} used at every tier; event count and tempo interpolate across the whole
 *       0..100 score, and anchors default to three below tier 2 and four from it.</li>
 * </ul>
 *
 * @param motifs default motif weights; a tier may replace them with its own list
 */
public record PatternSetDef(String id, boolean flat, List<TierDef> tiers, List<RitualParams.MotifWeight> motifs,
                            Limits limits, int countInBeats) {

    public PatternSetDef {
        tiers = List.copyOf(tiers);
        motifs = List.copyOf(motifs);
    }

    /**
     * @param anchors       3 or 4, or 0 to use the default for the tier (flat sets only)
     * @param allowTaps     informational; taps are always allowed because every motif is built from them
     * @param motifs        replaces the set's motif list at this tier when non-null
     */
    public record TierDef(int anchors, int minEvents, int maxEvents, double minBpm, double maxBpm, int perfectMs, int goodMs,
                          int grazeMs, boolean allowTaps, boolean allowHolds, boolean allowChords,
                          List<RitualParams.MotifWeight> motifs) {
    }

    public record Limits(int maxChordSize, int maxSimultaneousHolds, double maxDurationSeconds) {
        public static final Limits DEFAULT = new Limits(2, 1, 25.0);
    }

    public TierDef tier(int index) {
        if (flat || tiers.size() == 1) return tiers.get(0);
        return tiers.get(Math.max(0, Math.min(tiers.size() - 1, index)));
    }

    public static final String DEFAULT_ID = MotifLibrary.NAMESPACE + ":default";

    /** The built-in set (also shipped as a datapack file); the fallback whenever data is missing or broken. */
    public static final PatternSetDef DEFAULT = defaultSet();

    private static PatternSetDef defaultSet() {
        List<TierDef> tiers = new ArrayList<>();
        tiers.add(new TierDef(3, 12, 16, 80, 90, 95, 145, 200, true, false, false, null));
        tiers.add(new TierDef(3, 16, 22, 90, 100, 85, 135, 185, true, false, false, null));
        tiers.add(new TierDef(4, 22, 30, 100, 115, 75, 120, 170, true, true, false, null));
        tiers.add(new TierDef(4, 28, 38, 115, 130, 70, 110, 155, true, true, true, null));
        tiers.add(new TierDef(4, 36, 48, 130, 145, 60, 100, 145, true, true, true, null));
        tiers.add(new TierDef(4, 44, 60, 145, 160, 55, 90, 130, true, true, true, null));
        return new PatternSetDef(DEFAULT_ID, false, tiers, defaultMotifs(), Limits.DEFAULT, 3);
    }

    public static List<RitualParams.MotifWeight> defaultMotifs() {
        String ns = MotifLibrary.NAMESPACE + ":";
        return List.of(
                new RitualParams.MotifWeight(ns + "scatter", 0.6, -1),
                new RitualParams.MotifWeight(ns + "alternating_pair", 1.0, -1),
                new RitualParams.MotifWeight(ns + "ascending_cascade", 1.0, -1),
                new RitualParams.MotifWeight(ns + "descending_cascade", 1.0, -1),
                new RitualParams.MotifWeight(ns + "pulse", 0.7, -1),
                new RitualParams.MotifWeight(ns + "echo", 1.0, -1),
                new RitualParams.MotifWeight(ns + "palindrome", 0.8, -1),
                new RitualParams.MotifWeight(ns + "mirrored_cascade", 0.9, -1),
                new RitualParams.MotifWeight(ns + "left_right_split", 0.8, -1),
                new RitualParams.MotifWeight(ns + "center_cross", 0.8, -1),
                new RitualParams.MotifWeight(ns + "simple_hold", 1.0, -1),
                new RitualParams.MotifWeight(ns + "hold_anchor", 1.0, -1),
                new RitualParams.MotifWeight(ns + "chord_pulse", 0.9, -1),
                new RitualParams.MotifWeight(ns + "syncopated_echo", 0.8, -1),
                new RitualParams.MotifWeight(ns + "chord_cascade", 0.8, -1));
    }
}
