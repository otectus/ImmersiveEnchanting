package com.otectus.immersiveenchanting.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Flat-colored triangles in one draw call. All ritual geometry (rings, spokes, runes, holds, shards) goes through
 * here, so it scales with the GUI and needs no textures or shaders beyond the vanilla position-color shader.
 */
public final class VectorBatch {
    private BufferBuilder buffer;
    private Matrix4f matrix;

    public VectorBatch begin(GuiGraphics g) {
        matrix = new Matrix4f(g.pose().last().pose());
        buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return this;
    }

    public void end() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void v(float x, float y, int argb) {
        buffer.vertex(matrix, x, y, 0).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    public void tri(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        if ((argb >>> 24) == 0) return;
        v(x1, y1, argb);
        v(x2, y2, argb);
        v(x3, y3, argb);
    }

    public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int argb) {
        tri(x1, y1, x2, y2, x3, y3, argb);
        tri(x1, y1, x3, y3, x4, y4, argb);
    }

    public void rect(float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x1, y0, x1, y1, x0, y1, argb);
    }

    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    public void disc(float cx, float cy, float r, int argb) {
        ring(cx, cy, 0, r, 0, (float) (Math.PI * 2), argb, segments(r));
    }

    /** A ring (or arc) between two radii; angles in radians, screen coordinates (y down). */
    public void ring(float cx, float cy, float rIn, float rOut, float a0, float a1, int argb, int segments) {
        float step = (a1 - a0) / segments;
        for (int i = 0; i < segments; i++) {
            float s = a0 + step * i, e = s + step;
            float cs = (float) Math.cos(s), ss = (float) Math.sin(s), ce = (float) Math.cos(e), se = (float) Math.sin(e);
            if (rIn <= 0) {
                tri(cx, cy, cx + cs * rOut, cy + ss * rOut, cx + ce * rOut, cy + se * rOut, argb);
            } else {
                quad(cx + cs * rIn, cy + ss * rIn, cx + cs * rOut, cy + ss * rOut, cx + ce * rOut, cy + se * rOut,
                        cx + ce * rIn, cy + se * rIn, argb);
            }
        }
    }

    public void circle(float cx, float cy, float r, float width, int argb) {
        ring(cx, cy, Math.max(0, r - width / 2), r + width / 2, 0, (float) (Math.PI * 2), argb, segments(r));
    }

    /** A filled polygon, fanned from its first-point average; works for convex and star-shaped outlines. */
    public void polygon(float[] xs, float[] ys, int argb) {
        float cx = 0, cy = 0;
        for (int i = 0; i < xs.length; i++) {
            cx += xs[i];
            cy += ys[i];
        }
        cx /= xs.length;
        cy /= ys.length;
        for (int i = 0; i < xs.length; i++) {
            int j = (i + 1) % xs.length;
            tri(cx, cy, xs[i], ys[i], xs[j], ys[j], argb);
        }
    }

    public void outline(float[] xs, float[] ys, float width, int argb) {
        for (int i = 0; i < xs.length; i++) {
            int j = (i + 1) % xs.length;
            line(xs[i], ys[i], xs[j], ys[j], width, argb);
            disc(xs[i], ys[i], width / 2, argb);
        }
    }

    static int segments(float r) {
        return Math.max(12, Math.min(72, (int) (r * 1.2f)));
    }
}
