package com.otectus.immersiveenchanting.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One datapack profile ({@code data/<ns>/immersive_enchanting/enchantment_profiles/*.json}). Every field is optional;
 * a profile only overrides what it specifies, so profiles of several layers can combine.
 */
public record EnchantmentProfile(ResourceLocation id, int priority, ProfileSelector selector,
                                 @Nullable Double multiplier, @Nullable Double offset, @Nullable Integer minTier,
                                 @Nullable Integer maxTier, @Nullable Double arcaneValueMultiplier,
                                 @Nullable ResourceLocation patternSet, Map<String, Double> motifOverrides,
                                 @Nullable Double chordBias, @Nullable Double holdBias, @Nullable Boolean enabled,
                                 @Nullable Boolean protectAsPrimary, @Nullable ResourceLocation theme) {

    public ProfileSelector.Layer layer() {
        return id.getNamespace().equals(ImmersiveEnchanting.MOD_ID) ? ProfileSelector.Layer.BUNDLED : selector.specificity();
    }

    static EnchantmentProfile parse(ResourceLocation id, JsonElement json, Consumer<String> warn) {
        JsonObject root = JsonHelper.object(json, "profile");
        int priority = JsonHelper.getInt(root, "priority", 0, -100_000, 100_000, warn);
        JsonObject sel = JsonHelper.object(root.get("selector"), "\"selector\"");
        Set<ResourceLocation> enchantments = new HashSet<>();
        for (String s : JsonHelper.stringList(sel, "enchantments")) {
            ResourceLocation e = ResourceLocation.tryParse(s);
            if (e == null) throw new JsonParseException("invalid enchantment id: " + s);
            enchantments.add(e);
        }
        Set<String> namespaces = new HashSet<>(JsonHelper.stringList(sel, "namespaces"));
        Set<Enchantment.Rarity> rarities = new HashSet<>();
        for (String s : JsonHelper.stringList(sel, "rarities")) {
            try {
                rarities.add(Enchantment.Rarity.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("unknown rarity \"" + s + "\" (common, uncommon, rare, very_rare)");
            }
        }
        Boolean all = JsonHelper.optionalBoolean(sel, "all");
        ProfileSelector selector = new ProfileSelector(Set.copyOf(enchantments), Set.copyOf(namespaces), Set.copyOf(rarities),
                JsonHelper.optionalBoolean(sel, "curse"), JsonHelper.optionalBoolean(sel, "treasure"), all != null && all);
        if (selector.isEmpty()) warn.accept("selector is empty and matches nothing; use \"all\": true to match every enchantment");

        JsonObject difficulty = JsonHelper.optionalObject(root, "difficulty");
        JsonObject pattern = JsonHelper.optionalObject(root, "pattern");
        JsonObject behavior = JsonHelper.optionalObject(root, "behavior");
        JsonObject presentation = JsonHelper.optionalObject(root, "presentation");

        Double multiplier = null, offset = null, arcane = null, chordBias = null, holdBias = null;
        Integer minTier = null, maxTier = null;
        ResourceLocation patternSet = null, theme = null;
        Boolean enabled = null, protect = null;
        Map<String, Double> overrides = new LinkedHashMap<>();
        if (difficulty != null) {
            multiplier = JsonHelper.optionalDouble(difficulty, "multiplier", 0.0, 10.0, warn);
            offset = JsonHelper.optionalDouble(difficulty, "offset", -100.0, 100.0, warn);
            minTier = JsonHelper.optionalInt(difficulty, "min_tier", 0, 5, warn);
            maxTier = JsonHelper.optionalInt(difficulty, "max_tier", 0, 5, warn);
            arcane = JsonHelper.optionalDouble(difficulty, "arcane_value_multiplier", 0.05, 20.0, warn);
            if (minTier != null && maxTier != null && maxTier < minTier) {
                warn.accept("max_tier " + maxTier + " is below min_tier " + minTier + "; using min_tier for both");
                maxTier = minTier;
            }
        }
        if (pattern != null) {
            patternSet = JsonHelper.optionalId(pattern, "pattern_set");
            chordBias = JsonHelper.optionalDouble(pattern, "chord_bias", -1.0, 1.0, warn);
            holdBias = JsonHelper.optionalDouble(pattern, "hold_bias", -1.0, 1.0, warn);
            JsonObject weights = JsonHelper.optionalObject(pattern, "motif_weight_overrides");
            if (weights != null) {
                for (String key : weights.keySet()) {
                    ResourceLocation motif = ResourceLocation.tryParse(key);
                    if (motif == null) throw new JsonParseException("invalid motif id: " + key);
                    Double w = JsonHelper.optionalDouble(weights, key, 0.0, 100.0, warn);
                    if (w != null) overrides.put(motif.toString(), w);
                }
            }
        }
        if (behavior != null) {
            enabled = JsonHelper.optionalBoolean(behavior, "enabled");
            protect = JsonHelper.optionalBoolean(behavior, "protect_as_primary");
        }
        if (presentation != null) theme = JsonHelper.optionalId(presentation, "theme");
        return new EnchantmentProfile(id, priority, selector, multiplier, offset, minTier, maxTier, arcane, patternSet,
                Map.copyOf(overrides), chordBias, holdBias, enabled, protect, theme);
    }
}
