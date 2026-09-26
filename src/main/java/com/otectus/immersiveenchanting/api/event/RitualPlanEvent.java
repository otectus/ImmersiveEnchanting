package com.otectus.immersiveenchanting.api.event;

import com.otectus.immersiveenchanting.api.OfferSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * Posted on the server thread when a ritual is being planned, after the offer is captured and before the complexity
 * score is computed. Listeners may scale or offset the score and choose another pattern set. The snapshot is
 * read-only; the plan itself cannot be changed here. Also posted, with {@link #isPreview()} true, when offer
 * previews are computed, so previews and real rituals agree.
 */
public class RitualPlanEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation adapterId;
    private final OfferSnapshot snapshot;
    private final boolean preview;
    private double difficultyMultiplier = 1.0;
    private double difficultyOffset = 0.0;
    @Nullable
    private ResourceLocation patternSetOverride;

    public RitualPlanEvent(ServerPlayer player, ResourceLocation adapterId, OfferSnapshot snapshot, boolean preview) {
        this.player = player;
        this.adapterId = adapterId;
        this.snapshot = snapshot;
        this.preview = preview;
    }

    /** True when only the offer preview is being computed; no ritual is starting. */
    public boolean isPreview() {
        return preview;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getAdapterId() {
        return adapterId;
    }

    public OfferSnapshot getSnapshot() {
        return snapshot;
    }

    public double getDifficultyMultiplier() {
        return difficultyMultiplier;
    }

    /** Multiplies the complexity score (0..10). */
    public void setDifficultyMultiplier(double multiplier) {
        this.difficultyMultiplier = Double.isFinite(multiplier) ? Math.max(0.0, Math.min(10.0, multiplier)) : 1.0;
    }

    public double getDifficultyOffset() {
        return difficultyOffset;
    }

    /** Added to the complexity score after multipliers (-100..100). */
    public void setDifficultyOffset(double offset) {
        this.difficultyOffset = Double.isFinite(offset) ? Math.max(-100.0, Math.min(100.0, offset)) : 0.0;
    }

    @Nullable
    public ResourceLocation getPatternSetOverride() {
        return patternSetOverride;
    }

    /** A pattern set id to use instead of the profiles' choice; unknown ids fall back to the default set. */
    public void setPatternSetOverride(@Nullable ResourceLocation patternSet) {
        this.patternSetOverride = patternSet;
    }
}
