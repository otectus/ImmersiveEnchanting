package com.otectus.immersiveenchanting.ritual;

import java.util.Locale;

/** Presentation tiers over the 0..100 complexity score. */
public enum DifficultyTier {
    INITIATE(0),
    APPRENTICE(20),
    ADEPT(35),
    EXPERT(50),
    MASTER(65),
    ARCANE(80);

    private final double lowScore;

    DifficultyTier(double lowScore) {
        this.lowScore = lowScore;
    }

    public double lowScore() {
        return lowScore;
    }

    /** Exclusive upper bound, or 100 (inclusive) for the last tier. */
    public double highScore() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1].lowScore : 100.0;
    }

    /** The highest score still inside this tier. */
    public double maxScoreInTier() {
        return ordinal() + 1 < values().length ? Math.nextDown(highScore()) : 100.0;
    }

    /** Position of {@code score} within this tier, 0..1. */
    public double position(double score) {
        double span = highScore() - lowScore;
        return Math.max(0.0, Math.min(1.0, (score - lowScore) / span));
    }

    public int index() {
        return ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DifficultyTier fromScore(double score) {
        DifficultyTier result = INITIATE;
        for (DifficultyTier tier : values()) if (score >= tier.lowScore) result = tier;
        return result;
    }

    public static DifficultyTier byIndex(int index) {
        return values()[Math.max(0, Math.min(values().length - 1, index))];
    }
}
