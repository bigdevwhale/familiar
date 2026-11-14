package app.familiar.domain

import app.familiar.data.Shelf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** The pages of the book of achievements. */
enum class FeatGroup { READING, HABITS, SHELF, FAMILIARS, DEN }

/**
 * A measure of reading and the steps along it: each step is an achievement of its own, with its own name
 * ([app.familiar.content.FeatNames]). One medal is drawn per measure (art/badges); its rim tells the step.
 */
enum class Feat(val group: FeatGroup, vararg val steps: Int) {
    BOOKS(FeatGroup.READING, 1, 5, 10, 25, 50, 100, 250, 500),
    PAGES(FeatGroup.READING, 100, 1_000, 5_000, 10_000, 25_000, 50_000, 100_000),
    HOURS(FeatGroup.READING, 1, 10, 50, 100, 500),
    DAY_PAGES(FeatGroup.READING, 50, 100, 200, 300),
    BRICK(FeatGroup.READING, 500, 800, 1_200),
    LITTLE(FeatGroup.READING, 3, 10, 25),
    MONTH_BOOKS(FeatGroup.READING, 3, 5, 10),
    ONE_DAY(FeatGroup.READING, 1, 5),
    LISTEN(FeatGroup.READING, 1, 10, 50, 100),
    REREAD(FeatGroup.READING, 1, 3, 10),

    STREAK(FeatGroup.HABITS, 3, 7, 14, 30, 60, 100, 365),
    DAYS(FeatGroup.HABITS, 10, 50, 100, 365, 1_000),
    NIGHTS(FeatGroup.HABITS, 1, 10, 50, 100),
    MORNINGS(FeatGroup.HABITS, 1, 10, 50, 100),
    MIDNIGHT(FeatGroup.HABITS, 1, 10),
    RITUALS(FeatGroup.HABITS, 1, 10, 50, 100, 500),
    LONG_SIT(FeatGroup.HABITS, 30, 60, 120, 180),
    QUIET(FeatGroup.HABITS, 1, 10, 50),
    FIRES(FeatGroup.HABITS, 1, 10, 50),
    RAIN(FeatGroup.HABITS, 1, 10, 50),
    WEEKENDS(FeatGroup.HABITS, 1, 10, 30),
    FULL_MONTHS(FeatGroup.HABITS, 1, 3),
    COMEBACK(FeatGroup.HABITS, 1),
    CANDLES(FeatGroup.HABITS, 1, 5),
    YEAR_GOAL(FeatGroup.HABITS, 1),

    GENRES(FeatGroup.SHELF, 3, 6, 10),
    FANTASY(FeatGroup.SHELF, 1, 5, 15),
    SCIFI(FeatGroup.SHELF, 1, 5, 15),
    MYSTERY(FeatGroup.SHELF, 1, 5, 15),
    HORROR(FeatGroup.SHELF, 1, 5, 15),
    ROMANCE(FeatGroup.SHELF, 1, 5, 15),
    ADVENTURE(FeatGroup.SHELF, 1, 5, 15),
    CLASSIC(FeatGroup.SHELF, 1, 5, 15),
    HISTORY(FeatGroup.SHELF, 1, 5, 15),
    NONFICTION(FeatGroup.SHELF, 1, 5, 15),
    POETRY(FeatGroup.SHELF, 1, 5, 15),
    SERIES(FeatGroup.SHELF, 1, 3, 10),
    AUTHOR(FeatGroup.SHELF, 3, 5, 10),
    AUTHORS(FeatGroup.SHELF, 10, 50),
    RATED(FeatGroup.SHELF, 10, 50, 100),
    FIVE_STARS(FeatGroup.SHELF, 1, 10, 25),
    QUOTES(FeatGroup.SHELF, 1, 10, 50, 100),
    NOTES(FeatGroup.SHELF, 1, 10),
    WANT(FeatGroup.SHELF, 10, 50),
    ABANDONED(FeatGroup.SHELF, 1, 5),
    FORMATS(FeatGroup.SHELF, 3),
    OWN(FeatGroup.SHELF, 1, 5),
    LIBRARY(FeatGroup.SHELF, 50, 200, 500),

    PETS(FeatGroup.FAMILIARS, 3, 7, 14, 23),
    GROWN(FeatGroup.FAMILIARS, 1, 5, 10),
    ANCIENT(FeatGroup.FAMILIARS, 1, 3, 7),
    FORMS(FeatGroup.FAMILIARS, 1, 3, 5),
    TRAITS(FeatGroup.FAMILIARS, 3, 6),
    MANA(FeatGroup.FAMILIARS, 1_000, 10_000, 50_000, 100_000),
    STARTERS(FeatGroup.FAMILIARS, 3),
    COMPANION(FeatGroup.FAMILIARS, 1),
    GUESTS(FeatGroup.FAMILIARS, 1, 5),
    SHINY(FeatGroup.FAMILIARS, 1),

    FINDS(FeatGroup.DEN, 5, 15, 25),
    SEASONAL(FeatGroup.DEN, 1, 5),
    TREASURE(FeatGroup.DEN, 25, 100),
    DREAMS(FeatGroup.DEN, 1, 6, 11),
    FURNITURE(FeatGroup.DEN, 1, 8, 16, 28),
    QUESTS(FeatGroup.DEN, 1, 10, 30),
    ITEMS(FeatGroup.DEN, 1, 10);

    /** Kept secret until it is earned: luck, not effort. */
    val hidden: Boolean get() = this == SHINY

    /** The built-in genre this measure counts finished books of, or null. */
    val genre: Genre? get() = Genre.entries.firstOrNull { it.name == name && it != Genre.OTHER }
}

/** One step of a [Feat]. */
class Achievement(val feat: Feat, val index: Int) {
    val target: Int = feat.steps[index]

    /** Stays the same if steps are ever added between the others. */
    val id: String = feat.name + ":" + target

    /** 0 bronze, 1 silver, 2 gold, 3 amethyst, 4 starlight: how far along its measure the step is. */
    val rank: Int = when (val n = feat.steps.size) {
        1 -> 2
        2 -> index * 2
        in 3..5 -> index
        else -> Math.round(index * 4f / (n - 1))
    }

    /** Mana for the familiar when it is earned. */
    val reward: Int get() = REWARDS[rank]

    private companion object {
        val REWARDS = intArrayOf(20, 40, 70, 110, 160)
    }
}

/** What the den holds, beyond the shelf and the sittings: the part of [Feats] the settings remember. */
class DenState(
    val furniture: Int = 0,
    val quests: Int = 0,
    val dreams: Int = 0,
    /** Every find in the casket, by its [Trinket] name. */
    val finds: List<String> = emptyList(),
    val guarded: Int = 0,
    val shelves: Int = 0,
    val genres: Int = 0,
    val companion: Boolean = false,
    val yearGoal: Int = 0,
)

object Feats {
    val all: List<Achievement> = Feat.entries.flatMap { f -> f.steps.indices.map { Achievement(f, it) } }

    fun of(group: FeatGroup): List<Achievement> = all.filter { it.feat.group == group }

    /** How far along every measure the reader is. */
    fun values(s: Stats, d: DenState): Map<Feat, Int> {
        fun day(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(s.zone).toLocalDate()
        val counted = s.readings.filter { it.counts }
        val timed = s.readings.filter { it.timed }
        // Only books read here, sitting by sitting: an imported shelf arrives dated the day of the import.
        val readHere = s.readings.map { it.bookId }.toSet()
        val ofBook = counted.groupBy { it.bookId }
        val authors = s.finished.filter { it.author.isNotBlank() }.groupBy { it.author.trim().lowercase() }
        val grown = s.pets.filter { Stage.of(it.mana) >= Stage.ADULT }
        val trinkets = d.finds.mapNotNull { Trinket.of(it) }
        return Feat.entries.associateWith { f ->
            when (f) {
                Feat.BOOKS -> s.finished.size
                Feat.PAGES -> s.totalPages
                Feat.HOURS -> s.totalMinutes / 60
                Feat.DAY_PAGES -> s.readings.groupBy { day(it.end) }.maxOfOrNull { (_, l) -> l.sumOf { it.pages } } ?: 0
                Feat.BRICK -> s.finished.filter { !it.audio }.maxOfOrNull { it.pages } ?: 0
                Feat.LITTLE -> s.little
                Feat.MONTH_BOOKS -> s.finished.filter { it.finished != null && it.id in readHere }
                    .groupBy { YearMonth.from(day(it.finished!!)) }.maxOfOrNull { it.value.size } ?: 0
                Feat.ONE_DAY -> ofBook.count { (_, l) -> l.any { it.finishedBook } && l.sumOf { it.pages } >= 100 && l.map { day(it.end) }.distinct().size == 1 }
                Feat.LISTEN -> s.listens
                Feat.REREAD -> s.rereads

                Feat.STREAK -> s.bestStreak
                Feat.DAYS -> s.readDays.size
                Feat.NIGHTS -> s.nights
                Feat.MORNINGS -> s.mornings
                Feat.MIDNIGHT -> counted.count { Instant.ofEpochMilli(it.start).atZone(s.zone).hour < 4 }
                Feat.RITUALS -> timed.count { it.counts }
                Feat.LONG_SIT -> timed.maxOfOrNull { it.minutes } ?: 0
                Feat.QUIET -> s.quiet
                Feat.FIRES -> s.fires
                Feat.RAIN -> counted.count { it.ambience == "RAIN" }
                Feat.WEEKENDS -> s.readDays.count { it.dayOfWeek == DayOfWeek.SATURDAY && it.plusDays(1) in s.readDays }
                Feat.FULL_MONTHS -> s.readDays.groupBy { YearMonth.from(it) }.count { (m, l) -> l.size == m.lengthOfMonth() }
                Feat.COMEBACK -> if (s.cameBack) 1 else 0
                Feat.CANDLES -> d.guarded
                Feat.YEAR_GOAL -> if (d.yearGoal > 0 && Year(s, s.today.year).books.size >= d.yearGoal) 1 else 0

                Feat.GENRES -> s.genresFinished.count { it != Genre.OTHER }
                Feat.FANTASY, Feat.SCIFI, Feat.MYSTERY, Feat.HORROR, Feat.ROMANCE, Feat.ADVENTURE, Feat.CLASSIC, Feat.HISTORY, Feat.NONFICTION, Feat.POETRY ->
                    s.finished.count { it.genre == f.name }
                Feat.SERIES -> Series.all(s.books).count { it.complete }
                Feat.AUTHOR -> authors.maxOfOrNull { it.value.size } ?: 0
                Feat.AUTHORS -> authors.size
                Feat.RATED -> s.books.count { it.rating != null }
                Feat.FIVE_STARS -> s.books.count { it.rating == 5 }
                Feat.QUOTES -> s.quotes.size
                Feat.NOTES -> s.books.count { it.note.isNotBlank() }
                Feat.WANT -> s.books.count { it.shelfOf == Shelf.WANT }
                Feat.ABANDONED -> s.books.count { it.shelfOf == Shelf.ABANDONED }
                Feat.FORMATS -> listOf(s.finished.any { it.audio }, s.finished.any { it.ebook && !it.audio }, s.finished.any { !it.ebook && !it.audio }).count { it }
                Feat.OWN -> d.shelves + d.genres
                Feat.LIBRARY -> s.books.size

                Feat.PETS -> s.pets.size
                Feat.GROWN -> grown.size
                Feat.ANCIENT -> s.pets.count { Stage.of(it.mana) == Stage.ANCIENT }
                Feat.FORMS -> grown.map { Form.of(it.form) }.filter { it != Form.NONE }.distinct().size
                Feat.TRAITS -> s.pets.map { Trait.of(it.trait, it.joined) }.distinct().size
                Feat.MANA -> s.pets.sumOf { it.mana }
                Feat.STARTERS -> s.pets.map { Species.of(it.species) }.distinct().count { it.starter }
                Feat.COMPANION -> if (d.companion) 1 else 0
                Feat.GUESTS -> s.visits
                Feat.SHINY -> if (s.pets.any { it.shiny }) 1 else 0

                Feat.FINDS -> trinkets.distinct().size
                Feat.SEASONAL -> trinkets.filter { it.season != null }.distinct().size
                Feat.TREASURE -> trinkets.size
                Feat.DREAMS -> d.dreams
                Feat.FURNITURE -> d.furniture
                Feat.QUESTS -> d.quests
                Feat.ITEMS -> s.items.size
            }
        }
    }

    /** The achievements whose step is reached, by id. */
    fun met(values: Map<Feat, Int>): Set<String> = all.filter { (values[it.feat] ?: 0) >= it.target }.map { it.id }.toSet()
}
