package app.familiar.ui.share

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.data.Shelf
import app.familiar.domain.MonthSummary
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.Chip
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.Segmented
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.den.Postcard
import app.familiar.ui.locale
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.TextStyle

/** Cards to share: the book you're on, your month, your year and the den, for stories or posts. */
@Composable
fun ShareScreen(start: Cards.Kind, bookId: Long, data: FamiliarData, go: Go) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.stats
    // The book asked for, then the ones on the go, then the last ones finished.
    val books = (listOfNotNull(data.book(bookId)) + data.reading + s.finished.takeLast(4).reversed())
        .filter { it.shelfOf != Shelf.WANT }.distinctBy { it.id }
    val kinds = Cards.Kind.entries.filter {
        when (it) {
            Cards.Kind.BOOK -> books.isNotEmpty()
            Cards.Kind.DEN -> data.pet != null
            else -> true
        }
    }
    var kind by rememberSaveable { mutableStateOf(if (start in kinds) start else kinds.first()) }
    var format by rememberSaveable { mutableStateOf(Cards.Format.STORY) }
    var style by rememberSaveable { mutableStateOf(Cards.Style.NIGHT) }
    // Several books on the go: all of them together, unless one was asked for.
    val together = data.reading.size > 1
    var book by rememberSaveable { mutableLongStateOf(if (together && bookId <= 0) Cards.ALL else books.firstOrNull()?.id ?: 0L) }
    // Early in a month there's little to show yet: last month is offered first then.
    val thisMonth = YearMonth.from(s.today)
    val lastMonth = thisMonth.minusMonths(1)
    var month by rememberSaveable {
        mutableStateOf((if (MonthSummary(s, thisMonth).empty && !MonthSummary(s, lastMonth).empty) lastMonth else thisMonth).toString())
    }
    val request = Cards.Request(kind, format, style, book, YearMonth.parse(month))
    val image by produceState<Bitmap?>(null, request, data.books, data.readings, data.pets) {
        value = withContext(Dispatchers.Default) { runCatching { Cards.draw(context, data, request) }.getOrNull() }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.share_title), { go.back() })
        Segmented(kinds, kind, { kindName(it) }, { kind = it }, Modifier.padding(horizontal = 18.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Crossfade(image, label = "card") { img ->
                Box(
                    Modifier.aspectRatio(format.w / format.h.toFloat(), matchHeightConstraintsFirst = true)
                        .shadow(18.dp, U.RLg).clip(U.RLg).background(U.c.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    if (img != null) Image(img.asImageBitmap(), stringResource(R.string.share_title), Modifier.fillMaxSize())
                    else CircularProgressIndicator(color = U.c.accent)
                }
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            when (kind) {
                Cards.Kind.BOOK -> if (books.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (together) item(key = "all") {
                        val on = book == Cards.ALL
                        Box(
                            Modifier.width(66.dp).height(66.dp).clip(U.RSm)
                                .background(if (on) U.c.accentSoft else U.c.surface)
                                .border(if (on) 3.dp else 1.dp, if (on) U.c.accent else U.c.line, U.RSm)
                                .clickable { book = Cards.ALL },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.share_all) + "\n" + data.reading.size,
                                style = UType.Small.copy(color = if (on) U.c.accent else U.c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            )
                        }
                    }
                    items(books, key = { it.id }) { b ->
                        val on = b.id == book
                        BookCover(
                            b,
                            Modifier.width(44.dp).height(66.dp).clip(U.RSm)
                                .border(if (on) 3.dp else 1.dp, if (on) U.c.accent else U.c.line, U.RSm)
                                .clickable { book = b.id },
                            small = true,
                        )
                    }
                }
                Cards.Kind.MONTH -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(thisMonth, lastMonth).forEach { m ->
                        Chip(m.month.getDisplayName(TextStyle.FULL_STANDALONE, locale()).replaceFirstChar { it.titlecase(locale()) }, month == m.toString(), { month = m.toString() })
                    }
                }
                else -> {}
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(Cards.Format.entries, format, { stringResource(if (it == Cards.Format.STORY) R.string.share_story else R.string.share_post) }, { format = it }, Modifier.weight(1f))
                Segmented(Cards.Style.entries, style, { stringResource(if (it == Cards.Style.NIGHT) R.string.share_night else R.string.share_parchment) }, { style = it }, Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Saving to the gallery without asking for storage needs Android 10.
            if (Build.VERSION.SDK_INT >= 29) SoftButton(
                stringResource(R.string.share_save), {
                    val img = image ?: return@SoftButton
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { saveToGallery(context, img) }.isSuccess }
                        Toast.makeText(context, if (ok) R.string.share_saved else R.string.postcard_failed, Toast.LENGTH_SHORT).show()
                    }
                },
                Modifier.weight(1f).height(54.dp), icon = Icons.Rounded.Download,
            )
            PrimaryButton(
                stringResource(R.string.share), {
                    val img = image ?: return@PrimaryButton
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { runCatching { Postcard.save(context, img, "familiar.png") }.getOrNull() }
                        if (file == null) Toast.makeText(context, R.string.postcard_failed, Toast.LENGTH_SHORT).show()
                        else Postcard.send(context, file, context.getString(R.string.share_text, Cards.PLAY))
                    }
                },
                Modifier.weight(1.4f), enabled = image != null, icon = Icons.Rounded.Share,
            )
        }
    }
}

@Composable
private fun kindName(k: Cards.Kind): String = stringResource(
    when (k) {
        Cards.Kind.BOOK -> R.string.share_kind_book
        Cards.Kind.MONTH -> R.string.share_kind_month
        Cards.Kind.YEAR -> R.string.share_kind_year
        Cards.Kind.DEN -> R.string.tab_den
    }
)

private fun saveToGallery(context: Context, bmp: Bitmap) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "familiar-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Familiar")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no place")
    context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** The way in to the cards: a little stack of them, fanned like a hand of tarot. */
@Composable
fun ShareBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(U.RLg)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(U.c.accentSoft, U.c.manaSoft)))
            .border(1.dp, U.c.line, U.RLg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(64.dp).height(60.dp)) {
            listOf(Triple(-14f, U.c.mana, 0.dp), Triple(0f, U.c.gold, 12.dp), Triple(14f, U.c.accent, 24.dp)).forEach { (turn, color, x) ->
                Box(
                    Modifier.padding(start = x, top = 4.dp).width(30.dp).height(46.dp)
                        .graphicsLayer { rotationZ = turn }
                        .clip(U.RSm).background(color).border(2.dp, U.c.surface, U.RSm),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.share_cards_title), style = UType.Strong)
            Text(stringResource(R.string.share_cards_sub), style = UType.Muted)
        }
        androidx.compose.material3.Icon(Icons.Rounded.Share, null, tint = U.c.accent, modifier = Modifier.size(22.dp))
    }
}
