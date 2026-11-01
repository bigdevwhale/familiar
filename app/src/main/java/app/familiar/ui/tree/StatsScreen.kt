package app.familiar.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.domain.DayPart
import app.familiar.domain.Insights
import app.familiar.ui.FamiliarData
import app.familiar.ui.components.Bar
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.Stat
import app.familiar.ui.components.UCard
import app.familiar.ui.genreColor
import app.familiar.ui.genreName
import app.familiar.ui.locale
import app.familiar.ui.pages
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import java.time.format.TextStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.familiar.domain.Finished
import app.familiar.ui.books
import app.familiar.ui.components.Chip

/** The ledger itself, scrolling: the books finished, then pace, habits and months. */
@Composable
fun StatsContent(data: FamiliarData, modifier: Modifier = Modifier) {
    val n = remember(data) { Insights(data.stats) }
    run {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            UCard {
                Row {
                    Stat(n.perDay.toString(), stringResource(R.string.stats_per_day), Modifier.weight(1f))
                    Stat(n.avgSitting?.toString() ?: "–", stringResource(R.string.stats_per_sitting), Modifier.weight(1f))
                    Stat(n.hours.toString(), stringResource(R.string.stats_hours), Modifier.weight(1f))
                }
            }

            FinishedBooks(data)

            SectionTitle(stringResource(R.string.stats_pace))
            UCard {
                if (n.pace == null) Text(stringResource(R.string.stats_pace_none, Insights.MIN_MINUTES), style = UType.Muted)
                else {
                    Text(stringResource(R.string.stats_pace_value, n.pace), style = UType.Title)
                    n.paceByGenre.takeIf { it.size > 1 }?.let { genres ->
                        val top = genres.first().second.toFloat()
                        genres.forEach { (g, p) ->
                            Spacer(Modifier.height(12.dp))
                            Row {
                                Text(genreName(g), style = UType.Small, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.stats_pace_short, p), style = UType.Small)
                            }
                            Spacer(Modifier.height(4.dp))
                            Bar(p / top, color = genreColor(g), track = U.c.surface2)
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.stats_when))
            UCard {
                val most = n.dayParts.values.max().coerceAtLeast(1).toFloat()
                n.bestPart?.let {
                    Text(stringResource(partBest(it)), style = UType.Strong)
                    Spacer(Modifier.height(4.dp))
                }
                n.bestWeekday?.let {
                    Text(stringResource(R.string.stats_weekday, it.getDisplayName(TextStyle.FULL_STANDALONE, locale())), style = UType.Muted)
                }
                if (n.bestPart == null) Text(stringResource(R.string.stats_empty), style = UType.Muted)
                DayPart.entries.forEach { p ->
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Text(stringResource(partName(p)), style = UType.Small, modifier = Modifier.weight(1f))
                        Text(n.dayParts[p].toString(), style = UType.Small)
                    }
                    Spacer(Modifier.height(4.dp))
                    Bar((n.dayParts[p] ?: 0) / most, color = if (p == n.bestPart) U.c.accent else U.c.muted, track = U.c.surface2)
                }
            }

            SectionTitle(stringResource(R.string.stats_months))
            UCard {
                val months = n.months
                val now = months.last()
                val before = months[months.size - 2]
                Text(stringResource(R.string.stats_this_month, pages(now.amount)), style = UType.Strong)
                val diff = now.amount - before.amount
                Text(
                    when {
                        before.amount == 0 -> stringResource(R.string.stats_month_first)
                        diff > 0 -> stringResource(R.string.stats_month_more, pages(diff))
                        diff < 0 -> stringResource(R.string.stats_month_less, pages(-diff))
                        else -> stringResource(R.string.stats_month_same)
                    },
                    style = UType.Muted,
                )
                Spacer(Modifier.height(14.dp))
                val top = months.maxOf { it.amount }.coerceAtLeast(1).toFloat()
                Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    months.forEach { m ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(m.amount.toString(), style = UType.Small, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.fillMaxWidth().height((110 * (m.amount / top)).coerceAtLeast(3f).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (m == now) U.c.mana else U.c.accentSoft)
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = U.c.line)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    months.forEach { m ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(m.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale()), style = UType.Small, maxLines = 1, textAlign = TextAlign.Center)
                            Text(if (m.books > 0) "📖 ${m.books}" else " ", style = UType.Small.copy(color = U.c.leaf), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The finished books of this year or of all of them: how many, how long, stars, genres, authors. */
@Composable
private fun FinishedBooks(data: FamiliarData) {
    val s = data.stats
    var all by rememberSaveable { mutableStateOf(false) }
    val f = remember(data, all) { Finished(s, if (all) null else s.today.year) }
    SectionTitle(stringResource(R.string.stats_books))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
        Chip(s.today.year.toString(), !all, { all = false })
        Chip(stringResource(R.string.tree_all), all, { all = true })
    }
    UCard {
        Row {
            Stat(f.books.size.toString(), stringResource(R.string.stats_books_done), Modifier.weight(1f))
            Stat(f.pages.toString(), stringResource(R.string.stats_books_pages), Modifier.weight(1f))
            Stat(f.avgLength?.toString() ?: "–", stringResource(R.string.stats_avg_length), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Stat(f.avgRating?.let { String.format(locale(), "%.1f", it) } ?: "–", stringResource(R.string.stats_avg_rating), Modifier.weight(1f), color = U.c.gold)
            Stat(f.avgDays?.toString() ?: "–", stringResource(R.string.stats_avg_days), Modifier.weight(1f))
            Stat(f.authorCount.toString(), stringResource(R.string.stats_authors_count), Modifier.weight(1f))
        }
    }
    if (f.books.isEmpty()) return

    // Month by month this year, or year by year over all of them.
    val loc = locale()
    val columns = if (all) f.byYear.map { (y, n) -> y.toString().takeLast(2).let { "’$it" } to n }
    else f.byMonth.mapIndexed { i, n -> java.time.Month.of(i + 1).getDisplayName(TextStyle.NARROW_STANDALONE, loc) to n }
    if (columns.size > 1) {
        SectionTitle(stringResource(if (all) R.string.stats_by_year else R.string.stats_by_month))
        UCard {
            val top = columns.maxOf { it.second }.coerceAtLeast(1).toFloat()
            val current = if (all) columns.lastIndex else s.today.monthValue - 1
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                columns.forEachIndexed { i, (_, n) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (n > 0) Text(n.toString(), style = UType.Small, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier.fillMaxWidth().height((96 * (n / top)).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (n == 0) U.c.line else if (i == current) U.c.gold else U.c.leaf)
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = U.c.line)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                columns.forEach { (label, _) -> Text(label, style = UType.Small, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
        }
    }

    SectionTitle(stringResource(R.string.stats_genres))
    UCard {
        val genres = f.genres.take(8)
        val top = genres.first().second.toFloat()
        genres.forEachIndexed { i, (g, n) ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Line(genreName(g), n.toString(), n / top, genreColor(g))
        }
    }

    if (f.ratings.any { it.second > 0 }) {
        SectionTitle(stringResource(R.string.stats_ratings))
        UCard {
            val top = f.ratings.maxOf { it.second }.coerceAtLeast(1).toFloat()
            f.ratings.forEachIndexed { i, (r, n) ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Line("★".repeat(r), n.toString(), n / top, U.c.gold, labelColor = U.c.gold)
            }
        }
    }

    if (f.lengths.any { it > 0 }) {
        SectionTitle(stringResource(R.string.stats_lengths))
        UCard {
            val top = f.lengths.max().coerceAtLeast(1).toFloat()
            val names = listOf(
                stringResource(R.string.stats_len_short, Finished.LENGTHS[0]),
                stringResource(R.string.stats_len_mid, Finished.LENGTHS[0], Finished.LENGTHS[1] - 1),
                stringResource(R.string.stats_len_mid, Finished.LENGTHS[1], Finished.LENGTHS[2] - 1),
                stringResource(R.string.stats_len_long, Finished.LENGTHS[2]),
            )
            f.lengths.forEachIndexed { i, n ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Line(names[i], n.toString(), n / top, U.c.accent)
            }
        }
    }

    // Only once there's an author you've come back to: a list of ones says nothing.
    if (f.authors.any { it.second > 1 }) {
        SectionTitle(stringResource(R.string.stats_authors))
        UCard {
            val top = f.authors.first().second.toFloat()
            f.authors.filter { it.second > 1 }.forEachIndexed { i, (a, n) ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Line(a, books(n), n / top, U.c.mana)
            }
        }
    }
}

/** A labelled bar: the name and its number above, the bar under them. */
@Composable
private fun Line(label: String, value: String, fraction: Float, color: androidx.compose.ui.graphics.Color, labelColor: androidx.compose.ui.graphics.Color = U.c.ink) {
    Row {
        Text(label, style = UType.Small.copy(color = labelColor), maxLines = 1, modifier = Modifier.weight(1f))
        Text(value, style = UType.Small)
    }
    Spacer(Modifier.height(4.dp))
    Bar(fraction, color = color, track = U.c.surface2)
}

private fun partName(p: DayPart): Int = when (p) {
    DayPart.MORNING -> R.string.part_morning
    DayPart.DAY -> R.string.part_day
    DayPart.EVENING -> R.string.part_evening
    DayPart.NIGHT -> R.string.part_night
}

private fun partBest(p: DayPart): Int = when (p) {
    DayPart.MORNING -> R.string.stats_best_morning
    DayPart.DAY -> R.string.stats_best_day
    DayPart.EVENING -> R.string.stats_best_evening
    DayPart.NIGHT -> R.string.stats_best_night
}
