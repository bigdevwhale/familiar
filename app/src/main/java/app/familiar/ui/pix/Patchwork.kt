package app.familiar.ui.pix

import app.familiar.domain.Stage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The two familiars of the reader's own genres: the chimera and the magpie. */
internal object Patchwork {

    /** Blue-black magpie feathers. */
    val ink = Ramp(rgb(10, 10, 24), rgb(20, 22, 40), rgb(34, 38, 62), rgb(50, 58, 88), rgb(74, 88, 124), rgb(110, 128, 168), rgb(156, 174, 210))

    /* ---------- Chimera: stitched together from every genre of your own ---------- */

    fun chimera(pen: Pen, ctx: Sprites.Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        // Small leathery wings once it's grown.
        if (st >= Stage.ADULT) for (m in listOf(false, true)) {
            val deg = (if (m) -1f else 1f) * ctx.sw * 5f
            val wing = pen.path(deg, 40f, 56f, m).m(40f, 56f).c(32f, 44f, 22f, 38f, 14f, 40f).l(18f, 47f).l(13f, 50f).l(19f, 55f).l(16f, 60f).q(28f, 58f, 38f, 64f).shape()
            px.shade(wing, pen.turnedDome(26f, 50f, 14f, 10f, deg, 40f, 56f), R.violet, rim = R.violet[5], lo = 1)
            for (k in 0 until 2) {
                val (ax, ay) = pen.at(39f, 57f, deg, 40f, 56f, m)
                val (bx, by) = pen.at(listOf(18f, 19f)[k], listOf(47f, 55f)[k], deg, 40f, 56f, m)
                px.line(ax, ay, bx, by, R.violet[1])
            }
        }
        // A tail that ends in a little snake, looking round now and then.
        val sway = ctx.sw * 3f
        val pts = bez(62f, 82f, 80f, 86f, 90f + sway, 72f, 82f + sway, 56f, 9)
        val tailR = 3.6f
        for (i in 0 until 8) {
            val r = tailR - i * 0.18f
            px.shade(pen.ell(pts[i * 2], pts[i * 2 + 1], r, r), pen.dome(pts[i * 2] - 0.6f, pts[i * 2 + 1] - 0.8f, r, r), R.teal, texture = Sprites.scales(0.16f), lo = 1)
        }
        val sx = pts[16]; val sy = pts[17] - 2f
        px.shade(pen.ell(sx, sy, 4.6f, 3.6f), pen.dome(sx - 0.8f, sy - 1f, 4.6f, 3.6f), R.teal, rim = R.teal[5], lo = 1)
        px.set(pen.x(sx - 1.6f).toInt(), pen.y(sy - 0.8f).toInt(), EYE)
        px.set(pen.x(sx + 1.6f).toInt(), pen.y(sy - 0.8f).toInt(), EYE)
        // A forked tongue flicks out.
        if (ctx.f % 6 in 1..2) {
            val tx = pen.x(sx).toInt(); val ty = pen.y(sy + 3f).toInt()
            px.vline(tx, ty, ty + 2, R.red[3]); px.set(tx - 1, ty + 3, R.red[3]); px.set(tx + 1, ty + 3, R.red[3])
        }
        val body = pen.path().m(50f, 47f).c(64f, 48f, 72f, 68f, 70f, 86f).q(50f, 91f, 30f, 86f).c(28f, 68f, 36f, 48f, 50f, 47f).shape()
        px.shade(body, pen.dome(50f, 67f, 21f, 21f), R.tan, texture = Sprites.fur(61), rim = R.tan[5])
        // A patch sewn on for every genre of your own, with stitches round it.
        val spots = listOf(56f to 70f, 43f to 62f, 46f to 79f, 60f to 57f, 37f to 73f, 59f to 81f, 50f to 54f, 34f to 62f)
        val patches = ctx.look.patches.ifEmpty { listOf(R.cream[3]) }.take(spots.size)
        patches.forEachIndexed { i, color ->
            val (cx, cy) = spots[i]
            val rx = 5.6f - (i / 4) * 0.8f; val ry = 4.6f - (i / 4) * 0.6f
            val ramp = R.around(color)
            px.shade(and(body, pen.ell(cx, cy, rx, ry)), pen.dome(cx, cy - 1f, rx, ry), ramp, outline = false, lo = 1, hi = 5)
            for (a in 0 until 360 step 45) {
                val t = (a + i * 20) * PI / 180
                val x = pen.x(cx + cos(t).toFloat() * rx).toInt(); val y = pen.y(cy + sin(t).toFloat() * ry).toInt()
                if (px.has(x, y)) px.set(x, y, R.brown[1])
            }
        }
        for (lx in listOf(42f, 58f)) {
            px.shade(pen.box(lx - 3.6f, 72f, lx + 3.6f, 86f), pen.column(lx, 3.6f), R.tan, texture = Sprites.fur(62, 0.1f), lo = 1)
            px.shade(pen.ell(lx, 86f, 5f, 3f), pen.dome(lx, 85f, 5f, 3f), R.tan, lo = 1)
            for (c in listOf(-1.6f, 1.6f)) px.vline(pen.x(lx + c).toInt(), pen.y(85.5f).toInt(), pen.y(88f).toInt(), R.tan[1])
        }
        // A shaggy mane, a star of tufts around the head.
        val mane = pen.path()
        for (k in 0..28) {
            val a = (k / 28f * 2 * PI).toFloat()
            val r = if (k % 2 == 0) 21f else 18f
            val x = 50f + cos(a) * r * 0.95f; val y = 37f + sin(a) * r
            if (k == 0) mane.m(x, y) else mane.l(x, y)
        }
        px.shade(mane.shape(), pen.dome(50f, 35f, 21f, 21f), R.wood, texture = Sprites.fur(63, 0.22f), rim = R.wood[6], lo = 1)
        for (ex in listOf(37f, 63f)) {
            px.shade(pen.ell(ex, 24f, 4.6f, 4.2f), pen.dome(ex, 23f, 4.6f, 4.2f), R.tan, lo = 1)
            px.shade(pen.ell(ex, 24.6f, 2.2f, 2f), facing(), R.tongue, outline = false, lo = 1, hi = 2)
        }
        // A goat's curling horns, gold when ancient.
        if (st >= Stage.YOUNG) for (m in listOf(false, true)) {
            val horn = pen.path(mirror = m).m(44f, 25f).q(40f, 12f, 29f, 13f).q(25f, 17f, 29f, 21f).q(32f, 17f, 35f, 19f).q(38f, 22f, 39.5f, 27f).shape()
            px.shade(horn, pen.dome(if (m) 64f else 36f, 18f, 8f, 7f), if (st == Stage.ANCIENT) R.gold else R.horn, spec = st == Stage.ANCIENT, texture = { _, y -> if (y % 3 == 0) -0.16f else 0f }, lo = 1)
        }
        px.shade(pen.ell(50f, 37f, 13.4f, 12.4f), pen.dome(50f, 35f, 13.4f, 12.4f), R.tan, texture = Sprites.fur(64, 0.1f), rim = R.tan[5])
        px.shade(pen.ell(50f, 43f, 7f, 4.6f), pen.dome(50f, 42f, 7f, 4.6f), R.cream, outline = false, lo = 2)
        val nx = pen.x(50f).toInt(); val ny = pen.y(40.5f).toInt()
        px.rect(nx - 1, ny, 3, 2, R.tongue[1]); px.set(nx, ny + 2, EYE)
        if (ctx.look.chew && ctx.f % 2 == 0) px.rect(nx - 1, ny + 3, 3, 2, R.mouth[1])
        else { px.set(nx - 1, ny + 3, EYE); px.set(nx + 1, ny + 3, EYE) }
        return Sprites.face(px, pen, ctx, listOf(44f to 35.5f, 56f to 35.5f), 3.4f, R.leaf[3], R.tan[4], 50f to 37f, 14f)
    }

    /* ---------- Magpie: keeps a shiny thing from every subgenre in its nest ---------- */

    fun magpie(pen: Pen, ctx: Sprites.Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        // The long tail, longer as it grows, with a green-blue sheen.
        val len = when (st) { Stage.BABY -> 16f; Stage.YOUNG -> 24f; Stage.ADULT -> 30f; else -> 34f }
        val wag = ctx.sw * 3f
        val tail = pen.path(-38f + wag, 60f, 72f).m(57f, 70.5f).l(60f + len, 68.5f).q(63f + len, 71f, 60f + len, 73.5f).l(57f, 75.5f).shape()
        px.shade(tail, pen.turnedDome(60f + len / 2, 71f, len / 2 + 3f, 4f, -38f + wag, 60f, 72f), R.teal, texture = { x, _ -> if (x % 4 == 0) -0.12f else 0f }, rim = R.teal[5], lo = 0, hi = 4)
        val body = pen.path().m(50f, 44f).c(64f, 45f, 70f, 62f, 67f, 78f).q(50f, 86f, 33f, 78f).c(30f, 62f, 36f, 45f, 50f, 44f).shape()
        px.shade(body, pen.dome(50f, 63f, 19f, 20f), ink, texture = Sprites.feathers(0.1f), rim = ink[5])
        // The white belly and the white shoulder flash.
        px.shade(and(body, pen.ell(50f, 70f, 11.5f, 12f)), pen.dome(50f, 68f, 11.5f, 12f), R.snow, outline = false, texture = Sprites.feathers(0.08f), lo = 1)
        for (m in listOf(false, true)) {
            val wing = pen.path(mirror = m).m(36f, 52f).c(29f, 58f, 29f, 70f, 35f, 78f).q(39f, 70f, 40f, 56f).shape()
            px.shade(wing, pen.dome(if (m) 65f else 35f, 64f, 6f, 13f), R.teal, texture = Sprites.feathers(0.16f), rim = R.teal[5], lo = 0, hi = 4)
            px.shade(pen.ell(if (m) 64.5f else 35.5f, 56f, 2.8f, 4f), facing(if (m) 0.3f else -0.3f, -0.2f, 1f), R.snow, outline = false, lo = 2)
        }
        // The nest: woven twigs, and in it a shiny thing for every subgenre read.
        val nest = pen.path().m(24f, 78f).q(50f, 74f, 76f, 78f).q(75f, 89f, 50f, 90f).q(25f, 89f, 24f, 78f).shape()
        px.shade(nest, pen.dome(50f, 80f, 26f, 9f), R.wood, texture = { x, y -> if ((x + y * 2) % 5 == 0) -0.22f else if ((x - y * 2) % 7 == 0) 0.12f else 0f }, rim = R.wood[6], lo = 1)
        for (k in 0 until 5) {
            val x = pen.x(28f + k * 11f).toInt(); val y = pen.y(80f + (k % 2) * 2f).toInt()
            px.line(x - 3, y, x + 3, y - 1, R.wood[5])
        }
        // The shiny things sit on the rim, one for every subgenre.
        val rim = listOf(32f, 68f, 40f, 60f, 26f, 74f, 50f, 46f)
        ctx.look.patches.take(rim.size).forEachIndexed { i, color ->
            val x = rim[i]; val y = 76f + (i % 2) * 0.8f
            px.shade(pen.ell(x, y, 3f, 3f), pen.dome(x - 0.6f, y - 0.8f, 3f, 3f), R.around(color), spec = true, lo = 1)
            if ((ctx.f + i * 3) % Sprites.BEATS == 0) Sprites.stamp(px, Sprites.SPARK, pen.x(x).toInt() - 2, pen.y(y - 4f).toInt() - 2, WHITE, WHITE, 200)
        }
        // An old gold key, the best of the hoard, once ancient.
        if (st == Stage.ANCIENT) {
            val kx = 61f; val ky = 75f
            px.shade(pen.ell(kx, ky, 2.4f, 2.4f), facing(), R.gold, lo = 2, spec = true)
            px.hline(pen.x(kx + 2f).toInt(), pen.x(kx + 8f).toInt(), pen.y(ky).toInt(), R.gold[3])
            px.set(pen.x(kx + 7f).toInt(), pen.y(ky).toInt() + 1, R.gold[2]); px.set(pen.x(kx + 5f).toInt(), pen.y(ky).toInt() + 1, R.gold[2])
        }
        px.shade(pen.ell(50f, 34f, 12.4f, 11.8f), pen.dome(50f, 32f, 12.4f, 11.8f), ink, texture = Sprites.feathers(0.08f), rim = ink[6])
        // A cheeky tuft, and a sharp dark beak.
        px.shade(pen.path(ctx.sw * 6f, 50f, 23f).m(48f, 24f).q(49f, 16f, 55f, 15f).q(52f, 19f, 53f, 24f).shape(), facing(0f, -0.4f, 1f), ink, lo = 1, hi = 5)
        val beak = pen.path().m(46f, 38f).q(50f, 36.5f, 54f, 38f).q(52f, 43f, 50f, 46f).q(48f, 43f, 46f, 38f).shape()
        px.shade(beak, pen.dome(49f, 40f, 4.5f, 5.5f), R.iron, spec = true, lo = 3, hi = 5)
        // Holds a ribbon in its beak while it chews.
        if (ctx.look.chew && ctx.f % 2 == 0) px.rect(pen.x(48.5f).toInt(), pen.y(44f).toInt(), 3, 2, R.red[3])
        return Sprites.face(px, pen, ctx, listOf(44.5f to 33f, 55.5f to 33f), 3.2f, R.gold[3], ink[4], 50f to 34f, 12f)
    }
}
