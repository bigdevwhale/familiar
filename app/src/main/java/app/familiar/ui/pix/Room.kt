package app.familiar.ui.pix

import app.familiar.domain.Item
import app.familiar.domain.Place
import app.familiar.domain.Season
import app.familiar.domain.Sky
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The den: a cosy room behind the familiar, furnished with what was bought for sparks.
 * 144 x 108 pixels; the familiar stands in the middle with its feet on [FLOOR_Y].
 */
object Room {
    const val W = 144
    const val H = 108
    const val FLOOR_Y = 104
    /** Beats in the room's loop: flames, bulbs and glints flicker to these. */
    const val BEATS = 4
    /** Frames painted for it, so bats, leaves and bubbles move in small steps between the beats. */
    const val FRAMES = BEATS * 4
    const val STEP = FRAMES / BEATS

    private val brew = Ramp(rgb(16, 52, 30), rgb(30, 104, 46), rgb(64, 168, 70), rgb(140, 224, 110), rgb(214, 252, 170))

    /** The drawn layers, in the order they stack: what hangs on the wall first, then the floor and what stands on it. */
    private val order = listOf(
        Item.WINDOW_MOON, Item.WINDOW_STAINED, Item.BOOKSHELF, Item.PORTRAIT, Item.CLOCK, Item.MAP, Item.BANNER, Item.DREAMCATCHER, Item.LANTERN,
        Item.RUG_RED, Item.RUG_STARS, Item.RUG_MOSS, Item.ARMCHAIR, Item.CAULDRON, Item.TELESCOPE, Item.FIREPLACE, Item.MANDRAKE,
        Item.TREASURE, Item.GLOBE, Item.AQUARIUM, Item.MUSHROOMS, Item.CANDLES, Item.FIREFLIES, Item.YARN, Item.PLUSH, Item.GARLAND,
    )

    private fun layer(item: Item, night: Boolean): String {
        val name = item.name.lowercase()
        // A window and the den itself are drawn twice: by day and by night.
        return "room/" + if (item.place == Place.WINDOW || item.place == Place.ROOM) name + if (night) "_night" else "_day" else name
    }

    /** The piece has been drawn (art/room, tools/scenery.py): only then is it sold. */
    fun has(item: Item): Boolean = Painted.of(layer(item, night = true)) != null

    /** What the shop sells: every piece that has been drawn. */
    val shop: List<Item> by lazy { Item.entries.filter { has(it) } }

    /**
     * Frame [at] of [FRAMES] of the den. The room and its pieces are drawn pictures (tools/scenery.py);
     * flames, the brew and the bulbs are lit over them frame by frame. [night] shows the moon in the window.
     */
    @Suppress("UNUSED_PARAMETER")
    fun scene(placed: Set<Item>, books: List<Int>, night: Boolean, at: Int, season: Season = Season.NONE, sky: Sky = Sky.CLEAR): Px {
        val px = Px(W, H)
        val f = at / STEP
        val ph = at / FRAMES.toFloat()
        // The den it is: the attic it starts as, or another one bought and moved into.
        val den = placed.firstOrNull { it.place == Place.ROOM }?.let { Painted.lay(px, layer(it, night)) }
        if (den == null && Painted.lay(px, if (night) "room/room_night" else "room/room_day") == null) px.rect(0, 0, W, H, rgb(54, 42, 62))
        for (item in order) {
            if (item !in placed) continue
            if (item.place == Place.TOY) {
                // A plaything rolls a little from side to side, as if just batted.
                val l = Painted.of(layer(item, night)) ?: continue
                px.blit(l.px, (sin(ph * 2 * PI) * 1.6).roundToInt(), 0)
                continue
            }
            val l = Painted.lay(px, layer(item, night)) ?: continue
            light(px, item, l, night, f, ph)
            // Snow in winter is the season's own (dress); the rest of the year's weather goes by the day.
            if (item == Item.WINDOW_MOON && season != Season.WINTER) weather(px, l, sky, night, ph)
        }
        dress(px, season, placed, night, f, ph)
        // Warm light around where the familiar sits, stronger with candles and lanterns.
        val warmth = 34 + (if (Item.CANDLES in placed) 18 else 0) + (if (Item.LANTERN in placed) 18 else 0)
        px.glow(72f, 74f, 70f, rgb(255, 190, 110), warmth, 5, onlyDrawn = true)
        return px
    }

    /** What moves on a piece: flames flicker, the brew bubbles, bulbs and glints come and go. */
    private fun light(px: Px, item: Item, l: Painted.Layer, night: Boolean, f: Int, ph: Float) {
        when (item) {
            Item.LANTERN -> {
                val (x, y) = l.flames.firstOrNull() ?: (l.cx to l.top + l.h * 0.6f)
                px.glow(x, y, if (f % 2 == 0) 16f else 14f, R.gold[4], 60, 4)
            }
            Item.CANDLES -> l.flames.forEachIndexed { i, (x, y) ->
                px.glow(x, y, if ((f + i) % 2 == 0) 7f else 6f, R.gold[5], 45, 3)
            }
            Item.CAULDRON -> {
                // Bubbles rising off the brew, wobbling as they go.
                val top = l.top + l.h * 0.28f
                for (i in 0 until 3) {
                    val rise = (ph + i / 3f) % 1f
                    val bx = (l.cx - 6 + i * 5 + 0.5f + 0.7f * sin((rise * 2 + i * 0.3f) * 2 * PI).toFloat()).roundToInt()
                    val by = (top - rise * 11).toInt()
                    px.set(bx, by, brew[4]); px.set(bx + 1, by, brew[3]); px.set(bx, by - 1, brew[3])
                }
                px.glow(l.cx, top, 10f, brew[3], 60, 3)
            }
            Item.GARLAND -> {
                // The bulbs: the brightest, most colourful pixels of the string, lit in turns along it.
                val p = l.px
                for (y in l.top..l.bottom) for (x in l.left..l.right) {
                    val c = p[x, y]
                    if (c.al() == 0 || lum(c) < 0.6f) continue
                    if ((x / 9 + f) % 4 != 0) px.glow(x + 0.5f, y + 0.5f, 3f, c, 26, 2, onlyDrawn = true)
                }
            }
            Item.WINDOW_STAINED -> if (!night) px.glow(l.cx, (l.top + l.bottom) / 2f, 22f, rgb(255, 240, 200), 40, 4, onlyDrawn = true)
            Item.WINDOW_MOON -> if (night) {
                // A star or two winking in the glass: only on the night sky, never on the frame or the wall.
                val sky = l.sky
                if (sky.isNotEmpty()) for (i in 0 until 5) {
                    if ((i + f) % 4 != 0) continue
                    val p = sky[((noise(i, 1, 80) + 0.5f) * sky.size).toInt().coerceIn(0, sky.size - 1)]
                    px.set(p % W, p / W, WHITE)
                }
            }
            Item.TREASURE -> px.set((l.cx + if (f % 2 == 0) -4 else 5).toInt(), l.bottom - 3, WHITE)
            Item.FIREPLACE -> {
                val fires = l.flames.ifEmpty { listOf(l.cx to l.bottom - l.h * 0.25f) }
                fires.forEachIndexed { i, (x, y) -> px.glow(x, y, if ((f + i) % 2 == 0) 13f else 11f, R.ember[4], 70, 4) }
                // A spark flying up the chimney.
                val rise = ph
                val (sx, sy) = fires.first()
                px.set((sx + 2 * sin(rise * 4 * PI)).roundToInt(), (sy - 4 - rise * 12).toInt(), R.gold[5], (255 * (1 - rise)).toInt())
            }
            Item.AQUARIUM -> for (i in 0 until 3) {
                // Bubbles rising through the water, one after another.
                val rise = (ph + i / 3f) % 1f
                val bx = (l.left + l.w * (0.3f + 0.2f * i)).toInt()
                val by = (l.bottom - l.h * 0.25f - rise * l.h * 0.45f).toInt()
                px.set(bx, by, R.sky[5], 200)
            }
            Item.FIREFLIES -> for (i in 0 until 4) {
                if ((f + i) % 3 == 0) continue
                val x = l.left + 3 + ((noise(i, f / 2, 61) + 0.5f) * (l.w - 6)).toInt()
                val y = l.top + 4 + ((noise(i, f / 2, 62) + 0.5f) * (l.h - 9)).toInt()
                px.glow(x + 0.5f, y + 0.5f, 2.5f, rgb(220, 250, 140), 160, 2)
            }
            Item.MUSHROOMS -> if (night) px.glow(l.cx, l.top + l.h * 0.45f, 12f, R.sky[4], 55, 3, onlyDrawn = true)
            else -> {}
        }
    }

    /** What falls outside the window: on its glass only, and looping with the room's frames. */
    private fun weather(px: Px, l: Painted.Layer, sky: Sky, night: Boolean, ph: Float) {
        if (sky == Sky.CLEAR) return
        val pane = l.pane
        fun glass(x: Int, y: Int) = x in 0 until l.px.w && y in 0 until l.px.h && pane[y * l.px.w + x]
        val n = when (sky) { Sky.RAIN -> 9; Sky.SNOW -> 9; else -> 4 }
        for (i in 0 until n) {
            val x0 = l.left + ((noise(i, 3, 71) + 0.5f) * l.w).toInt()
            // Each falls the window's height a whole number of times a loop, so the last frame meets the first.
            val speed = if (sky == Sky.RAIN) 2 + i % 2 else 1
            val y = l.top + Math.floorMod(((noise(i, 4, 71) + 0.5f + ph * speed) * l.h).toInt(), l.h)
            when (sky) {
                Sky.RAIN -> for (k in 0..2) if (glass(x0 - k / 2, y + k)) px.set(x0 - k / 2, y + k, R.sky[if (night) 3 else 5], 190)
                Sky.SNOW -> {
                    val x = x0 + (sin((ph + i * 0.3f) * 2 * PI) * 1.2).roundToInt()
                    if (glass(x, y)) px.set(x, y, WHITE, 230)
                }
                else -> {
                    // A leaf or a petal drifting down, swaying as it goes.
                    val x = x0 + (sin((ph + i * 0.27f) * 2 * PI) * 2.4).roundToInt()
                    val c = if (sky == Sky.LEAVES) (if (i % 2 == 0) R.flame[4] else R.gold[3]) else R.pink[3]
                    if (glass(x, y)) { px.set(x, y, c); if (glass(x + 1, y)) px.set(x + 1, y, c, 170) }
                }
            }
        }
    }

    /** One piece alone on a bit of wall and floor, for the shop. */
    fun preview(item: Item, books: List<Int>, night: Boolean, f: Int): Px {
        val full = scene(setOf(item), books, night, f)
        val (x0, y0, w, h) = when (item.place) {
            app.familiar.domain.Place.WINDOW -> listOf(2, 2, 42, 56)
            app.familiar.domain.Place.WALL -> listOf(102, 20, 42, 84)
            app.familiar.domain.Place.TOP -> if (item == Item.GARLAND) listOf(0, 0, 60, 30) else listOf(46, 0, 52, 30)
            app.familiar.domain.Place.FLOOR_LEFT -> listOf(0, 46, 42, 62)
            app.familiar.domain.Place.FLOOR_RIGHT -> listOf(102, 54, 42, 54)
            app.familiar.domain.Place.RUG -> listOf(24, 84, 96, 24)
            app.familiar.domain.Place.LIGHT -> if (item == Item.LANTERN) listOf(28, 0, 34, 40) else listOf(0, 80, 30, 28)
            Place.TOY -> listOf(16, 82, 36, 26)
            Place.ROOM -> listOf(0, 0, W, H)
        }
        return full.crop(x0, y0, w, h)
    }

    /** The den dressed for the days of a season. */
    private fun dress(px: Px, season: Season, placed: Set<Item>, night: Boolean, f: Int, ph: Float) {
        // The drawn decorations (tools/scenery.py); what moves is painted over them frame by frame.
        fun lay(name: String) = Painted.lay(px, "room/season_$name")
        when (season) {
            Season.SAMHAIN -> {
                // A cobweb in the corner, bats on the wall, two carved pumpkins by the familiar.
                lay("cobweb")
                for (i in 0 until 2) {
                    // Each bat loops lazily about its spot, flapping three times a turn.
                    val a = (ph + i * 0.4f) * 2 * PI
                    val bx = (56 + i * 26 + 3 * kotlin.math.cos(a)).roundToInt(); val by = (30 + i * 6 + 1.5 * sin(2 * a)).roundToInt()
                    px.hline(bx - 1, bx + 1, by, EYE); px.set(bx, by + 1, EYE)
                    val tip = by + (-0.5 + 1.6 * sin(a * 3 + i)).roundToInt()
                    val mid = (by + tip) / 2
                    px.line(bx - 1, by, bx - 3, mid, EYE); px.line(bx - 3, mid, bx - 5, tip, EYE)
                    px.line(bx + 1, by, bx + 3, mid, EYE); px.line(bx + 3, mid, bx + 5, tip, EYE)
                }
                // The carved faces flicker.
                lay("pumpkins")?.flames?.forEachIndexed { i, (x, y) ->
                    px.glow(x, y, if ((f + i) % 2 == 0) 9f else 7.5f, R.gold[5], 40, 3)
                }
            }
            Season.WINTER -> {
                // A little fir by the familiar, its baubles catching the light, and snow in the window.
                lay("fir")?.flames?.forEachIndexed { i, (x, y) ->
                    if ((i + f) % 3 != 0) px.glow(x, y, 3.5f, R.gold[5], 50, 2, onlyDrawn = true)
                }
                if (Item.WINDOW_MOON in placed || Item.WINDOW_STAINED in placed) for (i in 0 until 10) {
                    val sx = 11 + ((noise(i, 3, 94) + 0.5f) * 24).toInt()
                    val sy = 12 + ((noise(i, 4, 94) + 0.5f) * 30 + ph * 12).toInt() % 34
                    px.set(sx, sy, WHITE, 220)
                } else lay("wreath") // A wreath on the wall instead.
            }
            Season.VALENTINE -> lay("hearts")
            Season.BOOKDAY -> {
                // Bunting of tiny books, and a stack of them by the familiar with a glint on top.
                lay("bunting")
                lay("books")?.let { l -> if (f % 2 == 0) px.set(l.left + l.w / 2, l.top + 1, R.gold[5]) }
            }
            Season.MIDSUMMER -> {
                for (i in 0 until 7) {
                    val a = (ph + i / 7f) * 2 * PI
                    val x = (72 + kotlin.math.cos(a + i).toFloat() * 60).toInt()
                    val y = (44 + sin(a * 2 + i).toFloat() * 26).toInt()
                    if ((f + i) % 3 != 0) px.glow(x + 0.5f, y + 0.5f, 3f, rgb(220, 250, 140), 170, 2)
                }
                // Daisies in a jar.
                lay("daisies")
            }
            Season.NONE -> {}
        }
    }
}
