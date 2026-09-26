package com.otectus.immersiveenchanting.client.render;

import com.otectus.immersiveenchanting.config.ClientConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Ritual colors. Lanes keep fixed colors across themes (players learn them); the theme only tints the sigil, rings
 * and text. Every lane color is paired with a distinct rune shape, so nothing relies on telling colors apart.
 */
public final class Palette {
    /** Okabe-Ito: sky blue, orange, bluish green, reddish purple. */
    private static final int[] SAFE_LANES = {0x56B4E9, 0xE69F00, 0x2FBF8F, 0xCC79A7};
    private static final int[] ARCANE_LANES = {0xB48CFF, 0x4DD8E8, 0xFFD466, 0xFF7FAF};

    public record Theme(int primary, int secondary, int glyph) {}

    public static final Theme ARCANE = new Theme(0xBD862B, 0x97ADAE, 0xE5C887);
    private static final Map<String, Theme> THEMES = Map.of(
            "immersive_enchanting:arcane", ARCANE,
            "immersive_enchanting:ember", new Theme(0xFF8A3D, 0xFFD166, 0xFFB38A),
            "immersive_enchanting:frost", new Theme(0x8FD3FF, 0xE6F7FF, 0xBFE8FF),
            "immersive_enchanting:tide", new Theme(0x3FC1C9, 0x2E86DE, 0x9BE3E8),
            "immersive_enchanting:verdant", new Theme(0x7BD389, 0xC5F277, 0xB6E8BE),
            "immersive_enchanting:storm", new Theme(0xB0C4FF, 0xFFF27A, 0xD8E0FF),
            "immersive_enchanting:void", new Theme(0xB35CFF, 0xFF4FA3, 0xD9A6FF));

    /** Unknown themes (from a datapack whose resource pack the client lacks) fall back to the default. */
    public static Theme theme(ResourceLocation id) {
        return id == null ? ARCANE : THEMES.getOrDefault(id.toString(), ARCANE);
    }

    public static int lane(int lane) {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_RUNES)) return 0xFFFFFF;
        int[] set = ClientConfig.get(ClientConfig.COLORBLIND_SAFE_MODE) ? SAFE_LANES : ARCANE_LANES;
        return set[Math.floorMod(lane, set.length)];
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, float alpha) {
        return withAlpha(rgb, Math.round(alpha * 255));
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Text colors must keep some alpha: Minecraft's font treats near-zero alpha as opaque. */
    public static int text(int rgb, float alpha) {
        return withAlpha(rgb, Math.max(6, Math.round(alpha * 255)));
    }

    private Palette() {}
}
