package app.familiar.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.data.Book
import app.familiar.domain.MonthSummary
import app.familiar.domain.Stats
import app.familiar.domain.dayBooks
import app.familiar.ui.books
import app.familiar.ui.components.BookCover
import app.familiar.ui.locale
import app.familiar.ui.pages
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * A month as a calendar of covers: each day shows the book finished that day (with a star)
 * or the one read most. Arrows go back through the months; a cover opens its book.
 */
@Composable
fun CoverCalendar(s: Stats, onBook: (Long) -> Unit, modifier: Modifier = Modifier) {
    val now = YearMonth.from(s.today)
    var shown by rememberSaveable { mutableStateOf(now.toString()) }
    val month = YearMonth.parse(shown)
    val days = remember(s, month) { dayBooks(s, month) }
    val summary = remember(s, month) { MonthSummary(s, month) }
    val loc = locale()
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shown = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.cal_earlier), tint = U.c.muted)
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.titlecase(loc) } + if (month.year != s.today.year) " " + month.year else "",
                style = UType.Strong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { shown = month.plusMonths(1).toString() }, enabled = month < now) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.cal_later), tint = if (month < now) U.c.muted else U.c.line)
            }
        }
        Row {
            DayOfWeek.entries.forEach { d ->
                Text(d.getDisplayName(TextStyle.SHORT_STANDALONE, loc), style = UType.Small.copy(color = U.c.faint), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        // Weeks start on Monday; the days before the 1st are left empty.
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0 until 7) {
                    val n = week * 7 + d - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(0.68f)) {
                        if (n in 1..month.lengthOfMonth()) {
                            val date = month.atDay(n)
                            DayCell(n, days[date].orEmpty(), date.isAfter(s.today), date == s.today, onBook)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (summary.empty && days.isEmpty()) stringResource(R.string.cal_empty)
            else listOfNotNull(
                pages(summary.pages).takeIf { summary.pages > 0 },
                stringResource(R.string.cal_finished, books(summary.finished.size)).takeIf { summary.finished.isNotEmpty() },
                stringResource(R.string.cal_days, days.size),
            ).joinToString(" · "),
            style = UType.Small.copy(color = U.c.muted),
        )
    }
}

@Composable
private fun DayCell(n: Int, day: List<app.familiar.domain.DayBook>, ahead: Boolean, today: Boolean, onBook: (Long) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val book = day.firstOrNull()?.book
    val finished = day.any { it.finished }
    Box(
        Modifier.fillMaxSize().clip(shape)
            .background(if (ahead) U.c.surface2.copy(alpha = 0.4f) else U.c.surface2)
            .then(if (today) Modifier.border(1.5.dp, U.c.accent, shape) else Modifier),
    ) {
        // Two books or more on a day share the cell: two covers side by side, and "+N" for the rest (#30).
        Row(Modifier.fillMaxSize()) {
            day.take(2).forEach { d ->
                BookCover(
                    d.book,
                    Modifier.weight(1f).fillMaxSize().clickable(onClickLabel = d.book.title) { onBook(d.book.id) },
                    small = true, bare = true,
                )
            }
        }
        if (day.size > 2) Box(
            Modifier.align(Alignment.BottomStart).padding(2.dp).clip(CircleShape).background(U.c.bg.copy(alpha = 0.85f)).padding(horizontal = 4.dp),
        ) { Text("+${day.size - 2}", style = UType.Small.copy(color = U.c.ink, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))) }
        // The day's number: on a cover it sits in a little dark disc so it still reads.
        Box(
            Modifier.align(Alignment.TopStart).padding(2.dp)
                .then(if (book != null) Modifier.clip(CircleShape).background(U.c.bg.copy(alpha = 0.8f)) else Modifier)
                .padding(horizontal = 4.dp),
        ) {
            Text(n.toString(), style = UType.Small.copy(color = if (ahead) U.c.faint else U.c.ink, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal))
        }
        if (finished) Icon(
            Icons.Rounded.Star, stringResource(R.string.cal_done),
            tint = U.c.gold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(14.dp).clip(CircleShape).background(U.c.bg.copy(alpha = 0.85f)).padding(1.dp),
        )
    }
}

/** The books finished in [year], month by month: a row of covers for each month that has any. */
@Composable
fun YearCovers(s: Stats, year: Int, onBook: (Long) -> Unit, modifier: Modifier = Modifier) {
    val loc = locale()
    val byMonth = remember(s, year) {
        s.finished.filter { it.finished != null && Instant.ofEpochMilli(it.finished).atZone(s.zone).year == year }
            .groupBy { Instant.ofEpochMilli(it.finished!!).atZone(s.zone).monthValue }
    }
    Column(modifier) {
        if (byMonth.isEmpty()) Text(stringResource(R.string.cal_year_empty), style = UType.Muted)
        // From the first month with a book to this one, the quiet ones too, so a gap shows as a gap.
        val first = byMonth.keys.minOrNull() ?: 1
        val last = if (byMonth.isEmpty()) 0 else if (year == s.today.year) s.today.monthValue else 12
        for (m in first..last) {
            val list = byMonth[m].orEmpty()
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    java.time.Month.of(m).getDisplayName(TextStyle.SHORT_STANDALONE, loc).uppercase(loc),
                    style = UType.Eyebrow, modifier = Modifier.width(44.dp),
                )
                Row(
                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(6.dp)).background(U.c.surface2).horizontalScroll(rememberScrollState()).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    list.forEach { b ->
                        Box(Modifier.width(33.dp).fillMaxSize().clickable(onClickLabel = b.title) { onBook(b.id) }) {
                            BookCover(b, Modifier.fillMaxSize(), small = true, bare = true)
                        }
                    }
                }
            }
        }
    }
}
