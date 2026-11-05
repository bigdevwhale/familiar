"""
Puts the review frames (app/build/art/review/fNN.png, from the RenderArt.review test) together into one
labelled, looping GIF: app/build/art/review.gif.

    ./gradlew :app:testDebugUnitTest -Prender --tests "app.familiar.RenderArt.review"
    python tools/review.py
"""
import glob
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "app", "build", "art")
CELL = 160
COLS = ["Egg", "Hatchling", "Young", "Grown", "Ancient", "Astral", "Shadow", "Bloom", "Sage", "Wild", "Asleep", "Stroked", "Shiny"]
ROWS = ["Mimic", "Kitsune", "Slime", "Dragon", "Phoenix", "Griffin", "Owlbear", "Moth", "Tanuki", "Sprout", "Ghost", "Bookworm", "Chimera", "Magpie"]
LEFT, TOP = 120, 36
# One idle frame lasts a sixth of a second in the app (12 beats, two frames a beat, about 4 beats a second).
MS = 125


def font(size):
    for name in ("arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def main():
    frames = sorted(glob.glob(os.path.join(ART, "review", "f*.png")))
    if not frames:
        raise SystemExit("No frames: run the RenderArt.review test first.")
    f = font(18)
    out = []
    for path in frames:
        sheet = Image.open(path).convert("RGB")
        canvas = Image.new("RGB", (LEFT + sheet.width, TOP + sheet.height), (243, 235, 221))
        canvas.paste(sheet, (LEFT, TOP))
        d = ImageDraw.Draw(canvas)
        for i, name in enumerate(COLS):
            w = d.textlength(name, font=f)
            d.text((LEFT + i * CELL + (CELL - w) / 2, 8), name, fill=(70, 50, 60), font=f)
        for i, name in enumerate(ROWS):
            d.text((10, TOP + i * CELL + CELL / 2 - 10), name, fill=(70, 50, 60), font=f)
        out.append(canvas.quantize(colors=255, method=Image.Quantize.MEDIANCUT))
    dst = os.path.join(ART, "review.gif")
    out[0].save(dst, save_all=True, append_images=out[1:], duration=MS, loop=0, optimize=True)
    print(dst, len(out), "frames", out[0].size)


if __name__ == "__main__":
    main()
