package com.otectus.immersiveenchanting.ritual;

/**
 * Score thresholds and retained budgets. A score at or above a threshold reaches that band.
 *
 * @param frayedRetention fraction of the plan's arcane value a Frayed binding keeps
 * @param weakRetention   fraction a Weak binding keeps
 */
public record OutcomeRules(double perfectScore, double fullRewardScore, double frayedScore, double weakScore,
                           double failureScore, double frayedRetention, double weakRetention) {

    public static final OutcomeRules DEFAULT = new OutcomeRules(90, 80, 65, 50, 35, 0.75, 0.45);

    /** True if thresholds strictly descend within 0..100 and retentions descend within 0..1. */
    public boolean isValid() {
        return perfectScore <= 100 && perfectScore >= fullRewardScore && fullRewardScore > frayedScore
                && frayedScore > weakScore && weakScore > failureScore && failureScore >= 0
                && frayedRetention > 0 && frayedRetention <= 1 && weakRetention > 0 && weakRetention <= frayedRetention;
    }

    public OutcomeBand band(double score) {
        if (score >= perfectScore) return OutcomeBand.PERFECT;
        if (score >= fullRewardScore) return OutcomeBand.STABLE;
        if (score >= frayedScore) return OutcomeBand.FRAYED;
        if (score >= weakScore) return OutcomeBand.WEAK;
        if (score >= failureScore) return OutcomeBand.FAILED;
        return OutcomeBand.SHATTERED;
    }

    public double retention(OutcomeBand band) {
        return switch (band) {
            case PERFECT, STABLE -> 1.0;
            case FRAYED -> frayedRetention;
            case WEAK -> weakRetention;
            case FAILED, SHATTERED -> 0.0;
        };
    }
}
