package com.otectus.immersiveenchanting.ritual;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the rolled plan into what a partial binding keeps. It only ever lowers levels or removes entries of the
 * original plan, so a degraded result can never contain an enchantment, or a level, that was not rolled.
 *
 * <p>Given a retention fraction, the target is {@code retention x total arcane value of the plan}. Steps are taken
 * one at a time until the kept value is at or below the target:
 * <ol>
 *   <li>lower the highest-level secondary enchantment by one level (ties: higher current value, then id);</li>
 *   <li>once every secondary is at its minimum level, remove the lowest-value secondary (ties: id);</li>
 *   <li>then lower protected secondaries ({@code protect_as_primary}) by level;</li>
 *   <li>then lower the primary binding by level.</li>
 * </ol>
 * The primary binding, and any protected entry, is never removed while retention is above zero: a partial binding
 * always keeps at least the primary at its minimum level. Retention of zero removes everything; retention of one
 * keeps the plan unchanged. Output preserves the plan's order.
 */
public final class DowngradeResolver {

    /**
     * One planned enchantment.
     *
     * @param values arcane value by level, index 0 = {@code minLevel}, covering at least {@code minLevel..level}
     */
    public record PlanEntry(String id, int minLevel, int level, double[] values, boolean primary, boolean protectedEntry) {
        double valueAt(int lvl) {
            int index = Math.max(0, Math.min(values.length - 1, lvl - minLevel));
            return values.length == 0 ? 0 : values[index];
        }

        /** The lowest level a partial binding may leave, never above the rolled level. */
        int floorLevel() {
            return Math.min(level, Math.max(1, minLevel));
        }
    }

    /** A kept enchantment. {@code planIndex} points back into the plan. */
    public record Kept(int planIndex, String id, int level) {}

    public static List<Kept> resolve(List<PlanEntry> plan, double retention) {
        List<Kept> out = new ArrayList<>();
        if (plan.isEmpty() || !(retention > 0)) return out;
        int n = plan.size();
        int[] level = new int[n];
        boolean[] present = new boolean[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            level[i] = plan.get(i).level();
            present[i] = true;
            total += plan.get(i).valueAt(level[i]);
        }
        if (retention < 1.0) {
            double target = total * retention;
            double current = total;
            while (current > target + 1e-9) {
                int step = pick(plan, level, present);
                if (step == Integer.MIN_VALUE) break;
                if (step >= 0) {
                    current -= plan.get(step).valueAt(level[step]) - plan.get(step).valueAt(level[step] - 1);
                    level[step]--;
                } else {
                    int i = -step - 1;
                    current -= plan.get(i).valueAt(level[i]);
                    present[i] = false;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (present[i]) out.add(new Kept(i, plan.get(i).id(), level[i]));
        }
        return out;
    }

    /** Next step: {@code i >= 0} lowers entry i by one level, {@code -(i+1)} removes it, MIN_VALUE means none left. */
    private static int pick(List<PlanEntry> plan, int[] level, boolean[] present) {
        int reduce = bestReduction(plan, level, present, Role.SECONDARY);
        if (reduce >= 0) return reduce;
        int remove = -1;
        for (int i = 0; i < plan.size(); i++) {
            PlanEntry e = plan.get(i);
            if (!present[i] || role(e) != Role.SECONDARY) continue;
            if (remove < 0 || compareRemoval(plan, level, i, remove) < 0) remove = i;
        }
        if (remove >= 0) return -remove - 1;
        reduce = bestReduction(plan, level, present, Role.PROTECTED);
        if (reduce >= 0) return reduce;
        reduce = bestReduction(plan, level, present, Role.PRIMARY);
        if (reduce >= 0) return reduce;
        return Integer.MIN_VALUE;
    }

    private enum Role { PRIMARY, PROTECTED, SECONDARY }

    private static Role role(PlanEntry e) {
        if (e.primary()) return Role.PRIMARY;
        return e.protectedEntry() ? Role.PROTECTED : Role.SECONDARY;
    }

    private static int bestReduction(List<PlanEntry> plan, int[] level, boolean[] present, Role role) {
        int best = -1;
        for (int i = 0; i < plan.size(); i++) {
            PlanEntry e = plan.get(i);
            if (!present[i] || role(e) != role || level[i] <= e.floorLevel()) continue;
            if (best < 0 || compareReduction(plan, level, i, best) < 0) best = i;
        }
        return best;
    }

    /** Negative if {@code a} should be lowered before {@code b}: higher level, then higher value, then id. */
    private static int compareReduction(List<PlanEntry> plan, int[] level, int a, int b) {
        if (level[a] != level[b]) return Integer.compare(level[b], level[a]);
        int byValue = Double.compare(plan.get(b).valueAt(level[b]), plan.get(a).valueAt(level[a]));
        if (byValue != 0) return byValue;
        int byId = plan.get(a).id().compareTo(plan.get(b).id());
        return byId != 0 ? byId : Integer.compare(a, b);
    }

    /** Negative if {@code a} should be removed before {@code b}: lower value, then id. */
    private static int compareRemoval(List<PlanEntry> plan, int[] level, int a, int b) {
        int byValue = Double.compare(plan.get(a).valueAt(level[a]), plan.get(b).valueAt(level[b]));
        if (byValue != 0) return byValue;
        int byId = plan.get(a).id().compareTo(plan.get(b).id());
        return byId != 0 ? byId : Integer.compare(a, b);
    }

    private DowngradeResolver() {}
}
