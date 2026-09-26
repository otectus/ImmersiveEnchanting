package com.otectus.immersiveenchanting.ritual;

import java.util.List;

/**
 * Ritual complexity from the actual planned enchantment bundle.
 *
 * <pre>
 * powerFactor   = clamp(displayedCost / referenceMaxCost, 0, 2)
 * levelFactor   = strongest(normalizedLevel)          strongest(xs) = clamp(max + 0.25 * (sum - max), 0, 1)
 * rarityFactor  = strongest(rarityWeight)             rarity weights: 0, 0.30, 0.65, 1.00
 * multiFactor   = clamp((count - 1) / 3, 0, 1)
 * specialFactor = clamp((any treasure ? 0.6 : 0) + (any curse ? 0.4 : 0), 0, 1)
 *
 * base = 28 min(power, 1) + 10 max(power - 1, 0) + 22 level + 20 rarity + 15 multi + 5 special + 1.5 offerIndex
 * score = clamp(base * global * adapter * profile * event + profileOffset + eventOffset, 0, 100),
 *         then clamped into [lowest score of minTier, highest score of maxTier]
 * </pre>
 *
 * <p>The level and rarity factors use {@code strongest} rather than an average so the score is monotonic: adding an
 * enchantment, raising a level, raising a rarity or raising the displayed cost never lowers it (without datapack
 * overrides). An average would let a weak secondary enchantment make a bundle easier.
 *
 * <p>Profile modifiers combine across the bundle: multipliers and offsets are averaged, weighted by each
 * enchantment's arcane value; the minimum tier is the highest requested; the maximum tier is the highest allowed.
 */
public final class DifficultyCalculator {
    public static final double[] RARITY_WEIGHTS = {0.00, 0.30, 0.65, 1.00};
    public static final double DEFAULT_REFERENCE_MAX_COST = 30.0;

    /**
     * One planned enchantment. {@code rarity} is 0 (common) to 3 (very rare). {@code weight} is its arcane value,
     * used to weight profile modifiers.
     */
    public record Entry(String id, int rarity, int rolledLevel, int minLevel, int maxLevel, boolean curse, boolean treasure,
                        double profileMultiplier, double profileOffset, int profileMinTier, int profileMaxTier, double weight) {

        public static Entry generic(String id, int rarity, int rolledLevel, int minLevel, int maxLevel, boolean curse,
                                    boolean treasure, double weight) {
            return new Entry(id, rarity, rolledLevel, minLevel, maxLevel, curse, treasure, 1.0, 0.0, 0, 5, weight);
        }
    }

    public record Input(List<Entry> entries, int displayedCost, double referenceMaxCost, int offerIndex,
                        double globalMultiplier, double adapterMultiplier, double eventMultiplier, double eventOffset) {
        public Input {
            entries = List.copyOf(entries);
        }

        public static Input of(List<Entry> entries, int displayedCost, int offerIndex) {
            return new Input(entries, displayedCost, DEFAULT_REFERENCE_MAX_COST, offerIndex, 1.0, 1.0, 1.0, 0.0);
        }
    }

    /** Every intermediate value, for the debug command and the tests. */
    public record Breakdown(double powerFactor, double levelFactor, double rarityFactor, double multiFactor,
                            double specialFactor, double base, double multiplier, double offset, int minTier,
                            int maxTier, double score, DifficultyTier tier) {
    }

    public static Breakdown compute(Input in) {
        List<Entry> entries = in.entries();
        double reference = in.referenceMaxCost() > 0 && Double.isFinite(in.referenceMaxCost()) ? in.referenceMaxCost() : DEFAULT_REFERENCE_MAX_COST;
        double power = clamp(Math.max(0, in.displayedCost()) / reference, 0.0, 2.0);

        double[] levels = new double[entries.size()];
        double[] rarities = new double[entries.size()];
        boolean treasure = false;
        boolean curse = false;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            levels[i] = normalizedLevel(e.rolledLevel(), e.minLevel(), e.maxLevel());
            rarities[i] = RARITY_WEIGHTS[Math.max(0, Math.min(3, e.rarity()))];
            treasure |= e.treasure();
            curse |= e.curse();
        }
        double levelFactor = strongest(levels);
        double rarityFactor = strongest(rarities);
        double multiFactor = clamp((entries.size() - 1) / 3.0, 0.0, 1.0);
        double specialFactor = clamp((treasure ? 0.6 : 0.0) + (curse ? 0.4 : 0.0), 0.0, 1.0);

        double base = 28.0 * Math.min(power, 1.0)
                + 10.0 * Math.max(power - 1.0, 0.0)
                + 22.0 * levelFactor
                + 20.0 * rarityFactor
                + 15.0 * multiFactor
                + 5.0 * specialFactor
                + 1.5 * Math.max(0, Math.min(2, in.offerIndex()));

        double weightSum = 0;
        for (Entry e : entries) weightSum += Math.max(0, finiteOr(e.weight(), 0));
        double profileMultiplier = 0;
        double profileOffset = 0;
        int minTier = 0;
        int maxTier = entries.isEmpty() ? 5 : 0;
        for (Entry e : entries) {
            double w = weightSum > 0 ? Math.max(0, finiteOr(e.weight(), 0)) / weightSum : 1.0 / entries.size();
            profileMultiplier += w * clamp(finiteOr(e.profileMultiplier(), 1.0), 0.0, 10.0);
            profileOffset += w * clamp(finiteOr(e.profileOffset(), 0.0), -100.0, 100.0);
            minTier = Math.max(minTier, clampTier(e.profileMinTier()));
            maxTier = Math.max(maxTier, clampTier(e.profileMaxTier()));
        }
        if (entries.isEmpty()) profileMultiplier = 1.0;
        maxTier = Math.max(maxTier, minTier);

        double multiplier = clamp(finiteOr(in.globalMultiplier(), 1.0), 0.0, 10.0)
                * clamp(finiteOr(in.adapterMultiplier(), 1.0), 0.0, 10.0)
                * profileMultiplier
                * clamp(finiteOr(in.eventMultiplier(), 1.0), 0.0, 10.0);
        double offset = profileOffset + clamp(finiteOr(in.eventOffset(), 0.0), -100.0, 100.0);
        double score = clamp(base * multiplier + offset, 0.0, 100.0);
        score = clamp(score, DifficultyTier.byIndex(minTier).lowScore(), DifficultyTier.byIndex(maxTier).maxScoreInTier());
        return new Breakdown(power, levelFactor, rarityFactor, multiFactor, specialFactor, base, multiplier, offset,
                minTier, maxTier, score, DifficultyTier.fromScore(score));
    }

    /**
     * Rolled level mapped to 0..1 within the enchantment's own range. Single-level enchantments use
     * {@code clamp((level - 1) / 4, 0.15, 1)} so they still register some weight.
     */
    public static double normalizedLevel(int rolled, int min, int max) {
        if (max <= min) return clamp((rolled - 1) / 4.0, 0.15, 1.0);
        return clamp((double) (rolled - min) / (max - min), 0.0, 1.0);
    }

    /** The strongest value plus a quarter of the rest, capped at 1. Never decreases when a value is added or raised. */
    static double strongest(double[] values) {
        if (values.length == 0) return 0.0;
        double max = 0;
        double sum = 0;
        for (double v : values) {
            max = Math.max(max, v);
            sum += v;
        }
        return clamp(max + 0.25 * (sum - max), 0.0, 1.0);
    }

    private static int clampTier(int tier) {
        return Math.max(0, Math.min(5, tier));
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private DifficultyCalculator() {}
}
