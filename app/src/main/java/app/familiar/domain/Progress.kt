package app.familiar.domain

import app.familiar.data.Book
import app.familiar.data.Pet
import app.familiar.data.Quote
import app.familiar.data.Reading
import app.familiar.data.Shelf
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random

/** A sitting started late in the evening or deep in the night. */
fun isNight(start: Long, zone: ZoneId): Boolean {
    val h = Instant.ofEpochMilli(start).atZone(zone).hour
    return h >= 22 || h < 5
}

/** A sitting started early in the morning, before ten. */
fun isMorning(start: Long, zone: ZoneId): Boolean = Instant.ofEpochMilli(start).atZone(zone).hour in 5..9

/** Everything derived from the shelf, the sittings and the den at one moment. */
class Stats(
    val books: List<Book>,
    val readings: List<Reading>,
    val pets: List<Pet>,
    val now: Long,
    val zone: ZoneId,
    val dailyGoal: Int,
    /** Quiet days a guardian candle burned for: they keep the chain like days of reading. */
    val guarded: Set<LocalDate> = emptySet(),
    /** Lines copied out of books: the raven's feathers. */
    val quotes: List<Quote> = emptyList(),
    /** Guests welcomed to the den so far: the owlet's stamps. */
    val visits: Int = 0,
) {
    val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    private fun day(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    val readDays: Set<LocalDate> = readings.filter { it.counts }.map { day(it.end) }.toSet()
    val todayPages: Int = readings.filter { day(it.end) == today }.sumOf { it.amount }
    val fed: Float = if (dailyGoal <= 0) 1f else (todayPages.toFloat() / dailyGoal).coerceIn(0f, 1f)

    /** Days that keep the chain: read, or kept by a guardian candle. */
    private val chain: Set<LocalDate> = readDays + guarded

    /** Days in a row with reading, counting today or (if not yet) up to yesterday. */
    val streak: Int = run {
        var d = if (today in chain) today else today.minusDays(1)
        var n = 0
        while (d in chain) { n++; d = d.minusDays(1) }
        n
    }
    val bestStreak: Int = longestRun(chain)

    /** The last sitting, or the end of a day a candle burned for, whichever is later. */
    val lastRead: Long? = listOfNotNull(
        readings.filter { it.counts }.maxOfOrNull { it.end },
        guarded.maxOrNull()?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1),
    ).maxOrNull()

    /**
     * The spell of forgetting: nothing for two days and the familiar starts turning to stone,
     * a statue after a week. A new familiar (no sittings yet) is spared.
     */
    val stone: Float = lastRead?.let { ((now - it) / 3_600_000f - 48f) / 120f }?.coerceIn(0f, 1f) ?: 0f

    val finished: List<Book> = books.filter { it.shelfOf == Shelf.DONE }.sortedBy { it.finished ?: it.added }
    val genresFinished: Set<Genre> = finished.map { Genre.of(it.genre) }.toSet()
    val tails: Int = genresFinished.count { it != Genre.OTHER }.coerceIn(1, 9)
    val nights: Int = readings.count { it.counts && isNight(it.start, zone) }
    val mornings: Int = readings.count { it.counts && isMorning(it.start, zone) }
    val listens: Int = readings.count { it.counts && it.listened }
    val longSits: Int = readings.count { it.timed && it.minutes >= Unlocks.WYRM_MINUTES }
    /** Horror and mystery books finished: the ghost's lanterns. */
    val spooky: Int = finished.count { it.genre == Genre.HORROR.name || it.genre == Genre.MYSTERY.name }
    /** Own genres and subgenres with a book finished in them: the chimera's patches. */
    val ownFinished: List<OwnGenre> = finished.mapNotNull { OwnGenre.of(it) }.distinct()
    /** The most subgenres finished inside one built-in genre: the magpie's nest. */
    val deepest: Int = ownFinished.filter { !it.standalone }.groupBy { it.parent }.maxOfOrNull { it.value.size } ?: 0
    /** The most volumes finished in one cycle: the hydra's heads. */
    val volumes: Int = finished.filter { it.series.isNotBlank() }.groupBy { it.series.trim().lowercase() }.maxOfOrNull { it.value.size } ?: 0
    /** Books read again and finished again: the cat's tales. */
    val rereads: Int = finished.sumOf { it.reread }
    /** Sittings with the phone lying face down for ten minutes or more: the snail's quiet. */
    val quiet: Int = readings.count { it.stillSec >= Unlocks.SNAIL_SECONDS }
    /** Sittings by the sound of the fire: the salamander's embers. */
    val fires: Int = readings.count { it.counts && it.ambience == "FIRE" }
    val scifi: Int = finished.count { it.genre == Genre.SCIFI.name }
    val classics: Int = finished.count { it.genre == Genre.CLASSIC.name }
    /** Poems and little books finished: the pixie's petals. */
    val little: Int = finished.count { Unlocks.little(it.genre, it.pages, it.audio) }
    val totalPages: Int = readings.sumOf { it.pages }
    val totalMinutes: Int = readings.filter { it.timed }.sumOf { it.minutes }
    val items: List<String> = readings.filter { it.item.isNotBlank() }.sortedByDescending { it.end }.map { it.item }

    /** A sitting after five or more quiet days: the phoenix rises from that. */
    val cameBack: Boolean = readings.filter { it.counts }.sortedBy { it.start }.zipWithNext().any { (a, b) -> b.start - a.end >= 5 * 86_400_000L }

    val active: Pet? = pets.firstOrNull { it.active } ?: pets.firstOrNull()
    val activeSpecies: Species? = active?.let { Species.of(it.species) }
    val stage: Stage = Stage.of(active?.mana ?: 0)

    /** The last book read colours the mood for twelve hours. */
    val mood: Mood = readings.maxByOrNull { it.end }?.takeIf { now - it.end < 12 * 3_600_000L }
        ?.let { r -> books.firstOrNull { it.id == r.bookId } }?.let { Genre.of(it.genre).mood } ?: Mood.NONE

    val unlocked: Set<Species> = Unlocks.unlocked(this)
}

fun longestRun(days: Set<LocalDate>): Int {
    var best = 0
    for (d in days) {
        if (d.minusDays(1) in days) continue
        var n = 0
        var x = d
        while (x in days) { n++; x = x.plusDays(1) }
        best = maxOf(best, n)
    }
    return best
}

/** How each familiar is found. */
object Unlocks {
    const val DRAGON_PAGES = 400
    const val MOTH_NIGHTS = 5
    const val GRIFFIN_STREAK = 7
    const val STARTERS_BOOKS = 2
    const val TANUKI_LISTENS = 5
    const val SPROUT_MORNINGS = 5
    const val WYRM_SITS = 3
    const val WYRM_MINUTES = 45
    const val CHIMERA_GENRES = 3
    const val MAGPIE_SUBS = 2
    const val HYDRA_VOLUMES = 3
    const val SNAIL_SITS = 5
    const val SNAIL_SECONDS = 600
    const val SALAMANDER_FIRES = 10
    const val AUTOMATON_BOOKS = 2
    const val PIXIE_BOOKS = 3
    const val PIXIE_PAGES = 150
    const val RAVEN_QUOTES = 5
    const val SPHINX_BOOKS = 2

    /** A poem, or a book on paper or screen shorter than [PIXIE_PAGES] pages. */
    fun little(genre: String, pages: Int, audio: Boolean): Boolean = genre == Genre.POETRY.name || (!audio && pages in 1 until PIXIE_PAGES)

    fun unlocked(s: Stats): Set<Species> {
        val out = s.pets.map { Species.of(it.species) }.toMutableSet()
        if (s.finished.size >= STARTERS_BOOKS) out += Species.entries.filter { it.starter }
        if (s.finished.any { it.pages >= DRAGON_PAGES }) out += Species.DRAGON
        if (s.nights >= MOTH_NIGHTS) out += Species.MOTH
        if (s.cameBack) out += Species.PHOENIX
        if (s.bestStreak >= GRIFFIN_STREAK) out += Species.GRIFFIN
        if (s.finished.any { it.genre == Genre.NONFICTION.name || it.genre == Genre.HISTORY.name }) out += Species.OWLBEAR
        if (s.listens >= TANUKI_LISTENS) out += Species.TANUKI
        if (s.mornings >= SPROUT_MORNINGS) out += Species.SPROUT
        if (s.spooky >= 1) out += Species.GHOST
        if (s.longSits >= WYRM_SITS) out += Species.WYRM
        if (s.ownFinished.size >= CHIMERA_GENRES) out += Species.CHIMERA
        if (s.deepest >= MAGPIE_SUBS) out += Species.MAGPIE
        if (s.volumes >= HYDRA_VOLUMES) out += Species.HYDRA
        if (s.rereads >= 1) out += Species.BAYUN
        if (s.quiet >= SNAIL_SITS) out += Species.SNAIL
        if (s.fires >= SALAMANDER_FIRES) out += Species.SALAMANDER
        if (s.scifi >= AUTOMATON_BOOKS) out += Species.AUTOMATON
        if (s.little >= PIXIE_BOOKS) out += Species.PIXIE
        if (s.quotes.size >= RAVEN_QUOTES) out += Species.RAVEN
        if (s.classics >= SPHINX_BOOKS) out += Species.SPHINX
        if (s.visits >= 1) out += Species.OWLET
        return out
    }

    /** How far along the way to [sp]: current and target. */
    fun progress(sp: Species, s: Stats): Pair<Int, Int> = when (sp) {
        Species.DRAGON -> (s.books.filter { it.shelfOf != Shelf.WANT }.maxOfOrNull { it.page } ?: 0).coerceAtMost(DRAGON_PAGES) to DRAGON_PAGES
        Species.MOTH -> s.nights.coerceAtMost(MOTH_NIGHTS) to MOTH_NIGHTS
        Species.PHOENIX -> (if (s.cameBack) 1 else 0) to 1
        Species.GRIFFIN -> s.bestStreak.coerceAtMost(GRIFFIN_STREAK) to GRIFFIN_STREAK
        Species.OWLBEAR -> s.finished.count { it.genre == Genre.NONFICTION.name || it.genre == Genre.HISTORY.name }.coerceAtMost(1) to 1
        Species.TANUKI -> s.listens.coerceAtMost(TANUKI_LISTENS) to TANUKI_LISTENS
        Species.SPROUT -> s.mornings.coerceAtMost(SPROUT_MORNINGS) to SPROUT_MORNINGS
        Species.GHOST -> s.spooky.coerceAtMost(1) to 1
        Species.WYRM -> s.longSits.coerceAtMost(WYRM_SITS) to WYRM_SITS
        Species.CHIMERA -> s.ownFinished.size.coerceAtMost(CHIMERA_GENRES) to CHIMERA_GENRES
        Species.MAGPIE -> s.deepest.coerceAtMost(MAGPIE_SUBS) to MAGPIE_SUBS
        Species.HYDRA -> s.volumes.coerceAtMost(HYDRA_VOLUMES) to HYDRA_VOLUMES
        Species.BAYUN -> s.rereads.coerceAtMost(1) to 1
        Species.SNAIL -> s.quiet.coerceAtMost(SNAIL_SITS) to SNAIL_SITS
        Species.SALAMANDER -> s.fires.coerceAtMost(SALAMANDER_FIRES) to SALAMANDER_FIRES
        Species.AUTOMATON -> s.scifi.coerceAtMost(AUTOMATON_BOOKS) to AUTOMATON_BOOKS
        Species.PIXIE -> s.little.coerceAtMost(PIXIE_BOOKS) to PIXIE_BOOKS
        Species.RAVEN -> s.quotes.size.coerceAtMost(RAVEN_QUOTES) to RAVEN_QUOTES
        Species.SPHINX -> s.classics.coerceAtMost(SPHINX_BOOKS) to SPHINX_BOOKS
        Species.OWLET -> s.visits.coerceAtMost(1) to 1
        else -> s.finished.size.coerceAtMost(STARTERS_BOOKS) to STARTERS_BOOKS
    }
}

enum class Part { PAGES, TIME, LISTEN, RITUAL, STILL, FAVOURITE, BOOK_DONE, ITEM, SNIFF }

class Feast(val parts: List<Pair<Part, Int>>) {
    val total: Int get() = parts.sumOf { it.second }.coerceAtLeast(0)
    val sniffed: Boolean get() = parts.any { it.first == Part.SNIFF }
}

/** What a sitting is worth to the familiar that eats it. */
object Mana {
    const val BOOK_DONE = 50
    const val MIMIC_FEAST = 100
    const val SLIME_ITEM = 15
    const val QUEST = 100

    /** Faster than this many pages a minute is more than a person reads; the rest counts half. */
    const val MAX_PACE = 2.5f

    /** An e-reader's page is a screen, often half a printed one or less. */
    const val MAX_PACE_EBOOK = 5f

    /** Minutes counted for a ritual without a page, at most. */
    const val MAX_MINUTES = 180

    class Sitting(
        val pages: Int,
        val timed: Boolean,
        val minutes: Int,
        val stillFraction: Float,
        val night: Boolean,
        val genre: Genre,
        /** Genre of the book read before this one, or null. */
        val previousGenre: Genre?,
        val bookPages: Int,
        /** The streak including this sitting. */
        val streak: Int,
        /** Whole days since the sitting before, or 0. */
        val gapDays: Int,
        val finishedBook: Boolean,
        val item: String,
        val listened: Boolean = false,
        /** Started before ten in the morning. */
        val morning: Boolean = false,
        /** The book is in one of the reader's own genres or subgenres. */
        val own: Boolean = false,
        /** Read as an e-book, whose pages are shorter. */
        val ebook: Boolean = false,
        /** The book is a volume of a cycle. */
        val series: Boolean = false,
        /** The book is being read again. */
        val reread: Boolean = false,
        /** Read by the sound of the fire. */
        val fire: Boolean = false,
        /** A line has been copied out of this book. */
        val quoted: Boolean = false,
        /** A friend's familiar is staying in the den. */
        val guest: Boolean = false,
        /** An audiobook, whose length is in minutes. */
        val audio: Boolean = false,
    )

    private fun loves(sp: Species, s: Sitting): Boolean = when (sp) {
        Species.KITSUNE -> s.previousGenre != null && s.previousGenre != s.genre
        Species.DRAGON -> s.bookPages >= Unlocks.DRAGON_PAGES
        Species.PHOENIX -> s.gapDays >= 3
        Species.GRIFFIN -> s.streak >= 3
        Species.OWLBEAR -> s.genre == Genre.NONFICTION || s.genre == Genre.HISTORY
        Species.MOTH -> s.night
        Species.TANUKI -> s.listened
        Species.SPROUT -> s.morning
        Species.GHOST -> s.genre == Genre.HORROR || s.genre == Genre.MYSTERY
        Species.WYRM -> s.timed && s.minutes >= Unlocks.WYRM_MINUTES
        Species.CHIMERA -> s.own
        Species.MAGPIE -> s.previousGenre != null && s.previousGenre == s.genre
        Species.HYDRA -> s.series
        Species.BAYUN -> s.reread
        Species.SNAIL -> s.timed && s.stillFraction >= 0.5f
        Species.SALAMANDER -> s.fire
        Species.AUTOMATON -> s.genre == Genre.SCIFI
        Species.PIXIE -> Unlocks.little(s.genre.name, s.bookPages, s.audio)
        Species.RAVEN -> s.quoted
        Species.SPHINX -> s.genre == Genre.CLASSIC
        Species.OWLET -> s.guest
        Species.MIMIC, Species.SLIME -> false
    }

    fun feast(sp: Species, s: Sitting): Feast {
        val parts = mutableListOf<Pair<Part, Int>>()
        // No page given after a ritual: the familiar eats the minutes by the candle instead.
        if (s.timed && s.pages == 0 && s.minutes > 0) {
            val minutes = s.minutes.coerceAtMost(MAX_MINUTES)
            parts += (if (s.listened) Part.LISTEN else Part.TIME) to minutes
            if (loves(sp, s)) parts += Part.FAVOURITE to (minutes * 0.5f).toInt().coerceAtLeast(1)
            if (s.finishedBook) parts += Part.BOOK_DONE to BOOK_DONE
            if (sp == Species.SLIME && s.item.isNotBlank()) parts += Part.ITEM to SLIME_ITEM
            return Feast(parts)
        }
        var pages = s.pages
        parts += Part.PAGES to s.pages
        if (s.timed) {
            val allowed = (s.minutes * (if (s.ebook) MAX_PACE_EBOOK else MAX_PACE) + 5).toInt()
            if (s.pages > allowed) {
                val cut = (s.pages - allowed) / 2
                parts += Part.SNIFF to -cut
                pages -= cut
            }
            if (pages > 0) parts += Part.RITUAL to (pages * 0.1f).toInt().coerceAtLeast(1)
            val still = (pages * 0.2f * s.stillFraction.coerceIn(0f, 1f)).toInt()
            if (still > 0) parts += Part.STILL to still
        }
        if (loves(sp, s) && pages > 0) parts += Part.FAVOURITE to (pages * if (sp == Species.PHOENIX) 1f else 0.5f).toInt().coerceAtLeast(1)
        if (s.finishedBook) {
            parts += Part.BOOK_DONE to BOOK_DONE
            if (sp == Species.MIMIC) parts += Part.FAVOURITE to MIMIC_FEAST
        }
        if (sp == Species.SLIME && s.item.isNotBlank()) parts += Part.ITEM to SLIME_ITEM
        return Feast(parts)
    }

}

/** Monthly guild errands: three from the board, the same three for everyone in that month. */
enum class Quest(val target: Int) {
    STREAK(5), PAGES(500), TWO_BOOKS(2), LONG_BOOK(1), NEW_GENRE(1), NIGHTS(3), HOUR(1), STILL(5);

    companion object {
        fun of(month: YearMonth): List<Quest> = entries.shuffled(Random(month.year * 12 + month.monthValue)).take(3)
    }
}

class QuestState(val quest: Quest, val progress: Int, val month: YearMonth) {
    val done: Boolean get() = progress >= quest.target
    val key: String get() = "$month:${quest.name}"
}

object Quests {
    fun state(s: Stats, month: YearMonth = YearMonth.from(s.today)): List<QuestState> {
        fun inMonth(t: Long) = YearMonth.from(Instant.ofEpochMilli(t).atZone(s.zone)) == month
        val rs = s.readings.filter { inMonth(it.end) }
        val doneNow = s.finished.filter { it.finished != null && inMonth(it.finished) }
        val monthStart = month.atDay(1).atStartOfDay(s.zone).toInstant().toEpochMilli()
        val oldGenres = s.finished.filter { (it.finished ?: 0) < monthStart }.map { it.genre }.toSet()
        return Quest.of(month).map { q ->
            val p = when (q) {
                Quest.STREAK -> longestRun(rs.filter { it.counts }.map { Instant.ofEpochMilli(it.end).atZone(s.zone).toLocalDate() }.toSet())
                Quest.PAGES -> rs.sumOf { it.amount }
                Quest.TWO_BOOKS -> doneNow.size
                Quest.LONG_BOOK -> doneNow.count { it.pages >= 300 }
                Quest.NEW_GENRE -> doneNow.count { it.genre !in oldGenres && it.genre != Genre.OTHER.name }
                Quest.NIGHTS -> rs.count { it.timed && it.counts && isNight(it.start, s.zone) }
                Quest.HOUR -> rs.count { it.timed && it.minutes >= 60 }
                Quest.STILL -> rs.count { it.stillSec >= 600 }
            }
            QuestState(q, p.coerceAtMost(q.target), month)
        }
    }
}

/** A year of reading, for the saga. */
class Year(private val s: Stats, val year: Int) {
    private fun inYear(t: Long) = Instant.ofEpochMilli(t).atZone(s.zone).year == year
    val books: List<Book> = s.finished.filter { it.finished != null && inYear(it.finished) }
    val pages: Int = s.readings.filter { inYear(it.end) }.sumOf { it.pages }
    val nights: Int = s.readings.count { inYear(it.end) && it.counts && isNight(it.start, s.zone) }
    val genre: Genre? = books.groupBy { Genre.of(it.genre) }.maxByOrNull { it.value.size }?.key
    val longest: Book? = books.maxByOrNull { it.pages }
    val bestStreak: Int = longestRun(s.readings.filter { inYear(it.end) && it.counts }.map { Instant.ofEpochMilli(it.end).atZone(s.zone).toLocalDate() }.toSet())
}

/** A cycle of books, gathered by the name typed on each volume. */
class Series(val name: String, val books: List<Book>) {
    val done: Int = books.count { it.shelfOf == Shelf.DONE }
    /** Every volume on the shelf is read, and there are at least two. */
    val complete: Boolean = books.size >= 2 && done == books.size
    val key: String get() = "series:" + name.lowercase()
    /** The volume to read next: the first one neither read nor put down; null once there's none. */
    val next: Book? = books.firstOrNull { it.shelfOf != Shelf.DONE && it.shelfOf != Shelf.ABANDONED }

    companion object {
        const val REWARD = 200

        fun all(books: List<Book>): List<Series> = books.filter { it.series.isNotBlank() }
            .groupBy { it.series.trim().lowercase() }
            .map { (_, list) -> Series(list.first().series.trim(), list.sortedWith(compareBy({ it.volume ?: Int.MAX_VALUE }, { it.added }))) }
            .sortedByDescending { s -> s.books.maxOf { it.finished ?: it.added } }
    }
}

/** Pages (and audiobook minutes) read on each day of [year]. */
fun daysOf(readings: List<Reading>, zone: ZoneId, year: Int): Map<LocalDate, Int> =
    readings.filter { Instant.ofEpochMilli(it.end).atZone(zone).year == year }
        .groupBy { Instant.ofEpochMilli(it.end).atZone(zone).toLocalDate() }
        .mapValues { (_, l) -> l.sumOf { it.amount } }
