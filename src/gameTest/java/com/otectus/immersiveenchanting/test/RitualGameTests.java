package com.otectus.immersiveenchanting.test;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.enchanting.VanillaEnchantingAdapter;
import com.otectus.immersiveenchanting.mixin.EnchantmentMenuAccessor;
import com.otectus.immersiveenchanting.network.RitualAbortedS2C;
import com.otectus.immersiveenchanting.network.RitualInputBatchC2S;
import com.otectus.immersiveenchanting.network.RitualResolvedS2C;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;
import com.otectus.immersiveenchanting.ritual.Autoplay;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.ritual.OutcomeBand;
import com.otectus.immersiveenchanting.ritual.PatternValidator;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The vanilla gate end to end on a real server: a real enchanting table and menu, the real session manager and the
 * real vanilla adapter. Only the network is replaced (fake players; messages are captured). Rituals run in real time.
 */
@GameTestHolder(ImmersiveEnchanting.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RitualGameTests {
    private static final int RITUAL_TIMEOUT = 900;

    private static Map<Enchantment, Integer> asMap(List<EnchantmentInstance> list) {
        Map<Enchantment, Integer> map = new HashMap<>();
        for (EnchantmentInstance e : list) map.put(e.enchantment, e.level);
        return map;
    }

    private static OfferSnapshot snapshot(FakePlayer p, EnchantmentMenu menu, int offer) {
        return VanillaEnchantingAdapter.INSTANCE.snapshot(p, menu, offer).orElseThrow();
    }

    private static int lapis(EnchantmentMenu menu) {
        return menu.getSlot(1).getItem().getCount();
    }

    /** The invoker returns exactly what the table's own enchant button applies. */
    @GameTest(template = "empty")
    public static void invokerCapturesTheExactPlan(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_SWORD), 64);
        h.assertTrue(menu.costs[2] >= 20, "15 bookshelves give a high third offer, got " + menu.costs[2]);
        OfferSnapshot s = snapshot(p, menu, 2);
        h.assertTrue(!s.plannedEnchantments().isEmpty(), "offer 3 has a plan");
        h.assertTrue(menu.clickMenuButton(p, 2), "vanilla enchant succeeds");
        h.assertTrue(TestSupport.enchantments(menu.getSlot(0).getItem()).equals(asMap(s.plannedEnchantments())),
                "captured plan " + asMap(s.plannedEnchantments()) + " must equal what vanilla applied " + TestSupport.enchantments(menu.getSlot(0).getItem()));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = RITUAL_TIMEOUT)
    public static void perfectRitualBindsThePlanExactlyOnce(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_PICKAXE), 10);
        int seed = p.getEnchantmentSeed();
        OfferSnapshot plan = snapshot(p, menu, 2);
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        h.assertTrue(started != null, "ritual started: " + TestSupport.last(p, RitualAbortedS2C.class));
        RitualPattern pattern = TestSupport.pattern(started);
        h.assertTrue(PatternValidator.validate(pattern, started.params()).isEmpty(), "client-side pattern is valid");
        h.assertTrue(TestSupport.enchantments(menu.getSlot(0).getItem()).isEmpty(), "nothing applied before the ritual resolves");
        List<InputRecord> inputs = Autoplay.perfect(pattern);
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            RitualInputBatchC2S batch = new RitualInputBatchC2S(started.sessionId(), 0, true, inputs);
            RitualSessionManager.receiveInputs(p, batch);
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null, "resolved: " + TestSupport.last(p, RitualAbortedS2C.class));
            h.assertTrue(resolved.band() == OutcomeBand.PERFECT && resolved.score() == 100.0, "perfect play scores 100, got " + resolved.score());
            ItemStack item = menu.getSlot(0).getItem();
            h.assertTrue(TestSupport.enchantments(item).equals(asMap(plan.plannedEnchantments())), "full plan bound: " + TestSupport.enchantments(item));
            h.assertTrue(p.experienceLevel == 27, "three levels charged, have " + p.experienceLevel);
            h.assertTrue(lapis(menu) == 7, "three lapis charged, have " + lapis(menu));
            h.assertTrue(p.getEnchantmentSeed() != seed, "enchantment seed advanced");
            h.assertTrue(RitualSessionManager.get(p).isEmpty(), "session closed");
            // Replaying the final packet changes nothing.
            RitualSessionManager.receiveInputs(p, batch);
            h.assertTrue(TestSupport.sent(p, RitualResolvedS2C.class).size() == 1, "a ritual resolves once");
            h.assertTrue(p.experienceLevel == 27 && lapis(menu) == 7, "no second charge");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = RITUAL_TIMEOUT)
    public static void silentRitualShattersAndChargesFully(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.IRON_CHESTPLATE), 10);
        int seed = p.getEnchantmentSeed();
        RitualSessionManager.start(p, menu.containerId, 1, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        h.assertTrue(started != null, "ritual started");
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, List.of()));
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null && resolved.band() == OutcomeBand.SHATTERED, "no input shatters");
            h.assertTrue(resolved.bound().isEmpty(), "nothing bound");
            h.assertTrue(TestSupport.enchantments(menu.getSlot(0).getItem()).isEmpty(), "item untouched");
            h.assertTrue(p.experienceLevel == 28 && lapis(menu) == 8, "FULL failure cost: 2 levels and 2 lapis, have "
                    + p.experienceLevel + " / " + lapis(menu));
            h.assertTrue(p.getEnchantmentSeed() != seed, "failure advances the seed by default");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = RITUAL_TIMEOUT)
    public static void sloppyRitualOnlyShrinksThePlan(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_SWORD), 10);
        OfferSnapshot plan = snapshot(p, menu, 2);
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        List<InputRecord> inputs = Autoplay.play(pattern, 0.3, 90, 1234L);
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, inputs));
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null, "resolved");
            Map<Enchantment, Integer> planned = asMap(plan.plannedEnchantments());
            Map<Enchantment, Integer> applied = TestSupport.enchantments(menu.getSlot(0).getItem());
            for (Map.Entry<Enchantment, Integer> e : applied.entrySet()) {
                h.assertTrue(planned.containsKey(e.getKey()), "only planned enchantments: " + e.getKey());
                h.assertTrue(e.getValue() <= planned.get(e.getKey()), "never above the rolled level");
                h.assertTrue(e.getValue() >= Math.min(planned.get(e.getKey()), e.getKey().getMinLevel()), "never below the minimum");
            }
            if (resolved.band().isFailure()) h.assertTrue(applied.isEmpty(), "failure binds nothing");
            else h.assertTrue(!applied.isEmpty(), "a partial binding keeps the primary");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void cancellingDuringTheCountInIsFree(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.BOW), 10);
        int seed = p.getEnchantmentSeed();
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualSessionManager.cancel(p, started.sessionId());
        RitualAbortedS2C aborted = TestSupport.last(p, RitualAbortedS2C.class);
        h.assertTrue(aborted != null && aborted.reason() == AbortReason.CANCELLED, "cancel reported");
        h.assertTrue(p.experienceLevel == 30 && lapis(menu) == 10 && p.getEnchantmentSeed() == seed, "nothing charged, seed kept");
        h.assertTrue(RitualSessionManager.get(p).isEmpty(), "session closed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void closingTheTableMidRitualFails(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_AXE), 10);
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.countInMs() + 700), () -> {
            p.closeContainer();
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null && resolved.abandoned() && resolved.band() == OutcomeBand.FAILED, "abandoned after the first rune fails");
            h.assertTrue(p.experienceLevel == 27, "FULL cost charged on abandonment, have " + p.experienceLevel);
            int lapisLeft = p.getInventory().countItem(Items.LAPIS_LAZULI);
            h.assertTrue(lapisLeft == 7, "lapis charged before it was returned, have " + lapisLeft);
            ItemStack axe = findItem(p, Items.DIAMOND_AXE);
            h.assertTrue(!axe.isEmpty() && TestSupport.enchantments(axe).isEmpty(), "the item came back unenchanted");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void swappingTheItemInvalidatesTheRitual(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_HOE), 10);
        RitualSessionManager.start(p, menu.containerId, 0, 1f);
        h.assertTrue(TestSupport.last(p, RitualStartedS2C.class) != null, "started");
        ((EnchantmentMenuAccessor) menu).immersiveenchanting$getEnchantSlots().setItem(0, new ItemStack(Items.GOLDEN_HOE));
        RitualSessionManager.tick(h.getLevel().getServer());
        RitualAbortedS2C aborted = TestSupport.last(p, RitualAbortedS2C.class);
        h.assertTrue(aborted != null && aborted.reason() == AbortReason.ITEM_CHANGED, "swap detected: " + aborted);
        h.assertTrue(p.experienceLevel == 30 && lapis(menu) == 10, "invalidated during the count-in: free");
        h.assertTrue(TestSupport.enchantments(menu.getSlot(0).getItem()).isEmpty(), "swapped item untouched");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void malformedInputFailsTheRitual(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.IRON_SWORD), 10);
        RitualSessionManager.start(p, menu.containerId, 0, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.countInMs() + 700), () -> {
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, false,
                    List.of(new InputRecord(pattern.countInMs(), 7, true))));
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null && resolved.abandoned() && resolved.bound().isEmpty(), "rejected input resolves as a failure");
            h.assertTrue(p.experienceLevel == 29, "failure cost charged, have " + p.experienceLevel);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void finishingEarlyIsRejected(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.IRON_PICKAXE), 10);
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.countInMs() + 700), () -> {
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, Autoplay.perfect(pattern)));
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null && resolved.abandoned(), "a perfect log sent before its runes arrived is refused");
            h.assertTrue(TestSupport.enchantments(menu.getSlot(0).getItem()).isEmpty(), "nothing bound");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = RITUAL_TIMEOUT)
    public static void booksKeepTheirNameAndBecomeEnchantedBooks(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        ItemStack book = new ItemStack(Items.BOOK);
        book.setHoverName(Component.literal("Grimoire"));
        EnchantmentMenu menu = TestSupport.open(h, p, book, 10);
        OfferSnapshot plan = snapshot(p, menu, 2);
        h.assertTrue(plan.isBook(), "snapshot knows it is a book");
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, Autoplay.perfect(pattern)));
            ItemStack result = menu.getSlot(0).getItem();
            h.assertTrue(result.is(Items.ENCHANTED_BOOK), "book converted");
            h.assertTrue("Grimoire".equals(result.getHoverName().getString()), "custom name kept: " + result.getHoverName().getString());
            h.assertTrue(TestSupport.enchantments(result).equals(asMap(plan.plannedEnchantments())), "stored enchantments are the plan");
            h.succeed();
        });
    }

    /** An enchantment from an unknown mod rolls at a real table and binds through a ritual with no integration. */
    @GameTest(template = "empty", timeoutTicks = RITUAL_TIMEOUT)
    public static void unknownModdedEnchantmentBindsGenerically(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_SWORD), 64);
        EnchantmentMenuAccessor accessor = (EnchantmentMenuAccessor) menu;
        int offer = -1;
        for (int attempt = 0; attempt < 600 && offer < 0; attempt++) {
            for (int k = 0; k < 3 && offer < 0; k++) {
                if (menu.costs[k] > 0 && snapshot(p, menu, k).plannedEnchantments().stream().anyMatch(e -> e.enchantment == TestContent.odd)) offer = k;
            }
            if (offer < 0) {
                p.onEnchantmentPerformed(ItemStack.EMPTY, 0);
                accessor.immersiveenchanting$getEnchantmentSeed().set(p.getEnchantmentSeed());
                menu.slotsChanged(accessor.immersiveenchanting$getEnchantSlots());
            }
        }
        h.assertTrue(offer >= 0, "the table eventually offers the unknown enchantment");
        p.experienceLevel = 50;
        OfferSnapshot plan = snapshot(p, menu, offer);
        RitualSessionManager.start(p, menu.containerId, offer, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        h.assertTrue(started != null, "ritual started: " + TestSupport.last(p, RitualAbortedS2C.class));
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, Autoplay.perfect(pattern)));
            Map<Enchantment, Integer> applied = TestSupport.enchantments(menu.getSlot(0).getItem());
            h.assertTrue(applied.containsKey(TestContent.odd), "the unknown enchantment was bound: " + applied);
            h.assertTrue(applied.equals(asMap(plan.plannedEnchantments())), "exactly the plan");
            h.succeed();
        });
    }

    /** Disconnecting after the first rune resolves the ritual as a failure once; a replay after reconnecting does nothing. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void disconnectingMidRitualResolvesOnce(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.NETHERITE_SWORD), 10);
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        RitualPattern pattern = TestSupport.pattern(started);
        h.runAfterDelay(TestSupport.ticks(pattern.countInMs() + 700), () -> {
            RitualSessionManager.onLogout(p);
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null && resolved.abandoned() && resolved.bound().isEmpty(), "disconnect resolves as a failure");
            h.assertTrue(p.experienceLevel == 27 && lapis(menu) == 7, "charged once");
            h.assertTrue(RitualSessionManager.get(p).isEmpty(), "session gone");
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, Autoplay.perfect(pattern)));
            h.assertTrue(TestSupport.sent(p, RitualResolvedS2C.class).size() == 1, "a replay after reconnecting resolves nothing");
            h.assertTrue(TestSupport.enchantments(menu.getSlot(0).getItem()).isEmpty() && p.experienceLevel == 27, "and changes nothing");
            h.succeed();
        });
    }

    private static ItemStack findItem(FakePlayer p, net.minecraft.world.item.Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) return s;
        }
        return ItemStack.EMPTY;
    }

    private RitualGameTests() {}
}
