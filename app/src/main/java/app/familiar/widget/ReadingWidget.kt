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
            if (book == null) {
                views.setImageViewBitmap(R.id.cover, null)
                views.setTextViewText(R.id.title, l.getString(R.string.wr_none))
                views.setTextViewText(R.id.author, l.getString(R.string.wr_none_sub))
                views.setTextViewText(R.id.progress, "")
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
