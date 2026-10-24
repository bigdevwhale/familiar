package app.familiar

import android.graphics.Bitmap
import app.familiar.domain.Genre
import app.familiar.domain.Mood
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.ui.pix.Look
import app.familiar.ui.pix.Px
import app.familiar.ui.pix.Sprites
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Paints the sprites to PNG sheets (run with `-Prender`), so they can be looked at without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArt {
    private val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }

    private fun sheet(name: String, cells: List<List<Px?>>, scale: Int = 4, bg: Int = 0xFFF3EBDD.toInt()) {
        val cw = Sprites.SIZE * scale
        val rows = cells.size
        val cols = cells.maxOf { it.size }
        val bmp = Bitmap.createBitmap(cols * cw, rows * cw, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(bg)
        cells.forEachIndexed { r, row ->
            row.forEachIndexed { c, px ->
                if (px == null) return@forEachIndexed
                for (y in 0 until px.h) for (x in 0 until px.w) {
                    val p = px.c[y * px.w + x]
                    val a = (p ushr 24) and 0xFF
                    if (a == 0) continue
                    val col = blend(bg, p, a / 255f)
                    for (j in 0 until scale) for (i in 0 until scale) bmp.setPixel(c * cw + x * scale + i, r * cw + y * scale + j, col)
                }
            }
        }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private val tails = Genre.tails.map { it.argb.toInt() }

    private fun look(sp: Species, st: Stage, mood: Mood = Mood.NONE, stone: Float = 0f) = Look(
        species = sp, stage = st, mood = mood, stone = stone, hatch = 0.6f,
        tails = tails.take(when (st) { Stage.BABY -> 1; Stage.YOUNG -> 3; Stage.ADULT -> 6; else -> 9 }),
        hoard = tails.take(when (st) { Stage.BABY -> 0; Stage.YOUNG -> 3; Stage.ADULT -> 8; else -> 10 }) + tails.take(if (st == Stage.ANCIENT) 4 else 0),
        stars = when (st) { Stage.BABY -> 3; Stage.YOUNG -> 10; Stage.ADULT -> 22; else -> 40 },
        items = listOf("меч", "кольцо", "письмо", "зелье", "ключ", "яблоко", "череп", "корона", "перо", "карта").take(when (st) { Stage.BABY -> 1; Stage.YOUNG -> 4; else -> 10 }),
        blooms = when (st) { Stage.BABY -> 2; Stage.YOUNG -> 4; Stage.ADULT -> 7; else -> 12 },
        wisps = when (st) { Stage.BABY -> 1; Stage.YOUNG -> 2; Stage.ADULT -> 4; else -> 6 },
        patches = tails.take(when (st) { Stage.BABY -> 1; Stage.YOUNG -> 3; Stage.ADULT -> 5; else -> 8 }),
    )

    /**
     * A poster of every familiar through every stage and every grown form, labelled, on the night
     * ground of the icon: docs/evolutions.png (in Russian with `-Plang=ru`, else English).
     */
    @Test
    fun evolutions() {
        val ru = System.getProperty("render.lang") == "ru"
        val forms = app.familiar.domain.Form.entries.drop(1)
        val k = 3
        val cell = Sprites.SIZE * k
        val left = 400
        val top = 260
        val gap = 34
        val cols = Stage.entries.size + forms.size
        val w = left + cols * cell + gap + 40
        val h = top + Species.entries.size * cell + 90
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val bg = android.graphics.Paint().apply { shader = android.graphics.RadialGradient(w / 2f, h * 0.35f, h * 0.9f, 0xFF3A2A6E.toInt(), 0xFF120D22.toInt(), android.graphics.Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val res = System.getProperty("render.res")
        fun font(name: String) = runCatching { android.graphics.Typeface.createFromFile(File(res, "font/$name")) }.getOrDefault(android.graphics.Typeface.DEFAULT_BOLD)
        val serif = font("cormorant_bold.ttf")
        val sans = font("nunito_bold.ttf")
        val title = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 96f; color = 0xFFF6EBD6.toInt() }
        val sub = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 34f; color = 0xFFB9A8E8.toInt() }
        val head = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 30f; color = 0xFFE9DFC8.toInt(); textAlign = android.graphics.Paint.Align.CENTER }
        val name = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 46f; color = 0xFFF6EBD6.toInt() }
        c.drawText(if (ru) "Фамильяры и их эволюции" else "Familiars and how they grow", 60f, 120f, title)
        c.drawText(if (ru) "14 фамильяров · 5 стадий · 5 форм взрослого, по прочитанным книгам" else "14 familiars · 5 stages · 5 grown forms, after the books they ate", 64f, 180f, sub)
        val stages = if (ru) listOf("Яйцо", "Малыш", "Юный", "Взрослый", "Древний") else listOf("Egg", "Hatchling", "Young", "Grown", "Ancient")
        val formNames = if (ru) listOf("Звёздная", "Туманная", "Цветущая", "Мудрая", "Лесная") else listOf("Astral", "Shadow", "Bloom", "Sage", "Wild")
        val formColors = forms.map { app.familiar.ui.pix.mixc((it.argb and 0xFFFFFF).toInt() or (0xFF shl 24), 0xFFFFFFFF.toInt(), 0.35f) }
        stages.forEachIndexed { i, s -> c.drawText(s, left + i * cell + cell / 2f, top - 24f, head) }
        formNames.forEachIndexed { i, s -> c.drawText(s, left + (stages.size + i) * cell + gap + cell / 2f, top - 24f, android.graphics.Paint(head).apply { color = formColors[i] }) }
        val divider = android.graphics.Paint().apply { color = 0x55B9A8E8; strokeWidth = 3f }
        val dx = left + stages.size * cell + gap / 2f
        c.drawLine(dx, top - 60f, dx, h - 60f, divider)
        val names = if (ru) listOf("Мимик", "Кицунэ", "Слайм", "Дракончик", "Феникс", "Грифон", "Совомедведь", "Лунный мотылёк", "Тануки", "Росток", "Призрак", "Книжный червь", "Химера", "Сорока")
        else listOf("Mimic", "Kitsune", "Slime", "Dragon", "Phoenix", "Griffin", "Owlbear", "Moon moth", "Tanuki", "Sprout", "Ghost", "Bookworm", "Chimera", "Magpie")
        fun put(px: Px, x0: Int, y0: Int) {
            for (y in 0 until px.h) for (x in 0 until px.w) {
                val p = px.c[y * px.w + x]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                for (j in 0 until k) for (i in 0 until k) {
                    val bx = x0 + x * k + i; val by = y0 + y * k + j
                    bmp.setPixel(bx, by, blend(bmp.getPixel(bx, by), p, a / 255f))
                }
            }
        }
        Species.entries.forEachIndexed { r, sp ->
            val y0 = top + r * cell
            c.drawText(names[r], 50f, y0 + cell / 2f + 16f, name)
            if (sp.ordinal >= Species.CHIMERA.ordinal) c.drawText(if (ru) "новый!" else "new!", 52f, y0 + cell / 2f + 56f, android.graphics.Paint(sub).apply { textSize = 28f; color = 0xFFF2C14E.toInt() })
            Stage.entries.forEachIndexed { i, st -> put(Sprites.frame(look(sp, st), 0), left + i * cell, y0) }
            forms.forEachIndexed { i, f -> put(Sprites.frame(look(sp, Stage.ADULT).copy(form = f), 2), left + (stages.size + i) * cell + gap, y0) }
        }
        val docs = File(System.getProperty("render.docs") ?: out.path)
        File(if (ru) out else docs, if (ru) "evolutions_ru.png" else "evolutions.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val newcomers = listOf(Species.TANUKI, Species.SPROUT, Species.GHOST, Species.WYRM, Species.CHIMERA, Species.MAGPIE)

    @Test
    fun newcomers() = sheet("newcomers", newcomers.map { sp -> Stage.entries.map { Sprites.frame(look(sp, it), 0) } + listOf(Sprites.frame(look(sp, Stage.ADULT).copy(shiny = true), 0)) }, scale = 4)

    @Test
    fun forms() = sheet("forms", Species.entries.map { sp -> app.familiar.domain.Form.entries.map { Sprites.frame(look(sp, Stage.ADULT).copy(form = it), 2) } }, scale = 2)

    @Test
    fun seasons() = sheet("seasons", listOf(Species.KITSUNE, Species.MIMIC, Species.TANUKI, Species.GHOST).map { sp ->
        app.familiar.domain.Season.entries.map { Sprites.frame(look(sp, Stage.ADULT).copy(season = it), 1) }
    }, scale = 3)

    @Test
    fun trinkets() {
        val all = app.familiar.domain.Trinket.entries
        val cells = all.chunked(5).map { row -> row.map { t -> app.familiar.ui.pix.Trinkets.draw(t).let { p -> Px(Sprites.SIZE, Sprites.SIZE).apply { for (y in 0 until p.h) for (x in 0 until p.w) { val c = p.c[y * p.w + x]; if ((c ushr 24) > 0) for (j in 0 until 6) for (i in 0 until 6) set(7 + x * 6 + i, 7 + y * 6 + j, c) } } } } }
        sheet("trinkets", cells, scale = 2)
    }

    @Test
    fun dreams() {
        val g = Genre.entries
        val w = app.familiar.ui.pix.Dreamscape.W * 4
        val h = app.familiar.ui.pix.Dreamscape.H * 4
        val bmp = Bitmap.createBitmap(w * 4, h * 3, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF2A2030.toInt())
        g.forEachIndexed { i, genre ->
            val px = app.familiar.ui.pix.Dreamscape.scene(genre, 1)
            for (y in 0 until px.h) for (x in 0 until px.w) { val p = px.c[y * px.w + x]; if ((p ushr 24) == 0) continue; for (j in 0 until 4) for (k in 0 until 4) bmp.setPixel((i % 4) * w + x * 4 + k, (i / 4) * h + y * 4 + j, blend(0xFF2A2030.toInt(), p, ((p ushr 24) and 0xFF) / 255f)) }
        }
        File(out, "dreams.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun seasonRooms() {
        val placed = setOf(app.familiar.domain.Item.WINDOW_MOON, app.familiar.domain.Item.BOOKSHELF, app.familiar.domain.Item.RUG_RED, app.familiar.domain.Item.CANDLES)
        val seasons = app.familiar.domain.Season.entries.drop(1)
        val cw = app.familiar.ui.pix.Room.W * 3
        val ch = app.familiar.ui.pix.Room.H * 3
        val bmp = Bitmap.createBitmap(cw * 3, ch * 2, Bitmap.Config.ARGB_8888)
        seasons.forEachIndexed { i, s ->
            val room = app.familiar.ui.pix.Room.scene(placed, tails, i % 2 == 0, 1, s)
            val fox = Sprites.frame(look(Species.KITSUNE, Stage.ADULT).copy(season = s), 0)
            room.blit(fox, (app.familiar.ui.pix.Room.W - fox.w) / 2, app.familiar.ui.pix.Room.FLOOR_Y - fox.h + 3)
            val guest = Sprites.frame(look(Species.TANUKI, Stage.YOUNG), 3)
            room.blit(guest, app.familiar.ui.pix.Room.W / 2 - 44 - guest.w / 2, app.familiar.ui.pix.Room.FLOOR_Y - guest.h + 1)
            for (y in 0 until room.h) for (x in 0 until room.w) { val p = room.c[y * room.w + x]; if ((p ushr 24) == 0) continue; for (j in 0 until 3) for (k in 0 until 3) bmp.setPixel((i % 3) * cw + x * 3 + k, (i / 3) * ch + y * 3 + j, blend(0xFF000000.toInt(), p, ((p ushr 24) and 0xFF) / 255f)) }
        }
        File(out, "season_rooms.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The tree of stories with 0, 12 and 60 books, a few frames each, on the light and the dark ground. */
    @Test
    fun tree() {
        val k = 3
        val w = 128 * k
        val h = 112 * k
        val counts = listOf(0, 12, 60)
        val frames = listOf(0, 3, 6, 9)
        val bmp = Bitmap.createBitmap(w * frames.size, h * counts.size * 2, Bitmap.Config.ARGB_8888)
        for ((d, bg) in listOf(0xFFEDE4D3.toInt(), 0xFF2B2438.toInt()).withIndex()) counts.forEachIndexed { r, n ->
            val fruits = List(n) { tails[it % tails.size] }
            frames.forEachIndexed { c, f ->
                val px = app.familiar.ui.pix.Scenes.tree(fruits, f, d == 1).first
                // The fireflies the tree tab paints on top, as they are at this moment.
                val (core, halo) = if (d == 1) 0xFFF6F7B0.toInt() to 0xFFC9EE6A.toInt() else 0xFFD08A1E.toInt() to 0xFFF2C14E.toInt()
                for (fly in app.familiar.ui.pix.Scenes.fireflies(n, f * 1.3f)) {
                    val fx = fly.x.toInt(); val fy = fly.y.toInt()
                    for (dy in -1..1) for (dx in -1..1) px.set(fx + dx, fy + dy, halo, ((if (dx == 0 || dy == 0) 0.5f else 0.2f) * fly.light * 255).toInt())
                    px.set(fx, fy, core, (fly.light * 255).toInt())
                }
                val y0 = (d * counts.size + r) * h
                for (y in 0 until px.h) for (x in 0 until px.w) {
                    val p = px.c[y * px.w + x]
                    val a = (p ushr 24) and 0xFF
                    val col = if (a == 0) bg else blend(bg, p, a / 255f)
                    for (j in 0 until k) for (i in 0 until k) bmp.setPixel(c * w + x * k + i, y0 + y * k + j, col)
                }
            }
        }
        File(out, "tree.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun stages() = sheet("stages", Species.entries.map { sp -> Stage.entries.map { Sprites.frame(look(sp, it), 0) } }, scale = 3)

    @Test
    fun moods() = sheet("moods", Species.entries.map { sp ->
        Mood.entries.map { Sprites.frame(look(sp, Stage.ADULT, it), 1) } + listOf(Sprites.frame(look(sp, Stage.ADULT, stone = 0.45f), 1), Sprites.frame(look(sp, Stage.ADULT, stone = 1f), 1))
    }, scale = 2)

    @Test
    fun hero() = sheet("hero", Species.entries.chunked(7).map { row -> row.map { Sprites.frame(look(it, Stage.ADULT), 0) } }, scale = 5)

    /** Each familiar alone on a clear ground, ancient and grown, for posts and promo pictures: art/solo/<species>_<stage>.png. */
    @Test
    fun solo() {
        val dir = File(out, "solo").apply { mkdirs() }
        for (sp in Species.entries) for (st in listOf(Stage.ADULT, Stage.ANCIENT)) {
            val px = Sprites.frame(look(sp, st), 0)
            val bmp = Bitmap.createBitmap(px.w, px.h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px.c, 0, px.w, 0, 0, px.w, px.h)
            File(dir, "${sp.name.lowercase()}_${st.name.lowercase()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun anim() = sheet("anim", listOf(Species.KITSUNE, Species.MIMIC, Species.MOTH).map { sp -> List(Sprites.FRAMES) { Sprites.frame(look(sp, Stage.ADULT), it) } }, scale = 2)

    @Test
    fun room() {
        val all = app.familiar.domain.Item.entries
        val a = setOf(app.familiar.domain.Item.WINDOW_MOON, app.familiar.domain.Item.BOOKSHELF, app.familiar.domain.Item.BANNER, app.familiar.domain.Item.LANTERN,
            app.familiar.domain.Item.RUG_RED, app.familiar.domain.Item.CAULDRON, app.familiar.domain.Item.MANDRAKE, app.familiar.domain.Item.CANDLES)
        val b = all.toSet() - a
        fun withFox(room: Px, shiny: Boolean): Px {
            val fox = Sprites.frame(look(Species.KITSUNE, Stage.ADULT).copy(shiny = shiny), 0)
            room.blit(fox, (app.familiar.ui.pix.Room.W - fox.w) / 2, app.familiar.ui.pix.Room.FLOOR_Y - fox.h + 3)
            return room
        }
        val rows = listOf(
            listOf(withFox(app.familiar.ui.pix.Room.scene(a, tails, true, 0), false), withFox(app.familiar.ui.pix.Room.scene(b, tails, false, 1), true), app.familiar.ui.pix.Room.scene(emptySet(), tails, true, 0)),
        )
        val cw = app.familiar.ui.pix.Room.W * 4
        val bmp = Bitmap.createBitmap(cw * 3, app.familiar.ui.pix.Room.H * 4, Bitmap.Config.ARGB_8888)
        rows[0].forEachIndexed { c, px -> for (y in 0 until px.h) for (x in 0 until px.w) { val p = px.c[y * px.w + x]; if ((p ushr 24) == 0) continue; for (j in 0 until 4) for (i in 0 until 4) bmp.setPixel(c * cw + x * 4 + i, y * 4 + j, blend(0xFF000000.toInt(), p, ((p ushr 24) and 0xFF) / 255f)) } }
        File(out, "room.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun shiny() = sheet("shiny", Species.entries.chunked(4).map { row -> row.map { Sprites.frame(look(it, Stage.ADULT).copy(shiny = true), 0) } } +
        listOf(listOf(Sprites.frame(look(Species.KITSUNE, Stage.ADULT).copy(happy = true), 0), Sprites.frame(look(Species.KITSUNE, Stage.ADULT).copy(sleeping = true), 3), app.familiar.ui.pix.Scenes.gramophone(1), Sprites.frame(look(Species.MIMIC, Stage.ADULT).copy(sleeping = true), 5))), scale = 4)

    /** The launcher icon: a kitsune with three tails, pixel for pixel, on the night-violet ground. */
    @Test
    fun icon() {
        val res = File(System.getProperty("render.res") ?: return)
        val px = Sprites.frame(Look(Species.KITSUNE, Stage.ADULT, tails = tails.take(5)), 0)
        var x0 = px.w; var y0 = px.h; var x1 = 0; var y1 = 0
        for (y in 0 until px.h) for (x in 0 until px.w) if ((px.c[y * px.w + x] ushr 24) > 0) { x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y) }
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        for ((dir, side) in listOf("mdpi" to 108, "hdpi" to 162, "xhdpi" to 216, "xxhdpi" to 324, "xxxhdpi" to 432)) {
            val k = maxOf(1, (side * 0.66f / maxOf(w, h)).toInt())
            fun draw(bmp: Bitmap, size: Int, scale: Int) {
                val ox = (size - w * scale) / 2
                val oy = (size - h * scale) / 2
                for (y in 0 until h) for (x in 0 until w) {
                    val p = px.c[(y0 + y) * px.w + x0 + x]
                    if ((p ushr 24) == 0) continue
                    for (j in 0 until scale) for (i in 0 until scale) bmp.setPixel(ox + x * scale + i, oy + y * scale + j, p)
                }
            }
            val fg = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            draw(fg, side, k)
            File(res, "mipmap-$dir").mkdirs()
            File(res, "mipmap-$dir/ic_launcher_foreground.png").outputStream().use { fg.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Legacy square icon (48dp) for launchers without adaptive icons.
            val legacy = side * 48 / 108
            val lg = Bitmap.createBitmap(legacy, legacy, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(lg)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.shader = android.graphics.RadialGradient(legacy / 2f, legacy * 0.44f, legacy * 0.7f, 0xFF4A3488.toInt(), 0xFF17112A.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawRoundRect(0f, 0f, legacy.toFloat(), legacy.toFloat(), legacy * 0.22f, legacy * 0.22f, paint)
            c.drawBitmap(Bitmap.createScaledBitmap(fg, (legacy * 1.25f).toInt(), (legacy * 1.25f).toInt(), true), -legacy * 0.125f, -legacy * 0.125f, null)
            for (name in listOf("ic_launcher", "ic_launcher_round")) File(res, "mipmap-$dir/$name.png").outputStream().use { lg.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // A big one for the README and the website.
        val docs = File(System.getProperty("render.docs") ?: return).apply { mkdirs() }
        val big = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(big)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.RadialGradient(256f, 225f, 360f, 0xFF4A3488.toInt(), 0xFF17112A.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, 512f, 512f, 112f, 112f, paint)
        val k = (512 * 0.78f / maxOf(w, h)).toInt()
        for (y in 0 until h) for (x in 0 until w) {
            val p = px.c[(y0 + y) * px.w + x0 + x]
            if ((p ushr 24) == 0) continue
            for (j in 0 until k) for (i in 0 until k) big.setPixel((512 - w * k) / 2 + x * k + i, (512 - h * k) / 2 + y * k + j, p)
        }
        File(docs, "logo.png").outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
