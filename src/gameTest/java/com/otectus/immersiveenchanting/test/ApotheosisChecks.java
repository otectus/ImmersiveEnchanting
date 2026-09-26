package com.otectus.immersiveenchanting.test;

import com.otectus.immersiveenchanting.api.CostSnapshot;
import com.otectus.immersiveenchanting.api.DifficultyScale;
import com.otectus.immersiveenchanting.api.EnchantingContextAdapter;
import com.otectus.immersiveenchanting.api.ItemFingerprint;
import com.otectus.immersiveenchanting.api.OfferSnapshot;
import com.otectus.immersiveenchanting.compat.apotheosis.ApotheosisAdapter;
import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersiveenchanting.enchanting.OfferPlanner;
import com.otectus.immersiveenchanting.enchanting.VanillaEnchantingAdapter;
import com.otectus.immersiveenchanting.network.RitualAbortedS2C;
import com.otectus.immersiveenchanting.network.RitualInputBatchC2S;
import com.otectus.immersiveenchanting.network.RitualResolvedS2C;
import com.otectus.immersiveenchanting.network.RitualStartedS2C;
import com.otectus.immersiveenchanting.ritual.Autoplay;
import com.otectus.immersiveenchanting.ritual.InputRecord;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import com.otectus.immersiveenchanting.ritual.RitualPattern;
import com.otectus.immersiveenchanting.ritual.ScoreEngine;
import com.otectus.immersiveenchanting.session.RitualSessionManager;
import dev.shadowsoffire.apotheosis.ench.asm.EnchHooks;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantTile;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Arrays;
import java.util.List;

/** Bodies of {@link ApotheosisGameTests}; loaded only when Apotheosis is present. */
final class ApotheosisChecks {
    /** Eterna exactly 10 (ten vanilla bookshelves) with the table's base Quanta 15 matches Apotheosis's carrot infusion. */
    private static final int INFUSION_SHELVES = 10;

    private static EnchantingContextAdapter adapter(GameTestHelper h) {
        EnchantingContextAdapter adapter = AdapterRegistry.adapters().stream()
                .filter(a -> a.id().equals(ApotheosisAdapter.ID)).findFirst().orElse(null);
        h.assertTrue(adapter != null, "the Apotheosis adapter is registered");
        return adapter;
    }

    /** Apotheosis raises level caps; a vanilla-maximum roll must no longer read as a maximum roll. */
    static void levelCaps(GameTestHelper h) {
        EnchantingContextAdapter apotheosis = adapter(h);
        Enchantment sharpness = Enchantments.SHARPNESS;
        int cap = EnchHooks.getMaxLevel(sharpness);
        int vanillaMax = sharpness.getMaxLevel();
        h.assertTrue(apotheosis.maxLevel(sharpness) == cap, "adapter cap " + apotheosis.maxLevel(sharpness) + " equals Apotheosis's " + cap);
        h.assertTrue(cap > vanillaMax, "Apotheosis raises Sharpness above " + vanillaMax + ", got " + cap);

        FakePlayer p = TestSupport.player(h);
        OfferSnapshot offer = new OfferSnapshot(1, 2, 30, new CostSnapshot(0, 100, 3, 30), new ResourceLocation("minecraft", "sharpness"),
                vanillaMax, List.of(new EnchantmentInstance(sharpness, vanillaMax)), ItemFingerprint.of(new ItemStack(Items.IRON_SWORD)),
                7, DifficultyScale.VANILLA, false, new CompoundTag());
        OfferPlanner.Plan vanilla = OfferPlanner.plan(p, VanillaEnchantingAdapter.INSTANCE, offer, 1.0, true);
        OfferPlanner.Plan capped = OfferPlanner.plan(p, apotheosis, offer, 1.0, true);
        h.assertTrue(vanilla.enchantments().get(0).entry().maxLevel() == vanillaMax, "vanilla adapter keeps vanilla's cap");
        h.assertTrue(capped.enchantments().get(0).entry().maxLevel() == cap, "Apotheosis adapter plans with Apotheosis's cap");
        h.assertTrue(capped.difficulty().levelFactor() < vanilla.difficulty().levelFactor(),
                "Sharpness " + vanillaMax + " is not a top roll under Apotheosis: level factor " + capped.difficulty().levelFactor()
                        + " vs vanilla " + vanilla.difficulty().levelFactor());
        h.succeed();
    }

    /**
     * An infusion transforms the item only on a full binding (Stable or Perfect). A partial binding is charged as a
     * failure and leaves the item as it was.
     */
    static void infusion(GameTestHelper h, boolean full) {
        EnchantingContextAdapter apotheosis = adapter(h);
        FakePlayer p = TestSupport.player(h);
        ApothEnchantmentMenu menu = infusionTable(h, p);
        OfferSnapshot offer = apotheosis.snapshot(p, menu, 2).orElse(null);
        h.assertTrue(offer != null && offer.adapterData().getBoolean("infusion"), "offer 3 is Apotheosis's infusion, costs "
                + Arrays.toString(menu.costs));
        float levels = p.experienceLevel + p.experienceProgress;
        int seed = p.getEnchantmentSeed();

        RitualSessionManager.start(p, menu.containerId, 2, 1f);
        RitualStartedS2C started = TestSupport.last(p, RitualStartedS2C.class);
        h.assertTrue(started != null, "ritual started: " + TestSupport.last(p, RitualAbortedS2C.class));
        RitualPattern pattern = TestSupport.pattern(started);
        List<InputRecord> inputs = full ? Autoplay.perfect(pattern) : partialBinding(h, pattern, started.params().sanitized());
        h.runAfterDelay(TestSupport.ticks(pattern.endMs() + 300), () -> {
            RitualSessionManager.receiveInputs(p, new RitualInputBatchC2S(started.sessionId(), 0, true, inputs));
            RitualResolvedS2C resolved = TestSupport.last(p, RitualResolvedS2C.class);
            h.assertTrue(resolved != null, "resolved: " + TestSupport.last(p, RitualAbortedS2C.class));
            ItemStack item = menu.getSlot(0).getItem();
            h.assertTrue(p.experienceLevel + p.experienceProgress < levels, "experience charged in both cases: band " + resolved.band()
                    + ", item " + item + ", levels " + levels + " -> " + (p.experienceLevel + p.experienceProgress));
            if (full) {
                h.assertTrue(resolved.band().grantsFull(), "perfect play grants a full binding, got " + resolved.band());
                h.assertTrue(item.is(Items.GOLDEN_CARROT), "the infusion transformed the carrot, have " + item);
            } else {
                h.assertTrue(!resolved.band().grantsFull() && !resolved.band().isFailure(), "a partial binding, got " + resolved.band());
                h.assertTrue(item.is(Items.CARROT) && item.getCount() == 1, "a partial binding leaves the carrot, have " + item);
                h.assertTrue(p.getEnchantmentSeed() != seed, "charged as a failure: the seed advanced");
            }
            h.succeed();
        });
    }

    /** A real Apotheosis table with exactly the stats of the carrot infusion, and its menu holding a carrot. */
    private static ApothEnchantmentMenu infusionTable(GameTestHelper h, FakePlayer p) {
        h.setBlock(TestSupport.TABLE, Blocks.ENCHANTING_TABLE);
        int placed = 0;
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (placed++ == INFUSION_SHELVES) break;
            h.setBlock(TestSupport.TABLE.offset(offset), Blocks.BOOKSHELF);
        }
        BlockPos pos = h.absolutePos(TestSupport.TABLE);
        BlockEntity tile = h.getLevel().getBlockEntity(pos);
        h.assertTrue(tile instanceof ApothEnchantTile, "Apotheosis replaces the enchanting table, have " + tile);
        ApothEnchantmentMenu menu = new ApothEnchantmentMenu(950, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos),
                (ApothEnchantTile) tile);
        p.containerMenu = menu;
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 10));
        menu.getSlot(0).set(new ItemStack(Items.CARROT));
        return menu;
    }

    /** Inputs that score inside the partial bands (Frayed or Weak), found by scoring candidates exactly as the server will. */
    private static List<InputRecord> partialBinding(GameTestHelper h, RitualPattern pattern, RitualParams params) {
        for (long seed = 1; seed < 2000; seed++) {
            for (double miss : new double[] {0.2, 0.3, 0.4}) {
                List<InputRecord> inputs = Autoplay.play(pattern, miss, 60, seed);
                double score = ScoreEngine.score(pattern, params, inputs).finalScore();
                if (score >= 55 && score <= 76) return inputs;
            }
        }
        h.fail("no candidate input scored in the partial bands");
        return List.of();
    }

    private ApotheosisChecks() {}
}
