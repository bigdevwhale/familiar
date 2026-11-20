package app.familiar.ui.ritual

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    // A candle left burning for a book that is gone (deleted, or replaced by a restore) is no candle at all:
    // it must not keep every other book from being lit.
    val ritual = s.ritual?.takeIf { data.book(it.bookId) != null }
    val book = data.book(ritual?.bookId ?: id)
    var finishing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var quoting by remember { mutableStateOf(false) }
    var autoPaused by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(false) }

    fun update(change: (Ritual) -> Ritual) {
        container.scope.launch { container.settings.update { st -> st.copy(ritual = st.ritual?.let(change)) } }
    }

    // Lit once when the screen opens; never again after it was put out here.
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (book != null) container.settings.update { st ->
            val alive = st.ritual?.takeIf { data.book(it.bookId) != null }
            st.copy(ritual = alive ?: Ritual(book.id, System.currentTimeMillis(), book.page, book.page, 0))
        }
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
    DisposableEffect(s.ambience, listening, ritual.paused) {
        Sound.ambience(if (listening || ritual.paused) Ambience.OFF else s.ambience)
        onDispose { Sound.stop() }
    }
    LaunchedEffect(down, s.stillDim) { dim(down && s.stillDim) }
    DisposableEffect(Unit) { onDispose { dim(false) } }
    // Time with the phone face down counts toward the stillness bonus.
    LaunchedEffect(down, ritual.paused) {
        if (!down || ritual.paused) return@LaunchedEffect
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
    // Once the sleep timer has gone off the record stands still: nothing after it is counted.
    val slept = ritual.sleepAt in (ritual.start + 1)..now
    val elapsed = ritual.elapsed(now)
    val left = 1f - elapsed / (s.candleMin * 60_000f)
    // An audiobook counts itself: every minute listened is a minute further.
    val heard = (ritual.fromPage + (elapsed / 60_000).toInt()).coerceAtMost(book.cap)

    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding().imePadding()) {
        TopBar(book.title, null) {
            // How the sitting goes (reading or listening, counting pages, the background sound) lives in one sheet.
            if (!finishing) IconButton(onClick = { tuning = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.ritual_settings), tint = U.c.muted) }
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
                        val end = ritual.endAt(System.currentTimeMillis())
                        // Pauses aren't reading: the sitting starts that much later (#70).
                        val r = Library.sit(
                            container.db.dao(), book, ritual.fromPage, input.toPage, ritual.start + ritual.pausedTotal(end), end, timed = true,
                            stillSec = (ritual.stillMs / 1000).toInt(), listened = listening, item = input.item, finish = input.finish,
                            guarded = s.guardedDays, ambience = if (listening) "" else s.ambience.name,
                            guest = data.guest != null, companionId = s.companion,
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
        // One screen, nothing to scroll: the candle, the time with its pause, where you are, and the way out.
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The scene sits in the middle of the free room, not at the bottom of it.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().heightIn(max = 340.dp).fillMaxHeight()) {
                    if (listening) Gramophone(Modifier.align(Alignment.Center).fillMaxWidth(0.7f).fillMaxHeight(), playing = !slept)
                    else Candle(left, Modifier.align(Alignment.Center).fillMaxWidth(0.42f).fillMaxHeight())
                    // It dozes with the phone face down, and falls asleep with you when the sleep timer stops the record.
                    val dozing = down || (listening && slept)
                    data.look(sleeping = dozing)?.let { look ->
                        FamiliarArt(look, Modifier.align(Alignment.BottomEnd).size(104.dp), fps = if (dozing) 2f else 5f)
                    }
                }
            }
            // The timer with a big pause beside it: the candle stops while you're away or writing (#70).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%d:%02d".format(elapsed / 60_000, (elapsed / 1000) % 60), style = UType.Timer.copy(color = if (ritual.paused) U.c.muted else UType.Timer.color))
                if (!slept) {
                    Spacer(Modifier.width(14.dp))
                    Box(
                        Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (ritual.paused) U.c.accent else U.c.surface2)
                            .clickable { val t = System.currentTimeMillis(); update { if (it.paused) it.resume(t) else it.pause(t) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (ritual.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            stringResource(if (ritual.paused) R.string.ritual_resume else R.string.ritual_pause),
                            tint = if (ritual.paused) U.c.onAccent else U.c.ink, modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            // One quiet line under the time: paused, face down, or how long the candle has left.
            Text(
                when {
                    ritual.paused -> stringResource(R.string.ritual_paused)
                    down && !listening -> stringResource(R.string.still_on)
                    listening && !book.audio -> stringResource(R.string.listen_text_short)
                    book.audio -> stringResource(R.string.audio_playing)
                    left > 0f -> stringResource(R.string.candle_left, ((s.candleMin * 60_000 - elapsed) / 60_000 + 1).toInt())
                    else -> stringResource(R.string.candle_out)
                },
                style = UType.Muted.copy(color = if (ritual.paused || down) U.c.accent else U.c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                maxLines = 1,
            )
            Spacer(Modifier.height(14.dp))
            when {
                listening -> {
                    Text(
                        if (book.audio) stringResource(R.string.minute_at, app.familiar.ui.hm(heard)) else "+" + app.familiar.ui.amount((elapsed / 60_000).toInt(), true),
                        style = UType.Title,
                    )
                    Spacer(Modifier.height(8.dp))
                    SleepTimer(ritual, now, slept) { at -> update { it.copy(sleepAt = at) } }
                }
                s.pagesAtEnd -> Text(stringResource(R.string.count_end_text), style = UType.Sub.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                // The page, with − and the big «turn the page» on one line.
                else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("", { update { r -> r.copy(page = (r.page - 1).coerceAtLeast(r.fromPage)) } }, Modifier.width(56.dp), color = U.c.muted, soft = U.c.surface2, icon = Icons.Rounded.Remove)
                    Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ritual.page.toString(), style = UType.Title, maxLines = 1)
                        Text(stringResource(R.string.page_plus, ritual.page - ritual.fromPage), style = UType.Small.copy(color = U.c.mana), maxLines = 1)
                    }
                    PrimaryButton(stringResource(R.string.turn_page_short), {
                        update { r -> r.copy(page = (r.page + 1).coerceAtMost(book.cap)) }
                        if (s.pageSound) Sound.page()
                        if (s.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowForward)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // The way out, with a quote and a note of your own beside it; writing pauses the candle while open (#67).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fun write(note: Boolean) {
                if (!ritual.paused) { autoPaused = true; val t = System.currentTimeMillis(); update { it.pause(t) } }
                noting = note; quoting = true
            }
            SoftButton("", { write(false) }, Modifier.width(56.dp), color = U.c.gold, soft = U.c.goldSoft, icon = Icons.Rounded.FormatQuote)
            SoftButton("", { write(true) }, Modifier.width(56.dp), color = U.c.accent, soft = U.c.accentSoft, icon = Icons.Rounded.EditNote)
            PrimaryButton(stringResource(if (listening) R.string.audio_finish else R.string.ritual_finish), { finishing = true }, Modifier.weight(1f), color = U.c.ember)
        }
    }
    if (tuning) RitualSettingsSheet(book.audio, ritual, s, data.pet?.name ?: "", { change -> update(change) }, { tuning = false })
    if (quoting) app.familiar.ui.books.QuoteDialog(book, null, {
        quoting = false
        if (autoPaused) { autoPaused = false; val t = System.currentTimeMillis(); update { it.resume(t) } }
    }, page = if (book.audio || s.pagesAtEnd || listening) 0 else ritual.page, note = noting) { text, page ->
        container.write { dao().insertQuote(app.familiar.data.Quote(bookId = book.id, text = text, page = page, at = System.currentTimeMillis(), note = noting)) }
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

/** [playing] false: the record has stopped (the sleep timer went off). */
@Composable
private fun Gramophone(modifier: Modifier, playing: Boolean = true) {
    val frames = remember { List(4) { Scenes.gramophone(it).bitmap() } }
    val t by rememberTime()
    Canvas(modifier) { pixels(frames[if (playing) (t * 4).toInt() % frames.size else 0], bottom = true) }
}

/** Falling asleep to a book: the record stops by itself after so many minutes, and what plays is paused. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimer(ritual: Ritual, now: Long, slept: Boolean, set: (Long) -> Unit) {
    UCard {
        Text(stringResource(R.string.sleep_timer), style = UType.Strong)
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                slept -> stringResource(R.string.sleep_done, app.familiar.ui.timeShort(ritual.sleepAt))
                ritual.sleepAt > now -> stringResource(R.string.sleep_left, ((ritual.sleepAt - now) / 60_000 + 1).toInt())
                else -> stringResource(R.string.sleep_text)
            },
            style = UType.Muted.copy(color = if (slept) U.c.leaf else U.c.muted),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.off), ritual.sleepAt == 0L, { set(0L) })
            app.familiar.notify.SleepTimer.MINUTES.forEach { m ->
                Chip(stringResource(R.string.sleep_min, m), false, { set(System.currentTimeMillis() + m * 60_000L) })
            }
        }
    }
}

@Composable
private fun Candle(left: Float, modifier: Modifier) {
    val q = (left.coerceIn(0f, 1f) * 20).toInt() / 20f
    val lit = left > 0f
    val frames = remember(q, lit) { List(4) { Scenes.candle(q, it, lit).bitmap() } }
    val t by rememberTime()
    Canvas(modifier) { pixels(frames[(t * 6).toInt() % frames.size], bottom = true) }
}

/** The sitting's settings in one sheet: reading or listening, where pages are counted, the background, and the face-down hint. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RitualSettingsSheet(audio: Boolean, ritual: Ritual, s: app.familiar.data.Settings, pet: String, update: ((Ritual) -> Ritual) -> Unit, onDismiss: () -> Unit) {
    val container = LocalContext.current.container
    fun set(change: (app.familiar.data.Settings) -> app.familiar.data.Settings) = container.scope.launch { container.settings.update(change) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = U.c.bg) {
        Column(Modifier.padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.ritual_settings), style = UType.Title)
            if (!audio) {
                app.familiar.ui.components.SectionTitle(stringResource(R.string.ritual_how))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.mode_read), !ritual.listen, { update { it.copy(listen = false, sleepAt = 0) } })
                    Chip(stringResource(R.string.mode_listen), ritual.listen, { update { it.copy(listen = true) } })
                }
            }
            if (!audio && !ritual.listen) {
                app.familiar.ui.components.SectionTitle(stringResource(R.string.ritual_pages))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.count_here), !s.pagesAtEnd, { set { it.copy(pagesAtEnd = false) } })
                    Chip(stringResource(R.string.count_end), s.pagesAtEnd, { set { it.copy(pagesAtEnd = true) } })
                }
                app.familiar.ui.components.SectionTitle(stringResource(R.string.ritual_sound))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ambience.entries.forEach { a ->
                        Chip(stringResource(when (a) { Ambience.OFF -> R.string.amb_off; Ambience.FIRE -> R.string.amb_fire; Ambience.RAIN -> R.string.amb_rain }), s.ambience == a, { set { it.copy(ambience = a) } })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.still_title), style = UType.Strong)
                Text(stringResource(R.string.still_text, pet), style = UType.Muted)
                if (ritual.stillMs > 60_000) Text(stringResource(R.string.still_minutes, (ritual.stillMs / 60_000).toInt()), style = UType.Small.copy(color = U.c.leaf), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
