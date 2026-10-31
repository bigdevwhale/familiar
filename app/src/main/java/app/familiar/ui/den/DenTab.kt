package app.familiar.ui.den

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familiar.R
import app.familiar.container
import app.familiar.content.Lines
import app.familiar.data.Book
import app.familiar.data.Shelf
import app.familiar.domain.Den
import app.familiar.domain.Dreams
import app.familiar.domain.Form
import app.familiar.domain.Genre
import app.familiar.domain.Item
import app.familiar.domain.Nudge
import app.familiar.domain.Season
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.domain.Trait
import app.familiar.domain.Trinket
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.Tab
import app.familiar.ui.components.Bar
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.CelebrateDialog
import app.familiar.ui.components.DenScene
import app.familiar.ui.components.DreamArt
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TrinketArt
import app.familiar.ui.components.UCard
import app.familiar.ui.components.rememberNow
import app.familiar.ui.dateShort
import app.familiar.ui.days
import app.familiar.ui.formName
import app.familiar.ui.locale
import app.familiar.ui.nudgeText
import app.familiar.ui.pix.Room
import app.familiar.ui.seasonName
import app.familiar.ui.speciesName
import app.familiar.ui.stageName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import app.familiar.ui.trinketName
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun DenTab(data: FamiliarData, go: Go, onTab: (Tab) -> Unit) {
    val context = LocalContext.current
    val s = data.stats
    val pet = data.pet
    val sp = data.species
    val trait = data.trait
    val container = context.container
    var dreaming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val h = LocalTime.now().hour
                val hello = stringResource(
                    when (h) { in 5..11 -> R.string.hello_morning; in 12..17 -> R.string.hello_day; in 18..22 -> R.string.hello_evening; else -> R.string.hello_night }
                )
                Text((if (data.settings.name.isBlank()) hello else "$hello, ${data.settings.name}").uppercase(), style = UType.Eyebrow)
                Text(pet?.name ?: stringResource(R.string.app_name), style = UType.Display)
            }
            if (s.streak > 0) Row(
                Modifier.clip(U.RSm).background(U.c.emberSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, stringResource(R.string.streak_line, days(s.streak)), tint = U.c.ember, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.streak.toString(), style = UType.Strong.copy(color = U.c.ember))
            }
            if (pet != null) {
                IconButton(onClick = { go.visit() }) { Icon(Icons.Rounded.QrCode2, stringResource(R.string.visit), tint = U.c.muted) }
                IconButton(onClick = { go.share(app.familiar.ui.share.Cards.Kind.DEN) }) { Icon(Icons.Rounded.Share, stringResource(R.string.share), tint = U.c.muted) }
            }
            IconButton(onClick = { go.settings() }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = U.c.muted) }
        }
        Spacer(Modifier.height(10.dp))
        val now = rememberNow(1000)
        // Moments that pass: purring after a stroke, a sleepy eye after a tap at night.
        var purrUntil by remember { mutableLongStateOf(0L) }
        var wokenUntil by remember { mutableLongStateOf(0L) }
        val hour = LocalTime.now().hour
        val night = hour < 6 || hour >= 20
        // A sleepy one goes to bed at eleven.
        val bedtime = if (trait == Trait.SLEEPY) hour < 6 || hour >= 23 else hour < 6
        val asleep = bedtime && now > wokenUntil && data.settings.ritual == null
        val purring = now < purrUntil
        val look = data.look(sleeping = asleep)?.let { if (purring && !asleep) it.copy(happy = true) else it }
        val placed = data.settings.placed.mapNotNull { Item.of(it) }.toSet()
        val books = s.finished.map { Genre.of(it.genre).argb.toInt() }.takeLast(24)
        val dream = remember(data.readings, data.now / 600_000) { Dreams.of(data.readings, data.books, data.now, ZoneId.systemDefault()) }
            ?.takeIf { data.settings.dreamDay != s.today.toString() && pet != null && s.stage != Stage.EGG }
        Box(Modifier.fillMaxWidth().aspectRatio(Room.W / Room.H.toFloat())) {
            if (look != null) DenScene(
                look, placed, books, night, Modifier.fillMaxSize(),
                season = data.season,
                guest = data.guestLook,
                hops = if (trait == Trait.PLAYFUL) 2 else 1,
                stroke = if (trait == Trait.GRUMPY) 560f else 260f,
                onTap = { if (asleep || now < wokenUntil) wokenUntil = System.currentTimeMillis() + 8_000 },
                onStroke = { purrUntil = System.currentTimeMillis() + 2_500 },
            )
            if (pet != null && sp != null) Row(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                val form = Form.of(pet.form)
                Tag("${if (pet.shiny) "✦ " else ""}${speciesName(sp)} · ${stageName(s.stage)}" + if (form != Form.NONE && s.stage >= Stage.ADULT) " · " + formName(form) else "")
            }
            // A thought bubble in the morning: what it dreamt of after last night's book.
            if (dream != null && !asleep) Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).width(96.dp).height(66.dp).clip(U.RMd).clickable { dreaming = true },
            ) { DreamArt(dream, Modifier.fillMaxSize()) }
        }
        if (pet != null && sp != null) {
            val seed = remember(data.now / 600_000) { (data.now / 600_000).toInt() }
            val l = locale()
            val line = when {
                purring -> Lines.line(l, sp, Lines.State.PURR, pet.name, seed)
                asleep -> Lines.line(l, sp, Lines.State.SLEEP, pet.name, seed)
                now < wokenUntil && bedtime -> Lines.line(l, sp, Lines.State.WOKEN, pet.name, seed)
                else -> speech(data, sp, pet.name, seed)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                line,
                style = UType.Body.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().clip(U.RMd).background(U.c.surface).border(1.dp, U.c.line, U.RMd).padding(horizontal = 14.dp, vertical = 10.dp),
            )
            val sparks = Den.sparks(data.pets, data.settings.spent)
            val cheapest = Item.entries.filter { it.name !in data.settings.owned }.minOfOrNull { it.price }
            Nudge.next(s, sparks, cheapest, data.reading.firstOrNull())?.let { n ->
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(U.RMd).background(U.c.manaSoft).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = U.c.mana, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(nudgeText(n, pet.name), style = UType.Small.copy(color = U.c.ink))
                }
            }
        }
        // A familiar turning to stone matters more than anything else on the page.
        if (pet != null && s.stone > 0f) {
            Spacer(Modifier.height(10.dp))
            Notice(
                stringResource(if (sp == Species.PHOENIX) R.string.ash_title else R.string.stone_title),
                stringResource(if (sp == Species.PHOENIX) R.string.ash_text else R.string.stone_text, pet.name),
                U.c.accent,
            )
        }
        val book = data.reading.firstOrNull()
        val wanted = data.books.any { it.shelfOf == Shelf.WANT }
        Spacer(Modifier.height(10.dp))
        if (book != null) {
            UCard(onClick = { go.book(book.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(book, Modifier.width(54.dp).height(78.dp), small = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.now_reading).uppercase(), style = UType.Eyebrow)
                        Text(book.title, style = UType.Strong, maxLines = 2)
                        if (book.author.isNotBlank()) Text(book.author, style = UType.Muted, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        if (book.pages > 0) Bar(book.fraction, color = U.c.accent, track = U.c.accentSoft)
                        Spacer(Modifier.height(4.dp))
                        Text(app.familiar.ui.progressLabel(book), style = UType.Small)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(stringResource(R.string.ritual_start), { go.ritual(book.id) }, Modifier.weight(1.5f), icon = Icons.Rounded.LocalFireDepartment)
                    SoftButton(stringResource(R.string.log_short), { go.log(book.id) }, Modifier.weight(1f).height(54.dp), icon = Icons.Rounded.Edit)
                }
            }
        } else {
            UCard {
                Text(stringResource(R.string.no_book_title), style = UType.Strong)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.no_book_text), style = UType.Muted)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.book_add), { go.editBook() }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                if (wanted) {
                    Spacer(Modifier.height(8.dp))
                    SoftButton(stringResource(R.string.lottery_short), { go.lottery() }, Modifier.fillMaxWidth(), color = U.c.gold, soft = U.c.goldSoft, icon = Icons.Rounded.Casino)
                    Spacer(Modifier.height(8.dp))
                    SoftButton(stringResource(R.string.from_shelf), { onTab(Tab.SHELF) }, Modifier.fillMaxWidth())
                }
            }
        }
        if (pet != null && sp != null) {
            val sparks = Den.sparks(data.pets, data.settings.spent)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(
                    stringResource(R.string.furnish_short, sparks), { go.furnish() },
                    Modifier.weight(1f), color = U.c.gold, soft = U.c.goldSoft, icon = Icons.Rounded.Chair,
                )
                SoftButton(
                    stringResource(R.string.casket_short, data.owned.size, Trinket.entries.size), { go.casket() },
                    Modifier.weight(1f), icon = Icons.Rounded.Inventory2,
                )
            }
        }
        if (data.settings.burnedNews.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val days = data.settings.burnedNews.sorted().mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                .map { dateShort(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
            Notice(
                stringResource(R.string.guard_burned_title), stringResource(R.string.guard_burned_text, days.joinToString(", ")), U.c.ember,
                onClose = { container.scope.launch { container.settings.update { it.copy(burnedNews = emptySet()) } } },
            )
        }
        data.guest?.let { g -> GuestCard(data, g) }
        if (data.season != Season.NONE) data.season.trinket?.let { t ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(U.RMd).background(U.c.goldSoft).clickable { go.casket() }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrinketArt(t, Modifier.size(32.dp), dim = t !in data.owned)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.season_title, seasonName(data.season)), style = UType.Small.copy(color = U.c.gold))
                    Text(stringResource(R.string.season_text, trinketName(t).lowercase(locale())), style = UType.Small.copy(color = U.c.ink))
                }
            }
        }
        if (pet != null) {
            Spacer(Modifier.height(12.dp))
            UCard {
                val next = s.stage.next
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = U.c.mana, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mana_title), style = UType.Strong, modifier = Modifier.weight(1f))
                    Text(if (next == null) stringResource(R.string.mana_n, pet.mana) else stringResource(R.string.mana_of, pet.mana, next.from), style = UType.Muted)
                }
                Spacer(Modifier.height(8.dp))
                Bar(Stage.progress(pet.mana), color = U.c.mana, track = U.c.manaSoft)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (next == null) stringResource(R.string.mana_max) else if (s.stage == Stage.EGG) stringResource(R.string.mana_to_hatch, next.from - pet.mana) else stringResource(R.string.mana_to_next, stageName(next), next.from - pet.mana),
                    style = UType.Small.copy(color = U.c.muted),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fed_title), style = UType.Strong, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.fed_of, s.todayPages, data.settings.dailyGoal), style = UType.Muted)
                }
                Spacer(Modifier.height(8.dp))
                Bar(s.fed, color = U.c.ember, track = U.c.line)
                // Guardian candles waiting to keep the streak.
                if (data.settings.candles > 0) Text(
                    stringResource(R.string.guard_n, data.settings.candles), style = UType.Small.copy(color = U.c.gold),
                    modifier = Modifier.padding(top = 6.dp).clip(U.RSm).clickable { go.furnish() }.padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        UCard(onClick = { go.bestiary() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bestiary), style = UType.Strong)
                    val eggs = s.unlocked.count { u -> data.pets.none { it.species == u.name } }
                    Text(
                        if (eggs > 0) stringResource(R.string.bestiary_eggs, eggs) else stringResource(R.string.bestiary_count, data.pets.size, Species.entries.size),
                        style = UType.Muted.copy(color = if (eggs > 0) U.c.gold else U.c.muted),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    data.pets.take(4).forEach { p -> FamiliarArt(data.lookOfPet(p), Modifier.size(46.dp), fps = 3f) }
                }
            }
        }
    }
    if (dreaming && pet != null) {
        val g = Dreams.of(data.readings, data.books, System.currentTimeMillis(), ZoneId.systemDefault()) ?: Genre.OTHER
        CelebrateDialog(
            color = Color(g.argb),
            eyebrow = stringResource(R.string.dream),
            title = stringResource(R.string.dream_title, pet.name),
            text = dreamText(g, pet.name),
            onDismiss = {
                dreaming = false
                container.scope.launch { container.settings.update { it.copy(dreamDay = data.stats.today.toString(), dreams = it.dreams + g.name) } }
            },
        ) { DreamArt(g, Modifier.fillMaxSize()) }
    }
}

@Composable
fun dreamText(g: Genre, name: String): String = stringResource(
    when (g) {
        Genre.FANTASY -> R.string.dream_fantasy
        Genre.SCIFI -> R.string.dream_scifi
        Genre.MYSTERY -> R.string.dream_mystery
        Genre.HORROR -> R.string.dream_horror
        Genre.ROMANCE -> R.string.dream_romance
        Genre.ADVENTURE -> R.string.dream_adventure
        Genre.CLASSIC -> R.string.dream_classic
        Genre.HISTORY -> R.string.dream_history
        Genre.NONFICTION -> R.string.dream_nonfiction
        Genre.POETRY -> R.string.dream_poetry
        Genre.OTHER -> R.string.dream_other
    }, name,
)

/** A friend's familiar visiting for a day, with the book it recommends. */
@Composable
private fun GuestCard(data: FamiliarData, g: app.familiar.domain.Visit) {
    val container = LocalContext.current.container
    Spacer(Modifier.height(12.dp))
    UCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            data.guestLook?.let { FamiliarArt(it, Modifier.size(84.dp), fps = 4f) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.guest_title, g.name), style = UType.Strong)
                Text(
                    stringResource(R.string.guest_from, g.reader.ifBlank { stringResource(R.string.guest_reader) }) + " · " + stringResource(R.string.guest_hours, ((data.settings.guestUntil - data.now) / 3_600_000 + 1).toInt()),
                    style = UType.Small,
                )
            }
        }
        if (g.title.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.guest_recommends, g.title) + if (g.author.isNotBlank()) " · ${g.author}" else "", style = UType.Body)
            val have = data.books.any { it.title.equals(g.title, ignoreCase = true) }
            Spacer(Modifier.height(8.dp))
            if (have) Text(stringResource(R.string.guest_added), style = UType.Small.copy(color = U.c.leaf))
            else SoftButton(stringResource(R.string.guest_add), {
                container.write { dao().insertBook(Book(title = g.title, author = g.author, pages = 0, genre = g.genre.name, shelf = Shelf.WANT.name, added = System.currentTimeMillis())) }
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
        TextButton(onClick = { container.scope.launch { container.settings.update { it.copy(guest = "", guestUntil = 0) } } }) {
            Text(stringResource(R.string.guest_bye), color = U.c.muted, style = UType.Button)
        }
    }
}

/** A short message on a tinted strip, with a cross to put it away when [onClose] is given. */
@Composable
private fun Notice(title: String, text: String, color: Color, onClose: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(U.RMd).background(color.copy(alpha = 0.14f)).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = if (onClose != null) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Small.copy(color = color))
            Text(text, style = UType.Small.copy(color = U.c.ink))
        }
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.nice), tint = U.c.muted, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun Tag(text: String) {
    Text(text, style = UType.Small.copy(color = U.c.ink), modifier = Modifier.clip(U.RSm).background(U.c.surface.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 5.dp))
}

/** What the familiar is doing, in a line. */
@Composable
private fun speech(data: FamiliarData, sp: Species, name: String, seed: Int): String {
    val l = locale()
    val s = data.stats
    val state = when {
        s.stage == Stage.EGG -> Lines.State.EGG
        s.stone > 0.3f && sp == Species.PHOENIX -> Lines.State.ASH
        s.stone > 0.3f -> Lines.State.STONE
        s.fed >= 1f -> Lines.State.FED
        else -> Lines.State.HUNGRY
    }
    if (state == Lines.State.FED || state == Lines.State.HUNGRY) {
        // Now and then it reacts to the last book, the season or its own character.
        when (Math.floorMod(seed, 4)) {
            0 -> Lines.mood(l, s.mood, name)?.let { return it }
            1 -> data.trait?.let { return Lines.trait(l, it, name, seed / 4) }
            3 -> Lines.season(l, data.season, name)?.let { return it }
        }
    }
    return Lines.line(l, sp, state, name, seed)
}
