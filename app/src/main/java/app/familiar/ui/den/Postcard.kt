package app.familiar.ui.den

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.familiar.R
import app.familiar.container
import app.familiar.domain.Form
import app.familiar.domain.Genre
import app.familiar.domain.Item
import app.familiar.domain.Stage
import app.familiar.domain.Year
import app.familiar.ui.FamiliarData
import app.familiar.ui.formRes
import app.familiar.ui.pix.Px
import app.familiar.ui.pix.Room
import app.familiar.ui.pix.Sprites
import app.familiar.ui.speciesRes
import app.familiar.ui.stageRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime

/** A picture of the familiar in its den with what you're reading, to send to friends. */
object Postcard {
    private const val W = 1080
    private const val H = 1350

    fun share(context: Context, data: FamiliarData) {
        val c = context.container
        c.scope.launch {
            val file = runCatching { withContext(Dispatchers.Default) { save(context, draw(context, data), "postcard.png") } }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file == null) Toast.makeText(context, R.string.postcard_failed, Toast.LENGTH_SHORT).show()
                else send(context, file)
            }
        }
    }

    fun draw(context: Context, data: FamiliarData): Bitmap {
        val pet = data.pet ?: error("no familiar")
        val sp = data.species ?: error("no familiar")
        val dark = 0xFF2A2030.toInt()
        val paper = 0xFFF3EBDD.toInt()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(paper)
        // The den, with the familiar (and a guest) in it.
        val hour = LocalTime.now().hour
        val room = Room.scene(data.settings.placed.mapNotNull { Item.of(it) }.toSet(), data.stats.finished.map { Genre.of(it.genre).argb.toInt() }.takeLast(24), hour < 6 || hour >= 20, Room.STEP, data.season)
        data.guestLook?.let { g ->
            val img = Sprites.frame(g, 3 * Sprites.STEP)
            room.blit(img, Room.W / 2 - 44 - img.w / 2, Room.FLOOR_Y - img.h + 1)
        }
        val look = data.look() ?: error("no look")
        val fam = Sprites.frame(look.copy(happy = true), 0)
        room.blit(fam, (Room.W - fam.w) / 2, Room.FLOOR_Y - fam.h + 3)
        val k = 7
        val ox = (W - Room.W * k) / 2
        val oy = 40
        blit(cv, room, ox, oy, k)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = dark }
        cv.drawRoundRect(RectF(ox.toFloat(), oy.toFloat(), (ox + Room.W * k).toFloat(), (oy + Room.H * k).toFloat()), 18f, 18f, frame)
        // The words underneath.
        val serif = ResourcesCompat.getFont(context, R.font.cormorant_bold) ?: Typeface.SERIF
        val sans = ResourcesCompat.getFont(context, R.font.nunito_semibold) ?: Typeface.SANS_SERIF
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 104f; color = dark; textAlign = Paint.Align.CENTER }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 40f; color = 0xFF5A4A5E.toInt(); textAlign = Paint.Align.CENTER }
        val accent = Paint(body).apply { color = 0xFF7A5CC8.toInt() }
        val faint = Paint(body).apply { textSize = 30f; color = 0xFF9A8E92.toInt() }
        var y = oy + Room.H * k + 130f
        cv.drawText((if (pet.shiny) "✦ " else "") + pet.name, W / 2f, y, title)
        val form = Form.of(pet.form)
        val kind = listOfNotNull(
            context.getString(speciesRes(sp)), context.getString(stageRes(data.stats.stage)),
            form.takeIf { it != Form.NONE && data.stats.stage >= Stage.ADULT }?.let { context.getString(formRes(it)) },
        ).joinToString(" · ")
        y += 70f
        cv.drawText(kind, W / 2f, y, body)
        data.reading.firstOrNull()?.let { b ->
            y += 64f
            cv.drawText(fit(context.getString(R.string.postcard_reading, b.title), accent, W - 120f), W / 2f, y, accent)
        }
        val year = Year(data.stats, data.stats.today.year)
        val facts = listOfNotNull(
            data.stats.streak.takeIf { it > 1 }?.let { context.getString(R.string.postcard_streak, context.resources.getQuantityString(R.plurals.days, it, it)) },
            year.books.size.takeIf { it > 0 }?.let { context.getString(R.string.postcard_year, context.resources.getQuantityString(R.plurals.books, it, it), year.year) },
        )
        if (facts.isNotEmpty()) {
            y += 60f
            cv.drawText(facts.joinToString("  ·  "), W / 2f, y, body)
        }
        cv.drawText(context.getString(R.string.postcard_footer), W / 2f, H - 44f, faint)
        return bmp
    }

    /** Shortens [text] with an ellipsis until it fits [width]. */
    private fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.length > 4 && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    /** Draws pixels [k] times bigger, crisp. */
    fun blit(cv: Canvas, px: Px, x0: Int, y0: Int, k: Int) {
        val img = Bitmap.createBitmap(px.w, px.h, Bitmap.Config.ARGB_8888)
        img.setPixels(px.c, 0, px.w, 0, 0, px.w, px.h)
        val big = Bitmap.createScaledBitmap(img, px.w * k, px.h * k, false)
        cv.drawBitmap(big, x0.toFloat(), y0.toFloat(), null)
    }

    fun save(context: Context, bmp: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun send(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }
}
