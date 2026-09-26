package com.otectus.immersiveenchanting.data;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.ritual.PatternSetDef;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effective profile for one enchantment after every matching profile is folded in precedence order.
 *
 * @param sources the profiles that contributed, least to most specific; empty for the generic fallback
 */
public record ResolvedProfile(double multiplier, double offset, int minTier, int maxTier, double arcaneValueMultiplier,
                              ResourceLocation patternSet, Map<String, Double> motifOverrides, double chordBias,
                              double holdBias, boolean enabled, boolean protectAsPrimary, ResourceLocation theme,
                              List<ResourceLocation> sources) {

    public static final ResourceLocation DEFAULT_THEME = ImmersiveEnchanting.id("arcane");
    public static final ResolvedProfile GENERIC = new ResolvedProfile(1.0, 0.0, 0, 5, 1.0,
            new ResourceLocation(PatternSetDef.DEFAULT_ID), Map.of(), 0.0, 0.0, true, false, DEFAULT_THEME, List.of());

    public boolean isGeneric() {
        return sources.isEmpty();
    }

    ResolvedProfile with(EnchantmentProfile p) {
        Map<String, Double> overrides = motifOverrides;
        if (!p.motifOverrides().isEmpty()) {
            Map<String, Double> merged = new LinkedHashMap<>(motifOverrides);
            merged.putAll(p.motifOverrides());
            overrides = Map.copyOf(merged);
        }
        int min = p.minTier() != null ? p.minTier() : minTier;
        int max = p.maxTier() != null ? p.maxTier() : maxTier;
        List<ResourceLocation> src = new java.util.ArrayList<>(sources);
        src.add(p.id());
        return new ResolvedProfile(
                p.multiplier() != null ? p.multiplier() : multiplier,
                p.offset() != null ? p.offset() : offset,
                min, Math.max(min, max),
                p.arcaneValueMultiplier() != null ? p.arcaneValueMultiplier() : arcaneValueMultiplier,
                p.patternSet() != null ? p.patternSet() : patternSet,
                overrides,
                p.chordBias() != null ? p.chordBias() : chordBias,
                p.holdBias() != null ? p.holdBias() : holdBias,
                p.enabled() != null ? p.enabled() : enabled,
                p.protectAsPrimary() != null ? p.protectAsPrimary() : protectAsPrimary,
                p.theme() != null ? p.theme() : theme,
                List.copyOf(src));
    }
}
