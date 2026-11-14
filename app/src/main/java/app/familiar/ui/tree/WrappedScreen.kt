package app.familiar.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familiar.R
import app.familiar.data.Book
import app.familiar.domain.DayPart
import app.familiar.domain.Genre
import app.familiar.domain.Wrapped
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.Bar
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.Den
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SoftButton
import app.familiar.ui.dateOf
import app.familiar.ui.genreColor
import app.familiar.ui.genreName
import app.familiar.ui.hm
import app.familiar.ui.locale
import app.familiar.ui.speciesName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch
import java.time.format.TextStyle

/** One page of the year: a tint for its ground and what stands on it. */
private class Page(val tint: Color, val content: @Composable ColumnScope.() -> Unit)

/**
 * The year looked back on, page after page like a story: swipe, or tap the right side of the screen to go on
 * and the left to go back. Pages with nothing to tell (no five-star book, no quote) are left out.
 */
@Composable
fun WrappedScreen(year: Int, data: FamiliarData, go: Go) {
    val w = remember(data.readings, data.books, data.quotes, year) { Wrapped(data.stats, year, data.finds, data.settings.earnedDays) }
    val l = locale()
    val pet = data.pet
    val pages = buildList {
        add(Page(U.c.accent) {
            Den(Modifier.fillMaxWidth().height(230.dp)) { data.look()?.let { FamiliarArt(it, Modifier.fillMaxSize().padding(18.dp)) } }
            Spacer(Modifier.height(24.dp))
            Eyebrow(stringResource(R.string.wrap_eyebrow))
            Big(year.toString(), U.c.gold)
            Caption(stringResource(if (w.empty) R.string.wrap_empty else R.string.wrap_intro, pet?.name ?: stringResource(R.string.app_name)))
        })
        if (!w.empty) {
            add(Page(U.c.mana) {
                Eyebrow(stringResource(R.string.wrap_read))
                Big(String.format(l, "%,d", w.pages), U.c.mana)
                Caption(pluralStringResource(R.plurals.wrap_pages, w.pages))
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(w.books.size.toString(), pluralStringResource(R.plurals.wrap_books, w.books.size), Modifier.weight(1f))
                    Tile(if (w.minutes < 60) hm(w.minutes) else (w.minutes / 60).toString(), pluralStringResource(R.plurals.wrap_hours, w.minutes / 60), Modifier.weight(1f))
                    Tile(String.format(l, "%,d", w.mana), stringResource(R.string.wrap_mana), Modifier.weight(1f))
                }
            })
            add(Page(U.c.ember) {
                Eyebrow(stringResource(R.string.wrap_days_eyebrow))
                Big(w.days.toString(), U.c.ember)
                Caption(pluralStringResource(R.plurals.wrap_days, w.days))
                Spacer(Modifier.height(22.dp))
                if (w.bestStreak > 1) Fact(pluralStringResource(R.plurals.wrap_streak, w.bestStreak, w.bestStreak))
                w.bestDay?.let { (day, n) -> Fact(stringResource(R.string.wrap_best_day, dateOf(day), n)) }
                w.bestMonth?.let { (m, _) -> Fact(stringResource(R.string.wrap_best_month, m.getDisplayName(TextStyle.FULL_STANDALONE, l))) }
            })
            if (w.first != null) add(Page(U.c.leaf) {
                Eyebrow(stringResource(R.string.wrap_first))
                Covers(listOfNotNull(w.first, w.last))
                Spacer(Modifier.height(18.dp))
                Text(w.first.title, style = UType.Title.copy(textAlign = TextAlign.Center), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                Caption(dateOf(java.time.Instant.ofEpochMilli(w.first.finished ?: w.first.added).atZone(data.stats.zone).toLocalDate()))
                w.last?.let { Fact(stringResource(R.string.wrap_last, it.title)) }
            })
            if (w.genres.isNotEmpty()) add(Page(genreColor(w.genres.first().first)) {
                val (top, _) = w.genres.first()
                Eyebrow(stringResource(R.string.wrap_genre))
                Big(genreName(top), genreColor(top), small = true)
                Spacer(Modifier.height(22.dp))
                val most = w.genres.first().second.coerceAtLeast(1)
                w.genres.take(5).forEach { (g, n) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(genreName(g), style = UType.Body, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Bar(n / most.toFloat(), Modifier.weight(1f), color = genreColor(g), track = U.c.surface2)
                        Text(n.toString(), style = UType.Strong, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                    }
                }
            })
            w.longest?.let { b ->
                add(Page(U.c.gold) {
                    Eyebrow(stringResource(R.string.wrap_longest))
                    Covers(listOf(b))
                    Spacer(Modifier.height(18.dp))
                    Text(b.title, style = UType.Title.copy(textAlign = TextAlign.Center), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    Caption(pluralStringResource(R.plurals.pages, b.pages, b.pages))
                    w.author?.let { (name, n) -> Fact(stringResource(R.string.wrap_author, name, pluralStringResource(R.plurals.books, n, n))) }
                })
            }
            w.bestPart?.let { part ->
                add(Page(if (part == DayPart.NIGHT) U.c.accent else U.c.gold) {
                    Eyebrow(stringResource(R.string.wrap_when))
                    Big(stringResource(
                        when (part) { DayPart.MORNING -> R.string.wrap_part_morning; DayPart.DAY -> R.string.wrap_part_day; DayPart.EVENING -> R.string.wrap_part_evening; DayPart.NIGHT -> R.string.wrap_part_night }
                    ), U.c.accent, small = true)
                    Spacer(Modifier.height(22.dp))
                    val most = w.parts.values.max().coerceAtLeast(1)
                    DayPart.entries.forEach { p ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(
                                when (p) { DayPart.MORNING -> R.string.part_morning; DayPart.DAY -> R.string.part_day; DayPart.EVENING -> R.string.part_evening; DayPart.NIGHT -> R.string.part_night }
                            ), style = UType.Body, modifier = Modifier.width(150.dp), maxLines = 1)
                            Bar((w.parts[p] ?: 0) / most.toFloat(), Modifier.weight(1f), color = if (p == part) U.c.accent else U.c.faint, track = U.c.surface2)
                        }
                    }
                    if (w.listens > 0) Fact(pluralStringResource(R.plurals.wrap_listens, w.listens, w.listens))
                })
            }
            if (w.fiveStars.isNotEmpty()) add(Page(U.c.gold) {
                Eyebrow(stringResource(R.string.wrap_stars))
                Big("★".repeat(5), U.c.gold, small = true)
                Spacer(Modifier.height(18.dp))
                Covers(w.fiveStars.takeLast(6))
                Spacer(Modifier.height(14.dp))
                Caption(pluralStringResource(R.plurals.wrap_stars_n, w.fiveStars.size, w.fiveStars.size))
            })
            w.quote?.let { q ->
                add(Page(U.c.accent) {
                    Eyebrow(stringResource(R.string.wrap_quote))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.quote_marks, q.text), style = UType.Quote.copy(textAlign = TextAlign.Center, fontSize = 24.sp, lineHeight = 31.sp), modifier = Modifier.fillMaxWidth())
                    data.book(q.bookId)?.let { Caption(it.title) }
                    if (w.quotes.size > 1) Fact(pluralStringResource(R.plurals.wrap_quotes, w.quotes.size, w.quotes.size))
                })
            }
            add(Page(U.c.leaf) {
                Eyebrow(stringResource(R.string.wrap_den))
                if (w.newPets.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    w.newPets.take(4).forEach { p -> FamiliarArt(data.lookOfPet(p).copy(stone = 0f), Modifier.size(84.dp), fps = 4f) }
                } else data.look()?.let { FamiliarArt(it, Modifier.size(150.dp).align(Alignment.CenterHorizontally)) }
                Spacer(Modifier.height(14.dp))
                if (w.newPets.isNotEmpty()) Fact(stringResource(R.string.wrap_new_pets, w.newPets.map { speciesName(app.familiar.domain.Species.of(it.species)).lowercase(l) }.joinToString(", ")))
                if (w.finds > 0) Fact(pluralStringResource(R.plurals.wrap_finds, w.finds, w.finds))
                if (w.feats > 0) Fact(pluralStringResource(R.plurals.wrap_feats, w.feats, w.feats))
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.wrap_end, pet?.name ?: stringResource(R.string.app_name)), style = UType.Quote.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                PrimaryButton(stringResource(R.string.share), { go.share(app.familiar.ui.share.Cards.Kind.YEAR) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
                Spacer(Modifier.height(8.dp))
                SoftButton(stringResource(R.string.saga_title, year), { go.saga() }, Modifier.fillMaxWidth())
            })
        }
    }
    val state = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(U.screen)) {
        HorizontalPager(state, Modifier.fillMaxSize()) { i ->
            val page = pages[i]
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(page.tint.copy(alpha = 0.22f), Color.Transparent)))
                    // The sides of the page turn it, like a story; what's on it (a button) is tapped first.
                    .pointerInput(i, pages.size) {
                        detectTapGestures { o ->
                            val to = if (o.x > size.width * 0.33f) i + 1 else i - 1
                            if (to in pages.indices) scope.launch { state.animateScrollToPage(to) }
                        }
                    },
            ) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 26.dp, end = 26.dp, top = 64.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    content = page.content,
                )
            }
        }
        // Where you are in the story, and the way out.
        Row(Modifier.statusBarsPadding().padding(start = 18.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pages.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= state.currentPage) U.c.gold else U.c.line))
                }
            }
            IconButton(onClick = { go.back() }) { Icon(Icons.Rounded.Close, stringResource(R.string.back), tint = U.c.muted) }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), style = UType.Eyebrow.copy(color = U.c.gold, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

/** The number or the word the page is about. */
@Composable
private fun Big(text: String, color: Color, small: Boolean = false) {
    Text(
        text, style = UType.Display.copy(color = color, fontSize = if (small) 46.sp else 84.sp, lineHeight = if (small) 50.sp else 88.sp, textAlign = TextAlign.Center),
        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = UType.Sub.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
}

/** One more thing about the year, in a line of its own. */
@Composable
private fun Fact(text: String) {
    Text(
        text, style = UType.Body.copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(U.RMd).background(U.c.surface).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(U.RMd).background(U.c.surface).padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = UType.Title, maxLines = 1)
        Text(label, style = UType.Small.copy(textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** A row of covers, centred: one large, or several side by side. */
@Composable
private fun Covers(books: List<Book>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        val wide = when (books.size) { 1 -> 130; 2 -> 104; 3 -> 84; else -> 46 }
        books.forEach { b -> BookCover(b, Modifier.width(wide.dp).height((wide * 1.45f).dp), small = books.size > 2, bare = books.size > 3) }
    }
}
