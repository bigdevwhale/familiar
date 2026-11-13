package app.familiar.domain

import app.familiar.data.Book
import app.familiar.data.Shelf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

/** The time of day a sitting starts in. */
enum class DayPart { MORNING, DAY, EVENING, NIGHT;
    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..9 -> MORNING
            in 10..16 -> DAY
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

/** One month of reading: pages and minutes read, and books finished in it. */
class MonthRead(val month: YearMonth, val amount: Int, val books: Int)

/** The plain numbers behind the reading: pace, habits and months. */
class Insights(private val s: Stats) {
    private val counted = s.readings.filter { it.counts }
    private val bookOf = s.books.associateBy { it.id }
    /** Timed sittings with paper pages: the only ones that tell how fast the reader reads. */
    private val paced = s.readings.filter { it.timed && !it.listened && it.pages > 0 && it.minutes > 0 && bookOf[it.bookId]?.audio != true }

    /** Pages an hour over every timed sitting, or null before half an hour of them. */
    val pace: Int? = pace(paced)

    /** Pages an hour in each genre with half an hour of timed reading, fastest first. */
    val paceByGenre: List<Pair<Genre, Int>> = paced.groupBy { Genre.of(bookOf[it.bookId]?.genre ?: "") }
        .mapNotNull { (g, rs) -> pace(rs)?.let { g to it } }
        .sortedByDescending { it.second }

    /** Sittings started in each part of the day. */
    val dayParts: Map<DayPart, Int> = DayPart.entries.associateWith { p -> counted.count { DayPart.of(hour(it.start)) == p } }
    val bestPart: DayPart? = dayParts.filter { it.value > 0 }.maxByOrNull { it.value }?.key

    /** Days of reading on each day of the week. */
    val weekdays: Map<DayOfWeek, Int> = DayOfWeek.entries.associateWith { d -> s.readDays.count { it.dayOfWeek == d } }
    val bestWeekday: DayOfWeek? = weekdays.filter { it.value > 0 }.maxByOrNull { it.value }?.key

    /** Average minutes a timed sitting lasts, or null without any. */
    val avgSitting: Int? = s.readings.filter { it.timed && it.minutes > 0 }.takeIf { it.isNotEmpty() }?.let { rs -> rs.sumOf { it.minutes } / rs.size }
    /** Average pages (and minutes) on a day of reading. */
    val perDay: Int = if (s.readDays.isEmpty()) 0 else counted.sumOf { it.amount } / s.readDays.size
    val hours: Int = s.totalMinutes / 60

    /** The last six months, oldest first, this one last. */
    val months: List<MonthRead> = run {
        val now = YearMonth.from(s.today)
        (5 downTo 0).map { back ->
            val m = now.minusMonths(back.toLong())
            MonthRead(
                m,
                counted.filter { YearMonth.from(date(it.end)) == m }.sumOf { it.amount },
                s.finished.count { it.finished != null && YearMonth.from(date(it.finished)) == m },
            )
        }
    }

    private fun pace(rs: List<app.familiar.data.Reading>): Int? {
        val minutes = rs.sumOf { it.minutes }
        if (minutes < MIN_MINUTES) return null
        return (rs.sumOf { it.pages } * 60f / minutes).toInt()
    }

    private fun hour(t: Long) = Instant.ofEpochMilli(t).atZone(s.zone).hour
    private fun date(t: Long) = Instant.ofEpochMilli(t).atZone(s.zone).toLocalDate()

    companion object {
        /** Less timed reading than this says nothing about a pace. */
        const val MIN_MINUTES = 30
        /** The recent days the pace of a book is measured over. */
        const val RECENT_DAYS = 14

        /**
         * The day [book] will be finished if it goes on as in the last two weeks, or null when its
         * length isn't known, it isn't being read, or it is too slow to say (more than a year).
         */
        fun forecast(book: Book, s: Stats): LocalDate? {
            if (book.pages <= 0 || book.shelfOf != Shelf.READING) return null
            val left = book.pages - book.page
            if (left <= 0) return null
            val mine = s.readings.filter { it.bookId == book.id && it.pages > 0 }
            if (mine.isEmpty()) return null
            val day = { t: Long -> Instant.ofEpochMilli(t).atZone(s.zone).toLocalDate() }
            val from = s.today.minusDays(RECENT_DAYS - 1L)
            val recent = mine.filter { !day(it.end).isBefore(from) }
            if (recent.isEmpty()) return null
            // A book begun this week is measured from its first sitting, not over two weeks.
            val first = day(mine.minOf { it.start })
            val span = (s.today.toEpochDay() - maxOf(first, from).toEpochDay() + 1).coerceIn(1, RECENT_DAYS.toLong())
            val perDay = recent.sumOf { it.pages } / span.toFloat()
            val days = ceil(left / perDay).toLong()
            return if (days > 365) null else s.today.plusDays(days)
        }
    }
}

/**
 * What the finished books of [year] add up to, or of every year when it's null: how many, how long,
 * how they were rated, which genres and authors, and when they were finished.
 */
class Finished(s: Stats, val year: Int?) {
    private val zone = s.zone
    private fun date(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    val books: List<Book> = s.finished.filter { year == null || (it.finished != null && date(it.finished).year == year) }
    /** Books on paper with a known length: audiobooks count minutes, not pages. */
    private val paper = books.filter { !it.audio && it.pages > 0 }
    val pages: Int = paper.sumOf { it.pages }
    val avgLength: Int? = if (paper.isEmpty()) null else pages / paper.size
    val avgRating: Float? = books.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    /** Days from the first page to the last, both counted, over the books where both are known. */
    val avgDays: Int? = books.mapNotNull { b ->
        val from = b.started ?: return@mapNotNull null
        val to = b.finished ?: return@mapNotNull null
        (date(to).toEpochDay() - date(from).toEpochDay() + 1).takeIf { it > 0 }
    }.takeIf { it.isNotEmpty() }?.average()?.let { kotlin.math.round(it).toInt() }

    /** Books in each genre, the most read first. */
    val genres: List<Pair<Genre, Int>> = books.groupBy { Genre.of(it.genre) }.map { it.key to it.value.size }.sortedByDescending { it.second }
    /** Books given each number of stars, five first. */
    val ratings: List<Pair<Int, Int>> = (5 downTo 1).map { r -> r to books.count { it.rating == r } }
    /** Books on paper by length: up to [LENGTHS] pages, each bucket starting where the last one ended. */
    val lengths: List<Int> = (LENGTHS.indices).map { i ->
        val from = if (i == 0) 0 else LENGTHS[i - 1]
        paper.count { it.pages >= from && (i == LENGTHS.lastIndex || it.pages < LENGTHS[i]) }
    }
    private val byAuthor = books.filter { it.author.isNotBlank() }.groupBy { it.author.trim().lowercase() }
    /** How many different authors wrote them. */
    val authorCount: Int = byAuthor.size
    /** The authors with most books, up to five. */
    val authors: List<Pair<String, Int>> = byAuthor
        .map { (_, l) -> l.first().author.trim() to l.size }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .take(5)
    /** Books finished in each month of [year], January first; empty when every year is counted. */
    val byMonth: List<Int> = if (year == null) emptyList() else (1..12).map { m -> books.count { it.finished != null && date(it.finished).monthValue == m } }
    /** Books finished in each of the last years up to this one, oldest first; empty for a single year. */
    val byYear: List<Pair<Int, Int>> = if (year != null) emptyList() else {
        val years = books.mapNotNull { it.finished?.let { t -> date(t).year } }
        if (years.isEmpty()) emptyList() else (maxOf(years.min(), s.today.year - YEARS + 1)..s.today.year).map { y -> y to years.count { it == y } }
    }

    companion object {
        /** Where the lengths are cut: under 200 pages, under 400, under 700, and the rest. */
        val LENGTHS = listOf(200, 400, 700, Int.MAX_VALUE)
        /** How many years the all-time columns go back. */
        const val YEARS = 8
    }
}

/** A book on a day of the calendar; [finished] that very day. */
class DayBook(val book: Book, val finished: Boolean)

/**
 * The books each day of [month] shows, the first one foremost: those finished that day (the latest
 * first), then those read, the most read first. A day with nothing logged shows the books open then,
 * from the day each was started to the day it was finished (#30), the latest started first, so books
 * brought over from elsewhere fill their days too. A book still being read fills them only up to its last sitting,
 * and a book read here fills only the days before the first sitting logged in the app at all: from then on the
 * sittings are the days, so a day nothing was read stays empty (#48, #50, #51).
 */
fun dayBooks(s: Stats, month: YearMonth): Map<LocalDate, List<DayBook>> {
    fun date(t: Long) = Instant.ofEpochMilli(t).atZone(s.zone).toLocalDate()
    val bookOf = s.books.associateBy { it.id }
    val out = linkedMapOf<LocalDate, MutableList<DayBook>>()
    fun add(d: LocalDate, b: Book, done: Boolean) {
        val l = out.getOrPut(d) { mutableListOf() }
        if (l.none { it.book.id == b.id }) l += DayBook(b, done)
    }
    s.finished.filter { it.finished != null && YearMonth.from(date(it.finished)) == month }
        .sortedByDescending { it.finished }
        .forEach { add(date(it.finished!!), it, true) }
    s.readings.filter { it.counts }
        .groupBy { date(it.end) }
        .filterKeys { YearMonth.from(it) == month }
        .forEach { (d, rs) ->
            rs.groupBy { it.bookId }.entries.sortedByDescending { (_, l) -> l.sumOf { it.amount } }
                .forEach { (id, _) -> bookOf[id]?.let { add(d, it, false) } }
        }
    val first = month.atDay(1)
    val last = minOf(month.atEndOfMonth(), s.today)
    // A book still open fills its days only up to its last sitting, never on to today: a book set on the
    // shelf and not opened yet, or a day nothing was read, mustn't show as a day of reading.
    val sittings = s.readings.filter { it.counts }.groupBy { it.bookId }
    val lastSitting = sittings.mapValues { (_, l) -> date(l.maxOf { it.end }) }
    // Once reading is logged here, sittings mark the days; a book read here fills only the time before the
    // first sitting in the app, of any book. Not before its own first one: a book set on the shelf on the 9th
    // and first opened on the 11th would paint the 10th, a day nothing was read.
    val logSince = sittings.values.flatten().minOfOrNull { date(it.end) }
    val spans = s.books.mapNotNull { b ->
        val start = b.started?.let { date(it) } ?: return@mapNotNull null
        val open = when (b.shelfOf) {
            Shelf.DONE -> b.finished?.let { date(it) }
            Shelf.READING, Shelf.PAUSED -> lastSitting[b.id]
            else -> null
        } ?: return@mapNotNull null
        val end = if (b.id in sittings && logSince != null) minOf(logSince.minusDays(1), open) else open
        Triple(b, start, end).takeIf { !start.isAfter(last) && !end.isBefore(first) && !end.isBefore(start) && end.toEpochDay() - start.toEpochDay() <= MAX_SPAN }
    }.sortedByDescending { it.second }
    var d = first
    while (!d.isAfter(last)) {
        if (d !in out) spans.filter { (_, a, z) -> !d.isBefore(a) && !d.isAfter(z) }.forEach { add(d, it.first, false) }
        d = d.plusDays(1)
    }
    return out
}

/** A book open longer than this many days doesn't paint the calendar: it was most likely forgotten. */
private const val MAX_SPAN = 400

/** One month of reading in full: what was finished, what was opened, and every day of it. */
class MonthSummary(private val s: Stats, val month: YearMonth) {
    private fun date(t: Long) = Instant.ofEpochMilli(t).atZone(s.zone).toLocalDate()
    private val mine = s.readings.filter { it.counts && YearMonth.from(date(it.end)) == month }
    private val bookOf = s.books.associateBy { it.id }

    val finished: List<Book> = s.finished.filter { it.finished != null && YearMonth.from(date(it.finished)) == month }
    /** Books with a sitting this month, the latest first. */
    val opened: List<Book> = mine.sortedByDescending { it.end }.mapNotNull { bookOf[it.bookId] }.distinctBy { it.id }
    /** Pages (and minutes listened) on each day of the month. */
    val days: Map<LocalDate, Int> = mine.groupBy { date(it.end) }.mapValues { (_, rs) -> rs.sumOf { it.amount } }
    val pages: Int = mine.sumOf { it.pages }
    val minutes: Int = mine.filter { it.timed }.sumOf { it.minutes }
    val bestStreak: Int = longestRun(days.keys)
    val genre: Genre? = (finished.ifEmpty { opened }).groupBy { Genre.of(it.genre) }.maxByOrNull { it.value.size }?.key
    val empty: Boolean get() = mine.isEmpty() && finished.isEmpty()
}
