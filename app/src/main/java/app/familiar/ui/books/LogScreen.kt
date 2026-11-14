package app.familiar.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.familiar.data.Library
import app.familiar.domain.Species
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.TopBar
import app.familiar.ui.lastFeast
import app.familiar.ui.ritual.SittingForm
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pages read away from the app (on the train, in bed), logged afterwards. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LogScreen(id: Long, data: FamiliarData, go: Go) {
    val container = LocalContext.current.container
    val book = data.book(id)
    if (book == null) {
        LaunchedEffect(Unit) { go.back() }
        return
    }
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding().imePadding()) {
        TopBar(book.title, { go.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.log_text), style = UType.Sub)
            // What to log: pages, minutes of reading (pages unknown), or minutes of the audio version.
            var mode by remember { mutableStateOf(LogMode.PAGES) }
            var minutes by remember { mutableStateOf("30") }
            val byTime = mode != LogMode.PAGES
            val listened = book.audio || mode == LogMode.LISTEN
            if (!book.audio) {
                app.familiar.ui.components.SectionTitle(stringResource(R.string.log_what))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    LogMode.entries.forEach { m ->
                        app.familiar.ui.components.Chip(
                            stringResource(when (m) { LogMode.PAGES -> R.string.log_pages_mode; LogMode.READ -> R.string.log_read_mode; LogMode.LISTEN -> R.string.log_listen_mode }),
                            mode == m, { mode = m },
                        )
                    }
                }
            }
            if (byTime) {
                app.familiar.ui.components.SectionTitle(stringResource(if (listened) R.string.log_minutes else R.string.log_minutes_read))
                app.familiar.ui.components.UTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, number = true, placeholder = "30")
            }
            androidx.compose.runtime.key(mode) {
            SittingForm(book, book.page, book.page, data.species == Species.SLIME, stringResource(R.string.feed), timed = byTime, listen = byTime && listened, skipPage = byTime, pickDay = true) { input ->
                container.scope.launch {
                    val now = System.currentTimeMillis()
                    val mins = if (byTime) (minutes.toIntOrNull() ?: 0).coerceIn(0, 600) else 0
                    val r = Library.sit(
                        container.db.dao(), book, book.page, input.toPage, now - mins * 60_000L, now, timed = mins > 0,
                        listened = listened, item = input.item, finish = input.finish, guarded = data.settings.guardedDays,
                        guest = data.guest != null, companionId = data.settings.companion,
                    )
                    // Finished another day: the book is dated then, the sitting stays today's.
                    input.finishedOn?.let { day ->
                        val dao = container.db.dao()
                        dao.book(book.id)?.takeIf { it.shelfOf == app.familiar.data.Shelf.DONE }?.let { b ->
                            dao.updateBook(b.copy(finished = day, started = b.started?.coerceAtMost(day), added = b.added.coerceAtMost(day)))
                        }
                    }
                    container.changed()
                    withContext(Dispatchers.Main) {
                        lastFeast.value = r
                        if (r != null) go.feast() else go.back()
                    }
                }
            }
            }
        }
    }
}

private enum class LogMode { PAGES, READ, LISTEN }
