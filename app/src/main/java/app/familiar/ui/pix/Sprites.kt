package app.familiar.ui.pix

import app.familiar.domain.Form
import app.familiar.domain.Mood
import app.familiar.domain.Season
import app.familiar.domain.Species
import app.familiar.domain.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything that shapes how a familiar looks right now. */
data class Look(
    val species: Species,
    val stage: Stage,
    /** 0 awake .. 1 a statue (the phoenix: a heap of ash). */
    val stone: Float = 0f,
    val mood: Mood = Mood.NONE,
    /** Kitsune: one colour (ARGB) per tail. */
    val tails: List<Int> = emptyList(),
    /** Dragon: the colours of the books in the hoard. */
    val hoard: List<Int> = emptyList(),
    /** Moth: stars on the wings, one per night of reading. */
    val stars: Int = 0,
    /** Slime: things it swallowed. */
    val items: List<String> = emptyList(),
    val sleeping: Boolean = false,
    /** Eating right now. */
    val chew: Boolean = false,
    /** Egg: 0 just laid .. 1 about to hatch. */
    val hatch: Float = 0f,
    /** The rare colouring. */
    val shiny: Boolean = false,
    /** Being stroked: eyes squeezed shut with pleasure. */
    val happy: Boolean = false,
    /** Sprout: a flower for every morning of reading. */
    val blooms: Int = 0,
    /** Ghost: a wisp for every horror or mystery book finished. */
    val wisps: Int = 0,
    /** Chimera and magpie: one colour (ARGB) for every own genre or subgenre with a book finished. */
    val patches: List<Int> = emptyList(),
    /** The form taken when grown. */
    val form: Form = Form.NONE,
    /** Days of the year when it dresses up. */
    val season: Season = Season.NONE,
)

enum class EyeState { OPEN, BLINK, CLOSED, SCARED, SQUINT, HAPPY }

/** Where the face is, in sprite pixels, for mood marks drawn on top. */
class Face(val eyes: List<Pair<Int, Int>>, val eyeW: Int, val headX: Int, val headY: Int, val headR: Int)

object Sprites {
    const val SIZE = 80
    /** Beats in the idle loop: blinks, sparkles and other things that switch on and off keep to these. */
    const val BEATS = 12
    /** Frames painted for it: twice the beats, so whatever sways, flaps or floats moves in finer steps. */
    const val FRAMES = BEATS * 2
    /** Frames to a beat. */
    const val STEP = FRAMES / BEATS

    fun stageScale(s: Stage) = when (s) {
        Stage.EGG -> 0.86f
        Stage.BABY -> 0.6f
        Stage.YOUNG -> 0.76f
        Stage.ADULT -> 0.9f
        Stage.ANCIENT -> 1f
    }

    /**
     * One frame ([at] of [FRAMES]) of the idle loop: a sway, a blink near the end. Breathing is left to whoever
     * draws it ([breath]), so it can rise and fall smoothly instead of a pixel at a time.
     */
    fun frame(look: Look, at: Int): Px {
        val body = Px(SIZE, SIZE)
        val ph = at / FRAMES.toFloat()
        val f = at / STEP
        val stone = look.stone
        val eyes = when {
            look.sleeping || stone > 0.85f -> EyeState.CLOSED
            look.happy -> EyeState.HAPPY
            f == BEATS - 2 -> EyeState.BLINK
            look.mood == Mood.SCARED -> EyeState.SCARED
            look.mood == Mood.SUSPICIOUS -> EyeState.SQUINT
            else -> EyeState.OPEN
        }
        val ctx = Ctx(look, f, ph, eyes)
        val face: Face? = if (look.stage == Stage.EGG) {
            egg(body, ctx)
            null
        } else {
            val floats = look.species == Species.MOTH || look.species == Species.GHOST
            val pen = Pen(body, stageScale(look.stage), dy = if (floats) sin(ph * 2 * PI.toFloat()) * 1.5f - 2f else 0f)
            when (look.species) {
                Species.MIMIC -> mimic(pen, ctx)
                Species.KITSUNE -> kitsune(pen, ctx)
                Species.SLIME -> slime(pen, ctx)
                Species.DRAGON -> dragon(pen, ctx)
                Species.PHOENIX -> phoenix(pen, ctx)
                Species.GRIFFIN -> griffin(pen, ctx)
                Species.OWLBEAR -> owlbear(pen, ctx)
                Species.MOTH -> moth(pen, ctx)
                Species.TANUKI -> Newcomers.tanuki(pen, ctx)
                Species.SPROUT -> Newcomers.sprout(pen, ctx)
                Species.GHOST -> Newcomers.ghost(pen, ctx)
                Species.WYRM -> Newcomers.wyrm(pen, ctx)
                Species.CHIMERA -> Patchwork.chimera(pen, ctx)
                Species.MAGPIE -> Patchwork.magpie(pen, ctx)
            }
        }
        // What it wears: the colours and crown of its form, or a hat for the season.
        if (face != null) Wardrobe.dress(body, face, look, f)
        // One dark line around the whole sprite so it reads on any background.
        val solid = BooleanArray(SIZE * SIZE) { body.c[it].al() >= 160 }
        body.ring(solid, OUT)
        if (look.shiny) shine(body, look.species)
        if (stone > 0f) petrify(body, stone, look.species == Species.PHOENIX, f)
        val out = Px(SIZE, SIZE)
        shadow(out, look)
        if (look.stage == Stage.ANCIENT && stone < 0.5f) aura(out, ph)
        if (face != null && stone < 0.5f) Wardrobe.behind(out, look, f)
        out.blit(body, 0, 0)
        if (face != null && stone < 0.5f) Wardrobe.around(out, face, look, f)
        if (face != null && stone < 0.6f && !look.sleeping && !look.happy) marks(out, face, look, f)
        if (face != null && look.happy) stamp(out, HEART, face.headX + face.headR, face.headY - face.headR - (f % 6), R.pink[2], WHITE)
        if (face != null && look.sleeping && stone < 0.85f) snore(out, face, f)
        if (look.shiny && stone < 0.5f) for (i in 0 until 3) {
            if ((f + i * 4) % BEATS >= 4) continue
            val x = (SIZE * (0.2f + 0.6f * (noise(i, f / 4, 44) + 0.5f))).toInt()
            val y = (SIZE * (0.15f + 0.6f * (noise(i, f / 4, 45) + 0.5f))).toInt()
            stamp(out, SPARK, x, y, rgb(230, 240, 255), WHITE, 220)
        }
        return out
    }

    /** [f] is the beat, for what switches on and off; [ph] runs smoothly through the loop, for what moves. */
    class Ctx(val look: Look, val f: Int, val ph: Float, val eyes: EyeState) {
        val sw: Float get() = sin(ph * 2 * PI.toFloat())
        val cw: Float get() = kotlin.math.cos(ph * 2 * PI.toFloat())
    }

    private fun shadow(out: Px, look: Look) {
        val k = stageScale(look.stage)
        val rx = (if (look.species == Species.MOTH || look.species == Species.GHOST) 16f else 24f) * k
        val cy = SIZE - 3.5f
        for (y in (cy - 3).toInt()..(cy + 2).toInt()) for (x in (SIZE / 2 - rx - 1).toInt()..(SIZE / 2 + rx + 1).toInt()) {
            val d = ((x + 0.5f - SIZE / 2f) / rx).let { it * it } + ((y + 0.5f - cy) / 2.2f).let { it * it }
            if (d <= 1f) out.set(x, y, OUT, if (d < 0.45f) 80 else 45)
        }
    }

    private fun aura(out: Px, ph: Float) {
        val pulse = 0.8f + 0.2f * sin(ph * 2 * PI.toFloat())
        out.glow(SIZE / 2f, SIZE * 0.52f, SIZE * 0.47f, rgb(255, 214, 120), (70 * pulse).toInt(), 5)
        for (i in 0 until 6) {
            val a = (ph * 60f + i * 60f) * PI / 180
            val x = (SIZE / 2f + kotlin.math.cos(a) * SIZE * 0.42f).roundToInt()
            val y = (SIZE * 0.55f + sin(a) * SIZE * 0.16f).roundToInt()
            val c = rgb(255, 232, 160)
            out.set(x, y, c); out.set(x, y - 1, c, 180); out.set(x, y + 1, c, 180); out.set(x - 1, y, c, 120); out.set(x + 1, y, c, 120)
        }
    }

    /** Stone creeps up from the feet; the phoenix turns to ash with embers in it. */
    private fun petrify(px: Px, stone: Float, ash: Boolean, f: Int) {
        var top = px.h; var bottom = 0
        for (y in 0 until px.h) for (x in 0 until px.w) if (px.has(x, y)) { top = minOf(top, y); bottom = max(bottom, y) }
        if (top > bottom) return
        val ramp = if (ash) R.ash else R.stone
        for (y in top..bottom) for (x in 0 until px.w) {
            val c = px[x, y]
            if (c.al() == 0) continue
            val up = (bottom - y) / (bottom - top + 1f)
            if (up + noise(x / 2, y / 2, 5) * 0.16f > stone * 1.2f - 0.08f) continue
            val l = lum(c)
            val v = (l * 1.15f * (ramp.size - 1) + bayer(x, y) * 0.6f).roundToInt().coerceIn(0, ramp.size - 1)
            px.c[y * px.w + x] = (c.al() shl 24) or (ramp[if (c == OUT) 0 else v] and 0xFFFFFF)
        }
        if (ash) {
            for (i in 0 until 9) {
                val x = (px.w * (0.35f + 0.3f * (noise(i, 1, 3) + 0.5f))).toInt()
                val y = (top + (bottom - top) * (0.3f + 0.7f * (noise(i, 2, 3) + 0.5f))).toInt()
                if (px.has(x, y) && (i + f) % 3 != 0) px.set(x, y, if ((i + f) % 2 == 0) R.ember[3] else R.ember[2])
            }
        } else if (stone > 0.6f) {
            val k = ((stone - 0.6f) / 0.4f * 3).toInt() + 1
            for (j in 0 until k) {
                var x = (px.w * (0.38f + j * 0.12f)).toInt()
                var y = top + (bottom - top) / 4 + j * 5
                repeat(7) { s ->
                    if (px.has(x, y)) px.set(x, y, ramp[0])
                    y += 1
                    x += if (noise(j, s, 8) > 0f) 1 else -1
                }
            }
        }
    }

    /* ---------- Faces ---------- */

    fun eye(px: Px, cx: Float, cy: Float, r: Float, iris: Int, state: EyeState, skin: Int): Pair<Int, Int> {
        val w = max(3, (r * 2f).roundToInt())
        val h = w + 1
        val x0 = (cx - w / 2f).roundToInt()
        val y0 = (cy - h / 2f).roundToInt()
        val ex = x0 + w / 2
        val ey = y0 + h / 2
        fun inEye(x: Int, y: Int): Boolean {
            val dx = (x + 0.5f - (x0 + w / 2f)) / (w / 2f)
            val dy = (y + 0.5f - (y0 + h / 2f)) / (h / 2f)
            return dx * dx + dy * dy <= 1.05f
        }
        when (state) {
            EyeState.CLOSED -> {
                for (i in 0 until w) px.set(x0 + i, ey + if (i == 0 || i == w - 1) 0 else 1, EYE)
                return ex to ey
            }
            EyeState.HAPPY -> {
                // Squeezed shut: ^ ^
                for (i in 0 until w) px.set(x0 + i, ey - if (i == 0 || i == w - 1) 0 else 1, EYE)
                return ex to ey
            }
            EyeState.BLINK -> {
                for (i in 0 until w) px.set(x0 + i, ey, EYE)
                return ex to ey
            }
            EyeState.SCARED -> {
                for (y in y0 until y0 + h) for (x in x0 until x0 + w) if (inEye(x, y)) {
                    val edge = !inEye(x - 1, y) || !inEye(x + 1, y) || !inEye(x, y - 1) || !inEye(x, y + 1)
                    px.set(x, y, if (edge) EYE else WHITE)
                }
                px.set(ex, ey, EYE); if (w >= 5) { px.set(ex - 1, ey, EYE); px.set(ex, ey - 1, EYE); px.set(ex - 1, ey - 1, EYE) }
                return ex to ey
            }
            else -> {}
        }
        val dark = mixc(iris, EYE, 0.6f)
        val light = mixc(iris, WHITE, 0.35f)
        for (y in y0 until y0 + h) for (x in x0 until x0 + w) {
            if (!inEye(x, y)) continue
            val edge = !inEye(x - 1, y) || !inEye(x + 1, y) || !inEye(x, y - 1) || !inEye(x, y + 1)
            val row = (y - y0) / (h - 1f)
            val c = when {
                edge -> EYE
                row < 0.35f -> dark
                row > 0.75f -> light
                else -> iris
            }
            px.set(x, y, c)
        }
        // Pupil and the shine.
        if (w >= 5) {
            for (y in ey - 1..ey + 1) for (x in ex - 1..ex) if (inEye(x, y)) px.set(x, y, EYE)
            px.set(x0 + 1, y0 + 1, WHITE); px.set(x0 + 2, y0 + 1, WHITE); px.set(x0 + 1, y0 + 2, WHITE)
            if (w >= 6) px.set(x0 + 2, y0 + 2, WHITE)
            px.set(x0 + w - 2, y0 + h - 3, WHITE, 200)
        } else {
            px.set(ex, ey, EYE)
            px.set(x0 + 1, y0 + 1, WHITE)
        }
        if (state == EyeState.SQUINT) {
            for (y in y0 until ey) for (x in x0 - 1..x0 + w) px.set(x, y, skin)
            for (x in x0 - 1..x0 + w) px.set(x, ey, EYE)
        }
        return ex to ey
    }

    private fun blush(px: Px, e: List<Pair<Int, Int>>, w: Int, strong: Boolean) {
        val c = R.pink[2]
        val a = if (strong) 220 else 120
        e.forEachIndexed { i, (x, y) ->
            val side = if (i == 0) -1 else 1
            val bx = x + side * (w / 2 + 1)
            val by = y + w / 2 + 2
            px.set(bx, by, c, a); px.set(bx - side, by, c, a - 40)
            if (strong) px.set(bx + side, by, c, a - 60)
        }
    }

    internal fun face(px: Px, pen: Pen, ctx: Ctx, eyes: List<Pair<Float, Float>>, r: Float, iris: Int, skin: Int, head: Pair<Float, Float>, headR: Float): Face {
        val pts = eyes.map { (x, y) -> eye(px, pen.x(x), pen.y(y), pen.r(r), iris, ctx.eyes, skin) }
        val w = max(3, (pen.r(r) * 2f).roundToInt())
        if (ctx.eyes != EyeState.CLOSED) blush(px, pts, w, ctx.look.mood == Mood.HEARTS)
        return Face(pts, w, pen.x(head.first).toInt(), pen.y(head.second).toInt(), pen.r(headR).toInt())
    }

    internal val HEART = arrayOf(".x.x.", "xxxxx", "xxxxx", ".xxx.", "..x..")
    internal val SPARK = arrayOf("..x..", "..x..", "xxxxx", "..x..", "..x..")
    private val DROP = arrayOf(".x.", "xxx", "xxx", ".x.")

    internal fun stamp(px: Px, pat: Array<String>, x0: Int, y0: Int, c: Int, hi: Int, a: Int = 255) {
        pat.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch == 'x') px.set(x0 + x, y0 + y, if (x == 1 && y == 1) hi else c, a) } }
    }

    private fun marks(px: Px, f: Face, look: Look, frame: Int) {
        val ph = frame / BEATS.toFloat()
        when (look.mood) {
            Mood.HEARTS -> for (i in 0 until 2) {
                val p = (ph + i * 0.5f) % 1f
                val x = f.headX + (if (i == 0) f.headR + 2 else -f.headR - 6)
                val y = (f.headY - f.headR - p * 14).toInt()
                stamp(px, HEART, x, y, R.pink[2], WHITE, (255 * sin(p * PI).toFloat()).toInt().coerceAtLeast(60))
            }
            Mood.SPARKLE -> for (i in 0 until 3) {
                val on = (frame + i * 4) % BEATS < 6
                if (!on) continue
                val x = f.headX + listOf(-f.headR - 6, f.headR + 2, -2)[i]
                val y = f.headY - f.headR - listOf(0, 3, 9)[i]
                stamp(px, SPARK, x, y, R.gold[4], WHITE)
            }
            Mood.STARS -> for (i in 0 until 3) {
                val a = (ph * 360f + i * 120f) * PI / 180
                val x = (f.headX + kotlin.math.cos(a) * (f.headR + 4)).toInt()
                val y = (f.headY - f.headR - 4 + sin(a) * 2).toInt()
                px.set(x, y, R.sky[5]); px.set(x - 1, y, R.sky[3], 180); px.set(x + 1, y, R.sky[3], 180); px.set(x, y - 1, R.sky[3], 180); px.set(x, y + 1, R.sky[3], 180)
            }
            Mood.SCARED -> stamp(px, DROP, f.headX + f.headR, f.headY - f.headR / 2 + (frame % 6) / 2, R.sky[4], WHITE)
            Mood.GLASSES -> glasses(px, f)
            Mood.SUSPICIOUS, Mood.NONE -> {}
        }
        if (look.species == Species.OWLBEAR && look.stage >= Stage.ADULT && look.mood != Mood.GLASSES) glasses(px, f)
        // The bookworm can't read without its spectacles.
        if (look.species == Species.WYRM && look.stage >= Stage.YOUNG && look.mood != Mood.GLASSES) glasses(px, f)
    }

    private val ZED = arrayOf("xxx", "..x", ".x.", "xxx")

    private fun snore(px: Px, f: Face, frame: Int) {
        for (i in 0 until 2) {
            val p = ((frame + i * 6) % BEATS) / BEATS.toFloat()
            val x = f.headX + f.headR / 2 + (p * 8).toInt() + i * 3
            val y = f.headY - f.headR - (p * 14).toInt()
            stamp(px, ZED, x, y, R.sky[5], R.sky[5], (255 * sin(p * PI).toFloat()).toInt().coerceAtLeast(120))
        }
    }

    /** The rare colouring: each species gets its own turn of hue (a moonlit kitsune, an ice phoenix…). */
    private fun shine(px: Px, sp: Species) {
        val (shift, sat) = when (sp) {
            Species.MIMIC -> 18f to 1.6f
            Species.KITSUNE -> 190f to 0.6f
            Species.SLIME -> 150f to 1f
            Species.DRAGON -> 170f to 1f
            Species.PHOENIX -> 190f to 0.9f
            Species.GRIFFIN -> 0f to 0.05f
            Species.OWLBEAR -> 0f to 0.08f
            Species.MOTH -> 110f to 1f
            Species.TANUKI -> 200f to 0.7f
            Species.SPROUT -> 250f to 1f
            Species.GHOST -> 0f to 1f
            Species.WYRM -> 130f to 1f
            Species.CHIMERA -> 160f to 0.8f
            Species.MAGPIE -> 200f to 1.4f
        }
        val hsv = FloatArray(3)
        for (i in px.c.indices) {
            val c = px.c[i]
            if (c.al() == 0 || c and 0xFFFFFF == OUT and 0xFFFFFF || c and 0xFFFFFF == EYE and 0xFFFFFF) continue
            android.graphics.Color.RGBToHSV(c.r(), c.g(), c.b(), hsv)
            if (hsv[1] < 0.12f) continue
            hsv[0] = (hsv[0] + shift) % 360f
            hsv[1] = (hsv[1] * sat).coerceIn(0f, 1f)
            if (sat < 0.2f) hsv[2] = (hsv[2] * 1.12f + 0.08f).coerceAtMost(1f)
            px.c[i] = (c.al() shl 24) or (android.graphics.Color.HSVToColor(hsv) and 0xFFFFFF)
        }
    }

    private fun glasses(px: Px, f: Face) {
        val rim = R.gold[1]
        val r = f.eyeW / 2 + 2
        f.eyes.forEach { (cx, cy) ->
            for (a in 0 until 360 step 10) {
                val x = (cx + kotlin.math.cos(a * PI / 180) * r).roundToInt()
                val y = (cy + sin(a * PI / 180) * r).roundToInt()
                px.set(x, y, rim)
            }
            px.set(cx - r + 2, cy - r + 2, WHITE, 160)
        }
        if (f.eyes.size == 2) {
            val (a, b) = f.eyes
            px.hline(a.first + r, b.first - r, a.second - 1, rim)
        }
    }

    /* ---------- Textures ---------- */

    fun fur(salt: Int, k: Float = 0.16f): (Int, Int) -> Float = { x, y -> noise(x, y / 3, salt) * k + if (noise(x / 2, y / 2, salt + 9) > 0.32f) -0.12f else 0f }
    fun feathers(k: Float = 0.2f): (Int, Int) -> Float = { x, y -> if ((y % 4 == 0 && (x + y / 4 * 2) % 4 != 0) || ((x + y / 4 * 2) % 4 == 0 && y % 4 == 3)) -k else 0f }
    fun scales(k: Float = 0.22f): (Int, Int) -> Float = { x, y -> val r = y / 3; val xx = x + (r % 2) * 2; if (y % 3 == 2 && xx % 4 != 0) -k else if (xx % 4 == 0 && y % 3 != 0) -k * 0.6f else 0f }
    fun grain(salt: Int): (Int, Int) -> Float = { x, y -> noise(x / 6, y, salt) * 0.24f + if (y % 6 == 0) -0.3f else 0f }

    /* ---------- Eggs ---------- */

    fun eggRamp(sp: Species): Ramp = when (sp) {
        Species.MIMIC -> R.wood
        Species.KITSUNE -> R.fox
        Species.SLIME -> R.slime
        Species.DRAGON -> R.teal
        Species.PHOENIX -> R.flame
        Species.GRIFFIN -> R.tan
        Species.OWLBEAR -> R.owl
        Species.MOTH -> R.night
        Species.TANUKI -> R.tanuki
        Species.SPROUT -> R.leaf
        Species.GHOST -> R.fluff
        Species.WYRM -> R.paper
        Species.CHIMERA -> R.tan
        Species.MAGPIE -> R.snow
    }

    private fun egg(px: Px, ctx: Ctx) {
        val sp = ctx.look.species
        val h = ctx.look.hatch
        val wob = if (h > 0.55f && ctx.f % 6 < 2) (if (ctx.f % 2 == 0) -1.5f else 1.5f) else 0f
        val pen = Pen(px, 0.86f, dx = wob)
        val shell = pen.path().m(50f, 30f).c(66f, 30f, 74f, 58f, 72f, 70f).c(70f, 82f, 61f, 88f, 50f, 88f).c(39f, 88f, 30f, 82f, 28f, 70f).c(26f, 58f, 34f, 30f, 50f, 30f).shape()
        val ramp = eggRamp(sp)
        val tex: (Int, Int) -> Float = when (sp) {
            Species.DRAGON -> scales()
            Species.GRIFFIN, Species.OWLBEAR -> feathers(0.14f)
            Species.MIMIC -> grain(3)
            else -> { _, _ -> 0f }
        }
        val m = px.shade(shell, pen.dome(50f, 62f, 23f, 29f), ramp, texture = tex, spec = true, rim = ramp[5])
        fun inShell(x: Int, y: Int) = x in 0 until px.w && y in 0 until px.h && m[y * px.w + x]
        when (sp) {
            Species.MIMIC -> for (yy in listOf(52f, 69f)) {
                val y = pen.y(yy).toInt()
                for (x in 0 until px.w) for (j in 0..2) if (inShell(x, y + j)) px.set(x, y + j, R.gold[if (j == 0) 4 else 3 - j])
            }
            Species.KITSUNE -> for (y in pen.y(73f).toInt() until px.h) for (x in 0 until px.w) if (inShell(x, y)) px.set(x, y, R.cream[if (x < px.w / 2) 4 else 3])
            Species.PHOENIX -> for (x in 0 until px.w) {
                val top = pen.y(70f) - 3 * kotlin.math.abs(sin(x * 0.45f))
                for (y in top.toInt() until px.h) if (inShell(x, y)) px.set(x, y, R.ember[if (y < top + 2) 4 else 3])
            }
            Species.MOTH -> for (i in 0 until 14) {
                val x = pen.x(34f + (noise(i, 1, 2) + 0.5f) * 32f).toInt(); val y = pen.y(38f + (noise(i, 3, 2) + 0.5f) * 44f).toInt()
                if (inShell(x, y)) px.set(x, y, if ((i + ctx.f) % 4 == 0) WHITE else R.gold[4])
            }
            Species.SLIME -> for (i in 0 until 6) {
                val x = pen.x(36f + (noise(i, 1, 4) + 0.5f) * 28f).toInt(); val y = pen.y(40f + (noise(i, 3, 4) + 0.5f) * 40f).toInt()
                if (inShell(x, y)) { px.set(x, y, R.slime[6]); px.set(x + 1, y, R.slime[5]) }
            }
            Species.TANUKI -> for (yy in listOf(56f, 70f)) {
                val y = pen.y(yy).toInt()
                for (x in 0 until px.w) for (j in 0..2) if (inShell(x, y + j)) px.set(x, y + j, R.brown[1 + j / 2])
            }
            Species.SPROUT -> for (i in 0 until 7) {
                val x = pen.x(34f + (noise(i, 1, 12) + 0.5f) * 32f).toInt(); val y = pen.y(40f + (noise(i, 3, 12) + 0.5f) * 42f).toInt()
                if (inShell(x, y)) { px.set(x, y, R.gold[4]); for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) if (inShell(x + dx, y + dy)) px.set(x + dx, y + dy, R.pink[3]) }
            }
            Species.GHOST -> if (ctx.f % 12 != 7) for (ex in listOf(44f, 56f)) {
                // Two eyes peek through the shell.
                val x = pen.x(ex).toInt(); val y = pen.y(58f).toInt()
                px.rect(x - 1, y - 1, 2, 3, EYE)
            }
            Species.WYRM -> for (k in 0 until 6) {
                val y = pen.y(44f + k * 6.5f).toInt()
                for (x in pen.x(36f).toInt()..pen.x(64f).toInt()) if (inShell(x, y) && (x + k) % 9 != 0) px.set(x, y, R.paper[1])
            }
            Species.CHIMERA -> listOf(R.violet, R.teal, R.red, R.leaf).forEachIndexed { i, ramp ->
                // Patches of other shells, sewn on.
                val cx = pen.x(listOf(41f, 58f, 47f, 60f)[i]); val cy = pen.y(listOf(48f, 58f, 72f, 77f)[i])
                for (y in (cy - 3).toInt()..(cy + 3).toInt()) for (x in (cx - 4).toInt()..(cx + 4).toInt()) {
                    val d = (x - cx) * (x - cx) / 16f + (y - cy) * (y - cy) / 9f
                    if (d <= 1f && inShell(x, y)) px.set(x, y, if (d > 0.7f && (x + y) % 2 == 0) R.brown[1] else ramp[3])
                }
            }
            Species.MAGPIE -> for (y in pen.y(36f).toInt() until pen.y(60f).toInt()) for (x in 0 until px.w) if (inShell(x, y)) px.set(x, y, Patchwork.ink[if (x < px.w / 2) 3 else 2])
            Species.OWLBEAR -> for (i in 0 until 16) {
                val x = pen.x(32f + (noise(i, 1, 6) + 0.5f) * 36f).toInt(); val y = pen.y(36f + (noise(i, 3, 6) + 0.5f) * 48f).toInt()
                if (inShell(x, y)) { px.set(x, y, R.owl[1]); if (i % 2 == 0) px.set(x + 1, y, R.owl[2]) }
            }
            else -> {}
        }
        if (h > 0.25f) {
            val pts = listOf(33f to 55f, 39f to 50f, 44f to 57f, 50f to 50f, 55f to 58f, 61f to 51f, 67f to 56f)
            val n = (2 + (h - 0.25f) / 0.75f * (pts.size - 2)).toInt().coerceIn(2, pts.size)
            for (i in 0 until n - 1) {
                val (ax, ay) = pts[i]; val (bx, by) = pts[i + 1]
                px.line(pen.x(ax).toInt(), pen.y(ay).toInt(), pen.x(bx).toInt(), pen.y(by).toInt(), OUT)
            }
        }
        if (h > 0.8f) px.glow(px.w / 2f, pen.y(58f), pen.r(30f), rgb(255, 236, 170), 90)
    }

    /* ---------- Kitsune: a tail for every genre ---------- */

    private fun kitsune(pen: Pen, ctx: Ctx): Face {
        val px = pen.px
        val tails = ctx.look.tails.ifEmpty { listOf(R.fox[4]) }
        val n = tails.size
        tails.forEachIndexed { i, color ->
            val ang = if (n == 1) 52f else -88f + 176f * i / (n - 1)
            val sway = sin(ctx.ph * 2 * PI.toFloat() + i * 0.8f) * 4f
            val len = 50f - abs(ang) * 0.06f
            val w = if (n > 5) 11f else 13f
            val a = ang + sway
            val tail = pen.path(a, 50f, 80f).m(50f, 80f).c(50f - w, 80f - len * 0.35f, 47f - w * 0.6f, 80f - len * 0.85f, 52f, 80f - len).c(50f + w * 0.9f, 80f - len * 0.8f, 50f + w, 80f - len * 0.3f, 50f, 80f).shape()
            val tip = pen.path(a, 50f, 80f).m(50f - w, 80f - len * 0.7f).l(50f - w * 0.4f, 80f - len * 0.78f).l(50f, 80f - len * 0.72f).l(50f + w * 0.5f, 80f - len * 0.8f).l(50f + w, 80f - len * 0.7f).l(54f, 80f - len - 4).l(46f, 80f - len - 4).shape()
            val ramp = R.around(mixc(color, R.fox[4], 0.2f))
            val normal = pen.turnedDome(50.5f, 80f - len * 0.55f, w * 0.85f, len * 0.55f, a, 50f, 80f)
            px.shade(tail, normal, ramp, texture = fur(i + 3, 0.2f), rim = ramp[5])
            px.shade(and(tail, tip), normal, R.cream, outline = false, texture = fur(i + 30, 0.1f), lo = 2)
        }
        val body = pen.path().m(50f, 47f).c(63f, 48f, 71f, 70f, 69f, 86f).q(50f, 91f, 31f, 86f).c(29f, 70f, 37f, 48f, 50f, 47f).shape()
        px.shade(body, pen.dome(50f, 66f, 21f, 22f), R.fox, texture = fur(1), rim = R.fox[5])
        val bib = pen.path().m(50f, 52f).c(58f, 56f, 59f, 72f, 50f, 80f).c(41f, 72f, 42f, 56f, 50f, 52f).shape()
        px.shade(bib, pen.dome(50f, 64f, 10f, 15f), R.cream, outline = false, texture = fur(2, 0.12f), lo = 2)
        for (sx in listOf(43f, 57f)) {
            px.shade(pen.ell(sx, 86f, 5.2f, 3.4f), pen.dome(sx, 85f, 5f, 3.4f), R.cream, lo = 1)
            pen.set(sx - 1.5f, 87.5f, R.cream[1]); pen.set(sx + 1.5f, 87.5f, R.cream[1])
        }
        val twitch = if (ctx.f in 3..4) -10f else 0f
        for (m in listOf(false, true)) {
            val deg = if (m) twitch else 0f
            val px0 = if (m) 58f else 42f
            val ear = pen.path(deg, px0, 28f, m).m(35f, 32f).l(29f, 8f).q(38f, 13f, 46f, 24f).shape()
            px.shade(ear, pen.dome(if (m) 64f else 36f, 22f, 9f, 13f), R.fox, texture = fur(7), rim = R.fox[5])
            val inner = pen.path(deg, px0, 28f, m).m(36f, 28f).l(31.5f, 13.5f).q(37.5f, 17f, 42f, 24f).shape()
            px.shade(inner, facing(0f, 0.3f, 1f), R.tongue, outline = false, lo = 0, hi = 2)
        }
        val head = pen.path().m(50f, 22.5f).c(62f, 22.5f, 68f, 30f, 67f, 38f).l(71f, 45f).l(62f, 47f).q(50f, 52f, 38f, 47f).l(29f, 45f).l(33f, 38f).c(32f, 30f, 38f, 22.5f, 50f, 22.5f).shape()
        px.shade(head, pen.dome(50f, 36f, 19f, 16f), R.fox, texture = fur(4, 0.12f), rim = R.fox[5])
        // White cheeks and muzzle.
        val cheeks = pen.path().m(33f, 41f).l(29f, 45f).l(38f, 47f).q(50f, 52f, 62f, 47f).l(71f, 45f).l(67f, 41f).q(58f, 44f, 50f, 40f).q(42f, 44f, 33f, 41f).shape()
        px.shade(cheeks, pen.dome(50f, 44f, 16f, 7f), R.cream, outline = false, lo = 2)
        px.shade(pen.ell(50f, 45f, 7.5f, 4.6f), pen.dome(50f, 44f, 7.5f, 4.6f), R.cream, outline = false, lo = 3)
        val nx = pen.x(50f).roundToInt(); val ny = pen.y(41.5f).roundToInt()
        px.rect(nx - 1, ny, 3, 2, EYE); px.set(nx - 1, ny, R.iron[3])
        px.set(nx, ny + 2, EYE); px.set(nx - 1, ny + 3, EYE); px.set(nx + 1, ny + 3, EYE); px.set(nx - 2, ny + 2, EYE, 160); px.set(nx + 2, ny + 2, EYE, 160)
        // A red flame mark on the brow.
        val mark = pen.path().m(50f, 24f).q(52.4f, 27.5f, 50f, 31f).q(47.6f, 27.5f, 50f, 24f).shape()
        px.shade(mark, facing(), R.red, outline = false, lo = 2, hi = 4)
        if (ctx.look.stage == Stage.ANCIENT) for (i in 0 until 3) {
            val a = (ctx.ph * 360f + i * 120f) * PI / 180
            val fx = pen.x(50f + kotlin.math.cos(a).toFloat() * 38f); val fy = pen.y(54f + sin(a).toFloat() * 14f)
            px.glow(fx, fy, 6f, R.sky[4], 110)
            val fl = poly(fx, fy - 4, fx + 2.2f, fy, fx + 1f, fy + 2, fx - 1f, fy + 2, fx - 2.2f, fy)
            px.shade(fl, facing(), R.sky, outline = false, lo = 3)
        }
        return face(px, pen, ctx, listOf(43.5f to 36f, 56.5f to 36f), 3.6f, R.gold[3], R.fox[4], 50f to 36f, 17f)
    }

    /* ---------- Mimic: a book chest with teeth ---------- */

    private fun mimic(pen: Pen, ctx: Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        val open = if (ctx.look.chew) (ctx.sw * 0.5f + 0.5f) else 0.3f + 0.12f * ctx.sw
        val lift = 5f + open * 8f
        val legs = st >= Stage.YOUNG
        val bottom = if (legs) 84f else 88f
        val top = bottom - 30f
        val gold = if (st == Stage.ANCIENT) R.gold else R.gold
        if (legs) for (lx in listOf(29f, 71f)) {
            px.shade(pen.box(lx - 3.5f, bottom - 3f, lx + 3.5f, 88.5f), pen.column(lx, 3.5f), R.wood, lo = 0, hi = 4)
            px.shade(pen.ell(lx, 88f, 4.5f, 1.6f), facing(0f, -0.5f, 1f), R.iron, lo = 1)
        }
        // The mouth, the books it ate, its teeth.
        px.shade(pen.ell(50f, top - lift / 2f + 1f, 26f, lift / 2f + 3f), facing(0f, 0.6f, 1f), R.mouth, outline = false)
        if (st == Stage.ANCIENT) for (i in 0 until 7) {
            val c = R.around(intArrayOf(rgb(122, 75, 176), rgb(46, 134, 201), rgb(192, 65, 58), rgb(63, 138, 92), rgb(216, 169, 63), rgb(226, 120, 156), rgb(79, 108, 158))[i])
            px.shade(pen.box(29f + i * 6f, top - lift * 0.7f, 33.6f + i * 6f, top + 1f), pen.column(31.3f + i * 6f, 2.3f), c, lo = 1)
        }
        val body = pen.box(22f, top, 78f, bottom)
        px.shade(body, { x, y -> V3(((pen.ux(x) - 50f) / 34f), 0.12f, 1f).let { val n = kotlin.math.sqrt(it.x * it.x + it.y * it.y + 1f); V3(it.x / n, it.y / n, 1f / n) } }, R.wood, texture = grain(2), rim = R.wood[5])
        for (bx in listOf(26.5f, 69f)) px.shade(pen.box(bx, top, bx + 4.5f, bottom), pen.column(bx + 2.25f, 2.25f), gold, spec = true)
        // Rivets.
        for (bx in listOf(28.75f, 71.25f)) for (yy in listOf(top + 4f, bottom - 4f)) { pen.set(bx, yy, R.gold[5]); pen.set(bx + 0.8f, yy + 0.8f, R.gold[1]) }
        val lock = pen.path().m(44.5f, top + 5f).l(55.5f, top + 5f).l(55.5f, top + 14f).q(50f, top + 19f, 44.5f, top + 14f).l(44.5f, top + 5f).shape()
        px.shade(lock, pen.dome(50f, top + 10f, 7f, 8f), gold, spec = true)
        val kx = pen.x(50f).roundToInt(); val ky = pen.y(top + 9.5f).roundToInt()
        px.rect(kx - 1, ky, 2, 2, OUT); px.rect(kx - 1, ky + 2, 2, 2, OUT)
        for (i in 0 until 6) {
            val tx = 25f + i * 8.5f
            px.shade(pen.path().m(tx, top + 0.5f).l(tx + 4.2f, top - 5.5f).l(tx + 8.4f, top + 0.5f).l(tx, top + 0.5f).shape(), facing(-0.4f, -0.3f, 1f), R.cream, lo = 2)
        }
        if (open > 0.28f) {
            val tongue = pen.path().m(52f, top - 1f).c(66f, top - 1f, 67f, top + 10f, 60f, top + 10f).c(56f, top + 10f, 55f, top + 4f, 52f, top - 1f).shape()
            px.shade(tongue, pen.dome(59f, top + 4f, 7f, 6f), R.tongue, spec = true)
            px.line(pen.x(58f).toInt(), pen.y(top + 1f).toInt(), pen.x(60f).toInt(), pen.y(top + 7f).toInt(), R.tongue[1])
        }
        // The lid, lifted on its hinge.
        val lp = Pen(px, pen.s, pen.dy - pen.r(lift), pen.dx)
        val lid = lp.path(-open * 3f, 78f, top).m(22f, top).l(22f, top - 8f).c(22f, top - 17f, 78f, top - 17f, 78f, top - 8f).l(78f, top).l(22f, top).shape()
        px.shade(lid, lp.dome(50f, top - 6f, 34f, 14f), R.wood, texture = grain(5), rim = R.wood[5])
        for (bx in listOf(26.5f, 69f)) px.shade(and(lid, lp.box(bx, top - 20f, bx + 4.5f, top)), lp.column(bx + 2.25f, 2.25f), gold, outline = false, spec = true)
        for (i in 0 until 5) {
            val tx = 29f + i * 8.5f
            px.shade(lp.path().m(tx, top - 0.5f).l(tx + 4.2f, top + 5f).l(tx + 8.4f, top - 0.5f).l(tx, top - 0.5f).shape(), facing(-0.4f, 0.3f, 1f), R.cream, lo = 2)
        }
        if (st == Stage.ANCIENT) {
            val gem = lp.path().m(50f, top - 19f).l(53.5f, top - 15f).l(50f, top - 11f).l(46.5f, top - 15f).l(50f, top - 19f).shape()
            px.shade(gem, lp.dome(50f, top - 15f, 3.5f, 4f), R.red, spec = true)
            px.glow(lp.x(50f), lp.y(top - 15f), lp.r(8f), R.red[4], 70)
        }
        return face(px, lp, ctx, listOf(39.5f to top - 8f, 60.5f to top - 8f), 4.4f, R.violet[4], R.wood[4], 50f to top - 8f, 16f)
    }

    /* ---------- Slime: you can see what it swallowed ---------- */

    private fun slime(pen0: Pen, ctx: Ctx): Face {
        val px = pen0.px
        val squash = if (ctx.look.chew) ctx.sw * 0.07f else ctx.sw * 0.035f
        val pen = Pen(px, pen0.s, pen0.dy, pen0.dx)
        val sx = 1f + squash
        val sy = 1f - squash
        fun X(v: Float) = 50f + (v - 50f) * sx
        fun Y(v: Float) = 88f + (v - 88f) * sy
        val dome = pen.path().m(X(15f), 88f).c(X(13f), Y(60f), X(30f), Y(35f), 50f, Y(35f)).c(X(70f), Y(35f), X(87f), Y(60f), X(85f), 88f).q(50f, 92f, X(15f), 88f).shape()
        val m = px.shade(dome, pen.dome(50f, Y(64f), 36f * sx, 30f * sy, 0.1f), R.slime, dither = 0.7f, rim = R.slime[5], lo = 1)
        // Swallowed things, tinted by the jelly around them.
        val slots = listOf(35f to 80f, 50f to 82f, 65f to 80f, 42f to 71f, 58f to 71f, 27f to 74f, 73f to 74f)
        ctx.look.items.take(slots.size).forEachIndexed { i, item ->
            val (ix, iy) = slots[i]
            val bob = if ((ctx.f + i * 3) % 12 < 6) 0 else 1
            val g = Px(9, 9)
            Glyphs.draw(g, Glyph.of(item), 1, 1)
            for (y in 0 until 9) for (x in 0 until 9) {
                val c = g[x, y]
                if (c.al() == 0) continue
                val tx = pen.x(X(ix)).toInt() - 4 + x
                val ty = pen.y(Y(iy)).toInt() - 4 + y + bob
                if (tx in 0 until px.w && ty in 0 until px.h && m[ty * px.w + tx]) px.set(tx, ty, mixc(c, R.slime[3], 0.28f))
            }
        }
        // Bubbles rising inside.
        for (i in 0 until 3) {
            val p = ((ctx.ph + i / 3f) % 1f)
            val bx = pen.x(X(36f + i * 13f)).toInt()
            val by = pen.y(84f - p * 36f).toInt()
            if (by in 0 until px.h && m[by * px.w + bx]) { px.set(bx, by, R.slime[6], 200); px.set(bx + 1, by - 1, R.slime[5], 160) }
        }
        // A wet shine.
        val shine = pen.path().m(X(25f), Y(58f)).c(X(25f), Y(48f), X(32f), Y(42f), X(38f), Y(41f)).l(X(39f), Y(44f)).c(X(33f), Y(46f), X(29f), Y(51f), X(28f), Y(59f)).shape()
        px.shade(shine, facing(), Ramp(WHITE, WHITE), outline = false)
        pen.set(X(41f), Y(40.5f), WHITE); pen.set(X(43f), Y(40.5f), WHITE, 180)
        // Little drip at the side.
        px.shade(pen.ell(X(80f), 86f, 2.4f, 3f), pen.dome(X(80f), 86f, 2.4f, 3f), R.slime, lo = 2)
        val mx = pen.x(50f).roundToInt(); val my = pen.y(Y(60.5f)).roundToInt()
        px.set(mx - 2, my, EYE); px.set(mx - 1, my + 1, EYE); px.set(mx, my + 1, EYE); px.set(mx + 1, my + 1, EYE); px.set(mx + 2, my, EYE)
        if (ctx.look.chew) { px.set(mx - 1, my + 2, R.mouth[2]); px.set(mx, my + 2, R.mouth[2]); px.set(mx + 1, my + 2, R.mouth[2]) }
        if (ctx.look.stage == Stage.ANCIENT) {
            val crown = pen.path().m(41f, Y(37f)).l(41f, Y(29f)).l(45.5f, Y(33f)).l(50f, Y(26f)).l(54.5f, Y(33f)).l(59f, Y(29f)).l(59f, Y(37f)).l(41f, Y(37f)).shape()
            px.shade(crown, facing(-0.2f, -0.2f, 1f), R.gold, spec = true)
            pen.set(50f, Y(33f), R.red[3]); pen.set(45.5f, Y(35f), R.sky[4]); pen.set(54.5f, Y(35f), R.sky[4])
        }
        return face(px, pen, ctx, listOf(X(40.5f) to Y(54f), X(59.5f) to Y(54f)), 4.6f, R.teal[2], R.slime[4], 50f to Y(54f), 20f)
    }

    /* ---------- Dragon: sits on a hoard of finished books ---------- */

    private fun book(px: Px, pen: Pen, x0: Float, y0: Float, w: Float, h: Float, color: Int) {
        val r = R.around(color)
        px.shade(pen.box(x0, y0, x0 + w, y0 + h), { x, y -> V3(0f, ((pen.uy(y) - (y0 + h / 2)) / (h / 2)).coerceIn(-1f, 1f) * 0.7f, 0.8f) }, r, lo = 1)
        px.shade(pen.box(x0 + 2.4f, y0 + 1f, x0 + w - 0.6f, y0 + h - 1f), facing(0f, -0.2f, 1f), R.paper, outline = false, lo = 2)
        val ly = pen.y(y0 + h / 2).toInt()
        for (x in pen.x(x0 + 4f).toInt() until pen.x(x0 + w - 2f).toInt() step 2) px.set(x, ly, R.paper[1], 120)
        pen.set(x0 + 1.2f, y0 + h / 2, R.gold[4])
    }

    private fun dragon(pen0: Pen, ctx: Ctx): Face {
        val px = pen0.px
        val books = ctx.look.hoard.take(16)
        val stack = minOf(books.size, 6)
        books.drop(6).forEachIndexed { i, c ->
            val left = i % 2 == 0
            val k = i / 2
            val bx = if (left) 20f - k * 5f else 75f + k * 5f
            val bh = 13f + (noise(i, 2, 21) + 0.5f) * 5f
            val r = R.around(c)
            px.shade(pen0.box(bx, 88f - bh, bx + 4.8f, 88f), pen0.column(bx + 2.4f, 2.4f), r, lo = 1)
            pen0.set(bx + 2.4f, 88f - bh * 0.75f, R.gold[4]); pen0.set(bx + 2.4f, 88f - bh * 0.3f, R.gold[4])
        }
        if (books.isEmpty()) for (i in 0 until 7) px.shade(pen0.ell(36f + i * 4.6f, 86.5f - (i % 2) * 1.2f, 2.8f, 1.6f), facing(0f, -0.6f, 1f), R.gold, spec = true)
        for (i in 0 until stack) {
            val by = 88f - 4.4f * (i + 1)
            val bx = 35f + ((i % 2) * 2 - 1) * 2.5f
            book(px, pen0, bx, by, 30f, 4.4f, books[i])
        }
        val lift = stack * 4.4f
        val sc = (88f - lift - 6f) / 82f
        val pen = Pen(px, pen0.s * sc, pen0.dy - pen0.r(lift), pen0.dx)
        val st = ctx.look.stage
        val flap = ctx.sw * 6f
        val horn = when (st) { Stage.BABY -> 0.55f; Stage.YOUNG -> 0.75f; Stage.ADULT -> 1f; else -> 1.2f }
        for (m in listOf(false, true)) {
            val deg = if (m) -flap else flap
            val pvx = if (m) 60f else 40f
            val wing = pen.path(deg, pvx, 56f, m).m(40f, 56f).c(30f, 40f, 18f, 29f, 7f, 29f).q(12f, 41f, 10f, 50f).q(18f, 49f, 20f, 58f).q(28f, 55f, 32f, 64f).l(40f, 62f).l(40f, 56f).shape()
            px.shade(wing, pen.turnedDome(24f, 46f, 18f, 16f, deg, pvx, 56f), R.violet, texture = { x, y -> noise(x, y, 4) * 0.08f }, rim = R.violet[5])
            // Finger bones.
            val (sx0, sy0) = pen.at(40f, 56f, deg, pvx, 56f, m)
            for ((fx, fy) in listOf(7f to 29f, 20f to 58f, 10f to 50f)) {
                val (ex, ey) = pen.at(fx, fy, deg, pvx, 56f, m)
                px.line(sx0, sy0, ex, ey, R.violet[1])
            }
        }
        val tail = pen.path().m(60f, 80f).c(74f, 82f, 86f, 80f, 86f, 70f).l(89f, 70f).c(89f, 85f, 74f, 91f, 60f, 88f).l(60f, 80f).shape()
        px.shade(tail, pen.dome(76f, 82f, 14f, 6f), R.teal, texture = scales(0.15f), rim = R.teal[5])
        px.shade(pen.path().m(87.5f, 61f).l(92f, 69f).l(87.5f, 73f).l(83f, 69f).l(87.5f, 61f).shape(), pen.dome(87.5f, 67f, 4.5f, 6f), R.violet, spec = true)
        val body = pen.path().m(50f, 46f).c(64f, 47f, 72f, 68f, 70f, 86f).q(50f, 91f, 30f, 86f).c(28f, 68f, 36f, 47f, 50f, 46f).shape()
        px.shade(body, pen.dome(50f, 66f, 21f, 22f), R.teal, texture = scales(), rim = R.teal[5])
        val belly = pen.path().m(50f, 54f).c(58f, 58f, 60f, 74f, 56f, 86f).l(44f, 86f).c(40f, 74f, 42f, 58f, 50f, 54f).shape()
        px.shade(belly, pen.dome(50f, 70f, 10f, 16f), R.belly, outline = false, lo = 1)
        for (yy in listOf(61f, 66.5f, 72f, 77.5f, 83f)) for (x in pen.x(44f).toInt()..pen.x(56f).toInt()) { val y = pen.y(yy).toInt(); if (px[x, y] != 0 && px[x, y] and 0xFFFFFF != R.teal[0] and 0xFFFFFF) px.set(x, y, R.belly[1], 150) }
        for (ax in listOf(42f, 58f)) px.shade(pen.ell(ax, 64f, 3.4f, 5.6f), pen.dome(ax, 63f, 3.4f, 5.6f), R.teal, rim = R.teal[5])
        for (fx in listOf(40f, 60f)) {
            px.shade(pen.ell(fx, 86f, 6.2f, 3.4f), pen.dome(fx, 85f, 6.2f, 3.4f), R.teal, lo = 0, hi = 4)
            for (c in -1..1) pen.set(fx + c * 3f, 88.3f, R.horn[3])
        }
        for (m in listOf(false, true)) {
            val h = pen.path(mirror = m).m(39f, 27f).q(33f, 26f - 8f * horn, 32f, 25f - 18f * horn).q(40f, 26f - 11f * horn, 46f, 24f).l(39f, 27f).shape()
            px.shade(h, pen.dome(if (m) 61f else 39f, 26f - 8f * horn, 5f, 9f * horn), R.horn, texture = { _, y -> if (y % 3 == 0) -0.2f else 0f })
            px.shade(pen.path(mirror = m).m(36f, 33f).l(28f, 30f).l(31f, 35f).l(28f, 38f).l(36f, 40f).l(36f, 33f).shape(), facing(if (m) 0.4f else -0.4f, 0f, 1f), R.violet, lo = 1)
        }
        for (sx in listOf(45f, 50f, 55f)) {
            val tall = if (sx == 50f) 6f else 4.5f
            px.shade(pen.path().m(sx - 2.2f, 24f).l(sx, 24f - tall).l(sx + 2.2f, 24f).l(sx - 2.2f, 24f).shape(), facing(-0.3f, -0.3f, 1f), R.violet, lo = 2)
        }
        px.shade(pen.ell(50f, 35f, 15.5f, 13f), pen.dome(50f, 33f, 15.5f, 13f), R.teal, texture = scales(0.08f), rim = R.teal[5], lo = 1)
        px.shade(pen.ell(50f, 42f, 9.5f, 6.5f), pen.dome(50f, 41f, 9.5f, 6.5f), R.teal, lo = 2, hi = 6)
        pen.set(47f, 40.5f, R.teal[0]); pen.set(53f, 40.5f, R.teal[0])
        val mx = pen.x(50f).roundToInt(); val my = pen.y(45.6f).roundToInt()
        px.hline(mx - 2, mx + 2, my, R.teal[1]); px.set(mx - 3, my - 1, R.teal[1]); px.set(mx + 3, my - 1, R.teal[1])
        if (ctx.look.stone < 0.3f && ctx.f in 6..10) for (j in 0 until 3) {
            val p = (ctx.f - 6) / 5f + j * 0.15f
            val r = pen.r(1.6f + p * 3f)
            px.shade(ellipse(pen.x(52f + j * 2.5f + p * 6f), pen.y(37f - p * 14f), r, r), dome(0f, 0f, 1f, 1f).let { facing(-0.3f, -0.4f, 1f) }, Ramp(rgb(120, 112, 120), rgb(170, 164, 168), rgb(214, 210, 210)), outline = false)
        }
        return face(px, pen, ctx, listOf(43.5f to 33f, 56.5f to 33f), 4.2f, R.gold[3], R.teal[4], 50f to 35f, 15.5f)
    }

    /* ---------- Phoenix: burns to ash and rises again ---------- */

    private fun flameShape(pen: Pen, bx: Float, by: Float, tx: Float, ty: Float, w: Float): Shape =
        pen.path().m(bx - w, by).q(tx - w * 0.9f, (ty + by) / 2f, tx, ty).q(tx + w * 0.9f, (ty + by) / 2f, bx + w, by).l(bx - w, by).shape()

    private fun phoenix(pen: Pen, ctx: Ctx): Face {
        val px = pen.px
        val alive = ctx.look.stone < 0.3f
        if (alive) px.glow(pen.x(50f), pen.y(50f), pen.r(44f), R.flame[5], 70, 5)
        for (i in listOf(-2, 2, -1, 1)) {
            val a = i * 22f + (if (alive) sin(ctx.ph * 2 * PI.toFloat() + i) * 4f else 0f)
            val plume = pen.path(a, 50f, 62f).m(50f, 62f).c(43f, 74f, 42f, 86f, 50f, 98f).c(58f, 86f, 57f, 74f, 50f, 62f).shape()
            px.shade(plume, pen.turnedDome(50f, 80f, 7f, 18f, a, 50f, 62f), R.flame, texture = { x, y -> if ((x + y) % 5 == 0) -0.2f else 0f }, rim = R.flame[6], lo = 0)
            val eye = pen.path(a, 50f, 62f).m(50f, 86f).c(46.5f, 88f, 46.5f, 93f, 50f, 94f).c(53.5f, 93f, 53.5f, 88f, 50f, 86f).shape()
            px.shade(eye, facing(), R.ember, outline = false, lo = 3)
        }
        for (m in listOf(false, true)) {
            val wing = pen.path(mirror = m).m(37f, 51f).c(25f, 57f, 25f, 75f, 34f, 83f).q(38f, 77f, 43f, 74f).l(37f, 51f).shape()
            px.shade(wing, pen.dome(if (m) 66f else 34f, 66f, 9f, 16f), R.flame, texture = feathers(0.22f), rim = R.flame[5], lo = 0, hi = 4)
            for (k in 0 until 3) { val yy = 70f + k * 4f; px.line(pen.x(if (m) 69f else 31f).toInt(), pen.y(yy).toInt(), pen.x(if (m) 64f else 36f).toInt(), pen.y(yy + 3f).toInt(), R.flame[1]) }
        }
        val body = pen.path().m(50f, 43f).c(63f, 44f, 67f, 62f, 64f, 76f).q(50f, 86f, 36f, 76f).c(33f, 62f, 37f, 44f, 50f, 43f).shape()
        px.shade(body, pen.dome(50f, 62f, 15f, 20f), R.flame, texture = feathers(0.16f), rim = R.flame[5], lo = 0, hi = 5)
        px.shade(pen.ell(50f, 66f, 8.5f, 11.5f), pen.dome(50f, 64f, 8.5f, 11.5f), R.ember, outline = false, texture = feathers(0.12f), lo = 2)
        for (s in listOf(-1f, 1f)) {
            val lx = pen.x(50f + 4.5f * s).toInt()
            px.vline(lx, pen.y(81f).toInt(), pen.y(87.5f).toInt(), R.beak[2])
            px.hline(lx - 1, lx + 1, pen.y(88f).toInt(), R.beak[1]); px.set(lx + if (s > 0) 2 else -2, pen.y(88f).toInt(), R.beak[1])
        }
        for (i in 0 until 5) {
            val bx = 44f + i * 3f
            val fl = if (alive) sin(ctx.ph * 4 * PI.toFloat() + i) * 1.5f else 0f
            px.shade(flameShape(pen, bx, 28f, 40f + i * 5f + fl, 11f + abs(i - 2) * 3.5f, 2f), facing(-0.2f, -0.3f, 1f), R.flame, rim = R.flame[6], lo = 2)
        }
        px.shade(pen.ell(50f, 35.5f, 11.5f, 11f), pen.dome(50f, 34f, 11.5f, 11f), R.flame, texture = feathers(0.1f), rim = R.flame[6], lo = 2)
        px.shade(pen.path().m(47.4f, 38.5f).l(52.6f, 38.5f).l(50f, 45f).l(47.4f, 38.5f).shape(), pen.dome(49f, 40f, 3f, 4f), R.beak, spec = true)
        for (s in listOf(-1f, 1f)) { pen.set(50f + 7.5f * s, 39.5f, R.gold[5]); pen.set(50f + 8.5f * s, 40.5f, R.gold[4]) }
        return face(px, pen, ctx, listOf(45.5f to 35f, 54.5f to 35f), 3f, R.gold[4], R.flame[4], 50f to 35.5f, 11.5f)
    }

    /* ---------- Griffin: eagle in front, lion behind ---------- */

    private fun griffin(pen: Pen, ctx: Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        val ws = when (st) { Stage.BABY -> 0.62f; Stage.YOUNG -> 0.8f; Stage.ADULT -> 1f; else -> 1.12f }
        val flap = ctx.sw * 4f
        for (m in listOf(false, true)) {
            val deg = if (m) -flap else flap
            val pvx = if (m) 60f else 40f
            fun wx(v: Float) = 40f + (v - 40f) * ws
            fun wy(v: Float) = 54f + (v - 54f) * ws
            val wing = pen.path(deg, pvx, 54f, m).m(wx(40f), wy(54f)).c(wx(30f), wy(36f), wx(18f), wy(20f), wx(5f), wy(13f)).c(wx(9f), wy(28f), wx(7f), wy(40f), wx(11f), wy(48f)).q(wx(17f), wy(48f), wx(17f), wy(54f)).q(wx(23f), wy(54f), wx(25f), wy(60f)).q(wx(31f), wy(60f), wx(34f), wy(65f)).l(wx(40f), wy(54f)).shape()
            px.shade(wing, pen.turnedDome(wx(22f), wy(40f), 18f * ws, 22f * ws, deg, pvx, 54f), R.brown, texture = feathers(0.18f), rim = R.brown[5])
            // Long flight feathers along the lower edge.
            for (k in 0 until 4) {
                val fx = wx(10f + k * 6.5f); val fy = wy(46f + k * 4.5f)
                val feather = pen.path(deg, pvx, 54f, m).m(fx, fy).l(fx - 3f * ws, fy + 7f * ws).l(fx + 2f * ws, fy + 5f * ws).l(fx + 4f * ws, fy).l(fx, fy).shape()
                px.shade(feather, facing(if (m) 0.3f else -0.3f, 0.2f, 1f), R.brown, lo = 2, hi = 5)
            }
        }
        val tail = pen.path().m(64f, 82f).c(78f, 86f, 86f, 78f, 84f, 68f).l(86.5f, 68f).c(89f, 80f, 79f, 90f, 64f, 87f).l(64f, 82f).shape()
        px.shade(tail, pen.dome(76f, 80f, 12f, 8f), R.tan, texture = fur(14), rim = R.tan[5])
        px.shade(pen.path().m(85f, 60f).q(91f, 65f, 87f, 71f).q(81f, 66f, 85f, 60f).shape(), pen.dome(86f, 66f, 3.5f, 5f), R.brown, texture = fur(15, 0.25f))
        val body = pen.path().m(50f, 48f).c(64f, 49f, 71f, 68f, 70f, 86f).q(50f, 90f, 30f, 86f).c(29f, 68f, 36f, 49f, 50f, 48f).shape()
        px.shade(body, pen.dome(50f, 67f, 21f, 21f), R.tan, texture = fur(11), rim = R.tan[5])
        for (lx in listOf(41f, 59f)) {
            px.shade(pen.box(lx - 3.8f, 68f, lx + 3.8f, 86f), pen.column(lx, 3.8f), R.tan, texture = fur(12, 0.1f), lo = 1)
            px.shade(pen.ell(lx, 86f, 4.8f, 2.8f), pen.dome(lx, 85f, 4.8f, 2.8f), R.tan, lo = 2)
            for (c in listOf(-1.6f, 1.6f)) px.vline(pen.x(lx + c).toInt(), pen.y(85f).toInt(), pen.y(88f).toInt(), R.tan[1])
        }
        val ruff = pen.path().m(36f, 46f).l(39f, 53f).l(41f, 50f).l(43f, 57f).l(45.5f, 53f).l(47.5f, 61f).l(50f, 56f).l(52.5f, 61f).l(54.5f, 53f).l(57f, 57f).l(59f, 50f).l(61f, 53f).l(64f, 46f).l(36f, 46f).shape()
        px.shade(ruff, pen.dome(50f, 50f, 14f, 9f), R.snow, texture = feathers(0.12f), lo = 1)
        for (m in listOf(false, true)) px.shade(pen.path(mirror = m).m(39f, 30f).l(31f, 21f).l(36f, 30f).l(33f, 33f).l(41f, 34f).l(39f, 30f).shape(), facing(if (m) 0.4f else -0.4f, -0.3f, 1f), R.brown, lo = 2)
        px.shade(pen.ell(50f, 35f, 13.2f, 12.6f), pen.dome(50f, 33f, 13.2f, 12.6f), R.snow, texture = feathers(0.1f), rim = R.snow[4], lo = 1)
        for (ex in listOf(44f, 56f)) px.shade(pen.ell(ex, 34f, 4.6f, 4.6f), facing(), R.beak, outline = false, lo = 2, hi = 3)
        val beak = pen.path().m(45f, 38.5f).q(50f, 36f, 55f, 38.5f).q(54.5f, 44f, 50f, 48f).q(49f, 44f, 45f, 38.5f).shape()
        px.shade(beak, pen.dome(49f, 40f, 5f, 6f), R.beak, spec = true)
        px.hline(pen.x(46.5f).toInt(), pen.x(53.5f).toInt(), pen.y(41.5f).toInt(), R.beak[0])
        val f = face(px, pen, ctx, listOf(44f to 34f, 56f to 34f), 3.3f, R.gold[3], R.snow[4], 50f to 35f, 13f)
        // Fierce brows over the eyes.
        if (ctx.eyes != EyeState.CLOSED) for (s in listOf(-1, 1)) {
            val (ex, ey) = f.eyes[if (s < 0) 0 else 1]
            val bw = f.eyeW / 2 + 1
            for (i in 0..bw * 2) px.set(ex - bw + i, ey - f.eyeW / 2 - 2 + if (s < 0) i / 3 - 1 else (bw * 2 - i) / 3 - 1, R.brown[1])
        }
        return f
    }

    /* ---------- Owlbear: a bear with an owl's face and a book ---------- */

    private fun owlbear(pen: Pen, ctx: Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        if (st == Stage.ANCIENT) {
            val cape = pen.path().m(50f, 45f).c(75f, 45f, 85f, 70f, 83f, 88f).l(17f, 88f).c(15f, 70f, 25f, 45f, 50f, 45f).shape()
            px.shade(cape, pen.dome(50f, 68f, 34f, 24f), R.violet, texture = { x, _ -> if (x % 5 == 0) -0.15f else 0f }, rim = R.violet[5])
        }
        val body = pen.path().m(50f, 44f).c(70f, 44f, 78f, 64f, 76f, 82f).q(50f, 94f, 24f, 82f).c(22f, 64f, 30f, 44f, 50f, 44f).shape()
        px.shade(body, pen.dome(50f, 66f, 27f, 24f), R.owl, texture = fur(21), rim = R.owl[5])
        px.shade(pen.ell(50f, 75f, 15f, 12f), pen.dome(50f, 72f, 15f, 12f), R.disc, outline = false, texture = feathers(0.2f), lo = 1)
        for (fx in listOf(38f, 62f)) {
            px.shade(pen.ell(fx, 86.5f, 7f, 3.4f), pen.dome(fx, 85.5f, 7f, 3.4f), R.owl, lo = 0, hi = 4)
            for (c in -1..1) pen.set(fx + c * 3f, 88.5f, R.horn[3])
        }
        // The book, read with great attention.
        val turn = if (ctx.f in 4..7) 1f else 0f
        px.shade(pen.box(35.5f, 63.5f, 64.5f, 79.5f), facing(0f, 0.2f, 1f), R.red, lo = 0, hi = 3)
        for (m in listOf(false, true)) {
            val page = pen.path(mirror = m).m(37.5f, 65f + turn).q(44f, 62.5f, 50f, 65.5f).l(50f, 78f).q(44f, 75.5f, 37.5f, 78f).l(37.5f, 65f + turn).shape()
            px.shade(page, facing(if (m) 0.3f else -0.3f, -0.2f, 1f), R.paper, lo = 2)
            for (k in 0 until 4) {
                val yy = 67.5f + k * 2.5f
                px.hline(pen.x(if (m) 52.5f else 40f).toInt(), pen.x(if (m) 60f else 47.5f).toInt(), pen.y(yy).toInt(), R.paper[1])
            }
        }
        px.vline(pen.x(50f).toInt(), pen.y(65.5f).toInt(), pen.y(78f).toInt(), R.paper[0])
        for (ax in listOf(36f, 64f)) px.shade(pen.ell(ax, 72f, 5.2f, 6.6f), pen.dome(ax, 71f, 5.2f, 6.6f), R.owl, texture = fur(23, 0.1f), rim = R.owl[5])
        for (m in listOf(false, true)) px.shade(pen.path(mirror = m).m(34f, 26f).l(28f, 8f).l(36f, 16f).l(37f, 11f).l(43f, 21f).l(34f, 26f).shape(), pen.dome(if (m) 64f else 36f, 18f, 7f, 10f), R.owl, texture = feathers(0.2f), lo = 0, hi = 5)
        px.shade(pen.ell(50f, 34f, 19.5f, 17f), pen.dome(50f, 32f, 19.5f, 17f), R.owl, texture = feathers(0.14f), rim = R.owl[5])
        for (dx in listOf(43f, 57f)) px.shade(pen.ell(dx, 35f, 8.6f, 8.3f), pen.dome(dx, 34f, 8.6f, 8.3f, 0.4f), R.disc, texture = { x, y -> if ((x + y) % 4 == 0) -0.1f else 0f }, lo = 1)
        for (dx in listOf(43f, 57f)) px.shade(pen.ell(dx, 35f, 5.6f, 5.6f), facing(), R.beak, outline = false, lo = 2, hi = 3)
        px.shade(pen.path().m(47.6f, 40.5f).l(52.4f, 40.5f).l(50f, 46f).l(47.6f, 40.5f).shape(), pen.dome(49.5f, 42f, 3f, 4f), R.beak, spec = true)
        if (st == Stage.ANCIENT) px.shade(pen.ell(50f, 51f, 2.6f, 2.6f), pen.dome(50f, 50f, 2.6f, 2.6f), R.gold, spec = true)
        return face(px, pen, ctx, listOf(43f to 35f, 57f to 35f), 4.4f, R.gold[3], R.disc[3], 50f to 34f, 19f)
    }

    /* ---------- Moth: wings that fill with the stars of reading nights ---------- */

    private fun moth(pen: Pen, ctx: Ctx): Face {
        val px = pen.px
        if (ctx.look.stone < 0.5f) px.glow(pen.x(50f), pen.y(50f), pen.r(46f), R.lilac[5], 55, 5)
        val flap = if (ctx.look.sleeping) 0.92f else 0.86f + 0.14f * ctx.sw
        fun wx(v: Float) = 50f + (v - 50f) * flap
        val stars = ctx.look.stars.coerceAtMost(40)
        for (m in listOf(false, true)) {
            val lower = pen.path(mirror = m).m(wx(48f), 54f).c(wx(34f), 58f, wx(20f), 66f, wx(22f), 78f).c(wx(28f), 86f, wx(40f), 78f, wx(48f), 62f).l(wx(48f), 54f).shape()
            px.shade(lower, pen.dome(if (m) 100f - wx(33f) else wx(33f), 68f, 13f * flap, 12f), R.lilac, rim = R.lilac[5], lo = 1, hi = 5)
            val tailTip = pen.path(mirror = m).m(wx(25f), 76f).l(wx(20f), 86f).l(wx(29f), 80f).l(wx(25f), 76f).shape()
            px.shade(tailTip, facing(), R.lilac, lo = 2, hi = 4)
            val upper = pen.path(mirror = m).m(wx(48f), 46f).c(wx(36f), 28f, wx(18f), 17f, wx(7f), 21f).c(wx(3f), 34f, wx(12f), 50f, wx(26f), 56f).q(wx(38f), 58f, wx(48f), 54f).l(wx(48f), 46f).shape()
            val um = px.shade(upper, pen.dome(if (m) 100f - wx(27f) else wx(27f), 38f, 21f * flap, 18f), R.night, dither = 0.7f, rim = R.lilac[4], lo = 1)
            // Pale border and an eyespot.
            val inner = pen.path(mirror = m).m(wx(46f), 47f).c(wx(36f), 31f, wx(20f), 21f, wx(10f), 24f).c(wx(7f), 35f, wx(14f), 48f, wx(26f), 53f).q(wx(38f), 55f, wx(46f), 52f).l(wx(46f), 47f).shape()
            val im = px.cover(inner)
            for (i in um.indices) if (um[i] && !im[i] && px.c[i] and 0xFFFFFF != R.night[0] and 0xFFFFFF) px.c[i] = (0xFF shl 24) or (R.lilac[4] and 0xFFFFFF)
            val sx = if (m) 100f - wx(22f) else wx(22f)
            px.shade(pen.ell(sx, 34f, 4.6f, 4.6f), pen.dome(sx, 33f, 4.6f, 4.6f), R.lilac, lo = 3, line = R.night[1])
            px.shade(pen.ell(sx, 34f, 2f, 2f), facing(), R.night, outline = false, lo = 1, hi = 2)
            // The stars of reading nights.
            for (i in (if (m) 1 else 0) until stars step 2) {
                val ang = (noise(i, 7, 31) + 0.5f) * 2 * PI
                val rr = kotlin.math.sqrt(noise(i, 9, 37) + 0.5f) * 11f
                val cx = 27f + kotlin.math.cos(ang).toFloat() * rr * 1.2f
                val cy = 40f + sin(ang).toFloat() * rr * 0.8f
                val xx = pen.x(if (m) 100f - wx(cx) else wx(cx)).toInt()
                val yy = pen.y(cy).toInt()
                if (xx !in 0 until px.w || yy !in 0 until px.h || !im[yy * px.w + xx]) continue
                val bright = (i + ctx.f) % 5 != 0
                px.set(xx, yy, if (bright) R.gold[5] else R.gold[3])
                if (i % 4 == 0 && bright) { px.set(xx - 1, yy, R.gold[3], 160); px.set(xx + 1, yy, R.gold[3], 160); px.set(xx, yy - 1, R.gold[3], 160); px.set(xx, yy + 1, R.gold[3], 160) }
            }
        }
        px.shade(pen.ell(50f, 61f, 7f, 16f), pen.dome(50f, 58f, 7f, 16f), R.fluff, texture = { _, y -> if (y % 3 == 0) -0.22f else 0f }, rim = R.fluff[4])
        for (i in 0 until 9) {
            val a = (190f + i * 20f) * PI / 180
            val cx = 50f + kotlin.math.cos(a).toFloat() * 9f; val cy = 46f - sin(a).toFloat() * 3.5f
            px.shade(pen.ell(cx, cy, 3.2f, 3.2f), pen.dome(cx, cy - 1f, 3.2f, 3.2f), R.fluff, outline = false, lo = 2)
        }
        for (m in listOf(false, true)) {
            fun ax(v: Float) = if (m) 100f - v else v
            val sway = if (ctx.f % 6 < 3) 0f else 1f
            val tip = ax(35f + sway) to 10f
            val base = ax(47f) to 30f
            px.line(pen.x(base.first).toInt(), pen.y(base.second).toInt(), pen.x(tip.first).toInt(), pen.y(tip.second).toInt(), R.lilac[1])
            for (k in 1..5) {
                val qx = base.first + (tip.first - base.first) * k / 6f; val qy = base.second + (tip.second - base.second) * k / 6f
                val s = if (m) 1f else -1f
                pen.set(qx + s * 2f, qy + 0.5f, R.lilac[3]); pen.set(qx - s * 1.5f, qy - 1f, R.lilac[3])
            }
        }
        px.shade(pen.ell(50f, 37f, 9.5f, 9f), pen.dome(50f, 35f, 9.5f, 9f), R.fluff, texture = fur(41, 0.1f), rim = R.fluff[4], lo = 1)
        return face(px, pen, ctx, listOf(46f to 37f, 54f to 37f), 3.2f, R.night[3], R.fluff[3], 50f to 37f, 9.5f)
    }

    /** All twelve frames for [look]. */
    fun frames(look: Look): List<Px> = List(FRAMES) { frame(look, it) }
}
