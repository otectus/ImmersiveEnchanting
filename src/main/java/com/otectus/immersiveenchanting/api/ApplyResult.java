package com.otectus.immersiveenchanting.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What an adapter did when applying an outcome.
 *
 * @param applied        the adapter performed the outcome (charged, and enchanted if anything bound)
 * @param rejection      why nothing was done, when {@code applied} is false; nothing was mutated in that case
 * @param resultStack    a copy of the item after application
 * @param bound          what was actually bound, when the system bound less than it was given (null: all of it)
 */
public record ApplyResult(boolean applied, @Nullable AbortReason rejection, ItemStack resultStack, int xpLevelsCharged,
                          int xpPointsCharged, int lapisCharged, boolean seedAdvanced, @Nullable List<EnchantmentInstance> bound) {

    public static ApplyResult rejected(AbortReason reason) {
        return new ApplyResult(false, reason, ItemStack.EMPTY, 0, 0, 0, false, null);
    }

    public static ApplyResult applied(ItemStack result, int xpLevels, int xpPoints, int lapis, boolean seedAdvanced) {
        return new ApplyResult(true, null, result.copy(), xpLevels, xpPoints, lapis, seedAdvanced, null);
    }

    /** Applied, but binding only {@code bound} of what the adapter was given. */
    public ApplyResult withBound(List<EnchantmentInstance> actuallyBound) {
        return new ApplyResult(applied, rejection, resultStack, xpLevelsCharged, xpPointsCharged, lapisCharged, seedAdvanced,
                List.copyOf(actuallyBound));
    }
}
