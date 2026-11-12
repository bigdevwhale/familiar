package app.familiar.ui.books

import app.familiar.data.Book
import app.familiar.data.Reading
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import app.familiar.domain.OwnGenre
import java.text.Collator
import java.util.Locale

/** How the shelf is ordered. Each has a natural direction; [Browse.reverse] turns it round. */
/** [SERIES]: cycle by cycle, each in volume order, books outside a cycle last. [MANUAL]: as the reader dragged them. */
enum class Sort { RECENT, ADDED, TITLE, AUTHOR, RATING, PROGRESS, LENGTH, SERIES, MANUAL }

enum class Format { ANY, PAPER, EBOOK, AUDIO }

/** What the shelf shows: a search, genres, a cycle, paper, e-book or audio, stars, and the order. */
data class Browse(
    val query: String = "",
    /** [genreKey] of built-in genres (with all their subgenres) and own genres; a book in any of them shows. */
    val genres: Set<String> = emptySet(),
    val format: Format = Format.ANY,
    /** At least this many stars; 0 for any. */
    val stars: Int = 0,
    /** Only the books of this cycle, in volume order; empty for any. */
    val series: String = "",
    val sort: Sort = Sort.RECENT,
    val reverse: Boolean = false,
) {
    /** How many filters are on (the search and the order aside). */
    val filters: Int get() = genres.size + (if (format != Format.ANY) 1 else 0) + (if (stars > 0) 1 else 0) + (if (series.isNotBlank()) 1 else 0)

    /** [genreNames]: what each built-in genre is called on screen, so typing "mystery" finds mysteries. */
    fun matches(b: Book, genreNames: Map<Genre, String> = emptyMap()): Boolean {
        val q = query.trim().lowercase()
        val genre = genreNames[Genre.of(b.genre)].orEmpty()
        if (q.isNotEmpty() && listOf(b.title, b.author, b.series, b.sub, b.tags, genre).none { q in it.lowercase() }) return false
        if (genres.isNotEmpty() && genreKey(Genre.of(b.genre)) !in genres && OwnGenre.of(b)?.let { genreKey(it) } !in genres) return false
        val f = when { b.audio -> Format.AUDIO; b.ebook -> Format.EBOOK; else -> Format.PAPER }
        if (format != Format.ANY && format != f) return false
        if (stars > 0 && (b.rating ?: 0) < stars) return false
        if (series.isNotBlank() && !b.series.trim().equals(series.trim(), ignoreCase = true)) return false
        return true
    }

    /** The matching [books] in order, paused ones last if [pausedLast] (on the reading shelf). */
    fun apply(books: List<Book>, readings: List<Reading>, pausedLast: Boolean = false, locale: Locale = Locale.getDefault(), genreNames: Map<Genre, String> = emptyMap()): List<Book> {
        val last = readings.groupBy { it.bookId }.mapValues { (_, l) -> l.maxOf { it.end } }
        val words = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val order: Comparator<Book> = when (sort) {
            Sort.RECENT -> compareByDescending { b -> listOfNotNull(b.finished, last[b.id], b.started, b.added).max() }
            Sort.ADDED -> compareByDescending { it.added }
            Sort.TITLE -> Comparator { a, b -> words.compare(a.title, b.title) }
            // By surname, books without an author last.
            Sort.AUTHOR -> compareBy<Book> { it.author.isBlank() }.then(Comparator { a, b -> words.compare(surname(a.author), surname(b.author)) }).then(Comparator { a, b -> words.compare(a.title, b.title) })
            Sort.RATING -> compareByDescending<Book> { it.rating ?: 0 }.thenByDescending { it.finished ?: 0 }
            Sort.PROGRESS -> compareByDescending<Book> { if (it.shelfOf == Shelf.DONE) 1f else it.fraction }.thenByDescending { it.page }
            Sort.LENGTH -> compareByDescending { it.pages }
            Sort.MANUAL -> manual
            Sort.SERIES -> compareBy<Book> { it.series.isBlank() }.then(Comparator { a, b -> words.compare(a.series.trim(), b.series.trim()) }).then(byVolume).then(Comparator { a, b -> words.compare(a.title, b.title) })
        }
        // One cycle picked: its volumes in order, whatever the sort; the sort only breaks ties.
        val chosen = if (reverse) order.reversed() else order
        val sorted = books.filter { matches(it, genreNames) }.sortedWith(if (series.isNotBlank()) byVolume.then(chosen) else chosen)
        val shelved = if (pausedLast && sort != Sort.MANUAL) sorted.sortedBy { it.shelfOf == Shelf.PAUSED } else sorted
        return if (sort == Sort.MANUAL) shelved else inVolumeOrder(shelved)
    }

    /**
     * A cycle's books keep the places the order gave them, but take them in volume order: sorted by when they
     * were read, the second volume mustn't stand before the first (#47). The reader's own order is left alone.
     */
    private fun inVolumeOrder(books: List<Book>): List<Book> {
        val out = books.toMutableList()
        books.withIndex().filter { it.value.series.isNotBlank() }.groupBy { it.value.series.trim().lowercase() }.values.forEach { cycle ->
            if (cycle.size < 2) return@forEach
            val ordered = cycle.map { it.value }.sortedWith(byVolume.thenBy { it.added })
            cycle.map { it.index }.zip(ordered).forEach { (i, b) -> out[i] = b }
        }
        return out
    }

    /** Volume 1, 2, 3…; books without a number after the numbered ones. */
    private val byVolume: Comparator<Book> get() = compareBy<Book> { it.volume == null }.thenBy { it.volume ?: 0 }

    /** The order as it's kept in settings: "TITLE", or "TITLE:r" turned round. */
    val saved: String get() = sort.name + if (reverse) ":r" else ""

    companion object {
        /** The reader's own order: by place, books never moved first, the newest of them first. */
        val manual: Comparator<Book> = compareBy<Book> { it.place }.thenByDescending { it.added }

        fun genreKey(g: Genre) = "g:" + g.name
        fun genreKey(g: OwnGenre) = "o:" + g.key

        fun sorted(saved: String): Browse = Browse(
            sort = Sort.entries.firstOrNull { it.name == saved.substringBefore(':') } ?: Sort.RECENT,
            reverse = saved.endsWith(":r"),
        )

        /** "J. R. R. Tolkien" → "Tolkien"; only the first of several authors counts. */
        fun surname(author: String): String {
            val first = author.split(',', '&', ';').first().trim()
            return first.substringAfterLast(' ').ifEmpty { first }
        }
    }
}
