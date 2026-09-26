package com.otectus.immersiveenchanting.api;

/**
 * How an enchanting system's power maps onto ritual difficulty.
 *
 * @param referenceMaxCost displayed cost that counts as full power (vanilla: 30); systems with higher costs use a larger value
 * @param multiplier       extra factor on the complexity score for this system
 */
public record DifficultyScale(double referenceMaxCost, double multiplier) {
    public static final DifficultyScale VANILLA = new DifficultyScale(30.0, 1.0);
}
