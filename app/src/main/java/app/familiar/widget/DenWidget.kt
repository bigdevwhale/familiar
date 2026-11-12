package app.familiar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import app.familiar.MainActivity
import app.familiar.R
import app.familiar.container
import app.familiar.domain.Genre
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.domain.Stats
import app.familiar.localized
import app.familiar.ui.pix.Look
import app.familiar.ui.pix.Sprites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

/** The familiar on the home screen: how it's doing and today's pages. A tap opens the den. */
class DenWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * The familiar cut out of its 80-pixel frame (which leaves room above its head for the stages to grow
         * into) and set in the middle of a square, so it fills a one-cell widget evenly instead of sitting low.
         */
        private fun fitted(b: Bitmap): Bitmap {
            val w = b.width; val h = b.height
            val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            // Only what's solid: the faint ground shadow doesn't count.
            for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 150) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            if (x1 < 0) return b
            val side = maxOf(x1 - x0 + 1, y1 - y0 + 1) + 6
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).drawBitmap(
                b, android.graphics.Rect(x0, y0, x1 + 1, y1 + 1),
                android.graphics.Rect((side - (x1 - x0 + 1)) / 2, (side - (y1 - y0 + 1)) / 2, (side - (x1 - x0 + 1)) / 2 + x1 - x0 + 1, (side - (y1 - y0 + 1)) / 2 + y1 - y0 + 1),
                null,
            )
            return out
        }

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DenWidget::class.java))
            val mini = manager.getAppWidgetIds(ComponentName(context, MiniWidget::class.java))
            if (ids.isEmpty() && mini.isEmpty()) return
            val c = context.container
            val s = c.settings.now()
            val dao = c.db.dao()
            val st = Stats(dao.books(), dao.readings(), dao.pets(), System.currentTimeMillis(), ZoneId.systemDefault(), s.dailyGoal, s.guardedDays)
            val l = context.localized()
            val views = RemoteViews(context.packageName, R.layout.widget_den)
            val pet = st.active
            // The familiar you read with, as it is: stage, form, rare colouring and what it has gathered.
            val art = pet?.let {
                val stage = Stage.of(it.mana)
                val look = Look(
                    Species.of(it.species), stage, stone = st.stone, mood = st.mood,
                    tails = Genre.tails.filter { g -> g in st.genresFinished }.map { g -> g.argb.toInt() },
                    hoard = st.finished.takeLast(16).map { b -> Genre.of(b.genre).argb.toInt() },
                    patches = st.ownFinished.map { o -> o.argb.toInt() },
                    stars = st.nights, items = st.items.take(7),
                    hatch = (it.mana / Stage.BABY.from.toFloat()).coerceIn(0f, 1f),
                    shiny = it.shiny && stage != Stage.EGG,
                    form = if (stage >= Stage.ADULT) app.familiar.domain.Form.of(it.form) else app.familiar.domain.Form.NONE,
                )
                val px = Sprites.frame(look, 0)
                val raw = Bitmap.createBitmap(px.fine, px.fw, px.fh, Bitmap.Config.ARGB_8888)
                // Scaled up without smoothing so the pixels stay crisp.
                Bitmap.createScaledBitmap(raw, px.fw * 2, px.fh * 2, false)
            }
            val open = PendingIntent.getActivity(
                context, 5000,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (mini.isNotEmpty()) {
                // One cell: just the familiar, with a thin line of how fed it is today.
                val small = RemoteViews(context.packageName, R.layout.widget_mini)
                if (art != null) small.setImageViewBitmap(R.id.art, fitted(art))
                small.setProgressBar(R.id.bar, 1000, (st.fed * 1000).toInt(), false)
                small.setOnClickPendingIntent(R.id.root, open)
                manager.updateAppWidget(mini, small)
            }
            if (ids.isEmpty()) return
            if (pet != null && art != null) {
                val sp = Species.of(pet.species)
                views.setImageViewBitmap(R.id.art, art)
                views.setTextViewText(R.id.name, pet.name)
                views.setTextViewText(
                    R.id.state,
                    l.getString(
                        when {
                            st.stage == Stage.EGG -> R.string.w_egg
                            st.stone > 0.3f -> if (sp == Species.PHOENIX) R.string.w_ash else R.string.w_stone
                            st.fed >= 1f -> R.string.w_fed
                            st.todayPages > 0 -> R.string.w_eating
                            else -> R.string.w_hungry
                        }
                    ),
                )
            }
            views.setTextViewText(R.id.pages, l.getString(R.string.fed_of, st.todayPages, s.dailyGoal))
            views.setProgressBar(R.id.bar, 1000, (st.fed * 1000).toInt(), false)
            views.setOnClickPendingIntent(R.id.root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}

/** The one-cell widget (#23): only the familiar you read with. Drawn together with [DenWidget]. */
class MiniWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                DenWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
