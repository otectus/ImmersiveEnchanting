package com.otectus.immersiveenchanting.ritual;

/**
 * SplitMix64. Client and server must generate byte-identical patterns from the same seed, so ritual code never uses
 * {@link java.util.Random} or Minecraft's random sources, whose algorithms are not part of this mod's protocol.
 * Integer arithmetic only; {@link #nextDouble()} is exact.
 */
public final class RitualRandom {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private long state;

    public RitualRandom(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        state += GOLDEN;
        return finish(state);
    }

    /** Uniform in [0, bound). The modulo bias is below 2^-40 for every bound used here. */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive: " + bound);
        return (int) ((nextLong() >>> 1) % bound);
    }

    /** Uniform in [min, max], inclusive. */
    public int range(int min, int max) {
        return max <= min ? min : min + nextInt(max - min + 1);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean chance(double probability) {
        return nextDouble() < probability;
    }

    /** Folds one more value into a running hash. Used to derive pattern seeds from several ingredients. */
    public static long mix(long hash, long value) {
        return finish(hash ^ finish(value + GOLDEN));
    }

    public static long mix(long hash, String value) {
        long h = hash;
        for (int i = 0; i < value.length(); i++) h = mix(h, value.charAt(i));
        return mix(h, value.length());
    }

    private static long finish(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
