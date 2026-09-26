package com.otectus.immersiveenchanting.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads enchantment profiles and resolves each enchantment's effective profile.
 *
 * <h2>Precedence</h2>
 * Matching profiles are folded from least to most specific, each overriding only the fields it sets:
 * <ol>
 *   <li>the generic fallback ({@link ResolvedProfile#GENERIC});</li>
 *   <li>bundled profiles - anything in the {@code immersive_enchanting} namespace;</li>
 *   <li>datapack profiles with broad selectors (rarity, curse, treasure, all);</li>
 *   <li>datapack profiles with namespace selectors;</li>
 *   <li>datapack profiles naming exact enchantments.</li>
 * </ol>
 * Server configuration multipliers apply afterwards. Within one layer a higher {@code priority} wins; equal
 * priorities are ordered by profile id, and the lexically greater id wins (logged at debug level).
 *
 * <p>Resolution is cached per enchantment and rebuilt on every reload; nothing is evaluated per tick.
 */
public final class EnchantmentProfileManager extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "immersive_enchanting/enchantment_profiles";
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile Index current = new Index(List.of());

    public EnchantmentProfileManager() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        try {
            List<EnchantmentProfile> profiles = new ArrayList<>();
            files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                ResourceLocation id = entry.getKey();
                try {
                    profiles.add(EnchantmentProfile.parse(id, entry.getValue(),
                            message -> ImmersiveEnchanting.LOGGER.warn("Enchantment profile {}: {}", id, message)));
                } catch (RuntimeException e) {
                    ImmersiveEnchanting.LOGGER.error("Skipping invalid Immersive Enchanting enchantment profile {}: {}", id, e.getMessage());
                }
            });
            current = new Index(profiles);
            logSummary(profiles.size());
        } catch (RuntimeException e) {
            ImmersiveEnchanting.LOGGER.error("Immersive Enchanting could not rebuild enchantment profiles; keeping the previous set", e);
        }
    }

    private static void logSummary(int count) {
        int generic = 0;
        int profiled = 0;
        for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
            if (resolve(enchantment).isGeneric()) generic++;
            else profiled++;
        }
        ImmersiveEnchanting.debug("Loaded {} enchantment profiles, {} pattern sets. Resolved {} registered enchantments: {} generic, {} profiled.",
                count, PatternSetManager.count(), generic + profiled, generic, profiled);
    }

    public static ResolvedProfile resolve(Enchantment enchantment) {
        return current.resolve(enchantment);
    }

    /** The matching profiles in the order they are applied, for the explain command. */
    public static List<EnchantmentProfile> matching(Enchantment enchantment) {
        return current.matching(enchantment);
    }

    public static int count() {
        return current.profiles.size();
    }

    private static final class Index {
        private final List<EnchantmentProfile> profiles;
        private final Map<Enchantment, ResolvedProfile> cache = new ConcurrentHashMap<>();

        Index(List<EnchantmentProfile> profiles) {
            List<EnchantmentProfile> sorted = new ArrayList<>(profiles);
            sorted.sort(Comparator.comparing(EnchantmentProfile::layer)
                    .thenComparingInt(EnchantmentProfile::priority)
                    .thenComparing(p -> p.id().toString()));
            this.profiles = List.copyOf(sorted);
        }

        List<EnchantmentProfile> matching(Enchantment enchantment) {
            ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (id == null) return List.of();
            List<EnchantmentProfile> out = new ArrayList<>();
            for (EnchantmentProfile p : profiles) if (p.selector().matches(id, enchantment)) out.add(p);
            return out;
        }

        ResolvedProfile resolve(Enchantment enchantment) {
            return cache.computeIfAbsent(enchantment, e -> {
                ResolvedProfile resolved = ResolvedProfile.GENERIC;
                EnchantmentProfile previous = null;
                for (EnchantmentProfile p : matching(e)) {
                    if (previous != null && previous.layer() == p.layer() && previous.priority() == p.priority()) {
                        ImmersiveEnchanting.debug("profiles {} and {} tie at priority {} for {}; {} applies last", previous.id(), p.id(),
                                p.priority(), BuiltInRegistries.ENCHANTMENT.getKey(e), p.id());
                    }
                    resolved = resolved.with(p);
                    previous = p;
                }
                return resolved;
            });
        }
    }
}
