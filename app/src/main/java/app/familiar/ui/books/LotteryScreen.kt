package app.familiar.ui.books

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.Den
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.Sparks
import app.familiar.ui.components.TopBar
import app.familiar.ui.genreName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Can't choose? The familiar shuffles the Want to read shelf and draws a book, with a little prophecy. */
@Composable
fun LotteryScreen(data: FamiliarData, go: Go) {
    val container = LocalContext.current.container
    val wanted = data.books.filter { it.shelfOf == Shelf.WANT }
    var round by rememberSaveable { mutableIntStateOf(0) }
    var drawn by rememberSaveable { mutableLongStateOf(0L) }
    var shown by remember { mutableIntStateOf(0) }
    val flip = remember { Animatable(0f) }
    LaunchedEffect(round) {
        if (wanted.isEmpty()) return@LaunchedEffect
        val rnd = Random(System.nanoTime())
        // Not the same book twice in a row, if there's a choice.
        val pick = wanted.filter { it.id != drawn || wanted.size == 1 }.random(rnd)
        drawn = 0L
        // Covers flicker past, slower and slower, then one stays.
        var wait = 60L
        repeat(14) { i ->
            shown = (shown + 1 + rnd.nextInt(wanted.size)) % wanted.size
            delay(wait)
            wait = (wait * 1.18f).toLong()
            if (i == 13) shown = wanted.indexOf(pick)
        }
        flip.snapTo(0.6f)
        flip.animateTo(1f, tween(260))
        drawn = pick.id
    }
    val book = wanted.firstOrNull { it.id == drawn }
    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding()) {
        TopBar(stringResource(R.string.lottery), { go.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lottery_text, data.pet?.name ?: ""), style = UType.Sub, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (wanted.isEmpty()) {
                Text(stringResource(R.string.lottery_empty), style = UType.Body, textAlign = TextAlign.Center)
                return@Column
            }
            Den(Modifier.fillMaxWidth().height(320.dp)) {
                val b = book ?: wanted.getOrNull(shown) ?: wanted.first()
                BookCover(
                    b.title, Genre.of(b.genre),
                    Modifier.align(Alignment.Center).padding(bottom = 40.dp).width(140.dp).height(206.dp).graphicsLayer { scaleX = if (book != null) flip.value else 1f },
                    cover = b.cover,
                )
                data.look()?.let { FamiliarArt(it.copy(happy = book != null), Modifier.align(Alignment.BottomStart).size(150.dp), fps = if (book == null) 10f else 5f) }
                if (book != null) Sparks(Modifier.fillMaxSize(), key = book.id)
            }
            Spacer(Modifier.height(14.dp))
            if (book == null) Text(stringResource(R.string.lottery_drawing), style = UType.Muted)
            else {
                Text(book.title, style = UType.Title, textAlign = TextAlign.Center)
                if (book.author.isNotBlank()) Text(book.author, style = UType.Muted)
                Spacer(Modifier.height(10.dp))
                Text(prophecy(Genre.of(book.genre)), style = UType.Quote.copy(textAlign = TextAlign.Center))
                Text(genreName(Genre.of(book.genre)), style = UType.Small.copy(color = U.c.gold), modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        if (wanted.isNotEmpty()) Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            PrimaryButton(stringResource(R.string.lottery_take), {
                book?.let { b ->
                    container.write { dao().updateBook(b.copy(shelf = Shelf.READING.name, started = b.started ?: System.currentTimeMillis())) }
                    go.back()
                    go.book(b.id)
                }
            }, Modifier.fillMaxWidth(), enabled = book != null, icon = Icons.Rounded.LocalFireDepartment)
            Spacer(Modifier.height(8.dp))
            SoftButton(stringResource(R.string.lottery_again), { if (book != null) round++ }, Modifier.fillMaxWidth(), color = U.c.gold, soft = U.c.goldSoft, icon = Icons.Rounded.Casino)
        }
    }
}

@Composable
private fun prophecy(g: Genre): String = stringResource(
    when (g) {
        Genre.FANTASY -> R.string.prophecy_fantasy
        Genre.SCIFI -> R.string.prophecy_scifi
        Genre.MYSTERY -> R.string.prophecy_mystery
        Genre.HORROR -> R.string.prophecy_horror
        Genre.ROMANCE -> R.string.prophecy_romance
        Genre.ADVENTURE -> R.string.prophecy_adventure
        Genre.CLASSIC -> R.string.prophecy_classic
        Genre.HISTORY -> R.string.prophecy_history
        Genre.NONFICTION -> R.string.prophecy_nonfiction
        Genre.POETRY -> R.string.prophecy_poetry
        Genre.OTHER -> R.string.prophecy_other
    }
)
