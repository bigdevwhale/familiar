package app.familiar.ui.books

import androidx.compose.foundation.layout.Arrangement
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
fun GenrePicker(genre: Genre, sub: String, all: List<OwnGenre>, onChange: (Genre, String) -> Unit) {
    val container = LocalContext.current.container
    var adding by remember { mutableStateOf<Genre?>(null) }
    val own = all.firstOrNull { it.key == OwnGenre.key(sub, genre) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Genre.entries.forEach { g ->
            Chip(genreName(g), genre == g && own?.standalone != true, { onChange(g, if (genre == g && own?.standalone != true) sub else "") }, genreColor(g))
        }
        all.filter { it.standalone }.forEach { o -> Chip(o.name, own?.key == o.key, { onChange(Genre.OTHER, o.name) }, Color(o.argb)) }
        Chip("+ " + stringResource(R.string.genre_own), false, { adding = Genre.OTHER }, U.c.gold)
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
    adding?.let { p ->
        GenreDialog(p, taken = all, onDismiss = { adding = null }) { g ->
            container.scope.launch { OwnGenres.add(container, g) }
            onChange(g.parent, g.name)
        }
    }
}
