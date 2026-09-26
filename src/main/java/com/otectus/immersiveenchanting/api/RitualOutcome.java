package com.otectus.immersiveenchanting.api;

import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;

/**
 * The resolved result of a ritual, handed to the adapter that applies it.
 *
 * @param abandoned     the ritual did not finish normally (closed, disconnected, expired or rejected input) and was
 *                      resolved as a failure
 * @param planned       the original plan from the {@link OfferSnapshot}
 * @param resolved      what to apply: a subset of {@code planned} at equal or lower levels; empty for a failure
 * @param failureCharge what to charge if {@code resolved} is empty
 */
public record RitualOutcome(OutcomeBand band, double score, boolean abandoned, List<EnchantmentInstance> planned,
                            List<EnchantmentInstance> resolved, ChargePolicy failureCharge) {
    public RitualOutcome {
        planned = List.copyOf(planned);
        resolved = List.copyOf(resolved);
    }

    public boolean bindsAnything() {
        return !resolved.isEmpty();
    }

    /** What this outcome costs: the full price when anything binds, else the failure policy. */
    public ChargePolicy charge() {
        return bindsAnything() ? ChargePolicy.FULL : failureCharge;
    }
}
