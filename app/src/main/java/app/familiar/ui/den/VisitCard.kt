package app.familiar.ui.den

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import app.familiar.R
import app.familiar.data.Book
import app.familiar.domain.Form
import app.familiar.domain.Genre
import app.familiar.domain.Item
import app.familiar.domain.Sky
import app.familiar.domain.Stage
import app.familiar.ui.FamiliarData
import app.familiar.ui.formRes
import app.familiar.ui.pix.DETAIL
import app.familiar.ui.pix.Px
import app.familiar.ui.pix.Room
import app.familiar.ui.pix.Sprites
import app.familiar.ui.speciesRes
import app.familiar.ui.stageRes
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * The visiting card as a postcard worth sending: the familiar at home in its den under a starry night, its
 * name and kind, the book it recommends, and the code as a postage stamp with a postmark, to scan from the
 * picture or from the screen.
 */
object VisitCard {
    private const val W = 1080
    private const val H = 1800
    private const val M = 84

    private const val TOP = 0xFF2A2042.toInt()
    private const val BOTTOM = 0xFF110E17.toInt()
    private const val INK = 0xFFF6EEDF.toInt()
    private const val MUTED = 0xFFC4B8C8.toInt()
    private const val FAINT = 0xFF8E829C.toInt()
    private const val GOLD = 0xFFE9C26A.toInt()
    private const val ACCENT = 0xFFB9A0FF.toInt()
    private const val PAPER = 0xFFFBF5E8.toInt()

    fun draw(context: Context, data: FamiliarData, card: String, book: Book?): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val pet = data.pet ?: return bmp
        val sp = data.species ?: return bmp
        val locale: Locale = context.resources.configuration.locales[0]
        val serif = ResourcesCompat.getFont(context, R.font.cormorant_bold) ?: Typeface.SERIF
        val sans = ResourcesCompat.getFont(context, R.font.nunito_semibold) ?: Typeface.SANS_SERIF
        val bold = ResourcesCompat.getFont(context, R.font.nunito_bold) ?: Typeface.DEFAULT_BOLD
        fun paint(size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.CENTER) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; textSize = size; this.color = color; textAlign = align
        }
        fun fit(text: String, p: TextPaint, width: Float): TextPaint { while (p.measureText(text) > width && p.textSize > 20f) p.textSize -= 1f; return p }

        ground(cv)

        // What the card is, between two little rules.
        val eyebrow = context.getString(R.string.visit_card_eyebrow).uppercase(locale)
        val ep = fit(eyebrow, paint(32f, GOLD, bold).apply { letterSpacing = 0.2f }, W - 2f * M - 220)
        cv.drawText(eyebrow, W / 2f, 132f, ep)
        val tw = ep.measureText(eyebrow)
        val rule = Paint().apply { color = GOLD; alpha = 150 }
        cv.drawRect(W / 2f - tw / 2 - 96, 119f, W / 2f - tw / 2 - 28, 122f, rule)
        cv.drawRect(W / 2f + tw / 2 + 28, 119f, W / 2f + tw / 2 + 96, 122f, rule)

        // The familiar at home: its own den, with everything that stands in it.
        val k = 6
        val den = RectF((W - Room.W * k) / 2f, 176f, (W + Room.W * k) / 2f, 176f + Room.H * k)
        cv.drawRoundRect(RectF(den.left, den.top + 18, den.right, den.bottom + 18), 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x70000000; maskFilter = BlurMaskFilter(34f, BlurMaskFilter.Blur.NORMAL)
        })
        cv.save()
        cv.clipPath(Path().apply { addRoundRect(den, 34f, 34f, Path.Direction.CW) })
        cv.drawBitmap(fine(home(data)), null, den, Paint().apply { isFilterBitmap = false })
        cv.restore()
        cv.drawRoundRect(den, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = GOLD; alpha = 190 })

        // Who is coming.
        var y = den.bottom + 104
        val name = (if (pet.shiny) "✦ " else "") + pet.name
        cv.drawText(name, W / 2f, y, fit(name, paint(104f, INK, serif), W - 2f * M))
        val form = Form.of(pet.form)
        val stage = Stage.of(pet.mana)
        val kind = listOfNotNull(
            context.getString(speciesRes(sp)), context.getString(stageRes(stage)),
            form.takeIf { it != Form.NONE && stage >= Stage.ADULT }?.let { context.getString(formRes(it)) },
        ).joinToString(" · ")
        y += 56
        cv.drawText(kind, W / 2f, y, fit(kind, paint(36f, MUTED, sans), W - 2f * M))
        if (data.settings.name.isNotBlank()) {
            y += 50
            val from = context.getString(R.string.visit_card_from, data.settings.name)
            cv.drawText(from, W / 2f, y, fit(from, paint(34f, ACCENT, bold), W - 2f * M))
        }

        // The book it brings a word about: a strip in the genre's colour, like a spine.
        val stampTop = H - 110f - 430
        if (book != null) {
            val top = y + 34
            val box = RectF(M.toFloat(), top, (W - M).toFloat(), minOf(top + 150, stampTop - 26))
            cv.drawRoundRect(box, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF })
            cv.drawRoundRect(box, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x2AFFFFFF })
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(box, 28f, 28f, Path.Direction.CW) })
            cv.drawRect(box.left, box.top, box.left + 18, box.bottom, Paint().apply { color = Genre.of(book.genre).argb.toInt() })
            cv.restore()
            val x = box.left + 48
            val width = (box.width() - 76).toInt()
            cv.drawText(context.getString(R.string.visit_card_rec).uppercase(locale), x, box.top + 44, paint(24f, GOLD, bold, Paint.Align.LEFT).apply { letterSpacing = 0.14f })
            val tp = paint(44f, INK, serif, Paint.Align.LEFT)
            val title = StaticLayout.Builder.obtain(book.title, 0, book.title.length, tp, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
            cv.save(); cv.translate(x, box.top + 58); title.draw(cv); cv.restore()
            if (book.author.isNotBlank()) {
                val ap = paint(30f, MUTED, sans, Paint.Align.LEFT)
                cv.drawText(TextUtils.ellipsize(book.author, ap, width.toFloat(), TextUtils.TruncateAt.END).toString(), x, box.top + 136, ap)
            }
        }

        // The code, as a postage stamp: toothed paper, the code on it, a postmark over its corner.
        val side = 430f
        val stamp = RectF(M.toFloat(), stampTop, M + side, stampTop + side)
        cv.drawRect(RectF(stamp.left + 8, stamp.top + 14, stamp.right + 8, stamp.bottom + 14), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000; maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
        })
        cv.drawRect(stamp, Paint().apply { color = PAPER })
        // The teeth: bites of the night taken out of the paper's edge.
        val bite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blend(TOP, BOTTOM, (stamp.centerY()) / H) }
        val teeth = 17
        for (i in 0..teeth) {
            val t = side * i / teeth
            cv.drawCircle(stamp.left + t, stamp.top, 9f, bite); cv.drawCircle(stamp.left + t, stamp.bottom, 9f, bite)
            cv.drawCircle(stamp.left, stamp.top + t, 9f, bite); cv.drawCircle(stamp.right, stamp.top + t, 9f, bite)
        }
        // Whole, crisp squares and a clear margin all round: it has to be read by a camera.
        // Made at the very size it is drawn, so every square is whole.
        val pad = 34
        cv.drawBitmap(qr(card, side.toInt() - 2 * pad), stamp.left + pad, stamp.top + pad, null)

        // What to do with it, beside the stamp.
        val tx = stamp.right + 44
        val tWidth = (W - M - tx).toInt()
        val how = paint(50f, INK, serif, Paint.Align.LEFT)
        val howText = context.getString(R.string.visit_card_how_title)
        val head = StaticLayout.Builder.obtain(howText, 0, howText.length, how, tWidth).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 0.95f).setIncludePad(false).build()
        cv.save(); cv.translate(tx, stamp.top + 6); head.draw(cv); cv.restore()
        val steps = context.getString(R.string.visit_card_how) + "\n\n" + context.getString(R.string.visit_card_gift)
        val body = StaticLayout.Builder.obtain(steps, 0, steps.length, paint(30f, MUTED, sans, Paint.Align.LEFT), tWidth).setMaxLines(8).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.08f).setIncludePad(false).build()
        cv.save(); cv.translate(tx, stamp.top + head.height + 28); body.draw(cv); cv.restore()

        // The postmark: two rings and the day it was sent, turned a little, with wavy lines running off it.
        val day = DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(data.stats.today).uppercase(locale)
        // It lies across the corner of the picture, the way a postmark lies across a stamp.
        val cx = den.right - 84
        val cy = den.top + 70
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = PAPER; alpha = 200 }
        cv.save()
        cv.rotate(-12f, cx, cy)
        cv.drawCircle(cx, cy, 92f, ink)
        cv.drawCircle(cx, cy, 68f, ink.apply { strokeWidth = 2.5f })
        cv.drawText(day, cx, cy + 10, fit(day, paint(25f, PAPER, bold).apply { alpha = 220 }, 122f))
        cv.drawText("✦", cx, cy - 26, paint(26f, PAPER, bold).apply { alpha = 220 })
        cv.drawText(context.getString(R.string.app_name).uppercase(locale), cx, cy + 46, fit(context.getString(R.string.app_name).uppercase(locale), paint(19f, PAPER, bold).apply { alpha = 200; letterSpacing = 0.1f }, 104f))
        val wave = Path()
        for (line in -1..1) {
            val wy = cy + line * 26f
            wave.moveTo(cx - 250, wy)
            var x = cx - 250
            while (x < cx - 100) { wave.quadTo(x + 18, wy - 9, x + 36, wy); wave.quadTo(x + 54, wy + 9, x + 72, wy); x += 72 }
        }
        cv.drawPath(wave, ink.apply { strokeWidth = 3f; alpha = 150 })
        cv.restore()

        cv.drawText(context.getString(R.string.postcard_footer), W / 2f, H - 62f, fit(context.getString(R.string.postcard_footer), paint(28f, FAINT, sans), W - 2f * M))
        return bmp
    }

    /** A candlelit night with square stars, kept to the edges, inside a gilt frame with studs at its corners. */
    private fun ground(cv: Canvas) {
        cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), TOP, BOTTOM, Shader.TileMode.CLAMP) })
        cv.drawCircle(W / 2f, 520f, 620f, Paint().apply { shader = RadialGradient(W / 2f, 520f, 620f, 0x40F2C77A, 0x00000000, Shader.TileMode.CLAMP) })
        val rnd = Random(20_261)
        val dot = Paint()
        repeat(110) {
            val k = if (rnd.nextInt(6) == 0) 12 else 6
            val x = (rnd.nextInt(W) / 6 * 6).toFloat()
            val y = (rnd.nextInt(H) / 6 * 6).toFloat()
            if (x > M + 40 && x < W - M - 40 && rnd.nextInt(4) > 0) return@repeat
            dot.color = listOf(GOLD, ACCENT, 0xFF72C2FF.toInt(), 0xFFFFFFFF.toInt())[rnd.nextInt(4)]
            dot.alpha = rnd.nextInt(40, 150)
            cv.drawRect(x, y, x + k, y + k, dot)
        }
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = GOLD; alpha = 150 }
        cv.drawRoundRect(RectF(30f, 30f, W - 30f, H - 30f), 46f, 46f, frame)
        cv.drawRoundRect(RectF(46f, 46f, W - 46f, H - 46f), 34f, 34f, frame.apply { strokeWidth = 1.5f; alpha = 90 })
        val stud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; alpha = 200 }
        for ((x, y) in listOf(64f to 64f, W - 64f to 64f, 64f to H - 64f, W - 64f to H - 64f)) {
            cv.save(); cv.rotate(45f, x, y); cv.drawRect(x - 8, y - 8, x + 8, y + 8, stud); cv.restore()
        }
    }

    /** The den as it stands now, with the familiar in it, eyes open. */
    private fun home(data: FamiliarData): Px {
        val s = data.stats
        val hour = LocalTime.now().hour
        val room = Room.scene(
            data.settings.placed.mapNotNull { Item.of(it) }.toSet(), s.finished.map { Genre.of(it.genre).argb.toInt() }.takeLast(24),
            hour < 6 || hour >= 20, Room.STEP, data.season, Sky.of(s.today),
        )
        data.look()?.let { look -> Sprites.frame(look.copy(sleeping = false, stone = 0f), 0).let { room.blit(it, (Room.W - it.w) / 2, Room.FLOOR_Y - it.h + 3) } }
        return room
    }

    /** A canvas's fine pixels as a picture: the drawn art at its own detail. */
    private fun fine(px: Px): Bitmap = Bitmap.createBitmap(px.fine, px.w * DETAIL, px.h * DETAIL, Bitmap.Config.ARGB_8888)

    private fun blend(a: Int, b: Int, t: Float): Int = androidx.core.graphics.ColorUtils.blendARGB(a, b, t.coerceIn(0f, 1f))
}
