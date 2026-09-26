package com.otectus.immersiveenchanting.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Which enchantments a profile applies to. Every criterion given must match; within a list, any entry matches.
 * An empty selector matches nothing; {@code "all": true} matches everything the other criteria allow.
 */
public record ProfileSelector(Set<ResourceLocation> enchantments, Set<String> namespaces, Set<Enchantment.Rarity> rarities,
                              @Nullable Boolean curse, @Nullable Boolean treasure, boolean all) {

    /** Precedence classes, least to most specific. Bundled profiles (this mod's namespace) sit below every datapack. */
    public enum Layer { BUNDLED, BROAD, NAMESPACE, EXACT }

    public boolean isEmpty() {
        return enchantments.isEmpty() && namespaces.isEmpty() && rarities.isEmpty() && curse == null && treasure == null && !all;
    }

    public Layer specificity() {
        if (!enchantments.isEmpty()) return Layer.EXACT;
        if (!namespaces.isEmpty()) return Layer.NAMESPACE;
        return Layer.BROAD;
    }

    public boolean matches(ResourceLocation id, Enchantment enchantment) {
        if (isEmpty()) return false;
        if (!enchantments.isEmpty() && !enchantments.contains(id)) return false;
        if (!namespaces.isEmpty() && !namespaces.contains(id.getNamespace())) return false;
        if (!rarities.isEmpty() && !rarities.contains(enchantment.getRarity())) return false;
        if (curse != null && enchantment.isCurse() != curse) return false;
        return treasure == null || enchantment.isTreasureOnly() == treasure;
    }
}
