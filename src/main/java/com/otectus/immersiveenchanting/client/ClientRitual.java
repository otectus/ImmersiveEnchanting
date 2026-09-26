package com.otectus.immersiveenchanting.client;

import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.registry.ModSounds;
import com.otectus.immersiveenchanting.ritual.EventType;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.ritual.Judgement;
import com.otectus.immersiveenchanting.ritual.RitualEvent;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.ritual.ScoreEngine;
import com.otectus.immersiveenchanting.ritual.ScoreResult;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A ritual being played on this client. Owns the ritual clock (started when the ritual arrives), records binding
 * inputs, judges them locally with the same {@link ScoreEngine} the server will use, sends inputs in small chunks
 * through its {@link Host}, and keeps the presentation state the renderer draws. Render thread only.
 */
public final class ClientRitual {
    /** Where inputs go and what happens at the end: the server for real rituals, nothing for practice. */
    public interface Host {
        void sendInputs(int sequence, List<InputRecord> inputs, boolean complete);

        void cancel();

        void finished(ClientRitual ritual, ScoreResult localResult);
    }

    public record Flash(int lane, Judgement judgement, long atMs, boolean broken) {}

    public record Label(int lane, Judgement judgement, long atMs) {}

    /** A fragment of a broken rune, positioned relative to its lane's anchor. */
    public static final class Shard {
        public int lane;
        public float ox, oy, vx, vy, life, maxLife, size;
    }

    private static final int FLUSH_COUNT = 64;
    private static final int FLUSH_INTERVAL_MS = 1500;
    /** Time after the last rune before the ritual closes itself. */
    private static final int END_PADDING_MS = 350;
    public static final int MAX_SHARDS = 96;

    public final RitualParams params;
    public final RitualPattern pattern;
    public final ScoreEngine engine;
    public final ResourceLocation theme;
    @Nullable
    public final Component clue;
    public final int containerId;
    private final Host host;
    private final long startNanos;
    private final List<InputRecord> pending = new ArrayList<>();
    private int sequence;
    private long lastFlushMs;
    private final boolean[] down = new boolean[RitualParams.MAX_ANCHORS];
    private boolean finished;
    private ScoreResult localResult;
    private int lastBeat = -1;

    /** Release held lanes while the game window is unfocused. Automated input (the smoke test) turns this off. */
    public boolean releaseOnFocusLoss = true;
    public double resonance = 0.5;
    public long lastMissAtMs = -10_000;
    public long escArmedUntilMs = -1;
    public final long[] lanePressedAtMs = {-10_000, -10_000, -10_000, -10_000};
    public final List<Flash> flashes = new ArrayList<>();
    public final List<Label> labels = new ArrayList<>();
    public final List<Shard> shards = new ArrayList<>();

    public ClientRitual(RitualParams params, RitualPattern pattern, ResourceLocation theme, @Nullable Component clue, int containerId, Host host) {
        this.params = params;
        this.pattern = pattern;
        this.engine = new ScoreEngine(pattern, params);
        this.theme = theme;
        this.clue = clue;
        this.containerId = containerId;
        this.host = host;
        this.startNanos = System.nanoTime();
    }

    public long nowMs() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public boolean isFinished() {
        return finished;
    }

    @Nullable
    public ScoreResult localResult() {
        return localResult;
    }

    public boolean inCountIn() {
        return nowMs() < pattern.countInMs();
    }

    public boolean isDown(int lane) {
        return lane >= 0 && lane < down.length && down[lane];
    }

    /** Milliseconds a rune is visible before its binding time. */
    public double approachMs() {
        double base = Math.max(900.0, Math.min(2200.0, pattern.beatMs() * 2.5));
        return base * ClientConfig.get(ClientConfig.APPROACH_TIME_MULTIPLIER);
    }

    // ---- input ----------------------------------------------------------------------------------------------------

    public void press(int lane) {
        if (finished || lane < 0 || lane >= pattern.anchors() || down[lane]) return;
        int t = (int) nowMs();
        down[lane] = true;
        lanePressedAtMs[lane] = t;
        pending.add(new InputRecord(t, lane, true));
        feedback(engine.press(lane, t), t);
    }

    public void release(int lane) {
        if (finished || lane < 0 || lane >= pattern.anchors() || !down[lane]) return;
        int t = (int) nowMs();
        down[lane] = false;
        pending.add(new InputRecord(t, lane, false));
        feedback(engine.release(lane, t), t);
    }

    /** Called every frame. */
    public void update() {
        if (finished) {
            tickShards();
            return;
        }
        long now = nowMs();
        // GLFW sends key releases when the window loses focus; this also covers mouse-bound lanes.
        if (releaseOnFocusLoss && !Minecraft.getInstance().isWindowActive()) {
            for (int lane = 0; lane < pattern.anchors(); lane++) if (down[lane]) release(lane);
        }
        feedback(engine.advance((int) now), (int) now);
        beats(now);
        if (pending.size() >= FLUSH_COUNT || (!pending.isEmpty() && now - lastFlushMs >= FLUSH_INTERVAL_MS)) flush(false);
        if (now >= (long) pattern.endMs() + params.grazeMs() + END_PADDING_MS) finish();
        tickShards();
    }

    /** Ends the ritual: releases held keys, scores locally, sends the final chunk. */
    public void finish() {
        if (finished) return;
        for (int lane = 0; lane < pattern.anchors(); lane++) if (down[lane]) release(lane);
        feedback(engine.advance((int) nowMs()), (int) nowMs());
        finished = true;
        localResult = engine.finish();
        flush(true);
        host.finished(this, localResult);
    }

    /** The player backed out (the host decides whether it is free). */
    public void abandon() {
        if (finished) return;
        finished = true;
        host.cancel();
    }

    private void flush(boolean complete) {
        if (pending.isEmpty() && !complete) return;
        List<InputRecord> chunk = List.copyOf(pending);
        pending.clear();
        lastFlushMs = nowMs();
        host.sendInputs(sequence++, chunk, complete);
    }

    private void beats(long now) {
        int beat = (int) Math.floor(now / pattern.beatMs());
        if (beat == lastBeat) return;
        lastBeat = beat;
        boolean countIn = now < pattern.countInMs();
        if (countIn || (ClientConfig.get(ClientConfig.METRONOME_CUE) && now <= pattern.endMs())) {
            RitualSounds.play(ModSounds.COUNT_TICK.get(), countIn ? 1.0F : 0.8F);
        }
    }

    // ---- presentation state ---------------------------------------------------------------------------------------

    private void feedback(List<ScoreEngine.Feedback> list, long now) {
        for (ScoreEngine.Feedback f : list) {
            RitualEvent e = f.eventIndex() >= 0 ? pattern.events().get(f.eventIndex()) : null;
            switch (f.kind()) {
                case HIT, HOLD_COMPLETE, CHORD_PARTIAL -> {
                    boolean chordPartial = f.kind() == ScoreEngine.Kind.CHORD_PARTIAL;
                    for (int lane : lanesOf(e, f.lane())) {
                        flashes.add(new Flash(lane, f.judgement(), now, chordPartial));
                        if (chordPartial) crack(lane, now);
                    }
                    labels.add(new Label(e != null && e.type() == EventType.CHORD ? e.laneA() : f.lane(), f.judgement(), now));
                    RitualSounds.judgement(f.judgement(), f.lane());
                    resonance += switch (f.judgement()) {
                        case PERFECT -> 0.035;
                        case GOOD -> 0.025;
                        case GRAZE -> 0.005;
                        case MISS -> -0.09;
                    };
                }
                case HOLD_START -> {
                    flashes.add(new Flash(f.lane(), f.judgement(), now, false));
                    RitualSounds.holdStart(f.lane());
                }
                case HOLD_BROKEN -> {
                    shatter(f.lane(), now);
                    labels.add(new Label(f.lane(), f.judgement(), now));
                    RitualSounds.judgement(Judgement.MISS, f.lane());
                    resonance -= 0.06;
                    lastMissAtMs = now;
                }
                case MISS -> {
                    for (int lane : lanesOf(e, f.lane())) shatter(lane, now);
                    labels.add(new Label(e != null ? e.laneA() : f.lane(), Judgement.MISS, now));
                    RitualSounds.judgement(Judgement.MISS, f.lane());
                    resonance -= 0.09;
                    lastMissAtMs = now;
                }
                case STRAY -> {
                    crack(f.lane(), now);
                    resonance -= 0.02;
                }
            }
            resonance = Math.max(0.0, Math.min(1.0, resonance));
        }
        flashes.removeIf(fl -> now - fl.atMs() > 500);
        labels.removeIf(l -> now - l.atMs() > 650);
    }

    private static int[] lanesOf(@Nullable RitualEvent e, int fallback) {
        if (e == null) return new int[]{fallback};
        return e.laneB() >= 0 ? new int[]{e.laneA(), e.laneB()} : new int[]{e.laneA()};
    }

    private void crack(int lane, long now) {
        flashes.add(new Flash(lane, Judgement.MISS, now, true));
    }

    /** The miss effect: the rune breaks into shards (skipped with reduced motion). */
    private void shatter(int lane, long now) {
        flashes.add(new Flash(lane, Judgement.MISS, now, true));
        if (ClientConfig.get(ClientConfig.REDUCED_MOTION)) return;
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 9 && shards.size() < MAX_SHARDS; i++) {
            Shard s = new Shard();
            s.lane = lane;
            double angle = random.nextDouble(Math.PI * 2);
            double speed = 25 + random.nextDouble(55);
            s.vx = (float) (Math.cos(angle) * speed);
            s.vy = (float) (Math.sin(angle) * speed);
            s.maxLife = s.life = 0.35F + random.nextFloat() * 0.3F;
            s.size = 1.2F + random.nextFloat() * 1.6F;
            shards.add(s);
        }
    }

    private long lastShardTick = -1;

    private void tickShards() {
        long now = System.nanoTime();
        float dt = lastShardTick < 0 ? 0 : Math.min(0.1F, (now - lastShardTick) / 1e9F);
        lastShardTick = now;
        for (Shard s : shards) {
            s.ox += s.vx * dt;
            s.oy += s.vy * dt;
            s.vy += 60 * dt;
            s.life -= dt;
        }
        shards.removeIf(s -> s.life <= 0);
    }
}
