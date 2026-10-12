package com.otectus.immersiveenchanting.test;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.api.AbortReason;
import com.otectus.immersiveenchanting.api.event.RitualPlanEvent;
import com.otectus.immersiveenchanting.network.RitualAbortedS2C;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** A {@code RitualPlanEvent} denial refuses the ritual before anything is charged (1.1.0). */
@GameTestHolder(ImmersiveEnchanting.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DenialGameTests {

    /** Denies every offer of one player; registered for the duration of a test only. */
    private static final class Gate {
        private final UUID player;

        Gate(UUID player) {
            this.player = player;
        }

        @SubscribeEvent
        public void onPlan(RitualPlanEvent event) {
            if (event.getPlayer().getUUID().equals(player)) event.deny(Component.literal("test: locked"));
        }
    }

    @GameTest(template = "empty")
    public static void aDeniedOfferIsRefusedBeforeAnyCharge(GameTestHelper h) {
        FakePlayer p = TestSupport.player(h);
        TestSupport.table(h);
        EnchantmentMenu menu = TestSupport.open(h, p, new ItemStack(Items.DIAMOND_PICKAXE), 10);
        int levels = p.experienceLevel;
        int seed = p.getEnchantmentSeed();
        Gate gate = new Gate(p.getUUID());
        MinecraftForge.EVENT_BUS.register(gate);
        try {
            RitualSessionManager.start(p, menu.containerId, 2, 1f);
            RitualAbortedS2C aborted = TestSupport.last(p, RitualAbortedS2C.class);
            h.assertTrue(aborted != null && aborted.reason() == AbortReason.DENIED_BY_MOD, "refused as DENIED_BY_MOD, got " + aborted);
            h.assertTrue(aborted.detail() != null && "test: locked".equals(aborted.detail().getString()), "the denial's reason travels with the abort");
            h.assertTrue(TestSupport.last(p, RitualStartedS2C.class) == null, "no ritual started");
            h.assertTrue(RitualSessionManager.get(p).isEmpty(), "no session exists");
            h.assertTrue(TestSupport.enchantments(menu.getSlot(0).getItem()).isEmpty(), "nothing applied");
            h.assertTrue(p.experienceLevel == levels, "no levels charged");
            h.assertTrue(menu.getSlot(1).getItem().getCount() == 10, "no lapis charged");
            h.assertTrue(p.getEnchantmentSeed() == seed, "the enchantment seed did not advance");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(gate);
        }
        // With the gate gone the same offer starts normally.
        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        h.assertTrue(TestSupport.last(p, RitualStartedS2C.class) != null, "the offer opens again once nothing denies it: "
                + TestSupport.last(p, RitualAbortedS2C.class));
        RitualSessionManager.get(p).ifPresent(s -> RitualSessionManager.cancel(p, s.sessionId()));
        h.succeed();
    }
}
