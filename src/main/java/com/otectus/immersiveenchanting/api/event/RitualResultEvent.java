package com.otectus.immersiveenchanting.api.event;

import com.otectus.immersiveenchanting.api.ApplyResult;
import com.otectus.immersiveenchanting.api.RitualSessionView;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;

/** Posted on the server thread around the application of a ritual's result. */
public abstract class RitualResultEvent extends Event {
    private final ServerPlayer player;
    private final RitualSessionView session;
    private final OutcomeBand band;
    private final double score;
    private final List<EnchantmentInstance> planned;

    protected RitualResultEvent(ServerPlayer player, RitualSessionView session, OutcomeBand band, double score,
                                List<EnchantmentInstance> planned) {
        this.player = player;
        this.session = session;
        this.band = band;
        this.score = score;
        this.planned = List.copyOf(planned);
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public RitualSessionView getSession() {
        return session;
    }

    public OutcomeBand getBand() {
        return band;
    }

    public double getScore() {
        return score;
    }

    /** The original plan. */
    public List<EnchantmentInstance> getPlanned() {
        return planned;
    }

    /**
     * Before anything is charged or applied. Cancelling aborts the ritual without cost. The resolved list may be
     * replaced, but only by a subset of the plan at equal or lower levels; anything else is rejected.
     */
    @Cancelable
    public static class Pre extends RitualResultEvent {
        private List<EnchantmentInstance> resolved;

        public Pre(ServerPlayer player, RitualSessionView session, OutcomeBand band, double score,
                   List<EnchantmentInstance> planned, List<EnchantmentInstance> resolved) {
            super(player, session, band, score, planned);
            this.resolved = List.copyOf(resolved);
        }

        public List<EnchantmentInstance> getResolved() {
            return resolved;
        }

        /** @return false (and no change) if {@code replacement} is not a subset of the plan at equal or lower levels */
        public boolean setResolved(List<EnchantmentInstance> replacement) {
            List<EnchantmentInstance> remaining = new ArrayList<>(getPlanned());
            for (EnchantmentInstance r : replacement) {
                EnchantmentInstance match = null;
                for (EnchantmentInstance p : remaining) {
                    if (p.enchantment == r.enchantment && r.level <= p.level && r.level >= 1) {
                        match = p;
                        break;
                    }
                }
                if (match == null) return false;
                remaining.remove(match);
            }
            this.resolved = List.copyOf(replacement);
            return true;
        }
    }

    /** After the result was applied. */
    public static class Post extends RitualResultEvent {
        private final List<EnchantmentInstance> applied;
        private final ApplyResult result;

        public Post(ServerPlayer player, RitualSessionView session, OutcomeBand band, double score,
                    List<EnchantmentInstance> planned, List<EnchantmentInstance> applied, ApplyResult result) {
            super(player, session, band, score, planned);
            this.applied = List.copyOf(applied);
            this.result = result;
        }

        public List<EnchantmentInstance> getApplied() {
            return applied;
        }

        public ApplyResult getApplyResult() {
            return result;
        }
    }
}
