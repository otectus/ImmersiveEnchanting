package com.otectus.immersiveenchanting.client.render;

import com.mojang.math.Axis;
import com.otectus.immersiveenchanting.client.ClientRitual;
import com.otectus.immersiveenchanting.client.ResultView;
import com.otectus.immersiveenchanting.client.RitualKeyMappings;
import com.otectus.immersiveenchanting.config.ClientConfig;
import com.otectus.immersiveenchanting.ritual.DifficultyTier;
import com.otectus.immersiveenchanting.ritual.Judgement;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import com.otectus.immersiveenchanting.ritual.RitualEvent;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.util.FormattedCharSequence;

/** Draws the ritual, its result and its messages over whatever screen is open. Render thread only. */
public final class RitualRenderer {
    private static final VectorBatch BATCH = new VectorBatch();
    private static final ResourceLocation ALT_FONT = new ResourceLocation("minecraft", "alt");
    private static final String GLYPHS = "abcdefghijklmnopqrstuvwxyz";
    private static final float Z = 450;

    public static RitualLayout layout(int width, int height, int anchors) {
        return ClientConfig.layout() == ClientConfig.RitualLayout.CLASSIC_LANES
                ? new LaneLayout(width, height, anchors) : new RadialLayout(width, height, anchors);
    }

    // ---- the ritual -----------------------------------------------------------------------------------------------

    public static void renderRitual(GuiGraphics g, ClientRitual r, int w, int h, boolean awaiting) {
        Font font = Minecraft.getInstance().font;
        long now = r.nowMs();
        RitualPattern pattern = r.pattern;
        Palette.Theme theme = Palette.theme(r.theme);
        boolean reduced = ClientConfig.get(ClientConfig.REDUCED_MOTION);
        int viewportWidth = w, viewportHeight = h;
        w = 440;
        h = 320;
        RitualLayout layout = layout(w, h, pattern.anchors());
        double beatPhase = frac(now / pattern.beatMs());
        boolean countIn = now < pattern.countInMs();
        float stability = (float) Math.min(1.0, r.engine.combo() / 16.0) * 0.6f + (float) r.resonance * 0.4f;

        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        dim(g, viewportWidth, viewportHeight);
        fit(g, viewportWidth, viewportHeight, w, h);
        StonebornGui.panel(g, 0, 0, w, h);
        g.fill(6, 6, w - 6, 50, StonebornGui.SURFACE);
        StonebornGui.rule(g, 12, 49, w - 24);
        StonebornGui.well(g, 12, 56, w - 24, h - 98);
        g.fill(12, h - 36, w - 12, h - 8, StonebornGui.SURFACE);
        if (ClientConfig.get(ClientConfig.SCREEN_SHAKE) && !reduced && now - r.lastMissAtMs < 120) {
            float s = (float) (1.5 * (1 - (now - r.lastMissAtMs) / 120.0));
            g.pose().translate(((now / 16) % 2 == 0 ? s : -s), ((now / 24) % 2 == 0 ? -s * 0.6f : s * 0.6f), 0);
        }

        VectorBatch b = BATCH.begin(g);
        layout.backdrop(b, theme, stability, now, countIn, beatPhase, reduced);
        float cracked = now - r.lastMissAtMs < 450 ? ((now / 70) % 2 == 0 ? 1f : 0.35f) : 0f;
        layout.resonance(b, theme, r.resonance, cracked);
        anchors(b, r, layout, now, theme);
        runes(b, r, layout, now);
        effects(b, r, layout, now);
        shards(b, r, layout);
        b.end();

        g.pose().translate(0, 0, 1);
        if (layout.radial()) glyphRing(g, font, layout, theme, now, stability, reduced);
        keyLabels(g, font, r, layout);
        judgementLabels(g, font, r, layout, now);
        hud(g, font, r, layout, theme, now, w, h, awaiting);
        g.pose().popPose();
    }

    private static void anchors(VectorBatch b, ClientRitual r, RitualLayout layout, long now, Palette.Theme theme) {
        float rr = layout.runeRadius();
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_RUNES);
        for (int lane = 0; lane < r.pattern.anchors(); lane++) {
            float[] a = layout.anchor(lane);
            int color = Palette.lane(lane);
            boolean pressed = r.isDown(lane);
            RuneGlyphs.filled(b, lane, a[0], a[1], rr * 1.12f, Palette.withAlpha(color, pressed ? 0.4f : 0.1f));
            if (contrast) RuneGlyphs.outline(b, lane, a[0], a[1], rr * 1.12f, 3.2f, Palette.withAlpha(0x000000, 0.9f));
            RuneGlyphs.outline(b, lane, a[0], a[1], rr * 1.12f, contrast ? 1.8f : 1.5f, Palette.withAlpha(contrast ? 0xFFFFFF : color, pressed ? 1f : 0.8f));
            long age = now - r.lanePressedAtMs[lane];
            if (age >= 0 && age < 160) {
                float t = age / 160f;
                b.circle(a[0], a[1], rr * (1.3f + 0.5f * t), 1.2f, Palette.withAlpha(color, 0.6f * (1 - t)));
            }
        }
    }

    private static void runes(VectorBatch b, ClientRitual r, RitualLayout layout, long now) {
        double approach = r.approachMs();
        float rr = layout.runeRadius();
        double late = r.params.grazeMs() / approach;
        for (RitualEvent e : r.pattern.events()) {
            double head = 1 - (e.timeMs() - now) / approach;
            if (head < 0) break;
            int i = e.index();
            if (r.engine.judgementOf(i) != null) continue;
            float fadeIn = (float) Math.min(1.0, head * 5.0);
            float fadeLate = head > 1 ? (float) Math.max(0.0, 1.0 - (head - 1) / late) : 1f;
            float alpha = Math.max(0f, fadeIn * fadeLate);
            switch (e.type()) {
                case TAP -> rune(b, layout, e.laneA(), head, rr, alpha, false);
                case CHORD -> {
                    int pressed = r.engine.chordPresses(i);
                    layout.chordLink(b, e.laneA(), e.laneB(), Math.min(head, 1.0), rr * 0.45f, Palette.withAlpha(0xFFFFFF, 0.5f * alpha));
                    rune(b, layout, e.laneA(), (pressed & 1) != 0 ? 1.0 : head, rr, alpha, (pressed & 1) != 0);
                    rune(b, layout, e.laneB(), (pressed & 2) != 0 ? 1.0 : head, rr, alpha, (pressed & 2) != 0);
                }
                case HOLD -> {
                    boolean holding = r.engine.isHolding(i);
                    double headP = holding ? 1.0 : head;
                    double tailP = Math.max(0.0, Math.min(headP, 1 - (e.endMs() - now) / approach));
                    float[] hp = layout.point(e.laneA(), headP), tp = layout.point(e.laneA(), tailP);
                    int color = Palette.lane(e.laneA());
                    float pulse = holding ? 0.25f + 0.2f * (float) Math.sin(now / 70.0) : 0f;
                    b.line(tp[0], tp[1], hp[0], hp[1], rr * 1.15f, Palette.withAlpha(color, (0.32f + pulse) * alpha));
                    b.line(tp[0], tp[1], hp[0], hp[1], rr * 0.35f, Palette.withAlpha(0xFFFFFF, (0.35f + pulse) * alpha));
                    b.disc(tp[0], tp[1], rr * 0.55f, Palette.withAlpha(color, 0.85f * alpha));
                    rune(b, layout, e.laneA(), headP, rr, alpha, holding);
                }
            }
        }
    }

    private static void rune(VectorBatch b, RitualLayout layout, int lane, double p, float rr, float alpha, boolean locked) {
        if (alpha <= 0.01f) return;
        float[] pos = layout.point(lane, p);
        int color = Palette.lane(lane);
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_RUNES);
        int edge = contrast ? Palette.withAlpha(0x000000, alpha) : Palette.withAlpha(Palette.mix(color, 0xFFFFFF, 0.55f), alpha);
        if (locked) b.disc(pos[0], pos[1], rr * 1.5f, Palette.withAlpha(color, 0.3f * alpha));
        RuneGlyphs.rune(b, lane, pos[0], pos[1], rr, Palette.withAlpha(color, alpha), edge, contrast ? 2.2f : 1.3f);
    }

    private static void effects(VectorBatch b, ClientRitual r, RitualLayout layout, long now) {
        float rr = layout.runeRadius();
        for (ClientRitual.Flash f : r.flashes) {
            float t = (now - f.atMs()) / 500f;
            if (t < 0 || t > 1) continue;
            float[] a = layout.anchor(f.lane());
            int color = Palette.lane(f.lane());
            if (f.broken() || f.judgement() == Judgement.MISS) {
                float s = rr * 0.9f, alpha = 0.9f * (1 - t);
                b.line(a[0] - s, a[1] - s, a[0] + s, a[1] + s, 1.6f, Palette.withAlpha(0xE8E8E8, alpha));
                b.line(a[0] - s, a[1] + s, a[0] + s, a[1] - s, 1.6f, Palette.withAlpha(0xE8E8E8, alpha));
                continue;
            }
            switch (f.judgement()) {
                case PERFECT -> {
                    if (t < 0.25f) RuneGlyphs.filled(b, f.lane(), a[0], a[1], rr * 1.12f, Palette.withAlpha(0xFFFFFF, 0.85f * (1 - t * 4)));
                    b.circle(a[0], a[1], rr * (1.2f + 1.3f * t), 2f, Palette.withAlpha(Palette.mix(color, 0xFFFFFF, 0.6f), 0.95f * (1 - t)));
                    b.circle(a[0], a[1], rr * (1.1f + 0.7f * t), 1f, Palette.withAlpha(0xFFFFFF, 0.8f * (1 - t)));
                }
                case GOOD -> b.circle(a[0], a[1], rr * (1.2f + 0.8f * t), 1.6f, Palette.withAlpha(color, 0.8f * (1 - t)));
                case GRAZE -> {
                    float flicker = ((now / 45) % 2 == 0) ? 0.8f : 0.2f;
                    RuneGlyphs.outline(b, f.lane(), a[0], a[1], rr * 1.3f, 1.2f, Palette.withAlpha(color, flicker * (1 - t)));
                    for (int k = 0; k < 3; k++) {
                        double ang = k * 2.1 + f.lane();
                        b.line(a[0], a[1], a[0] + (float) Math.cos(ang) * rr * 1.2f, a[1] + (float) Math.sin(ang) * rr * 1.2f, 1f,
                                Palette.withAlpha(0xDDDDDD, 0.6f * (1 - t)));
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void shards(VectorBatch b, ClientRitual r, RitualLayout layout) {
        for (ClientRitual.Shard s : r.shards) {
            float[] a = layout.anchor(s.lane);
            float alpha = Math.max(0, s.life / s.maxLife);
            RuneGlyphs.filled(b, s.lane, a[0] + s.ox, a[1] + s.oy, s.size, Palette.withAlpha(Palette.lane(s.lane), alpha));
        }
    }

    private static void glyphRing(GuiGraphics g, Font font, RitualLayout layout, Palette.Theme theme, long now, float stability, boolean reduced) {
        float[] c = layout.center(), a = layout.anchor(0);
        float radius = (float) Math.hypot(a[0] - c[0], a[1] - c[1]) * 1.3f;
        int count = 28;
        float spin = reduced ? 0 : now / 14000f * (float) (Math.PI * 2);
        int color = Palette.text(theme.glyph(), 0.12f + 0.3f * stability);
        for (int i = 0; i < count; i++) {
            float angle = spin + (float) (Math.PI * 2 * i / count);
            Component glyph = Component.literal(String.valueOf(GLYPHS.charAt((i * 7) % GLYPHS.length()))).withStyle(Style.EMPTY.withFont(ALT_FONT));
            g.pose().pushPose();
            g.pose().translate(c[0] + Math.cos(angle) * radius, c[1] + Math.sin(angle) * radius, 0);
            g.pose().mulPose(Axis.ZP.rotation(angle + (float) (Math.PI / 2)));
            g.drawString(font, glyph, -font.width(glyph) / 2, -4, color, false);
            g.pose().popPose();
        }
    }

    private static void keyLabels(GuiGraphics g, Font font, ClientRitual r, RitualLayout layout) {
        for (int lane = 0; lane < r.pattern.anchors(); lane++) {
            float[] p = layout.keyLabel(lane);
            Component key = RitualKeyMappings.label(lane, r.pattern.anchors());
            int tw = font.width(key);
            int x = Math.round(p[0] - tw / 2f), y = Math.round(p[1] - 4);
            StonebornGui.well(g, x - 4, y - 3, tw + 8, 16);
            g.drawString(font, key, x, y, r.isDown(lane) ? Palette.lane(lane) | 0xFF000000 : StonebornGui.TEXT, false);
        }
    }

    private static void judgementLabels(GuiGraphics g, Font font, ClientRitual r, RitualLayout layout, long now) {
        if (!ClientConfig.get(ClientConfig.SHOW_TIMING_LABELS)) return;
        for (ClientRitual.Label label : r.labels) {
            long age = now - label.atMs();
            if (age < 0 || age > 650) continue;
            float alpha = 1f - age / 650f;
            float[] p = layout.judgementLabel(label.lane());
            Component text = Component.translatable("immersive_enchanting.judgement." + label.judgement().id());
            int color = switch (label.judgement()) {
                case PERFECT -> 0xFFE9A8;
                case GOOD -> 0xC8F7FF;
                case GRAZE -> 0xC0C0C0;
                case MISS -> 0xFF9C9C;
            };
            int y = Math.round(p[1] - age / 60f);
            g.drawString(font, text, Math.round(p[0] - font.width(text) / 2f), y, Palette.text(color, alpha), true);
        }
    }

    private static void hud(GuiGraphics g, Font font, ClientRitual r, RitualLayout layout, Palette.Theme theme, long now, int w, int h,
                            boolean awaiting) {
        RitualPattern pattern = r.pattern;
        int top = 12;
        Component title = Component.translatable("immersive_enchanting.ritual.title",
                Component.translatable("immersive_enchanting.tier." + DifficultyTier.byIndex(r.params.tier()).id()));
        g.drawCenteredString(font, StonebornGui.clipped(font, title, w - 32), w / 2, top, StonebornGui.TEXT_WARM);
        if (r.clue != null) {
            g.drawCenteredString(font, StonebornGui.clipped(font, Component.translatable("immersive_enchanting.ritual.clue", r.clue), w - 32), w / 2, top + 12, StonebornGui.TEXT_DIM);
        }
        float progress = (float) Math.max(0, Math.min(1, (now - pattern.countInMs()) / (double) Math.max(1, pattern.endMs() - pattern.countInMs())));
        int barW = Math.min(160, w / 3), bx = w / 2 - barW / 2, by = top + 28;
        g.fill(bx, by, bx + barW, by + 2, StonebornGui.INSET);
        g.fill(bx, by, bx + Math.round(barW * progress), by + 2, StonebornGui.BRASS_LIGHT);

        float[] c = layout.center();
        if (now < pattern.countInMs()) {
            int beatsLeft = (int) Math.ceil((pattern.countInMs() - now) / pattern.beatMs());
            Component n = Component.literal(String.valueOf(Math.max(1, beatsLeft)));
            g.pose().pushPose();
            g.pose().translate(c[0], c[1] - 9, 0);
            g.pose().scale(2.5f, 2.5f, 1f);
            g.drawString(font, n, -font.width(n) / 2, -3, 0xFFFFFFFF, true);
            g.pose().popPose();
            Component hint = Component.translatable("immersive_enchanting.ritual.hint");
            int hy = layout.radial() ? Math.round(c[1] + layout.runeRadius() * 4.2f) : Math.round(c[1] + 24);
            g.drawCenteredString(font, StonebornGui.clipped(font, hint, w - 40), w / 2, hy, StonebornGui.TEXT);
        } else if (awaiting) {
            g.drawCenteredString(font, Component.translatable("immersive_enchanting.ritual.binding"), Math.round(c[0]), Math.round(c[1] - 4), 0xFFFFFFFF);
        }

        int bottom = h - 31;
        MutableComponent status = Component.empty();
        if (r.engine.combo() >= 2) status.append(Component.translatable("immersive_enchanting.ritual.combo", r.engine.combo()));
        if (ClientConfig.get(ClientConfig.SHOW_NUMERIC_SCORE) && r.engine.judgedCount() > 0) {
            if (r.engine.combo() >= 2) status.append("   ");
            status.append(Component.translatable("immersive_enchanting.ritual.accuracy", Math.round(r.engine.runningAccuracy())));
        }
        g.drawCenteredString(font, status, w / 2, bottom, StonebornGui.TEXT);
        if (r.escArmedUntilMs > now) {
            g.drawCenteredString(font, Component.translatable("immersive_enchanting.ritual.abandon_confirm"), w / 2, bottom + 11, 0xFFFFB0A0);
        } else if (now < pattern.countInMs()) {
            g.drawCenteredString(font, Component.translatable("immersive_enchanting.ritual.cancel_hint"), w / 2, bottom + 11, StonebornGui.TEXT_DIM);
        }
    }

    // ---- result ---------------------------------------------------------------------------------------------------

    public static void renderResult(GuiGraphics g, ResultView view, int w, int h) {
        Font font = Minecraft.getInstance().font;
        boolean scores = ClientConfig.get(ClientConfig.SHOW_NUMERIC_SCORE) && !view.abandoned();
        int panelW = 320;
        Component subtitle = view.practice() ? Component.translatable("immersive_enchanting.result.practice")
                : view.abandoned() ? Component.translatable("immersive_enchanting.result.abandoned")
                : Component.translatable("immersive_enchanting.result." + view.band().id() + ".description");
        List<FormattedCharSequence> subtitleLines = font.split(subtitle, panelW - 58);
        List<Component> details = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (scores) {
            details.add(Component.translatable("immersive_enchanting.result.score", (int) Math.floor(view.score()),
                    Math.round(view.accuracy()), view.longestCombo()));
            colors.add(StonebornGui.TEXT);
            details.add(Component.translatable("immersive_enchanting.result.counts", view.perfect(), view.good(), view.graze(), view.miss()));
            colors.add(StonebornGui.TEXT_DIM);
        }
        for (ResultView.Line line : view.lines()) {
            details.add(switch (line.fate()) {
                case KEPT -> Component.translatable("immersive_enchanting.result.kept", line.name());
                case WEAKENED -> Component.translatable("immersive_enchanting.result.weakened", line.name(),
                        Component.translatable("enchantment.level." + line.toLevel()));
                case LOST -> Component.translatable("immersive_enchanting.result.lost", line.name());
            });
            colors.add(switch (line.fate()) {
                case KEPT -> 0xFFB6F2C0;
                case WEAKENED -> 0xFFF2DDA0;
                case LOST -> 0xFFF2A8A8;
            });
        }
        if (!view.practice()) {
            details.add(view.xpLevels() == 0 && view.xpPoints() == 0 && view.lapis() == 0
                    ? Component.translatable("immersive_enchanting.result.no_cost")
                    : view.xpPoints() > 0 ? Component.translatable("immersive_enchanting.result.cost_points", view.xpPoints(), view.lapis())
                    : Component.translatable("immersive_enchanting.result.cost", view.xpLevels(), view.lapis()));
            colors.add(StonebornGui.TEXT_DIM);
        }
        List<List<FormattedCharSequence>> rows = details.stream().map(c -> font.split(c, panelW - 32)).toList();
        int bodyY = 34 + subtitleLines.size() * 11;
        int footerY = bodyY + rows.stream().mapToInt(row -> row.size() * 11 + 4).sum() + 10;
        Component footer = Component.translatable(view.practice() ? "immersive_enchanting.result.practice_continue" : "immersive_enchanting.result.continue");
        List<FormattedCharSequence> footerLines = font.split(footer, panelW - 32);
        int panelH = footerY + footerLines.size() * 11 + 12;
        int accent = bandColor(view.band());
        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        dim(g, w, h);
        fit(g, w, h, panelW, panelH);
        StonebornGui.panel(g, 0, 0, panelW, panelH);
        g.fill(6, 6, panelW - 6, bodyY - 3, StonebornGui.SURFACE);
        StonebornGui.well(g, 10, bodyY - 2, panelW - 20, footerY - bodyY + 1);
        g.fill(10, footerY + 3, panelW - 10, panelH - 8, StonebornGui.SURFACE);
        VectorBatch b = BATCH.begin(g);
        emblem(b, view.band(), 24, 23, 11, accent, view.ageMs());
        b.end();
        g.pose().translate(0, 0, 1);
        Component title = Component.translatable("immersive_enchanting.result." + view.band().id());
        g.drawString(font, StonebornGui.clipped(font, title, panelW - 58), 44, 12, accent | 0xFF000000, false);
        for (int i = 0; i < subtitleLines.size(); i++) g.drawString(font, subtitleLines.get(i), 44, 25 + i * 11, StonebornGui.TEXT_DIM, false);
        int y = bodyY + 4;
        for (int i = 0; i < rows.size(); i++) {
            for (FormattedCharSequence line : rows.get(i)) {
                g.drawString(font, line, 16, y, colors.get(i), false);
                y += 11;
            }
            y += 4;
        }
        for (int i = 0; i < footerLines.size(); i++) g.drawCenteredString(font, footerLines.get(i), panelW / 2, footerY + 5 + i * 11, StonebornGui.TEXT_DIM);
        g.pose().popPose();
    }

    /** A small sigil whose completeness mirrors the band, so the result reads without its color. */
    private static void emblem(VectorBatch b, OutcomeBand band, float cx, float cy, float r, int color, long ageMs) {
        float grow = Math.min(1f, ageMs / 400f);
        int c = Palette.withAlpha(color, 0.95f);
        float full = (float) (Math.PI * 2);
        switch (band) {
            case PERFECT -> {
                b.circle(cx, cy, r, 2f, c);
                b.circle(cx, cy, r * 0.62f, 1.2f, c);
                RuneGlyphs.filled(b, 3, cx, cy, r * 0.5f * grow, c);
            }
            case STABLE -> {
                b.circle(cx, cy, r, 2f, c);
                b.disc(cx, cy, r * 0.28f * grow, c);
            }
            case FRAYED -> {
                for (int i = 0; i < 6; i++) b.ring(cx, cy, r - 1, r + 1, full * i / 6f, full * i / 6f + full / 6f * 0.72f, c, 6);
                b.disc(cx, cy, r * 0.22f, c);
            }
            case WEAK -> {
                for (int i = 0; i < 4; i++) b.ring(cx, cy, r - 1, r + 1, full * i / 4f, full * i / 4f + full / 4f * 0.4f, c, 4);
            }
            case FAILED -> {
                b.ring(cx, cy, r - 1, r + 1, 0.3f, full * 0.42f, c, 10);
                b.ring(cx, cy, r - 1, r + 1, full * 0.55f, full * 0.9f, c, 10);
            }
            case SHATTERED -> {
                for (int i = 0; i < 5; i++) {
                    double a = i * 1.26 + 0.3;
                    float d = r * (0.5f + 0.5f * grow);
                    RuneGlyphs.filled(b, 1, cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d, r * 0.22f, c);
                }
            }
        }
    }

    private static int bandColor(OutcomeBand band) {
        return switch (band) {
            case PERFECT -> 0xFFD76E;
            case STABLE -> 0x8FE3A6;
            case FRAYED -> 0xE8C07A;
            case WEAK -> 0xD9A06A;
            case FAILED -> 0xC98A8A;
            case SHATTERED -> 0xB07A9A;
        };
    }

    // ---- small overlays -------------------------------------------------------------------------------------------

    /** "Preparing the ritual..." while waiting for the server. */
    public static void renderPending(GuiGraphics g, int w, int h) {
        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        dim(g, w, h);
        fit(g, w, h, 280, 58);
        StonebornGui.dialog(g, 0, 0, 280, 58);
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(Component.translatable("immersive_enchanting.ritual.preparing"), 252);
        for (int i = 0; i < lines.size(); i++) g.drawCenteredString(font, lines.get(i), 140, 20 + i * 11, StonebornGui.TEXT_WARM);
        g.pose().popPose();
    }

    public static void renderToast(GuiGraphics g, Component message, int w, float alpha) {
        if (alpha <= 0F) return;
        Font font = Minecraft.getInstance().font;
        int tw = Math.min(w - 40, font.width(message));
        List<FormattedCharSequence> lines = font.split(message, tw);
        int x = (w - tw) / 2 - 8, bottom = 20 + lines.size() * 11;
        g.pose().pushPose();
        g.pose().translate(0, 0, Z + 10);
        g.fill(x, 8, x + tw + 16, bottom, Palette.withAlpha(StonebornGui.EDGE, alpha));
        g.fill(x + 1, 9, x + tw + 15, bottom - 1, Palette.withAlpha(StonebornGui.BORDER, alpha));
        g.fill(x + 2, 10, x + tw + 14, bottom - 2, Palette.withAlpha(StonebornGui.SURFACE, alpha));
        for (int i = 0; i < lines.size(); i++) g.drawCenteredString(font, lines.get(i), w / 2, 14 + i * 11, Palette.text(StonebornGui.TEXT_WARM, alpha));
        g.pose().popPose();
    }

    /** Six-step complexity gauge in the margin right of each enchanting offer (tier 0 = one step). */
    public static void renderOfferGauge(GuiGraphics g, int left, int top, byte[] tiers) {
        for (int offer = 0; offer < 3 && offer < tiers.length; offer++) {
            int tier = tiers[offer];
            if (tier < 0) continue;
            int x = left + 169, y = top + 14 + 19 * offer + 1;
            for (int s = 0; s < 6; s++) {
                int sy = y + 15 - s * 3;
                g.fill(x, sy, x + 3, sy + 2, s <= tier ? StonebornGui.BRASS_LIGHT : StonebornGui.INSET);
            }
        }
    }

    private static void fit(GuiGraphics g, int w, int h, int panelW, int panelH) {
        float scale = Math.min(1F, Math.min((w - 16F) / panelW, (h - 16F) / panelH));
        scale = Math.max(0.1F, scale);
        g.pose().translate((w - panelW * scale) / 2F, (h - panelH * scale) / 2F, 0);
        g.pose().scale(scale, scale, 1F);
    }

    private static void dim(GuiGraphics g, int w, int h) {
        float dim = (float) ClientConfig.get(ClientConfig.BACKGROUND_DIM);
        if (dim > 0.001f) g.fill(0, 0, w, h, Palette.withAlpha(0x151310, dim));
        g.flush();
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private RitualRenderer() {}
}
