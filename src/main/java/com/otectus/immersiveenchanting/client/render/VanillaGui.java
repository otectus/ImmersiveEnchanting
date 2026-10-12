package com.otectus.immersiveenchanting.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The one look every Immersive Enchanting surface shares with the other Immersive workshops: Minecraft's own container
 * panel and buttons. Every surface is drawn from Minecraft's textures through the resource manager, so a resource pack
 * that restyles vanilla containers restyles the ritual too, and nothing here is a texture of this mod. Text follows
 * vanilla containers: dark grey labels on the light panel, light text inside the dark displays the runes play in.
 */
public final class VanillaGui {
    public static final ResourceLocation PANEL = new ResourceLocation("textures/gui/demo_background.png");
    public static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    /** Container titles and labels (vanilla {@code 0x404040}). */
    public static final int LABEL = 0xFF404040;
    /** Secondary text on the light panel. */
    public static final int LABEL_DIM = 0xFF6B6B6B;
    /** Warnings on the light panel (vanilla dark red). */
    public static final int LABEL_WARNING = 0xFFAA0000;
    /** Light text inside dark displays. */
    public static final int DISPLAY_TEXT = 0xFFE0E0E0;
    public static final int DISPLAY_TEXT_DIM = 0xFFA8A8A8;
    /** The fill of a dark display, for ritual geometry that has to sit flush with it. */
    public static final int DISPLAY_FILL = 0x262626;
    /** The empty colour of a vanilla slot and bar track. */
    public static final int TRACK = 0xFF8B8B8B;
    /** Vanilla's gold text ({@code ChatFormatting.GOLD}), also used by the offer tooltip's ritual line. */
    public static final int GOLD = 0xFFFFAA00;

    /** A vanilla container panel of any size: the standard bevelled light grey frame. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitNineSliced(PANEL, x, y, w, h, 4, 248, 166, 0, 0);
    }

    /** A recessed field in the colours of a vanilla slot (dark top-left edge, white bottom-right edge). */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, TRACK);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    /** A recessed dark display for everything that needs contrast: the ritual's play field, the result's readout. */
    public static void display(GuiGraphics g, int x, int y, int w, int h) {
        inset(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000 | DISPLAY_FILL);
    }

    /** A vanilla button face without a widget: normal, or highlighted (as when hovered) for a held key. */
    public static void key(GuiGraphics g, int x, int y, int w, int h, boolean highlighted) {
        g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, highlighted ? 86 : 66);
    }

    /** A vanilla-style bar: recessed track, flat fill and a one-pixel highlight on top. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        inset(g, x - 1, y - 1, w + 2, h + 2);
        g.fill(x, y, x + w, y + h, 0xFF373737);
        int filled = Math.round(w * Math.max(0F, Math.min(1F, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            if (h > 2) g.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
        }
    }

    /** The text cut to {@code width} with an ellipsis. */
    public static String clipped(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getString();
        return font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("..."))) + "...";
    }

    private VanillaGui() {}
}
