package com.otectus.immersiveenchanting.ritual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Judges binding inputs against a pattern. The same engine runs incrementally on the client (for instant feedback)
 * and in one pass on the server (authoritatively), so both reach identical results from identical inputs.
 *
 * <h2>Matching</h2>
 * A press on a lane binds the unjudged event on that lane whose time is closest to the press, among those within
 * the graze window; ties go to the earlier event. A press that binds nothing is a stray: it breaks the combo and
 * costs a little stability. A press on a lane that is already down is ignored, so a repeated key can never bind two
 * events.
 *
 * <h2>Holds</h2>
 * The start press is judged like a tap. Releasing within the graze window before the end, or any time after it,
 * completes the hold: its judgement is the worse of the start and release. An earlier release breaks the hold; it
 * scores Graze if at least half the hold was sustained, otherwise Miss, and always breaks the combo. A dropped hold
 * is one event, never a string of misses.
 *
 * <h2>Chords</h2>
 * Each lane of a chord is matched separately. With both pressed the chord takes the worse judgement, and at most
 * Graze if the presses were further apart than the chord tolerance. With one pressed it scores Graze; with none,
 * Miss.
 *
 * <h2>Ordering</h2>
 * Events without an input are finalized when their deadline passes (miss deadline: time + graze; hold completion:
 * release time). {@link #advance(int)} finalizes deadlines strictly before the given time in (deadline, index)
 * order, and every input first advances to its own time. Finalization order therefore depends only on the inputs,
 * not on how often the client advances between frames.
 */
public final class ScoreEngine {
    public enum Kind { HIT, MISS, STRAY, HOLD_START, HOLD_BROKEN, HOLD_COMPLETE, CHORD_PARTIAL }

    /** Something the client may want to show. {@code eventIndex} is -1 for strays. */
    public record Feedback(Kind kind, int eventIndex, int lane, Judgement judgement, int timeMs) {}

    private final RitualPattern pattern;
    private final List<RitualEvent> events;
    private final int perfectMs, goodMs, grazeMs, chordToleranceMs;
    private final Judgement[] judged;
    private final Judgement[] holdStart;
    private final int[] pressA;
    private final int[] pressB;
    private final int[] activeHold;
    private final boolean[] down;
    private int perfect, good, graze, miss, strays, brokenHolds, combo, longestCombo;
    private int lastInputMs = Integer.MIN_VALUE;
    private List<Feedback> sink;

    public ScoreEngine(RitualPattern pattern, RitualParams params) {
        RitualParams p = params.sanitized();
        this.pattern = pattern;
        this.events = pattern.events();
        this.perfectMs = p.perfectMs();
        this.goodMs = p.goodMs();
        this.grazeMs = p.grazeMs();
        this.chordToleranceMs = p.chordToleranceMs();
        int n = events.size();
        this.judged = new Judgement[n];
        this.holdStart = new Judgement[n];
        this.pressA = new int[n];
        this.pressB = new int[n];
        java.util.Arrays.fill(pressA, Integer.MIN_VALUE);
        java.util.Arrays.fill(pressB, Integer.MIN_VALUE);
        this.activeHold = new int[RitualParams.MAX_ANCHORS];
        java.util.Arrays.fill(activeHold, -1);
        this.down = new boolean[RitualParams.MAX_ANCHORS];
    }

    /** Replays a whole input log and returns the final score. Inputs must be in non-decreasing time order. */
    public static ScoreResult score(RitualPattern pattern, RitualParams params, List<InputRecord> inputs) {
        ScoreEngine engine = new ScoreEngine(pattern, params);
        for (InputRecord input : inputs) engine.input(input);
        return engine.finish();
    }

    public List<Feedback> input(InputRecord input) {
        return input.pressed() ? press(input.lane(), input.timeMs()) : release(input.lane(), input.timeMs());
    }

    public List<Feedback> press(int lane, int timeMs) {
        if (lane < 0 || lane >= pattern.anchors() || down[lane]) return List.of();
        List<Feedback> out = begin(timeMs);
        down[lane] = true;
        int best = -1;
        int bestDelta = Integer.MAX_VALUE;
        for (int i = 0; i < events.size(); i++) {
            if (judged[i] != null) continue;
            RitualEvent e = events.get(i);
            if (!e.usesLane(lane)) continue;
            if (e.type() == EventType.HOLD && holdStart[i] != null) continue;
            if (e.type() == EventType.CHORD && (lane == e.laneA() ? pressA[i] : pressB[i]) != Integer.MIN_VALUE) continue;
            int delta = Math.abs(timeMs - e.timeMs());
            if (delta > grazeMs) continue;
            if (delta < bestDelta) {
                best = i;
                bestDelta = delta;
            }
        }
        if (best < 0) {
            strays++;
            combo = 0;
            out.add(new Feedback(Kind.STRAY, -1, lane, Judgement.MISS, timeMs));
            return end(out);
        }
        RitualEvent e = events.get(best);
        Judgement j = judge(bestDelta);
        switch (e.type()) {
            case TAP -> finalizeEvent(best, j, false, lane, timeMs, Kind.HIT);
            case HOLD -> {
                holdStart[best] = j;
                pressA[best] = timeMs;
                activeHold[lane] = best;
                out.add(new Feedback(Kind.HOLD_START, best, lane, j, timeMs));
            }
            case CHORD -> {
                if (lane == e.laneA()) pressA[best] = timeMs;
                else pressB[best] = timeMs;
                if (pressA[best] != Integer.MIN_VALUE && pressB[best] != Integer.MIN_VALUE) {
                    Judgement chord = Judgement.worse(judge(Math.abs(pressA[best] - e.timeMs())), judge(Math.abs(pressB[best] - e.timeMs())));
                    if (Math.abs(pressA[best] - pressB[best]) > chordToleranceMs) chord = Judgement.worse(chord, Judgement.GRAZE);
                    finalizeEvent(best, chord, false, lane, timeMs, Kind.HIT);
                }
            }
        }
        return end(out);
    }

    public List<Feedback> release(int lane, int timeMs) {
        if (lane < 0 || lane >= pattern.anchors() || !down[lane]) return List.of();
        List<Feedback> out = begin(timeMs);
        down[lane] = false;
        int h = activeHold[lane];
        activeHold[lane] = -1;
        if (h >= 0 && judged[h] == null) {
            RitualEvent e = events.get(h);
            if (timeMs >= e.endMs() - grazeMs) {
                Judgement releaseJ = timeMs >= e.endMs() ? Judgement.PERFECT : judge(e.endMs() - timeMs);
                finalizeEvent(h, Judgement.worse(holdStart[h], releaseJ), false, lane, timeMs, Kind.HOLD_COMPLETE);
            } else {
                double sustained = (double) (timeMs - e.timeMs()) / Math.max(1, e.durationMs());
                brokenHolds++;
                finalizeEvent(h, sustained >= 0.5 ? Judgement.GRAZE : Judgement.MISS, true, lane, timeMs, Kind.HOLD_BROKEN);
            }
        }
        return end(out);
    }

    /** Finalizes every event whose deadline is strictly before {@code timeMs}. Safe to call every frame. */
    public List<Feedback> advance(int timeMs) {
        List<Feedback> out = begin(Math.max(timeMs, lastInputMs));
        return end(out);
    }

    /** Finalizes everything still pending and returns the score. */
    public ScoreResult finish() {
        begin(Integer.MAX_VALUE);
        sink = null;
        return result();
    }

    public ScoreResult result() {
        return ScoreResult.of(events.size(), perfect, good, graze, miss, strays, brokenHolds, longestCombo);
    }

    public Judgement judgementOf(int eventIndex) {
        return judged[eventIndex];
    }

    /** The judgement of a hold's start press while the hold is still being sustained, else null. */
    public Judgement holdStartOf(int eventIndex) {
        return holdStart[eventIndex];
    }

    public boolean isHolding(int eventIndex) {
        return holdStart[eventIndex] != null && judged[eventIndex] == null;
    }

    /** For a chord in progress: which lanes have been pressed (bit 0 = lane A, bit 1 = lane B). */
    public int chordPresses(int eventIndex) {
        return (pressA[eventIndex] != Integer.MIN_VALUE ? 1 : 0) | (pressB[eventIndex] != Integer.MIN_VALUE ? 2 : 0);
    }

    public boolean isDown(int lane) {
        return lane >= 0 && lane < down.length && down[lane];
    }

    public int combo() {
        return combo;
    }

    public int judgedCount() {
        return perfect + good + graze + miss;
    }

    /** Running accuracy over the events judged so far, 0..100 (100 before anything is judged). */
    public double runningAccuracy() {
        int n = judgedCount();
        if (n == 0) return 100.0;
        return (perfect * Judgement.PERFECT.value() + good * Judgement.GOOD.value() + graze * Judgement.GRAZE.value()) / n * 100.0;
    }

    // ---- internals ------------------------------------------------------------------------------------------------

    private List<Feedback> begin(int timeMs) {
        List<Feedback> out = new ArrayList<>(2);
        sink = out;
        if (timeMs != Integer.MAX_VALUE) lastInputMs = Math.max(lastInputMs, timeMs);
        finalizeDue(timeMs);
        return out;
    }

    private List<Feedback> end(List<Feedback> out) {
        sink = null;
        return out.isEmpty() ? List.of() : out;
    }

    private void finalizeDue(int beforeMs) {
        List<long[]> due = null;
        for (int i = 0; i < events.size(); i++) {
            if (judged[i] != null) continue;
            RitualEvent e = events.get(i);
            long deadline = e.type() == EventType.HOLD && holdStart[i] != null ? e.endMs() : (long) e.timeMs() + grazeMs;
            if (deadline < beforeMs) {
                if (due == null) due = new ArrayList<>();
                due.add(new long[]{deadline, i});
            }
        }
        if (due == null) return;
        if (due.size() > 1) Collections.sort(due, (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        for (long[] d : due) {
            int i = (int) d[1];
            RitualEvent e = events.get(i);
            int at = (int) d[0];
            if (e.type() == EventType.HOLD && holdStart[i] != null) {
                if (activeHold[e.laneA()] == i) activeHold[e.laneA()] = -1;
                finalizeEvent(i, holdStart[i], false, e.laneA(), at, Kind.HOLD_COMPLETE);
            } else if (e.type() == EventType.CHORD && (pressA[i] != Integer.MIN_VALUE || pressB[i] != Integer.MIN_VALUE)) {
                finalizeEvent(i, Judgement.GRAZE, false, pressA[i] != Integer.MIN_VALUE ? e.laneA() : e.laneB(), at, Kind.CHORD_PARTIAL);
            } else {
                finalizeEvent(i, Judgement.MISS, false, e.laneA(), at, Kind.MISS);
            }
        }
    }

    private void finalizeEvent(int index, Judgement j, boolean broken, int lane, int timeMs, Kind kind) {
        judged[index] = j;
        switch (j) {
            case PERFECT -> perfect++;
            case GOOD -> good++;
            case GRAZE -> graze++;
            case MISS -> miss++;
        }
        if (j == Judgement.MISS || broken) {
            combo = 0;
        } else {
            combo++;
            longestCombo = Math.max(longestCombo, combo);
        }
        if (sink != null) sink.add(new Feedback(kind, index, lane, j, timeMs));
    }

    private Judgement judge(int delta) {
        if (delta <= perfectMs) return Judgement.PERFECT;
        if (delta <= goodMs) return Judgement.GOOD;
        if (delta <= grazeMs) return Judgement.GRAZE;
        return Judgement.MISS;
    }
}
