package app.familiar.ui.pix

import kotlin.math.abs

/**
 * Drawn pictures of the den and the tree, cut to the app's pixels by tools/scenery.py and kept in
 * resources: each room piece is a whole-room layer, already standing in its spot. Each comes with a copy
 * [DETAIL] times finer (name@2x.png), which is what shows on screen.
 */
internal object Painted {
    /** A picture and the box its drawn pixels fill. */
    class Layer(val px: Px, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val cx get() = (left + right) / 2f
        val w get() = right - left + 1
        val h get() = bottom - top + 1

        /**
         * The night sky in it (a window's glass): deep blue pixels with sky all round them, so a star set on
         * one stays inside the glass. Indices into the picture's pixels.
         */
        val sky: IntArray by lazy {
            fun blue(i: Int): Boolean { val c = px.c[i]; return c.al() > 0 && c.b() > c.r() + 25 && c.b() > c.g() + 10 && lum(c) < 0.4f }
            (0 until px.w * px.h).filter { i ->
                val x = i % px.w; val y = i / px.w
                x in 2 until px.w - 2 && y in 2 until px.h - 2 &&
                    (-2..2).all { dy -> (-2..2).all { dx -> blue((y + dy) * px.w + x + dx) } }
            }.toIntArray()
        }

        /**
         * Open night sky in it: dark pixels in a patch of nearly one colour (no outline, no detail within two
         * pixels), so a twinkling star set there lands on sky and not on a castle or a tree. Indices into the pixels.
         */
        val calm: IntArray by lazy {
            fun close(a: Int, b: Int) = abs(a.r() - b.r()) + abs(a.g() - b.g()) + abs(a.b() - b.b()) < 40
            (0 until px.w * px.h).filter { i ->
                val x = i % px.w; val y = i / px.w
                val c = px.c[i]
                x in 2 until px.w - 2 && y in 2 until px.h - 2 && c.al() > 0 && lum(c) < 0.42f &&
                    (-2..2).all { dy -> (-2..2).all { dx -> close(c, px.c[(y + dy) * px.w + x + dx]) } }
            }.toIntArray()
        }

        /** Where the flames burn in it: the middle of each patch of bright yellow-orange, left to right. */
        val flames: List<Pair<Float, Float>> by lazy {
            val hot = BooleanArray(px.w * px.h) { val c = px.c[it]; c.al() > 0 && c.r() > 225 && c.g() > 150 && c.b() < 140 }
            val seen = BooleanArray(hot.size)
            val out = ArrayList<Triple<Float, Float, Int>>()
            for (start in hot.indices) {
                if (!hot[start] || seen[start]) continue
                var sx = 0f; var sy = 0f; var n = 0
                val stack = ArrayDeque<Int>().apply { add(start) }
                seen[start] = true
                while (stack.isNotEmpty()) {
                    val i = stack.removeLast()
                    val x = i % px.w; val y = i / px.w
                    sx += x; sy += y; n++
                    for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        val nx = x + dx; val ny = y + dy
                        if (nx !in 0 until px.w || ny !in 0 until px.h) continue
                        val j = ny * px.w + nx
                        if (hot[j] && !seen[j]) { seen[j] = true; stack.add(j) }
                    }
                }
                if (n >= 2) out += Triple(sx / n + 0.5f, sy / n + 0.5f, n)
            }
            out.sortedByDescending { it.third }.take(4).map { it.first to it.second }.sortedBy { it.first }
        }
    }

    private val cache = HashMap<String, Layer>()

    /**
     * The picture at resources/[path].png, or null when it is missing. Only one that loaded is kept: one that
     * couldn't be read (the app starting while it was being updated) is tried again next time.
     */
    fun of(path: String): Layer? = synchronized(cache) {
        cache[path] ?: run {
            runCatching {
                val bmp = Painted::class.java.getResourceAsStream("/$path.png")?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    ?: return@runCatching null
                val px = Px(bmp.width, bmp.height)
                bmp.getPixels(px.c, 0, px.w, 0, 0, px.w, px.h)
                bmp.recycle()
                // The same picture DETAIL times finer, when there is one; otherwise in blocks.
                val fine = Painted::class.java.getResourceAsStream("/$path@${DETAIL}x.png")?.use { android.graphics.BitmapFactory.decodeStream(it) }
                if (fine != null && fine.width == px.fw && fine.height == px.fh) fine.getPixels(px.fine, 0, px.fw, 0, 0, px.fw, px.fh) else px.coarsen()
                fine?.recycle()
                var l = px.w; var t = px.h; var r = -1; var b = -1
                for (y in 0 until px.h) for (x in 0 until px.w) if (px.c[y * px.w + x].al() > 0) {
                    l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); b = maxOf(b, y)
                }
                if (r < 0) null else Layer(px, l, t, r, b)
            }.getOrNull()?.also { cache[path] = it }
        }
    }

    /** Lays the picture at [path] over [px], from its top left corner. */
    fun lay(px: Px, path: String): Layer? = of(path)?.also { px.blit(it.px, 0, 0) }

    /** The lines of resources/[path], comments and blanks left out. */
    fun lines(path: String): List<List<String>> =
        (Painted::class.java.getResourceAsStream("/$path")?.bufferedReader()?.use { it.readText() }.orEmpty())
            .lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim().split(Regex("\\s+")) }
}
