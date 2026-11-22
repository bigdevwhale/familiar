package app.familiar.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.AppContainer
import app.familiar.R
import app.familiar.container
import app.familiar.domain.Genre
import app.familiar.domain.OwnGenre
import app.familiar.ui.components.Chip
import app.familiar.ui.components.UTextField
import app.familiar.ui.genreColor
import app.familiar.ui.genreName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch

/** The reader's own genres and subgenres: made, renamed (or moved) and removed together with the books in them. */
object OwnGenres {
    suspend fun add(c: AppContainer, g: OwnGenre) {
        c.settings.update { s -> if (s.genres.any { it.key == g.key }) s else s.copy(genres = s.genres + g) }
    }

    /** Renames [from] to [to] (and moves it to another genre); its books follow. */
    suspend fun change(c: AppContainer, from: OwnGenre, to: OwnGenre) {
        c.settings.update { s -> s.copy(genres = (s.genres.filter { it.key != from.key && it.key != to.key } + to)) }
        val dao = c.db.dao()
        dao.books().filter { OwnGenre.of(it)?.key == from.key }.forEach { b -> dao.updateBook(b.copy(sub = to.name, genre = to.parent.name)) }
        c.changed()
    }

    /** Forgets [g]; its books stay in the genre it was part of. */
    suspend fun remove(c: AppContainer, g: OwnGenre) {
        c.settings.update { s -> s.copy(genres = s.genres.filter { it.key != g.key }) }
        val dao = c.db.dao()
        dao.books().filter { OwnGenre.of(it)?.key == g.key }.forEach { b -> dao.updateBook(b.copy(sub = "")) }
        c.changed()
    }
}

/** Asks for an own genre: its name, and the genre it belongs to (or none). [initial] when changing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreDialog(parent: Genre, initial: OwnGenre? = null, taken: List<OwnGenre> = emptyList(), onDismiss: () -> Unit, onDone: (OwnGenre) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var under by remember { mutableStateOf(initial?.parent ?: parent) }
    val n = name.trim()
    // A name already used in the same genre would merge two; a built-in one would only confuse.
    // The same name under another genre is fine: "Mysticism" in horror and in mystery.
    val clash = taken.any { it.key == OwnGenre.key(n, under) && it.key != initial?.key }
    val builtIn = Genre.entries.map { genreName(it).lowercase() }
    val ok = n.isNotEmpty() && !clash && n.lowercase() !in builtIn
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = U.c.bg,
        title = {
            Text(
                stringResource(
                    when {
                        initial != null -> R.string.genre_change
                        under == Genre.OTHER -> R.string.genre_new_title
                        else -> R.string.subgenre_new_title
                    }
                ),
                style = UType.Strong,
            )
        },
        text = {
            Column {
                UTextField(name, { name = it.take(OwnGenre.MAX_NAME) }, placeholder = stringResource(if (under == Genre.OTHER) R.string.genre_name_hint else R.string.subgenre_name_hint))
                if (clash) Text(stringResource(R.string.genre_taken), style = UType.Small.copy(color = U.c.accent), modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.genre_under), style = UType.Muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Genre.entries.filter { it != Genre.OTHER }.forEach { g -> Chip(genreName(g), under == g, { under = g }, genreColor(g)) }
                    Chip(stringResource(R.string.genre_standalone), under == Genre.OTHER, { under = Genre.OTHER }, U.c.gold)
                }
                Text(stringResource(R.string.genre_under_note), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (ok) { onDone(OwnGenre(n, under)); onDismiss() } }, enabled = ok) {
                Text(stringResource(R.string.save), color = if (ok) U.c.accent else U.c.faint, style = UType.Button)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = U.c.muted, style = UType.Button) } },
    )
}

/**
 * The genre of a book: the built-in ones and your own standing by themselves, then the subgenres
 * of the chosen one. [sub] is the own genre's name, or empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenrePicker(genre: Genre, sub: String, all: List<OwnGenre>, books: List<app.familiar.data.Book> = emptyList(), onChange: (Genre, String) -> Unit) {
    val container = LocalContext.current.container
    var adding by remember { mutableStateOf<Genre?>(null) }
    var changing by remember { mutableStateOf<OwnGenre?>(null) }
    var removing by remember { mutableStateOf<OwnGenre?>(null) }
    var managing by remember { mutableStateOf(false) }
    val own = all.firstOrNull { it.key == OwnGenre.key(sub, genre) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Genre.entries.forEach { g ->
            Chip(genreName(g), genre == g && own?.standalone != true, { onChange(g, if (genre == g && own?.standalone != true) sub else "") }, genreColor(g))
        }
        all.filter { it.standalone }.forEach { o -> Chip(o.name, own?.key == o.key, { onChange(Genre.OTHER, o.name) }, Color(o.argb)) }
        Chip("+ " + stringResource(R.string.genre_own), false, { adding = Genre.OTHER }, U.c.gold)
        // all one's genres in one place, to rename or delete (#77)
        if (all.isNotEmpty()) Chip("✎ " + stringResource(R.string.genres_mine), false, { managing = true }, U.c.gold)
    }
    if (genre != Genre.OTHER) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.subgenre_of, genreName(genre)), style = UType.Muted)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.filter { it.parent == genre }.forEach { o ->
                Chip(o.name, own?.key == o.key, { onChange(genre, if (own?.key == o.key) "" else o.name) }, Color(o.argb))
            }
            Chip("+ " + stringResource(R.string.subgenre), false, { adding = genre }, genreColor(genre))
        }
    }
    // the chosen own genre can be renamed or deleted right here (#77): before, only from a shelf filtered to it alone
    if (own != null) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (own.standalone) R.string.genre_line else R.string.subgenre_line, own.name), style = UType.Small.copy(color = Color(own.argb)), modifier = Modifier.weight(1f))
        TextButton(onClick = { changing = own }) { Text(stringResource(R.string.genre_change_short), color = U.c.accent, style = UType.Button) }
        TextButton(onClick = { removing = own }) { Text(stringResource(R.string.delete), color = U.c.muted, style = UType.Button) }
    }
    changing?.let { g ->
        GenreDialog(g.parent, g, all, onDismiss = { changing = null }) { to ->
            container.scope.launch { OwnGenres.change(container, g, to) }
            onChange(to.parent, to.name)
        }
    }
    removing?.let { g -> RemoveGenreDialog(g, books, { removing = null }) { onChange(g.parent, "") } }
    if (managing) OwnGenresSheet(all, books, onDismiss = { managing = false }) { from, to ->
        if (own?.key == from.key) onChange(to?.parent ?: from.parent, to?.name ?: "")
    }
    adding?.let { p ->
        GenreDialog(p, taken = all, onDismiss = { adding = null }) { g ->
            container.scope.launch { OwnGenres.add(container, g) }
            onChange(g.parent, g.name)
        }
    }
}

/** Asks before deleting an own genre, saying how many books are in it and where they go (#77). */
@Composable
fun RemoveGenreDialog(g: OwnGenre, books: List<app.familiar.data.Book>, onDismiss: () -> Unit, onDone: () -> Unit = {}) {
    val ctx = LocalContext.current
    val container = ctx.container
    val n = books.count { OwnGenre.of(it)?.key == g.key }
    app.familiar.ui.components.ConfirmDialog(
        title = stringResource(if (g.standalone) R.string.genre_delete_title else R.string.subgenre_delete_title, g.name),
        text = if (n == 0) stringResource(R.string.genre_delete_none)
            else stringResource(R.string.genre_delete_n, n, genreName(g.parent)),
        confirm = stringResource(R.string.delete),
        danger = true,
        onConfirm = { container.scope.launch { OwnGenres.remove(container, g) }; onDone() },
        onDismiss = onDismiss,
    )
}

/**
 * All of one's own genres and subgenres in one place (#77): grouped by the genre they are part of, each with
 * its books and a pencil and a bin. [onChanged] says what became of one: renamed to the second, or deleted (null).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OwnGenresSheet(all: List<OwnGenre>, books: List<app.familiar.data.Book>, onDismiss: () -> Unit, onChanged: (OwnGenre, OwnGenre?) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    val container = ctx.container
    var changing by remember { mutableStateOf<OwnGenre?>(null) }
    var removing by remember { mutableStateOf<OwnGenre?>(null) }
    // the list follows the changes at once, before the settings come round again
    var list by remember { mutableStateOf(all) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = U.c.bg) {
        Column(Modifier.padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 20.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.genres_mine), style = UType.Title)
            Text(stringResource(R.string.genres_mine_text), style = UType.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            if (list.isEmpty()) Text(stringResource(R.string.genres_own_empty), style = UType.Muted, modifier = Modifier.padding(vertical = 16.dp))
            val groups = list.groupBy { if (it.standalone) null else it.parent }
            (listOf<Genre?>(null) + Genre.entries).forEach { parent ->
                val gs = groups[parent] ?: return@forEach
                Text(
                    (if (parent == null) stringResource(R.string.genres_standalone) else stringResource(R.string.subgenre_of, genreName(parent))).uppercase(),
                    style = UType.Small.copy(color = parent?.let { genreColor(it) } ?: U.c.gold, letterSpacing = 1.sp),
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
                gs.forEach { g ->
                    val n = books.count { OwnGenre.of(it)?.key == g.key }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(g.argb)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(g.name, style = UType.Strong)
                            Text(if (n == 0) stringResource(R.string.genre_no_books) else ctx.resources.getQuantityString(R.plurals.books, n, n), style = UType.Small.copy(color = U.c.muted))
                        }
                        IconButton(onClick = { changing = g }) { Icon(Icons.Rounded.Edit, stringResource(R.string.genre_change_short), tint = U.c.accent) }
                        IconButton(onClick = { removing = g }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = U.c.muted) }
                    }
                }
            }
        }
    }
    changing?.let { g ->
        GenreDialog(g.parent, g, list, onDismiss = { changing = null }) { to ->
            container.scope.launch { OwnGenres.change(container, g, to) }
            list = list.map { if (it.key == g.key) to else it }
            onChanged(g, to)
        }
    }
    removing?.let { g -> RemoveGenreDialog(g, books, { removing = null }) { list = list.filter { it.key != g.key }; onChanged(g, null) } }
}
