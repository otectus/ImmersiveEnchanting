package com.otectus.immersiveenchanting.client.render;

/**
 * Where runes travel. A rune moves along its lane's path from {@code p = 0} (appears) to {@code p = 1} (reaches its
 * anchor at its binding time); values above 1 continue past the anchor while the rune can still be bound late.
 * Everything is computed from the current screen size each frame, so GUI scale and window size never misalign it.
 */
public interface RitualLayout {
    float[] point(int lane, double p);

    default float[] anchor(int lane) {
        return point(lane, 1.0);
    }

    float runeRadius();

    /** Where the player's key binding is written for a lane. */
    float[] keyLabel(int lane);

    /** Where timing judgements appear for a lane: beside the anchor, never over the play field's centre. */
    float[] judgementLabel(int lane);

    /** Background: the sigil, rings and spokes, or the lanes and binding line. */
    void backdrop(VectorBatch b, Palette.Theme theme, float stability, long nowMs, boolean countIn, double beatPhase, boolean reducedMotion);

    /** The resonance meter, 0..1. {@code crackedAlpha} flashes fractured segments after a miss. */
    void resonance(VectorBatch b, Palette.Theme theme, double resonance, float crackedAlpha);

    /** Links the two runes of a chord at progress {@code p}. */
    void chordLink(VectorBatch b, int laneA, int laneB, double p, float width, int color);

    /** Centre of the play field, for count-in and status text. */
    float[] center();

    /** Top edge of the play field, for the title block. */
    float top();

    /** Bottom edge of the play field, for the combo and score line. */
    float bottom();

    boolean radial();
}
