"""Generates the TastySpread launcher icons (adaptive foreground + legacy mipmaps).

Same artwork as the TastySpread desktop icon: a vertical-spread payoff over a mean-reverting spread.
Run with any Python that has Pillow:  python3 tools/make_launcher_icons.py
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
S = 2048
TOP, BOT = (22, 20, 18), (58, 34, 12)


def stroke(draw, pts, width, fill):
    r = width / 2
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(x1 - x0, y1 - y0) / (r * 0.25)))
        for k in range(n + 1):
            x, y = x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n
            draw.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def artwork(L, R, top, bottom):
    """Transparent layer with the chart drawn inside the box [L,R]×[top,bottom]."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    h = bottom - top
    mid = top + h * 0.72
    band = h * 0.20
    for yy in (mid - band, mid + band):
        for x in range(int(L), int(R), int(S * 0.035)):
            d.line([(x, yy), (x + S * 0.018, yy)], fill=(255, 255, 255, 50), width=int(S * 0.007))
    d.line([(L, mid), (R, mid)], fill=(255, 255, 255, 80), width=int(S * 0.007))
    pts = [(L + (R - L) * t, mid - h * 0.24 * math.exp(-2.2 * t) * math.cos(2 * math.pi * 2.2 * t))
           for t in (i / 399 for i in range(400))]
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    stroke(ImageDraw.Draw(glow), pts, S * 0.05, (45, 212, 191, 150))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(S * 0.02)))
    stroke(d, pts, S * 0.03, (45, 212, 191, 255))
    k1, k2 = L + (R - L) * 0.36, L + (R - L) * 0.64
    low, high = top + h * 0.22, top + h * 0.02
    pay = [(L, low), (k1, low), (k2, high), (R, high)]
    glow2 = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    stroke(ImageDraw.Draw(glow2), pay, S * 0.06, (245, 158, 11, 160))
    img.alpha_composite(glow2.filter(ImageFilter.GaussianBlur(S * 0.022)))
    stroke(d, pay, S * 0.04, (251, 191, 36, 255))
    for x, y in ((k1, low), (k2, high)):
        r = S * 0.032
        d.ellipse([x - r, y - r, x + r, y + r], fill=(251, 191, 36, 255))
    return img


def gradient():
    g = Image.new("RGBA", (S, S))
    gd = ImageDraw.Draw(g)
    for y in range(S):
        t = y / S
        gd.line([(0, y), (S, y)], fill=tuple(int(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3)) + (255,))
    return g


# Adaptive foreground: keep the artwork inside the 66% safe zone.
fg = artwork(S * 0.25, S * 0.75, S * 0.28, S * 0.70)
fg.resize((432, 432), Image.LANCZOS).save(RES / "drawable" / "ic_launcher_foreground.png")

# Legacy square (rounded) and round icons.
full = gradient()
full.alpha_composite(artwork(S * 0.17, S * 0.83, S * 0.20, S * 0.80))
sq_mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(sq_mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
round_mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(round_mask).ellipse([0, 0, S - 1, S - 1], fill=255)
for folder, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    for name, mask in (("ic_launcher.png", sq_mask), ("ic_launcher_round.png", round_mask)):
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        out.paste(full, (0, 0), mask)
        out.resize((px, px), Image.LANCZOS).save(RES / f"mipmap-{folder}" / name)
print("icons written")
