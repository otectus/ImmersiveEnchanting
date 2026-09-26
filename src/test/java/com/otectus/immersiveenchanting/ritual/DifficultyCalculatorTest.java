package com.otectus.immersiveenchanting.ritual;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyCalculatorTest {

    private static DifficultyCalculator.Entry entry(RitualRandom r) {
        int min = r.chance(0.8) ? 1 : r.range(0, 3);
        int max = r.chance(0.2) ? min : min + r.range(1, 6);
        int rolled = r.range(min, max);
        return DifficultyCalculator.Entry.generic("test:e" + r.nextInt(1000), r.nextInt(4), rolled, min, max,
                r.chance(0.1), r.chance(0.15), 1 + r.nextInt(100));
    }

    private static List<DifficultyCalculator.Entry> bundle(RitualRandom r) {
        List<DifficultyCalculator.Entry> out = new ArrayList<>();
        int n = 1 + r.nextInt(5);
        for (int i = 0; i < n; i++) out.add(entry(r));
        return out;
    }

    private static double score(List<DifficultyCalculator.Entry> entries, int cost, int offer) {
        return DifficultyCalculator.compute(DifficultyCalculator.Input.of(entries, cost, offer)).score();
    }

    private static DifficultyCalculator.Entry with(DifficultyCalculator.Entry e, int rarity, int rolled) {
        return new DifficultyCalculator.Entry(e.id(), rarity, rolled, e.minLevel(), e.maxLevel(), e.curse(), e.treasure(),
                e.profileMultiplier(), e.profileOffset(), e.profileMinTier(), e.profileMaxTier(), e.weight());
    }

    @Test
    void addingAnEnchantmentNeverMakesItEasier() {
        RitualRandom r = new RitualRandom(1);
        for (int i = 0; i < 20_000; i++) {
            List<DifficultyCalculator.Entry> b = bundle(r);
            int cost = r.range(1, 60);
            double before = score(b, cost, 1);
            List<DifficultyCalculator.Entry> more = new ArrayList<>(b);
            more.add(entry(r));
            assertTrue(score(more, cost, 1) >= before - 1e-9, "added " + more.get(more.size() - 1) + " to " + b);
        }
    }

    @Test
    void raisingLevelRarityOrPowerNeverMakesItEasier() {
        RitualRandom r = new RitualRandom(2);
        for (int i = 0; i < 20_000; i++) {
            List<DifficultyCalculator.Entry> b = bundle(r);
            int cost = r.range(1, 60);
            int offer = r.nextInt(3);
            double before = score(b, cost, offer);
            int k = r.nextInt(b.size());
            DifficultyCalculator.Entry e = b.get(k);

            List<DifficultyCalculator.Entry> higherLevel = new ArrayList<>(b);
            higherLevel.set(k, with(e, e.rarity(), e.rolledLevel() + 1));
            assertTrue(score(higherLevel, cost, offer) >= before - 1e-9, "level");

            if (e.rarity() < 3) {
                List<DifficultyCalculator.Entry> rarer = new ArrayList<>(b);
                rarer.set(k, with(e, e.rarity() + 1, e.rolledLevel()));
                assertTrue(score(rarer, cost, offer) >= before - 1e-9, "rarity");
            }
            assertTrue(score(b, cost + r.range(1, 20), offer) >= before - 1e-9, "power");
            if (offer < 2) assertTrue(score(b, cost, offer + 1) >= before - 1e-9, "offer index");
        }
    }

    @Test
    void scoresStayInRange() {
        RitualRandom r = new RitualRandom(3);
        for (int i = 0; i < 5000; i++) {
            double s = score(bundle(r), r.range(0, 500), r.nextInt(3));
            assertTrue(s >= 0 && s <= 100, "score " + s);
        }
        assertEquals(0.0, score(List.of(), 0, 0), 1e-9);
    }

    @Test
    void vanillaShapedRollsSpreadAcrossTiers() {
        // Unbreaking I at cost 5 (easy) vs Sharpness V + Looting III + Unbreaking III at cost 30 (hard).
        double easy = score(List.of(DifficultyCalculator.Entry.generic("minecraft:unbreaking", 1, 1, 1, 3, false, false, 10)), 5, 0);
        double hard = score(List.of(
                DifficultyCalculator.Entry.generic("minecraft:sharpness", 0, 5, 1, 5, false, false, 50),
                DifficultyCalculator.Entry.generic("minecraft:looting", 2, 3, 1, 3, false, false, 40),
                DifficultyCalculator.Entry.generic("minecraft:unbreaking", 1, 3, 1, 3, false, false, 30)), 30, 2);
        assertTrue(DifficultyTier.fromScore(easy).index() <= 1, "easy roll tier " + DifficultyTier.fromScore(easy));
        assertTrue(DifficultyTier.fromScore(hard).index() >= 4, "hard roll tier " + DifficultyTier.fromScore(hard));
    }

    @Test
    void profileModifiersApply() {
        DifficultyCalculator.Entry base = DifficultyCalculator.Entry.generic("x:storm", 2, 2, 1, 3, false, false, 20);
        double plain = score(List.of(base), 20, 0);
        DifficultyCalculator.Entry boosted = new DifficultyCalculator.Entry("x:storm", 2, 2, 1, 3, false, false, 1.2, 5.0, 0, 5, 20);
        assertEquals(plain * 1.2 + 5.0, score(List.of(boosted), 20, 0), 1e-9);

        DifficultyCalculator.Entry pinned = new DifficultyCalculator.Entry("x:storm", 2, 2, 1, 3, false, false, 1, 0, 3, 3, 20);
        double s = score(List.of(pinned), 1, 0);
        assertEquals(DifficultyTier.EXPERT, DifficultyTier.fromScore(s), "min/max tier pin the score into tier 3");

        DifficultyCalculator.Entry capped = new DifficultyCalculator.Entry("x:tiny", 3, 5, 1, 5, true, true, 1, 0, 0, 1, 20);
        assertTrue(DifficultyTier.fromScore(score(List.of(capped), 60, 2)).index() <= 1, "max tier caps");
    }

    @Test
    void unusualLevelRanges() {
        assertEquals(0.15, DifficultyCalculator.normalizedLevel(1, 1, 1), 1e-9);
        assertEquals(1.0, DifficultyCalculator.normalizedLevel(9, 1, 1), 1e-9);
        assertEquals(0.0, DifficultyCalculator.normalizedLevel(3, 3, 7), 1e-9);
        assertEquals(1.0, DifficultyCalculator.normalizedLevel(7, 3, 7), 1e-9);
        assertEquals(1.0, DifficultyCalculator.normalizedLevel(12, 3, 7), 1e-9, "levels above max clamp");
        assertEquals(0.0, DifficultyCalculator.normalizedLevel(0, 1, 5), 1e-9, "levels below min clamp");
    }

    @Test
    void tiersPartitionTheScoreRange() {
        assertEquals(DifficultyTier.INITIATE, DifficultyTier.fromScore(0));
        assertEquals(DifficultyTier.INITIATE, DifficultyTier.fromScore(19.99));
        assertEquals(DifficultyTier.APPRENTICE, DifficultyTier.fromScore(20));
        assertEquals(DifficultyTier.EXPERT, DifficultyTier.fromScore(64.9));
        assertEquals(DifficultyTier.MASTER, DifficultyTier.fromScore(65));
        assertEquals(DifficultyTier.ARCANE, DifficultyTier.fromScore(100));
        for (DifficultyTier t : DifficultyTier.values()) assertEquals(t, DifficultyTier.fromScore(t.maxScoreInTier()));
    }
}
