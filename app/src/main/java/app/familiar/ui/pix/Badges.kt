package app.familiar.ui.pix

import app.familiar.domain.Feat

/**
 * The medals of the achievements: one drawn picture per measure (art/badges, cut by tools/scenery.py) set in a
 * round rim whose metal tells the step: bronze, silver, gold, amethyst, starlight. A star stands in while a
 * picture is missing; one not earned yet is a grey shadow of itself.
 */
object Badges {
    const val SIZE = 32
    /** The picture's own canvas, laid in the middle of the rim. */
    const val ART = 24

    private val bronze = Ramp(rgb(52, 24, 30), rgb(96, 50, 36), rgb(142, 82, 46), rgb(186, 120, 66), rgb(220, 162, 100), rgb(244, 206, 150))
    private val amethyst = Ramp(rgb(40, 18, 66), rgb(74, 36, 112), rgb(112, 62, 160), rgb(152, 100, 200), rgb(192, 148, 230), rgb(228, 204, 250))
    private val starlight = Ramp(rgb(16, 44, 84), rgb(30, 86, 136), rgb(60, 138, 186), rgb(112, 190, 226), rgb(176, 228, 248), rgb(232, 250, 255))

    /** The rim's metal for [rank] 0..4. */
    fun ramp(rank: Int): Ramp = when (rank) {
        0 -> bronze
        1 -> R.iron
        2 -> R.gold
        3 -> amethyst
        else -> starlight
    }

    /** The rim's colour, for words and bars beside the medal. */
    fun argb(rank: Int): Int = ramp(rank)[4]

    private val STAR = arrayOf(
        "....x....",
        "....x....",
        "...xxx...",
        "xxxxxxxxx",
        ".xxxxxxx.",
        "..xxxxx..",
        "..xxxxx..",
        ".xxx.xxx.",
        ".x.....x.",
    )

    fun has(feat: Feat): Boolean = Painted.of(path(feat)) != null

    private fun path(feat: Feat) = "badges/" + feat.name.lowercase()

    fun draw(feat: Feat, rank: Int, earned: Boolean): Px {
        val px = Px(SIZE, SIZE)
        val metal = if (earned) ramp(rank) else R.stone
        val c = SIZE / 2f
        px.shade(ellipse(c, c, 15f, 15f), dome(c, c, 15f, 15f, 0.4f), metal, spec = earned, rim = metal[metal.size - 2], lo = 1)
        // The field the picture sits on: deep in the metal's own shade.
        val field = Ramp(metal[0], mixc(metal[0], metal[1], 0.5f), metal[1])
        px.shade(ellipse(c, c, 11.5f, 11.5f), dome(c, c, 11.5f, 11.5f, 1.2f), field, outline = false)
        val off = (SIZE - ART) / 2
        val art = Painted.of(path(feat))
        if (art == null) {
            val ink = if (earned) metal[metal.size - 1] else R.stone[3]
            STAR.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch == 'x') px.set(c.toInt() - 4 + x, c.toInt() - 5 + y, ink) } }
        } else if (earned) {
            px.blit(art.px, off, off)
        } else {
            for (y in 0 until art.px.h) for (x in 0 until art.px.w) if (art.px[x, y].al() >= 128) px.set(x + off, y + off, R.stone[3])
        }
        // A glint on the rim of one that is earned.
        if (earned) { px.set(7, 6, WHITE, 200); px.set(8, 5, WHITE, 140) }
        return px
    }
}
