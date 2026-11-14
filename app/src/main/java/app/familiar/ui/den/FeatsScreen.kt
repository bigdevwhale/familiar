package app.familiar.ui.den

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.content.FeatNames
import app.familiar.domain.Achievement
import app.familiar.domain.FeatGroup
import app.familiar.domain.Feats
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.BadgeArt
import app.familiar.ui.components.Bar
import app.familiar.ui.components.TopBar
import app.familiar.ui.dateOf
import app.familiar.ui.locale
import app.familiar.ui.pix.Badges
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType

@Composable
fun featGroupName(g: FeatGroup): String = stringResource(
    when (g) {
        FeatGroup.READING -> R.string.feats_reading
        FeatGroup.HABITS -> R.string.feats_habits
        FeatGroup.SHELF -> R.string.feats_shelf
        FeatGroup.FAMILIARS -> R.string.feats_familiars
        FeatGroup.DEN -> R.string.feats_den
    }
)

/** The book of achievements: every medal, page by page, the earned ones in their metal. */
@Composable
fun FeatsScreen(data: FamiliarData, go: Go) {
    val l = locale()
    val earned = data.earned
    var open by remember { mutableStateOf<Achievement?>(null) }
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding()) {
        TopBar(stringResource(R.string.feats), { go.back() })
        LazyVerticalGrid(
            GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(4) }) {
                Column {
                    Text(stringResource(R.string.feats_text), style = UType.Sub)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.casket_count, Feats.all.count { it.id in earned }, Feats.all.size), style = UType.Strong.copy(color = U.c.gold))
                    Spacer(Modifier.height(6.dp))
                    Bar(Feats.all.count { it.id in earned } / Feats.all.size.toFloat(), color = U.c.gold, track = U.c.goldSoft)
                }
            }
            FeatGroup.entries.forEach { g ->
                val list = Feats.of(g)
                item(span = { GridItemSpan(4) }, key = "group:" + g.name) {
                    Column(Modifier.padding(top = 14.dp)) {
                        Text(featGroupName(g), style = UType.Title)
                        Text(stringResource(R.string.casket_count, list.count { it.id in earned }, list.size), style = UType.Small.copy(color = U.c.accent))
                    }
                }
                items(list, key = { it.id }) { a ->
                    val has = a.id in earned
                    val secret = a.feat.hidden && !has
                    Column(
                        Modifier.clip(U.RMd).background(if (has) U.c.surface else U.c.surface2).clickable { open = a }.padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BadgeArt(a, has, Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 4.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (secret) "???" else FeatNames.name(l, a),
                            style = UType.Small.copy(color = if (has) U.c.ink else U.c.muted, textAlign = TextAlign.Center),
                            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    open?.let { a ->
        val has = a.id in earned
        val secret = a.feat.hidden && !has
        val value = data.feats[a.feat] ?: 0
        val metal = Color(Badges.argb(a.rank))
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = U.c.bg,
            icon = { BadgeArt(a, has, Modifier.size(112.dp)) },
            title = { Text(if (secret) stringResource(R.string.feat_secret) else FeatNames.name(l, a), style = UType.Title, textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(if (secret) stringResource(R.string.feat_secret_text) else FeatNames.what(l, a), style = UType.Body, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    if (has) {
                        val day = data.settings.earnedDays[a.id]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                        Text(
                            if (day != null) stringResource(R.string.feat_earned_on, dateOf(day)) else stringResource(R.string.feat_earned),
                            style = UType.Small.copy(color = metal), textAlign = TextAlign.Center,
                        )
                    } else if (!secret) {
                        // Steps of one thing (a book of 500 pages, a ritual of an hour) have no bar to fill.
                        if (a.target > 1) {
                            Bar(value / a.target.toFloat(), color = U.c.gold, track = U.c.goldSoft)
                            Text(String.format(l, "%,d / %,d", value.coerceAtMost(a.target), a.target), style = UType.Small, modifier = Modifier.padding(top = 3.dp))
                        }
                        Text(stringResource(R.string.feat_gives, a.reward), style = UType.Small.copy(color = U.c.mana), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.nice), color = U.c.accent, style = UType.Button) } },
        )
    }
}
