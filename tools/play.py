"""Builds the Google Play graphics in docs/google-play/graphics from raw phone captures.

python tools/play.py <raw_dir>

<raw_dir> holds full-screen captures (1080 px wide) named <name>.png and <name>.ru.png, taken from the
`-Pdemo -Pdrawn -Pdemolang=en|ru` build so every cover is drawn by the app, not a publisher's. Writes
the 512 px icon, the 1024x500 feature graphics and eight 1080x1920 captioned screenshots per language.
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/google-play/graphics"
FONT = ROOT / "app/src/main/res/font"
STATUS = 110  # status bar height at 1080 px wide

NIGHT, VIOLET, CANDLE = (21, 18, 28), (52, 36, 96), (74, 52, 30)
SPARKS = [(114, 194, 255), (174, 147, 255), (233, 194, 106)]
INK, GOLD, LILAC = (246, 238, 222), (233, 194, 106), (214, 200, 255)

# The order they appear in the store, with the caption over each: a first line and its golden second.
SHOTS = [
    ("den", ("Read, and your", "familiar grows"), ("Читай — и твой", "фамильяр растёт")),
    ("ritual", ("A candle burns", "while you read"), ("Свеча горит,", "пока ты читаешь")),
    ("feast", ("Every page", "is a feast"), ("Каждая страница", "— это пир")),
    ("ledger", ("Your pace, habits", "and months"), ("Скорость, привычки", "и месяцы")),
    ("bestiary", ("Fourteen familiars", "to find"), ("Четырнадцать", "фамильяров")),
    ("tree", ("A glowing fruit", "for every book"), ("Светящийся плод", "за каждую книгу")),
    ("furnish", ("Furnish the den", "with sparks"), ("Обставь логово", "на искры")),
    ("shelf", ("Shelves, cycles", "and your own genres"), ("Полки, циклы", "и свои жанры")),
]


def font(name, size):
    return ImageFont.truetype(str(FONT / name), size)


def night(w, h, seed, keep_out=()):
    """Violet night above, warm candlelight below, square mana sparks."""
    g = Image.new("RGB", (w, h), NIGHT)
    d = ImageDraw.Draw(g)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(VIOLET[i] * (1 - t) + CANDLE[i] * t) for i in range(3)))
    rnd = random.Random(seed)
    for _ in range(w * h // 9000):
        x, y = rnd.randint(0, w), rnd.randint(0, h)
        if any(a <= x <= c and b <= y <= e for a, b, c, e in keep_out):
            continue
        s = rnd.choice([3, 3, 6, 6, 9])
        d.rectangle((x, y, x + s, y + s), fill=rnd.choice(SPARKS))
    return g.convert("RGBA")


def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius, fill=255)
    out = Image.new("RGBA", im.size)
    out.paste(im, (0, 0), mask)
    return out


def lay(g, card, x, y, radius):
    """A card with a soft shadow under it and a thin lilac rim."""
    sh = Image.new("RGBA", (card.width + 100, card.height + 100), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((50, 62, 50 + card.width, 62 + card.height), radius, fill=(0, 0, 0, 170))
    g.alpha_composite(sh.filter(ImageFilter.GaussianBlur(22)), (x - 50, y - 50))
    g.alpha_composite(card, (x, y))
    ImageDraw.Draw(g).rounded_rectangle((x - 2, y - 2, x + card.width + 1, y + card.height + 1), radius + 2, outline=(174, 147, 255, 140), width=2)


def screenshot(src, caption, seed):
    W, H = 1080, 1920
    g = night(W, H, seed, keep_out=[(60, 60, 1020, 300)])
    d = ImageDraw.Draw(g)
    title = font("cormorant_bold.ttf", 92)
    d.text((W / 2, 130), caption[0], font=title, fill=INK, anchor="mm")
    d.text((W / 2, 228), caption[1], font=title, fill=GOLD, anchor="mm")
    im = Image.open(src).convert("RGB")
    im = im.crop((0, STATUS, im.width, im.height))
    w = 800
    h = int(im.height * w / im.width)
    im = im.resize((w, h), Image.LANCZOS)
    top = 330
    # The phone runs off the bottom edge, as if held up to the reader.
    card = rounded(im.crop((0, 0, w, min(h, H - top + 40))), 40)
    lay(g, card, (W - w) // 2, top, 40)
    return g.convert("RGB")


def feature(lang):
    W, H = 1024, 500
    den = Image.open(ROOT / "docs/den.png").convert("RGBA")  # 576x432, the den with the kitsune
    dx, dy = W - den.width - 30, (H - den.height) // 2
    g = night(W, H, 5, keep_out=[(dx - 10, dy - 10, dx + den.width + 10, dy + den.height + 10), (40, 110, 470, 420)])
    lay(g, rounded(den, 28), dx, dy, 28)
    d = ImageDraw.Draw(g)
    name, lines = ("Familiar", ["A reading tracker", "with a familiar that", "feeds on stories"]) if lang == "en" else \
        ("Фамильяр", ["Трекер чтения", "с фамильяром, который", "питается историями"])
    g.alpha_composite(Image.open(ROOT / "docs/logo.png").convert("RGBA").resize((96, 96), Image.LANCZOS), (44, 120))
    d.text((44, 226), name, font=font("cormorant_bold.ttf", 84), fill=INK)
    sub = font("nunito_semibold.ttf", 25)
    for i, l in enumerate(lines):
        d.text((48, 330 + i * 34), l, font=sub, fill=LILAC if i else GOLD)
    g.convert("RGB").save(OUT / f"feature-graphic-{lang}.png", optimize=True)


def icon():
    """The README logo on a square ground of the same glow, so Play's own mask finds no corners."""
    g = Image.new("RGB", (512, 512))
    px = g.load()
    inner, outer = (0x4A, 0x34, 0x88), (0x17, 0x11, 0x2A)
    for y in range(512):
        for x in range(512):
            t = min(1.0, ((x - 256) ** 2 + (y - 225) ** 2) ** 0.5 / 360)
            px[x, y] = tuple(int(inner[i] * (1 - t) + outer[i] * t) for i in range(3))
    g = g.convert("RGBA")
    g.alpha_composite(Image.open(ROOT / "docs/logo.png").convert("RGBA"))
    g.convert("RGB").save(OUT / "icon-512.png", optimize=True)


def main():
    raw = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    icon()
    for lang in ("en", "ru"):
        feature(lang)
        folder = OUT / f"screenshots-{lang}"
        folder.mkdir(exist_ok=True)
        for i, (name, en, ru) in enumerate(SHOTS, 1):
            suffix = ".ru" if lang == "ru" else ""
            screenshot(raw / f"{name}{suffix}.png", en if lang == "en" else ru, i).save(folder / f"{i}.png", optimize=True)
    print("done:", OUT)


if __name__ == "__main__":
    main()
