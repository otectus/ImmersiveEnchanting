package com.otectus.immersiveenchanting.api;

/**
 * What an offer actually costs, in the enchanting system's own terms.
 *
 * @param xpLevels             levels consumed (vanilla: offer index + 1, not the displayed requirement)
 * @param xpPoints             experience points consumed, for systems that charge points instead of levels
 * @param lapis                enchanting fuel consumed (vanilla: offer index + 1)
 * @param displayedRequirement the level shown on the offer; an eligibility requirement, not a price, in vanilla
 */
public record CostSnapshot(int xpLevels, int xpPoints, int lapis, int displayedRequirement) {
}
