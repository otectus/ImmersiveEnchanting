package com.otectus.immersiveenchanting.client.render;

/**
 * The binding circle. Anchors sit on a ring around a central sigil; runes rise out of the sigil along a spoke to their
 * anchor. Four anchors take the arrow-key arrangement of rhythm games (I west, II south, III north, IV east); three
 * anchors arch over the top (I west, II north, III east), so key order always reads left to right.
 */
public final class RadialLayout implements RitualLayout {
    private final float cx, cy, radius, inner, rune;
    private final float[] dx, dy;

    /** Space kept free above and below the circle for the title block and the status line. */
    private static final float HUD_TOP = 64f, HUD_BOTTOM = 52f;
    /** The outermost ring (the drifting glyphs) sits at this multiple of the anchor radius. */
    private static final float OUTER = 1.3f;

    public RadialLayout(int width, int height, int anchors) {
        float available = Math.max(80f, height - HUD_TOP - HUD_BOTTOM);
        this.cx = width / 2f;
        this.cy = HUD_TOP + available / 2f;
        this.radius = Math.min(width * 0.3f, available / 2f / OUTER);
        this.inner = radius * 0.2f;
        this.rune = Math.max(5f, radius * 0.1f);
        float[][] dirs = anchors >= 4
                ? new float[][]{{-1, 0}, {0, 1}, {0, -1}, {1, 0}}
                : new float[][]{{-1, 0}, {0, -1}, {1, 0}};
        dx = new float[dirs.length];
        dy = new float[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            dx[i] = dirs[i][0];
            dy[i] = dirs[i][1];
        }
    }

    private float angle(int lane) {
        return (float) Math.atan2(dy[lane], dx[lane]);
    }

    @Override
    public float[] point(int lane, double p) {
        float r = (float) (inner + (radius - inner) * p);
        return new float[]{cx + dx[lane] * r, cy + dy[lane] * r};
    }

    @Override
    public float runeRadius() {
        return rune;
    }

    /** Outside the ring for side anchors; beside the anchor, opposite its judgement label, for top and bottom ones. */
    @Override
    public float[] keyLabel(int lane) {
        if (Math.abs(dx[lane]) > 0.5f) {
            float r = radius + rune * 2.6f;
            return new float[]{cx + dx[lane] * r, cy};
        }
        float[] a = anchor(lane);
        return new float[]{a[0] + dy[lane] * rune * 2.8f, a[1]};
    }

    @Override
    public float[] judgementLabel(int lane) {
        float[] a = anchor(lane);
        // Perpendicular to the spoke, beside the anchor.
        return new float[]{a[0] - dy[lane] * rune * 2.6f, a[1] + dx[lane] * rune * 2.6f - 4};
    }

    @Override
    public void backdrop(VectorBatch b, Palette.Theme theme, float stability, long nowMs, boolean countIn, double beatPhase, boolean reducedMotion) {
        float spin = reducedMotion ? 0 : nowMs / 9000f;
        // Soft disc behind the circle keeps runes readable on busy screens.
        b.disc(cx, cy, radius * 1.18f, Palette.withAlpha(0x27241F, 0.72f));
        b.circle(cx, cy, radius, 1.5f, Palette.withAlpha(theme.secondary(), 0.45f));
        b.circle(cx, cy, radius * 1.075f, 0.8f, Palette.withAlpha(theme.primary(), 0.22f));
        for (int lane = 0; lane < dx.length; lane++) {
            float[] s = point(lane, 0), e = point(lane, 1);
            b.line(s[0], s[1], e[0], e[1], rune * 1.3f, Palette.withAlpha(0xFFFFFF, 0.04f));
            b.line(s[0], s[1], e[0], e[1], 1f, Palette.withAlpha(theme.secondary(), 0.14f));
        }
        // Central sigil: counter-rotating tick rings, completing as the ritual stabilises.
        int ticks = 12;
        for (int i = 0; i < ticks; i++) {
            float a = (float) (spin * Math.PI * 2 + Math.PI * 2 * i / ticks);
            float r0 = inner * 0.55f, r1 = inner * 0.95f;
            float alpha = 0.18f + 0.5f * stability * ((i % 3 == 0) ? 1f : 0.6f);
            b.line(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0, cx + (float) Math.cos(a) * r1,
                    cy + (float) Math.sin(a) * r1, 1.2f, Palette.withAlpha(theme.primary(), alpha));
        }
        b.circle(cx, cy, inner, 1.2f, Palette.withAlpha(theme.primary(), 0.6f));
        float innerSpin = -spin * 1.7f;
        for (int i = 0; i < 3; i++) {
            float a = (float) (innerSpin * Math.PI * 2 + Math.PI * 2 * i / 3);
            b.ring(cx, cy, inner * 0.3f, inner * 0.42f, a, a + 1.4f, Palette.withAlpha(theme.secondary(), 0.35f + 0.4f * stability), 10);
        }
        if (countIn) {
            float pulse = reducedMotion ? 0.5f : (float) (1 - beatPhase);
            b.circle(cx, cy, inner * (1.2f + 1.6f * (1 - pulse)), 2f, Palette.withAlpha(theme.secondary(), 0.15f + 0.6f * pulse));
        }
    }

    @Override
    public void resonance(VectorBatch b, Palette.Theme theme, double resonance, float crackedAlpha) {
        int segments = 48;
        int lit = (int) Math.round(resonance * segments);
        float rIn = radius * 1.14f, rOut = radius * 1.14f + Math.max(2f, rune * 0.35f);
        float gap = 0.018f;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (-Math.PI / 2 + Math.PI * 2 * i / segments) + gap;
            float a1 = (float) (-Math.PI / 2 + Math.PI * 2 * (i + 1) / segments) - gap;
            int color;
            if (i < lit) color = Palette.withAlpha(theme.primary(), 0.85f);
            else if (i < lit + 4 && crackedAlpha > 0) color = Palette.withAlpha(0xFFFFFF, crackedAlpha * 0.6f);
            else color = Palette.withAlpha(0x4F473C, 0.7f);
            b.ring(cx, cy, rIn, rOut, a0, a1, color, 3);
        }
    }

    @Override
    public void chordLink(VectorBatch b, int laneA, int laneB, double p, float width, int color) {
        float r = (float) (inner + (radius - inner) * p);
        float a0 = angle(laneA), a1 = angle(laneB);
        float delta = a1 - a0;
        while (delta > Math.PI) delta -= (float) (Math.PI * 2);
        while (delta <= -Math.PI) delta += (float) (Math.PI * 2);
        float start, sweep;
        if (Math.abs(Math.abs(delta) - Math.PI) < 0.01f) {
            // Opposite anchors: west-east arcs over the top, north-south arcs through the east.
            float mid = Math.abs(dy[laneA]) < 0.5f ? (float) (-Math.PI / 2) : 0f;
            start = mid - (float) (Math.PI / 2);
            sweep = (float) Math.PI;
        } else if (delta >= 0) {
            start = a0;
            sweep = delta;
        } else {
            start = a1;
            sweep = -delta;
        }
        b.ring(cx, cy, r - width / 2, r + width / 2, start, start + sweep, color, 24);
    }

    @Override
    public float[] center() {
        return new float[]{cx, cy};
    }

    @Override
    public float top() {
        return cy - radius * OUTER;
    }

    @Override
    public float bottom() {
        return cy + radius * OUTER;
    }

    @Override
    public boolean radial() {
        return true;
    }
}
