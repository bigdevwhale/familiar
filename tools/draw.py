"""
Draws the pictures the app still lacks with Codex's image tool, one at a time, and cuts them for the app.

    python tools/draw.py                 what is drawn and what is still missing
    python tools/draw.py all             draw everything missing, then cut it (familiars.py, scenery.py)
    python tools/draw.py badges          one group: familiars, eggs, room, badges
    python tools/draw.py hydra clock     only these pictures
    python tools/draw.py hydra --again   draw one again, over what is there
    python tools/draw.py badges --dry    print the prompts and draw nothing

Codex must be logged in with image generation on (`codex exec` is run read-only; it leaves each picture
in ~/.codex/generated_images/<session>/, from where it is copied into art/). Every picture is asked for on
flat magenta (#FF00FF), which the cutting tools key out, and with one picture of the app's own as a
reference for the style.
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "art")
MADE = os.path.join(os.path.expanduser("~"), ".codex", "generated_images")

STYLE = (
    "Cute chibi pixel art for a cosy reading app about magical familiars, in exactly the style of the attached "
    "reference picture: chunky pixels, a dark plum outline, soft shading with a few tones per colour, warm storybook "
    "colours. The background is one flat pure magenta, #FF00FF, with nothing else on it: no floor, no shadow, no "
    "glow, no text, no letters, no numbers, no frame. Never use magenta or hot pink in the drawing itself. "
)

FAMILIAR = (
    "A wide sheet with ONE familiar drawn FOUR times in a single row, left to right, as it grows: baby, young, adult, "
    "ancient. Each one is bigger and more elaborate than the one before, all four face the viewer, sit or stand "
    "upright on the same ground line, and are clearly separated by empty magenta (they never touch or overlap). "
    "Every one has a round head, two big round dark glossy eyes each with one white shine, pink blush on the cheeks "
    "and a small open smile. "
)

# name: what it is, in the colours the app's rare and form recolourings expect (ui/pix/Drawn.kt).
FAMILIARS = {
    # Drawn again: the first sheet came in huge stair-step pixels with a soft glow, and blurred when cut down.
    "slime": "A friendly round green slime, a jelly dome, in fresh leaf green (not yellow, not neon) with a darker "
             "green rim of shading and ONE clean white highlight on top. It is solid and opaque, not glowing and not "
             "see-through, drawn with clean sharp edges and a crisp dark outline like the reference, at the same fine "
             "pixel size as the reference, never in big blocky pixels. Leave the lower middle of its body a plain even "
             "green with nothing on it (things it swallows are shown there by the app). Baby: a small low dome. "
             "Young: rounder, with one small drip at its side. Adult: bigger, with two little nubs on top. Ancient: "
             "large, wearing a small gold crown with a red gem.",
    "hydra": "A baby hydra: a plump jade teal-green dragonet with a cream belly and tiny horns. Baby: one head. Young: "
             "two heads. Adult: three heads. Ancient: five heads, the middle one wearing a small gold crown. Only the "
             "middle head has big open eyes; the side heads are smaller, on short necks, with happy closed eyes "
             "(curved lines).",
    "bayun": "The storyteller cat of Russian fairy tales: a fluffy smoky blue-grey cat with a cream muzzle and a big "
             "striped tail. Baby: a kitten. Young: a thin gold chain collar. Adult: holds a small closed book. Ancient: "
             "a heavy gold chain round the neck, a longer mane, sitting on a stack of two old books.",
    "snail": "A snail with a butter-yellow body and a coral-pink spiral shell. Its face is on the front of the body "
             "(eyes on the face, not on stalks), with two short feelers on top of the head. Baby: a plain little "
             "shell. Young: the shell has a tiny round door. Adult: the shell is a little house with a window and a "
             "chimney. Ancient: a cosy cottage shell with a lit window, moss on the roof and a wisp of white smoke.",
    "salamander": "A fire salamander: ember-orange with a pale yellow belly and a small flame at the tip of its tail. "
                  "Baby: tiny, one spark. Young: a row of little flames down its back. Adult: bigger flames and "
                  "glowing spots. Ancient: a crest of tall flames like a crown, embers floating close to it.",
    "automaton": "A small clockwork robot made of warm brass with dark iron joints, a round head and an antenna with "
                 "a red bulb. Baby: a wind-up toy with a key in its back. Young: gears showing at its sides. Adult: "
                 "a round glowing yellow lamp in its chest. Ancient: ornate engraved brass, two antennae, a bigger "
                 "bright chest lamp.",
    "pixie": "A tiny fairy with lilac hair, a lilac petal dress and pale mint-green translucent wings, floating a "
             "little above the ground. Baby: curled in a flower bud with tiny wings. Young: small wings, bare feet. "
             "Adult: larger wings, holding a tiny book. Ancient: four wings, a crown of small white flowers, a few "
             "sparkles close to her.",
    "raven": "A raven with deep indigo-blue feathers with lighter blue highlights and a dark grey beak. Baby: a round "
             "fluffy chick. Young: sleeker, a white quill feather held under one wing. Adult: a rolled paper scroll "
             "under its wing. Ancient: larger, with a ruff of long feathers and an ink bottle beside its feet.",
    "sphinx": "A sphinx cub: a sandy-gold lion cub with a tufted tail. Baby: a plain cub. Young: a small teal and "
              "gold striped headcloth (a nemes). Adult: the headcloth and small folded feathered wings. Ancient: "
              "large wings, the headcloth, and a broad gold collar.",
    "owlet": "Clearly a BIRD, not a cat or a fox: a round owl with feathered wings instead of arms, a small yellow hooked beak (no nose, no whiskers, no mouth with teeth), two short feathered ear tufts and bird feet with three toes. A post owlet: a warm brown baby owl with a cream face disc and small ear tufts. Baby: a fluffy chick "
             "holding one white letter. Young: a little blue postman's cap. Adult: the cap and a blue satchel full of "
             "letters. Ancient: the cap with a gold badge, a bulging satchel and a sealed scroll under its wing.",
}

EGGS = (
    "A wide sheet with NINE different eggs in a single row, left to right, all the same size, standing upright on "
    "the same ground line and clearly separated by empty magenta. No faces on the eggs. In this order: "
    "1) a jade teal-green egg with a scaly pattern; 2) a smoky blue-grey egg with a thin gold chain round it; "
    "3) a butter-yellow egg with a coral-pink spiral on it; 4) an ember-orange egg with yellow flame marks; "
    "5) a brass egg with rivets and a small wind-up key on top; 6) a lilac egg with two tiny mint leaves like wings; "
    "7) a deep indigo-blue egg with one dark feather stuck to it; 8) a sandy-gold egg with teal and gold stripes; "
    "9) a warm brown speckled egg with a small blue stamp on it."
)

PIECE = (
    "ONE object for the familiar's den, alone in the middle of the picture, seen from the front and a little from "
    "above like furniture in a cosy pixel-art room, filling most of the picture. "
)

# name: (what it is, how it is shaped in its spot)
ROOM = {
    "fireplace": "A small stone fireplace with a wooden mantel, logs burning with bright yellow-orange flames, a "
                 "candlestick and two books on the mantel. Taller than wide.",
    "aquarium": "A round glass aquarium on a small wooden stand, with a tiny friendly purple kraken inside, bubbles "
                "and a green water plant. Taller than wide.",
    "mushrooms": "A cluster of five glowing blue and teal mushrooms of different heights growing from a mossy log. "
                 "About as wide as tall.",
    "clock": "A wooden cuckoo clock for the wall, with a little roof, a round face (no numbers, just dots), a small "
             "open door with a bird, and two hanging pine-cone weights. Taller than wide.",
    "map": "An old parchment map of imaginary lands pinned to the wall: an island, a mountain, a sea serpent, a "
           "dotted route and a red X, in a thin wooden frame. A little wider than tall.",
    "dreamcatcher": "A dreamcatcher hanging from a short string: a wooden hoop with a web, a few beads and three "
                    "feathers hanging down. Taller than wide.",
    "rug_moss": "A soft oval rug of green moss with small white flowers and two little red mushrooms on it, seen "
                "from above at a low angle so it lies flat on the floor. Much wider than tall (about 5 to 1).",
    "fireflies": "A glass jar with a cork lid, with five glowing yellow-green fireflies inside. Taller than wide.",
    "yarn": "A ball of soft blue yarn with a loose thread trailing from it. A little wider than tall.",
    "plush": "A small stuffed toy dragon sewn from green cloth with visible stitches, button eyes and a red felt "
             "wing, sitting. About as wide as tall.",
}

DEN = (
    "The inside of an EMPTY room, exactly the same view, proportions (4:3) and layout as the attached reference "
    "picture of the attic den: a back wall facing the viewer with bare space on the left for a window and on the "
    "right for a shelf, and a floor across the bottom fifth of the picture whose line is at the same height as in "
    "the reference. No furniture, no window, no creatures, no text. This one fills the whole picture: there is NO "
    "magenta anywhere. "
)

# name: (what the room is, by day, by night)
DENS = {
    "tower": ("A wizard's tower room: curved grey-blue stone walls, a wooden beam ceiling, a worn stone floor with a "
              "few flagstones, small brass star charts carved into the stones.",
              "By day: soft cool daylight, pale stones.",
              "By night: dark blue shadows, a faint warm candle glow from below, the stones deep indigo."),
    "burrow": ("A burrow under a hill: round earthen walls panelled with warm honey-coloured wood, a low arched "
               "ceiling with roots hanging from it, a floor of wide wooden boards.",
               "By day: warm golden light, cosy and bright.",
               "By night: deep brown shadows, a faint amber glow, the wood dark and warm."),
}

SHELF_PIECE = (
    "ONE small ornament that stands on a bookshelf between books, alone in the middle of the picture, seen straight "
    "from the front, standing upright on its flat base, filling most of the picture's height. Its base is flat and "
    "level, so it stands on a plank. "
)

# Things for the shelf of spines (ui/books/ShelfDecor.kt), the first six drawn again from the ones drawn in code.
SHELF = {
    "plant": "A leafy green house plant in a terracotta pot. Taller than wide.",
    "candles": "Three cream candles of different heights on a small brass dish, each with a lit yellow flame. A little wider than tall.",
    "crystal": "A cluster of pointed violet amethyst crystals on a small rock. About as tall as wide.",
    "hourglass": "An hourglass with a dark wooden frame and golden sand running down. Taller than wide.",
    "globe": "A small globe of the world, blue seas and green lands, on a brass stand. Taller than wide.",
    "bookend": "A grey stone bookend shaped like an arch with a golden crescent moon carved in it. Taller than wide.",
    "teapot": "A round blue and white porcelain teapot with a wisp of steam. Wider than tall.",
    "potions": "Three small glass potion bottles with corks, one red, one green, one blue, standing together. About as tall as wide.",
    "owl": "A small carved wooden owl figurine with big round eyes. Taller than wide.",
    "dragon": "A small green dragon figurine curled up asleep on a tiny pile of gold coins. Wider than tall.",
    "lamp": "A brass reading lamp with a green glass shade, its bulb lit. Taller than wide.",
    "cactus": "A round little cactus with a pink flower on top, in a small white pot. Taller than wide.",
    "skull": "A friendly cartoon skull with a short lit candle melting on top of it. About as tall as wide.",
    "terrarium": "A glass dome terrarium on a wooden base, with moss, a tiny fern and a red mushroom inside. About as tall as wide.",
}

BADGE = (
    "A wide sheet with SEVEN small emblems in a single row, left to right, all the same size, each a simple, bold "
    "object that reads at a glance when shrunk to a tiny icon, clearly separated by empty magenta. Only the objects "
    "themselves: no medal, no ring, no ribbon, no badge shape around them. In this order: "
)
BADGE_LAST = (
    "A wide sheet with TWO small emblems side by side, the same size, each a simple, bold object that reads at a "
    "glance when shrunk to a tiny icon, clearly separated by empty magenta. Only the objects themselves: no medal, "
    "no ring, no ribbon. In this order: "
)

# In the order of tools/scenery.py BADGES (and domain/Feats.kt), seven to a sheet.
BADGES = [
    ("books", "a stack of three closed books"), ("pages", "a single curling paper page"), ("hours", "an hourglass"),
    ("day_pages", "an open book with a small sun rising over it"), ("brick", "one very thick red book, heavy as a brick"),
    ("little", "a tiny thin book with a small feather on it"), ("month_books", "a wall calendar page with a small book on it"),

    ("one_day", "a closed book with a yellow lightning bolt across it"), ("listen", "a pair of big headphones"),
    ("reread", "a book with a circular arrow round it"), ("streak", "a bright flame"),
    ("days", "a calendar page with a green tick"), ("nights", "a crescent moon with two stars"), ("mornings", "a rising sun with rays"),

    ("midnight", "a round clock with both hands pointing straight up and a small star"), ("rituals", "a lit candle in a holder"),
    ("long_sit", "a candle burned down low in a pool of wax"), ("quiet", "a feather resting on a closed book"),
    ("fires", "burning logs with flames"), ("rain", "a rain cloud with drops"), ("weekends", "a teacup with steam next to a small book"),

    ("full_months", "a calendar page covered in gold stars"), ("comeback", "a red-gold phoenix feather rising from a little grey ash"),
    ("candles", "a candle on a small blue shield"), ("year_goal", "a green laurel wreath with a gold star in it"),
    ("genres", "a fan of five book spines in five different colours"), ("fantasy", "a blue wizard's hat with stars"),
    ("scifi", "a small rocket taking off"),

    ("mystery", "a magnifying glass"), ("horror", "a little black bat with yellow eyes"), ("romance", "a red heart with an arrow through it"),
    ("adventure", "a sailing ship"), ("classic", "a quill pen in an inkwell"), ("history", "a broken Greek column"),
    ("nonfiction", "a glowing light bulb"),

    ("poetry", "a small golden lyre"), ("series", "three matching books standing side by side, tied with a ribbon"),
    ("author", "a feather pen writing a wavy line on paper"), ("authors", "three feather pens in a pot"),
    ("rated", "a scroll with one gold star on it"), ("five_stars", "five gold stars in an arc"),
    ("quotes", "a speech bubble with big quotation marks in it"),

    ("notes", "a pencil and a small note page"), ("want", "a tall wobbly pile of books with a ribbon bookmark"),
    ("abandoned", "a closed book with a little white flag stuck in it"), ("formats", "a book wearing headphones"),
    ("own", "a wooden shelf plank with a hanging name tag"), ("library", "a full bookcase"), ("pets", "a paw print"),

    ("grown", "a small paw print next to a big paw print"), ("ancient", "a gold crown with a soft glow"),
    ("forms", "five gems in five colours arranged in a star"), ("traits", "two theatre masks, one happy and one grumpy"),
    ("mana", "a round flask of glowing blue potion"), ("starters", "three different eggs in a nest"),
    ("companion", "two hearts linked together"),

    ("guests", "an envelope with a red wax seal"), ("shiny", "a sparkling white four-pointed star"),
    ("finds", "an open wooden casket with a glint inside"), ("seasonal", "a snowflake, an orange leaf and a daisy together"),
    ("treasure", "a pile of gold coins and trinkets"), ("dreams", "a fluffy dream cloud with a small moon"),
    ("furniture", "a cosy red armchair"),

    ("quests", "a rolled scroll with a guild seal"), ("items", "a green jelly cube with a little key inside it"),
]
PER_SHEET = 7


class Job:
    def __init__(self, name, group, target, ref, prompt):
        self.name, self.group, self.target, self.ref, self.prompt = name, group, target, ref, prompt


def jobs():
    out = []
    for name, what in FAMILIARS.items():
        out.append(Job(name, "familiars", os.path.join(ART, "familiars", name + ".png"), os.path.join(ART, "familiars", "kitsune.png"),
                       STYLE + FAMILIAR + what))
    out.append(Job("eggs_3", "eggs", os.path.join(ART, "eggs", "eggs_3.png"), os.path.join(ART, "eggs", "eggs_1.png"), STYLE + EGGS))
    for name, what in ROOM.items():
        out.append(Job(name, "room", os.path.join(ART, "room", name + ".png"), os.path.join(ART, "room", "armchair.png"), STYLE + PIECE + what))
    for name, (what, day, night) in DENS.items():
        for t, light in (("day", day), ("night", night)):
            out.append(Job(f"room_{name}_{t}", "room", os.path.join(ART, "room", f"room_{name}_{t}.png"), os.path.join(ART, "room", f"room_{t}.png"),
                           "Cosy pixel art in exactly the style of the attached reference picture. " + DEN + what + " " + light))
    for name, what in SHELF.items():
        out.append(Job(name, "shelf", os.path.join(ART, "shelf", name + ".png"), os.path.join(ART, "room", "candles.png"),
                       STYLE + SHELF_PIECE + what))
    sheets = [BADGES[i:i + PER_SHEET] for i in range(0, len(BADGES), PER_SHEET)]
    for n, sheet in enumerate(sheets, start=1):
        head = BADGE if len(sheet) == PER_SHEET else BADGE_LAST
        things = "; ".join(f"{i}) {what}" for i, (_, what) in enumerate(sheet, start=1)) + "."
        out.append(Job(f"badges_{n:02d}", "badges", os.path.join(ART, "badges", f"badges_{n:02d}.png"), os.path.join(ART, "finds", "find_compass.png"),
                       STYLE + head + things))
    return out


def pictures():
    return set(glob.glob(os.path.join(MADE, "**", "*.png"), recursive=True))


def draw(job):
    """Asks Codex for the picture of [job] and copies it to its place in art/. True when it came."""
    # On Windows npm leaves an extensionless shell shim next to codex.cmd: only the .cmd can be started.
    codex = (shutil.which("codex.cmd") if os.name == "nt" else None) or shutil.which("codex")
    if codex is None:
        sys.exit("codex is not on the PATH")
    before = pictures()
    started = time.time()
    ask = ("Use your image generation tool to draw exactly one picture, as described below, and stop. Do not run "
           "commands, do not write files, do not ask anything.\n\n" + job.prompt)
    cmd = [codex, "exec", "-s", "read-only", "--enable", "image_generation", "-i", job.ref]
    try:
        r = subprocess.run(cmd, input=ask, text=True, encoding="utf-8", errors="replace", capture_output=True, cwd=ROOT, timeout=1200)
    except subprocess.TimeoutExpired:
        print("  took too long, given up")
        return False
    log = (r.stdout or "") + (r.stderr or "")
    new = [p for p in pictures() - before if os.path.getmtime(p) >= started - 2]
    # Its own session's pictures first, in case something else is drawing at the same time.
    session = re.search(r"session id:\s*([0-9a-f-]{20,})", log)
    mine = [p for p in new if session and session.group(1) in p] or new
    if not mine:
        print("  no picture came:", (log.strip().splitlines() or ["(nothing said)"])[-1][:200])
        return False
    os.makedirs(os.path.dirname(job.target), exist_ok=True)
    shutil.copyfile(max(mine, key=os.path.getmtime), job.target)
    print("  ->", os.path.relpath(job.target, ROOT))
    return True


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    again, dry = "--again" in sys.argv, "--dry" in sys.argv
    all_jobs = jobs()
    if not args:
        for group in ("familiars", "eggs", "room", "shelf", "badges"):
            mine = [j for j in all_jobs if j.group == group]
            missing = [j.name for j in mine if not os.path.exists(j.target)]
            print(f"{group}: {len(mine) - len(missing)} of {len(mine)} drawn" + (", missing: " + ", ".join(missing) if missing else ""))
        return
    wanted = [j for j in all_jobs if "all" in args or j.group in args or j.name in args]
    unknown = [a for a in args if a != "all" and not any(a in (j.group, j.name) for j in all_jobs)]
    if unknown:
        sys.exit("no such picture or group: " + ", ".join(unknown))
    todo = [j for j in wanted if again or dry or not os.path.exists(j.target)]
    if not todo:
        print("nothing to draw: all of it is there (--again draws over)")
    failed = []
    for i, job in enumerate(todo, start=1):
        print(f"[{i}/{len(todo)}] {job.name}")
        if dry:
            print(job.prompt, "\n  reference:", os.path.relpath(job.ref, ROOT), "\n")
        elif not draw(job):
            failed.append(job.name)
    if dry:
        return
    # Cut whatever is there for the app.
    groups = {j.group for j in todo}
    if groups & {"familiars", "eggs"}:
        subprocess.run([sys.executable, os.path.join(ROOT, "tools", "familiars.py")], cwd=ROOT, check=False)
    if groups & {"room", "shelf", "badges"}:
        subprocess.run([sys.executable, os.path.join(ROOT, "tools", "scenery.py")], cwd=ROOT, check=False)
    if failed:
        sys.exit("not drawn: " + ", ".join(failed) + " (run again to retry)")


if __name__ == "__main__":
    main()
