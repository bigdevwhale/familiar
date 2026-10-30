package app.familiar.ui.ritual

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Ambience
import app.familiar.data.Library
import app.familiar.data.Ritual
import app.familiar.domain.Species
import app.familiar.read.Sound
import app.familiar.read.rememberFaceDown
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.Chip
import app.familiar.ui.components.ConfirmDialog
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.components.pixels
import app.familiar.ui.components.rememberNow
import app.familiar.ui.components.rememberTime
import app.familiar.ui.lastFeast
import app.familiar.ui.pix.Scenes
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reading by candlelight: a timer, a page counter and the familiar dozing beside you. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RitualScreen(id: Long, data: FamiliarData, go: Go, dim: (Boolean) -> Unit) {
    val container = LocalContext.current.container
    val haptics = LocalHapticFeedback.current
    val s = data.settings
    val ritual = s.ritual
    val book = data.book(ritual?.bookId ?: id)
    var finishing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }

    fun update(change: (Ritual) -> Ritual) {
        container.scope.launch { container.settings.update { st -> st.copy(ritual = st.ritual?.let(change)) } }
    }

    // Lit once when the screen opens; never again after it was put out here.
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (book != null) container.settings.update { it.copy(ritual = it.ritual ?: Ritual(book.id, System.currentTimeMillis(), book.page, book.page, 0)) }
    }
    if (book == null) {
        LaunchedEffect(Unit) { go.back() }
        return
    }
    if (ritual == null) return

    val down = rememberFaceDown()
    // No fireplace over an audiobook: the voice matters more.
    // Listening: an audiobook, or a book switched to its audio version for this sitting.
    val listening = book.audio || ritual.listen
    DisposableEffect(s.ambience, listening) {
        Sound.ambience(if (listening) Ambience.OFF else s.ambience)
        onDispose { Sound.stop() }
    }
    LaunchedEffect(down, s.stillDim) { dim(down && s.stillDim) }
    DisposableEffect(Unit) { onDispose { dim(false) } }
    // Time with the phone face down counts toward the stillness bonus.
    LaunchedEffect(down) {
        if (!down) return@LaunchedEffect
        var since = System.currentTimeMillis()
        while (true) {
            delay(5_000)
            val now = System.currentTimeMillis()
            val add = now - since
            since = now
            update { it.copy(stillMs = it.stillMs + add) }
        }
    }
    BackHandler { go.back() }

    val now = rememberNow(1000)
    val elapsed = (now - ritual.start).coerceAtLeast(0)
    val left = 1f - elapsed / (s.candleMin * 60_000f)
    // An audiobook counts itself: every minute listened is a minute further.
    val heard = (ritual.fromPage + (elapsed / 60_000).toInt()).coerceAtMost(book.cap)

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(book.title, null) {
            IconButton(onClick = { discarding = true }) { Icon(Icons.Rounded.Close, stringResource(R.string.ritual_discard), tint = U.c.muted) }
        }
        if (finishing) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                Text(stringResource(if (listening) R.string.listen_done_title else R.string.ritual_done_title), style = UType.Title)
                Text(stringResource(if (listening) R.string.listen_done_minutes else R.string.ritual_done_minutes, (elapsed / 60_000).toInt()), style = UType.Sub)
                SittingForm(book, ritual.fromPage, if (book.audio) heard else ritual.page, data.species == Species.SLIME, stringResource(R.string.feed), timed = true, listen = ritual.listen && !book.audio) { input ->
                    if (closing) return@SittingForm
                    closing = true
                    container.scope.launch {
                        val end = System.currentTimeMillis()
                        val r = Library.sit(
                            container.db.dao(), book, ritual.fromPage, input.toPage, ritual.start, end, timed = true,
                            stillSec = (ritual.stillMs / 1000).toInt(), listened = listening, item = input.item, finish = input.finish,
                            guarded = s.guardedDays,
                        )
                        container.settings.update { it.copy(ritual = null) }
                        container.changed()
                        withContext(Dispatchers.Main) {
                            lastFeast.value = r
                            go.feast()
                        }
                    }
                }
                SoftButton(stringResource(if (listening) R.string.listen_continue else R.string.ritual_continue), { finishing = false }, Modifier.fillMaxWidth().padding(top = 8.dp), color = U.c.muted, soft = U.c.surface2)
            }
            return@Column
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A book on paper can be listened to this time, and back again.
            if (!book.audio) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Chip(stringResource(R.string.mode_read), !ritual.listen, { update { it.copy(listen = false) } })
                Chip(stringResource(R.string.mode_listen), ritual.listen, { update { it.copy(listen = true) } })
            }
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                if (listening) Gramophone(Modifier.align(Alignment.Center).width(260.dp).fillMaxHeight())
                else Candle(left, Modifier.align(Alignment.Center).width(150.dp).fillMaxHeight())
                data.look(sleeping = down)?.let { look ->
                    FamiliarArt(look, Modifier.align(Alignment.BottomEnd).size(120.dp), fps = if (down) 2f else 5f)
                }
            }
            val min = elapsed / 60_000
            val sec = (elapsed / 1000) % 60
            Text("%d:%02d".format(min, sec), style = UType.Timer)
            when {
                ritual.listen && !book.audio -> {
                    Text(stringResource(R.string.listen_text), style = UType.Sub.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    Spacer(Modifier.height(10.dp))
                    Text("+" + app.familiar.ui.amount((elapsed / 60_000).toInt(), true), style = UType.Strong.copy(color = U.c.mana))
                }
                book.audio -> {
                    Text(stringResource(R.string.audio_playing), style = UType.Muted)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.minute_at, app.familiar.ui.hm(heard)), style = UType.Title)
                    Text("+" + app.familiar.ui.amount(heard - ritual.fromPage, true), style = UType.Strong.copy(color = U.c.mana))
                }
                else -> {
                    Text(
                        if (left > 0f) stringResource(R.string.candle_left, ((s.candleMin * 60_000 - elapsed) / 60_000 + 1).toInt()) else stringResource(R.string.candle_out),
                        style = UType.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Turn pages here, or just read and say where you stopped at the end.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.count_here), !s.pagesAtEnd, { container.scope.launch { container.settings.update { it.copy(pagesAtEnd = false) } } })
                        Chip(stringResource(R.string.count_end), s.pagesAtEnd, { container.scope.launch { container.settings.update { it.copy(pagesAtEnd = true) } } })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (s.pagesAtEnd) {
                        Text(stringResource(R.string.count_end_text), style = UType.Sub.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    } else {
                        Text(stringResource(R.string.page_n, ritual.page), style = UType.Title)
                        Text("+${ritual.page - ritual.fromPage}", style = UType.Strong.copy(color = U.c.mana))
                        val cheapest = app.familiar.domain.Item.entries.filter { it.name !in s.owned }.minOfOrNull { it.price }
                        app.familiar.domain.Nudge.next(data.stats, app.familiar.domain.Den.sparks(data.pets, s.spent), cheapest, book, ritual.page - ritual.fromPage)?.let { n ->
                            Text(app.familiar.ui.nudgeText(n, data.pet?.name ?: ""), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoftButton("", { update { r -> r.copy(page = (r.page - 1).coerceAtLeast(r.fromPage)) } }, Modifier.width(56.dp), color = U.c.muted, soft = U.c.surface2, icon = Icons.Rounded.Remove)
                            PrimaryButton(stringResource(R.string.turn_page), {
                                update { r -> r.copy(page = (r.page + 1).coerceAtMost(book.cap)) }
                                if (s.pageSound) Sound.page()
                                if (s.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowForward)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!listening) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ambience.entries.forEach { a ->
                    Chip(
                        stringResource(when (a) { Ambience.OFF -> R.string.amb_off; Ambience.FIRE -> R.string.amb_fire; Ambience.RAIN -> R.string.amb_rain }),
                        s.ambience == a,
                        { container.scope.launch { container.settings.update { it.copy(ambience = a) } } },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Face down is for paper; while listening there's nothing to put down.
            if (!listening) UCard {
                Text(stringResource(if (down) R.string.still_on else R.string.still_title), style = UType.Strong.copy(color = if (down) U.c.leaf else U.c.ink))
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.still_text, data.pet?.name ?: ""), style = UType.Muted)
                if (ritual.stillMs > 60_000) Text(stringResource(R.string.still_minutes, (ritual.stillMs / 60_000).toInt()), style = UType.Small.copy(color = U.c.leaf), modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(
            stringResource(if (listening) R.string.audio_finish else R.string.ritual_finish), { finishing = true },
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).navigationBarsPadding(), color = U.c.ember,
        )
    }
    if (discarding) ConfirmDialog(
        title = stringResource(R.string.ritual_discard_title),
        text = stringResource(R.string.ritual_discard_text),
        confirm = stringResource(R.string.ritual_discard),
        danger = true,
        onConfirm = {
            closing = true
            // Put the candle out first, then leave, so nothing relights it on the way.
            container.scope.launch {
                container.settings.update { it.copy(ritual = null) }
                withContext(Dispatchers.Main) { go.back() }
            }
        },
        onDismiss = { discarding = false },
    )
}

@Composable
private fun Gramophone(modifier: Modifier) {
    val frames = remember { List(4) { Scenes.gramophone(it).bitmap() } }
    val t by rememberTime()
    Canvas(modifier) { pixels(frames[(t * 4).toInt() % frames.size], bottom = true) }
}

@Composable
private fun Candle(left: Float, modifier: Modifier) {
    val q = (left.coerceIn(0f, 1f) * 20).toInt() / 20f
    val lit = left > 0f
    val frames = remember(q, lit) { List(4) { Scenes.candle(q, it, lit).bitmap() } }
    val t by rememberTime()
    Canvas(modifier) { pixels(frames[(t * 6).toInt() % frames.size], bottom = true) }
}
