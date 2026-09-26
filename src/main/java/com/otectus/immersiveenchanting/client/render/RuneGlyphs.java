package com.otectus.immersiveenchanting.client.render;

/**
 * The four binding runes. Each lane has its own shape - triangle, diamond, circle, four-point star - so runes are
 * told apart without color, and anchors show the same shape in outline.
 */
public final class RuneGlyphs {
    private static final float[][] UNIT_X = new float[4][];
    private static final float[][] UNIT_Y = new float[4][];

    static {
        // I: triangle, point up
        UNIT_X[0] = new float[]{0f, 0.95f, -0.95f};
        UNIT_Y[0] = new float[]{-1.1f, 0.62f, 0.62f};
        // II: diamond
        UNIT_X[1] = new float[]{0f, 0.82f, 0f, -0.82f};
        UNIT_Y[1] = new float[]{-1.12f, 0f, 1.12f, 0f};
        // III: circle, as a 20-gon
        UNIT_X[2] = new float[20];
        UNIT_Y[2] = new float[20];
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            UNIT_X[2][i] = (float) Math.cos(a) * 0.92f;
            UNIT_Y[2][i] = (float) Math.sin(a) * 0.92f;
        }
        // IV: four-point star
        UNIT_X[3] = new float[8];
        UNIT_Y[3] = new float[8];
        for (int i = 0; i < 8; i++) {
            double a = -Math.PI / 2 + Math.PI * i / 4;
            float r = i % 2 == 0 ? 1.18f : 0.4f;
            UNIT_X[3][i] = (float) Math.cos(a) * r;
            UNIT_Y[3][i] = (float) Math.sin(a) * r;
        }
    }

    private static float[] xs(int lane, float cx, float r) {
        float[] u = UNIT_X[Math.floorMod(lane, 4)];
        float[] out = new float[u.length];
        for (int i = 0; i < u.length; i++) out[i] = cx + u[i] * r;
        return out;
    }

    private static float[] ys(int lane, float cy, float r) {
        float[] u = UNIT_Y[Math.floorMod(lane, 4)];
        float[] out = new float[u.length];
        for (int i = 0; i < u.length; i++) out[i] = cy + u[i] * r;
        return out;
    }

    /** A solid rune: body, edge and a dark core. */
    public static void rune(VectorBatch b, int lane, float cx, float cy, float r, int fill, int edge, float edgeWidth) {
        float[] x = xs(lane, cx, r), y = ys(lane, cy, r);
        b.polygon(x, y, fill);
        b.outline(x, y, edgeWidth, edge);
        b.disc(cx, cy + (lane == 0 ? r * 0.12f : 0), r * 0.18f, Palette.withAlpha(0x000000, ((fill >>> 24) * 110) / 255));
    }

    /** The outline of a rune, used for anchors. */
    public static void outline(VectorBatch b, int lane, float cx, float cy, float r, float width, int color) {
        b.outline(xs(lane, cx, r), ys(lane, cy, r), width, color);
    }

    public static void filled(VectorBatch b, int lane, float cx, float cy, float r, int color) {
        b.polygon(xs(lane, cx, r), ys(lane, cy, r), color);
    }

    private RuneGlyphs() {}
}
