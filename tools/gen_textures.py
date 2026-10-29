#!/usr/bin/env python3
"""Generate the Nine Lives GUI texture set as RGBA pixel art.

ImageGen was unavailable (HTTP 403) in this environment, so the assets are
authored here with Pillow/numpy: hand-built 9x9 heart sprites, vector-derived
32x32 hearts, and native-resolution 9-slice panels/buttons/tabs.
Run: python3 tools/gen_textures.py
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/client/resources/assets/ninelives/textures/gui"
PREVIEW = ROOT / "build/texture_preview.png"
OUT.mkdir(parents=True, exist_ok=True)

DARK = (37, 7, 13, 255)
DARK2 = (26, 5, 9, 255)
GOLD = (217, 164, 65, 255)
GOLD_DARK = (138, 101, 36, 255)
GOLD_BRIGHT = (255, 217, 128, 255)
GOLD_PALE = (255, 240, 188, 255)
BURGUNDY = (122, 22, 38, 255)
BURGUNDY_LIGHT = (147, 32, 47, 255)
BURGUNDY_DARK = (74, 13, 24, 255)
BURGUNDY_DEEP = (58, 10, 19, 255)
CRIMSON = (178, 53, 69, 255)
HIGHLIGHT = (208, 106, 112, 255)
GLASS_TOP = (30, 20, 26, 240)
GLASS_BOTTOM = (14, 9, 13, 240)


# ---------------------------------------------------------------------------
# small 9x9 hearts (hand-authored mask, palette rings)
# ---------------------------------------------------------------------------

MASK = [
    "..##.##..",
    ".#######.",
    "#########",
    "#########",
    "#########",
    ".#######.",
    "..#####..",
    "...###...",
    "....#....",
]


def mask_at(x: int, y: int) -> bool:
    if x < 0 or y < 0 or x >= 9 or y >= 9:
        return False
    return MASK[y][x] == "#"


def is_boundary(x: int, y: int) -> bool:
    if not mask_at(x, y):
        return False
    return not all(mask_at(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def is_inner_ring(x: int, y: int) -> bool:
    if not mask_at(x, y) or is_boundary(x, y):
        return False
    return any(
        not mask_at(x + dx, y + dy)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1))
    )


def small_heart(kind: str) -> Image.Image:
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    crack = {(4, 1), (3, 2), (4, 3), (3, 4), (4, 5), (3, 6), (4, 7)}
    for y in range(9):
        for x in range(9):
            if not mask_at(x, y):
                continue
            if kind == "gold":
                boundary, ring_top, ring_bottom = (42, 26, 6, 255), GOLD_PALE, GOLD_DARK
                base, shade, light = (217, 164, 65, 255), (176, 126, 40, 255), (255, 217, 128, 255)
            elif kind == "cracked":
                boundary, ring_top, ring_bottom = (36, 8, 14, 255), (160, 120, 52, 255), (61, 28, 30, 255)
                base, shade, light = (110, 38, 48, 255), (78, 24, 32, 255), (138, 52, 60, 255)
            else:
                boundary, ring_top, ring_bottom = DARK, (201, 155, 63, 255), BURGUNDY_DARK
                base, shade, light = BURGUNDY, BURGUNDY_DARK, BURGUNDY_LIGHT

            color = base
            if is_boundary(x, y):
                color = boundary
            elif is_inner_ring(x, y):
                color = ring_top if (x + y <= 7) else ring_bottom
            else:
                if x + y >= 11:
                    color = shade
                elif x + y <= 5:
                    color = light

            if kind == "half" and x >= 5 and not is_boundary(x, y):
                color = (27, 6, 10, 255) if not is_inner_ring(x, y) else (42, 10, 18, 255)

            if kind == "full" or kind == "half":
                if (x, y) in ((2, 2), (2, 3)):
                    color = HIGHLIGHT
                elif (x, y) == (3, 2):
                    color = CRIMSON
            elif kind == "gold":
                if (x, y) in ((2, 2), (3, 2), (2, 3)):
                    color = GOLD_PALE
            elif kind == "cracked":
                if (x, y) == (2, 2):
                    color = (140, 70, 78, 255)

            if kind == "cracked" and (x, y) in crack:
                color = (20, 5, 8, 255)

            px[x, y] = color
    return img


# ---------------------------------------------------------------------------
# big 32x32 hearts: vector silhouette, rings, shading, then palette quantised
# ---------------------------------------------------------------------------

FULL_PALETTE = [DARK, GOLD, BURGUNDY, BURGUNDY_LIGHT, BURGUNDY_DARK, CRIMSON, HIGHLIGHT, (240, 160, 166, 255)]
GOLD_PALETTE = [(42, 26, 6, 255), GOLD_BRIGHT, GOLD, (176, 126, 40, 255), GOLD_PALE, (107, 74, 22, 255)]
CRACK_PALETTE = [(36, 8, 14, 255), (169, 130, 58, 255), (90, 32, 41, 255), (58, 18, 24, 255),
                 (116, 48, 58, 255), (20, 5, 8, 255), (140, 70, 78, 255)]


def heart_mask(size: int = 512) -> Image.Image:
    img = Image.new("L", (size, size), 0)
    pts = []
    for i in range(720):
        t = 2 * math.pi * i / 720
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((x, -y))
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    minx, maxx = min(xs), max(xs)
    miny, maxy = min(ys), max(ys)
    margin = size * 0.06
    scale = (size - margin * 2) / max(maxx - minx, maxy - miny)
    mapped = [((x - (minx + maxx) / 2) * scale + size / 2,
               (y - (miny + maxy) / 2) * scale + size / 2) for x, y in pts]
    ImageDraw.Draw(img).polygon(mapped, fill=255)
    return img


def quantize(img: Image.Image, palette: list[tuple[int, int, int, int]]) -> Image.Image:
    arr = np.array(img.convert("RGBA")).astype(np.int16)
    alpha = arr[:, :, 3]
    rgb = arr[:, :, :3].astype(np.int16)
    pal = np.array([[c[0], c[1], c[2]] for c in palette], dtype=np.int16)
    dist = ((rgb[:, :, None, :] - pal[None, None, :, :]) ** 2).sum(axis=3)
    idx = dist.argmin(axis=2)
    out = np.zeros_like(arr)
    for i, c in enumerate(palette):
        sel = idx == i
        out[sel] = c
    out[:, :, 3] = np.where(alpha >= 110, 255, 0)
    out[out[:, :, 3] == 0] = 0
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def big_heart(kind: str) -> Image.Image:
    # Silhouette at 32px, derived from the vector heart then thresholded so the
    # outline and shading bands stay clean pixel art (no blended-ring noise).
    hi = heart_mask(512)
    mask_img = hi.resize((32, 32), Image.LANCZOS).point(lambda p: 255 if p >= 110 else 0)
    mask_img = mask_img.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
    m = np.array(mask_img) > 128
    erode1 = np.array(mask_img.filter(ImageFilter.MinFilter(3))) > 128
    erode2 = np.array(mask_img.filter(ImageFilter.MinFilter(5))) > 128
    boundary = m & ~erode1
    ring = erode1 & ~erode2
    interior = erode2

    h, w = m.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)

    if kind == "gold":
        boundary_color = (42, 26, 6)
        ring_color = (255, 240, 188)
        bands = [(0.0, (255, 217, 128)), (0.45, (217, 164, 65)), (1.0, (176, 126, 40))]
        highlight = (255, 240, 188)
        shadow = (138, 101, 36)
    elif kind == "cracked":
        boundary_color = (36, 8, 14)
        ring_color = (160, 120, 52)
        bands = [(0.0, (138, 52, 60)), (0.5, (110, 38, 48)), (1.0, (78, 24, 32))]
        highlight = (150, 62, 70)
        shadow = (58, 18, 24)
    else:
        boundary_color = (37, 7, 13)
        ring_color = (201, 155, 63)
        bands = [(0.0, (147, 32, 47)), (0.5, (122, 22, 38)), (1.0, (74, 13, 24))]
        highlight = (208, 106, 112)
        shadow = (74, 13, 24)

    arr = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        t = y / (h - 1)
        for i in range(len(bands) - 1):
            p0, c0 = bands[i]
            p1, c1 = bands[i + 1]
            if p0 <= t <= p1:
                k = (t - p0) / max(1e-6, p1 - p0)
                arr[y, :, :3] = [int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3)]
                break
    arr[:, :, 3] = 255
    arr[~interior] = 0
    arr[ring, :3] = ring_color
    arr[ring, 3] = 255
    arr[boundary, :3] = boundary_color
    arr[boundary, 3] = 255

    if kind != "gold":
        # gloss in the upper-left lobe, 2-step for a pixel-art sheen
        cx, cy, rx, ry = w * 0.33, h * 0.30, w * 0.15, h * 0.12
        gloss = interior & (((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1)
        arr[gloss, :3] = highlight
        cx, cy = w * 0.30, h * 0.26
        gloss2 = interior & (((xx - cx) / (w * 0.06)) ** 2 + ((yy - cy) / (h * 0.05)) ** 2 <= 1)
        if kind == "full":
            arr[gloss2, :3] = (240, 160, 166)
        # bottom-right inner shadow
        arr[interior & ((xx / w + yy / h) > 1.42), :3] = shadow

    if kind == "cracked":
        img = Image.fromarray(arr, "RGBA")
        d = ImageDraw.Draw(img)
        pts = [(16, 3), (13, 9), (17, 14), (13, 19), (17, 24), (14, 27)]
        d.line(pts, fill=(20, 5, 8, 255), width=1)
        d.line([(p[0] + 1, p[1]) for p in pts], fill=(150, 62, 70, 255), width=1)
        arr = np.array(img)

    return Image.fromarray(arr, "RGBA")


# ---------------------------------------------------------------------------
# UI panels, buttons, tabs, progress
# ---------------------------------------------------------------------------

def vertical_gradient(w: int, h: int, stops: list[tuple[float, tuple[int, int, int]]]) -> Image.Image:
    arr = np.zeros((h, w, 3), dtype=np.uint8)
    for y in range(h):
        t = y / max(1, h - 1)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                k = (t - p0) / max(1e-6, p1 - p0)
                arr[y, :, :] = [int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3)]
                break
    img = Image.fromarray(arr, "RGB").convert("RGBA")
    return img


def paste_rounded(img: Image.Image, box, radius: int, fill_img: Image.Image) -> None:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius=radius, fill=255)
    img.paste(fill_img, (0, 0), mask)


def panel(highlight: bool) -> Image.Image:
    w = h = 24
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glass = vertical_gradient(w, h, [(0.0, (56, 36, 45) if highlight else (44, 28, 35)),
                                     (1.0, (26, 16, 21) if highlight else (20, 12, 16))])
    paste_rounded(img, [2, 2, 21, 21], 5, glass)
    d = ImageDraw.Draw(img)
    outer = (26, 6, 10, 255) if not highlight else (40, 12, 18, 255)
    mid = (138, 101, 36, 255) if not highlight else (201, 155, 63, 255)
    bright = (201, 155, 63, 255) if not highlight else (255, 217, 128, 255)
    frame = (74, 13, 24, 255) if not highlight else (122, 22, 38, 255)
    d.rounded_rectangle([0, 0, 23, 23], radius=6, outline=outer, width=1)
    d.rounded_rectangle([1, 1, 22, 22], radius=5, outline=mid, width=1)
    d.rounded_rectangle([2, 2, 21, 21], radius=5, outline=bright, width=1)
    d.rounded_rectangle([3, 3, 20, 20], radius=4, outline=frame, width=1)
    d.line([(6, 3), (17, 3)], fill=(147, 32, 47, 255) if not highlight else (178, 53, 69, 255))
    d.line([(3, 6), (3, 17)], fill=(107, 74, 22, 255))
    for x, y in ((4, 4), (19, 4), (4, 19)):
        d.point((x, y), fill=(255, 240, 188, 255))
        d.point((x + 1, y), fill=(138, 101, 36, 255))
        d.point((x, y + 1), fill=(138, 101, 36, 255))
    return img


def button(hover: bool, w: int = 96, h: int = 20, tab: bool = False, active: bool = False) -> Image.Image:
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if tab:
        stops = ([(0.0, (122, 22, 38)), (1.0, (58, 10, 19))] if active
                 else [(0.0, (34, 16, 21)), (1.0, (20, 9, 13))])
        outer = (26, 6, 10, 255)
        edge = (201, 155, 63, 255) if active else (107, 74, 22, 255)
        top = (255, 217, 128, 255) if active else (58, 30, 22, 255)
        bottom = (42, 10, 18, 255)
    else:
        stops = ([(0.0, (147, 32, 47)), (0.55, (92, 16, 28)), (1.0, (58, 10, 19))] if hover
                 else [(0.0, (122, 22, 38)), (0.55, (74, 13, 24)), (1.0, (46, 8, 15))])
        outer = (26, 6, 10, 255)
        edge = (255, 217, 128, 255) if hover else (201, 155, 63, 255)
        top = (224, 106, 118, 255) if hover else (156, 40, 54, 255)
        bottom = (26, 6, 10, 255)

    body = vertical_gradient(w, h, stops)
    paste_rounded(img, [2, 2, w - 3, h - 3], 4 if tab else 5, body)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=6 if not tab else 5, outline=outer, width=1)
    d.rounded_rectangle([1, 1, w - 2, h - 2], radius=5 if not tab else 4, outline=edge, width=1)
    d.line([(4, 3), (w - 5, 3)], fill=top)
    d.line([(4, h - 4), (w - 5, h - 4)], fill=bottom)
    if tab and active:
        d.line([(5, 2), (w - 6, 2)], fill=(255, 240, 188, 255))
    return img


def progress_frame() -> Image.Image:
    w, h = 120, 10
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=5, outline=(26, 6, 10, 255), width=1)
    d.rounded_rectangle([1, 1, w - 2, h - 2], radius=4, outline=GOLD, width=1)
    d.line([(5, h - 2), (w - 6, h - 2)], fill=(107, 74, 22, 255))
    d.line([(5, 1), (w - 6, 1)], fill=(255, 217, 128, 255))
    return img


def progress_fill() -> Image.Image:
    w, h = 118, 8
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    arr = np.zeros((h, w, 3), dtype=np.uint8)
    for x in range(w):
        t = x / max(1, w - 1)
        c = (int(217 + 38 * t), int(164 + 53 * t), int(65 + 63 * t))
        arr[:, x, :] = c
    img = Image.fromarray(arr, "RGB").convert("RGBA")
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (w - 1, 0)], fill=(255, 240, 188, 255))
    d.line([(0, h - 1), (w - 1, h - 1)], fill=(176, 126, 40, 255))
    return img


def badge() -> Image.Image:
    w = h = 16
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    body = vertical_gradient(w, h, [(0.0, (92, 16, 28)), (1.0, (42, 10, 18))])
    paste_rounded(img, [2, 2, 13, 13], 3, body)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, 15, 15], radius=4, outline=(26, 6, 10, 255), width=1)
    d.rounded_rectangle([1, 1, 14, 14], radius=3, outline=(217, 164, 65, 255), width=1)
    d.line([(4, 2), (11, 2)], fill=(178, 53, 69, 255))
    d.point((3, 3), fill=(255, 240, 188, 255))
    return img


# ---------------------------------------------------------------------------

def main() -> None:
    assets: dict[str, Image.Image] = {}
    for kind, name in (("full", "heart_full"), ("half", "heart_half"),
                       ("cracked", "heart_cracked"), ("gold", "heart_full_gold")):
        assets[f"{name}.png"] = small_heart(kind)
    for kind, name in (("full", "heart_big_full"), ("cracked", "heart_big_cracked"),
                       ("gold", "heart_big_gold")):
        assets[f"{name}.png"] = big_heart(kind)
    assets["panel.png"] = panel(False)
    assets["panel_highlight.png"] = panel(True)
    assets["button.png"] = button(False)
    assets["button_hover.png"] = button(True)
    assets["tab.png"] = button(False, 88, 20, tab=True, active=False)
    assets["tab_active.png"] = button(False, 88, 20, tab=True, active=True)
    assets["progress_frame.png"] = progress_frame()
    assets["progress_fill.png"] = progress_fill()
    assets["badge.png"] = badge()

    for name, image in assets.items():
        image.save(OUT / name)

    names = list(assets)
    cell_w, cell_h = 220, 120
    sheet = Image.new("RGBA", (cell_w * 4, cell_h * 4), (18, 12, 16, 255))
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        col, row = i % 4, i // 4
        image = assets[name]
        scale = max(1, min(8, (cell_w - 16) // max(1, image.width),
                           (cell_h - 26) // max(1, image.height)))
        big = image.resize((image.width * scale, image.height * scale), Image.NEAREST)
        x = col * cell_w + (cell_w - big.width) // 2
        y = row * cell_h + 16 + (cell_h - 16 - big.height) // 2
        sheet.alpha_composite(big, (x, y))
        draw.text((col * cell_w + 4, row * cell_h + 3), name, fill=(220, 210, 214, 255))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print(f"wrote {len(assets)} textures to {OUT}")
    print(f"preview: {PREVIEW}")


if __name__ == "__main__":
    main()
