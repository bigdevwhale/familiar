package app.familiar.ui.den

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.domain.Den
import app.familiar.domain.Genre
import app.familiar.domain.Guard
import app.familiar.domain.Item
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.components.pixels
import app.familiar.ui.components.rememberTime
import app.familiar.ui.itemName
import app.familiar.ui.pix.Room
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** The den's furniture, bought with sparks. Buying never takes anything from the familiar's growth. */
@Composable
fun FurnishScreen(data: FamiliarData, go: Go) {
    val container = LocalContext.current.container
    val s = data.settings
    val sparks = Den.sparks(data.pets, s.spent)
    val books = data.stats.finished.map { Genre.of(it.genre).argb.toInt() }.takeLast(24)
    val hour = LocalTime.now().hour
    val night = hour < 6 || hour >= 20
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.furnish), { go.back() })
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Text(stringResource(R.string.sparks_n, sparks), style = UType.Title.copy(color = U.c.gold))
                    Text(stringResource(R.string.furnish_text), style = UType.Sub)
                    Spacer(Modifier.height(10.dp))
                    UCard {
                        Text(stringResource(R.string.guard_title), style = UType.Strong.copy(color = U.c.ember))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.guard_text, Guard.MAX), style = UType.Muted)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.guard_have, s.candles, Guard.MAX), style = UType.Small.copy(color = U.c.gold))
                        Spacer(Modifier.height(8.dp))
                        val can = s.candles < Guard.MAX && sparks >= Guard.PRICE
                        SoftButton(
                            if (s.candles >= Guard.MAX) stringResource(R.string.guard_full) else stringResource(R.string.guard_buy, Guard.PRICE),
                            {
                                if (can) container.scope.launch {
                                    container.settings.update {
                                        // Checked again inside the write so a double tap can't spend twice.
                                        if (it.candles >= Guard.MAX || Den.sparks(data.pets, it.spent) < Guard.PRICE) it
                                        else it.copy(spent = it.spent + Guard.PRICE, candles = it.candles + 1)
                                    }
                                }
                            },
                            Modifier.fillMaxWidth(),
                            color = if (can) U.c.ember else U.c.faint,
                            soft = if (can) U.c.emberSoft else U.c.surface2,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(Item.entries, key = { it.name }) { item ->
                val owned = item.name in s.owned
                val standing = item.name in s.placed
                UCard(padding = PaddingValues(10.dp)) {
                    Preview(item, books, night, Modifier.fillMaxWidth().aspectRatio(1.2f).clip(U.RMd).background(Color(0xFF2A2030)))
                    Spacer(Modifier.height(8.dp))
                    Text(itemName(item), style = UType.Strong, maxLines = 2, minLines = 2)
                    Text(
                        when {
                            standing -> stringResource(R.string.item_standing)
                            owned -> stringResource(R.string.item_owned)
                            else -> stringResource(R.string.sparks_n, item.price)
                        },
                        style = UType.Small.copy(color = if (owned) U.c.leaf else U.c.gold),
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        standing -> SoftButton(stringResource(R.string.item_remove), {
                            container.scope.launch { container.settings.update { it.copy(placed = it.placed - item.name) } }
                        }, Modifier.fillMaxWidth(), color = U.c.muted, soft = U.c.surface2)
                        owned -> SoftButton(stringResource(R.string.item_place), {
                            container.scope.launch { container.settings.update { it.copy(placed = Den.place(it.placed, item)) } }
                        }, Modifier.fillMaxWidth())
                        else -> SoftButton(
                            stringResource(R.string.item_buy), {
                                container.scope.launch {
                                    container.settings.update {
                                        // Checked again inside the write so a double tap can't spend twice.
                                        if (item.name in it.owned || Den.sparks(data.pets, it.spent) < item.price) it
                                        else it.copy(spent = it.spent + item.price, owned = it.owned + item.name, placed = Den.place(it.placed, item))
                                    }
                                }
                            },
                            Modifier.fillMaxWidth(),
                            color = if (sparks >= item.price) U.c.gold else U.c.faint,
                            soft = if (sparks >= item.price) U.c.goldSoft else U.c.surface2,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Text(stringResource(R.string.furnish_note), style = UType.Small.copy(color = U.c.muted, textAlign = TextAlign.Center), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Preview(item: Item, books: List<Int>, night: Boolean, modifier: Modifier) {
    val frames by produceState(null as List<ImageBitmap>?, item, night) {
        value = withContext(Dispatchers.Default) { List(Room.FRAMES) { Room.preview(item, books, night, it).bitmap() } }
    }
    val t by rememberTime()
    Canvas(modifier) { frames?.let { pixels(it[(t * 3 * Room.STEP).toInt() % it.size]) } }
}
