package app.familiar.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Library
import app.familiar.data.Shelf
import app.familiar.domain.Series
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.Bar
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.progressLabel
import app.familiar.ui.shelfName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch

/** A cycle's volumes in order, and the reward once all of them are read. */
@Composable
fun SeriesScreen(name: String, data: FamiliarData, go: Go) {
    val container = LocalContext.current.container
    val c = Series.all(data.books).firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    if (c == null) {
        LaunchedEffect(Unit) { go.back() }
        return
    }
    val claimed = c.key in data.settings.claimed
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(c.name, { go.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.cycle_of, c.done, c.books.size), style = UType.Strong)
            Spacer(Modifier.height(8.dp))
            Bar(c.done / c.books.size.toFloat(), color = U.c.gold, track = U.c.goldSoft)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.cycle_text, Series.REWARD), style = UType.Small.copy(color = U.c.muted))
            if (c.complete) {
                Spacer(Modifier.height(12.dp))
                UCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = U.c.gold)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.cycle_done), style = UType.Strong.copy(color = U.c.gold), modifier = Modifier.weight(1f))
                    }
                    if (!claimed) {
                        Spacer(Modifier.height(10.dp))
                        SoftButton(stringResource(R.string.claim, Series.REWARD), {
                            container.scope.launch {
                                // Claimed in the same write that checks it, so a double tap can't pay twice.
                                var pay = false
                                container.settings.update { if (c.key in it.claimed) it else { pay = true; it.copy(claimed = it.claimed + c.key) } }
                                if (pay) Library.reward(container.db.dao(), Series.REWARD)
                                container.changed()
                            }
                        }, Modifier.fillMaxWidth(), color = U.c.gold, soft = U.c.goldSoft)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            c.books.forEach { b ->
                val next = b.id == c.next?.id
                UCard(Modifier.padding(bottom = 10.dp).then(if (next) Modifier.border(1.5.dp, U.c.accent, U.RMd) else Modifier), onClick = { go.book(b.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(b, Modifier.width(50.dp).height(72.dp), small = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            // The one to read next says so, over its number.
                            val eyebrow = listOfNotNull(b.volume?.let { stringResource(R.string.volume_n, it) }, if (next) stringResource(R.string.cycle_next_short) else null).joinToString(" · ")
                            if (eyebrow.isNotEmpty()) Text(eyebrow.uppercase(), style = UType.Eyebrow.copy(color = if (next) U.c.accent else UType.Eyebrow.color))
                            Text(b.title, style = UType.Strong, maxLines = 2)
                            Text(if (b.shelfOf == Shelf.DONE) shelfName(Shelf.DONE) else progressLabel(b), style = UType.Small)
                        }
                        if (b.shelfOf == Shelf.DONE) Icon(Icons.Rounded.CheckCircle, null, tint = U.c.leaf)
                    }
                }
            }
            SoftButton(stringResource(R.string.cycle_add), { go.editBook(series = c.name) }, Modifier.fillMaxWidth(), color = U.c.muted, soft = U.c.surface2)
        }
    }
}
