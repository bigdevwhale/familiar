package app.familiar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.familiar.domain.Genre
import app.familiar.ui.theme.LocalScreen
import app.familiar.ui.theme.U
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A picture behind every screen, softened so the text stays easy to read: a photo for each genre
 * (public-domain photos in assets/backdrops, see CREDITS.txt there), the genre of the book being read,
 * or one of the reader's own from the gallery.
 */
object Backdrops {
    const val NONE = ""
    const val AUTO = "auto"
    const val OWN = "own"
    private const val OWN_FILE = "backdrop.jpg"
    /** How much of the page colour lies over the picture. */
    const val VEIL = 0.8f

    fun of(g: Genre) = "g:" + g.name
    fun genreOf(key: String): Genre? = key.removePrefix("g:").takeIf { key.startsWith("g:") }?.let { n -> Genre.entries.firstOrNull { it.name == n } }

    fun ownFile(context: Context) = File(context.filesDir, OWN_FILE)

    /** The picture [key] shows; [reading] is the genre of the book open now, for [AUTO]. */
    fun resolve(key: String, reading: Genre?): String? = when {
        key == NONE -> null
        key == AUTO -> asset(reading ?: Genre.OTHER)
        key == OWN -> OWN
        else -> genreOf(key)?.let(::asset)
    }

    fun asset(g: Genre) = "backdrops/" + g.name.lowercase() + ".jpg"

    /** Reads [what] (an asset path, or [OWN]) no larger than [maxSide] on its longer side. */
    fun load(context: Context, what: String, maxSide: Int): Bitmap? = runCatching {
        fun open() = if (what == OWN) ownFile(context).inputStream() else context.assets.open(what)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()

    /** Keeps a picture from the gallery as the reader's own backdrop; false if it couldn't be read. */
    suspend fun importPicked(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2000) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
            val tmp = File(context.filesDir, "$OWN_FILE.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(ownFile(context))
        }.getOrDefault(false)
    }
}

/** [content] over the backdrop [key] chooses; screens then draw no page colour of their own. */
@Composable
fun BackdropBox(key: String, reading: Genre?, stamp: Long, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val what = Backdrops.resolve(key, reading)
    // [stamp] changes when the reader picks another picture of their own.
    val image by produceState<ImageBitmap?>(null, what, stamp) {
        value = what?.let { w -> withContext(Dispatchers.IO) { Backdrops.load(context, w, 1800)?.asImageBitmap() } }
    }
    Box(Modifier.fillMaxSize().background(U.c.bg)) {
        val shown = image
        if (shown != null) {
            Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(U.c.bg.copy(alpha = Backdrops.VEIL)))
        }
        CompositionLocalProvider(LocalScreen provides if (shown != null) Color.Transparent else U.c.bg) { content() }
    }
}

/** The backdrops to choose from: none, the book's genre, a photo for each genre, or one's own picture. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BackdropPicker(key: String, reading: Genre?, stamp: Long, onPick: (String) -> Unit, onOwn: () -> Unit) {
    val context = LocalContext.current
    val hasOwn = remember(stamp) { Backdrops.ownFile(context).exists() }
    // On a page of its own: every choice in sight, four to a row.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        run { Tile(null, androidx.compose.ui.res.stringResource(app.familiar.R.string.backdrop_none), key == Backdrops.NONE, stamp) { onPick(Backdrops.NONE) } }
        run { Tile(Backdrops.asset(reading ?: Genre.OTHER), androidx.compose.ui.res.stringResource(app.familiar.R.string.backdrop_auto), key == Backdrops.AUTO, stamp) { onPick(Backdrops.AUTO) } }
        run { Tile(if (hasOwn) Backdrops.OWN else null, androidx.compose.ui.res.stringResource(app.familiar.R.string.backdrop_own), key == Backdrops.OWN, stamp, plus = !hasOwn) { if (hasOwn && key != Backdrops.OWN) onPick(Backdrops.OWN) else onOwn() } }
        Genre.entries.forEach { g ->
            Tile(Backdrops.asset(g), genreName(g), key == Backdrops.of(g), stamp) { onPick(Backdrops.of(g)) }
        }
    }
}

@Composable
private fun Tile(what: String?, label: String, selected: Boolean, stamp: Long, plus: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, what, stamp) {
        value = what?.let { w -> withContext(Dispatchers.IO) { Backdrops.load(context, w, 320)?.asImageBitmap() } }
    }
    androidx.compose.foundation.layout.Column(
        Modifier.width(76.dp).clickable(onClick = onClick),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(76.dp).height(128.dp).clip(U.RSm).background(U.c.surface2)
                .border(if (selected) 3.dp else 1.dp, if (selected) U.c.accent else U.c.line, U.RSm),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (plus) androidx.compose.material3.Text("+", style = app.familiar.ui.theme.UType.Title.copy(color = U.c.accent))
        }
        androidx.compose.material3.Text(
            label, style = app.familiar.ui.theme.UType.Small.copy(color = if (selected) U.c.accent else U.c.muted),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
