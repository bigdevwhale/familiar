package app.familiar.ui.pix

import app.familiar.domain.Genre

/**
 * What a familiar dreams of after a book: a drawn scene for every genre (art/dreams, cut by
 * tools/scenery.py), inside a cloud, with a few stars twinkling over it.
 */
object Dreamscape {
    const val W = 128
    const val H = 88
    const val FRAMES = 4

    fun scene(g: Genre, f: Int): Px {
        val px = Px(W, H)
        val drawn = Painted.of("dreams/dream_" + g.name.lowercase())
        if (drawn != null) px.blit(drawn.px, 0, 0)
        else {
            // No picture: a soft sky in the colour of the genre.
            val base = R.around(g.argb.toInt())
            for (y in 0 until H) for (x in 0 until W) px.set(x, y, base[(1 + y * 3 / H + (bayer(x, y) * 0.9f).toInt()).coerceIn(0, 3)])
        }
        // Stars winking in the open sky, each in its own turn; none on a dream with no dark sky.
        // Only the upper part, and only where there is real sky: a few dark pixels on a dancer don't count.
        val sky = drawn?.calm?.filter { it / W < H * 6 / 10 }.orEmpty()
        if (sky.size >= 200) for (i in 0 until 12) {
            if ((i + f) % 4 != 0) continue
            val p = sky[((noise(i, 1, 300) + 0.5f) * sky.size).toInt().coerceIn(0, sky.size - 1)]
            val x = p % W; val y = p / W
            px.set(x, y, WHITE)
            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) px.set(x + dx, y + dy, WHITE, 150)
        }
        cloud(px)
        return px
    }

    /** Cuts the scene into a cloud shape, like a thought bubble. */
    private fun cloud(px: Px) {
        val k = W / 64f
        val c = union(
            ellipse(W / 2f, H / 2f + 2 * k, W * 0.42f, H * 0.36f), ellipse(W * 0.22f, H * 0.56f, 13f * k, 12f * k), ellipse(W * 0.78f, H * 0.56f, 13f * k, 12f * k),
            ellipse(W * 0.36f, H * 0.3f, 14f * k, 12f * k), ellipse(W * 0.64f, H * 0.3f, 14f * k, 12f * k), ellipse(W / 2f, H * 0.78f, 20f * k, 9f * k),
        )
        val m = px.cover(c)
        for (i in m.indices) if (!m[i]) px.put(i, 0)
        // A white rim two pixels thick, then a lilac line round it.
        px.ring(m, WHITE)
        val rim = BooleanArray(m.size) { m[it] || px.c[it].al() > 0 }
        px.ring(rim, WHITE)
        val outer = BooleanArray(m.size) { m[it] || px.c[it].al() > 0 }
        px.ring(outer, R.lilac[3])
    }
}
