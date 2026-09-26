package com.otectus.immersiveenchanting.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternGeneratorTest {
    private static final double[] TIER_SCORES = {10, 27, 42, 57, 72, 90};

    static RitualParams params(double score) {
        return ParamsResolver.resolve(PatternSetDef.DEFAULT, score, ParamsResolver.Tuning.DEFAULT, ParamsResolver.Shaping.NONE);
    }

    @Test
    void sameSeedSameTimeline() {
        for (double score : TIER_SCORES) {
            RitualParams p = params(score);
            for (long seed = 0; seed < 200; seed++) {
                assertEquals(PatternGenerator.generate(p, seed), PatternGenerator.generate(p, seed), "seed " + seed);
            }
        }
    }

    @Test
    void differentSeedsDiffer() {
        RitualParams p = params(60);
        int identical = 0;
        for (long seed = 0; seed < 200; seed++) {
            if (PatternGenerator.generate(p, seed).events().equals(PatternGenerator.generate(p, seed + 1000).events())) identical++;
        }
        assertTrue(identical < 2, "seeds should change the timeline, identical pairs: " + identical);
    }

    @Test
    void everyTierGeneratesValidPatternsAcrossManySeeds() {
        for (double score : TIER_SCORES) {
            RitualParams p = params(score);
            int tier = p.tier();
            for (long seed = 0; seed < 3000; seed++) {
                RitualPattern pattern = PatternGenerator.generate(p, seed * 7919L + tier);
                List<String> problems = PatternValidator.validate(pattern, p);
                assertTrue(problems.isEmpty(), "tier " + tier + " seed " + seed + ": " + problems);
                assertTrue(pattern.events().size() >= p.targetEvents() * 3 / 4,
                        "tier " + tier + " seed " + seed + " produced " + pattern.events().size() + " of " + p.targetEvents());
                assertTrue(pattern.events().size() <= p.maxEvents());
                assertTrue(pattern.endMs() <= p.maxDurationMs());
                assertTrue(pattern.events().get(0).timeMs() >= pattern.countInMs());
                assertTrue(pattern.countInMs() >= 2 * p.beatMs() - 1, "count-in lasts at least two beats");
                for (RitualEvent e : pattern.events()) {
                    assertTrue(e.laneA() < p.anchors() && e.laneB() < p.anchors());
                    if (e.type() == EventType.HOLD) assertTrue(tier >= 2, "holds start at tier 2");
                    if (e.type() == EventType.CHORD) assertTrue(tier >= 3, "chords start at tier 3");
                }
            }
        }
    }

    @Test
    void extremeTuningStaysValid() {
        ParamsResolver.Tuning[] tunings = {
                new ParamsResolver.Tuning(1.5, 1.5, 1.5, 1.25, 96, 25),
                new ParamsResolver.Tuning(0.5, 0.5, 0.5, 1.0, 96, 25),
                new ParamsResolver.Tuning(2.0, 3.0, 0.5, 1.0, 128, 60),
                new ParamsResolver.Tuning(1.0, 3.0, 1.0, 1.0, 96, 8)};
        for (ParamsResolver.Tuning tuning : tunings) {
            for (double score : TIER_SCORES) {
                RitualParams p = ParamsResolver.resolve(PatternSetDef.DEFAULT, score, tuning, ParamsResolver.Shaping.NONE);
                for (long seed = 0; seed < 400; seed++) {
                    RitualPattern pattern = PatternGenerator.generate(p, seed);
                    List<String> problems = PatternValidator.validate(pattern, p);
                    assertTrue(problems.isEmpty(), tuning + " score " + score + " seed " + seed + ": " + problems);
                    assertFalse(pattern.events().isEmpty());
                    assertTrue(pattern.endMs() <= p.maxDurationMs());
                }
            }
        }
    }

    @Test
    void biasesShiftMechanics() {
        RitualParams plain = params(90);
        RitualParams holdy = ParamsResolver.resolve(PatternSetDef.DEFAULT, 90, ParamsResolver.Tuning.DEFAULT,
                new ParamsResolver.Shaping(1.0, -1.0, java.util.Map.of()));
        int plainHolds = 0, holdyHolds = 0, holdyChords = 0;
        for (long seed = 0; seed < 300; seed++) {
            plainHolds += PatternGenerator.generate(plain, seed).holdCount();
            RitualPattern h = PatternGenerator.generate(holdy, seed);
            holdyHolds += h.holdCount();
            holdyChords += h.chordCount();
        }
        assertTrue(holdyHolds > plainHolds, "hold bias adds holds: " + holdyHolds + " vs " + plainHolds);
        assertEquals(0, holdyChords, "chord bias -1 removes chord motifs");
    }

    @Test
    void higherTiersAreHarderOnAverage() {
        double previousDensity = 0;
        double previousCount = 0;
        for (double score : TIER_SCORES) {
            RitualParams p = params(score);
            double count = 0, density = 0;
            for (long seed = 0; seed < 300; seed++) {
                RitualPattern pattern = PatternGenerator.generate(p, seed);
                count += pattern.events().size();
                density += pattern.events().size() / ((pattern.endMs() - pattern.countInMs()) / 1000.0 + 0.5);
            }
            count /= 300;
            density /= 300;
            assertTrue(count > previousCount, "tier " + p.tier() + " mean events " + count);
            assertTrue(density > previousDensity, "tier " + p.tier() + " mean events/second " + density);
            previousCount = count;
            previousDensity = density;
        }
    }

    @Test
    void unknownMotifsAndEmptySetsFallBack() {
        RitualParams base = params(30);
        RitualParams odd = new RitualParams(base.generatorVersion(), base.tier(), base.complexity(), base.anchors(),
                base.targetEvents(), base.bpm(), base.perfectMs(), base.goodMs(), base.grazeMs(), false, false, 2, 1,
                base.maxEvents(), base.maxDurationMs(), base.countInBeats(), 0, 0,
                List.of(new RitualParams.MotifWeight("examplemod:unknown", 5.0, -1)));
        RitualPattern pattern = PatternGenerator.generate(odd, 5);
        assertTrue(PatternValidator.validate(pattern, odd).isEmpty());
        assertFalse(pattern.events().isEmpty());
    }

    @Test
    void seedsMix() {
        assertNotEquals(RitualRandom.mix(1, 2), RitualRandom.mix(2, 1));
        assertNotEquals(RitualRandom.mix(0, "a"), RitualRandom.mix(0, "b"));
        RitualRandom r = new RitualRandom(42);
        for (int i = 0; i < 10_000; i++) {
            int v = r.nextInt(7);
            assertTrue(v >= 0 && v < 7);
            double d = r.nextDouble();
            assertTrue(d >= 0 && d < 1);
        }
    }
}
