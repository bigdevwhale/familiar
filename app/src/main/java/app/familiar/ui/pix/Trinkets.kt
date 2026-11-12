package app.familiar.ui.pix

import app.familiar.domain.Trinket

/** The things familiars bring back: drawn pictures (art/finds, cut by tools/scenery.py), outlined like the glyphs. */
object Trinkets {
    const val SIZE = 24

    /** [t] on its own little canvas, outlined; [dim] draws a dark silhouette of one not found yet. */
    fun draw(t: Trinket, dim: Boolean = false): Px {
        val px = Px(SIZE, SIZE)
        val m = BooleanArray(SIZE * SIZE)
        Painted.of("finds/find_" + t.name.lowercase())?.let { l ->
            for (y in 0 until l.px.h) for (x in 0 until l.px.w) {
                val c = l.px[x, y]
                if (c.al() < 128) continue
                px.set(x + 1, y + 1, if (dim) R.iron[2] else c)
                m[(y + 1) * SIZE + x + 1] = true
            }
        }
        px.ring(m, if (dim) R.iron[1] else OUT)
        return px
    }
}
