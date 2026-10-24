package app.familiar.ui.pix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The ritual candle and the tree of stories, in the same pixels as the familiars. */
object Scenes {
    /** A candle with [left] (1 new .. 0 a stub) still to burn; the flame dances through [f]. */
    fun candle(left: Float, f: Int, lit: Boolean = true): Px {
        val px = Px(36, 72)
        val l = left.coerceIn(0f, 1f)
        val top = 64f - (8f + 40f * l)
        if (lit) px.glow(18f, top - 8f, 17f, R.gold[5], 60, 5)
        // Dish and handle.
        px.shade(ellipse(18f, 67f, 15f, 3.6f), dome(18f, 66f, 15f, 3.6f), R.gold, spec = true)
        px.shade(ellipse(31f, 61f, 3.6f, 3.6f), dome(31f, 60f, 3.6f, 3.6f), R.gold)
        px.shade(ellipse(31f, 61f, 1.8f, 1.8f), facing(), Ramp(0), outline = false)
        for (x in 0 until px.w) for (y in 0 until px.h) if (px[x, y] == (0xFF shl 24)) px.c[y * px.w + x] = 0
        // Wax with drips.
        px.shade(rect(11f, top, 14f, 64.5f - top), column(18f, 7f), R.cream, rim = R.cream[5], lo = 1)
        px.shade(ellipse(18f, top + 0.5f, 7f, 1.6f), facing(0f, -1f, 0.5f), R.cream, outline = false, lo = 3)
        for ((dx, len) in listOf(12f to 6f, 16f to 3f, 22f to 9f)) px.shade(rect(dx, top, 2f, len * (1.2f - l * 0.5f)), column(dx + 1f, 1f), R.cream, outline = false, lo = 3)
        px.vline(18, (top - 3).toInt(), top.toInt(), OUT)
        if (!lit) {
            for (j in 0 until 3) {
                val y = (top - 6 - ((f * 2 + j * 7) % 18)).toInt()
                px.set(18 + if ((f + j) % 2 == 0) 1 else 0, y, R.iron[3], 150)
            }
            return px
        }
        val sway = listOf(0f, 0.6f, 0f, -0.6f)[f % 4]
        val tall = listOf(11f, 12.5f, 10.5f, 12f)[f % 4]
        val fy = top - 2.5f
        val flame = poly(*(floatArrayOf(18f, fy + 1f) + bez(18f, fy + 1f, 13.5f, fy - 1f, 15f + sway, fy - tall * 0.6f, 18f + sway * 2, fy - tall) + bez(18f + sway * 2, fy - tall, 21f + sway, fy - tall * 0.6f, 22.5f, fy - 1f, 18f, fy + 1f)))
        px.shade(flame, dome(18f, fy - 3f, 3.5f, 6f), R.ember, outline = false, lo = 1)
        px.shade(ellipse(18f + sway, fy - 3.5f, 1.6f, 3f), facing(), Ramp(WHITE), outline = false)
        px.set(18, fy.toInt(), R.sky[3])
        return px
    }

    /** A gramophone for audiobooks: the record turns and notes float up while [playing]. */
    fun gramophone(f: Int, playing: Boolean = true): Px {
        val px = Px(72, 72)
        // Wooden cabinet.
        px.shade(rect(14f, 44f, 36f, 20f), { x, _ -> V3((x - 32f) / 24f, 0f, 1f) }, R.wood, lo = 0, hi = 5, texture = { x, _ -> if (x % 9 == 0) -0.25f else 0f })
        px.shade(rect(12f, 40f, 40f, 5f), facing(0f, -0.6f, 1f), R.wood, lo = 1, hi = 5)
        for (lx in listOf(15, 46)) px.rect(lx, 64, 3, 5, R.wood[1])
        px.shade(ellipse(32f, 54f, 5f, 4f), dome(31f, 53f, 5f, 4f), R.gold, spec = true)
        // The record, its shine turning.
        px.shade(ellipse(27f, 39f, 13f, 3.2f), facing(0f, -1f, 0.3f), R.iron, lo = 0, hi = 1)
        val a = (if (playing) f else 0) * PI.toFloat() / 2f
        for (k in 0 until 2) {
            val sx = (27 + kotlin.math.cos(a + k * PI.toFloat()) * 8).toInt()
            val sy = (39 + sin(a + k * PI.toFloat()) * 2).toInt()
            px.set(sx, sy, R.iron[4]); px.set(sx + 1, sy, R.iron[3])
        }
        px.set(27, 39, R.red[3]); px.set(28, 39, R.red[3])
        // Tone arm and the brass horn.
        px.line(44, 36, 30, 38, R.iron[3])
        px.line(44, 36, 48, 22, R.gold[2])
        val horn = poly(46f, 26f, 50f, 22f, 66f, 4f, 70f, 10f, 70f, 22f, 64f, 24f)
        px.shade(horn, { x, y -> V3((x - 62f) / 12f, (y - 14f) / 14f, 1f) }, R.gold, spec = true)
        px.shade(ellipse(67f, 13f, 3.5f, 8f), facing(-0.7f, 0f, 0.6f), R.gold, lo = 0, hi = 1)
        if (playing) for (i in 0 until 2) {
            val p = ((f + i * 2) % 4) / 4f
            val nx = (58 - p * 18 + i * 6).toInt()
            val ny = (8 - p * 6 + i * 4).toInt()
            px.vline(nx + 2, ny - 4, ny, R.lilac[5]); px.rect(nx, ny, 3, 2, R.lilac[5]); px.set(nx + 3, ny - 4, R.lilac[5])
        }
        val solid = BooleanArray(px.w * px.h) { px.c[it].al() >= 160 }
        px.ring(solid, OUT)
        return px
    }

    class Fruit(val index: Int, val x: Int, val y: Int)

    /**
     * The tree of stories: one glowing fruit for every finished book, in its genre's colour.
     * The crown fills out as books are added. Returns the picture and where each fruit hangs.
     */
    fun tree(fruits: List<Int>, f: Int, night: Boolean): Pair<Px, List<Fruit>> {
        val px = Px(128, 112)
        val n = fruits.size
        val grow = 0.62f + 0.38f * (n / 40f).coerceAtMost(1f)
        val cx = 64f
        val cy = 44f
        val rx = 54f * grow
        val ry = 34f * grow
        // Ground and roots.
        px.shade(ellipse(64f, 106f, 40f, 4.5f), facing(0f, -1f, 0.6f), if (night) R.leaf else R.leaf, outline = false, lo = 1, hi = 3)
        for (s in listOf(-1f, 1f)) {
            val root = poly(64f + 4 * s, 98f, 64f + 16 * s, 104f, 64f + 24 * s, 107f, 64f + 14 * s, 106f, 64f + 3 * s, 104f)
            px.shade(root, facing(s * 0.4f, -0.3f, 1f), R.wood, lo = 1)
        }
        // Branches reaching into the crown.
        for ((bx, by) in listOf(30f to 36f, 98f to 36f, 42f to 22f, 86f to 22f, 64f to 16f)) {
            val tx = 64f + (bx - 64f) * grow
            val ty = 46f + (by - 46f) * grow
            val dx = (tx - 64f) / 14f
            for (i in 0..14) {
                val t = i / 14f
                val x = 64f + (tx - 64f) * t + dx * sin(t * PI.toFloat()) * 2f
                val y = 70f + (ty - 70f) * t
                val w = 3.2f - t * 2f
                px.shade(ellipse(x, y, w, w), dome(x, y, w, w), R.wood, outline = false, lo = 1, hi = 4)
            }
        }
        val trunk = poly(*(floatArrayOf(55f, 104f) + bez(55f, 104f, 59f, 90f, 59f, 72f, 58f, 54f) + floatArrayOf(70f, 54f) + bez(70f, 54f, 69f, 72f, 69f, 90f, 73f, 104f)))
        px.shade(trunk, column(64f, 9f), R.wood, texture = { x, y -> noise(x / 2, y / 5, 13) * 0.3f + if ((x + y / 6) % 5 == 0) -0.2f else 0f }, rim = R.wood[5])
        // The hollow, where two eyes glint now and then.
        px.shade(ellipse(64f, 82f, 4f, 5.5f), facing(), Ramp(R.wood[0], R.mouth[0]), lo = 1, hi = 1)
        if (f % 12 != 5) { px.set(62, 81, R.gold[5]); px.set(66, 81, R.gold[5]) }
        // The crown: clumps of leaves round the edge, even on both sides, the upper ones behind the
        // lower so each clump shows its rounded top. Their edges inside are dark leaf, not ink.
        val blobs = 12
        val clumps = List(blobs) { i ->
            val a = (i + 0.5f) * 2 * PI.toFloat() / blobs
            val r = (15f + (noise(minOf(i, (blobs + 5 - i) % blobs), 2, 9) + 0.5f) * 6f) * grow
            Triple(cx + cos(a) * rx * 0.62f, cy + sin(a) * ry * 0.55f, r)
        }.sortedBy { it.second }
        clumps.forEachIndexed { i, (bx, by, r) ->
            px.shade(ellipse(bx, by, r * 1.1f, r * 0.9f), dome(bx, by - 2, r * 1.1f, r * 0.9f), R.leaf, texture = { x, y -> if (noise(x / 2, y / 2, i) > 0.3f) 0.15f else 0f }, rim = R.leaf[5], dither = 0.6f, line = R.leaf[1])
        }
        px.shade(ellipse(cx, cy - 2, rx * 0.75f, ry * 0.75f), dome(cx, cy - 6, rx * 0.75f, ry * 0.75f), R.leaf, texture = { x, y -> if (noise(x / 2, y / 2, 50) > 0.3f) 0.15f else 0f }, dither = 0.6f, outline = false)
        // A few lit tufts on the sunny upper left.
        for ((tx, ty, tr) in listOf(Triple(-0.38f, -0.42f, 9f), Triple(0.05f, -0.6f, 8f), Triple(-0.62f, -0.05f, 7f))) {
            val x = cx + tx * rx
            val y = cy + ty * ry
            px.shade(ellipse(x, y, tr * grow, tr * 0.7f * grow), dome(x - 2, y - 3, tr * grow, tr * 0.7f * grow), R.leaf, dither = 0.6f, outline = false, lo = 3)
        }
        // Fruits, on a sunflower spiral.
        val slots = maxOf(n, 14)
        val out = ArrayList<Fruit>()
        fruits.forEachIndexed { i, c ->
            val r = sqrt((i + 0.5f) / slots)
            val a = i * 137.508f * PI.toFloat() / 180f
            val x = cx + cos(a) * rx * 0.86f * r
            val y = cy + sin(a) * ry * 0.86f * r
            val ramp = R.around(c)
            // Each fruit glows steadily and swells a little once a turn, at its own moment.
            val bright = (f + i * 5) % 12 == 0
            px.glow(x, y, if (bright) 7.5f else 6f, ramp[6], if (night) 90 else 70, 3, onlyDrawn = true)
            px.shade(ellipse(x, y, 2.6f, 2.6f), dome(x, y, 2.6f, 2.6f), ramp, spec = true, lo = 1)
            px.set(x.toInt() - 1, y.toInt() - 3, R.wood[2])
            out += Fruit(i, x.toInt(), y.toInt())
        }
        val solid = BooleanArray(px.w * px.h) { px.c[it].al() >= 160 }
        px.ring(solid, OUT)
        return px to out
    }

    /** A firefly by the tree, in its pixels, and how brightly it shines (0..1). */
    class Firefly(val x: Float, val y: Float, val light: Float)

    /**
     * The fireflies round the tree at [t] seconds: more of them as it bears [fruits]. They drift
     * smoothly on slow loops and each blinks at its own pace, so they are drawn every frame on
     * top of the tree rather than baked into its few frames.
     */
    fun fireflies(fruits: Int, t: Float): List<Firefly> = List(5 + minOf(fruits / 6, 7)) { i ->
        val r = kotlin.random.Random(i * 7919 + 13)
        fun p() = r.nextFloat() * 2 * PI.toFloat()
        val sx = 0.12f + r.nextFloat() * 0.18f
        val sy = 0.15f + r.nextFloat() * 0.2f
        val x = 64f + sin(t * sx + p()) * (34f + r.nextFloat() * 20f) + sin(t * sx * 2.7f + p()) * 5f
        val y = 56f + sin(t * sy + p()) * (26f + r.nextFloat() * 10f) + sin(t * sy * 3.1f + p()) * 4f
        val blink = sin(t * (0.9f + r.nextFloat() * 1.2f) + p())
        Firefly(x, y, (blink * 0.5f + 0.5f).let { it * it }.coerceIn(0f, 1f))
    }
}
