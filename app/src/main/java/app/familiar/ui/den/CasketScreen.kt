package app.familiar.ui.den

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.domain.Genre
import app.familiar.domain.Trinket
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.Bar
import app.familiar.ui.components.DreamArt
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.TrinketArt
import app.familiar.ui.components.UCard
import app.familiar.ui.genreName
import app.familiar.ui.seasonName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import app.familiar.ui.trinketName
import app.familiar.ui.trinketText

/** The casket of finds, and the album of dreams. */
@Composable
fun CasketScreen(data: FamiliarData, go: Go) {
    val counts = remember(data.finds) { data.finds.groupingBy { it.kind }.eachCount() }
    var open by remember { mutableStateOf<Trinket?>(null) }
    var dream by remember { mutableStateOf<Genre?>(null) }
    val seen = data.settings.dreams
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding()) {
        TopBar(stringResource(R.string.casket), { go.back() })
        LazyVerticalGrid(
            GridCells.Fixed(5),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(5) }) {
                Column {
                    Text(stringResource(R.string.casket_text), style = UType.Sub)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.casket_count, data.owned.size, Trinket.entries.size), style = UType.Strong.copy(color = U.c.gold))
                    Spacer(Modifier.height(6.dp))
                    Bar(data.owned.size / Trinket.entries.size.toFloat(), color = U.c.gold, track = U.c.goldSoft)
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(Trinket.entries, key = { it.name }) { t ->
                val n = counts[t.name] ?: 0
                Box(
                    Modifier.aspectRatio(1f).clip(U.RMd).background(if (n > 0) U.c.surface else U.c.surface2).clickable { open = t },
                    contentAlignment = Alignment.Center,
                ) {
                    TrinketArt(t, Modifier.fillMaxSize().padding(8.dp), dim = n == 0)
                    if (n > 1) Text(stringResource(R.string.find_times, n), style = UType.Small.copy(color = U.c.gold), modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
                    if (t.season != null && n == 0) Text("✦", style = UType.Small.copy(color = U.c.gold), modifier = Modifier.align(Alignment.TopEnd).padding(3.dp))
                }
            }
            item(span = { GridItemSpan(5) }) {
                Column(Modifier.padding(top = 18.dp)) {
                    Text(stringResource(R.string.dreams), style = UType.Title)
                    Text(stringResource(R.string.dreams_text), style = UType.Sub)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.casket_count, seen.size, Genre.entries.size), style = UType.Small.copy(color = U.c.accent))
                }
            }
            items(Genre.entries, key = { "dream:" + it.name }, span = { GridItemSpan(5) }) { g ->
                val has = g.name in seen
                UCard(onClick = if (has) ({ dream = g }) else null, padding = PaddingValues(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(96.dp).height(66.dp)) {
                            if (has) DreamArt(g, Modifier.fillMaxSize(), fps = 0f)
                            else Text("?", style = UType.Title.copy(color = U.c.faint), modifier = Modifier.align(Alignment.Center))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(genreName(g), style = UType.Strong)
                            Text(if (has) dreamText(g, data.pet?.name ?: "").substringBefore(',').take(70) + "…" else stringResource(R.string.dream_unknown), style = UType.Muted, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
    open?.let { t ->
        val n = counts[t.name] ?: 0
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = U.c.bg,
            icon = { TrinketArt(t, Modifier.size(96.dp), dim = n == 0) },
            title = { Text(if (n > 0) trinketName(t) else stringResource(R.string.find_unknown), style = UType.Title, textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (n > 0) Text(trinketText(t), style = UType.Body, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            t.season != null -> stringResource(R.string.find_from_season, seasonName(t.season))
                            t.genre != null -> stringResource(R.string.find_from_genre, genreName(t.genre).lowercase())
                            else -> stringResource(R.string.find_from_any)
                        },
                        style = UType.Small.copy(color = U.c.gold), textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.nice), color = U.c.accent, style = UType.Button) } },
        )
    }
    dream?.let { g ->
        app.familiar.ui.components.CelebrateDialog(
            color = androidx.compose.ui.graphics.Color(g.argb),
            eyebrow = stringResource(R.string.dream),
            title = genreName(g),
            text = dreamText(g, data.pet?.name ?: ""),
            onDismiss = { dream = null },
        ) { DreamArt(g, Modifier.fillMaxSize()) }
    }
}
