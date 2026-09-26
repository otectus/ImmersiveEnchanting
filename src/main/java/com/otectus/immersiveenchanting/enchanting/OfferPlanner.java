package com.otectus.immersiveenchanting.enchanting;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.api.event.RitualPlanEvent;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.data.EnchantmentProfileManager;
import com.otectus.immersiveenchanting.data.PatternSetManager;
import com.otectus.immersiveenchanting.data.ResolvedProfile;
import com.otectus.immersiveenchanting.ritual.ArcaneValue;
import com.otectus.immersiveenchanting.ritual.DifficultyCalculator;
import com.otectus.immersiveenchanting.ritual.DowngradeResolver;
import com.otectus.immersiveenchanting.ritual.ParamsResolver;
import com.otectus.immersiveenchanting.ritual.PatternGenerator;
import com.otectus.immersiveenchanting.ritual.PatternSetDef;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import com.otectus.immersiveenchanting.ritual.RitualRandom;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a captured offer into everything a ritual needs: per-enchantment facts and profiles, the complexity
 * breakdown, the resolved pattern set and parameters, the primary binding, the theme and the pattern seed. Pure
 * reads; nothing here mutates game state. Used for starting rituals, for offer previews and by the debug commands.
 */
public final class OfferPlanner {
    /** Non-secret constant folded into every pattern seed. */
    private static final long SEED_SALT = 0x1E5A_2026_0925L;

    public record PlannedEnchantment(ResourceLocation id, EnchantmentInstance instance, ResolvedProfile profile,
                                     double[] arcaneValues, DifficultyCalculator.Entry entry, boolean primary) {
        public double value() {
            return arcaneValues[Math.max(0, Math.min(arcaneValues.length - 1, instance.level - instance.enchantment.getMinLevel()))];
        }
    }

    public record Plan(OfferSnapshot snapshot, List<PlannedEnchantment> enchantments, DifficultyCalculator.Breakdown difficulty,
                       ResourceLocation patternSetId, RitualParams params, long seed, ResourceLocation theme,
                       boolean ritualEnabled) {

        public List<DowngradeResolver.PlanEntry> downgradeEntries() {
            List<DowngradeResolver.PlanEntry> out = new ArrayList<>();
            for (PlannedEnchantment p : enchantments) {
                Enchantment e = p.instance().enchantment;
                out.add(new DowngradeResolver.PlanEntry(p.id().toString(), e.getMinLevel(), p.instance().level, p.arcaneValues(),
                        p.primary(), p.profile().protectAsPrimary()));
            }
            return out;
        }

        public int tier() {
            return difficulty.tier().index();
        }
    }

    /**
     * @param timingAssist the player's requested accessibility widening; clamped here to the server's ceiling
     * @param preview      true for offer previews; only changes the event's flag and skips the debug log
     */
    public static Plan plan(ServerPlayer player, EnchantingContextAdapter adapter, OfferSnapshot snapshot, double timingAssist, boolean preview) {
        ResourceLocation adapterId = adapter.id();
        List<EnchantmentInstance> planned = snapshot.plannedEnchantments();
        List<ResourceLocation> ids = new ArrayList<>();
        List<ResolvedProfile> profiles = new ArrayList<>();
        List<double[]> values = new ArrayList<>();
        boolean enabled = true;
        for (EnchantmentInstance instance : planned) {
            Enchantment e = instance.enchantment;
            ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(e);
            if (id == null) id = new ResourceLocation("unknown", "unregistered");
            ResolvedProfile profile = EnchantmentProfileManager.resolve(e);
            ids.add(id);
            profiles.add(profile);
            values.add(ArcaneValue.byLevel(rarity(e), e.getMinLevel(), Math.max(instance.level, e.getMinLevel()), e::getMinCost,
                    profile.arcaneValueMultiplier()));
            enabled &= profile.enabled();
        }
        int primary = primaryIndex(snapshot, ids, planned, values);

        List<PlannedEnchantment> entries = new ArrayList<>();
        List<DifficultyCalculator.Entry> difficultyEntries = new ArrayList<>();
        for (int i = 0; i < planned.size(); i++) {
            EnchantmentInstance instance = planned.get(i);
            Enchantment e = instance.enchantment;
            ResolvedProfile p = profiles.get(i);
            double[] v = values.get(i);
            double weight = v[Math.max(0, Math.min(v.length - 1, instance.level - e.getMinLevel()))];
            DifficultyCalculator.Entry entry = new DifficultyCalculator.Entry(ids.get(i).toString(), rarity(e), instance.level,
                    e.getMinLevel(), Math.max(e.getMinLevel(), adapter.maxLevel(e)), e.isCurse(), adapter.isTreasure(e), p.multiplier(), p.offset(), p.minTier(),
                    p.maxTier(), weight);
            difficultyEntries.add(entry);
            entries.add(new PlannedEnchantment(ids.get(i), instance, p, v, entry, i == primary));
        }

        RitualPlanEvent event = new RitualPlanEvent(player, adapterId, snapshot, preview);
        MinecraftForge.EVENT_BUS.post(event);

        DifficultyCalculator.Breakdown difficulty = DifficultyCalculator.compute(new DifficultyCalculator.Input(difficultyEntries,
                snapshot.displayedRequirement(), snapshot.difficultyScale().referenceMaxCost(), snapshot.offerIndex(),
                ServerConfig.get(ServerConfig.GLOBAL_DIFFICULTY_MULTIPLIER), snapshot.difficultyScale().multiplier(),
                event.getDifficultyMultiplier(), event.getDifficultyOffset()));

        ResolvedProfile primaryProfile = primary >= 0 ? profiles.get(primary) : ResolvedProfile.GENERIC;
        ResourceLocation patternSetId = event.getPatternSetOverride() != null ? event.getPatternSetOverride() : primaryProfile.patternSet();
        if (!PatternSetManager.has(patternSetId)) patternSetId = PatternSetManager.DEFAULT_ID;
        PatternSetDef set = PatternSetManager.get(patternSetId);

        double weightSum = 0, holdBias = 0, chordBias = 0;
        for (PlannedEnchantment p : entries) weightSum += p.value();
        for (PlannedEnchantment p : entries) {
            double w = weightSum > 0 ? p.value() / weightSum : 1.0 / entries.size();
            holdBias += w * p.profile().holdBias();
            chordBias += w * p.profile().chordBias();
        }
        Map<String, Double> overrides = primaryProfile.motifOverrides();
        double assist = Math.max(1.0, Math.min(ServerConfig.get(ServerConfig.MAX_CLIENT_TIMING_ASSIST),
                Double.isFinite(timingAssist) ? timingAssist : 1.0));
        ParamsResolver.Tuning tuning = new ParamsResolver.Tuning(ServerConfig.get(ServerConfig.TEMPO_MULTIPLIER),
                ServerConfig.get(ServerConfig.EVENT_COUNT_MULTIPLIER), ServerConfig.get(ServerConfig.TIMING_WINDOW_MULTIPLIER), assist,
                ServerConfig.get(ServerConfig.MAX_PATTERN_EVENTS), ServerConfig.get(ServerConfig.MAX_RITUAL_DURATION_SECONDS));
        RitualParams params = ParamsResolver.resolve(set, difficulty.score(), tuning,
                new ParamsResolver.Shaping(holdBias, chordBias, overrides));

        long seed = seed(player, snapshot, patternSetId);
        if (!preview) {
            ImmersiveEnchanting.debug("planned offer {} for {} via {}: {} enchantment(s), power {} / {}, complexity {} ({}), pattern set {}",
                    snapshot.offerIndex(), player.getGameProfile().getName(), adapterId, planned.size(), snapshot.displayedRequirement(),
                    snapshot.difficultyScale().referenceMaxCost(), String.format("%.1f", difficulty.score()), difficulty.tier().id(), patternSetId);
        }
        return new Plan(snapshot, List.copyOf(entries), difficulty, patternSetId, params, seed, primaryProfile.theme(), enabled);
    }

    /** The clue enchantment when it is in the plan, otherwise the most valuable entry (ties: lowest id). */
    private static int primaryIndex(OfferSnapshot snapshot, List<ResourceLocation> ids, List<EnchantmentInstance> planned, List<double[]> values) {
        if (planned.isEmpty()) return -1;
        if (snapshot.primaryClueId() != null) {
            int sameId = -1;
            for (int i = 0; i < planned.size(); i++) {
                if (!ids.get(i).equals(snapshot.primaryClueId())) continue;
                if (planned.get(i).level == snapshot.primaryClueLevel()) return i;
                if (sameId < 0) sameId = i;
            }
            if (sameId >= 0) return sameId;
        }
        int best = 0;
        for (int i = 1; i < planned.size(); i++) {
            double vi = values.get(i)[values.get(i).length - 1];
            double vb = values.get(best)[values.get(best).length - 1];
            if (vi > vb || (vi == vb && ids.get(i).toString().compareTo(ids.get(best).toString()) < 0)) best = i;
        }
        return best;
    }

    /** Player, enchantment seed, container, offer, item and pattern set; never the world seed. */
    private static long seed(ServerPlayer player, OfferSnapshot snapshot, ResourceLocation patternSetId) {
        long h = SEED_SALT;
        h = RitualRandom.mix(h, player.getUUID().getMostSignificantBits());
        h = RitualRandom.mix(h, player.getUUID().getLeastSignificantBits());
        h = RitualRandom.mix(h, snapshot.playerEnchantSeed());
        h = RitualRandom.mix(h, snapshot.containerId());
        h = RitualRandom.mix(h, snapshot.offerIndex());
        h = RitualRandom.mix(h, snapshot.itemFingerprint().hash());
        h = RitualRandom.mix(h, patternSetId.toString());
        return RitualRandom.mix(h, PatternGenerator.VERSION);
    }

    public static int rarity(Enchantment e) {
        return switch (e.getRarity()) {
            case COMMON -> 0;
            case UNCOMMON -> 1;
            case RARE -> 2;
            case VERY_RARE -> 3;
        };
    }

    private OfferPlanner() {}
}
