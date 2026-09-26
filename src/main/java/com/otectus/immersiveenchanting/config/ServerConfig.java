package com.otectus.immersiveenchanting.config;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.ritual.OutcomeRules;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * Gameplay configuration. SERVER configs are per world and synced to clients on join, so the client reads the same
 * switches (enabled, item/book rituals, creative bypass, preview) when deciding whether to intercept a click. Every
 * read goes through a helper that falls back to the default while no world is loaded.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public enum FailureCostMode { FULL, LAPIS_ONLY, NONE }

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue RITUALS_FOR_ITEMS;
    public static final ForgeConfigSpec.BooleanValue RITUALS_FOR_BOOKS;
    public static final ForgeConfigSpec.BooleanValue PREVIEW_RITUAL_DIFFICULTY;

    public static final ForgeConfigSpec.DoubleValue GLOBAL_DIFFICULTY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TEMPO_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue EVENT_COUNT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TIMING_WINDOW_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_CLIENT_TIMING_ASSIST;
    public static final ForgeConfigSpec.IntValue VANILLA_REFERENCE_MAX_COST;

    public static final ForgeConfigSpec.DoubleValue PERFECT_SCORE;
    public static final ForgeConfigSpec.DoubleValue FULL_REWARD_SCORE;
    public static final ForgeConfigSpec.DoubleValue FRAYED_SCORE;
    public static final ForgeConfigSpec.DoubleValue WEAK_SCORE;
    public static final ForgeConfigSpec.DoubleValue FAILURE_SCORE;
    public static final ForgeConfigSpec.DoubleValue FRAYED_RETENTION;
    public static final ForgeConfigSpec.DoubleValue WEAK_RETENTION;

    public static final ForgeConfigSpec.EnumValue<FailureCostMode> FAILURE_COST_MODE;
    public static final ForgeConfigSpec.BooleanValue ADVANCE_SEED_ON_FAILURE;

    public static final ForgeConfigSpec.IntValue MAX_PATTERN_EVENTS;
    public static final ForgeConfigSpec.IntValue MAX_RITUAL_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue SESSION_GRACE_SECONDS;

    public static final ForgeConfigSpec.BooleanValue CREATIVE_BYPASS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SERVER_BYPASS_PERMISSION;

    public static final ForgeConfigSpec.BooleanValue SERVER_GUARD_VANILLA_ENCHANT_BUTTON;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VANILLA_COMPATIBLE_MENUS;
    public static final ForgeConfigSpec.IntValue APOTHEOSIS_REFERENCE_MAX_COST;

    public static final ForgeConfigSpec.BooleanValue VERBOSE_LOGGING;

    private static volatile OutcomeRules outcomeRules = OutcomeRules.DEFAULT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("general");
        ENABLED = b.comment("Master switch. When false the enchanting table behaves exactly as in vanilla.")
                .define("enabled", true);
        RITUALS_FOR_ITEMS = b.comment("Require a ritual when enchanting items.").define("ritualsForItems", true);
        RITUALS_FOR_BOOKS = b.comment("Require a ritual when enchanting books.").define("ritualsForBooks", true);
        PREVIEW_RITUAL_DIFFICULTY = b.comment("Show each offer's ritual complexity (0-5) at the table. Reveals difficulty only,",
                        "never hidden enchantments, though a harder ritual hints that an offer holds more magic.")
                .define("previewRitualDifficulty", true);
        b.pop();

        b.comment("Difficulty. Event count, tempo and timing windows scale independently.").push("difficulty");
        GLOBAL_DIFFICULTY_MULTIPLIER = b.comment("Multiplies every ritual's complexity score before tiers are assigned.")
                .defineInRange("globalDifficultyMultiplier", 1.0, 0.0, 5.0);
        TEMPO_MULTIPLIER = b.comment("Multiplies ritual tempo (BPM).").defineInRange("tempoMultiplier", 1.0, 0.5, 2.0);
        EVENT_COUNT_MULTIPLIER = b.comment("Multiplies the number of runes per ritual.").defineInRange("eventCountMultiplier", 1.0, 0.25, 3.0);
        TIMING_WINDOW_MULTIPLIER = b.comment("Multiplies the Perfect/Good/Graze timing windows. Above 1 is more forgiving.")
                .defineInRange("timingWindowMultiplier", 1.0, 0.5, 2.0);
        MAX_CLIENT_TIMING_ASSIST = b.comment("Largest timing-window widening a player may request through their accessibility setting.",
                        "Set to 1.0 to give every player identical windows.")
                .defineInRange("maxClientTimingAssist", 1.25, 1.0, 1.5);
        VANILLA_REFERENCE_MAX_COST = b.comment("Displayed level cost treated as full power for a vanilla table (30 with 15 bookshelves).")
                .defineInRange("vanillaReferenceMaxCost", 30, 1, 1000);
        b.pop();

        b.comment("Outcome bands. Thresholds must descend: perfect >= fullReward > frayed > weak > failure.",
                "Invalid settings are reported in the log and the defaults are used instead.").push("outcome");
        PERFECT_SCORE = b.comment("Perfect Binding: the full result plus prestige effects. Never more than the rolled enchantments.")
                .defineInRange("perfectScore", 90.0, 0.0, 100.0);
        FULL_REWARD_SCORE = b.comment("Stable Binding: the full rolled result.").defineInRange("fullRewardScore", 80.0, 0.0, 100.0);
        FRAYED_SCORE = b.comment("Frayed Binding: keeps frayedRetention of the roll's arcane value.").defineInRange("frayedScore", 65.0, 0.0, 100.0);
        WEAK_SCORE = b.comment("Weak Binding: keeps weakRetention of the roll's arcane value.").defineInRange("weakScore", 50.0, 0.0, 100.0);
        FAILURE_SCORE = b.comment("Failed Binding at or above this score, Shattered Binding below. Neither binds anything.")
                .defineInRange("failureScore", 35.0, 0.0, 100.0);
        FRAYED_RETENTION = b.defineInRange("frayedRetention", 0.75, 0.01, 1.0);
        WEAK_RETENTION = b.defineInRange("weakRetention", 0.45, 0.01, 1.0);
        b.pop();

        b.comment("What a failed or abandoned ritual costs. A successful ritual always costs the normal price.").push("costs");
        FAILURE_COST_MODE = b.comment("FULL: levels and lapis. LAPIS_ONLY: lapis only. NONE: free.",
                        "Cancelling before the first rune reaches its anchor is always free.")
                .defineEnum("failureCostMode", FailureCostMode.FULL);
        ADVANCE_SEED_ON_FAILURE = b.comment("A failed ritual rerolls the player's enchanting offers, as a successful one does.")
                .define("advanceSeedOnFailure", true);
        b.pop();

        b.push("limits");
        MAX_PATTERN_EVENTS = b.defineInRange("maxPatternEvents", 96, 8, 128);
        MAX_RITUAL_DURATION_SECONDS = b.defineInRange("maxRitualDurationSeconds", 25, 5, 60);
        SESSION_GRACE_SECONDS = b.comment("Extra time after a ritual's last rune before an unfinished session expires as a failure.")
                .defineInRange("sessionGraceSeconds", 5, 1, 60);
        b.pop();

        b.push("bypass");
        CREATIVE_BYPASS = b.comment("Creative-mode players enchant instantly without a ritual.").define("creativeBypass", false);
        ALLOW_SERVER_BYPASS_PERMISSION = b.comment("Players granted the permission node immersive_enchanting.bypass_ritual enchant instantly.")
                .define("allowServerBypassPermission", true);
        b.pop();

        b.comment("Compatibility").push("compat");
        SERVER_GUARD_VANILLA_ENCHANT_BUTTON = b.comment("Reject direct enchant-button packets (from modified or incompatible clients) while a ritual is required.")
                .define("serverGuardVanillaEnchantButton", true);
        VANILLA_COMPATIBLE_MENUS = b.comment("Fully qualified class names of enchanting menus that subclass the vanilla EnchantmentMenu and keep its",
                        "offer selection and enchant button logic. They are handled exactly like the vanilla table.")
                .defineListAllowEmpty("vanillaCompatibleMenus", List.of(), o -> o instanceof String s && !s.isBlank());
        APOTHEOSIS_REFERENCE_MAX_COST = b.comment("Apotheosis tables reach far higher levels than vanilla; this displayed level counts as full power.",
                        "Offers above it still add difficulty, up to twice this value.")
                .defineInRange("apotheosisReferenceMaxCost", 50, 1, 1000);
        b.pop();

        b.push("debug");
        VERBOSE_LOGGING = b.comment("Log adapter choice, profile resolution, complexity and session transitions at INFO instead of DEBUG.")
                .define("verboseLogging", false);
        b.pop();

        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static FailureCostMode failureCostMode() {
        return SPEC.isLoaded() ? FAILURE_COST_MODE.get() : FAILURE_COST_MODE.getDefault();
    }

    public static List<? extends String> vanillaCompatibleMenus() {
        return SPEC.isLoaded() ? VANILLA_COMPATIBLE_MENUS.get() : List.of();
    }

    public static boolean verboseLogging() {
        return get(VERBOSE_LOGGING);
    }

    /** Validated outcome thresholds; the defaults if the configured ones are inconsistent. */
    public static OutcomeRules outcomeRules() {
        return SPEC.isLoaded() ? outcomeRules : OutcomeRules.DEFAULT;
    }

    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) return;
        OutcomeRules rules = new OutcomeRules(PERFECT_SCORE.get(), FULL_REWARD_SCORE.get(), FRAYED_SCORE.get(), WEAK_SCORE.get(),
                FAILURE_SCORE.get(), FRAYED_RETENTION.get(), WEAK_RETENTION.get());
        if (rules.isValid()) {
            outcomeRules = rules;
        } else {
            ImmersiveEnchanting.LOGGER.error("Immersive Enchanting outcome thresholds are inconsistent ({}); they must descend "
                    + "perfect >= fullReward > frayed > weak > failure, with 0 < weakRetention <= frayedRetention <= 1. Using the defaults.", rules);
            outcomeRules = OutcomeRules.DEFAULT;
        }
    }

    private ServerConfig() {}
}
