package com.otectus.immersiveenchanting.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.data.EnchantmentProfile;
import com.otectus.immersiveenchanting.data.EnchantmentProfileManager;
import com.otectus.immersiveenchanting.data.PatternSetManager;
import com.otectus.immersiveenchanting.data.ResolvedProfile;
import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersiveenchanting.enchanting.OfferPlanner;
import com.otectus.immersiveenchanting.ritual.ArcaneValue;
import com.otectus.immersiveenchanting.ritual.Autoplay;
import com.otectus.immersiveenchanting.ritual.DifficultyCalculator;
import com.otectus.immersiveenchanting.ritual.DifficultyTier;
import com.otectus.immersiveenchanting.ritual.ParamsResolver;
import com.otectus.immersiveenchanting.ritual.PatternGenerator;
import com.otectus.immersiveenchanting.ritual.PatternSetDef;
import com.otectus.immersiveenchanting.ritual.RitualEvent;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.ritual.ScoreEngine;
import com.otectus.immersiveenchanting.session.RitualSession;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Operator tools (permission level 2). Output reveals hidden enchantments, so it is never shown to ordinary players.
 * <pre>
 * /immersiveenchanting debug offer [index]
 * /immersiveenchanting debug enchantment &lt;id&gt; [level]
 * /immersiveenchanting debug pattern &lt;tier&gt; [seed]
 * /immersiveenchanting session inspect &lt;player&gt;
 * /immersiveenchanting session abort &lt;player&gt;
 * </pre>
 */
public final class RitualCommands {
    private static final String KEY = "immersive_enchanting.command.";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("immersiveenchanting")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("offer")
                                .executes(c -> debugOffer(c, -1))
                                .then(Commands.argument("index", IntegerArgumentType.integer(0, 2))
                                        .executes(c -> debugOffer(c, IntegerArgumentType.getInteger(c, "index")))))
                        .then(Commands.literal("enchantment")
                                .then(Commands.argument("enchantment", ResourceArgument.resource(context, Registries.ENCHANTMENT))
                                        .executes(c -> debugEnchantment(c, -1))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 255))
                                                .executes(c -> debugEnchantment(c, IntegerArgumentType.getInteger(c, "level"))))))
                        .then(Commands.literal("pattern")
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5))
                                        .executes(c -> debugPattern(c, 0L))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(c -> debugPattern(c, LongArgumentType.getLong(c, "seed")))))))
                .then(Commands.literal("session")
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("player", EntityArgument.player()).executes(RitualCommands::inspect)))
                        .then(Commands.literal("abort")
                                .then(Commands.argument("player", EntityArgument.player()).executes(RitualCommands::abort)))));
    }

    private static int debugOffer(CommandContext<CommandSourceStack> c, int only) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<EnchantingContextAdapter> owner = AdapterRegistry.find(player, player.containerMenu);
        if (owner.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "debug.no_table", player.containerMenu.getClass().getName()));
            return 0;
        }
        EnchantingContextAdapter adapter = owner.get();
        int shown = 0;
        for (int offer = 0; offer < 3; offer++) {
            if (only >= 0 && offer != only) continue;
            int index = offer;
            Optional<OfferSnapshot> snapshot = adapter.snapshot(player, player.containerMenu, offer);
            if (snapshot.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(KEY + "debug.offer.empty", index), false);
                continue;
            }
            shown++;
            OfferSnapshot s = snapshot.get();
            OfferPlanner.Plan plan = OfferPlanner.plan(player, adapter, s, 1.0, true);
            DifficultyCalculator.Breakdown d = plan.difficulty();
            RitualParams p = plan.params();
            source.sendSuccess(() -> Component.translatable(KEY + "debug.offer.header", s.offerIndex(), adapter.id().toString()), false);
            source.sendSuccess(() -> Component.translatable(KEY + "debug.offer.cost", s.displayedRequirement(), s.cost().xpLevels(),
                    s.cost().xpPoints(), s.cost().lapis(), fmt(s.difficultyScale().referenceMaxCost())), false);
            for (OfferPlanner.PlannedEnchantment e : plan.enchantments()) {
                source.sendSuccess(() -> Component.translatable(KEY + "debug.offer.enchantment", e.id().toString(), e.instance().level,
                        e.instance().enchantment.getRarity().name().toLowerCase(Locale.ROOT), fmt(e.value()),
                        e.primary() ? Component.translatable(KEY + "debug.primary") : Component.empty(),
                        e.profile().isGeneric() ? Component.translatable(KEY + "debug.generic") : Component.literal(e.profile().sources().toString())), false);
            }
            source.sendSuccess(() -> Component.translatable(KEY + "debug.offer.components", fmt(d.powerFactor()), fmt(d.levelFactor()),
                    fmt(d.rarityFactor()), fmt(d.multiFactor()), fmt(d.specialFactor()), fmt(d.base()), fmt(d.multiplier()), fmt(d.offset())), false);
            source.sendSuccess(() -> Component.translatable(KEY + "debug.offer.result", fmt(d.score()),
                    Component.translatable("immersive_enchanting.tier." + d.tier().id()), plan.patternSetId().toString(), p.targetEvents(),
                    fmt(p.bpm()), p.perfectMs(), p.goodMs(), p.grazeMs(), plan.theme().toString()), false);
        }
        return shown;
    }

    private static int debugEnchantment(CommandContext<CommandSourceStack> c, int levelArg) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        Holder.Reference<Enchantment> holder = ResourceArgument.getEnchantment(c, "enchantment");
        Enchantment e = holder.value();
        ResourceLocation id = holder.key().location();
        EnchantingContextAdapter range = AdapterRegistry.widestLevelRange(e);
        int maxLevel = Math.max(e.getMinLevel(), range.maxLevel(e));
        boolean treasure = range.isTreasure(e);
        int level = levelArg > 0 ? levelArg : maxLevel;
        ResolvedProfile profile = EnchantmentProfileManager.resolve(e);
        List<EnchantmentProfile> matching = EnchantmentProfileManager.matching(e);
        source.sendSuccess(() -> Component.translatable(KEY + "debug.enchantment.header", id.toString(),
                e.getRarity().name().toLowerCase(Locale.ROOT), e.getMinLevel(), maxLevel, e.isCurse(), treasure), false);
        if (matching.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "debug.enchantment.generic"), false);
        } else {
            for (EnchantmentProfile p : matching) {
                source.sendSuccess(() -> Component.translatable(KEY + "debug.enchantment.profile", p.id().toString(),
                        p.layer().name().toLowerCase(Locale.ROOT), p.priority()), false);
            }
        }
        source.sendSuccess(() -> Component.translatable(KEY + "debug.enchantment.resolved", fmt(profile.multiplier()), fmt(profile.offset()),
                profile.minTier(), profile.maxTier(), fmt(profile.arcaneValueMultiplier()), profile.patternSet().toString(),
                fmt(profile.holdBias()), fmt(profile.chordBias()), profile.enabled(), profile.protectAsPrimary(), profile.theme().toString()), false);
        double[] values = ArcaneValue.byLevel(OfferPlanner.rarity(e), e.getMinLevel(), Math.max(level, e.getMinLevel()), e::getMinCost,
                profile.arcaneValueMultiplier());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) sb.append(i == 0 ? "" : ", ").append(e.getMinLevel() + i).append('=').append(fmt(values[i]));
        source.sendSuccess(() -> Component.translatable(KEY + "debug.enchantment.values", sb.toString()), false);
        DifficultyCalculator.Breakdown d = DifficultyCalculator.compute(DifficultyCalculator.Input.of(List.of(new DifficultyCalculator.Entry(
                id.toString(), OfferPlanner.rarity(e), level, e.getMinLevel(), maxLevel, e.isCurse(), treasure,
                profile.multiplier(), profile.offset(), profile.minTier(), profile.maxTier(), values[values.length - 1])), 30, 2));
        source.sendSuccess(() -> Component.translatable(KEY + "debug.enchantment.sample", level, fmt(d.score()),
                Component.translatable("immersive_enchanting.tier." + d.tier().id())), false);
        return 1;
    }

    private static int debugPattern(CommandContext<CommandSourceStack> c, long seed) {
        int tier = IntegerArgumentType.getInteger(c, "tier");
        DifficultyTier t = DifficultyTier.byIndex(tier);
        double score = (t.lowScore() + t.highScore()) / 2.0;
        PatternSetDef set = PatternSetManager.get(PatternSetManager.DEFAULT_ID);
        RitualParams params = ParamsResolver.resolve(set, score, ParamsResolver.Tuning.DEFAULT, ParamsResolver.Shaping.NONE);
        RitualPattern pattern = PatternGenerator.generate(params, seed);
        double check = ScoreEngine.score(pattern, params, Autoplay.perfect(pattern)).finalScore();
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "debug.pattern.header", tier,
                Component.translatable("immersive_enchanting.tier." + t.id()), seed, pattern.events().size(), fmt(pattern.endMs() / 1000.0),
                fmt(params.bpm()), params.anchors(), pattern.holdCount(), pattern.chordCount(), params.perfectMs(), params.goodMs(),
                params.grazeMs(), fmt(check)), false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(24, pattern.events().size()); i++) {
            RitualEvent e = pattern.events().get(i);
            sb.append(i == 0 ? "" : " ").append(switch (e.type()) {
                case TAP -> "T" + (e.laneA() + 1);
                case HOLD -> "H" + (e.laneA() + 1) + "(" + e.durationMs() + ")";
                case CHORD -> "C" + (e.laneA() + 1) + "+" + (e.laneB() + 1);
            }).append('@').append(e.timeMs());
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return pattern.events().size();
    }

    private static int inspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        Optional<RitualSession> session = RitualSessionManager.get(target);
        if (session.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "session.none", target.getDisplayName()), false);
            return 0;
        }
        RitualSession s = session.get();
        StringBuilder plan = new StringBuilder();
        for (OfferPlanner.PlannedEnchantment e : s.plan().enchantments()) {
            plan.append(plan.isEmpty() ? "" : ", ").append(e.id()).append(' ').append(e.instance().level).append(e.primary() ? "*" : "");
        }
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "session.inspect", target.getDisplayName(), s.sessionId().toString(),
                s.adapterId().toString(), s.state().name().toLowerCase(Locale.ROOT), s.elapsedMs(), s.pattern().endMs(), s.inputCount(),
                s.tier(), fmt(s.complexity()), s.patternSeed(), plan.toString()), false);
        return 1;
    }

    private static int abort(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        boolean aborted = RitualSessionManager.adminAbort(target);
        if (aborted) c.getSource().sendSuccess(() -> Component.translatable(KEY + "session.aborted", target.getDisplayName()), true);
        else c.getSource().sendSuccess(() -> Component.translatable(KEY + "session.none", target.getDisplayName()), false);
        return aborted ? 1 : 0;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private RitualCommands() {}
}
