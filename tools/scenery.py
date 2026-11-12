"""
Cuts the drawn den and tree (art/room/*.png, art/tree/*.png, drawn on flat magenta) down to the app's pixels.

    python tools/scenery.py

Also the finds familiars bring back (art/finds, 22 x 22 each, resources/finds/<name>.png) and the
dreams (art/dreams, 128 x 88 each, resources/dreams/<genre>.png; the app cuts them into a cloud).

The den is 144 x 108: each piece is shrunk into its spot and written as a whole-room layer, so the app
only stacks them (app/src/main/resources/room/<name>.png). The tree is 128 x 112, one picture per size
(app/src/main/resources/tree/<size>.png), with crowns.txt saying where the leaves are for the fruit.
"""
import os

import numpy as np
from PIL import Image
from scipy import ndimage

from familiars import crisp, keyed

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "art")
RES = os.path.join(ROOT, "app", "src", "main", "resources")

ROOM_W, ROOM_H = 144, 108
TREE_W, TREE_H = 128, 112

# Where each piece goes in the den: (x, y, w, h) of its spot, and how it sits in it
# (bottom: stands on the floor; top: hangs from the ceiling; mid: on the wall).
SPOTS = {
    "window_moon_night": (4, 8, 38, 46, "mid"),
    "window_moon_day": (4, 8, 38, 46, "mid"),
    "window_stained_night": (5, 4, 36, 52, "mid"),
    "window_stained_day": (5, 4, 36, 52, "mid"),
    "bookshelf": (106, 22, 36, 78, "bottom"),
    "portrait": (108, 10, 32, 38, "mid"),
    "banner": (56, 0, 32, 28, "top"),
    "garland": (0, 0, 144, 18, "top"),
    "lantern": (36, 0, 18, 34, "top"),
    "rug_red": (24, 90, 96, 18, "mid"),
    "rug_stars": (24, 90, 96, 18, "mid"),
    "armchair": (0, 56, 40, 44, "bottom"),
    "cauldron": (0, 70, 36, 32, "bottom"),
    "telescope": (2, 48, 36, 54, "bottom"),
    "mandrake": (106, 62, 34, 40, "bottom"),
    "treasure": (102, 70, 42, 32, "bottom"),
    "globe": (108, 58, 30, 44, "bottom"),
    "candles": (0, 84, 24, 22, "bottom"),
    # The den dressed for the days of a season.
    "season_cobweb": (0, 0, 32, 32, "top"),
    "season_pumpkins": (104, 86, 38, 18, "bottom"),
    "season_fir": (116, 60, 26, 44, "bottom"),
    "season_wreath": (13, 15, 20, 22, "mid"),
    "season_hearts": (0, 2, 144, 16, "top"),
    "season_bunting": (0, 1, 144, 13, "top"),
    "season_books": (90, 86, 22, 18, "bottom"),
    "season_daisies": (94, 80, 13, 24, "bottom"),
}
# Drawn facing the other way from where they hang.
FLIP = {"season_cobweb"}

TREES = ["small", "medium", "large"]
# How much of the tree box each size fills, so the tree visibly grows.
TREE_FILL = {"small": 0.62, "medium": 0.8, "large": 1.0}
TREE_GROUND = 109


def cutout(path):
    """The drawing with its magenta taken out, cropped to what is left."""
    im = np.array(Image.open(path).convert("RGBA"))
    solid = ~ndimage.binary_dilation(keyed(im[..., :3]), iterations=1)
    lab, n = ndimage.label(solid)
    if n:
        sizes = ndimage.sum(solid, lab, range(1, n + 1))
        solid &= np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= max(200, sizes.max() * 0.01)])
    im[..., 3] = np.where(solid, 255, 0)
    ys, xs = np.nonzero(solid)
    return im[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def fit(part, w, h):
    ph, pw = part.shape[:2]
    k = min(w / pw, h / ph)
    return crisp(part, max(1, round(pw * k)), max(1, round(ph * k)))


def ink(sprite):
    """A dark line round the outside, like the familiars have."""
    a = sprite[..., 3] > 0
    edge = a & ndimage.binary_dilation(~a, iterations=1)
    rgb = sprite[..., :3].astype(float)
    sprite[edge, :3] = (rgb[edge] * 0.3 + np.array([22.0, 14.0, 26.0]) * 0.7).astype(np.uint8)
    return sprite


DETAIL = 2


def save(arr, path, part=None, box=None, size=None, colors=64):
    """Writes [arr] to [path], and next to it (name@2x.png) the same picture DETAIL times finer: [part]
    shrunk into [box] (x, y, w, h on the coarse grid) of a canvas [size] (w, h) DETAIL times bigger, outlined
    the same way. With no [part], [arr] itself is redrawn from its source by the caller."""
    Image.fromarray(arr, "RGBA").save(path)
    if part is None:
        return
    d = DETAIL
    x, y, w, h = box
    big = np.zeros((size[1] * d, size[0] * d, 4), np.uint8)
    big[y * d:(y + h) * d, x * d:(x + w) * d] = ink(crisp(part, w * d, h * d))
    Image.fromarray(big, "RGBA").save(path[:-4] + f"@{d}x.png")


def fine_full(im, w, h, path, colors):
    """A whole-picture background DETAIL times finer, next to its coarse one."""
    d = DETAIL
    Image.fromarray(crisp(im, w * d, h * d, colors=colors), "RGBA").save(path[:-4] + f"@{d}x.png")


def room():
    out = os.path.join(RES, "room")
    os.makedirs(out, exist_ok=True)
    for t in ("night", "day"):
        im = np.array(Image.open(os.path.join(ART, "room", f"room_{t}.png")).convert("RGBA"))
        im[..., 3] = 255
        path = os.path.join(out, f"room_{t}.png")
        Image.fromarray(crisp(im, ROOM_W, ROOM_H, colors=96), "RGBA").save(path)
        fine_full(im, ROOM_W, ROOM_H, path, 128)
    for name, (x, y, w, h, sit) in SPOTS.items():
        part = cutout(os.path.join(ART, "room", name + ".png"))
        if name in FLIP:
            part = part[:, ::-1].copy()
        s = ink(fit(part, w, h))
        sh, sw = s.shape[:2]
        ox = x + (w - sw) // 2
        oy = y + h - sh if sit == "bottom" else y if sit == "top" else y + (h - sh) // 2
        layer = np.zeros((ROOM_H, ROOM_W, 4), np.uint8)
        layer[oy:oy + sh, ox:ox + sw] = s
        save(layer, os.path.join(out, name + ".png"), part, (ox, oy, sw, sh), (ROOM_W, ROOM_H))
        print(name, (ox, oy, sw, sh))


def hollow(pic, below, ground):
    """The hollow in the trunk, where two eyes glint: the biggest dark patch inside the trunk, under the crown."""
    a = pic[..., 3] > 0
    rgb = pic[..., :3].astype(int)
    L = (0.3 * rgb[..., 0] + 0.59 * rgb[..., 1] + 0.11 * rgb[..., 2]) / 255
    inner = a & ~ndimage.binary_dilation(~a, iterations=2)
    dark = inner & (L < 0.22)
    dark[:below - 6] = False
    dark[ground - (ground - below) // 5:] = False
    w = pic.shape[1]
    dark[:, :w // 3] = False
    dark[:, w - w // 3:] = False
    lab, n = ndimage.label(dark)
    if not n:
        return w // 2, (below + ground) // 2
    sizes = ndimage.sum(dark, lab, range(1, n + 1))
    cy, cx = ndimage.center_of_mass(lab == int(np.argmax(sizes)) + 1)
    return int(round(cx)), int(round(cy))


def tree():
    out = os.path.join(RES, "tree")
    os.makedirs(out, exist_ok=True)
    lines = ["# size  crown cx cy rx ry  hollow x y (written by tools/scenery.py)"]
    for name in TREES:
        part = cutout(os.path.join(ART, "tree", f"tree_{name}.png"))
        fill = TREE_FILL[name]
        s = ink(fit(part, int((TREE_W - 4) * fill), int((TREE_GROUND - 2) * fill)))
        sh, sw = s.shape[:2]
        ox, oy = (TREE_W - sw) // 2, TREE_GROUND - sh
        pic = np.zeros((TREE_H, TREE_W, 4), np.uint8)
        pic[oy:oy + sh, ox:ox + sw] = s
        save(pic, os.path.join(out, name + ".png"), part, (ox, oy, sw, sh), (TREE_W, TREE_H))
        # The crown: green pixels in the upper part of the tree, as an ellipse kept inside them.
        rgb = pic[..., :3].astype(int)
        green = (pic[..., 3] > 0) & (rgb[..., 1] > rgb[..., 0] + 10) & (rgb[..., 1] > rgb[..., 2])
        green[oy + int(sh * 0.75):] = False
        # Clumps of leaves are split by their dark outlines: closed up, so the crown is one piece.
        green = ndimage.binary_closing(green, iterations=3)
        lab, n = ndimage.label(green)
        sizes = ndimage.sum(green, lab, range(1, n + 1))
        crown = lab == (int(np.argmax(sizes)) + 1)
        crown = ndimage.binary_fill_holes(crown)
        ys, xs = np.nonzero(crown)
        cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
        rx, ry = (xs.max() - xs.min()) / 2 * 0.78, (ys.max() - ys.min()) / 2 * 0.72
        hx, hy = hollow(pic, int(ys.max()), oy + sh)
        lines.append(f"{name} {cx:.1f} {cy:.1f} {rx:.1f} {ry:.1f} {hx} {hy}")
        print(name, (ox, oy, sw, sh), lines[-1])
    with open(os.path.join(out, "crowns.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


FIND = 22
DREAM_W, DREAM_H = 128, 88


def finds():
    out = os.path.join(RES, "finds")
    os.makedirs(out, exist_ok=True)
    src = os.path.join(ART, "finds")
    for f in sorted(os.listdir(src)):
        part = cutout(os.path.join(src, f))
        s = fit(part, FIND, FIND)
        sh, sw = s.shape[:2]
        pic = np.zeros((FIND, FIND, 4), np.uint8)
        x, y = (FIND - sw) // 2, (FIND - sh) // 2
        pic[y:y + sh, x:x + sw] = s
        Image.fromarray(pic, "RGBA").save(os.path.join(out, f))
        # The finds are lined by the app, not here.
        d = DETAIL
        big = np.zeros((FIND * d, FIND * d, 4), np.uint8)
        big[y * d:(y + sh) * d, x * d:(x + sw) * d] = crisp(part, sw * d, sh * d)
        Image.fromarray(big, "RGBA").save(os.path.join(out, f[:-4] + f"@{d}x.png"))


def dreams():
    out = os.path.join(RES, "dreams")
    os.makedirs(out, exist_ok=True)
    src = os.path.join(ART, "dreams")
    for f in sorted(os.listdir(src)):
        im = np.array(Image.open(os.path.join(src, f)).convert("RGBA"))
        im[..., 3] = 255
        # Cropped to the scene's shape, from the middle, before it is shrunk.
        h, w = im.shape[:2]
        if w / h > DREAM_W / DREAM_H:
            cw = int(h * DREAM_W / DREAM_H)
            im = im[:, (w - cw) // 2:(w - cw) // 2 + cw]
        else:
            ch = int(w * DREAM_H / DREAM_W)
            im = im[(h - ch) // 2:(h - ch) // 2 + ch]
        Image.fromarray(crisp(im, DREAM_W, DREAM_H, colors=64), "RGBA").save(os.path.join(out, f))
        fine_full(im, DREAM_W, DREAM_H, os.path.join(out, f), 96)


if __name__ == "__main__":
    room()
    tree()
    finds()
    dreams()
