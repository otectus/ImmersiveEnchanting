package com.otectus.immersiveenchanting.api;

/**
 * What a ritual that bound nothing costs, resolved from the server configuration. A ritual that binds at least one
 * enchantment always costs the offer's normal price and advances the enchantment seed, regardless of this policy.
 */
public record ChargePolicy(boolean chargeXp, boolean chargeLapis, boolean advanceSeed) {
    public static final ChargePolicy FREE = new ChargePolicy(false, false, false);
    public static final ChargePolicy FULL = new ChargePolicy(true, true, true);
}
