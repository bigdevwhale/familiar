"""Builds README / Stillcraft screenshots from raw phone captures.

python tools/shots.py <raw_dir> <out_dir>

<raw_dir> holds full-screen captures (1080x2400) named <name>.png and <name>.ru.png.
Each one is cropped below the status bar, scaled to 540 px wide with rounded corners, and
four of them are laid out on a candlelit night as overview.png / overview.ru.png.
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

STATUS = 110  # status bar height at 1080 px wide
OVERVIEW = ["den", "ritual", "furnish", "saga"]
SINGLE = ["den", "ritual", "feast", "bestiary", "furnish", "shelf", "tree", "saga", "look"]

NIGHT = (21, 18, 28)
VIOLET = (52, 36, 96)
CANDLE = (74, 52, 30)


def card(src: Path) -> Image.Image:
    im = Image.open(src).convert("RGB")
    w, h = im.size
    # A shorter screen (1080x2340) keeps the same card: a sliver off each side instead of a black band below.
    tall = min(2290, h - STATUS)
    side = (w - round(tall * 540 / 1145)) // 2
    im = im.crop((side, STATUS, w - side, STATUS + tall))
    im = im.resize((540, 1145), Image.LANCZOS)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.size[0] - 1, im.size[1] - 1), 34, fill=255)
    out = Image.new("RGBA", im.size)
    out.paste(im, (0, 0), mask)
    return out


def ground(w: int, h: int) -> Image.Image:
    g = Image.new("RGB", (w, h), NIGHT)
    d = ImageDraw.Draw(g)
    # Violet night above, warm candlelight from below.
    for y in range(h):
        t = y / h
        c = tuple(int(VIOLET[i] * (1 - t) + CANDLE[i] * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    # Square mana sparks.
    rnd = random.Random(3)
    for _ in range(160):
        x, y = rnd.randint(0, w), rnd.randint(0, h)
        s = rnd.choice([3, 3, 6, 6, 9])
        d.rectangle((x, y, x + s, y + s), fill=rnd.choice([(114, 194, 255), (174, 147, 255), (233, 194, 106)]))
    return g


def overview(cards: list[Image.Image]) -> Image.Image:
    W, H = 2400, 1300
    g = ground(W, H).convert("RGBA")
    xs = [64, 646, 1228, 1810]
    ys = [30, 92, 30, 92]
    for c, x, y in zip(cards, xs, ys):
        shadow = Image.new("RGBA", (c.size[0] + 80, c.size[1] + 80), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle((40, 40, 40 + c.size[0], 40 + c.size[1]), 34, fill=(0, 0, 0, 150))
        shadow = shadow.filter(ImageFilter.GaussianBlur(18))
        g.alpha_composite(shadow, (x - 40, y - 26))
        frame = Image.new("RGBA", (c.size[0] + 4, c.size[1] + 4), (0, 0, 0, 0))
        ImageDraw.Draw(frame).rounded_rectangle((0, 0, frame.size[0] - 1, frame.size[1] - 1), 36, outline=(90, 70, 120), width=2)
        g.alpha_composite(c, (x, y))
        g.alpha_composite(frame, (x - 2, y - 2))
    return g.convert("RGB")


def main() -> None:
    raw, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    for suffix in ["", ".ru"]:
        made = {}
        for name in SINGLE:
            src = raw / f"{name}{suffix}.png"
            if src.exists():
                made[name] = card(src)
                made[name].save(out / f"{name}{suffix}.png", optimize=True)
        if all(n in made for n in OVERVIEW):
            overview([made[n] for n in OVERVIEW]).save(out / f"overview{suffix}.png", optimize=True)


if __name__ == "__main__":
    main()
