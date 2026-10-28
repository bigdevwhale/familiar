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
