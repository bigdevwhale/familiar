package app.familiar.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import app.familiar.ui.components.BookCover
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Book
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.Chip
import app.familiar.ui.components.HoursMinutesField
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UTextField
import app.familiar.ui.shelfName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookEditScreen(id: Long, data: FamiliarData, go: Go, newSeries: String = "") {
    val container = LocalContext.current.container
    val old = data.book(id)
    var title by remember { mutableStateOf(old?.title ?: "") }
    var author by remember { mutableStateOf(old?.author ?: "") }
    var pages by remember { mutableStateOf(old?.pages?.takeIf { it > 0 }?.toString() ?: "") }
    var page by remember { mutableStateOf(old?.page?.takeIf { it > 0 }?.toString() ?: "") }
    var genre by remember { mutableStateOf(old?.let { Genre.of(it.genre) } ?: Genre.FANTASY) }
    var sub by remember { mutableStateOf(old?.sub ?: "") }
    var shelf by remember { mutableStateOf(old?.shelfOf ?: Shelf.READING) }
    var cover by remember { mutableStateOf(old?.cover ?: "") }
    var audio by remember { mutableStateOf(old?.audio ?: false) }
    var ebook by remember { mutableStateOf(old?.ebook ?: false) }
    var series by remember { mutableStateOf(old?.series ?: newSeries) }
    // A new volume of a cycle gets the next number.
    var tags by remember { mutableStateOf(old?.tagList?.toSet() ?: emptySet()) }
    var volume by remember {
        mutableStateOf(old?.volume?.toString() ?: if (newSeries.isBlank()) "" else ((data.books.filter { it.series.equals(newSeries, true) }.mapNotNull { it.volume }.maxOrNull() ?: 0) + 1).toString())
    }
    // For a book added as already read: the day it was finished and its stars (0 for none yet).
    var finished by remember { mutableStateOf(System.currentTimeMillis()) }
    var rating by remember { mutableIntStateOf(0) }
    var pickingDate by remember { mutableStateOf(false) }
    var begun by remember { mutableStateOf<Long?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    var allCycles by remember { mutableStateOf(false) }
    // The length is optional: 0 means not known yet.
    val total = pages.toIntOrNull()?.takeIf { it in 1..20000 } ?: 0
    val ok = title.isNotBlank() && (pages.isBlank() || pages.toIntOrNull()?.let { it in 1..20000 } == true)
    // How far through, for readers and players that only show a percent: it sets the page.
    var percent by remember { mutableStateOf("") }
    @Composable
    fun PercentField(modifier: Modifier) = UTextField(percent, {
        percent = it.filter(Char::isDigit).take(3).let { p -> if ((p.toIntOrNull() ?: 0) > 100) "100" else p }
        percent.toIntOrNull()?.let { p -> page = Book.atPercent(total, p).toString() }
    }, modifier, number = true, placeholder = Book.percentOf(total, page.toIntOrNull() ?: 0).toString(), suffix = "%")
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding().imePadding()) {
        TopBar(stringResource(if (old == null) R.string.book_add else R.string.book_edit), { go.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            SectionTitle(stringResource(R.string.search_title))
            BookSearch { f ->
                title = f.title
                author = f.author
                f.pages?.let { pages = it.toString() }
                cover = f.cover
            }
            SectionTitle(stringResource(R.string.f_cover))
            CoverPicker(title, genre, cover, app.familiar.domain.OwnGenre.key(sub, genre).let { k -> data.genres.firstOrNull { it.key == k }?.argb }) { cover = it }
            SectionTitle(stringResource(R.string.f_title))
            UTextField(title, { title = it.take(120) }, placeholder = stringResource(R.string.f_title_hint))
            SectionTitle(stringResource(R.string.f_author))
            UTextField(author, { author = it.take(80) }, placeholder = stringResource(R.string.f_author_hint))
            // On paper, an e-book, or an audiobook counted in minutes.
            SectionTitle(stringResource(R.string.f_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.f_paper), !audio && !ebook, { audio = false; ebook = false })
                Chip(stringResource(R.string.f_ebook), !audio && ebook, { audio = false; ebook = true })
                Chip(stringResource(R.string.f_audio), audio, { audio = true; ebook = false })
            }
            if (audio) Text(stringResource(R.string.f_audio_sub), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))
            if (audio) {
                // An audiobook's length as the shop or the player shows it: hours and minutes.
                SectionTitle(stringResource(R.string.f_minutes))
                HoursMinutesField(pages.toIntOrNull(), { pages = it?.toString() ?: "" })
                SectionTitle(stringResource(R.string.f_minute_now))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoursMinutesField(page.toIntOrNull(), { page = it?.toString() ?: ""; percent = "" }, Modifier.weight(2f))
                    if (total > 0) PercentField(Modifier.weight(1f))
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.f_pages))
                    UTextField(pages, { pages = it.filter(Char::isDigit).take(5) }, number = true, placeholder = stringResource(R.string.f_pages_optional))
                }
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.f_page_now))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UTextField(page, { page = it.filter(Char::isDigit).take(5); percent = "" }, Modifier.weight(1f), number = true, placeholder = "0")
                        if (total > 0) PercentField(Modifier.weight(1f))
                    }
                }
            }
            if (total == 0) Text(stringResource(R.string.f_percent_note), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(2f)) {
                    SectionTitle(stringResource(R.string.f_series))
                    UTextField(series, { series = it.take(80) }, placeholder = stringResource(R.string.f_series_hint))
                }
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.f_volume))
                    UTextField(volume, { volume = it.filter(Char::isDigit).take(3) }, number = true, placeholder = "1")
                }
            }
            // The cycles on the shelves, the latest first: the ones that match what's typed, or all of them on request.
            val known = remember(data.books) { app.familiar.domain.Series.all(data.books).map { it.name } }
            val typed = series.trim()
            val fits = if (typed.isEmpty()) known else known.filter { it.contains(typed, true) && !it.equals(typed, true) }
            if (fits.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (allCycles) fits else fits.take(CYCLES_SHOWN)).forEach { n -> Chip(n.take(28), false, { series = n; allCycles = false }) }
                if (!allCycles && fits.size > CYCLES_SHOWN) Chip(stringResource(R.string.f_series_all, fits.size), false, { allCycles = true }, U.c.gold)
            }
            SectionTitle(stringResource(R.string.f_genre))
            GenrePicker(genre, sub, data.genres) { g, s -> genre = g; sub = s }
            Text(stringResource(R.string.f_genre_note), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))
            SectionTitle(stringResource(R.string.f_shelves))
            ShelfChips(data.shelves, tags) { tags = it }
            if (old == null) {
                SectionTitle(stringResource(R.string.f_shelf))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Shelf.READING, Shelf.WANT, Shelf.DONE).forEach { s -> Chip(shelfName(s), shelf == s, { shelf = s }) }
                }
                // A book read before Familiar: when it was finished and how it was. It feeds nobody, but counts on the tree.
                if (shelf == Shelf.DONE) {
                    Text(stringResource(R.string.f_read_before), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.finished_on, app.familiar.ui.dateShort(finished)), style = UType.Strong, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.edit), color = U.c.accent, style = UType.Button) }
                    }
                    // And when it was begun, for the calendar (#30).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(begun?.let { stringResource(R.string.started_on, app.familiar.ui.dateShort(it)) } ?: stringResource(R.string.started_unknown), style = UType.Sub, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickingStart = true }) { Text(stringResource(R.string.edit), color = U.c.accent, style = UType.Button) }
                    }
                    Text(stringResource(R.string.your_rating), style = UType.Muted)
                    Row {
                        (1..5).forEach { r ->
                            Text(
                                if (rating >= r) "★" else "☆", fontSize = 30.sp, color = U.c.gold,
                                modifier = Modifier.clickable { rating = if (rating == r) 0 else r }.padding(4.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        PrimaryButton(
            stringResource(R.string.save),
            {
                val now = System.currentTimeMillis()
                val read = old == null && shelf == Shelf.DONE
                val p = if (read) total else (page.toIntOrNull() ?: 0).coerceIn(0, if (total > 0) total else 99_999)
                val b = (old ?: Book(title = "", pages = total, genre = genre.name, added = if (read) finished else now)).copy(
                    title = title.trim(), author = author.trim(), pages = total, genre = genre.name, sub = sub.trim(), page = p, cover = cover, audio = audio, ebook = ebook && !audio, tags = tags.joinToString("\n"), series = series.trim(), volume = volume.toIntOrNull()?.takeIf { series.isNotBlank() },
                    shelf = if (old == null) shelf.name else old.shelf,
                    started = old?.started ?: if (shelf == Shelf.READING) now else if (read) begun?.coerceAtMost(finished) else null,
                    finished = if (read) finished else old?.finished,
                    rating = if (read) rating.takeIf { it in 1..5 } else old?.rating,
                )
                container.write { if (old == null) dao().insertBook(b) else dao().updateBook(b) }
                go.back()
            },
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).navigationBarsPadding(),
            enabled = ok,
        )
    }
    if (pickingStart) app.familiar.ui.components.DateTimeDialog(begun ?: finished, { pickingStart = false }, time = false) { begun = it; pickingStart = false }
    if (pickingDate) app.familiar.ui.components.DateTimeDialog(finished, { pickingDate = false }, time = false) { finished = it; pickingDate = false }
}

/** How many of the cycles show as chips before "All cycles". */
private const val CYCLES_SHOWN = 6
