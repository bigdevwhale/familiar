package app.familiar.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.AppContainer
import app.familiar.R
import app.familiar.container
import app.familiar.ui.components.Chip
import app.familiar.ui.components.UTextField
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch

/** The user's own shelves: made, renamed and removed together with the books on them. */
object Shelves {
    suspend fun add(c: AppContainer, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        c.settings.update { s -> if (s.shelves.any { it.equals(n, true) }) s else s.copy(shelves = s.shelves + n) }
    }

    suspend fun rename(c: AppContainer, from: String, to: String) {
        val n = to.trim()
        if (n.isEmpty() || n == from) return
        c.settings.update { s ->
            // The view picked for the shelf goes with it to its new name.
            val views = s.shelfViews[OWN + from]?.let { v -> s.shelfViews - (OWN + from) + (OWN + n to v) } ?: s.shelfViews
            s.copy(shelves = s.shelves.map { if (it == from) n else it }.distinct(), shelfViews = views)
        }
        val dao = c.db.dao()
        dao.books().filter { from in it.tagList }.forEach { b ->
            dao.updateBook(b.copy(tags = b.tagList.map { if (it == from) n else it }.distinct().joinToString("\n")))
        }
        c.changed()
    }

    suspend fun remove(c: AppContainer, name: String) {
        c.settings.update { s -> s.copy(shelves = s.shelves - name, shelfViews = s.shelfViews - (OWN + name)) }
        val dao = c.db.dao()
        dao.books().filter { name in it.tagList }.forEach { b -> dao.updateBook(b.copy(tags = (b.tagList - name).joinToString("\n"))) }
        c.changed()
    }
}

/** Asks for a shelf name; [initial] when renaming. */
@Composable
fun ShelfNameDialog(initial: String = "", onDismiss: () -> Unit, onName: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.bg,
        title = { Text(stringResource(if (initial.isEmpty()) R.string.shelf_new_title else R.string.shelf_rename), style = UType.Strong) },
        text = { UTextField(name, { name = it.take(30) }, placeholder = stringResource(R.string.shelf_name_hint)) },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onName(name.trim()); onDismiss() }) { Text(stringResource(R.string.save), color = U.c.accent, style = UType.Button) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted, style = UType.Button) } },
    )
}

/** Which of your shelves a book is on, with a chip to make a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShelfChips(all: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val container = LocalContext.current.container
    var adding by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEach { s -> Chip(s, s in selected, { onChange(if (s in selected) selected - s else selected + s) }) }
        Chip("+ " + stringResource(R.string.shelf_new), false, { adding = true })
    }
    if (adding) ShelfNameDialog(onDismiss = { adding = false }) { n ->
        container.scope.launch { Shelves.add(container, n) }
        onChange(selected + n)
    }
}
