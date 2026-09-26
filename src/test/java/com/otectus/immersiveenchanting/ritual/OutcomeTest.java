package com.otectus.immersiveenchanting.ritual;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutcomeTest {

    private static List<DowngradeResolver.PlanEntry> randomPlan(RitualRandom r) {
        int n = 1 + r.nextInt(5);
        List<DowngradeResolver.PlanEntry> plan = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int primary = r.nextInt(n);
        for (int i = 0; i < n; i++) {
            String id;
            do id = "mod" + r.nextInt(3) + ":e" + r.nextInt(40); while (!ids.add(id));
            int min = r.chance(0.85) ? 1 : r.range(2, 3);
            int max = min + r.range(0, 5);
            int level = r.range(min, max);
            double[] values = ArcaneValue.byLevel(r.nextInt(4), min, max, lvl -> 1 + (lvl - 1) * r.range(0, 12), 1.0);
            plan.add(new DowngradeResolver.PlanEntry(id, min, level, values, i == primary, r.chance(0.1)));
        }
        return plan;
    }

    @Test
    void degradedResultsOnlyShrinkThePlan() {
        RitualRandom r = new RitualRandom(7);
        double[] retentions = {0.0, 0.1, 0.3, 0.45, 0.6, 0.75, 0.9, 0.99, 1.0};
        for (int i = 0; i < 20_000; i++) {
            List<DowngradeResolver.PlanEntry> plan = randomPlan(r);
            for (double retention : retentions) {
                List<DowngradeResolver.Kept> kept = DowngradeResolver.resolve(plan, retention);
                Set<Integer> seen = new HashSet<>();
                for (DowngradeResolver.Kept k : kept) {
                    DowngradeResolver.PlanEntry original = plan.get(k.planIndex());
                    assertTrue(seen.add(k.planIndex()), "no duplicates");
                    assertEquals(original.id(), k.id(), "only planned enchantments");
                    assertTrue(k.level() <= original.level(), "never raised");
                    assertTrue(k.level() >= Math.min(original.level(), Math.max(1, original.minLevel())), "never below minimum");
                }
                if (retention <= 0) assertTrue(kept.isEmpty());
                if (retention >= 1) assertEquals(plan.size(), kept.size());
                if (retention > 0) {
                    int primary = -1;
                    for (int p = 0; p < plan.size(); p++) if (plan.get(p).primary()) primary = p;
                    int finalPrimary = primary;
                    assertTrue(kept.stream().anyMatch(k -> k.planIndex() == finalPrimary), "primary survives a partial binding");
                    for (int p = 0; p < plan.size(); p++) {
                        int index = p;
                        if (plan.get(p).protectedEntry()) assertTrue(kept.stream().anyMatch(k -> k.planIndex() == index), "protected survives");
                    }
                }
                assertEquals(kept, DowngradeResolver.resolve(plan, retention), "deterministic");
            }
        }
    }

    @Test
    void keptValueApproachesTheTarget() {
        RitualRandom r = new RitualRandom(8);
        for (int i = 0; i < 5000; i++) {
            List<DowngradeResolver.PlanEntry> plan = randomPlan(r);
            double total = 0;
            for (DowngradeResolver.PlanEntry e : plan) total += e.valueAt(e.level());
            List<DowngradeResolver.Kept> kept = DowngradeResolver.resolve(plan, 0.45);
            double value = 0;
            boolean anyAboveFloor = false;
            for (DowngradeResolver.Kept k : kept) {
                DowngradeResolver.PlanEntry e = plan.get(k.planIndex());
                value += e.valueAt(k.level());
                if ((e.primary() || e.protectedEntry()) && k.level() > e.floorLevel()) anyAboveFloor = true;
            }
            boolean atFloor = !anyAboveFloor && kept.stream().allMatch(k -> plan.get(k.planIndex()).primary() || plan.get(k.planIndex()).protectedEntry());
            assertTrue(value <= total * 0.45 + 1e-6 || atFloor, "either within budget or nothing more may be taken");
        }
    }

    @Test
    void secondariesGoBeforeThePrimary() {
        double[] v = {10, 20, 30, 40, 50};
        List<DowngradeResolver.PlanEntry> plan = List.of(
                new DowngradeResolver.PlanEntry("minecraft:sharpness", 1, 4, v, true, false),
                new DowngradeResolver.PlanEntry("minecraft:unbreaking", 1, 3, v, false, false),
                new DowngradeResolver.PlanEntry("minecraft:looting", 1, 2, v, false, false));
        // total 40 + 30 + 20 = 90; 75% = 67.5: unbreaking III -> II (80), looting/unbreaking II -> I ... until <= 67.5
        List<DowngradeResolver.Kept> frayed = DowngradeResolver.resolve(plan, 0.75);
        assertEquals(4, frayed.get(0).level(), "primary untouched while secondaries can give");
        assertEquals(3, frayed.size());
        List<DowngradeResolver.Kept> weak = DowngradeResolver.resolve(plan, 0.2);
        assertEquals(1, weak.size(), "secondaries removed before the primary loses its binding");
        assertEquals("minecraft:sharpness", weak.get(0).id());
        assertTrue(weak.get(0).level() < 4);
    }

    @Test
    void arcaneValueStrictlyIncreases() {
        RitualRandom r = new RitualRandom(9);
        for (int i = 0; i < 2000; i++) {
            int min = r.range(0, 3);
            int max = min + r.range(0, 8);
            double[] v = ArcaneValue.byLevel(r.nextInt(4), min, max, lvl -> r.range(-50, 400), r.nextDouble() * 3);
            for (int k = 1; k < v.length; k++) assertTrue(v[k] > v[k - 1], "value must grow with level");
            for (double d : v) assertTrue(d > 0 && Double.isFinite(d));
        }
    }

    @Test
    void bandsFollowThresholds() {
        OutcomeRules rules = OutcomeRules.DEFAULT;
        assertTrue(rules.isValid());
        assertEquals(OutcomeBand.PERFECT, rules.band(100));
        assertEquals(OutcomeBand.PERFECT, rules.band(90));
        assertEquals(OutcomeBand.STABLE, rules.band(89.99));
        assertEquals(OutcomeBand.STABLE, rules.band(80));
        assertEquals(OutcomeBand.FRAYED, rules.band(79.9));
        assertEquals(OutcomeBand.WEAK, rules.band(50));
        assertEquals(OutcomeBand.FAILED, rules.band(49));
        assertEquals(OutcomeBand.FAILED, rules.band(35));
        assertEquals(OutcomeBand.SHATTERED, rules.band(34.9));
        assertEquals(1.0, rules.retention(OutcomeBand.STABLE));
        assertEquals(0.0, rules.retention(OutcomeBand.FAILED));
        assertFalse(new OutcomeRules(90, 80, 85, 50, 35, 0.75, 0.45).isValid(), "frayed above full is invalid");
        assertFalse(new OutcomeRules(90, 80, 65, 50, 35, 0.4, 0.45).isValid(), "weak retaining more than frayed is invalid");
    }
}
