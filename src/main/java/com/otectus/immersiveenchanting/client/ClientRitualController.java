package com.otectus.immersiveenchanting.client;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.network.ModNetwork;
import com.otectus.immersiveenchanting.network.RitualAbortedS2C;
import com.otectus.immersiveenchanting.network.RitualCancelC2S;
import com.otectus.immersiveenchanting.network.RitualInputBatchC2S;
import com.otectus.immersiveenchanting.network.RitualResolvedS2C;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;
import com.otectus.immersiveenchanting.network.StartRitualC2S;
import com.otectus.immersiveenchanting.registry.ModSounds;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.ritual.PatternGenerator;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.ritual.ScoreResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The client side of a real (server-backed) ritual: IDLE -> REQUESTED -> PLAYING -> AWAITING_RESULT -> RESULT.
 * While anything but IDLE, the enchanting screen's own input is suspended (see {@link EnchantmentScreenHooks}).
 */
public final class ClientRitualController {
    public enum Phase { IDLE, REQUESTED, PLAYING, AWAITING_RESULT, RESULT }

    private static final long REQUEST_TIMEOUT_MS = 6_000;
    private static final long RESULT_TIMEOUT_MS = 12_000;
    private static final long RESULT_MIN_MS = 600;
    private static final long RESULT_AUTO_CLOSE_MS = 12_000;

    private static Phase phase = Phase.IDLE;
    private static ClientRitual ritual;
    private static UUID sessionId;
    private static int containerId = -1;
    private static long phaseSince;
    private static boolean cancelOnArrival;
    private static ResultView result;
    private static ScoreResult localResult;
    /** A session whose screen closed mid-ritual; its outcome is reported in chat, where it can be read later. */
    private static UUID orphan;
    private static Component message;
    private static long messageUntil;
    /**
     * Keys seen going down and not yet up while the ritual owns input. A key still held from the ritual (or its
     * auto-repeat, which arrives as another press) neither dismisses the result nor confirms an abandon.
     */
    private static final Set<Integer> heldKeys = new HashSet<>();

    public static Phase phase() {
        return phase;
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static boolean isPlaying() {
        return phase == Phase.PLAYING;
    }

    @Nullable
    public static ClientRitual ritual() {
        return ritual;
    }

    @Nullable
    public static ResultView result() {
        return result;
    }

    @Nullable
    public static UUID sessionId() {
        return sessionId;
    }

    @Nullable
    public static ScoreResult localResult() {
        return localResult;
    }

    public static int containerId() {
        return containerId;
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------------

    public static void requestStart(int menuContainerId, int offerIndex) {
        if (phase != Phase.IDLE) return;
        phase = Phase.REQUESTED;
        phaseSince = System.currentTimeMillis();
        containerId = menuContainerId;
        cancelOnArrival = false;
        ModNetwork.sendToServer(new StartRitualC2S(menuContainerId, offerIndex, (float) ClientConfig.get(ClientConfig.TIMING_ASSIST)));
    }

    static void onStarted(RitualStartedS2C msg) {
        Minecraft mc = Minecraft.getInstance();
        boolean expected = phase == Phase.REQUESTED && !cancelOnArrival && msg.containerId() == containerId
                && mc.player != null && mc.player.containerMenu.containerId == msg.containerId();
        if (msg.generatorVersion() != PatternGenerator.VERSION) {
            ModNetwork.sendToServer(new RitualCancelC2S(msg.sessionId()));
            showMessage(Component.translatable(AbortReason.VERSION_MISMATCH.translationKey()));
            reset();
            return;
        }
        if (!expected) {
            ModNetwork.sendToServer(new RitualCancelC2S(msg.sessionId()));
            if (phase == Phase.REQUESTED) reset();
            return;
        }
        RitualParams params = msg.params().sanitized();
        RitualPattern pattern = PatternGenerator.generate(params, msg.patternSeed());
        Component clue = null;
        if (msg.clueId() != null) {
            Enchantment e = BuiltInRegistries.ENCHANTMENT.get(msg.clueId());
            if (e != null) clue = e.getFullname(msg.clueLevel());
        }
        sessionId = msg.sessionId();
        ritual = new ClientRitual(params, pattern, msg.theme(), clue, msg.containerId(), new NetworkHost(msg.sessionId()));
        phase = Phase.PLAYING;
        phaseSince = System.currentTimeMillis();
        RitualSounds.play(ModSounds.RITUAL_BEGIN.get(), 1.0F);
    }

    static void onResolved(RitualResolvedS2C msg) {
        if (orphan != null && orphan.equals(msg.sessionId())) {
            orphan = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.gui.getChat().addMessage(orphanSummary(ResultView.of(msg)));
            return;
        }
        if (sessionId == null || !sessionId.equals(msg.sessionId())) return;
        if (localResult != null && Math.abs(localResult.finalScore() - msg.score()) > 1e-6 && !msg.abandoned()) {
            ImmersiveEnchanting.LOGGER.warn("Ritual {}: local score {} differs from the server's {}", msg.sessionId(), localResult.finalScore(), msg.score());
        }
        result = ResultView.of(msg);
        phase = Phase.RESULT;
        phaseSince = System.currentTimeMillis();
        RitualSounds.result(msg.band());
    }

    static void onAborted(RitualAbortedS2C msg) {
        if (orphan != null && orphan.equals(msg.sessionId())) {
            orphan = null;
            return;
        }
        if (phase == Phase.IDLE) return;
        if (msg.sessionId() != null && sessionId != null && !msg.sessionId().equals(sessionId)) return;
        if (msg.sessionId() == null && phase != Phase.REQUESTED) return;
        boolean quiet = msg.reason() == AbortReason.BYPASSED || (msg.reason() == AbortReason.CANCELLED && phase == Phase.AWAITING_RESULT);
        if (!quiet) showMessage(msg.detail() != null ? msg.detail() : Component.translatable(msg.reason().translationKey()));
        reset();
    }

    /** Per frame, from the screen render hook. */
    public static void frame() {
        long now = System.currentTimeMillis();
        switch (phase) {
            case REQUESTED -> {
                if (now - phaseSince > REQUEST_TIMEOUT_MS) {
                    showMessage(Component.translatable("immersive_enchanting.abort.timeout"));
                    reset();
                }
            }
            case PLAYING -> ritual.update();
            case AWAITING_RESULT -> {
                ritual.update();
                if (now - phaseSince > RESULT_TIMEOUT_MS) {
                    showMessage(Component.translatable("immersive_enchanting.abort.timeout"));
                    reset();
                }
            }
            case RESULT -> {
                if (now - phaseSince > RESULT_AUTO_CLOSE_MS) reset();
            }
            default -> {
            }
        }
    }

    /** The screen closed: an unfinished ritual is abandoned (the server decides whether that is free). */
    public static void onScreenClosed() {
        if (phase == Phase.PLAYING && ritual != null) {
            orphan = sessionId;
            ritual.abandon();
        } else if (phase == Phase.AWAITING_RESULT) {
            orphan = sessionId;
        } else if (phase == Phase.REQUESTED) {
            cancelOnArrival = true;
            return;
        }
        reset();
    }

    /**
     * The outcome of a ritual whose table closed first, for chat: the band, what happened to each enchantment and what
     * it cost - the same facts as the result panel, which is no longer there to show them.
     */
    static Component orphanSummary(ResultView view) {
        Component band = Component.translatable(view.abandoned() ? "immersive_enchanting.result.abandoned"
                : "immersive_enchanting.result." + view.band().id()).withStyle(view.band().isFailure() ? ChatFormatting.RED : ChatFormatting.GOLD);
        List<Component> fates = new ArrayList<>();
        for (ResultView.Line line : view.lines()) {
            fates.add(switch (line.fate()) {
                case KEPT -> Component.translatable("immersive_enchanting.result.kept", line.name()).withStyle(ChatFormatting.GREEN);
                case WEAKENED -> Component.translatable("immersive_enchanting.result.weakened", line.name(),
                        Component.translatable("enchantment.level." + line.toLevel())).withStyle(ChatFormatting.YELLOW);
                case LOST -> Component.translatable("immersive_enchanting.result.lost", line.name()).withStyle(ChatFormatting.RED);
            });
        }
        Component cost = view.xpLevels() == 0 && view.xpPoints() == 0 && view.lapis() == 0
                ? Component.translatable("immersive_enchanting.result.no_cost")
                : view.xpPoints() > 0 ? Component.translatable("immersive_enchanting.result.cost_points", view.xpPoints(), view.lapis())
                : Component.translatable("immersive_enchanting.result.cost", view.xpLevels(), view.lapis());
        return Component.translatable("immersive_enchanting.result.orphan", band,
                fates.isEmpty() ? Component.literal("-") : ComponentUtils.formatList(fates, Component.literal(", ")),
                cost.copy().withStyle(ChatFormatting.GRAY));
    }

    public static void reset() {
        heldKeys.clear();
        phase = Phase.IDLE;
        ritual = null;
        sessionId = null;
        result = null;
        localResult = null;
        containerId = -1;
        phaseSince = System.currentTimeMillis();
    }

    public static void onLogout() {
        reset();
        heldKeys.clear();
        orphan = null;
        cancelOnArrival = false;
        message = null;
    }

    // ---- input ----------------------------------------------------------------------------------------------------

    public static void keyPressed(int keyCode, int scanCode) {
        boolean fresh = heldKeys.add(keyCode);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (fresh) escape();
            return;
        }
        switch (phase) {
            case PLAYING -> ritual.press(RitualKeyMappings.laneForKey(keyCode, scanCode, ritual.pattern.anchors()));
            case RESULT -> {
                if (fresh) dismissResult();
            }
            default -> {
            }
        }
    }

    public static void keyReleased(int keyCode, int scanCode) {
        heldKeys.remove(keyCode);
        if (phase == Phase.PLAYING) ritual.release(RitualKeyMappings.laneForKey(keyCode, scanCode, ritual.pattern.anchors()));
    }

    public static void mousePressed(int button) {
        switch (phase) {
            case PLAYING -> ritual.press(RitualKeyMappings.laneForMouse(button, ritual.pattern.anchors()));
            case RESULT -> dismissResult();
            default -> {
            }
        }
    }

    public static void mouseReleased(int button) {
        if (phase == Phase.PLAYING) ritual.release(RitualKeyMappings.laneForMouse(button, ritual.pattern.anchors()));
    }

    private static void escape() {
        switch (phase) {
            case REQUESTED -> {
                cancelOnArrival = true;
                phase = Phase.IDLE;
            }
            case PLAYING -> {
                long now = ritual.nowMs();
                if (ritual.inCountIn() || ritual.escArmedUntilMs > now) {
                    ritual.abandon();
                } else {
                    ritual.escArmedUntilMs = now + 3000;
                }
            }
            case RESULT -> dismissResult();
            default -> {
            }
        }
    }

    private static void dismissResult() {
        if (result != null && result.ageMs() >= RESULT_MIN_MS) reset();
    }

    // ---- messages -------------------------------------------------------------------------------------------------

    /** A notice over the table, kept long enough to read: at least five seconds, longer for longer text. */
    public static void showMessage(Component text) {
        message = text;
        messageUntil = System.currentTimeMillis() + Math.max(5000, 1500 + 60L * text.getString().length());
    }

    @Nullable
    public static Component message() {
        return message != null && System.currentTimeMillis() < messageUntil ? message : null;
    }

    public static float messageAlpha() {
        long left = messageUntil - System.currentTimeMillis();
        return Math.max(0f, Math.min(1f, left / 500f));
    }

    /** Sends inputs to the server; the server's answer ends the ritual. */
    private record NetworkHost(UUID session) implements ClientRitual.Host {
        @Override
        public void sendInputs(int sequence, List<InputRecord> inputs, boolean complete) {
            int max = RitualInputBatchC2S.MAX_INPUTS_PER_PACKET;
            int start = 0;
            do {
                int end = Math.min(inputs.size(), start + max);
                boolean last = end >= inputs.size();
                ModNetwork.sendToServer(new RitualInputBatchC2S(session, SequenceCounter.next(session), complete && last,
                        List.copyOf(inputs.subList(start, end))));
                start = end;
            } while (start < inputs.size());
        }

        @Override
        public void cancel() {
            ModNetwork.sendToServer(new RitualCancelC2S(session));
            if (session.equals(sessionId)) {
                phase = Phase.AWAITING_RESULT;
                phaseSince = System.currentTimeMillis();
            }
        }

        @Override
        public void finished(ClientRitual finishedRitual, ScoreResult local) {
            if (!session.equals(sessionId)) return;
            localResult = local;
            phase = Phase.AWAITING_RESULT;
            phaseSince = System.currentTimeMillis();
        }
    }

    /** Wire sequence numbers per session (a ritual's own chunk counter may split into several packets). */
    private static final class SequenceCounter {
        private static UUID current;
        private static int next;

        static int next(UUID session) {
            if (!session.equals(current)) {
                current = session;
                next = 0;
            }
            return next++;
        }
    }

    private ClientRitualController() {}
}
