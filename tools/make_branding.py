#!/usr/bin/env python3
"""Генератор айдентики Nine Lives: иконка 512/128 и баннер 1280x720.

Рисует пиксель-арт средствами Pillow (без внешних шрифтов),
используя игровые текстуры сердца и палитру интерфейса.
Запуск: python3 tools/make_branding.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
GUI = ROOT / "src/client/resources/assets/ninelives/textures/gui"
DOCS = ROOT / "docs/images"
SRC = DOCS / "source"

BG_TOP = (38, 7, 17)
BG_BOTTOM = (16, 4, 10)
GOLD = (240, 192, 96)
GOLD_DARK = (122, 59, 18)
GOLD_SOFT = (232, 199, 122)
CREAM = (224, 214, 218)
MUTED = (150, 130, 138)
BURGUNDY = (90, 30, 44)

FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01110"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "10101", "01010"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "0": ["01110", "10011", "10101", "10101", "10101", "11001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11111", "00010", "00100", "00010", "00001", "10001", "01110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00010", "01100"],
    ".": ["00000", "00000", "00000", "00000", "00000", "01100", "01100"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    "/": ["00001", "00010", "00010", "00100", "01000", "01000", "10000"],
    "!": ["00100", "00100", "00100", "00100", "00100", "00000", "00100"],
    " ": ["00000", "00000", "00000", "00000", "00000", "00000", "00000"],
}


def text_width(text, scale, spacing):
    if not text:
        return 0
    return len(text) * 5 * scale + (len(text) - 1) * spacing


def draw_text(draw, text, x, y, scale, spacing, color, shadow=None):
    if shadow is not None:
        offset = max(2, scale // 2)
        draw_text(draw, text, x + offset, y + offset, scale, spacing, shadow)
    cursor = x
    for ch in text.upper():
        glyph = FONT.get(ch, FONT[" "])
        for row, bits in enumerate(glyph):
            for col, bit in enumerate(bits):
                if bit == "1":
                    draw.rectangle(
                        [cursor + col * scale, y + row * scale,
                         cursor + (col + 1) * scale - 1, y + (row + 1) * scale - 1],
                        fill=color)
        cursor += 5 * scale + spacing
    return cursor


def gradient(size, top, bottom):
    width, height = size
    img = Image.new("RGBA", size)
    draw = ImageDraw.Draw(img)
    for row in range(height):
        t = row / max(1, height - 1)
        color = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        draw.line([(0, row), (width, row)], fill=color)
    return img


def glow_layer(size, center, radius, color, alpha):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    steps = 24
    for i in range(steps, 0, -1):
        r = radius * i / steps
        a = round(alpha * (1 - i / steps) ** 1.6)
        draw.ellipse([center[0] - r, center[1] - r, center[0] + r, center[1] + r],
                     fill=color + (a,))
    return layer.filter(ImageFilter.GaussianBlur(radius / 12))


def vignette(size, strength=120):
    width, height = size
    layer = Image.new("L", size, 0)
    draw = ImageDraw.Draw(layer)
    margin = 80
    draw.rectangle([margin, margin, width - margin, height - margin], fill=255)
    layer = layer.filter(ImageFilter.GaussianBlur(margin))
    dark = Image.new("RGBA", size, (0, 0, 0, strength))
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.paste(dark, (0, 0), Image.eval(layer, lambda v: 255 - v))
    return out


def frame(draw, size, inset, color, width):
    w, h = size
    draw.rectangle([inset, inset, w - inset - 1, h - inset - 1], outline=color, width=width)


def make_icon():
    size = (512, 512)
    img = gradient(size, BG_TOP, BG_BOTTOM)
    img.alpha_composite(vignette(size, 110))
    draw = ImageDraw.Draw(img)
    frame(draw, size, 18, GOLD_DARK, 6)
    frame(draw, size, 30, GOLD, 4)
    for x, y in ((30, 30), (size[0] - 31, 30), (30, size[1] - 31), (size[0] - 31, size[1] - 31)):
        draw.rectangle([x - 4, y - 4, x + 4, y + 4], fill=GOLD)
    img.alpha_composite(glow_layer(size, (256, 256), 190, GOLD, 95))
    heart = Image.open(GUI / "heart_big_gold.png").convert("RGBA")
    heart = heart.resize((384, 384), Image.Resampling.NEAREST)
    img.alpha_composite(heart, (64, 64))
    img.save(DOCS / "icon-512.png")
    img.resize((128, 128), Image.Resampling.NEAREST).save(
        ROOT / "src/main/resources/assets/ninelives/icon.png")
    print("icon: docs/images/icon-512.png + src/main/resources/assets/ninelives/icon.png")


def make_banner():
    size = (1280, 720)
    img = gradient(size, BG_TOP, BG_BOTTOM)
    img.alpha_composite(vignette(size, 100))
    draw = ImageDraw.Draw(img)
    frame(draw, size, 22, GOLD_DARK, 5)
    frame(draw, size, 34, GOLD, 3)
    for x, y in ((34, 34), (size[0] - 35, 34), (34, size[1] - 35), (size[0] - 35, size[1] - 35)):
        draw.rectangle([x - 5, y - 5, x + 5, y + 5], fill=GOLD)
    glow = glow_layer(size, (250, 360), 300, GOLD, 90)
    img.alpha_composite(glow)
    heart = Image.open(GUI / "heart_big_gold.png").convert("RGBA")
    heart = heart.resize((300, 300), Image.Resampling.NEAREST)
    img.alpha_composite(heart, (100, 210))
    draw = ImageDraw.Draw(img)

    title = "NINE LIVES"
    title_scale = 12
    title_x = 470
    title_y = 196
    draw_text(draw, title, title_x, title_y, title_scale, 12, GOLD, GOLD_DARK)

    for i in range(9):
        small = Image.open(GUI / "heart_full_gold.png").convert("RGBA")
        small = small.resize((27, 27), Image.Resampling.NEAREST)
        img.alpha_composite(small, (title_x + 4 + i * 40, 330))
    draw = ImageDraw.Draw(img)

    tagline = "ONE HEART. NINE LIVES."
    draw_text(draw, tagline, title_x + 4, 392, 4, 8, CREAM)
    edition = "FABRIC  /  MINECRAFT 26.3"
    draw_text(draw, edition, title_x + 4, 480, 3, 6, MUTED)

    img.save(DOCS / "banner-ninelives.png")
    print("banner: docs/images/banner-ninelives.png")


def apply_source_artwork():
    emblem = Image.open(SRC / "emblem.png").convert("RGBA")
    icon = emblem.resize((512, 512), Image.Resampling.LANCZOS)
    icon.save(DOCS / "icon-512.png")
    icon.resize((128, 128), Image.Resampling.LANCZOS).save(
        ROOT / "src/main/resources/assets/ninelives/icon.png")
    Image.open(SRC / "banner.png").convert("RGB").save(DOCS / "banner-ninelives.png")
    print("artwork: docs/images/icon-512.png + src/main/resources/assets/ninelives/icon.png + docs/images/banner-ninelives.png")


def main():
    DOCS.mkdir(parents=True, exist_ok=True)
    if (SRC / "emblem.png").exists() and (SRC / "banner.png").exists():
        apply_source_artwork()
        return
    make_icon()
    make_banner()


if __name__ == "__main__":
    main()
