package app.familiar.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Shelf
import androidx.compose.material.icons.rounded.Share
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.Bar
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.Chip
import app.familiar.ui.components.ConfirmDialog
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.dateShort
import app.familiar.ui.mana
import app.familiar.ui.pages
import app.familiar.ui.bookColor
import app.familiar.ui.bookGenre
import app.familiar.ui.shelfName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookScreen(id: Long, data: FamiliarData, go: Go) {
    val container = LocalContext.current.container
    val book = data.book(id)
    var deleting by remember { mutableStateOf(false) }
    if (book == null) {
        LaunchedEffect(Unit) { go.back() }
        return
    }
    val sittings = data.readings.filter { it.bookId == id }.sortedByDescending { it.end }
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar("", { go.back() }) {
            if (book.shelfOf != Shelf.WANT) IconButton(onClick = { go.share(app.familiar.ui.share.Cards.Kind.BOOK, id) }) {
                Icon(Icons.Rounded.Share, stringResource(R.string.share), tint = U.c.muted)
            }
            IconButton(onClick = { go.editBook(id) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit), tint = U.c.muted) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = U.c.muted) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book, Modifier.width(104.dp).height(150.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = UType.Title)
                    if (book.author.isNotBlank()) Text(book.author, style = UType.Sub)
                    Spacer(Modifier.height(6.dp))
                    Text("${bookGenre(book)} · ${shelfName(book.shelfOf)}", style = UType.Small.copy(color = bookColor(book)))
                    if (book.series.isNotBlank()) Text(
                        (book.volume?.let { stringResource(R.string.volume_n, it) + " · " } ?: "") + book.series,
                        style = UType.Small.copy(color = U.c.gold),
                        modifier = Modifier.padding(top = 4.dp).clickable { go.series(book.series) },
                    )
                    Spacer(Modifier.height(10.dp))
                    if (book.pages > 0) Bar(book.fraction, color = U.c.accent, track = U.c.accentSoft)
                    Spacer(Modifier.height(4.dp))
                    Text(app.familiar.ui.progressLabel(book), style = UType.Small)
                    app.familiar.domain.Insights.forecast(book, data.stats)?.let {
                        Text(stringResource(R.string.forecast, app.familiar.ui.dateOf(it)), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            when (book.shelfOf) {
                Shelf.READING, Shelf.PAUSED -> {
                    PrimaryButton(stringResource(R.string.ritual_start), { go.ritual(id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.LocalFireDepartment)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(stringResource(R.string.log_pages), { go.log(id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Edit)
                        SoftButton(
                            stringResource(if (book.shelfOf == Shelf.PAUSED) R.string.resume else R.string.pause),
                            { container.write { dao().updateBook(book.copy(shelf = if (book.shelfOf == Shelf.PAUSED) Shelf.READING.name else Shelf.PAUSED.name)) } },
                            Modifier.fillMaxWidth(), color = U.c.muted, soft = U.c.surface2,
                        )
                    }
                }
                Shelf.WANT -> PrimaryButton(stringResource(R.string.start_reading), {
                    container.write { dao().updateBook(book.copy(shelf = Shelf.READING.name, started = System.currentTimeMillis())) }
                }, Modifier.fillMaxWidth())
                Shelf.DONE -> {
                    UCard {
                        Text(stringResource(R.string.finished_on, dateShort(book.finished ?: book.added)), style = UType.Strong)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.your_rating), style = UType.Muted)
                        Row {
                            (1..5).forEach { r ->
                                Text(
                                    if ((book.rating ?: 0) >= r) "★" else "☆",
                                    fontSize = 30.sp, color = U.c.gold,
                                    modifier = Modifier.clickable { container.write { dao().updateBook(book.copy(rating = r)) } }.padding(4.dp),
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.f_shelves))
            ShelfChips(data.shelves, book.tagList.toSet()) { t -> container.write { dao().updateBook(book.copy(tags = t.joinToString(NL))) } }

            SectionTitle(stringResource(R.string.chronicle))
            if (sittings.isEmpty()) Text(stringResource(R.string.no_sittings), style = UType.Muted)
            else UCard {
                sittings.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = U.c.line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dateShort(r.end), style = UType.Strong.copy(fontSize = 14.sp))
                            Text(
                                (if (r.listened) "🎧 " else "") + when {
                                    book.audio -> app.familiar.ui.amount(r.pages, true) + " · " + app.familiar.ui.hm(r.fromPage) + "–" + app.familiar.ui.hm(r.toPage)
                                    // Listened or read by the candle without a page: only the time is known.
                                    r.pages == 0 && r.timed -> stringResource(R.string.minutes_n, r.minutes)
                                    else -> pages(r.pages) + (if (r.timed) " · " + stringResource(R.string.minutes_n, r.minutes) else "") + " · " + stringResource(R.string.page_range, r.fromPage, r.toPage)
                                },
                                style = UType.Small,
                            )
                        }
                        Text("+${mana(r.mana)}", style = UType.Small.copy(color = U.c.mana))
                    }
                    if (r.item.isNotBlank()) Text(stringResource(R.string.item_swallowed, r.item), style = UType.Small.copy(color = U.c.leaf), modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (book.shelfOf != Shelf.DONE) {
                SectionTitle(stringResource(R.string.move_to))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Shelf.READING, Shelf.WANT, Shelf.PAUSED).filter { it != book.shelfOf }.forEach { sh ->
                        Chip(shelfName(sh), false, { container.write { dao().updateBook(book.copy(shelf = sh.name)) } })
                    }
                    Chip(stringResource(R.string.mark_done), false, { go.log(id) })
                }
            }
        }
    }
    if (deleting) ConfirmDialog(
        title = stringResource(R.string.delete_book_title),
        text = stringResource(R.string.delete_book_text),
        confirm = stringResource(R.string.delete),
        danger = true,
        onConfirm = { container.write { dao().removeBook(book) }; go.back() },
        onDismiss = { deleting = false },
    )
}

private const val NL = "\n"
