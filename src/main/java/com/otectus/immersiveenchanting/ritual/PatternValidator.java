package com.otectus.immersiveenchanting.ritual;

import java.util.ArrayList;
import java.util.List;

/**
 * The playability rules every pattern must satisfy. The generator only places a motif where these hold, and the
 * test suite checks whole patterns against them across thousands of seeds.
 *
 * <ul>
 *   <li>Nothing is scorable before the count-in ends.</li>
 *   <li>Event starts are strictly increasing and at least one tick apart; a chord is one event.</li>
 *   <li>Two presses on one lane are at least {@link RitualParams#minSameLaneGapMs()} apart, measured from a hold's
 *       release, so no press lands on a lane a hold occupies.</li>
 *   <li>Holds last at least {@value #MIN_HOLD_MS} ms and never overlap another hold.</li>
 *   <li>Chords use two distinct lanes and never start while a hold is active or within a tick of its release.</li>
 *   <li>Event count and total duration stay within their caps.</li>
 * </ul>
 */
public final class PatternValidator {
    public static final int MIN_HOLD_MS = 250;

    /** All violations in a finished pattern; empty when valid. */
    public static List<String> validate(RitualPattern pattern, RitualParams params) {
        RitualParams p = params.sanitized();
        List<String> problems = new ArrayList<>();
        List<RitualEvent> events = pattern.events();
        if (events.isEmpty()) problems.add("pattern has no events");
        if (events.size() > p.maxEvents()) problems.add("pattern has " + events.size() + " events, cap " + p.maxEvents());
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).index() != i) problems.add("event " + i + " carries index " + events.get(i).index());
            String problem = check(events, i, p, pattern.countInMs(), pattern.tickMs());
            if (problem != null) problems.add(problem);
        }
        return problems;
    }

    /** True if {@code candidates}, appended after {@code existing}, keep the whole timeline valid. */
    static boolean canAppend(List<RitualEvent> existing, List<RitualEvent> candidates, RitualParams p, int countInMs,
                             double tickMs) {
        if (existing.size() + candidates.size() > p.maxEvents()) return false;
        List<RitualEvent> all = new ArrayList<>(existing.size() + candidates.size());
        all.addAll(existing);
        all.addAll(candidates);
        for (int i = existing.size(); i < all.size(); i++) {
            if (check(all, i, p, countInMs, tickMs) != null) return false;
        }
        return true;
    }

    /** Checks event {@code i} against everything before it. */
    private static String check(List<RitualEvent> events, int i, RitualParams p, int countInMs, double tickMs) {
        RitualEvent e = events.get(i);
        int minStartGap = (int) Math.floor(tickMs);
        if (e.laneA() < 0 || e.laneA() >= p.anchors()) return "event " + i + " uses lane " + e.laneA();
        if (e.timeMs() < countInMs) return "event " + i + " at " + e.timeMs() + " ms precedes the count-in (" + countInMs + " ms)";
        if (e.endMs() > p.maxDurationMs()) return "event " + i + " ends at " + e.endMs() + " ms, cap " + p.maxDurationMs();
        if (e.endMs() < e.timeMs()) return "event " + i + " ends before it starts";
        switch (e.type()) {
            case TAP -> {
                if (e.laneB() != -1 || e.endMs() != e.timeMs()) return "tap " + i + " is malformed";
            }
            case HOLD -> {
                if (e.laneB() != -1) return "hold " + i + " has a second lane";
                if (e.durationMs() < MIN_HOLD_MS) return "hold " + i + " lasts " + e.durationMs() + " ms";
            }
            case CHORD -> {
                if (e.laneB() < 0 || e.laneB() >= p.anchors() || e.laneB() == e.laneA()) return "chord " + i + " lanes " + e.laneA() + "/" + e.laneB();
                if (e.endMs() != e.timeMs()) return "chord " + i + " has a duration";
            }
        }
        if (i > 0) {
            RitualEvent prev = events.get(i - 1);
            if (e.timeMs() <= prev.timeMs()) return "event " + i + " does not start after event " + (i - 1);
            if (e.timeMs() - prev.timeMs() < minStartGap) return "events " + (i - 1) + " and " + i + " are " + (e.timeMs() - prev.timeMs()) + " ms apart";
        }
        for (int lane = 0; lane < p.anchors(); lane++) {
            if (!e.usesLane(lane)) continue;
            for (int j = i - 1; j >= 0; j--) {
                RitualEvent other = events.get(j);
                if (!other.usesLane(lane)) continue;
                int freeFrom = other.type() == EventType.HOLD ? other.endMs() : other.timeMs();
                if (e.timeMs() - freeFrom < p.minSameLaneGapMs()) {
                    return "lane " + lane + " pressed at " + e.timeMs() + " ms, only " + (e.timeMs() - freeFrom) + " ms after event " + j;
                }
                break;
            }
        }
        for (int j = i - 1; j >= 0; j--) {
            RitualEvent other = events.get(j);
            if (other.type() != EventType.HOLD) continue;
            if (e.type() == EventType.HOLD && e.timeMs() < other.endMs() + minStartGap) {
                return "hold " + i + " overlaps hold " + j;
            }
            if (e.type() == EventType.CHORD && e.timeMs() >= other.timeMs() && e.timeMs() < other.endMs() + minStartGap) {
                return "chord " + i + " starts during hold " + j;
            }
        }
        return null;
    }

    private PatternValidator() {}
}
