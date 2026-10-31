package app.familiar.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import app.familiar.R
import app.familiar.data.Book
import app.familiar.data.Covers
import app.familiar.data.Shelf
import app.familiar.domain.DayPart
import app.familiar.domain.Form
import app.familiar.domain.Genre
import app.familiar.domain.Insights
import app.familiar.domain.Item
import app.familiar.domain.MonthSummary
import app.familiar.domain.OwnGenre
import app.familiar.domain.Stage
import app.familiar.domain.Year
import app.familiar.ui.FamiliarData
import app.familiar.ui.den.qr
import app.familiar.ui.formRes
import app.familiar.ui.genreRes
import app.familiar.ui.hm
import app.familiar.ui.pix.Px
import app.familiar.ui.pix.Room
import app.familiar.ui.pix.Sprites
import app.familiar.ui.speciesRes
import app.familiar.ui.stageRes
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.random.Random

/** Pictures of your reading for stories and posts, each with the way to the app on it. */
object Cards {
    const val PLAY = "https://play.google.com/store/apps/details?id=app.familiar"
    /** The book card with every book on the go rather than one. */
    const val ALL = -1L

    enum class Kind { BOOK, MONTH, YEAR, DEN }

    enum class Format(val w: Int, val h: Int) { STORY(1080, 1920), POST(1080, 1350) }

    /** A candlelit night or warm parchment, like the app itself. */
    enum class Style(
        val top: Int, val bottom: Int, val ink: Int, val muted: Int, val faint: Int,
        val accent: Int, val mana: Int, val gold: Int, val panel: Int, val line: Int, val glow: Int,
    ) {
        NIGHT(
            0xFF2A2042.toInt(), 0xFF110E17.toInt(), 0xFFF6EEDF.toInt(), 0xFFC4B8C8.toInt(), 0xFF8E829C.toInt(),
            0xFFB9A0FF.toInt(), 0xFF72C2FF.toInt(), 0xFFE9C26A.toInt(), 0x14FFFFFF, 0x24FFFFFF, 0x55F2C77A,
        ),
        PARCHMENT(
            0xFFFCF6EA.toInt(), 0xFFEBDCC1.toInt(), 0xFF2B2233.toInt(), 0xFF6E6170.toInt(), 0xFFA59683.toInt(),
            0xFF6A4BB5.toInt(), 0xFF2E86C9.toInt(), 0xFFB4831F.toInt(), 0x8CFFFFFF.toInt(), 0x262B2233, 0x66FFE3A6,
        ),
    }

    data class Request(val kind: Kind, val format: Format, val style: Style, val bookId: Long = 0, val month: YearMonth? = null)

    suspend fun draw(context: Context, data: FamiliarData, r: Request): Bitmap {
        val bmp = Bitmap.createBitmap(r.format.w, r.format.h, Bitmap.Config.ARGB_8888)
        val p = Painter(context, Canvas(bmp), r.format, r.style)
        p.background(r.kind.ordinal)
        val blocks = when (r.kind) {
            Kind.BOOK -> if (r.bookId == ALL) p.books(data, data.reading) else p.book(data, data.book(r.bookId) ?: error("no book"))
            Kind.MONTH -> p.month(data, r.month ?: YearMonth.from(data.stats.today))
            Kind.YEAR -> p.year(data)
            Kind.DEN -> p.den(data)
        }
        p.compose(blocks)
        return bmp
    }
}

/** A piece of a card: how tall it is, how to draw it from a top edge, and how gladly it is left out when space is short (0: never). */
/** The most covers a month card fans out. */
private const val FAN = 12

private class Block(val h: Int, val spare: Int = 0, val draw: (Float) -> Unit)

private class Painter(val context: Context, val cv: Canvas, val f: Cards.Format, val st: Cards.Style) {
    val W = f.w
    val H = f.h
    val story = f == Cards.Format.STORY
    val M = 80
    val CW = W - 2 * M
    val serif: Typeface = ResourcesCompat.getFont(context, R.font.cormorant_bold) ?: Typeface.SERIF
    val sans: Typeface = ResourcesCompat.getFont(context, R.font.nunito_semibold) ?: Typeface.SANS_SERIF
    val bold: Typeface = ResourcesCompat.getFont(context, R.font.nunito_bold) ?: Typeface.DEFAULT_BOLD
    val locale: Locale = context.resources.configuration.locales[0]
    fun str(id: Int, vararg a: Any): String = context.getString(id, *a)
    fun plural(id: Int, n: Int, vararg a: Any): String = context.resources.getQuantityString(id, n, *a)
    fun num(n: Int): String = String.format(locale, "%,d", n)

    fun paint(size: Float, color: Int, face: Typeface = sans, align: Paint.Align = Paint.Align.CENTER) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face; textSize = size; this.color = color; textAlign = align; fontFeatureSettings = "lnum"
    }

    // ---- The ground ----

    fun background(seed: Int) {
        cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), st.top, st.bottom, Shader.TileMode.CLAMP) })
        // Mana motes and stars, square like the pixels of the familiar.
        val rnd = Random(seed * 7919 + H)
        val night = st == Cards.Style.NIGHT
        val dot = Paint()
        repeat(if (story) 90 else 64) {
            val k = if (rnd.nextInt(6) == 0) 12 else 6
            val x = (rnd.nextInt(W) / 6 * 6).toFloat()
            val y = (rnd.nextInt(H) / 6 * 6).toFloat()
            // Most of the sky is kept to the edges, clear of the words.
            if (x > M + 60 && x < W - M - 60 && rnd.nextInt(3) > 0) return@repeat
            val c = listOf(st.gold, st.accent, st.mana, if (night) 0xFFFFFFFF.toInt() else st.faint)[rnd.nextInt(4)]
            dot.color = ColorUtils.setAlphaComponent(c, rnd.nextInt(if (night) 40 else 30, if (night) 150 else 95))
            cv.drawRect(x, y, x + k, y + k, dot)
            if (k == 12 && rnd.nextBoolean()) {
                dot.color = ColorUtils.setAlphaComponent(c, dot.alpha / 2)
                cv.drawRect(x - 12, y, x, y + k, dot); cv.drawRect(x + k, y, x + k + 12, y + k, dot)
                cv.drawRect(x, y - 12, x + k, y, dot); cv.drawRect(x, y + k, x + k, y + k + 12, dot)
            }
        }
        // A thin gilt frame, as on the cover of an old book.
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = ColorUtils.setAlphaComponent(st.gold, 90) }
        cv.drawRoundRect(RectF(30f, 30f, W - 30f, H - 30f), 44f, 44f, frame)
    }

    fun glow(cx: Float, cy: Float, r: Float) {
        cv.drawCircle(cx, cy, r, Paint().apply { shader = RadialGradient(cx, cy, r, st.glow, 0x00000000, Shader.TileMode.CLAMP) })
    }

    // ---- Laying out ----

    /** Spreads the blocks between the top and the footer, dropping the most spare ones that don't fit. */
    fun compose(blocks: List<Block>) {
        val top = if (story) 170 else 84
        val footerH = if (story) 224 else 196
        val footerTop = H - (if (story) 130 else 64) - footerH
        val bottom = footerTop - 36
        val minGap = 26
        var list = blocks
        while (list.sumOf { it.h } + minGap * (list.size - 1) > bottom - top && list.any { it.spare > 0 }) {
            val drop = list.filter { it.spare > 0 }.maxBy { it.spare }
            list = list - drop
        }
        val free = bottom - top - list.sumOf { it.h }
        val gap = (free / (list.size + 1)).coerceIn(minGap, if (story) 90 else 56)
        val used = list.sumOf { it.h } + gap * (list.size - 1)
        var y = top + ((bottom - top - used) / 2).coerceAtLeast(0).toFloat()
        list.forEach { b -> b.draw(y); y += b.h + gap }
        footer(footerTop.toFloat(), footerH)
    }

    /** The way to the app: what it is, that it's free, and a code that opens it on Google Play. */
    fun footer(y: Float, h: Int) {
        panel(M.toFloat(), y, (W - M).toFloat(), y + h, 40f)
        val pad = 24f
        val q = h - 2 * pad.toInt()
        val tile = RectF(M + pad, y + pad, M + pad + q, y + pad + q)
        cv.drawRoundRect(tile, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        val code = qr(Cards.PLAY, q - 20)
        cv.drawBitmap(code, null, RectF(tile.left + 10, tile.top + 10, tile.right - 10, tile.bottom - 10), Paint())
        val x = tile.right + 34
        val w = W - M - pad - x
        val name = paint(if (story) 64f else 58f, st.ink, serif, Paint.Align.LEFT)
        cv.drawText(str(R.string.app_name), x, y + h * 0.36f, name)
        val line = shrink(str(R.string.card_cta_line), paint(31f, st.muted, sans, Paint.Align.LEFT), w)
        cv.drawText(str(R.string.card_cta_line), x, y + h * 0.60f, line)
        val free = shrink(str(R.string.card_cta_free), paint(29f, st.gold, bold, Paint.Align.LEFT), w)
        cv.drawText(str(R.string.card_cta_free), x, y + h * 0.81f, free)
    }

    // ---- Pieces ----

    fun panel(l: Float, t: Float, r: Float, b: Float, radius: Float = 32f) {
        val rect = RectF(l, t, r, b)
        // Opaque, so no star shows through the numbers: the ground at that height with the panel's tint over it.
        val ground = ColorUtils.blendARGB(st.top, st.bottom, ((t + b) / 2 / H).coerceIn(0f, 1f))
        cv.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.compositeColors(st.panel, ground) })
        cv.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = st.line })
    }

    /** Makes [p] smaller until [text] fits [width]. */
    fun shrink(text: String, p: TextPaint, width: Float, min: Float = p.textSize * 0.6f): TextPaint {
        while (p.measureText(text) > width && p.textSize > min) p.textSize -= 1f
        return p
    }

    fun layout(text: String, p: TextPaint, width: Int, lines: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER): StaticLayout {
        val tp = TextPaint(p).apply { textAlign = Paint.Align.LEFT }
        return StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(align).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 0.92f).setIncludePad(false).build()
    }

    fun drawLayout(l: StaticLayout, x: Float, y: Float) {
        cv.save(); cv.translate(x, y); l.draw(cv); cv.restore()
    }

    fun eyebrow(text: String): Block = Block(46) { y ->
        val p = paint(32f, st.gold, bold).apply { letterSpacing = 0.22f }
        val t = text.uppercase(locale)
        shrink(t, p, CW - 200f)
        val tw = p.measureText(t)
        cv.drawText(t, W / 2f, y + 36, p)
        val line = Paint().apply { color = ColorUtils.setAlphaComponent(st.gold, 150) }
        val cy = y + 25
        cv.drawRect(W / 2f - tw / 2 - 96, cy - 1.5f, W / 2f - tw / 2 - 30, cy + 1.5f, line)
        cv.drawRect(W / 2f + tw / 2 + 30, cy - 1.5f, W / 2f + tw / 2 + 96, cy + 1.5f, line)
        cv.drawRect(W / 2f - tw / 2 - 112, cy - 6, W / 2f - tw / 2 - 100, cy + 6, line)
        cv.drawRect(W / 2f + tw / 2 + 100, cy - 6, W / 2f + tw / 2 + 112, cy + 6, line)
    }

    fun title(text: String, size: Float, color: Int = st.ink, lines: Int = 2): Block {
        val l = layout(text, paint(size, color, serif), CW, lines)
        return Block(l.height) { y -> drawLayout(l, M.toFloat(), y) }
    }

    fun line(text: String, size: Float, color: Int, face: Typeface = sans, spare: Int = 0): Block {
        val p = shrink(text, paint(size, color, face), CW.toFloat())
        return Block((size * 1.3f).toInt(), spare) { y -> cv.drawText(text, W / 2f, y + size, p) }
    }

    /** Numbers with a caption under each, side by side. */
    fun tiles(items: List<Pair<String, String>>, h: Int = if (story) 184 else 160, value: Float = if (story) 76f else 66f, spare: Int = 0): Block {
        val gap = 20
        val n = items.size
        val tw = (CW - gap * (n - 1)) / n
        return Block(h, spare) { y ->
            items.forEachIndexed { i, (v, label) ->
                val x = (M + i * (tw + gap)).toFloat()
                panel(x, y, x + tw, y + h)
                val vp = shrink(v, paint(value, st.ink, serif), tw - 28f)
                cv.drawText(v, x + tw / 2, y + h * 0.52f, vp)
                val lp = shrink(label, paint(if (n > 3) 25f else 28f, st.muted), tw - 24f)
                cv.drawText(label, x + tw / 2, y + h * 0.80f, lp)
            }
        }
    }

    fun bar(x: Float, y: Float, w: Float, h: Float, fraction: Float) {
        val r = h / 2
        cv.drawRoundRect(RectF(x, y, x + w, y + h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.line })
        val fw = (w * fraction.coerceIn(0f, 1f)).coerceAtLeast(h)
        cv.drawRoundRect(RectF(x, y, x + fw, y + h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x, 0f, x + w, 0f, st.accent, st.mana, Shader.TileMode.CLAMP)
        })
    }

    /** Pixels [k] times bigger, crisp. */
    fun pix(px: Px, x: Float, y: Float, k: Int) {
        val img = Bitmap.createBitmap(px.c, px.w, px.h, Bitmap.Config.ARGB_8888)
        cv.drawBitmap(img, null, RectF(x, y, x + px.w * k, y + px.h * k), Paint().apply { isFilterBitmap = false })
    }

    fun familiar(data: FamiliarData): Px? = data.look()?.let { Sprites.frame(it.copy(happy = true), 0) }

    /** The familiar alone in a warm glow. */
    fun hero(data: FamiliarData, k: Int, spare: Int): Block? {
        val px = familiar(data) ?: return null
        val s = px.w * k
        return Block(px.h * k, spare) { y ->
            glow(W / 2f, y + s * 0.55f, s * 0.75f)
            pix(px, (W - s) / 2f, y, k)
        }
    }

    fun bookArgb(b: Book): Int = (OwnGenre.of(b)?.argb ?: Genre.of(b.genre).argb).toInt()

    suspend fun coverOf(b: Book): Bitmap? = runCatching { Covers.load(context, b.cover)?.asAndroidBitmap() }.getOrNull()

    /** A book lying on the card: its real cover, or one drawn in its genre's colour with the title in gold. */
    fun cover(b: Book, img: Bitmap?, x: Float, y: Float, w: Float, h: Float) {
        val r = w * 0.045f
        val rect = RectF(x, y, x + w, y + h)
        cv.drawRoundRect(RectF(x + w * 0.02f, y + h * 0.03f, x + w * 1.02f, y + h * 1.03f), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x70000000; maskFilter = BlurMaskFilter(w * 0.06f, BlurMaskFilter.Blur.NORMAL)
        })
        cv.save()
        cv.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        if (img != null) {
            // Cropped to fill, like on the shelf.
            val sr = img.width / img.height.toFloat()
            val dr = w / h
            val src = if (sr > dr) {
                val cw = (img.height * dr).toInt(); Rect((img.width - cw) / 2, 0, (img.width + cw) / 2, img.height)
            } else {
                val ch = (img.width / dr).toInt(); Rect(0, (img.height - ch) / 2, img.width, (img.height + ch) / 2)
            }
            cv.drawBitmap(img, src, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val c = bookArgb(b)
            cv.drawRect(rect, Paint().apply {
                shader = LinearGradient(x, y, x + w, y + h, intArrayOf(ColorUtils.blendARGB(c, -1, 0.18f), c, ColorUtils.blendARGB(c, 0xFF000000.toInt(), 0.28f)), null, Shader.TileMode.CLAMP)
            })
            val gold = Paint().apply { color = 0xCCF2D58A.toInt() }
            val t = h * 0.012f
            cv.drawRect(x + w * 0.16f, y + h * 0.15f, x + w * 0.9f, y + h * 0.15f + t, gold)
            cv.drawRect(x + w * 0.16f, y + h * 0.85f - t, x + w * 0.9f, y + h * 0.85f, gold)
            val l = layout(b.title, paint(w * 0.12f, 0xFFFFF6E4.toInt(), serif), (w * 0.7f).toInt(), 4)
            drawLayout(l, x + w * 0.18f, y + (h - l.height) / 2)
        }
        // The spine's shadow and a soft sheen.
        cv.drawRect(x, y, x + w * 0.06f, y + h, Paint().apply { color = 0x2E000000 })
        cv.drawRect(x + w * 0.06f, y, x + w * 0.075f, y + h, Paint().apply { color = 0x22FFFFFF })
        cv.restore()
    }

    // ---- The cards ----

    /** Every book on the go, each with its cover and how far along it is. */
    suspend fun books(data: FamiliarData, list: List<Book>): List<Block> {
        val s = data.stats
        val shown = list.take(4)
        val covers = shown.map { coverOf(it) }
        // Fewer books, bigger covers.
        val cw = when (shown.size) { 2 -> if (story) 220f else 150f; 3 -> if (story) 180f else 124f; else -> if (story) 150f else 108f }
        val ch = cw * 1.5f
        val row = ch + if (story) 44 else 30
        return buildList {
            hero(data, 3, 2)?.let { add(it) }
            add(eyebrow(str(R.string.card_now_reading)))
            add(title(plural(R.plurals.card_at_once, list.size, list.size), if (story) 84f else 70f, lines = 1))
            add(Block((row * shown.size - (row - ch)).toInt()) { y ->
                shown.forEachIndexed { i, b ->
                    val top = y + i * row
                    val x = M + 30f
                    cover(b, covers[i], x, top, cw, ch)
                    val tx = x + cw + 40
                    val tw = (W - M - 30 - tx).toInt()
                    val t = layout(b.title, paint(if (story) 52f else 42f, st.ink, serif), tw, 2, Layout.Alignment.ALIGN_NORMAL)
                    val block = t.height + (if (b.author.isNotBlank()) 46 else 0) + (if (b.pages > 0) 60 else 0)
                    var yy = top + (ch - block) / 2
                    drawLayout(t, tx, yy)
                    yy += t.height
                    if (b.author.isNotBlank()) {
                        cv.drawText(b.author, tx, yy + 36, shrink(b.author, paint(30f, st.muted, sans, Paint.Align.LEFT), tw.toFloat()))
                        yy += 46
                    }
                    if (b.pages > 0) {
                        bar(tx, yy + 22, tw - 110f, 18f, b.fraction)
                        cv.drawText("${Book.percentOf(b.pages, b.page)}%", tx + tw, yy + 40, paint(34f, st.accent, bold, Paint.Align.RIGHT))
                    }
                }
            })
            if (list.size > shown.size) add(line(str(R.string.card_and_more, list.size - shown.size), 34f, st.muted))
            val year = Year(s, s.today.year).books.size
            add(tiles(listOfNotNull(
                s.streak.takeIf { it > 0 }?.let { num(it) to plural(R.plurals.card_l_streak, it) },
                num(year) to plural(R.plurals.card_l_books_year, year, s.today.year),
                Insights(s).pace?.let { num(it) to str(R.string.card_l_pace) },
            ), spare = 1))
        }
    }

    suspend fun book(data: FamiliarData, b: Book): List<Block> {
        val s = data.stats
        val done = b.shelfOf == Shelf.DONE
        val img = coverOf(b)
        val px = familiar(data)
        val cw = if (story) 430f else 300f
        val ch = cw * 1.5f
        val k = 3
        val sprite = (px?.w ?: 0) * k
        val mine = data.readings.filter { it.bookId == b.id }
        return buildList {
            add(eyebrow(str(if (done) R.string.card_finished else R.string.card_now_reading)))
            add(Block((ch + if (px != null) 40 else 0).toInt()) { y ->
                // The cover leads; the familiar sits at its foot, beside it rather than over it.
                val cx = W / 2f - if (px != null) sprite * 0.32f else 0f
                glow(W / 2f, y + ch / 2, ch * 0.75f)
                cover(b, img, cx - cw / 2, y, cw, ch)
                if (px != null) pix(px, cx + cw / 2 - sprite * 0.12f, y + ch + 40 - px.h * k, k)
            })
            add(title(b.title, if (story) 80f else 64f))
            if (b.author.isNotBlank()) add(line(b.author, if (story) 40f else 34f, st.muted))
            if (done) {
                val stars = b.rating ?: 0
                val days = b.started?.let { st0 -> b.finished?.let { ((it - st0) / 86_400_000L).toInt() + 1 } }
                add(Block(if (stars > 0 && days != null) 130 else 70) { y ->
                    var yy = y
                    if (stars > 0) {
                        val p = paint(64f, st.gold, sans)
                        cv.drawText("★".repeat(stars) + "☆".repeat(5 - stars), W / 2f, yy + 58, p)
                        yy += 76
                    }
                    days?.let { cv.drawText(plural(R.plurals.card_read_in, it, it), W / 2f, yy + 40, paint(34f, st.muted)) }
                })
            } else if (b.pages > 0) {
                val forecast = Insights.forecast(b, s)
                add(Block(if (forecast != null) 136 else 92) { y ->
                    val left = if (b.audio) str(R.string.card_minute_of, hm(b.page), hm(b.pages)) else str(R.string.card_page_of, b.page, b.pages)
                    cv.drawText(left, M + 40f, y + 34, paint(32f, st.muted, sans, Paint.Align.LEFT))
                    cv.drawText("${Book.percentOf(b.pages, b.page)}%", W - M - 40f, y + 36, paint(40f, st.accent, bold, Paint.Align.RIGHT))
                    bar(M + 40f, y + 56, CW - 80f, 24f, b.fraction)
                    forecast?.let {
                        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(it).removeSuffix(" г.")
                        cv.drawText(str(R.string.forecast, date), W / 2f, y + 128, paint(30f, st.faint))
                    }
                })
            } else if (b.page > 0) add(line(if (b.audio) hm(b.page) else str(R.string.card_page, b.page), 34f, st.muted))
            val minutes = mine.filter { it.timed }.sumOf { it.minutes }
            val year = Year(s, s.today.year).books.size
            add(tiles(listOfNotNull(
                s.streak.takeIf { it > 0 }?.let { num(it) to plural(R.plurals.card_l_streak, it) },
                num(year) to plural(R.plurals.card_l_books_year, year, s.today.year),
                minutes.takeIf { it > 0 }?.let { hm(it) to str(R.string.card_l_with_book) },
            ), spare = 1))
        }
    }

    suspend fun month(data: FamiliarData, month: YearMonth): List<Block> {
        val s = data.stats
        val m = MonthSummary(s, month)
        // Oldest first, so the latest lies on top of the fan; past a dozen the rest become "+N".
        val all = if (m.finished.isNotEmpty()) m.finished else m.opened.reversed()
        val list = all.takeLast(if (all.size > FAN) FAN - 1 else FAN)
        val more = all.size - list.size
        val covers = list.map { coverOf(it) }
        return buildList {
            add(eyebrow(month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year))
            add(title(str(R.string.card_month_title), if (story) 92f else 76f, lines = 1))
            val hours = m.minutes / 60
            add(tiles(listOf(
                num(m.finished.size) to plural(R.plurals.card_l_books, m.finished.size),
                num(m.pages) to plural(R.plurals.card_l_pages, m.pages),
                (if (m.minutes in 1..59) hm(m.minutes) else num(hours)) to plural(R.plurals.card_l_hours, hours),
                num(m.days.size) to plural(R.plurals.card_l_days, m.days.size),
            ), value = if (story) 70f else 60f))
            if (list.isNotEmpty()) {
                val gap = 26f
                // Up to four lie side by side; more fan out over one another in the same row.
                val cw = (CW - gap * 3) / 4 * (if (story) 1f else 0.8f)
                val ch = cw * 1.5f
                val n = list.size + if (more > 0) 1 else 0
                val step = if (n <= 4) cw + gap else (CW - 40 - cw) / (n - 1)
                val span = cw + step * (n - 1)
                add(Block((58 + ch + 24).toInt()) { y ->
                    cv.drawText(str(if (m.finished.isNotEmpty()) R.string.card_finished_books else R.string.card_opened_books), W / 2f, y + 32, paint(30f, st.faint, bold).apply { letterSpacing = 0.12f })
                    val x0 = (W - span) / 2
                    list.forEachIndexed { i, b ->
                        // A gentle wave, so the fan looks laid by hand.
                        val dy = if (n > 4 && i % 2 == 1) 14f else 0f
                        cover(b, covers[i], x0 + i * step, y + 58 + dy, cw, ch)
                    }
                    if (more > 0) {
                        val x = x0 + list.size * step
                        val rect = RectF(x, y + 58, x + cw, y + 58 + ch)
                        val ground = ColorUtils.blendARGB(st.top, st.bottom, (rect.centerY() / H).coerceIn(0f, 1f))
                        cv.drawRoundRect(rect, cw * 0.045f, cw * 0.045f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.blendARGB(ground, st.accent, 0.35f) })
                        cv.drawRoundRect(rect, cw * 0.045f, cw * 0.045f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = st.accent })
                        cv.drawText("+$more", rect.centerX(), rect.centerY() + cw * 0.1f, paint(cw * 0.3f, st.ink, serif))
                    }
                })
            }
            add(calendar(m, data))
        }
    }

    /** Every day of the month as a square, deeper the more was read and gold the day a book was finished, with the familiar beside it. */
    fun calendar(m: MonthSummary, data: FamiliarData): Block {
        val s = data.stats
        val goal = data.settings.dailyGoal
        val px = familiar(data)
        val k = 3
        val first = WeekFields.of(locale).firstDayOfWeek
        val start = m.month.atDay(1)
        val lead = Math.floorMod(start.dayOfWeek.value - first.value, 7)
        val rows = (lead + m.month.lengthOfMonth() + 6) / 7
        val c = if (story) 60f else 46f
        val g = 12f
        val ends = m.finished.mapNotNull { it.finished }.map { Instant.ofEpochMilli(it).atZone(s.zone).toLocalDate() }.toSet()
        val today = s.today
        val gw = 7 * c + 6 * g
        val side = (px?.w ?: 0) * k
        val gh = rows * (c + g) - g
        return Block((52 + maxOf(gh, (px?.h ?: 0) * k * 0.8f)).toInt(), spare = 1) { y ->
            val x0 = (W - gw - side) / 2
            cv.drawText(str(R.string.card_calendar), x0 + gw / 2, y + 32, paint(30f, st.faint, bold).apply { letterSpacing = 0.12f })
            if (px != null) {
                val fx = x0 + gw + 10
                val fy = y + 52 + gh - px.h * k + 12
                glow(fx + side / 2f, fy + side * 0.6f, side * 0.7f)
                pix(px, fx, fy, k)
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = st.line }
            for (d in 1..m.month.lengthOfMonth()) {
                val i = lead + d - 1
                val date = m.month.atDay(d)
                val x = x0 + (i % 7) * (c + g)
                val yy = y + 52 + (i / 7) * (c + g)
                val rect = RectF(x, yy, x + c, yy + c)
                val amount = m.days[date] ?: 0
                when {
                    date in ends -> { fill.color = st.gold; cv.drawRoundRect(rect, 10f, 10f, fill) }
                    amount > 0 -> {
                        val level = if (goal <= 0) 1f else (amount / goal.toFloat()).coerceIn(0.35f, 1f)
                        fill.color = ColorUtils.setAlphaComponent(st.accent, (255 * level).toInt())
                        cv.drawRoundRect(rect, 10f, 10f, fill)
                    }
                    date.isAfter(today) -> cv.drawRoundRect(rect, 10f, 10f, edge)
                    else -> { fill.color = st.line; cv.drawRoundRect(rect, 10f, 10f, fill) }
                }
            }
        }
    }

    fun year(data: FamiliarData): List<Block> {
        val s = data.stats
        val y = Year(s, s.today.year)
        val n = Insights(s)
        val minutes = s.readings.filter { it.timed && Instant.ofEpochMilli(it.end).atZone(s.zone).year == y.year }.sumOf { it.minutes }
        val hours = minutes / 60
        val goal = data.settings.yearGoal
        return buildList {
            hero(data, 3, 2)?.let { add(it) }
            add(eyebrow(str(R.string.card_year_eyebrow)))
            add(title(y.year.toString(), if (story) 170f else 130f, st.gold, 1))
            add(tiles(listOf(
                num(y.books.size) to plural(R.plurals.card_l_books, y.books.size),
                num(y.pages) to plural(R.plurals.card_l_pages, y.pages),
                (if (minutes in 1..59) hm(minutes) else num(hours)) to plural(R.plurals.card_l_hours, hours),
                num(y.bestStreak) to plural(R.plurals.card_l_streak_best, y.bestStreak),
            ), value = if (story) 70f else 60f))
            if (goal > 0) add(Block(84) { top ->
                cv.drawText(str(R.string.goal_of, y.books.size, goal), M + 40f, top + 34, paint(34f, st.ink, bold, Paint.Align.LEFT))
                cv.drawText("${(y.books.size * 100 / goal).coerceAtMost(100)}%", W - M - 40f, top + 34, paint(34f, st.accent, bold, Paint.Align.RIGHT))
                bar(M + 40f, top + 56, CW - 80f, 24f, y.books.size / goal.toFloat())
            })
            add(months(n))
            val facts = listOfNotNull(
                y.genre?.takeIf { it != Genre.OTHER }?.let { str(R.string.card_genre, str(genreRes(it)).lowercase(locale)) to it.argb.toInt() },
                n.bestPart?.let { str(partBest(it)) to st.accent },
                n.pace?.let { str(R.string.stats_pace_value, it) to st.mana },
            )
            if (facts.isNotEmpty()) add(Block(facts.size * 56, spare = 3) { top ->
                facts.forEachIndexed { i, (text, color) ->
                    val p = shrink(text, paint(36f, st.ink), CW - 60f)
                    val w = p.measureText(text)
                    val yy = top + i * 56 + 38
                    cv.drawRect(W / 2f - w / 2 - 36, yy - 22, W / 2f - w / 2 - 20, yy - 6, Paint().apply { this.color = color })
                    cv.drawText(text, W / 2f + 8, yy, p)
                }
            })
        }
    }

    /** The last six months as columns, this one bright. */
    fun months(n: Insights): Block {
        val h = if (story) 300 else 230
        return Block(h, spare = 1) { y ->
            val months = n.months
            val top = months.maxOf { it.amount }.coerceAtLeast(1).toFloat()
            val gap = 22f
            val cw = (CW - 80 - gap * (months.size - 1)) / months.size
            val base = y + h - 46
            val tall = h - 46 - 52f
            months.forEachIndexed { i, m ->
                val x = M + 40 + i * (cw + gap)
                val bh = (tall * m.amount / top).coerceAtLeast(8f)
                val now = i == months.size - 1
                cv.drawRoundRect(RectF(x, base - bh, x + cw, base), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (now) st.accent else ColorUtils.setAlphaComponent(st.accent, 70)
                })
                if (m.amount > 0) cv.drawText(num(m.amount), x + cw / 2, base - bh - 14, shrink(num(m.amount), paint(26f, if (now) st.ink else st.muted, bold), cw + gap))
                cv.drawText(m.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.'), x + cw / 2, base + 38, paint(26f, if (now) st.ink else st.faint, bold))
            }
        }
    }

    fun den(data: FamiliarData): List<Block> {
        val pet = data.pet ?: error("no familiar")
        val sp = data.species ?: error("no familiar")
        val s = data.stats
        val k = if (story) 7 else 6
        val hour = LocalTime.now().hour
        val room = Room.scene(data.settings.placed.mapNotNull { Item.of(it) }.toSet(), s.finished.map { Genre.of(it.genre).argb.toInt() }.takeLast(24), hour < 6 || hour >= 20, Room.STEP, data.season)
        data.guestLook?.let { g ->
            val img = Sprites.frame(g, 3 * Sprites.STEP)
            room.blit(img, Room.W / 2 - 44 - img.w / 2, Room.FLOOR_Y - img.h + 1)
        }
        familiar(data)?.let { room.blit(it, (Room.W - it.w) / 2, Room.FLOOR_Y - it.h + 3) }
        val form = Form.of(pet.form)
        val kind = listOfNotNull(
            str(speciesRes(sp)), str(stageRes(s.stage)),
            form.takeIf { it != Form.NONE && s.stage >= Stage.ADULT }?.let { str(formRes(it)) },
        ).joinToString(" · ")
        val year = Year(s, s.today.year)
        val facts = listOfNotNull(
            s.streak.takeIf { it > 1 }?.let { str(R.string.postcard_streak, plural(R.plurals.days, it, it)) },
            year.books.size.takeIf { it > 0 }?.let { str(R.string.postcard_year, plural(R.plurals.books, it, it), year.year) },
        )
        return buildList {
            add(Block(Room.H * k) { y ->
                val w = Room.W * k
                val x = (W - w) / 2f
                val rect = RectF(x, y, x + w, y + Room.H * k)
                cv.drawRoundRect(RectF(rect.left, rect.top + 18, rect.right, rect.bottom + 18), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x60000000; maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
                })
                cv.save()
                cv.clipPath(Path().apply { addRoundRect(rect, 28f, 28f, Path.Direction.CW) })
                pix(room, x, y, k)
                cv.restore()
                cv.drawRoundRect(rect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = ColorUtils.setAlphaComponent(st.gold, 160) })
            })
            add(title((if (pet.shiny) "✦ " else "") + pet.name, if (story) 110f else 92f, lines = 1))
            add(line(kind, 38f, st.muted))
            data.reading.firstOrNull()?.let { add(line(str(R.string.postcard_reading, it.title), 38f, st.accent, bold, spare = 1)) }
            if (facts.isNotEmpty()) add(line(facts.joinToString("  ·  "), 34f, st.ink, sans, spare = 2))
        }
    }

    private fun partBest(p: DayPart): Int = when (p) {
        DayPart.MORNING -> R.string.stats_best_morning
        DayPart.DAY -> R.string.stats_best_day
        DayPart.EVENING -> R.string.stats_best_evening
        DayPart.NIGHT -> R.string.stats_best_night
    }
}
