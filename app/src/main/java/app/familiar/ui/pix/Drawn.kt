package app.familiar.ui.pix

import app.familiar.domain.Form
import app.familiar.domain.Species
import app.familiar.domain.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The hand-drawn familiars: one finished picture per species and stage (cut from the sheets in
 * art/familiars by tools/familiars.py), with the eyes marked so they can still blink, sleep and
 * squint. What a familiar collects on the way (the slime's swallowed things, the ghost's wisps,
 * the moth's stars) is painted over the picture here.
 */
internal object Drawn {
    class Sprite(val c: IntArray, val eyes: List<IntArray>, val lids: BooleanArray?, val fine: IntArray? = null) {
        /** Rows and columns the picture covers. */
        val top: Int; val bottom: Int; val left: Int; val right: Int
        init {
            var t = Sprites.SIZE; var b = 0; var l = Sprites.SIZE; var r = 0
            for (y in 0 until Sprites.SIZE) for (x in 0 until Sprites.SIZE) if (c[y * Sprites.SIZE + x].al() > 0) {
                t = minOf(t, y); b = max(b, y); l = minOf(l, x); r = max(r, x)
            }
            top = t; bottom = b; left = l; right = r
        }
    }

    private val cache = HashMap<String, Sprite>()

    private val faces: Map<String, List<IntArray>> by lazy {
        val text = Drawn::class.java.getResourceAsStream("/familiars/faces.txt")?.bufferedReader()?.use { it.readText() }.orEmpty()
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val p = line.trim().split(Regex("\\s+"))
            val n = p.drop(1).map { it.toInt() }
            p[0] to n.chunked(4).filter { it.size == 4 }.map { it.toIntArray() }
        }
    }

    private fun name(sp: Species, st: Stage) = sp.name.lowercase() + "_" + st.name.lowercase()

    /** The picture for [sp] at [st], or null when there is none (an egg, or a resource gone missing). */
    fun of(sp: Species, st: Stage): Sprite? {
        if (st == Stage.EGG) return null
        val key = name(sp, st)
        return synchronized(cache) {
            // Only a picture that loaded is kept: one that couldn't be read (the app was starting while it was
            // being updated) is tried again next time, rather than leaving the old look until the app restarts.
            cache[key] ?: runCatching {
                    val bmp = Drawn::class.java.getResourceAsStream("/familiars/$key.png")?.use { android.graphics.BitmapFactory.decodeStream(it) }
                        ?: return@runCatching null
                    val n = Sprites.SIZE
                    val c = IntArray(n * n)
                    if (bmp.width == n && bmp.height == n) bmp.getPixels(c, 0, n, 0, 0, n, n)
                    bmp.recycle()
                    // The eyes' exact pixels, painted over when they close.
                    val lids = Drawn::class.java.getResourceAsStream("/familiars/${key}_eyes.png")?.use { android.graphics.BitmapFactory.decodeStream(it) }?.let { m ->
                        val e = IntArray(n * n)
                        if (m.width == n && m.height == n) m.getPixels(e, 0, n, 0, 0, n, n)
                        m.recycle()
                        BooleanArray(n * n) { e[it].al() > 0 }
                    }
                    // The picture DETAIL times finer, which is what shows; the coarse one is what the code reads.
                    val f = n * DETAIL
                    val fine = Drawn::class.java.getResourceAsStream("/familiars/$key@${DETAIL}x.png")?.use { android.graphics.BitmapFactory.decodeStream(it) }?.let { b ->
                        IntArray(f * f).also { a -> if (b.width == f && b.height == f) b.getPixels(a, 0, f, 0, 0, f, f); b.recycle() }
                    }
                    Sprite(c, faces[key].orEmpty(), lids, fine)
                }.onFailure { android.util.Log.w("Drawn", "can't load $key", it) }.getOrNull()?.also { cache[key] = it }
        }
    }

    /** Paints [s] onto [px] for this frame and returns where its face is. */
    fun paint(px: Px, s: Sprite, ctx: Sprites.Ctx): Face {
        val look = ctx.look
        val floats = look.species == Species.MOTH || look.species == Species.GHOST
        val dy = (if (floats) sin(ctx.ph * 2 * PI.toFloat()) * 1.5f - 2f else 0f).roundToInt() + if (look.chew && ctx.f % 2 == 1) 1 else 0
        val eyes = s.eyes.map { intArrayOf(it[0], it[1] + dy, it[2], it[3] + dy) }
        val cx = (s.left + s.right) / 2f
        val headX = if (eyes.size == 2) (eyes[0][0] + eyes[1][2]) / 2 else cx.toInt()
        val headY = if (eyes.isNotEmpty()) eyes.sumOf { it[1] + it[3] } / (2 * eyes.size) else s.top + dy + 12

        if (look.species == Species.GHOST) wisps(px, s, ctx, headY, front = false)
        // What it has earned by reading and wears behind it: the kitsune's tails, the dragon's books.
        if (look.species == Species.KITSUNE) tails(px, s, ctx, dy)
        if (look.species == Species.DRAGON) hoard(px, s, ctx, dy)
        val n = Sprites.SIZE
        val c = colours(s, look.species, look.form, look.shiny)
        val pic = Px(n, n)
        c.copyInto(pic.c)
        val fine = fineColours(s, look.species, look.form, look.shiny)
        if (fine != null) fine.copyInto(pic.fine) else pic.coarsen()
        px.blit(pic, 0, dy)
        // The wisps on the near side of their circle pass in front of the ghost.
        if (look.species == Species.GHOST) wisps(px, s, ctx, headY, front = true)
        when (look.species) {
            Species.SLIME -> swallowed(px, s, ctx, dy, eyes)
            Species.MOTH -> stars(px, s, ctx, dy)
            Species.SPROUT -> blooms(px, s, ctx, dy)
            Species.CHIMERA -> patches(px, s, ctx, dy, eyes)
            Species.MAGPIE -> shinies(px, s, ctx, dy)
            else -> {}
        }
        // The lids of a pair match: the same width and height, at one level unless the face is tilted.
        val lids = if (eyes.size == 2) {
            val (a, b) = eyes
            val w = max(a[2] - a[0], b[2] - b[0])
            val h = max(a[3] - a[1], b[3] - b[1])
            val ca = (a[1] + a[3]) / 2f; val cb = (b[1] + b[3]) / 2f
            val level = if (abs(ca - cb) <= 2f) (ca + cb) / 2f else Float.NaN
            listOf(a, b).map { e ->
                val cx = (e[0] + e[2]) / 2f
                val cy = if (level.isNaN()) (e[1] + e[3]) / 2f else level
                val lx = (cx - w / 2f).roundToInt(); val ly = (cy - h / 2f).roundToInt()
                intArrayOf(lx, ly, lx + w, ly + h)
            }
        } else eyes
        // The tanuki's eyes sit in dark patches: a closed eye is the patch, not the light ring round it.
        val patch = look.species == Species.TANUKI
        lids.forEachIndexed { i, e -> lid(px, e, ctx.eyes, if (lids.size < 2) 0 else if (i == 0) -1 else 1, s.lids, dy, eyes[i], patch, look.species == Species.OWLBEAR || look.species == Species.WYRM) }

        // The top of the head, above the eyes: hats and hearts go from there.
        var top = s.top + dy
        for (y in 0 until n) if (px.has(headX, y)) { top = y; break }
        // How big the head is, from how far apart the eyes are: a hat sits on the head itself, not on the
        // tip of a flame, a crown or antennae drawn above it.
        val sep = if (eyes.size == 2) (eyes[1][0] + eyes[1][2] - eyes[0][0] - eyes[0][2]) / 2 else 10
        val headR = max(6, minOf(headY - top, (sep * 1.05f).roundToInt()))
        val pts = eyes.map { ((it[0] + it[2]) / 2) to ((it[1] + it[3]) / 2) }
        val w = eyes.maxOfOrNull { it[2] - it[0] } ?: 5
        return Face(pts, w, headX, headY, headR, drawn = true)
    }

    /**
     * Closes, squints or shuts an eye ([b] is x0, y0, x1, y1): the skin is painted back over it, shaded
     * the way the face around it is, then the lid is drawn: a curve thick in the middle and thin at the
     * ends, a soft crease beside it and lashes at the outer corner ([outer] -1 for the left eye, 1 for the right).
     */
    private fun lid(px: Px, b: IntArray, state: EyeState, outer: Int, mask: BooleanArray?, dy: Int, real: IntArray = b, patch: Boolean = false, glasses: Boolean = false) {
        if (state == EyeState.OPEN || state == EyeState.SCARED) return
        val (x0, y0, x1, y1) = b.toList()
        val w = x1 - x0
        val h = y1 - y0
        val ex = (x0 + x1 - 1) / 2f
        val ey = (y0 + y1 - 1) / 2f
        val around = skin(px, b)

        // Only the eye's own pixels (marked by tools/familiars.py) are painted over, each filled from its
        // painted neighbours from the edge inward, so the face's own shading carries across and nothing else changes.
        val bottom = if (state == EyeState.SQUINT) ey.toInt() else max(y1, real[3]) + 1
        val gx0 = minOf(x0, real[0]) - 2; val gy0 = minOf(y0, real[1]) - 2
        val gw = max(x1, real[2]) + 2 - gx0; val gh = bottom - gy0 + 1
        val hole = BooleanArray(gw * gh)
        val n = Sprites.SIZE
        for (y in gy0..bottom) for (x in gx0 until gx0 + gw) {
            if (!px.has(x, y)) continue
            val sy = y - dy
            val eye = if (mask != null) x in 0 until n && sy in 0 until n && mask[sy * n + x]
                else x in x0 until x1 && y in y0 until y1 && dist(px[x, y], around) > 48
            if (eye) hole[(y - gy0) * gw + (x - gx0)] = true
        }
        // The skin is what touches the eye itself, on its upper half, where a lid comes down from: the
        // kitsune's orange rather than the cream muzzle under its eyes, the tanuki's dark patch.
        val skin = run {
            val count = HashMap<Int, Int>()
            val real = HashMap<Int, Int>()
            for (y in gy0 until gy0 + gh) for (x in gx0 until gx0 + gw) {
                if (!hole[(y - gy0) * gw + (x - gx0)]) continue
                for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) {
                    val inGrid = xx in gx0 until gx0 + gw && yy in gy0 until gy0 + gh
                    if (inGrid && hole[(yy - gy0) * gw + (xx - gx0)]) continue
                    if (yy > ey && !patch) continue
                    val c = px[xx, yy]
                    if (c.al() == 0 || lum(c) < 0.13f || pink(c) || (glasses && gold(c))) continue
                    if (patch && lum(c) > 0.42f) continue
                    val q = rgb(c.r() / 16 * 16, c.g() / 16 * 16, c.b() / 16 * 16)
                    count[q] = (count[q] ?: 0) + 1
                    real[q] = c or (0xFF shl 24)
                }
            }
            count.maxByOrNull { it.value }?.key?.let { real[it] } ?: around
        }
        val dark = lum(skin) < 0.3f
        // The lid is drawn in a deep shade of the familiar's own colour, the way its picture is lined,
        // not in a plum black from elsewhere; on a dark face (the magpie, the tanuki's patch) in a light one, or it wouldn't show.
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(skin.r(), skin.g(), skin.b(), hsv)
        val ink = if (dark) mixc(skin, WHITE, 0.58f) else android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 1.2f + 0.25f).coerceAtMost(1f), 0.2f))
        val skinL = lum(skin)
        val skinS = (maxOf(skin.r(), skin.g(), skin.b()) - minOf(skin.r(), skin.g(), skin.b())) / 255f
        fun known(x: Int, y: Int): Boolean {
            val i = (y - gy0) * gw + (x - gx0)
            if (x in gx0 until gx0 + gw && y in gy0 until gy0 + gh && hole[i]) return false
            val c = px[x, y]
            if (c.al() == 0 || pink(c) || (glasses && gold(c))) return false
            if (dark) return lum(c) < skinL + 0.25f
            // Only the face's own tones feed the fill: not a dark rim under the eye, not a grey lash.
            if (lum(c) < skinL - 0.15f) return false
            val cs = (maxOf(c.r(), c.g(), c.b()) - minOf(c.r(), c.g(), c.b())) / 255f
            return !(cs < 0.1f && skinS > 0.3f)
        }
        var left = hole.count { it }
        var pass = 0
        while (left > 0 && pass < 24) {
            pass++
            val fill = ArrayList<Triple<Int, Int, Int>>()
            for (y in gy0 until gy0 + gh) for (x in gx0 until gx0 + gw) {
                if (!hole[(y - gy0) * gw + (x - gx0)]) continue
                var r = 0; var g = 0; var bl = 0; var n = 0
                for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) {
                    if ((xx == x && yy == y) || !known(xx, yy)) continue
                    val c = px[xx, yy]; r += c.r(); g += c.g(); bl += c.b(); n++
                }
                if (n >= (if (pass < 6) 3 else 1)) fill += Triple(x, y, rgb(r / n, g / n, bl / n))
            }
            for ((x, y, c) in fill) { px.set(x, y, c); hole[(y - gy0) * gw + (x - gx0)] = false }
            left -= fill.size
        }
        for (y in gy0 until gy0 + gh) for (x in gx0 until gx0 + gw) if (hole[(y - gy0) * gw + (x - gx0)]) px.set(x, y, skin)

        // The lid is a little shorter than the eye, centred on it: a line as wide as the whole eye reads as
        // a bar across the face (the griffin's, the owlbear's behind its glasses).
        val sw = if (w >= 6) (w * 0.8f).roundToInt().coerceAtLeast(4) else w
        val lx = x0 + (w - sw) / 2
        val big = sw >= 7
        val mid = ey.roundToInt()
        // The lid: y of the line at each column, and how thick it is there.
        // One unbroken pixel line, the way an arc is drawn in pixel art: flat in the middle, its ends turned
        // up (asleep) or down (stroked) by a single row, so it never breaks into separate dashes.
        val ends = if (sw >= 9) 2 else 1
        fun curve(i: Int): Int {
            val edge = minOf(i, sw - 1 - i) < ends
            return when (state) {
                EyeState.CLOSED -> if (edge) mid else mid + 1
                EyeState.HAPPY -> if (edge) mid + 1 else mid
                else -> mid
            }
        }
        for (i in 0 until sw) {
            val y = curve(i)
            // Softer at the very ends, the way the picture's own lines fade.
            val a = if (i == 0 || i == sw - 1) 190 else 255
            px.set(lx + i, y, ink, a)
            if (state == EyeState.SQUINT && big) px.set(lx + i, y + 1, ink, a)
        }
    }

    /**
     * A recolouring for the rare one: one or two hue bands of the body's own colours (from, to degrees) are
     * moved to a new hue, with saturation and brightness scaled. Everything else stays as drawn: the eyes,
     * blush, mouth and tongue, the outline, white and black, and what it carries (the magpie's nest).
     */
    private class Band(val from: Float, val to: Float, val hue: Float, val sat: Float = 1f, val value: Float = 1f, val pale: Boolean = false)

    private val palettes: Map<Species, List<Band>> = mapOf(
        // Ebony wood with silver bands.
        Species.MIMIC to listOf(Band(8f, 36f, 280f, 0.5f, 0.85f), Band(36f, 58f, 220f, 0.12f, 1.08f)),
        // A moonlit fox: lavender and silver.
        Species.KITSUNE to listOf(Band(12f, 42f, 255f, 0.42f, 1.06f)),
        // Strawberry jelly.
        Species.SLIME to listOf(Band(60f, 150f, 335f, 0.75f, 1.02f)),
        // A ruby dragon with golden wings.
        Species.DRAGON to listOf(Band(95f, 200f, 352f, 0.85f, 0.95f), Band(250f, 310f, 40f, 0.9f, 1.05f)),
        // An ice phoenix.
        Species.PHOENIX to listOf(Band(0f, 58f, 200f, 0.8f, 1.02f), Band(325f, 360f, 215f, 0.8f, 1.0f)),
        // A night griffin: slate fur, blue-black wings.
        Species.GRIFFIN to listOf(Band(15f, 50f, 225f, 0.55f, 0.92f)),
        // A snowy owlbear with frost-blue fur.
        Species.OWLBEAR to listOf(Band(12f, 45f, 205f, 0.4f, 1.08f)),
        // A luna moth: mint wings.
        Species.MOTH to listOf(Band(215f, 300f, 158f, 0.75f, 1.08f)),
        // A silver tanuki.
        Species.TANUKI to listOf(Band(12f, 48f, 218f, 0.5f, 1.0f)),
        // A cherry-blossom sprout: pink leaves, a lilac root.
        Species.SPROUT to listOf(Band(60f, 150f, 340f, 0.7f, 1.05f), Band(20f, 52f, 290f, 0.45f, 1.0f)),
        // A mint ghost: its pale sheet takes the tint too.
        Species.GHOST to listOf(Band(200f, 320f, 165f, 1.3f, 1.0f, pale = true)),
        // A rose bookworm.
        Species.WYRM to listOf(Band(55f, 120f, 335f, 0.8f, 1.03f)),
        // A frost chimera: icy fur, teal wings.
        Species.CHIMERA to listOf(Band(18f, 52f, 200f, 0.5f, 1.05f), Band(270f, 340f, 175f, 0.85f, 1.0f)),
        // An amethyst magpie with a golden sheen; the nest stays.
        Species.MAGPIE to listOf(Band(170f, 260f, 285f, 1.1f, 1.05f)),
    )

    /** How many of a species' bands are its body (the rest are wings, bands, leaves): what a form recolours. */
    private fun body(sp: Species): Int = if (sp == Species.PHOENIX || sp == Species.SPROUT) 2 else 1

    /** A form's own shade (hue, saturation and brightness scaled), laid over the body's colours. */
    private class Tone(val hue: Float, val sat: Float, val value: Float)

    private val tones: Map<Form, Tone> = mapOf(
        // Starry indigo, for fantasy and science fiction.
        Form.ASTRAL to Tone(230f, 0.75f, 0.98f),
        // Dusky violet, for mysteries and horror.
        Form.SHADOW to Tone(272f, 0.5f, 0.78f),
        // Rose, for romance and poetry.
        Form.BLOOM to Tone(340f, 0.65f, 1.05f),
        // Old gold, like a gilt page, for the classics, history and non-fiction.
        Form.SAGE to Tone(40f, 0.8f, 1.02f),
        // Leaf green, for adventure.
        Form.WILD to Tone(108f, 0.6f, 0.95f),
    )

    private val recolours = HashMap<Triple<Sprite, Species, Pair<Form, Boolean>>, IntArray>()

    /** The picture's colours for this look: as drawn, in its rare colouring, or in its grown form's shade. */
    fun colours(s: Sprite, sp: Species, form: Form, shiny: Boolean): IntArray {
        if (form == Form.NONE && !shiny) return s.c
        return synchronized(recolours) {
            recolours.getOrPut(Triple(s, sp, form to shiny)) { recolour(s.c, 1, s, sp, bands(sp, form, shiny)) }
        }
    }

    /** The fine picture in the same colours as [colours], or null when there is no fine one. */
    private fun fineColours(s: Sprite, sp: Species, form: Form, shiny: Boolean): IntArray? {
        val fine = s.fine ?: return null
        if (form == Form.NONE && !shiny) return fine
        return synchronized(fineRecolours) {
            fineRecolours.getOrPut(Triple(s, sp, form to shiny)) { recolour(fine, DETAIL, s, sp, bands(sp, form, shiny)) }
        }
    }

    private val fineRecolours = HashMap<Triple<Sprite, Species, Pair<Form, Boolean>>, IntArray>()

    private fun bands(sp: Species, form: Form, shiny: Boolean): List<Band> {
        val own = palettes[sp].orEmpty()
        val tone = tones[form] ?: return own
        return own.take(body(sp)).map { b ->
            // A rare one in a form is a lighter, warmer turn of the same shade.
            val hue = if (shiny) (tone.hue + 28f) % 360f else tone.hue
            Band(b.from, b.to, hue, if (b.pale) tone.sat + 0.6f else tone.sat, tone.value * if (shiny) 1.1f else 1f, b.pale)
        }
    }

    /**
     * Moves the given hue bands of the picture to their new hue, scaling saturation and brightness. Everything
     * else stays as drawn: the eyes, blush, mouth and tongue, the outline, white and black, and what it carries.
     */
    private fun recolour(src: IntArray, k: Int, s: Sprite, sp: Species, bands: List<Band>): IntArray {
        val hsv = FloatArray(3)
        val n = Sprites.SIZE
        return IntArray(src.size) { i ->
            val p = src[i]
            // [src] is [k] times finer than the sprite: an eye's pixel there is under an eye's pixel here.
            val lid = s.lids?.get((i / (n * k)) / k * n + (i % (n * k)) / k) == true
            // Blush is a light pink (plenty of green in it); a pure red, the phoenix's flame, isn't.
            if (p.al() == 0 || lid || (pink(p) && p.g() > 100)) return@IntArray p
            android.graphics.Color.RGBToHSV(p.r(), p.g(), p.b(), hsv)
            // Outline, deep shadow, the dark of a mouth.
            if (hsv[2] < 0.22f) return@IntArray p
            // A red mouth or tongue: much more red than blue, on a face that isn't red itself.
            if (sp != Species.PHOENIX && (hsv[0] > 340f || hsv[0] < 8f) && hsv[1] > 0.35f) return@IntArray p
            val band = bands.firstOrNull { b -> hsv[0] >= b.from && hsv[0] < b.to && (hsv[1] >= 0.12f || b.pale) } ?: return@IntArray p
            // Near-white stays white unless the whole body is pale (the ghost).
            if (!band.pale && hsv[1] < 0.18f && hsv[2] > 0.85f) return@IntArray p
            hsv[0] = band.hue
            hsv[1] = if (band.pale) (hsv[1] * band.sat + 0.08f).coerceIn(0f, 1f) else (hsv[1] * band.sat).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * band.value).coerceIn(0f, 1f)
            (p.al() shl 24) or (android.graphics.Color.HSVToColor(hsv) and 0xFFFFFF)
        }
    }

    /** Spectacle gold (the owlbear's and the bookworm's rims): never the skin a closed eye is filled with. */
    private fun gold(c: Int) = c.r() > 140 && c.g() > 90 && c.b() < 110 && c.r() - c.b() > 70 && c.r() - c.g() < 90

    private fun pink(c: Int) = c.r() > 170 && c.r() - c.g() > 50 && c.b() >= c.g() - 12

    private fun dist(a: Int, b: Int) = abs(a.r() - b.r()) + abs(a.g() - b.g()) + abs(a.b() - b.b())

    /** The colour of the skin around an eye: the commonest colour in a ring just outside it. */
    private fun skin(px: Px, b: IntArray): Int {
        val count = HashMap<Int, Int>()
        var fallback = 0
        for (y in b[1] - 2..b[3] + 1) for (x in b[0] - 2..b[2] + 1) {
            val inside = x in b[0] until b[2] && y in b[1] until b[3]
            if (inside) continue
            val c = px[x, y]
            if (c.al() == 0) continue
            fallback = c
            if (lum(c) < 0.16f) continue
            // Quantise so near tones count together.
            val q = rgb(c.r() / 12 * 12, c.g() / 12 * 12, c.b() / 12 * 12)
            count[q] = (count[q] ?: 0) + 1
        }
        val best = count.maxByOrNull { it.value }?.key ?: return if (fallback != 0) fallback else R.cream[3]
        // Back to a real colour from the ring with that tone.
        for (y in b[1] - 2..b[3] + 1) for (x in b[0] - 2..b[2] + 1) {
            val c = px[x, y]
            if (c.al() > 0 && rgb(c.r() / 12 * 12, c.g() / 12 * 12, c.b() / 12 * 12) == best) return c or (0xFF shl 24)
        }
        return best
    }

    /**
     * The slime: what it swallowed floats in the jelly below its face. Each thing goes whole where it fits
     * (inside the jelly, off the outline, the mouth and the cheeks, clear of the others), never cut off.
     */
    private fun swallowed(px: Px, s: Sprite, ctx: Sprites.Ctx, dy: Int, eyes: List<IntArray>) {
        val items = ctx.look.items
        if (items.isEmpty()) return
        val below = (eyes.maxOfOrNull { it[3] } ?: (s.top + dy)) + 2
        val tint = px[(s.left + s.right) / 2, s.bottom + dy - 3]
        // Jelly: not the outline, not the mouth or a cheek, not the dark of an eye.
        fun jelly(x: Int, y: Int): Boolean {
            // Judged on the picture as drawn, so the rare (pink) slime finds its jelly too.
            val sy = y - dy
            if (x !in 0 until Sprites.SIZE || sy !in 0 until Sprites.SIZE || px[x, y].al() == 0) return false
            val c = s.c[sy * Sprites.SIZE + x]
            return c.al() > 0 && lum(c) >= 0.25f && !pink(c) && c.r() <= c.g() + 30
        }
        val taken = BooleanArray(Sprites.SIZE * Sprites.SIZE)
        // Places to try: lower and nearer the middle first.
        val cx = (s.left + s.right) / 2
        val spots = ArrayList<Pair<Int, Int>>()
        for (y in s.bottom + dy - 5 downTo below + 4) for (x in s.left + 4..s.right - 4) spots += x to y
        spots.sortBy { (x, y) -> abs(x - cx) * 2 - (y - below) }
        items.take(7).forEachIndexed { i, item ->
            val g = Px(9, 9)
            Glyphs.draw(g, Glyph.of(item), 1, 1)
            val pts = (0 until 81).filter { g.c[it].al() > 0 }.map { it % 9 to it / 9 }
            if (pts.isEmpty()) return@forEachIndexed
            val bob = if ((ctx.f + i * 3) % 12 < 6) 0 else 1
            val spot = spots.firstOrNull { (x, y) ->
                pts.all { (gx, gy) ->
                    val tx = x - 4 + gx; val ty = y - 4 + gy
                    ty >= below && jelly(tx, ty) && jelly(tx - 1, ty) && jelly(tx + 1, ty) && jelly(tx, ty + 1) && jelly(tx, ty - 1) && jelly(tx, ty + 2) &&
                        tx in 0 until Sprites.SIZE && ty in 0 until Sprites.SIZE && !taken[ty * Sprites.SIZE + tx]
                }
            } ?: return@forEachIndexed
            for ((gx, gy) in pts) {
                val tx = spot.first - 4 + gx; val ty = spot.second - 4 + gy
                // A little room around each, so the next doesn't touch it.
                for (yy in ty - 1..ty + 1) for (xx in tx - 1..tx + 1) if (xx in 0 until Sprites.SIZE && yy in 0 until Sprites.SIZE) taken[yy * Sprites.SIZE + xx] = true
            }
            for ((gx, gy) in pts) px.set(spot.first - 4 + gx, spot.second - 4 + gy + bob, mixc(g[gx, gy], if (tint.al() > 0) tint else R.slime[3], 0.3f))
        }
    }

    /** The moth: a star glints on its wings for every night of reading (the wings are drawn plain). */
    private fun stars(px: Px, s: Sprite, ctx: Sprites.Ctx, dy: Int) {
        val extra = ctx.look.stars.coerceIn(0, 30)
        val hsv = FloatArray(3)
        var placed = 0
        var k = 0
        while (placed < extra && k < extra * 12) {
            val x = (s.left + (noise(k, 1, 91) + 0.5f) * (s.right - s.left)).toInt()
            val y = (s.top + (noise(k, 2, 91) + 0.5f) * (s.bottom - s.top)).toInt() + dy
            k++
            val c = px[x, y]
            if (c.al() == 0) continue
            android.graphics.Color.RGBToHSV(c.r(), c.g(), c.b(), hsv)
            if (hsv[0] !in 225f..300f || hsv[2] > 0.8f || hsv[2] < 0.18f || hsv[1] < 0.2f) continue
            val on = (placed + ctx.f) % 7 != 0
            px.set(x, y, if (on) R.gold[4] else WHITE)
            placed++
        }
    }

    /** The ghost: a wisp for every horror or mystery book read, circling it. */
    private fun wisps(px: Px, s: Sprite, ctx: Sprites.Ctx, headY: Int, front: Boolean) {
        val n = ctx.look.wisps.coerceAtMost(6)
        val cx = (s.left + s.right) / 2f
        val rx = (s.right - s.left) / 2f + 5f
        for (i in 0 until n) {
            val a = (ctx.ph * 360f + i * 360f / n) * PI / 180
            // Lower on the circle is nearer the viewer: in front of the ghost; the rest behind it.
            if ((sin(a) > 0) != front) continue
            val fx = cx + cos(a).toFloat() * rx
            val fy = headY + 4f + sin(a).toFloat() * 5f
            px.glow(fx, fy, 4f, R.sky[4], 100)
            val fl = poly(fx, fy - 3.5f, fx + 1.8f, fy, fx + 0.8f, fy + 1.6f, fx - 0.8f, fy + 1.6f, fx - 1.8f, fy)
            px.shade(fl, facing(), R.sky, outline = false, lo = 3)
        }
    }

    /**
     * The kitsune: a tail for every genre it has read, each in that genre's colour, fanned out behind it
     * (one, in its own orange, before any).
     */
    private fun tails(px: Px, s: Sprite, ctx: Sprites.Ctx, dy: Int) {
        val tail = Painted.of("familiars/kitsune_tail") ?: return
        val colours = ctx.look.tails.take(9)
        val n = maxOf(1, colours.size)
        val h = (s.bottom - s.top).toFloat()
        // Long enough to show round it, short enough that a fan of nine stays on the picture.
        val k = h * (if (n > 5) 0.58f else 0.64f) / tail.h
        val bx = (s.left + s.right) / 2f + 0.5f
        val by = s.bottom + dy + 1f - h * 0.24f
        // The outer ones first, so the middle tails lie over them.
        for (i in (0 until n).sortedByDescending { abs(it - (n - 1) / 2f) }) {
            val spread = minOf(68f, 26f + 7f * n)
            val ang = if (n == 1) 52f else -spread + 2 * spread * i / (n - 1)
            val sway = sin(ctx.ph * 2 * PI.toFloat() + i * 0.8f) * 4f
            part(px, tail, bx, by, (ang + sway) * PI.toFloat() / 180f, k, colours.getOrNull(i), ORANGE)
        }
    }

    /**
     * Lays the drawn part [l] (art/parts) on both grids: its bottom middle at [bx], [by], turned by [a] radians
     * clockwise from upright and scaled by [k]. Its own colour (the hues [own] allows) takes the hue of [colour]
     * (null leaves it as drawn); the rest (gold bands, a yellow heart, a cream tip, the outline) stays.
     */
    private fun part(px: Px, l: Painted.Layer, bx: Float, by: Float, a: Float, k: Float, colour: Int?, own: (Float) -> Boolean = RED) {
        val ca = cos(a); val sa = sin(a)
        val hsv = FloatArray(3)
        var hue = 0f; var sat = 0f; var value = 0f
        if (colour != null) {
            android.graphics.Color.RGBToHSV(colour.r(), colour.g(), colour.b(), hsv)
            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        }
        val cache = HashMap<Int, Int>()
        fun tint(c: Int): Int = if (colour == null) c else cache.getOrPut(c) {
            android.graphics.Color.RGBToHSV(c.r(), c.g(), c.b(), hsv)
            if (hsv[1] < 0.3f || hsv[2] < 0.3f || !own(hsv[0])) return@getOrPut c
            hsv[0] = hue
            hsv[1] = (hsv[1] * (0.25f + sat * 0.85f)).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * (0.5f + value * 0.55f)).coerceIn(0f, 1f)
            (c.al() shl 24) or (android.graphics.Color.HSVToColor(hsv) and 0xFFFFFF)
        }
        val reach = maxOf(l.w, l.h) * k + 2
        for (d in listOf(1, DETAIL)) {
            val w = px.w * d; val hh = px.h * d
            val src = if (d == 1) l.px.c else l.px.fine
            val sw = l.px.w * d; val sh = l.px.h * d
            val dst = if (d == 1) px.c else px.fine
            // The part's own bottom middle, on this grid.
            val ox = (l.left + l.right + 1) / 2f * d; val oy = (l.bottom + 1f) * d
            val x0 = ((bx - reach) * d).toInt().coerceAtLeast(0); val x1 = ((bx + reach) * d).toInt().coerceAtMost(w - 1)
            val y0 = ((by - reach) * d).toInt().coerceAtLeast(0); val y1 = ((by + reach) * d).toInt().coerceAtMost(hh - 1)
            for (y in y0..y1) for (x in x0..x1) {
                val ux = (x + 0.5f) / d - bx; val uy = (y + 0.5f) / d - by
                // Back into the upright part.
                val sx = kotlin.math.floor((ux * ca + uy * sa) / k * d + ox).toInt()
                val sy = kotlin.math.floor((-ux * sa + uy * ca) / k * d + oy).toInt()
                if (sx !in 0 until sw || sy !in 0 until sh) continue
                val c = src[sy * sw + sx]
                if (c.al() < 128) continue
                dst[y * w + x] = tint(c) or (0xFF shl 24)
            }
        }
    }

    /** [part] standing upright with its middle at [cx], [cy], [size] pixels across at its widest. */
    private fun partAt(px: Px, l: Painted.Layer, cx: Float, cy: Float, size: Float, colour: Int?, own: (Float) -> Boolean = RED) {
        val k = size / maxOf(l.w, l.h)
        part(px, l, cx, cy + l.h * k / 2f, 0f, k, colour, own)
    }

    /** The hues the parts are drawn in, which take a genre's colour: red (book, patch, gem), pink petals, orange fur. */
    private val RED: (Float) -> Boolean = { it < 20f || it > 335f }
    private val PINK: (Float) -> Boolean = { it > 290f || it < 15f }
    private val ORANGE: (Float) -> Boolean = { it in 5f..45f }

    /** The dragon: the books it has read, standing on end on either side of it, in their genres' colours. */
    private fun hoard(px: Px, s: Sprite, ctx: Sprites.Ctx, dy: Int) {
        val books = ctx.look.hoard.take(16)
        val book = Painted.of("familiars/part_book") ?: return
        if (books.isEmpty()) return
        // How wide the dragon sits at its feet.
        val fy = s.bottom - 2
        var l = s.right; var r = s.left
        for (x in s.left..s.right) if (s.c[fy * Sprites.SIZE + x].al() > 0) { l = minOf(l, x); r = maxOf(r, x) }
        val h0 = (s.bottom - s.top) * 0.3f
        books.forEachIndexed { i, colour ->
            val left = i % 2 == 0
            val j = i / 2
            val h = h0 * (0.85f + (noise(i, 2, 21) + 0.5f) * 0.3f)
            val k = h / book.h
            val w = book.w * k
            val x = if (left) l + 2 - (j + 0.5f) * (w + 0.5f) else r - 1 + (j + 0.5f) * (w + 0.5f)
            if (x - w / 2 < 0 || x + w / 2 > Sprites.SIZE) return@forEachIndexed
            // Leaning a touch, now one way, now the other.
            val lean = (noise(i, 5, 21) * 0.25f)
            part(px, book, x, s.bottom + dy + 1f, lean, k, colour)
        }
    }

    /** The sprout: a flower for every morning of reading, along the top of its leaves. */
    private fun blooms(px: Px, s: Sprite, ctx: Sprites.Ctx, dy: Int) {
        val n = ctx.look.blooms.coerceAtMost(9)
        val flower = Painted.of("familiars/part_flower") ?: return
        if (n == 0) return
        val m = Sprites.SIZE
        fun leaf(c: Int) = c.al() > 0 && c.g() > c.r() + 12 && c.g() > c.b() + 20
        // The top edge of the leaves, column by column.
        val tops = (s.left..s.right).mapNotNull { x ->
            (s.top..s.bottom).firstOrNull { y -> leaf(s.c[y * m + x]) }?.let { y -> x to y }
        }
        if (tops.size < 3) return
        val petals = listOf(null, WHITE, R.lilac[4], R.pink[2], R.gold[4])
        val size = ((s.bottom - s.top) * 0.13f).coerceIn(5f, 8f)
        for (i in 0 until n) {
            val t = if (n == 1) 0.5f else 0.15f + 0.7f * i / (n - 1)
            val (x, top) = tops[(t * (tops.size - 1)).roundToInt()]
            partAt(px, flower, x + 0.5f, top + size * 0.35f + (i % 2) * 2 + dy, size, petals[i % petals.size], PINK)
        }
    }

    /** The chimera: a patch sewn on for every genre of your own. */
    private fun patches(px: Px, s: Sprite, ctx: Sprites.Ctx, dy: Int, eyes: List<IntArray>) {
        val colours = ctx.look.patches.take(6)
        val patch = Painted.of("familiars/part_patch") ?: return
        if (colours.isEmpty()) return
        val m = Sprites.SIZE
        // Below the face: past the eyes, the cheeks and the mouth.
        val below = (eyes.maxOfOrNull { it[3] } ?: s.top) + 3 + (s.bottom - s.top) / 9
        // Its pale fur, not the outline, horns, wings or tail.
        fun fur(x: Int, y: Int): Boolean {
            if (x !in 0 until m || y !in 0 until m) return false
            val c = s.c[y * m + x]
            return c.al() > 0 && lum(c) in 0.45f..0.97f && c.r() > c.b() + 15 && !pink(c)
        }
        val cx = (s.left + s.right) / 2f
        val taken = ArrayList<Pair<Int, Int>>()
        val r = if (s.bottom - s.top > 50) 4 else 3
        var k = 0
        for (colour in colours) {
            var spot: Pair<Int, Int>? = null
            while (spot == null && k < 400) {
                val x = (cx + noise(k, 1, 77) * (s.right - s.left) * 0.7f).roundToInt()
                val y = (below + (noise(k, 2, 77) + 0.5f) * (s.bottom - below)).roundToInt()
                k++
                val clear = taken.all { (tx, ty) -> abs(tx - x) > r * 2 || abs(ty - y) > r * 2 }
                // Mostly on fur: a hair of mane or a shadow in it doesn't matter.
                var on = 0; var all = 0
                for (ey in -r..r) for (ex in -r..r) if (ex * ex + ey * ey <= r * r) { all++; if (fur(x + ex, y + ey)) on++ }
                val inside = on >= all * 0.85f
                if (clear && inside) spot = x to y
            }
            val (x, y) = spot ?: break
            taken += spot
            partAt(px, patch, x + 0.5f, y + 0.5f + dy, r * 2f + 1.5f, colour)
        }
    }

    /** The magpie: a shiny thing on the rim of its nest for every subgenre read. */
    private fun shinies(px: Px, s: Sprite, ctx: Sprites.Ctx, dy: Int) {
        val colours = ctx.look.patches.take(8)
        val gem = Painted.of("familiars/part_gem") ?: return
        if (colours.isEmpty()) return
        val m = Sprites.SIZE
        val hsv = FloatArray(3)
        fun twig(c: Int): Boolean {
            if (c.al() == 0) return false
            android.graphics.Color.RGBToHSV(c.r(), c.g(), c.b(), hsv)
            return hsv[0] in 15f..45f && hsv[1] > 0.35f && hsv[2] > 0.3f
        }
        // The nest is the woven part at the bottom: its top edge, column by column.
        val low = s.bottom - (s.bottom - s.top) / 3
        val rim = (s.left..s.right).mapNotNull { x ->
            (low - 4..s.bottom).firstOrNull { y -> y in 0 until m && twig(s.c[y * m + x]) }?.let { y -> x to y }
        }
        if (rim.size < 6) return
        val size = ((s.bottom - s.top) * 0.09f).coerceIn(4f, 6f)
        colours.forEachIndexed { i, colour ->
            // Out from the middle, one side then the other.
            val t = 0.5f + (if (i % 2 == 0) -1 else 1) * (0.12f + 0.1f * (i / 2))
            val (x, y0) = rim[(t.coerceIn(0.05f, 0.95f) * (rim.size - 1)).roundToInt()]
            val y = y0 + dy + 1
            partAt(px, gem, x + 0.5f, y.toFloat(), size, colour)
            if ((ctx.f + i * 3) % Sprites.BEATS == 0) Sprites.stamp(px, Sprites.SPARK, x - 2, y - 7, WHITE, WHITE, 200)
        }
    }

    /**
     * Paints the drawn egg of the familiar in [ctx] (art/eggs, cut by tools/familiars.py): it rocks once it is
     * more than half hatched, cracks open across its middle as it fills, and glows just before it hatches.
     * False when there is no drawn egg, so the old one is painted instead.
     */
    fun egg(px: Px, ctx: Sprites.Ctx): Boolean {
        val l = Painted.of("familiars/egg_" + ctx.look.species.name.lowercase()) ?: return false
        val h = ctx.look.hatch
        val wob = if (h > 0.55f && ctx.f % 6 < 2) (if (ctx.f % 2 == 0) -1 else 1) else 0
        px.blit(l.px, wob, 0)
        if (h > 0.25f) {
            // A zigzag crack round the middle, longer the closer it is to hatching.
            val pts = listOf(0f to 0.48f, 0.14f to 0.4f, 0.28f to 0.52f, 0.43f to 0.4f, 0.57f to 0.54f, 0.72f to 0.42f, 0.86f to 0.5f, 1f to 0.44f)
            val n = (2 + (h - 0.25f) / 0.75f * (pts.size - 2)).toInt().coerceIn(2, pts.size)
            val shell = Px(px.w, px.h)
            for (i in 0 until n - 1) {
                val (ax, ay) = pts[i]; val (bx, by) = pts[i + 1]
                shell.line(l.left + wob + (ax * l.w).toInt(), l.top + (ay * l.h).toInt(), l.left + wob + (bx * l.w).toInt(), l.top + (by * l.h).toInt(), OUT)
            }
            // Only over the shell, not out past its edge.
            for (i in shell.c.indices) if (shell.c[i].al() > 0 && px.c[i].al() > 0) px.put(i, OUT)
        }
        if (h > 0.8f) px.glow(px.w / 2f, l.top + l.h * 0.5f, l.w * 0.8f, rgb(255, 236, 170), 90)
        return true
    }
}
