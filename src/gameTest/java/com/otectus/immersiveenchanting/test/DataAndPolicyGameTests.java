package com.otectus.immersiveenchanting.test;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.api.CostSnapshot;
import com.otectus.immersiveenchanting.api.DifficultyScale;
import com.otectus.immersiveenchanting.api.ItemFingerprint;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.config.ServerConfig;
import com.otectus.immersiveenchanting.data.EnchantmentProfileManager;
import com.otectus.immersiveenchanting.data.PatternSetManager;
import com.otectus.immersiveenchanting.data.ResolvedProfile;
import com.otectus.immersiveenchanting.enchanting.EnchantGuard;
import com.otectus.immersiveenchanting.enchanting.OfferPlanner;
import com.otectus.immersiveenchanting.enchanting.VanillaEnchantingAdapter;
import com.otectus.immersiveenchanting.network.OfferDifficultyS2C;
import com.otectus.immersiveenchanting.network.RitualAbortedS2C;
import com.otectus.immersiveenchanting.network.RitualInputBatchC2S;
import com.otectus.immersiveenchanting.network.RitualResolvedS2C;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;
import com.otectus.immersiveenchanting.ritual.DowngradeResolver;
import com.otectus.immersiveenchanting.ritual.PatternGenerator;
import com.otectus.immersiveenchanting.ritual.PatternSetDef;
import com.otectus.immersiveenchanting.ritual.PatternValidator;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import com.otectus.immersiveenchanting.session.SessionEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ImmersiveEnchanting.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataAndPolicyGameTests {

    /** Exact beats namespace beats broad; higher priority wins within a layer; each profile overrides only its fields. */
    @GameTest(template = "empty")
    public static void datapackProfilesFollowPrecedence(GameTestHelper h) {
        ResolvedProfile p = EnchantmentProfileManager.resolve(TestContent.odd);
        h.assertTrue(p.multiplier() == 1.4, "higher-priority exact profile sets the multiplier, got " + p.multiplier());
        h.assertTrue(p.offset() == 5.0, "namespace profile's offset overrides the broad one, got " + p.offset());
        h.assertTrue(p.maxTier() == 4, "broad profile's max_tier survives (nothing more specific sets it), got " + p.maxTier());
        h.assertTrue(p.holdBias() == 0.5, "hold bias from the priority-10 exact profile, got " + p.holdBias());
        h.assertTrue(p.theme().equals(ImmersiveEnchanting.id("storm")), "theme from the namespace profile, got " + p.theme());
        h.assertTrue(p.sources().equals(List.of(new ResourceLocation("ie_test", "rare_things"), new ResourceLocation("ie_test", "namespace"),
                new ResourceLocation("ie_test", "odd_exact"), new ResourceLocation("ie_test", "odd_exact_priority"))),
                "applied least to most specific: " + p.sources());
        ResolvedProfile flame = EnchantmentProfileManager.resolve(Enchantments.FLAMING_ARROWS);
        h.assertTrue(flame.theme().equals(ImmersiveEnchanting.id("ember")), "bundled theme profile applies to vanilla flame");
        ResolvedProfile sharpness = EnchantmentProfileManager.resolve(Enchantments.SHARPNESS);
        h.assertTrue(sharpness.isGeneric(), "an enchantment no profile names falls back to the generic profile");
        h.assertTrue(PatternSetManager.has(new ResourceLocation("ie_test", "flowing")), "flat pattern set loaded");
        h.assertTrue(!PatternSetManager.has(new ResourceLocation("ie_test", "broken_set")), "invalid pattern set rejected");
        h.assertTrue(PatternSetManager.has(PatternSetManager.DEFAULT_ID), "default always present");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flatPatternSetsGenerateValidRituals(GameTestHelper h) {
        PatternSetDef set = PatternSetManager.get(new ResourceLocation("ie_test", "flowing"));
        h.assertTrue(set.flat(), "flat form");
        for (double score : new double[]{5, 30, 55, 80, 99}) {
            var params = com.otectus.immersiveenchanting.ritual.ParamsResolver.resolve(set, score,
                    com.otectus.immersiveenchanting.ritual.ParamsResolver.Tuning.DEFAULT, com.otectus.immersiveenchanting.ritual.ParamsResolver.Shaping.NONE);
            for (long seed = 0; seed < 50; seed++) {
                RitualPattern pattern = PatternGenerator.generate(params, seed);
                h.assertTrue(PatternValidator.validate(pattern, params).isEmpty(), "valid at " + score);
            }
        }
        h.succeed();
    }

    /** A synthetic plan with the odd enchantment goes through planning, difficulty and downgrade without special cases. */
    @GameTest(template = "empty")
    public static void unusualEnchantmentPlansGenerically(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        OfferSnapshot snapshot = new OfferSnapshot(1, 2, 30, new CostSnapshot(3, 0, 3, 30), TestContent.ODD_ID, 5,
                List.of(new EnchantmentInstance(TestContent.odd, 5), new EnchantmentInstance(Enchantments.SHARPNESS, 3)),
                ItemFingerprint.of(new ItemStack(Items.IRON_SWORD)), 12345, DifficultyScale.VANILLA, false, new CompoundTag());
        OfferPlanner.Plan plan = OfferPlanner.plan(p, VanillaEnchantingAdapter.INSTANCE, snapshot, 1.0, true);
        h.assertTrue(Double.isFinite(plan.difficulty().score()), "finite complexity");
        OfferSnapshot alone = new OfferSnapshot(1, 2, 60, new CostSnapshot(3, 0, 3, 60), TestContent.ODD_ID, 6,
                List.of(new EnchantmentInstance(TestContent.odd, 6)), ItemFingerprint.of(new ItemStack(Items.IRON_SWORD)), 1,
                DifficultyScale.VANILLA, false, new CompoundTag());
        h.assertTrue(OfferPlanner.plan(p, VanillaEnchantingAdapter.INSTANCE, alone, 1.0, true).tier() <= 4,
                "the odd enchantment's profile caps its own ritual at tier 4");
        h.assertTrue(plan.enchantments().get(0).primary(), "the clue enchantment is the primary binding");
        for (double v : plan.enchantments().get(0).arcaneValues()) h.assertTrue(Double.isFinite(v) && v > 0, "arcane values are sane despite a falling cost curve");
        List<DowngradeResolver.Kept> weak = DowngradeResolver.resolve(plan.downgradeEntries(), 0.45);
        h.assertTrue(weak.stream().anyMatch(k -> k.id().equals(TestContent.ODD_ID.toString()) && k.level() >= 2), "primary kept at or above its minimum level 2");
        h.assertTrue(PatternValidator.validate(PatternGenerator.generate(plan.params(), plan.seed()), plan.params()).isEmpty(), "pattern valid");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void offerPreviewReportsTiersOnlyWhenOffersChange(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_BOOTS), 10);
        SessionEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
        OfferDifficultyS2C preview = TestSupport.last(p, OfferDifficultyS2C.class);
        h.assertTrue(preview != null && preview.containerId() == menu.containerId, "preview sent for the open menu");
        for (int k = 0; k < 3; k++) {
            h.assertTrue(preview.tiers()[k] >= (menu.costs[k] > 0 ? 0 : -1) && preview.tiers()[k] <= 5, "tier in range for offer " + k);
        }
        SessionEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
        h.assertTrue(TestSupport.sent(p, OfferDifficultyS2C.class).size() == 1, "unchanged offers are not re-sent");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void guardRefusesDirectEnchantingOnlyForOffers(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_SWORD), 10);
        h.assertTrue(EnchantGuard.shouldBlock(p, menu, 0), "offer buttons are guarded while rituals apply");
        h.assertTrue(!EnchantGuard.shouldBlock(p, menu, 4), "other buttons are never guarded");
        h.assertTrue(!EnchantGuard.shouldBlock(p, p.inventoryMenu, 0), "unsupported menus are never guarded");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "config")
    public static void creativeBypassEnchantsDirectly(GameTestHelper h) {
        boolean before = ServerConfig.CREATIVE_BYPASS.get();
        ServerConfig.CREATIVE_BYPASS.set(true);
        try {
            FakePlayer p = TestSupport.player(h);
            p.setGameMode(GameType.CREATIVE);
            TestSupport.table(h);
            EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_SWORD), 10);
            h.assertTrue(!EnchantGuard.shouldBlock(p, menu, 2), "exempt players are not guarded");
            RitualSessionManager.start(p, menu.containerId, 2, 1f);
            RitualAbortedS2C reply = TestSupport.last(p, RitualAbortedS2C.class);
            h.assertTrue(reply != null && reply.reason() == AbortReason.BYPASSED, "server enchanted directly");
            h.assertTrue(TestSupport.last(p, RitualStartedS2C.class) == null, "no ritual");
            h.assertTrue(!TestSupport.enchantments(menu.getSlot(0).getItem()).isEmpty(), "the vanilla enchant happened");
        } finally {
            ServerConfig.CREATIVE_BYPASS.set(before);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "config", timeoutTicks = 900)
    public static void lapisOnlyFailuresKeepLevels(GameTestHelper h) {
        ServerConfig.FailureCostMode before = ServerConfig.FAILURE_COST_MODE.get();
        ServerConfig.FAILURE_COST_MODE.set(ServerConfig.FailureCostMode.LAPIS_ONLY);
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_SHOVEL), 10);
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            try {
                RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, List.of()));
                RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
                h.assertTrue(resolved != null && resolved.band().isFailure(), "failed");
                h.assertTrue(p.experienceLevel == 30, "LAPIS_ONLY keeps levels, have " + p.experienceLevel);
                h.assertTrue(menu.getSlot(1).getItem().getCount() == 7, "LAPIS_ONLY takes the lapis");
            } finally {
                ServerConfig.FAILURE_COST_MODE.set(before);
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "config2", timeoutTicks = 900)
    public static void freeFailuresChargeNothingButStillReroll(GameTestHelper h) {
        ServerConfig.FailureCostMode before = ServerConfig.FAILURE_COST_MODE.get();
        ServerConfig.FAILURE_COST_MODE.set(ServerConfig.FailureCostMode.NONE);
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.IRON_BOOTS), 10);
        int seed = p.getEnchantmentSeed();
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            try {
                RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, List.of()));
                RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
                h.assertTrue(resolved != null && resolved.band().isFailure(), "failed");
                h.assertTrue(p.experienceLevel == 30 && menu.getSlot(1).getItem().getCount() == 10, "NONE charges nothing");
                h.assertTrue(p.getEnchantmentSeed() != seed, "the offers still reroll (advanceSeedOnFailure)");
            } finally {
                ServerConfig.FAILURE_COST_MODE.set(before);
            }
            h.succeed();
        });
    }

    private DataAndPolicyGameTests() {}
}
