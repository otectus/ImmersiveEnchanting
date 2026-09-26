package com.otectus.immersiveenchanting.ritual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds input logs that play a pattern: perfectly, with timing error, or skipping events. Used by the debug command,
 * the automated tests and the client smoke test; never by normal play.
 */
public final class Autoplay {
    private static final int TAP_RELEASE_MS = 40;

    /** Every event hit exactly on time. */
    public static List<InputRecord> perfect(RitualPattern pattern) {
        return play(pattern, 0.0, 0, 0L);
    }

    /**
     * @param missChance   probability of skipping each event entirely
     * @param maxErrorMs   uniform timing error in [-maxErrorMs, maxErrorMs] applied to each press
     */
    public static List<InputRecord> play(RitualPattern pattern, double missChance, int maxErrorMs, long seed) {
        RitualRandom rng = new RitualRandom(seed);
        List<InputRecord> out = new ArrayList<>();
        for (RitualEvent e : pattern.events()) {
            if (missChance > 0 && rng.chance(missChance)) continue;
            int error = maxErrorMs > 0 ? rng.range(-maxErrorMs, maxErrorMs) : 0;
            int press = Math.max(0, e.timeMs() + error);
            switch (e.type()) {
                case TAP -> {
                    out.add(new InputRecord(press, e.laneA(), true));
                    out.add(new InputRecord(press + TAP_RELEASE_MS, e.laneA(), false));
                }
                case HOLD -> {
                    out.add(new InputRecord(press, e.laneA(), true));
                    out.add(new InputRecord(Math.max(press + 1, e.endMs()), e.laneA(), false));
                }
                case CHORD -> {
                    out.add(new InputRecord(press, e.laneA(), true));
                    out.add(new InputRecord(press, e.laneB(), true));
                    out.add(new InputRecord(press + TAP_RELEASE_MS, e.laneA(), false));
                    out.add(new InputRecord(press + TAP_RELEASE_MS, e.laneB(), false));
                }
            }
        }
        out.sort(Comparator.comparingInt(InputRecord::timeMs).thenComparing(r -> r.pressed() ? 1 : 0));
        return out;
    }

    private Autoplay() {}
}
