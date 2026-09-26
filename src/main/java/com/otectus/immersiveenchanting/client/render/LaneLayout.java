package com.otectus.immersiveenchanting.client.render;

/**
 * Classic lanes: one clear vertical lane per anchor, left to right in key order; runes fall to a binding line.
 * Same patterns, timing and scoring as the radial layout.
 */
public final class LaneLayout implements RitualLayout {
    private final float cx, laneWidth, top, line, rune;
    private final int anchors;

    public LaneLayout(int width, int height, int anchors) {
        this.anchors = anchors;
        this.cx = width / 2f;
        this.laneWidth = Math.max(22f, Math.min(width * 0.085f, 46f));
        this.rune = laneWidth * 0.3f;
        // Title block above, key labels and the status line below.
        this.top = Math.max(64f, height * 0.12f);
        this.line = Math.max(top + 60f, height - 52f - rune * 2.4f - 8f);
    }

    private float laneX(int lane) {
        return cx + (lane - (anchors - 1) / 2f) * laneWidth;
    }

    @Override
    public float[] point(int lane, double p) {
        return new float[]{laneX(lane), (float) (top + (line - top) * p)};
    }

    @Override
    public float runeRadius() {
        return rune;
    }

    @Override
    public float[] keyLabel(int lane) {
        return new float[]{laneX(lane), line + rune * 2.4f};
    }

    /** Just above the anchor, inside its lane. */
    @Override
    public float[] judgementLabel(int lane) {
        return new float[]{laneX(lane), line - rune * 2.9f};
    }

    @Override
    public void backdrop(VectorBatch b, Palette.Theme theme, float stability, long nowMs, boolean countIn, double beatPhase, boolean reducedMotion) {
        float left = laneX(0) - laneWidth / 2, right = laneX(anchors - 1) + laneWidth / 2;
        b.rect(left - 3, top - 6, right + 3, line + rune * 1.8f, Palette.withAlpha(0x27241F, 0.84f));
        for (int lane = 0; lane < anchors; lane++) {
            float x = laneX(lane);
            b.rect(x - laneWidth / 2 + 1, top, x + laneWidth / 2 - 1, line + rune * 1.4f, Palette.withAlpha(0xFFFFFF, lane % 2 == 0 ? 0.035f : 0.055f));
        }
        for (int i = 0; i <= anchors; i++) {
            float x = cx + (i - anchors / 2f) * laneWidth;
            b.line(x, top, x, line + rune * 1.4f, 1f, Palette.withAlpha(theme.secondary(), 0.18f));
        }
        float pulse = countIn && !reducedMotion ? (float) (1 - beatPhase) : 0f;
        b.line(left, line, right, line, 2f + 2f * pulse, Palette.withAlpha(theme.secondary(), 0.55f + 0.35f * pulse));
        b.line(left, line, right, line, 6f, Palette.withAlpha(theme.primary(), 0.12f + 0.25f * stability));
    }

    @Override
    public void resonance(VectorBatch b, Palette.Theme theme, double resonance, float crackedAlpha) {
        float x = laneX(anchors - 1) + laneWidth / 2 + 10;
        float h = line - top;
        int segments = 24;
        float segH = h / segments;
        int lit = (int) Math.round(resonance * segments);
        for (int i = 0; i < segments; i++) {
            float y1 = line - i * segH, y0 = y1 - segH + 1.5f;
            int color = i < lit ? Palette.withAlpha(theme.primary(), 0.85f)
                    : (i < lit + 2 && crackedAlpha > 0 ? Palette.withAlpha(0xFFFFFF, crackedAlpha * 0.6f) : Palette.withAlpha(0x4F473C, 0.7f));
            b.rect(x, y0, x + 5, y1, color);
        }
    }

    @Override
    public void chordLink(VectorBatch b, int laneA, int laneB, double p, float width, int color) {
        float[] a = point(laneA, p), c = point(laneB, p);
        b.line(a[0], a[1], c[0], c[1], width, color);
    }

    @Override
    public float[] center() {
        return new float[]{cx, (top + line) / 2f};
    }

    @Override
    public float top() {
        return top - 4;
    }

    @Override
    public float bottom() {
        return line + rune * 2.4f + 8;
    }

    @Override
    public boolean radial() {
        return false;
    }
}
