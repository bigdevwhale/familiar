package app.familiar.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Share
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
import app.familiar.ui.Go
import app.familiar.ui.components.Bar
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.Stat
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.genreColor
import app.familiar.ui.genreName
import app.familiar.ui.locale
import app.familiar.ui.pages
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import java.time.format.TextStyle

/** Pace, habits and the last months, in plain numbers. */
@Composable
fun StatsScreen(data: FamiliarData, go: Go) {
    val n = remember(data) { Insights(data.stats) }
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.stats_title), { go.back() }) {
            androidx.compose.material3.IconButton(onClick = { go.share(app.familiar.ui.share.Cards.Kind.YEAR) }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Share, stringResource(R.string.share), tint = U.c.muted)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            UCard {
                Row {
                    Stat(n.perDay.toString(), stringResource(R.string.stats_per_day), Modifier.weight(1f))
                    Stat(n.avgSitting?.toString() ?: "–", stringResource(R.string.stats_per_sitting), Modifier.weight(1f))
                    Stat(n.hours.toString(), stringResource(R.string.stats_hours), Modifier.weight(1f))
                }
            }

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
