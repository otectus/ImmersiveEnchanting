package com.otectus.immersiveenchanting.ritual;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic pattern generation: a pure function of {@link RitualParams}, a seed and {@link #VERSION}. The
 * server generates to know the timeline it will score against; the client generates the same timeline to play it.
 *
 * <p>Generation picks motifs by weight (respecting tier gates, lane needs, allowed mechanics and neighbour rules),
 * lays them end to end on a half-beat grid with short rests, and places each only where {@link PatternValidator}
 * accepts it, shifting it later by whole ticks if the seam with the previous motif would be unplayable. It stops at
 * the target event count or before the duration cap.
 */
public final class PatternGenerator {
    /** Bump whenever generation changes in any way that alters output for the same inputs. */
    public static final int VERSION = 1;

    /** Chance per tier that a motif uses half-beat spacing instead of full beats. */
    private static final double[] FAST_UNIT_CHANCE = {0.0, 0.1, 0.3, 0.55, 0.8, 0.95};
    private static final int MAX_STALLS = 48;
    private static final int MAX_SHIFT = 6;

    public static RitualPattern generate(RitualParams raw, long seed) {
        RitualParams p = raw.sanitized();
        RitualRandom rng = new RitualRandom(RitualRandom.mix(seed, VERSION));
        double tickMs = p.tickMs();
        int startTick = p.countInBeats() * 2;
        int countInMs = timeOf(startTick, tickMs);
        List<WeightedMotif> pool = pool(p);

        List<RitualEvent> events = new ArrayList<>();
        int cursor = startTick;
        Motif previous = null;
        int stalls = 0;
        boolean durationReached = false;
        while (events.size() < p.targetEvents() && stalls < MAX_STALLS && !durationReached) {
            Motif motif = pick(pool, previous, p.tier(), rng);
            int unit = rng.chance(FAST_UNIT_CHANCE[p.tier()]) ? 1 : 2;
            List<Motif.Proto> protos = motif.builder().build(rng, p.anchors(), p.tier(), unit);
            int remaining = p.targetEvents() - events.size();
            boolean placed = false;
            for (int shift = 0; shift <= MAX_SHIFT && !placed; shift++) {
                List<RitualEvent> candidates = place(protos, cursor + shift, tickMs, events.size(), remaining);
                if (candidates.isEmpty()) break;
                int end = 0;
                int lastTick = 0;
                for (int k = 0; k < candidates.size(); k++) {
                    end = Math.max(end, candidates.get(k).endMs());
                    Motif.Proto proto = protos.get(k);
                    lastTick = Math.max(lastTick, cursor + shift + proto.tick() + proto.lengthTicks());
                }
                if (end > p.maxDurationMs()) {
                    durationReached = true;
                    break;
                }
                if (PatternValidator.canAppend(events, candidates, p, countInMs, tickMs)) {
                    events.addAll(candidates);
                    cursor = lastTick + unit + rest(rng, p.tier());
                    previous = motif;
                    placed = true;
                }
            }
            if (!placed) stalls++;
        }
        if (events.isEmpty()) {
            // Only reachable with a duration cap shorter than the count-in plus one beat; one tap keeps the ritual valid.
            events.add(RitualEvent.tap(0, 0, countInMs));
        }
        int endMs = 0;
        for (RitualEvent e : events) endMs = Math.max(endMs, e.endMs());
        return new RitualPattern(VERSION, events, countInMs, endMs, tickMs, p.anchors());
    }

    /** Protos as concrete events starting at {@code baseTick}, truncated to {@code limit}. */
    private static List<RitualEvent> place(List<Motif.Proto> protos, int baseTick, double tickMs, int firstIndex, int limit) {
        List<RitualEvent> out = new ArrayList<>(Math.min(protos.size(), limit));
        for (Motif.Proto proto : protos) {
            if (out.size() >= limit) break;
            int index = firstIndex + out.size();
            int tick = baseTick + proto.tick();
            int time = timeOf(tick, tickMs);
            out.add(switch (proto.type()) {
                case TAP -> RitualEvent.tap(index, proto.laneA(), time);
                case HOLD -> RitualEvent.hold(index, proto.laneA(), time, timeOf(tick + proto.lengthTicks(), tickMs));
                case CHORD -> RitualEvent.chord(index, proto.laneA(), proto.laneB(), time);
            });
        }
        return out;
    }

    private static int timeOf(int tick, double tickMs) {
        return (int) Math.round(tick * tickMs);
    }

    private static int rest(RitualRandom rng, int tier) {
        return switch (tier) {
            case 0, 1 -> 2;
            case 2 -> rng.range(1, 2);
            case 3 -> 1;
            default -> rng.range(0, 1);
        };
    }

    private record WeightedMotif(Motif motif, double weight) {}

    private static List<WeightedMotif> pool(RitualParams p) {
        List<WeightedMotif> pool = new ArrayList<>();
        for (RitualParams.MotifWeight entry : p.motifs()) {
            Motif motif = MotifLibrary.get(entry.id());
            if (motif == null || !Double.isFinite(entry.weight()) || entry.weight() <= 0) continue;
            if (!motif.eligible(p.tier(), p.anchors(), p.allowHolds(), p.allowChords(), entry.minTier())) continue;
            double weight = entry.weight();
            if (motif.holds()) weight *= 1.0 + 2.0 * p.holdBias();
            if (motif.chords()) weight *= 1.0 + 2.0 * p.chordBias();
            if (weight > 0) pool.add(new WeightedMotif(motif, weight));
        }
        if (pool.isEmpty()) pool.add(new WeightedMotif(MotifLibrary.get(MotifLibrary.SCATTER), 1.0));
        return pool;
    }

    private static Motif pick(List<WeightedMotif> pool, Motif previous, int tier, RitualRandom rng) {
        double total = 0;
        for (WeightedMotif w : pool) if (w.motif().mayFollow(previous, tier)) total += w.weight();
        boolean filter = total > 0;
        if (!filter) for (WeightedMotif w : pool) total += w.weight();
        double roll = rng.nextDouble() * total;
        WeightedMotif chosen = null;
        for (WeightedMotif w : pool) {
            if (filter && !w.motif().mayFollow(previous, tier)) continue;
            chosen = w;
            roll -= w.weight();
            if (roll < 0) break;
        }
        return chosen.motif();
    }

    private PatternGenerator() {}
}
