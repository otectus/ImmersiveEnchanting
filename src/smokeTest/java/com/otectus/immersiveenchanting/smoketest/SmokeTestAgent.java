package com.otectus.immersiveenchanting.smoketest;

import net.minecraftforge.fml.common.Mod;

/**
 * Test-only companion mod used by {@code runClient -PsmokeTest}. It does nothing unless the system property
 * {@code immersive_enchanting.smoketest.output} names a directory for its result file.
 */
@Mod(SmokeTestAgent.MOD_ID)
public class SmokeTestAgent {
    public static final String MOD_ID = "immersive_enchanting_smoketest";
    public static final String OUTPUT_PROPERTY = "immersive_enchanting.smoketest.output";

    public SmokeTestAgent() {
    }
}
