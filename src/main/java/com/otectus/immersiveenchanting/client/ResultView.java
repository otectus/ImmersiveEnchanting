package com.otectus.immersiveenchanting.client;

import com.otectus.immersiveenchanting.network.RitualResolvedS2C;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import com.otectus.immersiveenchanting.ritual.ScoreResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/** What the result panel shows: the band, the score, and the fate of every enchantment in the plan. */
public record ResultView(OutcomeBand band, double score, double accuracy, int perfect, int good, int graze, int miss,
                         int longestCombo, boolean abandoned, boolean practice, List<Line> lines, int xpLevels,
                         int xpPoints, int lapis, long shownAtMs) {

    public enum Fate { KEPT, WEAKENED, LOST }

    public record Line(Component name, Fate fate, int fromLevel, int toLevel, ResourceLocation id) {}

    public static ResultView of(RitualResolvedS2C msg) {
        List<Line> lines = new ArrayList<>();
        for (RitualResolvedS2C.Entry planned : msg.planned()) {
            RitualResolvedS2C.Entry bound = null;
            for (RitualResolvedS2C.Entry b : msg.bound()) {
                if (b.planIndex() == planned.planIndex()) {
                    bound = b;
                    break;
                }
            }
            Fate fate = bound == null ? Fate.LOST : bound.level() < planned.level() ? Fate.WEAKENED : Fate.KEPT;
            lines.add(new Line(name(planned.id(), planned.level()), fate, planned.level(), bound == null ? 0 : bound.level(), planned.id()));
        }
        return new ResultView(msg.band(), msg.score(), msg.accuracy(), msg.perfect(), msg.good(), msg.graze(), msg.miss(),
                msg.longestCombo(), msg.abandoned(), false, List.copyOf(lines), msg.xpLevels(), msg.xpPoints(), msg.lapis(),
                System.currentTimeMillis());
    }

    public static ResultView practice(OutcomeBand band, ScoreResult score) {
        return new ResultView(band, score.finalScore(), score.accuracy(), score.perfect(), score.good(), score.graze(), score.miss(),
                score.longestCombo(), false, true, List.of(), 0, 0, 0, System.currentTimeMillis());
    }

    public static Component name(ResourceLocation id, int level) {
        Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
        if (enchantment == null) return Component.literal(id + " " + level);
        return enchantment.getFullname(level);
    }

    public long ageMs() {
        return System.currentTimeMillis() - shownAtMs;
    }
}
