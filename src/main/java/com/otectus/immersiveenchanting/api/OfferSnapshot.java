package com.otectus.immersiveenchanting.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The exact enchantment plan an enchanting system would apply for one offer, captured when a ritual starts. The plan
 * is authoritative: Immersive Enchanting never re-validates it against vanilla rules, and a degraded result is
 * always a subset of it at equal or lower levels. It stays on the server until the ritual resolves.
 *
 * @param primaryClueId  the enchantment the table showed as this offer's clue, if any; the ritual's primary binding
 * @param adapterData    adapter-private data carried from snapshot to application
 */
public record OfferSnapshot(int containerId, int offerIndex, int displayedRequirement, CostSnapshot cost,
                            @Nullable ResourceLocation primaryClueId, int primaryClueLevel,
                            List<EnchantmentInstance> plannedEnchantments, ItemFingerprint itemFingerprint,
                            int playerEnchantSeed, DifficultyScale difficultyScale, boolean isBook, CompoundTag adapterData) {

    public OfferSnapshot {
        plannedEnchantments = plannedEnchantments.stream().map(e -> new EnchantmentInstance(e.enchantment, e.level)).toList();
        adapterData = adapterData == null ? new CompoundTag() : adapterData.copy();
    }

    @Override
    public CompoundTag adapterData() {
        return adapterData.copy();
    }
}
