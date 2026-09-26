package com.otectus.immersiveenchanting.session;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.api.ApplyResult;
import com.otectus.immersiveenchanting.api.ChargePolicy;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.api.RitualOutcome;
import com.otectus.immersiveenchanting.api.event.RitualResultEvent;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersiveenchanting.enchanting.OfferPlanner;
import com.otectus.immersiveenchanting.enchanting.RitualPolicy;
import com.otectus.immersiveenchanting.network.ModNetwork;
import com.otectus.immersiveenchanting.network.RitualAbortedS2C;
import com.otectus.immersiveenchanting.network.RitualInputBatchC2S;
import com.otectus.immersiveenchanting.network.RitualResolvedS2C;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;
import com.otectus.immersiveenchanting.ritual.DowngradeResolver;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import com.otectus.immersiveenchanting.ritual.OutcomeRules;
import com.otectus.immersiveenchanting.ritual.PatternGenerator;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.ritual.ScoreEngine;
import com.otectus.immersiveenchanting.ritual.ScoreResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side ritual lifecycle. At most one ritual per player. Server thread only.
 *
 * <h2>Authority</h2>
 * The plan, pattern and costs are captured at start and never sent to the client. The client sends key inputs;
 * the server replays them through the same {@link ScoreEngine} the client used and computes the outcome itself.
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>A session is removed before its result is applied, so any repeated, replayed or late packet finds nothing
 *       and is ignored: a ritual resolves at most once.</li>
 *   <li>Adapters validate every precondition before mutating; a failed check aborts without cost.</li>
 *   <li>Abandoning a ritual (cancel, closing the table, disconnecting, invalid input, expiry) is free until the first
 *       rune is due and resolves as a failure afterwards, charged per {@code failureCostMode}.</li>
 * </ul>
 */
public final class RitualSessionManager {
    private static final int EARLY_FINISH_TOLERANCE_MS = 1500;
    private static final int FUTURE_INPUT_TOLERANCE_MS = 1000;
    private static final Map<UUID, RitualSession> SESSIONS = new HashMap<>();

    public static Optional<RitualSession> get(ServerPlayer player) {
        return Optional.ofNullable(SESSIONS.get(player.getUUID()));
    }

    public static Collection<RitualSession> all() {
        return List.copyOf(SESSIONS.values());
    }

    // ---- start ----------------------------------------------------------------------------------------------------

    public static void start(ServerPlayer player, int containerId, int offerIndex, float timingAssist) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) {
            reject(player, containerId, AbortReason.MENU_CHANGED);
            return;
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            reject(player, containerId, AbortReason.ALREADY_ACTIVE);
            return;
        }
        Optional<EnchantingContextAdapter> owner = AdapterRegistry.find(player, menu);
        if (owner.isEmpty()) {
            ImmersiveEnchanting.debug("{} asked for a ritual on unsupported menu {}", player.getGameProfile().getName(), menu.getClass().getName());
            reject(player, containerId, AbortReason.NOT_SUPPORTED);
            return;
        }
        EnchantingContextAdapter adapter = owner.get();
        if (!adapter.isOfferButton(menu, offerIndex)) {
            reject(player, containerId, AbortReason.INVALID_OFFER);
            return;
        }
        if (!menu.stillValid(player)) {
            reject(player, containerId, AbortReason.MENU_CHANGED);
            return;
        }
        try {
            if (!RitualPolicy.requiresRitual(player, adapter, menu)) {
                bypass(player, adapter, menu, offerIndex);
                return;
            }
            Optional<OfferSnapshot> snapshot = adapter.snapshot(player, menu, offerIndex);
            if (snapshot.isEmpty()) {
                reject(player, containerId, AbortReason.INVALID_OFFER);
                return;
            }
            Optional<AbortReason> blocked = adapter.checkStart(player, menu, snapshot.get());
            if (blocked.isPresent()) {
                reject(player, containerId, blocked.get());
                return;
            }
            OfferPlanner.Plan plan = OfferPlanner.plan(player, adapter, snapshot.get(), timingAssist, false);
            if (!plan.ritualEnabled()) {
                ImmersiveEnchanting.debug("offer {} contains an enchantment with rituals disabled by profile; enchanting directly", offerIndex);
                bypass(player, adapter, menu, offerIndex);
                return;
            }
            RitualPattern pattern = PatternGenerator.generate(plan.params(), plan.seed());
            RitualSession session = new RitualSession(player, adapter, menu, plan, pattern,
                    ServerConfig.get(ServerConfig.SESSION_GRACE_SECONDS) * 1000);
            SESSIONS.put(player.getUUID(), session);
            session.state = RitualState.COUNTDOWN;
            OfferSnapshot s = plan.snapshot();
            ModNetwork.sendTo(player, new RitualStartedS2C(session.sessionId(), containerId, offerIndex, plan.seed(),
                    plan.patternSetId().toString(), PatternGenerator.VERSION, plan.params(), plan.theme(), s.primaryClueId(),
                    s.primaryClueLevel()));
            ImmersiveEnchanting.debug("session {} started for {}: adapter {}, tier {}, {} runes over {} ms, seed {}",
                    session.sessionId(), player.getGameProfile().getName(), adapter.id(), plan.tier(), pattern.events().size(),
                    pattern.endMs(), plan.seed());
        } catch (Throwable t) {
            SESSIONS.remove(player.getUUID());
            ImmersiveEnchanting.LOGGER.error("Immersive Enchanting could not start a ritual for {} (adapter {}, offer {})",
                    player.getGameProfile().getName(), adapter.id(), offerIndex, t);
            reject(player, containerId, AbortReason.INTERNAL_ERROR);
        }
    }

    private static void bypass(ServerPlayer player, EnchantingContextAdapter adapter, AbstractContainerMenu menu, int offerIndex) {
        boolean enchanted = adapter.passThrough(player, menu, offerIndex);
        ImmersiveEnchanting.debug("{} is exempt from rituals; direct enchant {}", player.getGameProfile().getName(), enchanted ? "applied" : "refused");
        ModNetwork.sendTo(player, new RitualAbortedS2C(null, menu.containerId, AbortReason.BYPASSED));
    }

    private static void reject(ServerPlayer player, int containerId, AbortReason reason) {
        ImmersiveEnchanting.debug("ritual refused for {}: {}", player.getGameProfile().getName(), reason.id());
        ModNetwork.sendTo(player, new RitualAbortedS2C(null, containerId, reason));
    }

    // ---- inputs and cancellation ----------------------------------------------------------------------------------

    public static void receiveInputs(ServerPlayer player, RitualInputBatchC2S msg) {
        RitualSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.sessionId().equals(msg.sessionId())) {
            ImmersiveEnchanting.debug("ignored inputs for session {} from {} (not active)", msg.sessionId(), player.getGameProfile().getName());
            return;
        }
        if (msg.malformed() || msg.sequence() != session.lastSequence + 1) {
            abandon(player, session, AbortReason.MALFORMED_INPUT, false);
            return;
        }
        long elapsed = session.elapsedMs();
        AbortReason problem = session.accept(msg.inputs(), elapsed + FUTURE_INPUT_TOLERANCE_MS);
        if (problem != null) {
            abandon(player, session, problem, false);
            return;
        }
        session.lastSequence = msg.sequence();
        if (msg.complete()) {
            if (elapsed < session.pattern().endMs() - EARLY_FINISH_TOLERANCE_MS) {
                abandon(player, session, AbortReason.TIMING_INVALID, false);
                return;
            }
            resolve(player, session);
        }
    }

    public static void cancel(ServerPlayer player, UUID sessionId) {
        RitualSession session = SESSIONS.get(player.getUUID());
        if (session != null && session.sessionId().equals(sessionId)) abandon(player, session, AbortReason.CANCELLED, false);
    }

    /** Called from {@code EnchantmentMenu#removed}, before the menu returns its items. */
    public static void onMenuRemoved(ServerPlayer player, AbstractContainerMenu menu) {
        RitualSession session = SESSIONS.get(player.getUUID());
        if (session != null && session.menu() == menu) abandon(player, session, AbortReason.MENU_CHANGED, true);
    }

    public static void onLogout(ServerPlayer player) {
        RitualSession session = SESSIONS.get(player.getUUID());
        if (session != null) abandon(player, session, AbortReason.CANCELLED, true);
    }

    /** Operator abort: always free. */
    public static boolean adminAbort(ServerPlayer player) {
        RitualSession session = SESSIONS.get(player.getUUID());
        if (session == null) return false;
        abortFree(player, session, AbortReason.ADMIN);
        return true;
    }

    /** Watches active sessions for closed menus, changed items and expiry. Nothing to do without sessions. */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (RitualSession session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = session.player();
            if (player.isRemoved() || player.hasDisconnected()) {
                SESSIONS.remove(session.playerId(), session);
                continue;
            }
            if (player.containerMenu != session.menu()) {
                abandon(player, session, AbortReason.MENU_CHANGED, true);
            } else if (!session.adapter().stillMatches(player, session.menu(), session.snapshot())) {
                abandon(player, session, AbortReason.ITEM_CHANGED, false);
            } else if (session.expired()) {
                abandon(player, session, AbortReason.EXPIRED, false);
            }
        }
    }

    public static void clear() {
        SESSIONS.clear();
    }

    // ---- resolution -----------------------------------------------------------------------------------------------

    private static void resolve(ServerPlayer player, RitualSession session) {
        ScoreResult score = ScoreEngine.score(session.pattern(), session.plan().params(), session.inputs());
        OutcomeRules rules = ServerConfig.outcomeRules();
        OutcomeBand band = rules.band(score.finalScore());
        List<DowngradeResolver.Kept> kept = DowngradeResolver.resolve(session.plan().downgradeEntries(), rules.retention(band));
        List<EnchantmentInstance> planned = session.snapshot().plannedEnchantments();
        List<EnchantmentInstance> resolved = new ArrayList<>();
        for (DowngradeResolver.Kept k : kept) resolved.add(new EnchantmentInstance(planned.get(k.planIndex()).enchantment, k.level()));
        finish(player, session, band, score, resolved, false, false);
    }

    private static void abandon(ServerPlayer player, RitualSession session, AbortReason reason, boolean closing) {
        if (!session.costsApply()) {
            abortFree(player, session, reason);
            return;
        }
        ImmersiveEnchanting.debug("session {} abandoned after the first rune ({}); resolving as a failure", session.sessionId(), reason.id());
        finish(player, session, OutcomeBand.FAILED, null, List.of(), true, closing);
    }

    private static void abortFree(ServerPlayer player, RitualSession session, AbortReason reason) {
        SESSIONS.remove(player.getUUID(), session);
        session.state = RitualState.ABORTED;
        ImmersiveEnchanting.debug("session {} aborted without cost: {}", session.sessionId(), reason.id());
        ModNetwork.sendTo(player, new RitualAbortedS2C(session.sessionId(), session.containerId(), reason));
    }

    private static void finish(ServerPlayer player, RitualSession session, OutcomeBand band, ScoreResult score,
                               List<EnchantmentInstance> resolved, boolean abandoned, boolean closing) {
        session.state = RitualState.RESOLVING;
        double scoreValue = score == null ? 0.0 : score.finalScore();
        List<EnchantmentInstance> planned = session.snapshot().plannedEnchantments();
        RitualResultEvent.Pre pre = new RitualResultEvent.Pre(player, session, band, scoreValue, planned, resolved);
        if (MinecraftForge.EVENT_BUS.post(pre)) {
            abortFree(player, session, AbortReason.CANCELLED_BY_MOD);
            return;
        }
        List<EnchantmentInstance> toApply = pre.getResolved();
        RitualOutcome outcome = new RitualOutcome(band, scoreValue, abandoned, planned, toApply, failureCharge());
        // Removed before applying: nothing can resolve this session a second time, even re-entrantly.
        SESSIONS.remove(player.getUUID(), session);
        ApplyResult result;
        try {
            result = session.adapter().apply(player, session.menu(), session, toApply, outcome, closing);
        } catch (Throwable t) {
            session.state = RitualState.ABORTED;
            ImmersiveEnchanting.LOGGER.error("Immersive Enchanting failed while applying ritual {} for {} (adapter {}, plan {}, result {}). "
                            + "The result was not retried.", session.sessionId(), player.getGameProfile().getName(), session.adapterId(),
                    ids(planned), ids(toApply), t);
            ModNetwork.sendTo(player, new RitualAbortedS2C(session.sessionId(), session.containerId(), AbortReason.INTERNAL_ERROR));
            return;
        }
        if (!result.applied()) {
            session.state = RitualState.ABORTED;
            AbortReason reason = result.rejection() == null ? AbortReason.INTERNAL_ERROR : result.rejection();
            ImmersiveEnchanting.debug("session {} not applied: {}", session.sessionId(), reason.id());
            ModNetwork.sendTo(player, new RitualAbortedS2C(session.sessionId(), session.containerId(), reason));
            return;
        }
        session.state = RitualState.RESOLVED;
        List<EnchantmentInstance> bound = result.bound() != null ? result.bound() : toApply;
        MinecraftForge.EVENT_BUS.post(new RitualResultEvent.Post(player, session, band, scoreValue, planned, bound, result));
        RitualEffects.play(player, session, band, abandoned);
        ModNetwork.sendTo(player, resolvedPacket(session, band, score, abandoned, planned, bound, result));
        ImmersiveEnchanting.debug("session {} resolved: {} ({}), bound {} of {}, charged {} levels {} points {} lapis",
                session.sessionId(), band.id(), String.format("%.1f", scoreValue), ids(bound), ids(planned),
                result.xpLevelsCharged(), result.xpPointsCharged(), result.lapisCharged());
    }

    private static RitualResolvedS2C resolvedPacket(RitualSession session, OutcomeBand band, ScoreResult score, boolean abandoned,
                                                    List<EnchantmentInstance> planned, List<EnchantmentInstance> bound, ApplyResult result) {
        List<RitualResolvedS2C.Entry> plannedEntries = new ArrayList<>();
        for (int i = 0; i < planned.size(); i++) plannedEntries.add(new RitualResolvedS2C.Entry(id(planned.get(i)), planned.get(i).level, i));
        boolean[] used = new boolean[planned.size()];
        List<RitualResolvedS2C.Entry> boundEntries = new ArrayList<>();
        for (EnchantmentInstance b : bound) {
            int index = -1;
            for (int i = 0; i < planned.size(); i++) {
                if (!used[i] && planned.get(i).enchantment == b.enchantment) {
                    index = i;
                    used[i] = true;
                    break;
                }
            }
            boundEntries.add(new RitualResolvedS2C.Entry(id(b), b.level, index));
        }
        return new RitualResolvedS2C(session.sessionId(), band, score == null ? 0 : score.finalScore(),
                score == null ? 0 : score.accuracy(), score == null ? 0 : score.perfect(), score == null ? 0 : score.good(),
                score == null ? 0 : score.graze(), score == null ? 0 : score.miss(), score == null ? 0 : score.longestCombo(),
                abandoned, plannedEntries, boundEntries, result.xpLevelsCharged(), result.xpPointsCharged(), result.lapisCharged(),
                result.seedAdvanced());
    }

    private static ChargePolicy failureCharge() {
        boolean seed = ServerConfig.get(ServerConfig.ADVANCE_SEED_ON_FAILURE);
        return switch (ServerConfig.failureCostMode()) {
            case FULL -> new ChargePolicy(true, true, seed);
            case LAPIS_ONLY -> new ChargePolicy(false, true, seed);
            case NONE -> new ChargePolicy(false, false, seed);
        };
    }

    private static ResourceLocation id(EnchantmentInstance instance) {
        ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(instance.enchantment);
        return id == null ? new ResourceLocation("unknown", "unregistered") : id;
    }

    private static String ids(List<EnchantmentInstance> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(id(list.get(i))).append(' ').append(list.get(i).level);
        }
        return sb.append(']').toString();
    }

    private RitualSessionManager() {}
}
