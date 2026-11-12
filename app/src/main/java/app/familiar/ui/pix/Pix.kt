package app.familiar.ui.pix

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * A small pixel-art toolkit: shapes lit through their surface normals with hue-shifted colour ramps,
 * ordered dither between tones, selective outlines and rim light. Sprites are painted on the phone,
 * so a familiar can grow a new tail or a new star without shipping a picture for every case.
 */

fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
fun Int.r() = (this shr 16) and 0xFF
fun Int.g() = (this shr 8) and 0xFF
fun Int.b() = this and 0xFF
fun Int.al() = (this ushr 24) and 0xFF

fun mixc(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    return rgb(
        (a.r() + (b.r() - a.r()) * k).roundToInt(),
        (a.g() + (b.g() - a.g()) * k).roundToInt(),
        (a.b() + (b.b() - a.b()) * k).roundToInt(),
    )
}

fun lum(c: Int): Float = (0.3f * c.r() + 0.59f * c.g() + 0.11f * c.b()) / 255f

/** Dark to light, each tone shifted in hue (shadows toward plum, lights toward warm). */
class Ramp(vararg val c: Int) {
    val size get() = c.size
    operator fun get(i: Int) = c[i.coerceIn(0, c.size - 1)]
    val darkest get() = c[0]
    val lightest get() = c[c.size - 1]
}

/** The darkest outline: deep plum, never pure black. */
val OUT = rgb(26, 14, 34)
val WHITE = rgb(255, 252, 244)
val EYE = rgb(30, 18, 42)

object R {
    val fox = Ramp(rgb(58, 18, 36), rgb(108, 32, 38), rgb(162, 54, 36), rgb(210, 88, 38), rgb(238, 132, 54), rgb(250, 180, 96), rgb(255, 224, 160))
    val cream = Ramp(rgb(92, 58, 74), rgb(146, 108, 110), rgb(196, 160, 148), rgb(228, 202, 178), rgb(246, 232, 208), rgb(255, 250, 236))
    val wood = Ramp(rgb(42, 20, 32), rgb(76, 36, 38), rgb(116, 60, 44), rgb(156, 92, 56), rgb(196, 132, 78), rgb(226, 176, 116), rgb(246, 214, 160))
    val gold = Ramp(rgb(70, 34, 30), rgb(128, 72, 30), rgb(186, 120, 38), rgb(228, 170, 56), rgb(250, 214, 100), rgb(255, 244, 186))
    val iron = Ramp(rgb(30, 26, 44), rgb(56, 52, 74), rgb(90, 88, 110), rgb(132, 132, 150), rgb(182, 184, 196), rgb(226, 230, 236))
    val mouth = Ramp(rgb(26, 8, 22), rgb(52, 16, 34), rgb(84, 26, 46), rgb(120, 40, 60))
    val tongue = Ramp(rgb(96, 22, 52), rgb(164, 52, 82), rgb(218, 96, 120), rgb(244, 150, 166), rgb(255, 204, 210))
    val slime = Ramp(rgb(14, 48, 52), rgb(22, 88, 70), rgb(40, 134, 82), rgb(78, 180, 98), rgb(132, 214, 120), rgb(196, 238, 160), rgb(236, 252, 214))
    val teal = Ramp(rgb(12, 34, 48), rgb(18, 66, 74), rgb(28, 104, 98), rgb(46, 144, 116), rgb(86, 182, 132), rgb(150, 214, 158), rgb(214, 240, 196))
    val belly = Ramp(rgb(104, 62, 46), rgb(162, 112, 66), rgb(210, 166, 92), rgb(238, 208, 130), rgb(252, 236, 180), rgb(255, 252, 226))
    val violet = Ramp(rgb(30, 14, 52), rgb(58, 26, 90), rgb(92, 44, 132), rgb(130, 72, 172), rgb(170, 112, 206), rgb(206, 160, 232), rgb(236, 210, 250))
    val horn = Ramp(rgb(90, 66, 70), rgb(150, 122, 110), rgb(200, 178, 150), rgb(234, 220, 190), rgb(252, 246, 224))
    val flame = Ramp(rgb(84, 14, 30), rgb(146, 26, 34), rgb(202, 50, 34), rgb(236, 92, 36), rgb(252, 148, 48), rgb(255, 204, 90), rgb(255, 244, 180))
    val ember = Ramp(rgb(110, 30, 40), rgb(176, 56, 38), rgb(226, 104, 46), rgb(250, 160, 64), rgb(255, 214, 120), rgb(255, 248, 204))
    val tan = Ramp(rgb(68, 36, 34), rgb(118, 70, 46), rgb(168, 112, 62), rgb(206, 154, 86), rgb(232, 192, 122), rgb(248, 224, 168), rgb(255, 244, 214))
    val brown = Ramp(rgb(34, 18, 26), rgb(62, 32, 34), rgb(96, 54, 42), rgb(132, 82, 54), rgb(168, 114, 72), rgb(204, 152, 100), rgb(232, 194, 144))
    val snow = Ramp(rgb(70, 64, 92), rgb(122, 118, 142), rgb(172, 170, 188), rgb(212, 212, 222), rgb(238, 238, 242), rgb(255, 255, 255))
    val beak = Ramp(rgb(102, 52, 26), rgb(170, 100, 28), rgb(222, 150, 36), rgb(248, 196, 68), rgb(255, 232, 140))
    val owl = Ramp(rgb(40, 22, 28), rgb(70, 40, 38), rgb(104, 64, 48), rgb(140, 94, 62), rgb(176, 128, 84), rgb(210, 170, 118), rgb(236, 210, 164))
    val disc = Ramp(rgb(120, 86, 76), rgb(176, 140, 112), rgb(218, 190, 150), rgb(240, 222, 186), rgb(252, 244, 220))
    val night = Ramp(rgb(16, 12, 42), rgb(28, 24, 74), rgb(44, 40, 112), rgb(66, 62, 150), rgb(98, 90, 188), rgb(140, 128, 220), rgb(190, 178, 244))
    val lilac = Ramp(rgb(52, 36, 92), rgb(86, 64, 140), rgb(124, 100, 186), rgb(160, 140, 220), rgb(196, 182, 240), rgb(226, 218, 252), rgb(248, 246, 255))
    val fluff = Ramp(rgb(92, 82, 122), rgb(150, 140, 178), rgb(200, 192, 222), rgb(232, 226, 246), rgb(248, 246, 255), rgb(255, 255, 255))
    val red = Ramp(rgb(60, 12, 36), rgb(116, 24, 46), rgb(172, 42, 54), rgb(218, 84, 70), rgb(246, 140, 108), rgb(255, 196, 168))
    val paper = Ramp(rgb(120, 96, 84), rgb(176, 152, 126), rgb(222, 204, 172), rgb(242, 230, 204), rgb(255, 250, 236))
    val glass = Ramp(rgb(40, 70, 110), rgb(80, 130, 170), rgb(150, 200, 225), rgb(220, 245, 255))
    val pink = Ramp(rgb(104, 36, 78), rgb(176, 72, 116), rgb(232, 128, 162), rgb(252, 192, 206))
    val sky = Ramp(rgb(16, 40, 80), rgb(30, 76, 130), rgb(54, 124, 182), rgb(100, 176, 226), rgb(168, 220, 250), rgb(228, 248, 255))
    val stone = Ramp(rgb(40, 38, 50), rgb(66, 64, 78), rgb(98, 96, 108), rgb(132, 130, 138), rgb(166, 164, 168), rgb(200, 198, 196), rgb(228, 226, 220))
    val ash = Ramp(rgb(22, 18, 22), rgb(40, 34, 38), rgb(62, 56, 58), rgb(86, 80, 80), rgb(112, 106, 104), rgb(140, 134, 130))
    val tanuki = Ramp(rgb(44, 30, 34), rgb(80, 58, 52), rgb(118, 90, 70), rgb(154, 124, 94), rgb(188, 160, 124), rgb(216, 194, 158), rgb(238, 224, 196))
    val root = Ramp(rgb(92, 50, 52), rgb(146, 92, 78), rgb(196, 140, 108), rgb(228, 180, 140), rgb(244, 212, 174), rgb(252, 236, 208))
    val leaf = Ramp(rgb(14, 36, 40), rgb(22, 66, 54), rgb(38, 102, 60), rgb(70, 140, 66), rgb(118, 178, 82), rgb(176, 210, 112), rgb(226, 236, 160))

    /** A seven-tone ramp around a base colour (a genre colour for a tail, a book, a fruit). */
    fun around(base: Int): Ramp {
        val dark = rgb(30, 14, 40)
        val warm = rgb(255, 246, 214)
        return Ramp(mixc(base, dark, 0.78f), mixc(base, dark, 0.56f), mixc(base, dark, 0.32f), mixc(base, dark, 0.12f), base, mixc(base, warm, 0.35f), mixc(base, warm, 0.68f))
    }
}

private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
fun bayer(x: Int, y: Int): Float = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f - 0.5f

/** Cheap hash noise, -0.5..0.5, stable for a pixel. */
fun noise(x: Int, y: Int, salt: Int = 0): Float {
    var h = x * 374761393 + y * 668265263 + salt * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f - 0.5f
}

class V3(val x: Float, val y: Float, val z: Float)

/** Light from the top left and a little in front. */
private val LIGHT = run {
    val x = -0.55f; val y = -0.75f; val z = 0.65f
    val n = sqrt(x * x + y * y + z * z)
    V3(x / n, y / n, z / n)
}

fun dot(a: V3, b: V3) = a.x * b.x + a.y * b.y + a.z * b.z

/** A region in pixel space: which pixels a shape covers, and its bounds for speed. */
class Shape(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val inside: (Float, Float) -> Boolean)

fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = Shape(
    (cx - rx - 1).toInt(), (cy - ry - 1).toInt(), (cx + rx + 1).toInt(), (cy + ry + 1).toInt(),
) { x, y -> ((x - cx) / rx).let { it * it } + ((y - cy) / ry).let { it * it } <= 1f }

fun rect(x: Float, y: Float, w: Float, h: Float) = Shape(x.toInt() - 1, y.toInt() - 1, (x + w).toInt() + 1, (y + h).toInt() + 1) { px, py ->
    px >= x && px < x + w && py >= y && py < y + h
}

fun poly(vararg p: Float): Shape {
    val xs = p.filterIndexed { i, _ -> i % 2 == 0 }
    val ys = p.filterIndexed { i, _ -> i % 2 == 1 }
    return Shape(xs.min().toInt() - 1, ys.min().toInt() - 1, xs.max().toInt() + 1, ys.max().toInt() + 1) { x, y ->
        var inside = false
        var j = xs.size - 1
        for (i in xs.indices) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i] + 1e-6f) + xs[i]) inside = !inside
            j = i
        }
        inside
    }
}

/** Points along a cubic Bézier, for building smooth outlines as polygons. */
fun bez(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, n: Int = 10): FloatArray {
    val out = FloatArray(n * 2)
    for (i in 0 until n) {
        val t = (i + 1) / n.toFloat()
        val u = 1 - t
        out[i * 2] = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
        out[i * 2 + 1] = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
    }
    return out
}

fun union(vararg s: Shape) = Shape(s.minOf { it.x0 }, s.minOf { it.y0 }, s.maxOf { it.x1 }, s.maxOf { it.y1 }) { x, y -> s.any { it.inside(x, y) } }
fun minus(a: Shape, b: Shape) = Shape(a.x0, a.y0, a.x1, a.y1) { x, y -> a.inside(x, y) && !b.inside(x, y) }
fun and(a: Shape, b: Shape) = Shape(max(a.x0, b.x0), max(a.y0, b.y0), min(a.x1, b.x1), min(a.y1, b.y1)) { x, y -> a.inside(x, y) && b.inside(x, y) }

/** Normal of an ellipsoid (a ball, a belly, a head). */
fun dome(cx: Float, cy: Float, rx: Float, ry: Float, flat: Float = 0f): (Float, Float) -> V3 = { x, y ->
    val dx = ((x - cx) / rx).coerceIn(-1f, 1f)
    val dy = ((y - cy) / ry).coerceIn(-1f, 1f)
    val z = sqrt(max(0f, 1 - dx * dx - dy * dy)) + flat
    val n = sqrt(dx * dx + dy * dy + z * z)
    V3(dx / n, dy / n, z / n)
}

fun facing(nx: Float = 0f, ny: Float = 0f, nz: Float = 1f): (Float, Float) -> V3 {
    val m = sqrt(nx * nx + ny * ny + nz * nz)
    val v = V3(nx / m, ny / m, nz / m)
    return { _, _ -> v }
}

/** A vertical cylinder's side (a leg, a candle, a book spine). */
fun column(cx: Float, rx: Float, tilt: Float = 0f): (Float, Float) -> V3 = { x, _ ->
    val dx = ((x - cx) / rx).coerceIn(-1f, 1f)
    val z = sqrt(max(0f, 1 - dx * dx))
    V3(dx, tilt, z)
}

/** A horizontal cylinder (a book lying flat, a branch). */
fun log(cy: Float, ry: Float): (Float, Float) -> V3 = { _, y ->
    val dy = ((y - cy) / ry).coerceIn(-1f, 1f)
    V3(0f, dy, sqrt(max(0f, 1 - dy * dy)))
}

/** How much finer than a [Px]'s own grid its pictures are kept: drawn art shows at this many pixels per pixel. */
const val DETAIL = 2

/**
 * A canvas of [w] x [h] pixels. It keeps a copy [DETAIL] times finer ([fine]): whatever is painted here by
 * code lands on both, as blocks in the fine one, while drawn pictures laid on with [blit] keep their own
 * finer pixels there. Reading ([get], [has], [c]) is on the coarse grid, so all the drawing code stays the same.
 */
class Px(val w: Int, val h: Int) {
    val c = IntArray(w * h)
    val fw = w * DETAIL
    val fh = h * DETAIL
    val fine = IntArray(fw * fh)

    operator fun get(x: Int, y: Int): Int = if (x in 0 until w && y in 0 until h) c[y * w + x] else 0
    fun has(x: Int, y: Int) = get(x, y).al() > 0

    private fun mixed(base: Int, color: Int, alpha: Int): Int {
        if (alpha >= 255 || base.al() == 0) return (min(255, if (base.al() == 0) alpha else 255) shl 24) or (color and 0xFFFFFF)
        val m = mixc(base, color, alpha / 255f)
        return (max(base.al(), alpha) shl 24) or (m and 0xFFFFFF)
    }

    /** The fine pixels under coarse pixel [i]. */
    inline fun under(i: Int, f: (Int) -> Unit) {
        val x = (i % w) * DETAIL; val y = (i / w) * DETAIL
        for (dy in 0 until DETAIL) for (dx in 0 until DETAIL) f((y + dy) * fw + x + dx)
    }

    fun set(x: Int, y: Int, color: Int, alpha: Int = 255) {
        if (x !in 0 until w || y !in 0 until h || alpha <= 0) return
        val i = y * w + x
        c[i] = mixed(c[i], color, alpha)
        under(i) { j -> fine[j] = mixed(fine[j], color, alpha) }
    }

    /** Pixel [i] becomes [color] outright, alpha and all, on both grids. */
    fun put(i: Int, color: Int) {
        c[i] = color
        under(i) { j -> fine[j] = color }
    }

    /** Pixel [i] is turned by [f], on both grids: each fine pixel under it by the same rule. */
    inline fun map(i: Int, f: (Int) -> Int) {
        c[i] = f(c[i])
        under(i) { j -> fine[j] = f(fine[j]) }
    }

    /** The part [x0], [y0], [cw] x [ch] of this, as its own canvas. */
    fun crop(x0: Int, y0: Int, cw: Int, ch: Int): Px {
        val out = Px(cw, ch)
        for (y in 0 until ch) for (x in 0 until cw) out.c[y * cw + x] = this[x0 + x, y0 + y]
        for (y in 0 until ch * DETAIL) for (x in 0 until cw * DETAIL) {
            val sx = x0 * DETAIL + x; val sy = y0 * DETAIL + y
            if (sx in 0 until fw && sy in 0 until fh) out.fine[y * out.fw + x] = fine[sy * fw + sx]
        }
        return out
    }

    /** Fills the fine copy from the coarse one, a block per pixel: for a picture loaded without a finer one. */
    fun coarsen() { for (i in c.indices) under(i) { j -> fine[j] = c[i] } }

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int, alpha: Int = 255) {
        for (j in y until y + h) for (i in x until x + w) set(i, j, color, alpha)
    }

    fun hline(x0: Int, x1: Int, y: Int, color: Int) { for (i in x0..x1) set(i, y, color) }
    fun vline(x: Int, y0: Int, y1: Int, color: Int) { for (j in y0..y1) set(x, j, color) }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, alpha: Int = 255) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(x, y, color, alpha)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    /** Pixels of [s], as a set of indices. */
    fun cover(s: Shape): BooleanArray {
        val m = BooleanArray(w * h)
        for (y in max(0, s.y0)..min(h - 1, s.y1)) for (x in max(0, s.x0)..min(w - 1, s.x1)) {
            if (s.inside(x + 0.5f, y + 0.5f)) m[y * w + x] = true
        }
        return m
    }

    /**
     * Fills [s] lit by [normal]: tones from [ramp] with ordered dither between them, an optional
     * texture (fur, feathers) nudging the light, a rim of light on the shadow side and a selective
     * outline (darkest tone, softer on lit edges). Returns the covered pixels.
     */
    fun shade(
        s: Shape,
        normal: (Float, Float) -> V3,
        ramp: Ramp,
        outline: Boolean = true,
        dither: Float = 0.45f,
        ambient: Float = 0.2f,
        texture: ((Int, Int) -> Float)? = null,
        spec: Boolean = false,
        rim: Int? = null,
        lo: Int = 0,
        hi: Int = ramp.size - 1,
        line: Int? = null,
    ): BooleanArray {
        val m = cover(s)
        for (y in max(0, s.y0)..min(h - 1, s.y1)) for (x in max(0, s.x0)..min(w - 1, s.x1)) {
            if (!m[y * w + x]) continue
            val n = normal(x + 0.5f, y + 0.5f)
            val d = dot(n, LIGHT)
            var l = max(0f, d) * (1 - ambient) + ambient
            if (texture != null) l += texture(x, y)
            // A gamma curve keeps the top tone for real highlights and gives shadows room.
            val g = Math.pow(l.coerceIn(0f, 1f).toDouble(), 1.35).toFloat()
            val v = lo + g * (hi - lo - 0.45f) + bayer(x, y) * dither
            var col = ramp[v.roundToInt().coerceIn(lo, hi)]
            if (spec && d > 0.95f) col = mixc(ramp.lightest, WHITE, 0.5f)
            set(x, y, col)
        }
        if (outline) {
            val dark = line ?: ramp.darkest
            for (y in max(0, s.y0)..min(h - 1, s.y1)) for (x in max(0, s.x0)..min(w - 1, s.x1)) {
                if (!m[y * w + x]) continue
                val r = x + 1 >= w || !m[y * w + x + 1]
                val l = x == 0 || !m[y * w + x - 1]
                val b = y + 1 >= h || !m[(y + 1) * w + x]
                val t = y == 0 || !m[(y - 1) * w + x]
                if (!(r || l || b || t)) continue
                val lit = (t || l) && !(r || b)
                set(x, y, if (lit) ramp[1] else dark)
                // Light bouncing back onto the shadow side, one pixel in.
                if (rim != null && (r || b) && !(t || l)) {
                    val ix = if (r) x - 1 else x
                    val iy = if (b) y - 1 else y
                    if (ix in 0 until w && iy in 0 until h && m[iy * w + ix]) set(ix, iy, rim, 130)
                }
            }
        }
        return m
    }

    /** A 1-pixel line around the outside of [m]. */
    fun ring(m: BooleanArray, color: Int) {
        val add = ArrayList<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            if (m[y * w + x]) continue
            if ((x > 0 && m[y * w + x - 1]) || (x < w - 1 && m[y * w + x + 1]) || (y > 0 && m[(y - 1) * w + x]) || (y < h - 1 && m[(y + 1) * w + x])) add += y * w + x
        }
        // On the coarse grid as always; on the fine one a line one fine pixel thick round what is drawn
        // there, so a drawn picture is lined at its own detail rather than in coarse blocks.
        val near = BooleanArray(w * h)
        add.forEach { c[it] = (0xFF shl 24) or (color and 0xFFFFFF); near[it] = true }
        for (i in m.indices) if (m[i]) near[i] = true
        val solid = BooleanArray(fine.size) { fine[it].al() >= 128 }
        for (y in 0 until fh) for (x in 0 until fw) {
            val j = y * fw + x
            if (solid[j] || !near[(y / DETAIL) * w + x / DETAIL]) continue
            if ((x > 0 && solid[j - 1]) || (x < fw - 1 && solid[j + 1]) || (y > 0 && solid[j - fw]) || (y < fh - 1 && solid[j + fw])) {
                fine[j] = (0xFF shl 24) or (color and 0xFFFFFF)
            }
        }
    }

    /** Soft light in stepped rings, over what's already drawn ([onlyDrawn]) or anywhere. */
    fun glow(cx: Float, cy: Float, r: Float, color: Int, strength: Int = 90, steps: Int = 4, onlyDrawn: Boolean = false) {
        for (y in (cy - r).toInt() - 1..(cy + r).toInt() + 1) for (x in (cx - r).toInt() - 1..(cx + r).toInt() + 1) {
            val d = hypot(x + 0.5f - cx, y + 0.5f - cy) / r
            if (d >= 1f) continue
            if (onlyDrawn && !has(x, y)) continue
            val step = kotlin.math.floor((1 - d) * steps + bayer(x, y) * 0.8f) / steps
            if (step > 0) set(x, y, color, (strength * step).toInt())
        }
    }

    /** Lays [o] over this at [ox], [oy]: each grid onto its own, so its fine pixels stay fine. */
    fun blit(o: Px, ox: Int, oy: Int) {
        for (y in 0 until o.h) for (x in 0 until o.w) {
            val p = o.c[y * o.w + x]
            val tx = ox + x; val ty = oy + y
            if (p.al() > 0 && tx in 0 until w && ty in 0 until h) c[ty * w + tx] = mixed(c[ty * w + tx], p, p.al())
        }
        for (y in 0 until o.fh) for (x in 0 until o.fw) {
            val p = o.fine[y * o.fw + x]
            val tx = ox * DETAIL + x; val ty = oy * DETAIL + y
            if (p.al() > 0 && tx in 0 until fw && ty in 0 until fh) fine[ty * fw + tx] = mixed(fine[ty * fw + tx], p, p.al())
        }
    }

    /** The fine copy, [DETAIL] times this canvas's size: what goes on screen. */
    fun bitmap(): ImageBitmap {
        val b = android.graphics.Bitmap.createBitmap(fw, fh, android.graphics.Bitmap.Config.ARGB_8888)
        b.setPixels(fine, 0, fw, 0, 0, fw, fh)
        return b.asImageBitmap()
    }
}
