package app.familiar.ui.den

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.FileProvider
import app.familiar.ui.pix.Px
import java.io.File

/** Pictures made to be sent: kept in the cache and handed to the share sheet. The den itself is one of the share cards. */
object Postcard {
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

    /** Opens the share sheet with the picture, and [text] as its caption where the app takes one. */
    fun send(context: Context, file: File, text: String? = null) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        context.startActivity(Intent.createChooser(send, null))
    }
}
