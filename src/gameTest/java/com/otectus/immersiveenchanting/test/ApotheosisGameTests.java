package com.otectus.immersiveenchanting.test;

import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Apotheosis adapter on a real Apotheosis table. These run for real only with {@code -PcompatMods=apotheosis};
 * without Apotheosis each test passes at once, and {@link ApotheosisChecks} (which links Apotheosis classes) is never
 * loaded.
 */
@GameTestHolder(ImmersiveEnchanting.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApotheosisGameTests {
    private static final int RITUAL_TIMEOUT = 900;

    private static boolean skipped(GameTestHelper h) {
        if (ModList.get().isLoaded("apotheosis")) return false;
        h.succeed();
        return true;
    }

    @GameTest(template = "empty")
    public static void apotheosisLevelCapsDriveDifficulty(GameTestHelper h) {
        if (!skipped(h)) ApotheosisChecks.levelCaps(h);
    }

    @GameTest(template = "empty", timeoutTicks = RITUAL_TIMEOUT)
    public static void fullBindingPerformsTheInfusion(GameTestHelper h) {
        if (!skipped(h)) ApotheosisChecks.infusion(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = RITUAL_TIMEOUT)
    public static void partialBindingLeavesTheInfusionUndone(GameTestHelper h) {
        if (!skipped(h)) ApotheosisChecks.infusion(h, false);
    }
}
