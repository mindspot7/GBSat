"""Original sample box art for GBSat's README screenshots (fictional games, no real artwork or trademarks).

Drawn at 2x and downscaled for smooth edges. Fonts: Google Fonts (SIL Open Font License).
Usage: python3 tools/make_sample_covers.py <fonts dir> apps/android/app/src/test/resources/covers
Fonts (from github.com/google/fonts, ofl/): Anton, Bungee, Russo One, Orbitron (variable, saved as Orbitron.ttf),
Cinzel Decorative Black, Bangers, Black Ops One, Audiowide, saved under the file names used below.
"""
import math
import random
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONTS, OUT = sys.argv[1], sys.argv[2]
S = 2  # supersampling


def font(name, size, weight=None):
    f = ImageFont.truetype(f"{FONTS}/{name}", size * S)
    if name.startswith("Orbitron"):
        f.set_variation_by_axes([weight or 700])
    return f


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vgrad(w, h, stops):
    """Vertical gradient through [(t, (r,g,b)), ...]."""
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        for i in range(len(stops) - 1):
            t0, c0 = stops[i]
            t1, c1 = stops[i + 1]
            if t0 <= t <= t1:
                c = lerp(c0, c1, (t - t0) / max(1e-6, t1 - t0))
                break
        d.line([(0, y), (w, y)], fill=c)
    return img


def glow(img, xy, r, color, strength=0.8):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x, y = xy
    d.ellipse([x - r, y - r, x + r, y + r], fill=color + (int(255 * strength),))
    layer = layer.filter(ImageFilter.GaussianBlur(r * 0.5))
    img.alpha_composite(layer)


def stars(img, n, seed, area=None, color=(255, 255, 255)):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    w, h = img.size
    x0, y0, x1, y1 = area or (0, 0, w, h)
    for _ in range(n):
        x, y = rnd.uniform(x0, x1), rnd.uniform(y0, y1)
        r = rnd.choice([1, 1, 1, 2, 2, 3]) * S / 2
        a = rnd.randint(120, 255)
        d.ellipse([x - r, y - r, x + r, y + r], fill=color + (a,))


def logo(img, text, fnt, y, fill_top, fill_bottom, outline=(20, 10, 30), stroke=6, shadow=True, italic=0.0, lines_gap=0.92):
    """Centered logo text with gradient fill, outline and drop shadow; '\n' splits lines."""
    w, h = img.size
    lines = text.split("\n")
    d0 = ImageDraw.Draw(img)
    cy = y
    for line in lines:
        bbox = d0.textbbox((0, 0), line, font=fnt, stroke_width=stroke * S)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        layer = Image.new("RGBA", (tw + 40 * S, th + 40 * S), (0, 0, 0, 0))
        mask = Image.new("L", layer.size, 0)
        ImageDraw.Draw(mask).text((20 * S - bbox[0], 20 * S - bbox[1]), line, font=fnt, fill=255)
        outline_mask = Image.new("L", layer.size, 0)
        ImageDraw.Draw(outline_mask).text((20 * S - bbox[0], 20 * S - bbox[1]), line, font=fnt, fill=255,
                                          stroke_width=stroke * S, stroke_fill=255)
        fill = vgrad(layer.size[0], layer.size[1], [(0.0, fill_top), (1.0, fill_bottom)]).convert("RGBA")
        out = Image.new("RGBA", layer.size, outline + (255,))
        layer = Image.composite(out, layer, outline_mask)
        layer = Image.composite(fill, layer, mask)
        if italic:
            layer = layer.transform(layer.size, Image.AFFINE, (1, italic, -italic * layer.size[1] / 2, 0, 1, 0), Image.BICUBIC)
        x = (w - layer.size[0]) // 2
        if shadow:
            sh = Image.new("RGBA", layer.size, (0, 0, 0, 0))
            sh.putalpha(layer.getchannel("A").point(lambda a: a * 0.6))
            sh = sh.filter(ImageFilter.GaussianBlur(6 * S))
            img.alpha_composite(sh, (x + 4 * S, int(cy) + 8 * S))
        img.alpha_composite(layer, (x, int(cy)))
        cy += th * lines_gap + 24 * S
    return cy


def small_text(img, text, fnt, xy, fill, anchor="mm"):
    ImageDraw.Draw(img).text(xy, text, font=fnt, fill=fill, anchor=anchor)


def band(img, label="SAMPLE"):
    """Black top band like a box-art header strip, marking the cover as a sample."""
    w, _ = img.size
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w, 30 * S], fill=(12, 12, 16, 255))
    small_text(img, label, font("Orbitron.ttf", 13), (16 * S, 15 * S), (235, 235, 240), "lm")
    small_text(img, "GBSat demo cover", font("Orbitron.ttf", 10), (w - 16 * S, 15 * S), (150, 150, 165), "rm")


def footer(img, tag):
    w, h = img.size
    d = ImageDraw.Draw(img)
    d.rectangle([0, h - 34 * S, w, h], fill=(10, 10, 14, 230))
    small_text(img, tag, font("Orbitron.ttf", 12), (w // 2, h - 17 * S), (225, 225, 235))


def finish(img, name):
    img = img.convert("RGB").resize((img.size[0] // S, img.size[1] // S), Image.LANCZOS)
    img.save(f"{OUT}/{name}.png", optimize=True)


# ---- Covers --------------------------------------------------------------------------------------------------------

def thunder_circuit(W=560, H=640):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (40, 20, 80)), (0.38, (230, 90, 70)), (0.55, (255, 190, 90)), (0.56, (40, 40, 60)), (1, (20, 20, 30))]).convert("RGBA")
    glow(img, (w // 2, int(h * 0.52)), 110 * S, (255, 220, 120), 0.9)
    d = ImageDraw.Draw(img)
    hz = int(h * 0.555)
    # mountains on the horizon
    for i, (x, peak) in enumerate([(0.0, 0.47), (0.22, 0.43), (0.5, 0.48), (0.75, 0.42), (1.0, 0.47)]):
        pass
    d.polygon([(0, hz), (int(w * .15), int(h * .47)), (int(w * .32), hz)], fill=(60, 30, 80))
    d.polygon([(int(w * .62), hz), (int(w * .82), int(h * .45)), (w, hz)], fill=(60, 30, 80))
    # road in perspective
    d.polygon([(int(w * .46), hz), (int(w * .54), hz), (int(w * 1.1), h), (int(-w * .1), h)], fill=(45, 45, 55))
    for k in range(9):
        t0, t1 = (k / 9) ** 1.8, ((k + 0.45) / 9) ** 1.8
        y0, y1 = hz + (h - hz) * t0, hz + (h - hz) * t1
        hw0, hw1 = 2 * S + 14 * S * t0, 2 * S + 14 * S * t1
        d.polygon([(w / 2 - hw0, y0), (w / 2 + hw0, y0), (w / 2 + hw1, y1), (w / 2 - hw1, y1)], fill=(250, 230, 120))
    for side in (-1, 1):
        d.line([(w / 2 + side * 0.04 * w, hz), (w / 2 + side * 0.62 * w, h)], fill=(240, 240, 250), width=4 * S)
    # car (rear view)
    cx, cy = w // 2, int(h * 0.80)
    d.rounded_rectangle([cx - 120 * S, cy - 40 * S, cx + 120 * S, cy + 30 * S], 18 * S, fill=(210, 30, 40))
    d.polygon([(cx - 80 * S, cy - 40 * S), (cx - 55 * S, cy - 85 * S), (cx + 55 * S, cy - 85 * S), (cx + 80 * S, cy - 40 * S)], fill=(170, 20, 30))
    d.polygon([(cx - 62 * S, cy - 44 * S), (cx - 44 * S, cy - 78 * S), (cx + 44 * S, cy - 78 * S), (cx + 62 * S, cy - 44 * S)], fill=(60, 90, 130))
    d.rectangle([cx - 135 * S, cy - 58 * S, cx + 135 * S, cy - 48 * S], fill=(30, 30, 35))  # wing
    for sx in (-1, 1):
        d.rounded_rectangle([cx + sx * 100 * S - 22 * S, cy + 10 * S, cx + sx * 100 * S + 22 * S, cy + 48 * S], 6 * S, fill=(20, 20, 22))
        d.rounded_rectangle([cx + sx * 70 * S - 22 * S, cy - 22 * S, cx + sx * 70 * S + 22 * S, cy - 8 * S], 4 * S, fill=(255, 80, 70))
    d.rectangle([cx - 30 * S, cy - 4 * S, cx + 30 * S, cy + 12 * S], fill=(240, 240, 240))
    small_text(img, "01", font("RussoOne-Regular.ttf.ttf", 14), (cx, cy + 4 * S), (20, 20, 30))
    band(img)
    logo(img, "THUNDER\nCIRCUIT", font("RussoOne-Regular.ttf.ttf", 64), 52 * S, (255, 255, 255), (255, 200, 60),
         outline=(120, 10, 20), stroke=5, italic=0.22)
    footer(img, "ARCADE RACING - 1-2 PLAYERS")
    finish(img, "Thunder Circuit")


def iron_fist(W=560, H=640):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (90, 10, 10)), (0.5, (220, 70, 20)), (1, (40, 5, 5))]).convert("RGBA")
    d = ImageDraw.Draw(img)
    cx, cy = w // 2, int(h * 0.58)
    for k in range(36):  # burst rays
        a0, a1 = k * math.tau / 36, (k + 0.5) * math.tau / 36
        d.polygon([(cx, cy), (cx + math.cos(a0) * w, cy + math.sin(a0) * w), (cx + math.cos(a1) * w, cy + math.sin(a1) * w)],
                  fill=(255, 150, 40, 70))
    glow(img, (cx, cy), 120 * S, (255, 240, 180), 0.9)

    def fighter(x, flip, color, belt):
        f = -1 if flip else 1
        g = Image.new("RGBA", img.size, (0, 0, 0, 0))
        q = ImageDraw.Draw(g)
        base = int(h * 0.95)
        q.ellipse([x - 34 * S, base - 330 * S, x + 34 * S, base - 262 * S], fill=color)  # head
        q.polygon([(x - 70 * S, base - 255 * S), (x + 70 * S, base - 255 * S), (x + 50 * S, base - 120 * S), (x - 50 * S, base - 120 * S)], fill=color)
        q.rectangle([x - 52 * S, base - 132 * S, x + 52 * S, base - 112 * S], fill=belt)
        q.polygon([(x - 50 * S, base - 115 * S), (x - 5 * S, base - 115 * S), (x - 30 * S, base), (x - 85 * S, base)], fill=color)
        q.polygon([(x + 5 * S, base - 115 * S), (x + 50 * S, base - 115 * S), (x + 85 * S, base), (x + 30 * S, base)], fill=color)
        # punching arm towards the center
        q.polygon([(x + f * 50 * S, base - 250 * S), (x + f * 170 * S, base - 235 * S), (x + f * 170 * S, base - 205 * S), (x + f * 50 * S, base - 205 * S)], fill=color)
        q.ellipse([x + f * 160 * S - 26 * S, base - 248 * S, x + f * 160 * S + 26 * S, base - 192 * S], fill=belt)
        q.polygon([(x - f * 60 * S, base - 245 * S), (x - f * 110 * S, base - 175 * S), (x - f * 85 * S, base - 160 * S), (x - f * 40 * S, base - 215 * S)], fill=color)
        img.alpha_composite(g)

    fighter(int(w * 0.27), False, (25, 15, 20, 255), (230, 200, 60, 255))
    fighter(int(w * 0.73), True, (15, 20, 40, 255), (60, 160, 240, 255))
    band(img)
    logo(img, "IRON FIST\nLEGENDS", font("Anton-Regular.ttf.ttf", 72), 46 * S, (255, 255, 255), (190, 190, 200),
         outline=(60, 0, 0), stroke=6)
    footer(img, "3D FIGHTING - 12 WARRIORS")
    finish(img, "Iron Fist Legends")


def starfall(W=420, H=600):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (5, 5, 25)), (0.6, (20, 15, 60)), (1, (60, 20, 80))]).convert("RGBA")
    stars(img, 260, 3)
    glow(img, (int(w * 0.8), int(h * 0.32)), 150 * S, (90, 140, 255), 0.5)
    d = ImageDraw.Draw(img)
    px, py, pr = int(w * 0.82), int(h * 0.34), 120 * S
    d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=(70, 110, 220))
    d.ellipse([px - pr + 30 * S, py - pr + 10 * S, px + pr + 10 * S, py + pr], fill=(40, 60, 150))
    d.arc([px - pr - 70 * S, py - 30 * S, px + pr + 70 * S, py + 40 * S], 160, 380, fill=(200, 210, 255), width=5 * S)
    # lasers
    for k, x in enumerate([0.2, 0.35, 0.5]):
        d.line([(w * x, h * 0.70), (w * (x + 0.12), h * 0.18)], fill=(120, 255, 200, 200), width=4 * S)
    # ship
    sx, sy = int(w * 0.42), int(h * 0.74)
    glow(img, (sx, sy + 70 * S), 50 * S, (255, 150, 60), 0.9)
    d.polygon([(sx, sy - 90 * S), (sx + 40 * S, sy + 50 * S), (sx, sy + 30 * S), (sx - 40 * S, sy + 50 * S)], fill=(220, 225, 240))
    d.polygon([(sx - 40 * S, sy + 10 * S), (sx - 105 * S, sy + 70 * S), (sx - 30 * S, sy + 45 * S)], fill=(200, 60, 70))
    d.polygon([(sx + 40 * S, sy + 10 * S), (sx + 105 * S, sy + 70 * S), (sx + 30 * S, sy + 45 * S)], fill=(200, 60, 70))
    d.polygon([(sx, sy - 50 * S), (sx + 12 * S, sy - 5 * S), (sx - 12 * S, sy - 5 * S)], fill=(80, 200, 255))
    band(img)
    logo(img, "STARFALL\nSQUADRON", font("Orbitron.ttf", 42, 900), 60 * S, (230, 250, 255), (110, 190, 255),
         outline=(10, 20, 60), stroke=4)
    footer(img, "SHOOT 'EM UP - 7 STAGES")
    finish(img, "Starfall Squadron")


def crown_of_embers(W=420, H=600):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (20, 10, 40)), (0.55, (80, 30, 90)), (0.75, (200, 90, 60)), (1, (30, 10, 20))]).convert("RGBA")
    stars(img, 140, 9, (0, 0, w, h * 0.5))
    glow(img, (int(w * 0.3), int(h * 0.36)), 70 * S, (255, 240, 200), 0.7)
    d = ImageDraw.Draw(img)
    d.ellipse([w * 0.3 - 48 * S, h * 0.36 - 48 * S, w * 0.3 + 48 * S, h * 0.36 + 48 * S], fill=(255, 245, 220))
    # castle on a hill
    base = int(h * 0.80)
    d.ellipse([-w * 0.3, base - 60 * S, w * 1.3, h * 1.3], fill=(25, 10, 25))
    sil = (25, 10, 25)
    for x, tw, th in [(0.38, 46, 230), (0.55, 60, 300), (0.72, 46, 210)]:
        cx = w * x
        d.rectangle([cx - tw * S / 2, base - th * S, cx + tw * S / 2, base], fill=sil)
        d.polygon([(cx - tw * S / 2 - 8 * S, base - th * S), (cx, base - th * S - 70 * S), (cx + tw * S / 2 + 8 * S, base - th * S)], fill=sil)
        for wy in range(2):
            yy = base - th * S + 40 * S + wy * 50 * S
            d.rectangle([cx - 6 * S, yy, cx + 6 * S, yy + 18 * S], fill=(255, 190, 90))
    d.rectangle([w * 0.38, base - 140 * S, w * 0.72, base], fill=sil)
    for k in range(9):
        x = w * 0.38 + k * (w * 0.34) / 9
        d.rectangle([x, base - 158 * S, x + 14 * S, base - 140 * S], fill=sil)
    # embers
    rnd = random.Random(5)
    for _ in range(60):
        x, y = rnd.uniform(0, w), rnd.uniform(h * 0.45, h * 0.9)
        r = rnd.uniform(1.5, 4) * S
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, rnd.randint(120, 200), 60, rnd.randint(140, 255)))
    band(img)
    logo(img, "CROWN OF\nEMBERS", font("CinzelDecorative-Black.ttf.ttf", 46), 54 * S, (255, 240, 190), (210, 140, 40),
         outline=(40, 10, 10), stroke=4)
    footer(img, "ROLE-PLAYING ADVENTURE")
    finish(img, "Crown of Embers")


def mecha_vanguard(W=560, H=640):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (10, 25, 40)), (0.7, (30, 70, 100)), (1, (10, 20, 30))]).convert("RGBA")
    d = ImageDraw.Draw(img)
    for k in range(0, w, 40 * S):  # grid floor
        d.line([(w / 2, h * 0.6), (k * 2 - w / 2, h)], fill=(80, 200, 255, 60), width=S)
    for k in range(8):
        y = h * 0.6 + (h * 0.4) * (k / 8) ** 1.6
        d.line([(0, y), (w, y)], fill=(80, 200, 255, 60), width=S)
    glow(img, (w // 2, int(h * 0.5)), 170 * S, (90, 200, 255), 0.35)
    cx, base = w // 2, int(h * 0.93)
    steel, dark, acc = (150, 165, 180, 255), (70, 80, 95, 255), (255, 120, 30, 255)
    d.rectangle([cx - 95 * S, base - 370 * S, cx + 95 * S, base - 230 * S], fill=steel)  # torso
    d.polygon([(cx - 95 * S, base - 370 * S), (cx - 150 * S, base - 395 * S), (cx - 150 * S, base - 320 * S), (cx - 95 * S, base - 300 * S)], fill=dark)
    d.polygon([(cx + 95 * S, base - 370 * S), (cx + 150 * S, base - 395 * S), (cx + 150 * S, base - 320 * S), (cx + 95 * S, base - 300 * S)], fill=dark)
    d.rectangle([cx - 38 * S, base - 425 * S, cx + 38 * S, base - 370 * S], fill=steel)  # head
    d.rectangle([cx - 28 * S, base - 405 * S, cx + 28 * S, base - 392 * S], fill=(120, 255, 230, 255))
    d.rectangle([cx - 20 * S, base - 340 * S, cx + 20 * S, base - 300 * S], fill=acc)
    for sx in (-1, 1):
        d.rectangle([cx + sx * 150 * S - 28 * S, base - 330 * S, cx + sx * 150 * S + 28 * S, base - 170 * S], fill=dark)  # arms
        d.rectangle([cx + sx * 150 * S - 36 * S, base - 175 * S, cx + sx * 150 * S + 36 * S, base - 130 * S], fill=steel)
        d.polygon([(cx + sx * 20 * S, base - 230 * S), (cx + sx * 85 * S, base - 230 * S), (cx + sx * 105 * S, base), (cx + sx * 35 * S, base)], fill=steel)
        d.rectangle([cx + sx * 45 * S - 30 * S, base - 130 * S, cx + sx * 45 * S + 30 * S, base - 112 * S], fill=acc)
    d.rectangle([cx + 170 * S, base - 300 * S, cx + 210 * S, base - 120 * S], fill=(40, 45, 55, 255))  # cannon
    band(img)
    logo(img, "MECHA\nVANGUARD", font("BlackOpsOne-Regular.ttf.ttf", 62), 46 * S, (240, 245, 250), (150, 170, 190),
         outline=(10, 15, 25), stroke=5)
    footer(img, "MECH ACTION - ARMORED CORPS")
    finish(img, "Mecha Vanguard")


def pocket_pals(W=560, H=560):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (90, 190, 255)), (0.65, (190, 235, 255)), (1, (190, 235, 255))]).convert("RGBA")
    d = ImageDraw.Draw(img)
    for x, y, r in [(0.18, 0.3, 40), (0.3, 0.27, 52), (0.78, 0.22, 46), (0.88, 0.25, 34)]:
        d.ellipse([w * x - r * S, h * y - r * S * 0.6, w * x + r * S, h * y + r * S * 0.6], fill=(255, 255, 255))
    d.ellipse([-w * 0.2, h * 0.68, w * 0.7, h * 1.4], fill=(90, 200, 90))
    d.ellipse([w * 0.4, h * 0.72, w * 1.3, h * 1.4], fill=(60, 170, 70))
    for k, x in enumerate([0.15, 0.62, 0.86]):  # coins
        cx, cy = w * x, h * (0.55 + 0.05 * (k % 2))
        d.ellipse([cx - 16 * S, cy - 16 * S, cx + 16 * S, cy + 16 * S], fill=(255, 210, 40), outline=(200, 140, 20), width=3 * S)
    cx, cy = w * 0.42, h * 0.70  # round hero
    d.ellipse([cx - 70 * S, cy - 70 * S, cx + 70 * S, cy + 70 * S], fill=(255, 120, 160), outline=(120, 30, 60), width=5 * S)
    for ex in (-24, 24):
        d.ellipse([cx + ex * S - 12 * S, cy - 30 * S, cx + ex * S + 12 * S, cy + 4 * S], fill=(255, 255, 255))
        d.ellipse([cx + ex * S - 5 * S, cy - 18 * S, cx + ex * S + 7 * S, cy - 2 * S], fill=(30, 20, 40))
    d.arc([cx - 26 * S, cy - 4 * S, cx + 26 * S, cy + 34 * S], 20, 160, fill=(120, 30, 60), width=5 * S)
    d.ellipse([cx - 80 * S, cy + 50 * S, cx - 30 * S, cy + 76 * S], fill=(220, 50, 80))
    d.ellipse([cx + 30 * S, cy + 50 * S, cx + 80 * S, cy + 76 * S], fill=(220, 50, 80))
    bx, by = w * 0.74, h * 0.66  # buddy
    d.ellipse([bx - 42 * S, by - 42 * S, bx + 42 * S, by + 42 * S], fill=(120, 200, 255), outline=(30, 70, 120), width=4 * S)
    d.ellipse([bx - 18 * S, by - 14 * S, bx - 4 * S, by + 4 * S], fill=(30, 20, 40))
    d.ellipse([bx + 4 * S, by - 14 * S, bx + 18 * S, by + 4 * S], fill=(30, 20, 40))
    band(img)
    logo(img, "POCKET PALS\nPARTY!", font("Bangers-Regular.ttf.ttf", 70), 44 * S, (255, 240, 80), (255, 140, 30),
         outline=(110, 30, 90), stroke=7, italic=0.08)
    footer(img, "PLATFORM FUN - 4 PLAYERS")
    finish(img, "Pocket Pals Party")


def deep_blue(W=420, H=600):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (40, 160, 200)), (0.4, (10, 70, 130)), (1, (2, 10, 35))]).convert("RGBA")
    rays = Image.new("RGBA", img.size, (0, 0, 0, 0))
    q = ImageDraw.Draw(rays)
    for k in range(6):
        x = w * (0.1 + 0.16 * k)
        q.polygon([(x, 0), (x + 40 * S, 0), (x + 160 * S, h * 0.8), (x + 60 * S, h * 0.8)], fill=(200, 240, 255, 40))
    img.alpha_composite(rays.filter(ImageFilter.GaussianBlur(10 * S)))
    d = ImageDraw.Draw(img)
    cx, cy = w * 0.52, h * 0.70  # submarine
    glow(img, (int(cx + 120 * S), int(cy)), 70 * S, (255, 240, 160), 0.6)
    d.ellipse([cx - 130 * S, cy - 40 * S, cx + 130 * S, cy + 40 * S], fill=(240, 190, 40), outline=(90, 60, 10), width=4 * S)
    d.rounded_rectangle([cx - 40 * S, cy - 80 * S, cx + 30 * S, cy - 30 * S], 14 * S, fill=(240, 190, 40), outline=(90, 60, 10), width=4 * S)
    for k in range(3):
        px = cx - 70 * S + k * 55 * S
        d.ellipse([px - 14 * S, cy - 14 * S, px + 14 * S, cy + 14 * S], fill=(120, 220, 255), outline=(90, 60, 10), width=3 * S)
    d.polygon([(cx - 130 * S, cy), (cx - 175 * S, cy - 35 * S), (cx - 175 * S, cy + 35 * S)], fill=(200, 150, 30))
    rnd = random.Random(11)
    for _ in range(40):
        x, y, r = rnd.uniform(0, w), rnd.uniform(h * 0.35, h), rnd.uniform(3, 10) * S
        d.ellipse([x - r, y - r, x + r, y + r], outline=(200, 240, 255, 160), width=2 * S)
    for k in range(5):  # sea floor kelp
        x = w * (0.05 + 0.22 * k)
        d.polygon([(x, h), (x + 10 * S, h * 0.82), (x + 24 * S, h)], fill=(20, 90, 60))
    band(img)
    logo(img, "DEEP BLUE\nODYSSEY", font("Audiowide-Regular.ttf.ttf", 46), 60 * S, (220, 255, 255), (80, 210, 230),
         outline=(0, 30, 60), stroke=4)
    footer(img, "UNDERWATER EXPLORATION")
    finish(img, "Deep Blue Odyssey")


def night_strike(W=420, H=600):
    w, h = W * S, H * S
    img = vgrad(w, h, [(0, (25, 5, 15)), (0.6, (90, 15, 35)), (1, (10, 5, 10))]).convert("RGBA")
    glow(img, (int(w * 0.5), int(h * 0.42)), 150 * S, (255, 60, 60), 0.6)
    d = ImageDraw.Draw(img)
    d.ellipse([w * 0.5 - 115 * S, h * 0.42 - 115 * S, w * 0.5 + 115 * S, h * 0.42 + 115 * S], fill=(230, 40, 50))
    rnd = random.Random(2)
    x = 0
    while x < w:  # city rooftops
        bw = rnd.randint(40, 90) * S
        bh = rnd.randint(int(h * 0.12), int(h * 0.3))
        d.rectangle([x, h - bh, x + bw, h], fill=(12, 6, 12))
        for wy in range(h - bh + 12 * S, h, 26 * S):
            for wx in range(x + 8 * S, x + bw - 10 * S, 20 * S):
                if rnd.random() < 0.3:
                    d.rectangle([wx, wy, wx + 8 * S, wy + 10 * S], fill=(255, 200, 90))
        x += bw
    # leaping ninja silhouette with sword
    nx, ny = w * 0.5, h * 0.44
    sil = (8, 4, 8)
    d.ellipse([nx - 22 * S, ny - 120 * S, nx + 22 * S, ny - 76 * S], fill=sil)
    d.polygon([(nx - 30 * S, ny - 80 * S), (nx + 34 * S, ny - 80 * S), (nx + 20 * S, ny), (nx - 26 * S, ny)], fill=sil)
    d.polygon([(nx - 26 * S, ny - 4 * S), (nx - 4 * S, ny), (nx - 70 * S, ny + 90 * S), (nx - 92 * S, ny + 76 * S)], fill=sil)
    d.polygon([(nx + 2 * S, ny), (nx + 22 * S, ny - 6 * S), (nx + 90 * S, ny + 40 * S), (nx + 76 * S, ny + 60 * S)], fill=sil)
    d.polygon([(nx + 30 * S, ny - 74 * S), (nx + 110 * S, ny - 130 * S), (nx + 118 * S, ny - 118 * S), (nx + 36 * S, ny - 60 * S)], fill=sil)
    d.line([(nx + 112 * S, ny - 126 * S), (nx + 190 * S, ny - 230 * S)], fill=(230, 230, 240), width=5 * S)
    d.line([(nx - 22 * S, ny - 100 * S), (nx - 110 * S, ny - 80 * S)], fill=sil, width=6 * S)  # scarf
    band(img)
    logo(img, "NIGHT STRIKE\nZERO", font("Anton-Regular.ttf.ttf", 62), 52 * S, (255, 255, 255), (255, 120, 120),
         outline=(30, 0, 0), stroke=5)
    footer(img, "NINJA ACTION")
    finish(img, "Night Strike Zero")


for f in (thunder_circuit, iron_fist, starfall, crown_of_embers, mecha_vanguard, pocket_pals, deep_blue, night_strike):
    f()
print("done")
