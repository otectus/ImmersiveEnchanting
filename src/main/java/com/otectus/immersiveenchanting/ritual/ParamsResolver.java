package com.otectus.immersiveenchanting.ritual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves concrete {@link RitualParams} from a pattern set, a complexity score and server tuning. Event count, tempo
 * and timing windows scale independently, as the server configuration requires.
 */
public final class ParamsResolver {

    /**
     * Server-side tuning applied on top of the pattern set.
     *
     * @param timingAssist accessibility widening the player asked for, already clamped by the server's ceiling
     */
    public record Tuning(double tempoMultiplier, double eventCountMultiplier, double timingWindowMultiplier,
                         double timingAssist, int maxEvents, double maxDurationSeconds) {
        public static final Tuning DEFAULT = new Tuning(1.0, 1.0, 1.0, 1.0, 96, 25.0);
    }

    /**
     * Profile-driven adjustments.
     *
     * @param motifOverrides multiplies the weight of motifs already in the set; adds motifs not in it with that weight
     */
    public record Shaping(double holdBias, double chordBias, Map<String, Double> motifOverrides) {
        public static final Shaping NONE = new Shaping(0.0, 0.0, Map.of());
    }

    public static RitualParams resolve(PatternSetDef set, double complexity, Tuning tuning, Shaping shaping) {
        DifficultyTier tier = DifficultyTier.fromScore(complexity);
        PatternSetDef.TierDef def = set.tier(tier.index());
        double t = set.flat() ? clamp(complexity / 100.0, 0, 1) : tier.position(complexity);

        int anchors = def.anchors() >= 3 ? def.anchors() : (tier.index() >= 2 ? 4 : 3);
        double events = lerp(def.minEvents(), def.maxEvents(), t) * positive(tuning.eventCountMultiplier());
        int maxEvents = Math.max(1, Math.min(RitualParams.HARD_MAX_EVENTS, tuning.maxEvents()));
        int target = (int) Math.max(4, Math.min(maxEvents, Math.round(events)));
        double bpm = lerp(def.minBpm(), def.maxBpm(), t) * positive(tuning.tempoMultiplier());
        double windows = positive(tuning.timingWindowMultiplier()) * Math.max(1.0, positive(tuning.timingAssist()));

        double limitSeconds = set.limits().maxDurationSeconds() > 0 ? set.limits().maxDurationSeconds() : 25.0;
        double durationSeconds = Math.min(limitSeconds, tuning.maxDurationSeconds() > 0 ? tuning.maxDurationSeconds() : 25.0);

        List<RitualParams.MotifWeight> motifs = mergeMotifs(def.motifs() != null ? def.motifs() : set.motifs(), shaping.motifOverrides());
        return new RitualParams(PatternGenerator.VERSION, tier.index(), complexity, anchors, target, bpm,
                (int) Math.round(def.perfectMs() * windows), (int) Math.round(def.goodMs() * windows),
                (int) Math.round(def.grazeMs() * windows), def.allowHolds(), def.allowChords(),
                set.limits().maxChordSize(), set.limits().maxSimultaneousHolds(), maxEvents,
                (int) Math.round(durationSeconds * 1000.0), set.countInBeats(), shaping.holdBias(), shaping.chordBias(),
                motifs).sanitized();
    }

    private static List<RitualParams.MotifWeight> mergeMotifs(List<RitualParams.MotifWeight> base, Map<String, Double> overrides) {
        if (overrides == null || overrides.isEmpty()) return base;
        Map<String, RitualParams.MotifWeight> merged = new LinkedHashMap<>();
        for (RitualParams.MotifWeight w : base) merged.put(w.id(), w);
        for (Map.Entry<String, Double> o : overrides.entrySet()) {
            double factor = o.getValue() == null || !Double.isFinite(o.getValue()) ? 1.0 : Math.max(0.0, o.getValue());
            RitualParams.MotifWeight existing = merged.get(o.getKey());
            merged.put(o.getKey(), existing == null
                    ? new RitualParams.MotifWeight(o.getKey(), factor, -1)
                    : new RitualParams.MotifWeight(existing.id(), existing.weight() * factor, existing.minTier()));
        }
        return new ArrayList<>(merged.values());
    }

    private static double positive(double v) {
        return Double.isFinite(v) && v > 0 ? v : 1.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private ParamsResolver() {}
}
