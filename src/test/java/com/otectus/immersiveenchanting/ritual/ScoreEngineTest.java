package com.otectus.immersiveenchanting.ritual;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreEngineTest {

    private static RitualParams params() {
        // perfect 50, good 100, graze 150
        return new RitualParams(PatternGenerator.VERSION, 3, 55, 4, 10, 120, 50, 100, 150, true, true, 2, 1, 96, 25_000, 3,
                0, 0, PatternSetDef.defaultMotifs());
    }

    private static RitualPattern pattern(RitualEvent... events) {
        int end = 0;
        for (RitualEvent e : events) end = Math.max(end, e.endMs());
        return new RitualPattern(PatternGenerator.VERSION, List.of(events), 1000, end, 250, 4);
    }

    private static Judgement single(int pressOffset) {
        ScoreEngine engine = new ScoreEngine(pattern(RitualEvent.tap(0, 1, 2000)), params());
        engine.press(1, 2000 + pressOffset);
        engine.release(1, 2000 + pressOffset + 30);
        engine.finish();
        return engine.judgementOf(0);
    }

    @Test
    void exactHitsScoreOneHundred() {
        for (double score : new double[]{10, 45, 90}) {
            RitualParams p = PatternGeneratorTest.params(score);
            for (long seed = 0; seed < 300; seed++) {
                RitualPattern pattern = PatternGenerator.generate(p, seed);
                ScoreResult result = ScoreEngine.score(pattern, p, Autoplay.perfect(pattern));
                assertEquals(100.0, result.finalScore(), 1e-9, "seed " + seed);
                assertEquals(pattern.events().size(), result.perfect());
                assertEquals(0, result.strays());
            }
        }
    }

    @Test
    void boundariesClassify() {
        assertEquals(Judgement.PERFECT, single(0));
        assertEquals(Judgement.PERFECT, single(50));
        assertEquals(Judgement.PERFECT, single(-50));
        assertEquals(Judgement.GOOD, single(51));
        assertEquals(Judgement.GOOD, single(-100));
        assertEquals(Judgement.GRAZE, single(101));
        assertEquals(Judgement.GRAZE, single(150));
        assertEquals(Judgement.GRAZE, single(-150));
        assertEquals(Judgement.MISS, single(151));
        assertEquals(Judgement.MISS, single(-151));
    }

    @Test
    void duplicatePressesCannotDoubleScore() {
        RitualPattern p = pattern(RitualEvent.tap(0, 0, 2000), RitualEvent.tap(1, 1, 2500));
        ScoreEngine engine = new ScoreEngine(p, params());
        engine.press(0, 2000);
        engine.press(0, 2001); // key still down: ignored
        engine.release(0, 2040);
        engine.press(0, 2060); // tap already bound: stray
        engine.release(0, 2090);
        engine.press(1, 2500);
        engine.release(1, 2530);
        ScoreResult r = engine.finish();
        assertEquals(2, r.perfect());
        assertEquals(1, r.strays());
        assertEquals(0, r.miss());
    }

    @Test
    void closestEventWinsOnALane() {
        RitualPattern p = pattern(RitualEvent.tap(0, 2, 2000), RitualEvent.tap(1, 2, 2200));
        ScoreEngine engine = new ScoreEngine(p, params());
        engine.press(2, 2120); // 120 ms late for the first, 80 ms early for the second
        engine.release(2, 2150);
        engine.finish();
        assertEquals(Judgement.GOOD, engine.judgementOf(1));
        assertEquals(Judgement.MISS, engine.judgementOf(0));
    }

    @Test
    void chordsRequireBothLanes() {
        RitualPattern p = pattern(RitualEvent.chord(0, 0, 3, 2000));
        ScoreEngine both = new ScoreEngine(p, params());
        both.press(0, 2000);
        both.press(3, 2010);
        assertEquals(Judgement.PERFECT, both.judgementOf(0));

        ScoreEngine partial = new ScoreEngine(p, params());
        partial.press(0, 2000);
        partial.finish();
        assertEquals(Judgement.GRAZE, partial.judgementOf(0), "one lane of a chord is a graze at most");

        ScoreEngine spread = new ScoreEngine(p, params());
        spread.press(0, 1945); // each press is Good on its own, but they are 110 ms apart
        spread.press(3, 2055);
        assertEquals(Judgement.GRAZE, spread.judgementOf(0), "presses further apart than the tolerance");

        ScoreEngine none = new ScoreEngine(p, params());
        none.finish();
        assertEquals(Judgement.MISS, none.judgementOf(0));
    }

    @Test
    void holdsScoreStartSustainAndRelease() {
        RitualPattern p = pattern(RitualEvent.hold(0, 1, 2000, 3000));

        ScoreEngine held = new ScoreEngine(p, params());
        held.press(1, 2000);
        held.release(1, 3200);
        assertEquals(Judgement.PERFECT, held.judgementOf(0));

        ScoreEngine lateStart = new ScoreEngine(p, params());
        lateStart.press(1, 2080);
        lateStart.release(1, 3000);
        assertEquals(Judgement.GOOD, lateStart.judgementOf(0));

        ScoreEngine slightlyEarly = new ScoreEngine(p, params());
        slightlyEarly.press(1, 2000);
        slightlyEarly.release(1, 2880);
        assertEquals(Judgement.GRAZE, slightlyEarly.judgementOf(0), "released 120 ms early");

        ScoreEngine brokenLate = new ScoreEngine(p, params());
        brokenLate.press(1, 2000);
        brokenLate.release(1, 2600);
        ScoreResult r1 = brokenLate.finish();
        assertEquals(Judgement.GRAZE, brokenLate.judgementOf(0), "sustained 60 percent");
        assertEquals(1, r1.brokenHolds());

        ScoreEngine brokenEarly = new ScoreEngine(p, params());
        brokenEarly.press(1, 2000);
        brokenEarly.release(1, 2300);
        ScoreResult r2 = brokenEarly.finish();
        assertEquals(Judgement.MISS, brokenEarly.judgementOf(0));
        assertEquals(1, r2.miss(), "a dropped hold is one miss");

        ScoreEngine stillHeld = new ScoreEngine(p, params());
        stillHeld.press(1, 2000);
        stillHeld.finish();
        assertEquals(Judgement.PERFECT, stillHeld.judgementOf(0), "holding past the end completes the hold");
    }

    @Test
    void pressesDuringAHoldOnItsLaneAreStrays() {
        RitualPattern p = pattern(RitualEvent.hold(0, 1, 2000, 3000), RitualEvent.tap(1, 2, 2500));
        ScoreEngine engine = new ScoreEngine(p, params());
        engine.press(1, 2000);
        engine.press(2, 2500);
        engine.release(2, 2530);
        engine.release(1, 3000);
        ScoreResult r = engine.finish();
        assertEquals(2, r.perfect());
        assertEquals(0, r.strays());
    }

    @Test
    void noInputShatters() {
        RitualParams p = PatternGeneratorTest.params(50);
        RitualPattern pattern = PatternGenerator.generate(p, 3);
        ScoreResult r = ScoreEngine.score(pattern, p, List.of());
        assertEquals(0.0, r.finalScore(), 1e-9);
        assertEquals(OutcomeBand.SHATTERED, OutcomeRules.DEFAULT.band(r.finalScore()));
    }

    @Test
    void oneEarlyMissDoesNotRuinARun() {
        RitualParams p = PatternGeneratorTest.params(50);
        RitualPattern pattern = PatternGenerator.generate(p, 11);
        List<InputRecord> inputs = new ArrayList<>(Autoplay.perfect(pattern));
        RitualEvent first = pattern.events().get(0);
        inputs.removeIf(r -> r.timeMs() <= first.endMs() + 40 && first.usesLane(r.lane()));
        ScoreResult r = ScoreEngine.score(pattern, p, inputs);
        assertTrue(r.finalScore() >= 90, "one early miss keeps a perfect-band score, got " + r.finalScore());
    }

    @Test
    void allGoodIsAFullBindingAndAllGrazeIsWeak() {
        RitualParams p = PatternGeneratorTest.params(20);
        RitualPattern pattern = PatternGenerator.generate(p, 4);
        List<InputRecord> good = shifted(Autoplay.perfect(pattern), (p.perfectMs() + p.goodMs()) / 2);
        List<InputRecord> graze = shifted(Autoplay.perfect(pattern), (p.goodMs() + p.grazeMs()) / 2);
        assertEquals(OutcomeBand.STABLE, OutcomeRules.DEFAULT.band(ScoreEngine.score(pattern, p, good).finalScore()));
        assertEquals(OutcomeBand.WEAK, OutcomeRules.DEFAULT.band(ScoreEngine.score(pattern, p, graze).finalScore()));
    }

    private static List<InputRecord> shifted(List<InputRecord> inputs, int by) {
        List<InputRecord> out = new ArrayList<>();
        for (InputRecord r : inputs) out.add(new InputRecord(r.timeMs() + by, r.lane(), r.pressed()));
        return out;
    }

    /** The client advances every frame; the server replays the whole log at once. Results must match exactly. */
    @Test
    void incrementalAndBatchScoringAgree() {
        for (double score : new double[]{15, 40, 60, 75, 95}) {
            RitualParams p = PatternGeneratorTest.params(score);
            for (long seed = 0; seed < 400; seed++) {
                RitualPattern pattern = PatternGenerator.generate(p, seed);
                List<InputRecord> inputs = Autoplay.play(pattern, 0.2, 170, seed * 31);
                RitualRandom frames = new RitualRandom(seed);
                ScoreEngine client = new ScoreEngine(pattern, p);
                int now = 0;
                for (InputRecord input : inputs) {
                    while (now < input.timeMs()) {
                        client.advance(now);
                        now += 5 + frames.nextInt(40);
                    }
                    client.input(input);
                }
                ScoreResult incremental = client.finish();
                ScoreResult batch = ScoreEngine.score(pattern, p, inputs);
                assertEquals(batch, incremental, "score " + score + " seed " + seed);
            }
        }
    }

    @Test
    void mashingEveryLaneFails() {
        RitualParams p = PatternGeneratorTest.params(50);
        RitualPattern pattern = PatternGenerator.generate(p, 9);
        List<InputRecord> mash = new ArrayList<>();
        for (int t = 0; t < pattern.endMs() + 500; t += 60) {
            for (int lane = 0; lane < p.anchors(); lane++) {
                mash.add(new InputRecord(t, lane, true));
                mash.add(new InputRecord(t + 20, lane, false));
            }
        }
        mash.sort(java.util.Comparator.comparingInt(InputRecord::timeMs));
        ScoreResult r = ScoreEngine.score(pattern, p, mash);
        assertTrue(OutcomeRules.DEFAULT.band(r.finalScore()).isFailure() || OutcomeRules.DEFAULT.band(r.finalScore()) == OutcomeBand.WEAK,
                "mashing must not earn a full binding, got " + r.finalScore());
    }
}
