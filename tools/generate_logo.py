#!/usr/bin/env python3
"""Draws the mod-list logo (src/main/resources/immersive_enchanting_logo.png): the binding circle, its four rune
anchors in their lane shapes and colours, and the central sigil. Rendered at 4x and downsampled for smooth edges."""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "immersive_enchanting_logo.png")
SIZE = 256
S = 4
W = SIZE * S

LANES = [(0x56, 0xB4, 0xE9), (0xE6, 0x9F, 0x00), (0x2F, 0xBF, 0x8F), (0xCC, 0x79, 0xA7)]
PRIMARY = (0x9B, 0x7C, 0xFF)
SECONDARY = (0x5F, 0xE3, 0xFF)


def glyph(lane, cx, cy, r):
    if lane == 0:
        return [(cx, cy - 1.1 * r), (cx + 0.95 * r, cy + 0.62 * r), (cx - 0.95 * r, cy + 0.62 * r)]
    if lane == 1:
        return [(cx, cy - 1.12 * r), (cx + 0.82 * r, cy), (cx, cy + 1.12 * r), (cx - 0.82 * r, cy)]
    if lane == 2:
        return [(cx + math.cos(a) * 0.92 * r, cy + math.sin(a) * 0.92 * r) for a in (i * math.pi / 16 for i in range(32))]
    pts = []
    for i in range(8):
        a = -math.pi / 2 + math.pi * i / 4
        rr = 1.18 * r if i % 2 == 0 else 0.4 * r
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return pts


def main():
    img = Image.new("RGBA", (W, W), (12, 9, 24, 255))
    glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    d = ImageDraw.Draw(img)
    c = W / 2
    ring = W * 0.33
    # background vignette
    for i in range(40, 0, -1):
        rr = W * 0.5 * i / 40
        shade = int(12 + 22 * (1 - i / 40))
        d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=(shade, int(shade * 0.7), shade + 18, 255))
    # glowing rings
    for width, colour, radius in ((10 * S, SECONDARY + (160,), ring), (5 * S, PRIMARY + (140,), ring * 1.16), (4 * S, PRIMARY + (200,), ring * 0.24)):
        g.ellipse([c - radius, c - radius, c + radius, c + radius], outline=colour, width=width)
    glow = glow.filter(ImageFilter.GaussianBlur(6 * S))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)
    d.ellipse([c - ring, c - ring, c + ring, c + ring], outline=SECONDARY + (230,), width=2 * S)
    d.ellipse([c - ring * 1.16, c - ring * 1.16, c + ring * 1.16, c + ring * 1.16], outline=PRIMARY + (170,), width=S)
    # spokes and resonance ticks
    dirs = [(-1, 0), (0, 1), (0, -1), (1, 0)]
    for dx, dy in dirs:
        d.line([c + dx * ring * 0.24, c + dy * ring * 0.24, c + dx * ring, c + dy * ring], fill=SECONDARY + (90,), width=S)
    for i in range(48):
        if i % 12 in (0, 11):
            continue
        a0 = -math.pi / 2 + 2 * math.pi * i / 48 + 0.02
        a1 = a0 + 2 * math.pi / 48 - 0.04
        r0, r1 = ring * 1.24, ring * 1.3
        d.polygon([(c + math.cos(a0) * r0, c + math.sin(a0) * r0), (c + math.cos(a0) * r1, c + math.sin(a0) * r1),
                   (c + math.cos(a1) * r1, c + math.sin(a1) * r1), (c + math.cos(a1) * r0, c + math.sin(a1) * r0)],
                  fill=PRIMARY + (200 if i < 34 else 60,))
    # central sigil
    for i in range(12):
        a = 2 * math.pi * i / 12
        d.line([c + math.cos(a) * ring * 0.12, c + math.sin(a) * ring * 0.12, c + math.cos(a) * ring * 0.2, c + math.sin(a) * ring * 0.2],
               fill=PRIMARY + (230,), width=S)
    d.ellipse([c - ring * 0.06, c - ring * 0.06, c + ring * 0.06, c + ring * 0.06], fill=SECONDARY + (255,))
    # anchors, with one rune on its way to each
    rune = ring * 0.16
    for lane, (dx, dy) in enumerate(dirs):
        ax, ay = c + dx * ring, c + dy * ring
        col = LANES[lane]
        d.polygon(glyph(lane, ax, ay, rune * 1.15), fill=col + (70,), outline=col + (255,), width=int(1.5 * S))
        px, py = c + dx * ring * (0.52 + 0.1 * lane), c + dy * ring * (0.52 + 0.1 * lane)
        d.polygon(glyph(lane, px, py, rune * 0.8), fill=col + (255,), outline=(255, 255, 255, 200), width=S)
    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    img.save(OUT)
    print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
