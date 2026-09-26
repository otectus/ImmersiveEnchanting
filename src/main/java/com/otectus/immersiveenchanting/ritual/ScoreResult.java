package com.otectus.immersiveenchanting.ritual;

/**
 * A finished ritual's score. {@code finalScore} = accuracy x 0.75 + combo x 0.15 + stability x 0.10, all 0..100.
 */
public record ScoreResult(double finalScore, double accuracy, double comboScore, double stability, int total,
                          int perfect, int good, int graze, int miss, int strays, int brokenHolds, int longestCombo) {

    public static final double ACCURACY_WEIGHT = 0.75;
    public static final double COMBO_WEIGHT = 0.15;
    public static final double STABILITY_WEIGHT = 0.10;

    static ScoreResult of(int total, int perfect, int good, int graze, int miss, int strays, int brokenHolds, int longestCombo) {
        int n = Math.max(1, total);
        double accuracy = (perfect * Judgement.PERFECT.value() + good * Judgement.GOOD.value()
                + graze * Judgement.GRAZE.value()) / n * 100.0;
        double combo = (double) longestCombo / n * 100.0;
        double missRate = (double) miss / n;
        double brokenRate = (double) brokenHolds / n;
        double strayRate = Math.min(1.0, (double) strays / n);
        double stability = Math.max(0.0, 100.0 - missRate * 160.0 - brokenRate * 80.0 - strayRate * 30.0);
        double score = accuracy * ACCURACY_WEIGHT + combo * COMBO_WEIGHT + stability * STABILITY_WEIGHT;
        score = Math.max(0.0, Math.min(100.0, score));
        return new ScoreResult(score, accuracy, combo, stability, total, perfect, good, graze, miss, strays, brokenHolds, longestCombo);
    }

    /** The score shown to players: floored, so a displayed 80 always means the 80 band was reached. */
    public int displayScore() {
        return (int) Math.floor(finalScore);
    }
}
