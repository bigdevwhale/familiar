package app.familiar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.ColorUtils
import app.familiar.MainActivity
import app.familiar.R
import app.familiar.container
import app.familiar.data.Book
import app.familiar.data.Covers
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import app.familiar.domain.OwnGenre
import app.familiar.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * «Reading now» on the home screen (#61, #68): the book on the go with its cover and how far along.
 * A tap opens the app. Drawn again whenever the den widget is.
 */
class ReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { refresh(context) } finally { pending.finish() }
        }
    }

    /** Resized: the card is painted again at the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ReadingWidget::class.java))
            if (ids.isEmpty()) return
            val dao = context.container.db.dao()
            val books = dao.books()
            val readings = dao.readings()
            val l = context.localized()
            // The book read last that is still on the go; else any on the go.
            val going = books.filter { it.shelfOf == Shelf.READING }
            val last = readings.maxByOrNull { it.end }?.bookId
            val book = going.firstOrNull { it.id == last } ?: going.maxByOrNull { it.started ?: it.added }
            val views = RemoteViews(context.packageName, R.layout.widget_reading)
            val settings = context.container.settings.now()
            val st = app.familiar.domain.Stats(books, readings, dao.pets(), System.currentTimeMillis(), java.time.ZoneId.systemDefault(), settings.dailyGoal, settings.guardedDays)
            // The card in the book's colour, at the widget's own size so its corners stay round.
            val opts = manager.getAppWidgetOptions(ids.first())
            val dp = context.resources.displayMetrics.density
            val cw = ((opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250).takeIf { it > 0 } ?: 250) * dp).toInt()
            val ch = ((opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).takeIf { it > 0 } ?: 110) * dp).toInt()
            val tint = book?.let { (OwnGenre.of(it)?.argb ?: Genre.of(it.genre).argb).toInt() } ?: 0xFF6B5CA5.toInt()
            views.setImageViewBitmap(R.id.card, card(cw.coerceIn(200, 1400), ch.coerceIn(150, 900), tint, 22 * dp))
            DenWidget.art(st)?.let { views.setImageViewBitmap(R.id.pet, DenWidget.fitted(it)) }
            if (book == null) {
                views.setImageViewBitmap(R.id.cover, null)
                views.setTextViewText(R.id.title, l.getString(R.string.wr_none))
                views.setTextViewText(R.id.author, l.getString(R.string.wr_none_sub))
                views.setTextViewText(R.id.progress, "")
                views.setTextViewText(R.id.today, "")
                views.setProgressBar(R.id.bar, 1000, 0, false)
            } else {
                val img = runCatching { Covers.load(context, book.cover)?.asAndroidBitmap() }.getOrNull()
                views.setImageViewBitmap(R.id.cover, cover(book, img))
                views.setTextViewText(R.id.title, book.title)
                views.setTextViewText(R.id.author, book.author)
                views.setTextViewText(
                    R.id.progress,
                    when {
                        book.pages <= 0 -> l.getString(R.string.wr_page, book.page)
                        book.audio -> l.getString(R.string.wr_minutes, Book.percentOf(book.pages, book.page))
                        else -> l.getString(R.string.wr_pages, book.page, book.pages, Book.percentOf(book.pages, book.page))
                    },
                )
                views.setTextViewText(
                    R.id.today,
                    when {
                        st.todayPages <= 0 -> l.getString(R.string.wr_today_none)
                        // past the day's goal: no «66 of 30», just the pages and a word that the familiar is full
                        st.todayPages >= settings.dailyGoal -> l.getString(R.string.wr_today_full, st.todayPages)
                        else -> l.getString(R.string.wr_today, st.todayPages, settings.dailyGoal)
                    },
                )
                views.setProgressBar(R.id.bar, 1000, if (book.pages > 0) (book.fraction * 1000).toInt() else 0, false)
            }
            val open = PendingIntent.getActivity(
                context, 5001,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.root, open)
            manager.updateAppWidget(ids, views)
        }

        /** The card behind it all: a dusk gradient from the book's colour down to night, a faint glow top left, round corners. */
        private fun card(w: Int, h: Int, c: Int, r: Float): Bitmap {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            val top = ColorUtils.blendARGB(c, 0xFF000000.toInt(), 0.35f)
            val bottom = ColorUtils.blendARGB(c, 0xFF120E1A.toInt(), 0.82f)
            cv.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w * 0.4f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
            cv.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(w * 0.18f, h * 0.2f, w * 0.6f, intArrayOf(0x40FFE9B0, 0x00FFE9B0), null, Shader.TileMode.CLAMP)
            })
            // A few pixel stars over the night end, as in the den.
            val star = Paint().apply { color = 0x66FFF6E4 }
            val rnd = java.util.Random(c.toLong())
            repeat(14) { val x = w * (0.45f + rnd.nextFloat() * 0.53f); val y = h * rnd.nextFloat() * 0.9f; val s = if (rnd.nextInt(4) == 0) 4f else 2.5f; cv.drawRect(x, y, x + s, y + s, star) }
            return out
        }

        /** The cover as on the shelf: the real picture cropped to fill, or one drawn in the genre's colour with the title. */
        private fun cover(b: Book, img: Bitmap?): Bitmap {
            val w = 240; val h = 352
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            if (img != null) {
                val sr = img.width / img.height.toFloat(); val dr = w / h.toFloat()
                val src = if (sr > dr) { val cw = (img.height * dr).toInt(); Rect((img.width - cw) / 2, 0, (img.width + cw) / 2, img.height) }
                else { val ch = (img.width / dr).toInt(); Rect(0, (img.height - ch) / 2, img.width, (img.height + ch) / 2) }
                cv.drawBitmap(img, src, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                val c = (OwnGenre.of(b)?.argb ?: Genre.of(b.genre).argb).toInt()
                cv.drawRect(rect, Paint().apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(ColorUtils.blendARGB(c, -1, 0.18f), c, ColorUtils.blendARGB(c, 0xFF000000.toInt(), 0.28f)), null, Shader.TileMode.CLAMP)
                })
                val gold = Paint().apply { color = 0xCCF2D58A.toInt() }
                cv.drawRect(w * 0.16f, h * 0.15f, w * 0.9f, h * 0.15f + 4, gold)
                cv.drawRect(w * 0.16f, h * 0.85f - 4, w * 0.9f, h * 0.85f, gold)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF6E4.toInt(); textSize = 26f; typeface = Typeface.SERIF }
                val layout = StaticLayout.Builder.obtain(b.title, 0, b.title.length, tp, (w * 0.7f).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(5).build()
                cv.save(); cv.translate(w * 0.18f, (h - layout.height) / 2f); layout.draw(cv); cv.restore()
            }
            // The spine's shadow, as on the shelf.
            cv.drawRect(0f, 0f, w * 0.06f, h.toFloat(), Paint().apply { color = 0x2E000000 })
            return out
        }
    }
}
