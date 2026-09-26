package com.otectus.immersiveenchanting.ritual;

import java.util.Locale;

/** Timing judgements in order of quality, with their normalized accuracy value. */
public enum Judgement {
    PERFECT(1.00),
    GOOD(0.78),
    GRAZE(0.45),
    MISS(0.00);

    private final double value;

    Judgement(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }

    public boolean isHit() {
        return this != MISS;
    }

    public static Judgement worse(Judgement a, Judgement b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
