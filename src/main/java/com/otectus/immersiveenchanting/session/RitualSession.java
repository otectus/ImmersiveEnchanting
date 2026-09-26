package com.otectus.immersiveenchanting.session;

import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.api.RitualSessionView;
import com.otectus.immersiveenchanting.enchanting.OfferPlanner;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One player's ritual on the server. Holds the immutable plan and pattern captured at start (so a datapack reload
 * mid-ritual cannot change what is being scored) and the inputs received so far. Server thread only.
 */
public final class RitualSession implements RitualSessionView {
    /** Round trip allowance: a cancel sent during the count-in still arrives within this much of the first rune. */
    static final int LATENCY_ALLOWANCE_MS = 500;

    private final UUID id = UUID.randomUUID();
    private final ServerPlayer player;
    private final UUID playerId;
    private final EnchantingContextAdapter adapter;
    private final AbstractContainerMenu menu;
    private final OfferPlanner.Plan plan;
    private final RitualPattern pattern;
    private final long startedAtMs;
    private final long freeUntilMs;
    private final long expiresAfterMs;
    private final int maxInputs;
    private final List<InputRecord> inputs = new ArrayList<>();
    private final boolean[] down = new boolean[RitualParams.MAX_ANCHORS];
    private int lastInputMs;
    int lastSequence = -1;
    RitualState state = RitualState.CREATED;

    RitualSession(ServerPlayer player, EnchantingContextAdapter adapter, AbstractContainerMenu menu, OfferPlanner.Plan plan,
                  RitualPattern pattern, int graceMs) {
        this.player = player;
        this.playerId = player.getUUID();
        this.adapter = adapter;
        this.menu = menu;
        this.plan = plan;
        this.pattern = pattern;
        this.startedAtMs = SessionClock.now();
        this.freeUntilMs = pattern.countInMs() + LATENCY_ALLOWANCE_MS;
        this.expiresAfterMs = (long) pattern.endMs() + plan.params().grazeMs() + graceMs + 2_000L;
        this.maxInputs = Math.min(4096, pattern.events().size() * 8 + 64);
    }

    public long elapsedMs() {
        return SessionClock.now() - startedAtMs;
    }

    /** False while cancelling is still free (before the first rune is due, plus the latency allowance). */
    public boolean costsApply() {
        return elapsedMs() >= freeUntilMs;
    }

    boolean expired() {
        return elapsedMs() > expiresAfterMs;
    }

    public RitualState state() {
        if (state == RitualState.COUNTDOWN && elapsedMs() >= pattern.countInMs()) return RitualState.PLAYING;
        return state;
    }

    /**
     * Appends a chunk of inputs after checking it: times non-decreasing, not negative, not ahead of the server clock
     * by more than {@code latestAllowedMs}; lanes within the ritual's anchors; presses and releases alternating per
     * lane; total count bounded.
     *
     * @return why the chunk was refused, or null if accepted
     */
    AbortReason accept(List<InputRecord> chunk, long latestAllowedMs) {
        if (inputs.size() + chunk.size() > maxInputs) return AbortReason.MALFORMED_INPUT;
        int previous = lastInputMs;
        boolean[] lanes = down.clone();
        for (InputRecord r : chunk) {
            if (r.timeMs() < 0 || r.timeMs() < previous) return AbortReason.MALFORMED_INPUT;
            if (r.timeMs() > latestAllowedMs) return AbortReason.TIMING_INVALID;
            if (r.lane() < 0 || r.lane() >= pattern.anchors()) return AbortReason.MALFORMED_INPUT;
            if (r.pressed() == lanes[r.lane()]) return AbortReason.MALFORMED_INPUT;
            lanes[r.lane()] = r.pressed();
            previous = r.timeMs();
        }
        inputs.addAll(chunk);
        System.arraycopy(lanes, 0, down, 0, lanes.length);
        lastInputMs = previous;
        return null;
    }

    List<InputRecord> inputs() {
        return inputs;
    }

    public int inputCount() {
        return inputs.size();
    }

    /** The player object the ritual was started by; a respawned player is a different object. */
    ServerPlayer player() {
        return player;
    }

    public EnchantingContextAdapter adapter() {
        return adapter;
    }

    public AbstractContainerMenu menu() {
        return menu;
    }

    public OfferPlanner.Plan plan() {
        return plan;
    }

    public RitualPattern pattern() {
        return pattern;
    }

    @Override
    public UUID sessionId() {
        return id;
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public ResourceLocation adapterId() {
        return adapter.id();
    }

    @Override
    public int containerId() {
        return plan.snapshot().containerId();
    }

    @Override
    public int offerIndex() {
        return plan.snapshot().offerIndex();
    }

    @Override
    public OfferSnapshot snapshot() {
        return plan.snapshot();
    }

    @Override
    public double complexity() {
        return plan.difficulty().score();
    }

    @Override
    public int tier() {
        return plan.tier();
    }

    @Override
    public long patternSeed() {
        return plan.seed();
    }

    @Override
    public ResourceLocation patternSetId() {
        return plan.patternSetId();
    }
}
