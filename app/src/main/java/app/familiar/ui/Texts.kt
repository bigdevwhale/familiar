package app.familiar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.familiar.R
import app.familiar.data.Shelf
import app.familiar.domain.Form
import app.familiar.domain.Genre
import app.familiar.domain.Nudge
import app.familiar.domain.OwnGenre
import app.familiar.domain.Season
import app.familiar.domain.Trait
import app.familiar.domain.Trinket
import app.familiar.domain.Part
import app.familiar.domain.Quest
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.domain.Stats
import app.familiar.domain.Unlocks
import app.familiar.ui.pix.Look
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun locale(): Locale = LocalConfiguration.current.locales[0]

fun genreColor(g: Genre): Color = Color(g.argb)

/** The colour a book's genre shows in: its own genre's, or the built-in one's. */
fun bookColor(b: app.familiar.data.Book): Color = Color(OwnGenre.of(b)?.argb ?: Genre.of(b.genre).argb)

/** "Fantasy · Dark fantasy" for a subgenre, "Manga" for an own genre standing by itself, "Fantasy" otherwise. */
@Composable
fun bookGenre(b: app.familiar.data.Book): String {
    val own = OwnGenre.of(b) ?: return genreName(Genre.of(b.genre))
    return if (own.standalone) own.name else genreName(own.parent) + " · " + own.name
}

@Composable
fun genreName(g: Genre): String = stringResource(genreRes(g))

fun genreRes(g: Genre): Int = (
    when (g) {
        Genre.FANTASY -> R.string.g_fantasy
        Genre.SCIFI -> R.string.g_scifi
        Genre.MYSTERY -> R.string.g_mystery
        Genre.HORROR -> R.string.g_horror
        Genre.ROMANCE -> R.string.g_romance
        Genre.ADVENTURE -> R.string.g_adventure
        Genre.CLASSIC -> R.string.g_classic
        Genre.HISTORY -> R.string.g_history
        Genre.NONFICTION -> R.string.g_nonfiction
        Genre.POETRY -> R.string.g_poetry
        Genre.OTHER -> R.string.g_other
    }
)

@Composable
fun speciesName(sp: Species): String = stringResource(speciesRes(sp))

fun speciesRes(sp: Species): Int = (
    when (sp) {
        Species.MIMIC -> R.string.sp_mimic
        Species.KITSUNE -> R.string.sp_kitsune
        Species.SLIME -> R.string.sp_slime
        Species.DRAGON -> R.string.sp_dragon
        Species.PHOENIX -> R.string.sp_phoenix
        Species.GRIFFIN -> R.string.sp_griffin
        Species.OWLBEAR -> R.string.sp_owlbear
        Species.MOTH -> R.string.sp_moth
        Species.TANUKI -> R.string.sp_tanuki
        Species.SPROUT -> R.string.sp_sprout
        Species.GHOST -> R.string.sp_ghost
        Species.WYRM -> R.string.sp_wyrm
        Species.CHIMERA -> R.string.sp_chimera
        Species.MAGPIE -> R.string.sp_magpie
    }
)

@Composable
fun speciesLore(sp: Species): String = stringResource(
    when (sp) {
        Species.MIMIC -> R.string.lore_mimic
        Species.KITSUNE -> R.string.lore_kitsune
        Species.SLIME -> R.string.lore_slime
        Species.DRAGON -> R.string.lore_dragon
        Species.PHOENIX -> R.string.lore_phoenix
        Species.GRIFFIN -> R.string.lore_griffin
        Species.OWLBEAR -> R.string.lore_owlbear
        Species.MOTH -> R.string.lore_moth
        Species.TANUKI -> R.string.lore_tanuki
        Species.SPROUT -> R.string.lore_sprout
        Species.GHOST -> R.string.lore_ghost
        Species.WYRM -> R.string.lore_wyrm
        Species.CHIMERA -> R.string.lore_chimera
        Species.MAGPIE -> R.string.lore_magpie
    }
)

/** What it loves to eat: its mana bonus. */
@Composable
fun speciesLoves(sp: Species): String = stringResource(
    when (sp) {
        Species.MIMIC -> R.string.loves_mimic
        Species.KITSUNE -> R.string.loves_kitsune
        Species.SLIME -> R.string.loves_slime
        Species.DRAGON -> R.string.loves_dragon
        Species.PHOENIX -> R.string.loves_phoenix
        Species.GRIFFIN -> R.string.loves_griffin
        Species.OWLBEAR -> R.string.loves_owlbear
        Species.MOTH -> R.string.loves_moth
        Species.TANUKI -> R.string.loves_tanuki
        Species.SPROUT -> R.string.loves_sprout
        Species.GHOST -> R.string.loves_ghost
        Species.WYRM -> R.string.loves_wyrm
        Species.CHIMERA -> R.string.loves_chimera
        Species.MAGPIE -> R.string.loves_magpie
    }
)

/** How it's found. */
@Composable
fun speciesHow(sp: Species): String = when (sp) {
    Species.DRAGON -> stringResource(R.string.how_dragon, Unlocks.DRAGON_PAGES)
    Species.MOTH -> pluralStringResource(R.plurals.how_moth, Unlocks.MOTH_NIGHTS, Unlocks.MOTH_NIGHTS)
    Species.PHOENIX -> stringResource(R.string.how_phoenix)
    Species.GRIFFIN -> pluralStringResource(R.plurals.how_griffin, Unlocks.GRIFFIN_STREAK, Unlocks.GRIFFIN_STREAK)
    Species.OWLBEAR -> stringResource(R.string.how_owlbear)
    Species.TANUKI -> pluralStringResource(R.plurals.how_tanuki, Unlocks.TANUKI_LISTENS, Unlocks.TANUKI_LISTENS)
    Species.SPROUT -> pluralStringResource(R.plurals.how_sprout, Unlocks.SPROUT_MORNINGS, Unlocks.SPROUT_MORNINGS)
    Species.GHOST -> stringResource(R.string.how_ghost)
    Species.WYRM -> pluralStringResource(R.plurals.how_wyrm, Unlocks.WYRM_SITS, Unlocks.WYRM_SITS, Unlocks.WYRM_MINUTES)
    Species.CHIMERA -> pluralStringResource(R.plurals.how_chimera, Unlocks.CHIMERA_GENRES, Unlocks.CHIMERA_GENRES)
    Species.MAGPIE -> pluralStringResource(R.plurals.how_magpie, Unlocks.MAGPIE_SUBS, Unlocks.MAGPIE_SUBS)
    else -> pluralStringResource(R.plurals.how_starter, Unlocks.STARTERS_BOOKS, Unlocks.STARTERS_BOOKS)
}

@Composable
fun itemName(i: app.familiar.domain.Item): String = stringResource(
    when (i) {
        app.familiar.domain.Item.WINDOW_MOON -> R.string.it_window_moon
        app.familiar.domain.Item.WINDOW_STAINED -> R.string.it_window_stained
        app.familiar.domain.Item.BOOKSHELF -> R.string.it_bookshelf
        app.familiar.domain.Item.PORTRAIT -> R.string.it_portrait
        app.familiar.domain.Item.BANNER -> R.string.it_banner
        app.familiar.domain.Item.GARLAND -> R.string.it_garland
        app.familiar.domain.Item.ARMCHAIR -> R.string.it_armchair
        app.familiar.domain.Item.CAULDRON -> R.string.it_cauldron
        app.familiar.domain.Item.TELESCOPE -> R.string.it_telescope
        app.familiar.domain.Item.MANDRAKE -> R.string.it_mandrake
        app.familiar.domain.Item.TREASURE -> R.string.it_treasure
        app.familiar.domain.Item.GLOBE -> R.string.it_globe
        app.familiar.domain.Item.RUG_RED -> R.string.it_rug_red
        app.familiar.domain.Item.RUG_STARS -> R.string.it_rug_stars
        app.familiar.domain.Item.CANDLES -> R.string.it_candles
        app.familiar.domain.Item.LANTERN -> R.string.it_lantern
    }
)

@Composable
fun traitName(t: Trait): String = stringResource(
    when (t) {
        Trait.SLEEPY -> R.string.tr_sleepy
        Trait.GLUTTON -> R.string.tr_glutton
        Trait.CURIOUS -> R.string.tr_curious
        Trait.GRUMPY -> R.string.tr_grumpy
        Trait.PLAYFUL -> R.string.tr_playful
        Trait.BRAVE -> R.string.tr_brave
    }
)

/** What the trait does, in a line. */
@Composable
fun traitText(t: Trait): String = stringResource(
    when (t) {
        Trait.SLEEPY -> R.string.trt_sleepy
        Trait.GLUTTON -> R.string.trt_glutton
        Trait.CURIOUS -> R.string.trt_curious
        Trait.GRUMPY -> R.string.trt_grumpy
        Trait.PLAYFUL -> R.string.trt_playful
        Trait.BRAVE -> R.string.trt_brave
    }
)

@Composable
fun formName(f: Form): String = stringResource(formRes(f))

fun formRes(f: Form): Int = (
    when (f) {
        Form.NONE -> R.string.form_none
        Form.ASTRAL -> R.string.form_astral
        Form.SHADOW -> R.string.form_shadow
        Form.BLOOM -> R.string.form_bloom
        Form.SAGE -> R.string.form_sage
        Form.WILD -> R.string.form_wild
    }
)

/** A form's colour, lightened so it reads on dark cards too. */
fun formColor(f: Form): Color = androidx.compose.ui.graphics.lerp(Color(f.argb), Color.White, 0.3f)

/** Which books lead to the form, by genre names. */
@Composable
fun formGenres(f: Form): String = f.genres.filter { it != Genre.OTHER }.map { genreName(it).lowercase(locale()) }.joinToString(", ")

@Composable
fun trinketName(t: Trinket): String = stringResource(
    when (t) {
        Trinket.BOOKMARK -> R.string.tk_bookmark
        Trinket.ACORN -> R.string.tk_acorn
        Trinket.BUTTON -> R.string.tk_button
        Trinket.CANDLE_STUB -> R.string.tk_candle_stub
        Trinket.TEACUP -> R.string.tk_teacup
        Trinket.DICE -> R.string.tk_dice
        Trinket.RIBBON -> R.string.tk_ribbon
        Trinket.SEASHELL -> R.string.tk_seashell
        Trinket.BELL -> R.string.tk_bell
        Trinket.MOON_PEBBLE -> R.string.tk_moon_pebble
        Trinket.DRAGON_SCALE -> R.string.tk_dragon_scale
        Trinket.GEAR -> R.string.tk_gear
        Trinket.MAGNIFIER -> R.string.tk_magnifier
        Trinket.SPIDER -> R.string.tk_spider
        Trinket.LOVE_LETTER -> R.string.tk_love_letter
        Trinket.COMPASS -> R.string.tk_compass
        Trinket.QUILL -> R.string.tk_quill
        Trinket.OLD_COIN -> R.string.tk_old_coin
        Trinket.SPECTACLES -> R.string.tk_spectacles
        Trinket.PRESSED_FLOWER -> R.string.tk_pressed_flower
        Trinket.PUMPKIN -> R.string.tk_pumpkin
        Trinket.SNOW_GLOBE -> R.string.tk_snow_globe
        Trinket.VALENTINE -> R.string.tk_valentine
        Trinket.FIRST_EDITION -> R.string.tk_first_edition
        Trinket.FIREFLY_JAR -> R.string.tk_firefly_jar
    }
)

@Composable
fun trinketText(t: Trinket): String = stringResource(
    when (t) {
        Trinket.BOOKMARK -> R.string.tkt_bookmark
        Trinket.ACORN -> R.string.tkt_acorn
        Trinket.BUTTON -> R.string.tkt_button
        Trinket.CANDLE_STUB -> R.string.tkt_candle_stub
        Trinket.TEACUP -> R.string.tkt_teacup
        Trinket.DICE -> R.string.tkt_dice
        Trinket.RIBBON -> R.string.tkt_ribbon
        Trinket.SEASHELL -> R.string.tkt_seashell
        Trinket.BELL -> R.string.tkt_bell
        Trinket.MOON_PEBBLE -> R.string.tkt_moon_pebble
        Trinket.DRAGON_SCALE -> R.string.tkt_dragon_scale
        Trinket.GEAR -> R.string.tkt_gear
        Trinket.MAGNIFIER -> R.string.tkt_magnifier
        Trinket.SPIDER -> R.string.tkt_spider
        Trinket.LOVE_LETTER -> R.string.tkt_love_letter
        Trinket.COMPASS -> R.string.tkt_compass
        Trinket.QUILL -> R.string.tkt_quill
        Trinket.OLD_COIN -> R.string.tkt_old_coin
        Trinket.SPECTACLES -> R.string.tkt_spectacles
        Trinket.PRESSED_FLOWER -> R.string.tkt_pressed_flower
        Trinket.PUMPKIN -> R.string.tkt_pumpkin
        Trinket.SNOW_GLOBE -> R.string.tkt_snow_globe
        Trinket.VALENTINE -> R.string.tkt_valentine
        Trinket.FIRST_EDITION -> R.string.tkt_first_edition
        Trinket.FIREFLY_JAR -> R.string.tkt_firefly_jar
    }
)

@Composable
fun seasonName(s: Season): String = stringResource(
    when (s) {
        Season.SAMHAIN -> R.string.season_samhain
        Season.WINTER -> R.string.season_winter
        Season.VALENTINE -> R.string.season_valentine
        Season.BOOKDAY -> R.string.season_bookday
        Season.MIDSUMMER -> R.string.season_midsummer
        Season.NONE -> R.string.season_none
    }
)

/** "Only 6 more pages and …" */
@Composable
fun nudgeText(n: Nudge, name: String): String = when (n.goal) {
    Nudge.Goal.FED -> pluralStringResource(R.plurals.nudge_fed, n.left, n.left, name)
    Nudge.Goal.HATCH -> pluralStringResource(R.plurals.nudge_hatch, n.left, n.left)
    Nudge.Goal.STAGE -> pluralStringResource(R.plurals.nudge_stage, n.left, n.left, name)
    Nudge.Goal.BOOK -> pluralStringResource(R.plurals.nudge_book, n.left, n.left)
    Nudge.Goal.ITEM -> pluralStringResource(R.plurals.nudge_item, n.left, n.left)
}

@Composable
fun stageName(s: Stage): String = stringResource(stageRes(s))

fun stageRes(s: Stage): Int = (
    when (s) {
        Stage.EGG -> R.string.st_egg
        Stage.BABY -> R.string.st_baby
        Stage.YOUNG -> R.string.st_young
        Stage.ADULT -> R.string.st_adult
        Stage.ANCIENT -> R.string.st_ancient
    }
)

@Composable
fun shelfName(s: Shelf): String = stringResource(
    when (s) {
        Shelf.READING -> R.string.shelf_reading
        Shelf.WANT -> R.string.shelf_want
        Shelf.DONE -> R.string.shelf_done
        Shelf.PAUSED -> R.string.shelf_paused
    }
)

@Composable
fun partName(p: Part, sp: Species?, audio: Boolean = false): String = when (p) {
    Part.PAGES -> stringResource(if (audio) R.string.part_minutes else R.string.part_pages)
    Part.TIME -> stringResource(R.string.part_time)
    Part.LISTEN -> stringResource(R.string.part_listen)
    Part.RITUAL -> stringResource(R.string.part_ritual)
    Part.STILL -> stringResource(R.string.part_still)
    Part.FAVOURITE -> stringResource(R.string.part_favourite, sp?.let { speciesLoves(it) } ?: "")
    Part.BOOK_DONE -> stringResource(R.string.part_book)
    Part.ITEM -> stringResource(R.string.part_item)
    Part.SNIFF -> stringResource(R.string.part_sniff)
}

@Composable
fun questTitle(q: Quest): String = stringResource(
    when (q) {
        Quest.STREAK -> R.string.q_streak
        Quest.PAGES -> R.string.q_pages
        Quest.TWO_BOOKS -> R.string.q_two_books
        Quest.LONG_BOOK -> R.string.q_long_book
        Quest.NEW_GENRE -> R.string.q_new_genre
        Quest.NIGHTS -> R.string.q_nights
        Quest.HOUR -> R.string.q_hour
        Quest.STILL -> R.string.q_still
    }
)

@Composable
fun questText(q: Quest): String = when (q) {
    Quest.STREAK -> pluralStringResource(R.plurals.qt_streak, q.target, q.target)
    Quest.PAGES -> pluralStringResource(R.plurals.qt_pages, q.target, q.target)
    Quest.TWO_BOOKS -> pluralStringResource(R.plurals.qt_books, q.target, q.target)
    Quest.LONG_BOOK -> stringResource(R.string.qt_long_book)
    Quest.NEW_GENRE -> stringResource(R.string.qt_new_genre)
    Quest.NIGHTS -> pluralStringResource(R.plurals.qt_nights, q.target, q.target)
    Quest.HOUR -> stringResource(R.string.qt_hour)
    Quest.STILL -> pluralStringResource(R.plurals.qt_still, q.target, q.target)
}

@Composable
fun pages(n: Int): String = pluralStringResource(R.plurals.pages, n, n)

/** Minutes as hours and minutes, 1:05. */
fun hm(min: Int): String = "%d:%02d".format(min / 60, min % 60)

/** "Page 12 of 300 · 4%", or for an audiobook "1:05 of 9:30 · 11%". */
@Composable
fun progressLabel(b: app.familiar.data.Book): String =
    when {
        b.pages <= 0 -> if (b.audio) stringResource(R.string.minute_at, hm(b.page)) else stringResource(R.string.page_n, b.page)
        b.audio -> stringResource(R.string.audio_of, hm(b.page), hm(b.pages)) + " · ${app.familiar.data.Book.percentOf(b.pages, b.page)}%"
        else -> stringResource(R.string.page_of, b.page, b.pages) + " · ${app.familiar.data.Book.percentOf(b.pages, b.page)}%"
    }

/** Pages read, or minutes listened. */
@Composable
fun amount(n: Int, audio: Boolean): String = if (audio) pluralStringResource(R.plurals.minutes, n, n) else pages(n)

@Composable
fun days(n: Int): String = pluralStringResource(R.plurals.days, n, n)

@Composable
fun books(n: Int): String = pluralStringResource(R.plurals.books, n, n)

@Composable
fun mana(n: Int): String = stringResource(R.string.mana_n, n)

@Composable
fun dateShort(t: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()).format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))

@Composable
fun dateOf(d: java.time.LocalDate): String =
    // "3 November" within this year, the full date beyond it.
    if (d.year == java.time.LocalDate.now().year) DateTimeFormatter.ofPattern("d MMMM", locale()).format(d)
    else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()).format(d)

@Composable
fun timeShort(t: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale()).format(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()))

/** The look of a familiar from what's been read so far. */
fun lookOf(
    sp: Species, mana: Int, s: Stats, activeOne: Boolean, sleeping: Boolean = false, chew: Boolean = false, shiny: Boolean = false,
    form: Form = Form.NONE, season: Season = Season.NONE, trait: Trait? = null,
): Look {
    val stage = Stage.of(mana)
    return Look(
        species = sp,
        stage = stage,
        stone = if (activeOne) s.stone else 0f,
        // A brave one is never scared, only thrilled.
        mood = if (!activeOne) app.familiar.domain.Mood.NONE else if (trait == Trait.BRAVE && s.mood == app.familiar.domain.Mood.SCARED) app.familiar.domain.Mood.SPARKLE else s.mood,
        tails = Genre.tails.filter { it in s.genresFinished }.map { it.argb.toInt() },
        hoard = s.finished.takeLast(16).map { Genre.of(it.genre).argb.toInt() },
        stars = s.nights,
        items = s.items.take(7),
        sleeping = sleeping,
        chew = chew,
        hatch = if (stage == Stage.EGG) (mana / Stage.BABY.from.toFloat()).coerceIn(0f, 1f) else 0f,
        shiny = shiny && stage != Stage.EGG,
        blooms = s.mornings,
        wisps = s.spooky,
        patches = s.ownFinished.map { it.argb.toInt() },
        form = if (stage >= Stage.ADULT) form else Form.NONE,
        season = season,
    ).let { if ((it.stone * 10).toInt() != 0) it.copy(stone = (it.stone * 10).toInt() / 10f) else it.copy(stone = 0f) }
}
