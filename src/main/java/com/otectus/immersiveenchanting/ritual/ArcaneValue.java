package com.otectus.immersiveenchanting.ritual;

import java.util.function.IntUnaryOperator;

/**
 * The "arcane value" of an enchantment at each level: the currency of partial bindings. A degraded ritual keeps a
 * fraction of the plan's total value, so value must describe unknown modded enchantments sensibly without authored
 * data. It combines the enchantment's rarity, its level and its own enchantability cost curve:
 *
 * <pre>
 * value(level) = rarityScale * (0.6 * clamp(minCost(level), 1, 150) + 10 * (level - minLevel + 1)) * profileMultiplier
 * </pre>
 *
 * then forced strictly increasing with level, so taking a level away always gives value back. Rarity scales are
 * 1.0, 1.35, 1.75 and 2.25 from common to very rare.
 */
public final class ArcaneValue {
    public static final double[] RARITY_SCALE = {1.0, 1.35, 1.75, 2.25};

    /**
     * Values for levels {@code minLevel..maxLevel}; index 0 is {@code minLevel}.
     *
     * @param minCost the enchantment's minimum enchantability for a level (Enchantment#getMinCost)
     */
    public static double[] byLevel(int rarity, int minLevel, int maxLevel, IntUnaryOperator minCost, double profileMultiplier) {
        int lo = Math.max(0, minLevel);
        int hi = Math.max(lo, maxLevel);
        double scale = RARITY_SCALE[Math.max(0, Math.min(3, rarity))]
                * (Double.isFinite(profileMultiplier) ? Math.max(0.05, Math.min(20.0, profileMultiplier)) : 1.0);
        double[] values = new double[hi - lo + 1];
        double previous = 0;
        for (int level = lo; level <= hi; level++) {
            int cost;
            try {
                cost = minCost.applyAsInt(level);
            } catch (RuntimeException e) {
                cost = 1;
            }
            double raw = scale * (0.6 * Math.max(1, Math.min(150, cost)) + 10.0 * (level - lo + 1));
            double value = level == lo ? raw : Math.max(raw, previous + scale);
            values[level - lo] = value;
            previous = value;
        }
        return values;
    }

    private ArcaneValue() {}
}
