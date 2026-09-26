package com.otectus.immersiveenchanting.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Presentation and accessibility settings. Only {@link #TIMING_ASSIST} affects scoring, and the server caps it.
 * Binding keys are ordinary key mappings (Options > Controls > Immersive Enchanting).
 */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public enum RitualLayout { RADIAL, CLASSIC_LANES }

    public static final ForgeConfigSpec.EnumValue<RitualLayout> RITUAL_LAYOUT;
    public static final ForgeConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.DoubleValue BACKGROUND_DIM;
    public static final ForgeConfigSpec.BooleanValue SHOW_NUMERIC_SCORE;
    public static final ForgeConfigSpec.BooleanValue SHOW_TIMING_LABELS;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST_RUNES;
    public static final ForgeConfigSpec.BooleanValue COLORBLIND_SAFE_MODE;
    public static final ForgeConfigSpec.DoubleValue RITUAL_SOUND_VOLUME;
    public static final ForgeConfigSpec.BooleanValue METRONOME_CUE;
    public static final ForgeConfigSpec.DoubleValue APPROACH_TIME_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue TIMING_ASSIST;
    public static final ForgeConfigSpec.BooleanValue SHOW_OFFER_COMPLEXITY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Ritual presentation. None of these change scoring except timingAssist.").push("ritual");
        RITUAL_LAYOUT = b.comment("RADIAL: runes travel from the sigil to four anchors on a binding ring.",
                        "CLASSIC_LANES: runes fall down four vertical lanes to a binding line. Same patterns and scoring.")
                .defineEnum("ritualLayout", RitualLayout.RADIAL);
        APPROACH_TIME_MULTIPLIER = b.comment("How long each rune is visible before it must be bound. Higher gives more reading time; timing is unchanged.")
                .defineInRange("approachTimeMultiplier", 1.0, 0.5, 2.0);
        BACKGROUND_DIM = b.comment("How strongly the enchanting screen is dimmed behind the ritual (0 = not at all, 1 = black).")
                .defineInRange("backgroundDim", 0.55, 0.0, 1.0);
        SHOW_NUMERIC_SCORE = b.define("showNumericScore", true);
        SHOW_TIMING_LABELS = b.comment("Show Perfect/Good/Graze/Miss beside the anchors.").define("showTimingLabels", true);
        SHOW_OFFER_COMPLEXITY = b.comment("Show the complexity gauge beside enchanting offers when the server allows previews.")
                .define("showOfferComplexity", true);
        b.pop();

        b.comment("Accessibility").push("accessibility");
        REDUCED_MOTION = b.comment("Still background glyphs, no pulses, no shard particles.").define("reducedMotion", false);
        SCREEN_SHAKE = b.comment("A very small jolt of the ritual circle on a miss.").define("screenShake", false);
        HIGH_CONTRAST_RUNES = b.comment("White runes with dark outlines and brighter anchors.").define("highContrastRunes", false);
        COLORBLIND_SAFE_MODE = b.comment("Use the Okabe-Ito palette for the four runes. Runes always differ by shape as well as color.")
                .define("colorblindSafeMode", true);
        RITUAL_SOUND_VOLUME = b.defineInRange("ritualSoundVolume", 1.0, 0.0, 1.0);
        METRONOME_CUE = b.comment("A soft tick on every beat of the ritual, not just the count-in.").define("metronomeCue", false);
        TIMING_ASSIST = b.comment("Widen your timing windows by up to this factor. The server caps it (maxClientTimingAssist, default 1.25).")
                .defineInRange("timingAssist", 1.0, 1.0, 1.5);
        b.pop();
        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static RitualLayout layout() {
        return SPEC.isLoaded() ? RITUAL_LAYOUT.get() : RITUAL_LAYOUT.getDefault();
    }

    private ClientConfig() {}
}
