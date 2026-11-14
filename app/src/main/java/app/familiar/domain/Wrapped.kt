package app.familiar.domain

import app.familiar.data.Book
import app.familiar.data.Find
import app.familiar.data.Pet
import app.familiar.data.Quote
import java.time.Instant
import java.time.LocalDate
import java.time.Month

/**
 * A year looked back on, a page at a time: what was read, when, and what the den gained by it. Everything
 * here is of [year] alone. [earnedDays] are the days achievements were earned on, by id.
 */
class Wrapped(s: Stats, val year: Int, allFinds: List<Find> = emptyList(), earnedDays: Map<String, String> = emptyMap()) {
    private val zone = s.zone
    private fun date(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    private fun inYear(t: Long) = date(t).year == year

    private val readings = s.readings.filter { inYear(it.end) }
    private val counted = readings.filter { it.counts }
    private val finished = Finished(s, year)

    /** Finished in the year, first to last. */
    val books: List<Book> = finished.books.sortedBy { it.finished ?: it.added }
    val pages: Int = readings.sumOf { it.pages }
    val minutes: Int = readings.filter { it.timed }.sumOf { it.minutes }
    val days: Int = counted.map { date(it.end) }.toSet().size
    val bestStreak: Int = longestRun(counted.map { date(it.end) }.toSet())

    val first: Book? = books.firstOrNull()
    val last: Book? = books.lastOrNull()?.takeIf { books.size > 1 }
    /** The thickest one, on paper or screen. */
    val longest: Book? = books.filter { !it.audio && it.pages > 0 }.maxByOrNull { it.pages }

    /** Genres by books finished, most first. */
    val genres: List<Pair<Genre, Int>> = finished.genres
    /** The author read most, when it was more than one book. */
    val author: Pair<String, Int>? = finished.authors.firstOrNull()?.takeIf { it.second >= 2 }
    val authors: Int = finished.authorCount

    /** The month most was read in, with its pages and minutes. */
    val bestMonth: Pair<Month, Int>? = counted.groupBy { date(it.end).month }.mapValues { (_, l) -> l.sumOf { it.amount } }.maxByOrNull { it.value }?.toPair()
    /** The day most pages were read on. */
    val bestDay: Pair<LocalDate, Int>? = readings.groupBy { date(it.end) }.mapValues { (_, l) -> l.sumOf { it.pages } }.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.toPair()

    val parts: Map<DayPart, Int> = DayPart.entries.associateWith { p -> counted.count { DayPart.of(Instant.ofEpochMilli(it.start).atZone(zone).hour) == p } }
    val bestPart: DayPart? = parts.filter { it.value > 0 }.maxByOrNull { it.value }?.key
    val nights: Int = counted.count { isNight(it.start, zone) }
    val mornings: Int = counted.count { isMorning(it.start, zone) }
    val listens: Int = counted.count { it.listened }

    val fiveStars: List<Book> = books.filter { it.rating == 5 }
    val avgRating: Float? = finished.avgRating

    val quotes: List<Quote> = s.quotes.filter { inYear(it.at) }
    /** A line of the year to show: the longest that still fits a page. */
    val quote: Quote? = quotes.filter { it.text.length <= 280 }.maxByOrNull { it.text.length } ?: quotes.firstOrNull()

    /** Familiars that joined the den in the year. */
    val newPets: List<Pet> = s.pets.filter { inYear(it.joined) }
    val mana: Int = readings.sumOf { it.mana }
    val finds: Int = allFinds.count { inYear(it.at) }
    val feats: Int = earnedDays.values.count { it.startsWith("$year-") }

    /** Nothing was read that year: there is no story to tell. */
    val empty: Boolean = counted.isEmpty() && books.isEmpty()

    companion object {
        /** The year to offer looking back on: the one just ended through January, this one otherwise. */
        fun yearToShow(today: LocalDate): Int = if (today.monthValue == 1) today.year - 1 else today.year
    }
}
