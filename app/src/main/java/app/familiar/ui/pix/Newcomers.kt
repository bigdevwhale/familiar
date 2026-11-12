package app.familiar.ui.pix

import app.familiar.domain.Stage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The four familiars found later: the tanuki, the sprout, the ghost and the bookworm. */
internal object Newcomers {

    /* ---------- Tanuki: listens to audiobooks in big headphones ---------- */

    fun tanuki(pen: Pen, ctx: Sprites.Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        // A fat striped tail curling up behind, on the right.
        val sway = ctx.sw * 4f
        val tail = pen.path(sway, 64f, 82f).m(60f, 86f).c(76f, 88f, 92f, 76f, 88f, 58f).c(86f, 48f, 74f, 48f, 72f, 58f).c(70f, 68f, 66f, 76f, 60f, 79f).l(60f, 86f).shape()
        val tn = pen.turnedDome(80f, 68f, 11f, 17f, sway, 64f, 82f)
        px.shade(tail, tn, R.tanuki, texture = Sprites.fur(51, 0.18f), rim = R.tanuki[5])
        for ((a, b) in listOf(66f to 70f, 74f to 58f)) {
            val band = pen.path(sway, 64f, 82f).m(a, b).l(a + 26f, b + 22f).l(a + 28f, b + 18f).l(a + 2f, b - 4f).shape()
            px.shade(and(tail, band), tn, R.brown, outline = false, lo = 0, hi = 3)
        }
        val body = pen.path().m(50f, 46f).c(67f, 47f, 75f, 68f, 73f, 86f).q(50f, 92f, 27f, 86f).c(25f, 68f, 33f, 47f, 50f, 46f).shape()
        px.shade(body, pen.dome(50f, 67f, 24f, 22f), R.tanuki, texture = Sprites.fur(52), rim = R.tanuki[5])
        // The round belly it drums on.
        px.shade(pen.ell(50f, 72f, 13.5f, 13f), pen.dome(49f, 70f, 13.5f, 13f), R.cream, outline = false, lo = 2, texture = Sprites.fur(53, 0.08f))
        for (fx in listOf(39f, 61f)) {
            px.shade(pen.ell(fx, 86.5f, 6.4f, 3.4f), pen.dome(fx, 85.5f, 6.4f, 3.4f), R.brown, lo = 0, hi = 4)
        }
        for (ax in listOf(33.5f, 66.5f)) px.shade(pen.ell(ax, 66f, 4.6f, 7f), pen.dome(ax, 65f, 4.6f, 7f), R.brown, rim = R.brown[5], lo = 0, hi = 5)
        // Round ears.
        for (ex in listOf(36f, 64f)) {
            px.shade(pen.ell(ex, 25f, 6f, 5.6f), pen.dome(ex, 24f, 6f, 5.6f), R.brown, lo = 0, hi = 4)
            px.shade(pen.ell(ex, 25.8f, 3.2f, 2.8f), facing(), R.tanuki, outline = false, lo = 4, hi = 5)
        }
        px.shade(pen.ell(50f, 36f, 18f, 15f), pen.dome(50f, 34f, 18f, 15f), R.tanuki, texture = Sprites.fur(54, 0.1f), rim = R.tanuki[5])
        // The raccoon mask, a pale brow and a pale muzzle.
        for (mx in listOf(42.5f, 57.5f)) px.shade(pen.ell(mx, 37.5f, 7f, 5f), pen.dome(mx, 37f, 7f, 5f), R.brown, outline = false, lo = 0, hi = 2)
        px.shade(pen.ell(50f, 29f, 6f, 3.4f), facing(), R.cream, outline = false, lo = 3, hi = 4)
        px.shade(pen.ell(50f, 44f, 8f, 5f), pen.dome(50f, 43f, 8f, 5f), R.cream, outline = false, lo = 3)
        val nx = pen.x(50f).toInt(); val ny = pen.y(41.5f).toInt()
        px.rect(nx - 1, ny, 3, 2, EYE); px.set(nx - 1, ny, R.iron[3])
        px.set(nx, ny + 2, EYE); px.set(nx - 1, ny + 3, EYE); px.set(nx + 1, ny + 3, EYE)
        // Big headphones once it's old enough to love audiobooks.
        if (st >= Stage.YOUNG) {
            val band = and(minus(pen.ell(50f, 35f, 20.5f, 18f), pen.ell(50f, 35f, 17.6f, 15.2f)), pen.box(0f, 0f, 100f, 31f))
            px.shade(band, pen.dome(50f, 35f, 20f, 18f), R.iron, lo = 1, hi = 4)
            for (cx in listOf(31.5f, 68.5f)) {
                px.shade(pen.ell(cx, 36f, 4.6f, 6.6f), pen.dome(cx, 35f, 4.6f, 6.6f), R.red, spec = true)
                px.shade(pen.ell(cx, 36f, 2f, 4f), facing(), R.red, outline = false, lo = 1, hi = 2)
            }
            // A note now and then.
            if (ctx.f in 2..7) {
                val p = (ctx.f - 2) / 5f
                val x = pen.x(74f + p * 8f).toInt(); val y = pen.y(26f - p * 10f).toInt()
                px.vline(x + 2, y - 4, y, R.lilac[5]); px.rect(x, y, 3, 2, R.lilac[5]); px.set(x + 3, y - 4, R.lilac[5])
            }
        }
        // The shape-shifting leaf on its head, gold when ancient.
        val leafRamp = if (st == Stage.ANCIENT) R.gold else R.leaf
        val leaf = pen.path(-24f + ctx.sw * 4f, 50f, 22f).m(50f, 23f).q(41f, 16f, 47f, 6f).q(57f, 12f, 50f, 23f).shape()
        px.shade(leaf, pen.turnedDome(48f, 15f, 4f, 8f, -24f, 50f, 22f), leafRamp, spec = st == Stage.ANCIENT, lo = 1)
        return Sprites.face(px, pen, ctx, listOf(43f to 37f, 57f to 37f), 3.6f, R.brown[2], R.brown[2], 50f to 35f, 17f)
    }

    /* ---------- Sprout: a little root that blooms with every morning of reading ---------- */

    fun sprout(pen: Pen, ctx: Sprites.Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        val leaves = when (st) { Stage.BABY -> 3; Stage.YOUNG -> 4; Stage.ADULT -> 5; else -> 6 }
        val angles = List(leaves) { i -> if (leaves == 1) 0f else -54f + 108f * i / (leaves - 1) }
        val len = when (st) { Stage.BABY -> 22f; Stage.YOUNG -> 26f; else -> 30f }
        val tips = ArrayList<Pair<Int, Int>>()
        val mids = ArrayList<Pair<Int, Int>>()
        angles.forEachIndexed { i, a0 ->
            val a = a0 + sin(ctx.ph * 2 * PI.toFloat() + i) * 4f
            val top = 46f - len
            val leaf = pen.path(a, 50f, 46f).m(50f, 46f).c(42f, 40f, 42f, top + 8f, 50f, top).c(58f, top + 8f, 58f, 40f, 50f, 46f).shape()
            px.shade(leaf, pen.turnedDome(50f, 46f - len / 2, 6.5f, len / 2, a, 50f, 46f), R.leaf, rim = R.leaf[5], lo = 1)
            val (vx0, vy0) = pen.at(50f, 45f, a, 50f, 46f)
            val (vx1, vy1) = pen.at(50f, top + 4f, a, 50f, 46f)
            px.line(vx0, vy0, vx1, vy1, R.leaf[2])
            tips += pen.at(50f, top + 2f, a, 50f, 46f)
            mids += pen.at(50f + if (i % 2 == 0) -3f else 3f, top + len * 0.45f, a, 50f, 46f)
        }
        // Root legs and little root arms.
        for (s in listOf(-1f, 1f)) {
            val leg = pen.path().m(50f + 5f * s, 80f).q(50f + 9f * s, 86f, 50f + 7f * s, 89.5f).l(50f + 3f * s, 89.5f).q(50f + 4f * s, 85f, 50f + 1f * s, 81f).shape()
            px.shade(leg, facing(0.3f * s, -0.2f, 1f), R.root, lo = 1, hi = 4)
            val arm = pen.path().m(50f + 16f * s, 64f).q(50f + 24f * s, 66f, 50f + 25f * s, 72f).l(50f + 22f * s, 72f).q(50f + 21f * s, 69f, 50f + 15f * s, 68f).shape()
            px.shade(arm, facing(0.3f * s, 0f, 1f), R.root, lo = 1, hi = 4)
        }
        val body = pen.path().m(50f, 43f).c(65f, 43f, 71f, 60f, 68f, 72f).q(64f, 84f, 50f, 84f).q(36f, 84f, 32f, 72f).c(29f, 60f, 35f, 43f, 50f, 43f).shape()
        px.shade(body, pen.dome(50f, 62f, 19f, 21f), R.root, texture = { x, y -> if ((y + x / 5) % 6 == 0) -0.14f else 0f }, rim = R.root[5])
        // Root hairs.
        for ((hx, hy) in listOf(36f to 76f, 64f to 74f, 42f to 82f)) px.set(pen.x(hx).toInt(), pen.y(hy).toInt(), R.root[1])
        // Flowers: one for every morning of reading, on the leaf tips first.
        val spots = tips + mids
        val petals = listOf(R.pink[3], WHITE, R.lilac[5], R.pink[2], R.gold[5])
        for (i in 0 until minOf(ctx.look.blooms, spots.size)) {
            val (x, y) = spots[i]
            val c = petals[i % petals.size]
            // Five round petals around a gold heart.
            for ((dx, dy) in listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1, 0 to -2, -2 to 0, 2 to 0, -1 to 2, 1 to 2)) px.set(x + dx, y + dy, c)
            for ((dx, dy) in listOf(0 to -3, -3 to 0, 3 to 0, -2 to 2, 2 to 2, -2 to -2, 2 to -2)) px.set(x + dx, y + dy, OUT, 150)
            px.set(x, y, R.gold[3])
        }
        if (st == Stage.ANCIENT) for (i in 0 until 4) {
            val p = ((ctx.ph + i / 4f) % 1f)
            val x = pen.x(30f + i * 13f + sin(p * 6f) * 2f).toInt(); val y = pen.y(40f - p * 30f).toInt()
            px.set(x, y, R.gold[5], (220 * sin(p * PI.toFloat())).toInt().coerceAtLeast(60))
        }
        val mx = pen.x(50f).toInt(); val my = pen.y(70f).toInt()
        px.set(mx - 1, my, EYE); px.set(mx, my + 1, EYE); px.set(mx + 1, my, EYE)
        if (ctx.look.chew) px.rect(mx - 1, my + 1, 3, 2, R.mouth[2])
        return Sprites.face(px, pen, ctx, listOf(43.5f to 63f, 56.5f to 63f), 3.4f, R.leaf[2], R.root[4], 50f to 62f, 18f)
    }

    /* ---------- Ghost: a sheet with a lantern, fond of horror and mysteries ---------- */

    fun ghost(pen: Pen, ctx: Sprites.Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        val ramp = if (ctx.look.shiny) Ramp(rgb(30, 70, 70), rgb(70, 140, 130), rgb(130, 200, 180), rgb(190, 238, 218), rgb(226, 252, 240), rgb(248, 255, 252)) else R.fluff
        val wave = if (ctx.f % 6 < 3) 0f else 1.6f
        val path = pen.path().m(50f, 24f).c(69f, 24f, 75f, 42f, 75f, 60f).l(75f, 82f)
        val xs = listOf(75f, 67f, 58f, 50f, 42f, 33f, 25f)
        for (i in 0 until xs.size - 1) {
            val up = if (i % 2 == 0) wave else 1.6f - wave
            path.q((xs[i] + xs[i + 1]) / 2f, 88f - up, xs[i + 1], 82f + if (i % 2 == 0) 0f else 1f)
        }
        val sheet = path.l(25f, 60f).c(25f, 42f, 31f, 24f, 50f, 24f).shape()
        // A wisp for every horror or mystery book read, circling behind.
        val n = ctx.look.wisps.coerceAtMost(6)
        for (i in 0 until n) {
            val a = (ctx.ph * 360f + i * 360f / n) * PI / 180
            val fx = pen.x(50f + cos(a).toFloat() * 34f); val fy = pen.y(46f + sin(a).toFloat() * 8f)
            px.glow(fx, fy, 4.5f, R.sky[4], 100)
            val fl = poly(fx, fy - 3.5f, fx + 1.8f, fy, fx + 0.8f, fy + 1.6f, fx - 0.8f, fy + 1.6f, fx - 1.8f, fy)
            px.shade(fl, facing(), R.sky, outline = false, lo = 3)
        }
        val m = px.shade(sheet, pen.dome(50f, 52f, 26f, 30f, 0.15f), ramp, dither = 0.7f, rim = ramp[4], lo = 1)
        // A little see-through.
        for (i in m.indices) if (m[i]) px.map(i) { p -> if (p.al() == 0) p else (226 shl 24) or (p and 0xFFFFFF) }
        for (s in listOf(-1f, 1f)) px.shade(pen.ell(50f + 25f * s, 60f, 4.4f, 6f), pen.dome(50f + 25f * s, 59f, 4.4f, 6f), ramp, lo = 1)
        // The lantern it carries, brighter as it grows.
        if (st >= Stage.YOUNG) {
            val lx = 80f; val ly = 66f
            px.line(pen.x(76f).toInt(), pen.y(58f).toInt(), pen.x(lx).toInt(), pen.y(ly - 6f).toInt(), R.iron[1])
            px.glow(pen.x(lx), pen.y(ly), pen.r(if (st == Stage.YOUNG) 9f else 13f), R.gold[5], 90)
            px.shade(pen.box(lx - 3.5f, ly - 5f, lx + 3.5f, ly + 5f), facing(), R.iron, lo = 0, hi = 2)
            val flame = if (ctx.f % 2 == 0) R.ember else R.flame
            px.shade(pen.box(lx - 2.3f, ly - 3.8f, lx + 2.3f, ly + 3.8f), { _, y -> V3(0f, (pen.uy(y) - ly) / 4f, 1f) }, flame, outline = false, lo = 3)
            px.shade(pen.box(lx - 4.5f, ly - 6.5f, lx + 4.5f, ly - 4.5f), facing(0f, -0.5f, 1f), R.iron, lo = 1)
            px.shade(pen.box(lx - 4.5f, ly + 4.5f, lx + 4.5f, ly + 6.5f), facing(0f, 0.5f, 1f), R.iron, lo = 1)
        }
        if (st == Stage.ANCIENT) {
            // A tall old top hat.
            px.shade(pen.box(39f, 21f, 61f, 24.5f), facing(0f, -0.5f, 1f), R.iron, lo = 0, hi = 2)
            px.shade(pen.box(42f, 8f, 58f, 22f), pen.column(50f, 8f), R.iron, lo = 0, hi = 2)
            px.shade(pen.box(42f, 17f, 58f, 20f), facing(), R.red, outline = false, lo = 2, hi = 3)
        }
        // A round "oo" of a mouth.
        val mx = pen.x(50f).toInt(); val my = pen.y(53f).toInt()
        px.rect(mx - 1, my, 3, if (ctx.look.chew && ctx.f % 2 == 0) 2 else 3, R.mouth[1])
        return Sprites.face(px, pen, ctx, listOf(42.5f to 45f, 57.5f to 45f), 4.6f, R.night[2], ramp[4], 50f to 44f, 20f)
    }

    /* ---------- Bookworm: every segment the colour of a book it finished ---------- */

    fun wyrm(pen: Pen, ctx: Sprites.Ctx): Face {
        val px = pen.px
        val st = ctx.look.stage
        val all = listOf(Triple(85f, 84f, 6.5f), Triple(77f, 82f, 8f), Triple(67f, 80f, 9.5f), Triple(56f, 78f, 10.5f), Triple(45f, 75f, 11.5f), Triple(38f, 66f, 12f))
        val n = when (st) { Stage.BABY -> 3; Stage.YOUNG -> 4; Stage.ADULT -> 5; else -> 6 }
        val segs = all.takeLast(n)
        val colours = ctx.look.hoard.ifEmpty { listOf(rgb(120, 182, 96)) }
        // A red ribbon bookmark trailing from the tail.
        val (tx, ty, tr) = segs.first()
        val ribbon = pen.path().m(tx + tr * 0.5f, ty - 2f).l(tx + tr + 6f, ty + 2f + ctx.sw).l(tx + tr + 4f, ty + 4f + ctx.sw).l(tx + tr + 7f, ty + 6f + ctx.sw).l(tx + tr * 0.4f, ty + 3f).shape()
        px.shade(ribbon, facing(0.3f, 0f, 1f), R.red, lo = 1, hi = 4)
        segs.forEachIndexed { i, (sx, sy, r) ->
            val bob = sin(ctx.ph * 2 * PI.toFloat() + i * 0.9f) * 1.2f
            val ramp = R.around(colours[(segs.size - 1 - i) % colours.size])
            px.shade(pen.ell(sx, sy + bob - r * 0.1f, r, r * 0.92f), pen.dome(sx - r * 0.2f, sy + bob - r * 0.3f, r, r * 0.92f), ramp, rim = ramp[5], lo = 1)
            // A gold band like the spine of a book.
            val band = and(pen.ell(sx, sy + bob - r * 0.1f, r, r * 0.92f), pen.box(sx - r, sy + bob - r * 0.45f, sx + r, sy + bob - r * 0.3f))
            px.shade(band, facing(), R.gold, outline = false, lo = 3, hi = 4)
            if (i < segs.size - 1) for (s in listOf(-0.45f, 0.45f)) {
                val fx = pen.x(sx + r * s).toInt(); val fy = pen.y(88f).toInt()
                px.rect(fx - 1, fy - 2, 3, 3, R.around(colours[(segs.size - 1 - i) % colours.size])[1])
            }
        }
        val hx = 40f; val hy = 44f
        val skin = Ramp(rgb(26, 56, 40), rgb(40, 92, 54), rgb(66, 134, 70), rgb(104, 172, 88), rgb(150, 206, 116), rgb(200, 232, 160), rgb(236, 248, 210))
        // Antennae with round tips.
        for (s in listOf(-1f, 1f)) {
            val wob = if ((ctx.f + if (s > 0) 3 else 0) % 6 < 3) 0f else 1f
            val ax = hx + 6f * s; val tipX = hx + 12f * s + wob * s; val tipY = 22f - wob
            px.line(pen.x(ax).toInt(), pen.y(32f).toInt(), pen.x(tipX).toInt(), pen.y(tipY).toInt(), skin[1])
            px.shade(pen.ell(tipX, tipY, 2.4f, 2.4f), pen.dome(tipX, tipY - 0.5f, 2.4f, 2.4f), R.gold, lo = 2)
        }
        px.shade(pen.ell(hx, hy, 15f, 13.5f), pen.dome(hx - 2f, hy - 3f, 15f, 13.5f), skin, rim = skin[5], lo = 1)
        px.shade(pen.ell(hx, hy + 6.5f, 7f, 3.6f), facing(0f, 0.4f, 1f), skin, outline = false, lo = 4, hi = 5)
        val mx = pen.x(hx).toInt(); val my = pen.y(hy + 6f).toInt()
        if (ctx.look.chew && ctx.f % 2 == 0) px.rect(mx - 1, my, 3, 2, R.mouth[1])
        else { px.set(mx - 2, my, EYE); px.set(mx - 1, my + 1, EYE); px.set(mx, my + 1, EYE); px.set(mx + 1, my + 1, EYE); px.set(mx + 2, my, EYE) }
        return Sprites.face(px, pen, ctx, listOf(hx - 6f to hy - 1f, hx + 6f to hy - 1f), 4f, R.night[3], skin[4], hx to hy, 14f)
    }
}
