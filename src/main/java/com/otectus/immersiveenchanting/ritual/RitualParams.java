package com.otectus.immersiveenchanting.ritual;

import java.util.List;

/**
 * Every value the pattern generator and score engine need, fully resolved on the server (pattern set, tier,
 * configuration multipliers, profile biases, timing assist) and sent to the client as-is. The generator is a pure
 * function of these parameters and a seed, so the client never needs the server's datapacks.
 *
 * @param complexity           0..100 complexity score the parameters were resolved from
 * @param anchors              number of binding anchors (lanes), 3 or 4
 * @param targetEvents         events the generator aims for; it may produce fewer if the duration cap is reached
 * @param maxChordSize         lanes per chord; always 2 in generator version 1
 * @param maxSimultaneousHolds holds that may overlap; always 1 in generator version 1
 * @param holdBias             -1..1, scales the weight of motifs containing holds
 * @param chordBias            -1..1, scales the weight of motifs containing chords
 */
public record RitualParams(int generatorVersion, int tier, double complexity, int anchors, int targetEvents, double bpm,
                           int perfectMs, int goodMs, int grazeMs, boolean allowHolds, boolean allowChords,
                           int maxChordSize, int maxSimultaneousHolds, int maxEvents, int maxDurationMs,
                           int countInBeats, double holdBias, double chordBias, List<MotifWeight> motifs) {

    public static final int MIN_ANCHORS = 3;
    public static final int MAX_ANCHORS = 4;
    public static final int HARD_MAX_EVENTS = 128;
    public static final int HARD_MAX_DURATION_MS = 60_000;

    public RitualParams {
        motifs = List.copyOf(motifs);
    }

    /** A motif id with its selection weight. {@code minTier} overrides the motif's own gate when not -1. */
    public record MotifWeight(String id, double weight, int minTier) {
    }

    public double beatMs() {
        return 60_000.0 / bpm;
    }

    public double tickMs() {
        return beatMs() / 2.0;
    }

    /** Tolerance between the two presses of a chord; beyond it the chord scores Graze at most. */
    public int chordToleranceMs() {
        return Math.max(goodMs, 60);
    }

    /** Closest two presses on one lane may be. Holds count from their release. */
    public int minSameLaneGapMs() {
        return (int) Math.max(Math.floor(tickMs()), 150);
    }

    /**
     * Returns a copy with every value forced into the range the generator and scorer support. The server resolves
     * parameters from datapacks and configuration, so it sanitizes before generating; the client sanitizes what it
     * receives with the same code, so both sides agree even on nonsense input.
     */
    public RitualParams sanitized() {
        int a = Math.max(MIN_ANCHORS, Math.min(MAX_ANCHORS, anchors));
        double b = Double.isFinite(bpm) ? Math.max(40.0, Math.min(240.0, bpm)) : 100.0;
        int perfect = Math.max(15, Math.min(250, perfectMs));
        int good = Math.max(perfect + 5, Math.min(350, goodMs));
        int graze = Math.max(good + 5, Math.min(450, grazeMs));
        int maxEv = Math.max(1, Math.min(HARD_MAX_EVENTS, maxEvents));
        int target = Math.max(1, Math.min(maxEv, targetEvents));
        int duration = Math.max(3_000, Math.min(HARD_MAX_DURATION_MS, maxDurationMs));
        int countIn = Math.max(2, Math.min(4, countInBeats));
        int t = Math.max(0, Math.min(5, tier));
        double c = Double.isFinite(complexity) ? Math.max(0.0, Math.min(100.0, complexity)) : 0.0;
        double hb = Double.isFinite(holdBias) ? Math.max(-1.0, Math.min(1.0, holdBias)) : 0.0;
        double cb = Double.isFinite(chordBias) ? Math.max(-1.0, Math.min(1.0, chordBias)) : 0.0;
        return new RitualParams(generatorVersion, t, c, a, target, b, perfect, good, graze, allowHolds, allowChords,
                2, 1, maxEv, duration, countIn, hb, cb, motifs);
    }
}
