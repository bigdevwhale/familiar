package app.familiar.data

import app.familiar.domain.Genre
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Books from another reading site, as a Goodreads export (My Books → Import and export) or the LiveLib
 * export made on the website, which uses the same columns plus a cover address. Only books come across:
 * no sittings, so they feed no familiar. Pages and genre are whatever the file knows, often nothing.
 */
object Import {
    /** [isbn] keeps what the file knew of each book's ISBN, to find covers by once the books are in. */
    class Result(val books: List<Book>, val skipped: Int, val isbn: Map<Pair<String, String>, String> = emptyMap())

    /** The books in [text] that aren't already among [have]. Throws [IllegalArgumentException] when it isn't such a file. */
    fun parse(text: String, have: List<Book>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Result {
        val rows = csv(text.removePrefix(Char(0xFEFF).toString()))
        val head = rows.firstOrNull()?.map { it.trim() }.orEmpty()
        val col = head.withIndex().associate { (i, name) -> name to i }
        require("Title" in col && "Author" in col) { "Not a Goodreads or LiveLib export" }
        fun List<String>.of(name: String) = col[name]?.let { getOrNull(it) }?.trim().orEmpty()

        val seen = have.map { key(it.title, it.author) }.toMutableSet()
        var skipped = 0
        val isbn = mutableMapOf<Pair<String, String>, String>()
        val books = rows.drop(1).mapNotNull { r ->
            // Goodreads keeps the cycle in the title: "Dune (Dune Chronicles, #1)".
            val cycle = CYCLE.matchEntire(r.of("Title"))
            val title = (cycle?.groupValues?.get(1)?.trim() ?: r.of("Title")).replace(EDITION, "").trim()
            if (title.isEmpty()) return@mapNotNull null
            val author = r.of("Author")
            if (!seen.add(key(title, author))) { skipped++; return@mapNotNull null }
            // Goodreads writes ISBNs as ="0441013597", so only digits (and a final X) count.
            r.of("ISBN13").ifEmpty { r.of("ISBN") }.filter { it.isDigit() || it == 'X' }.takeIf { it.length >= 10 }?.let { isbn[key(title, author)] = it }
            val exclusive = r.of("Exclusive Shelf").lowercase()
            val shelf = when {
                exclusive == "read" -> Shelf.DONE
                exclusive == "currently-reading" -> Shelf.READING
                // A shelf of the reader's own on Goodreads: read if it says when.
                exclusive != "to-read" && r.of("Date Read").isNotEmpty() -> Shelf.DONE
                else -> Shelf.WANT
            }
            val pages = r.of("Number of Pages").toIntOrNull()?.coerceAtLeast(0) ?: 0
            val read = date(r.of("Date Read"), zone)
            val added = date(r.of("Date Added"), zone)
            val finished = if (shelf == Shelf.DONE) read ?: added ?: now else null
            Book(
                title = title,
                author = author,
                pages = pages,
                // Goodreads has no genres, but a reader's own shelves often are ones: "fantasy", "popular-science".
                genre = genre(r.of("Genres").ifEmpty { r.of("Bookshelves") + "," + exclusive }).name,
                shelf = shelf.name,
                page = if (shelf == Shelf.DONE) pages else 0,
                added = added ?: finished ?: now,
                started = if (shelf == Shelf.READING) added ?: now else null,
                finished = finished,
                // Goodreads writes "4.0".
                rating = r.of("My Rating").toDoubleOrNull()?.roundToInt()?.takeIf { shelf == Shelf.DONE && it in 1..5 },
                cover = r.of("Cover").takeIf { it.startsWith("https://") }.orEmpty(),
                audio = r.of("Audio").isNotEmpty(),
                series = r.of("Series").ifEmpty { cycle?.groupValues?.get(2)?.trim().orEmpty() },
                volume = (r.of("Series Number").ifEmpty { cycle?.groupValues?.get(3).orEmpty() }).toIntOrNull(),
                // Goodreads lists every shelf a book is on; the ones besides read / to-read become the reader's own shelves.
                tags = (r.of("Bookshelves").split(',') + exclusive).map { it.trim() }.filter { it.isNotEmpty() && it !in SHELVES }.distinct().joinToString("\n"),
            )
        }
        return Result(books, skipped, isbn)
    }

    /** Where the books still waiting for a cover are kept, so the search goes on after the app is closed. */
    private const val QUEUE = "cover-queue.json"
    private val searching = AtomicBoolean()
    private val queueLock = Any()

    /** Goodreads gives no covers: puts every book [result] brought without one in line for [covers]. */
    suspend fun wantCovers(dir: File, dao: FamiliarDao, result: Result) {
        val wanted = result.books.filter { it.cover.isEmpty() }.map { key(it.title, it.author) }.toSet()
        val bare = dao.books().filter { it.cover.isEmpty() && key(it.title, it.author) in wanted }
        synchronized(queueLock) { write(dir, read(dir) + bare.associate { it.id to result.isbn[key(it.title, it.author)].orEmpty() }) }
    }

    /**
     * Looks up a cover for each book waiting in line, a few at a time and in the background, telling [changed]
     * now and then so the shelf fills in as they come. Each book leaves the line once it has been tried, so
     * opening the app again goes on from where it stopped.
     */
    suspend fun covers(dir: File, dao: FamiliarDao, changed: suspend () -> Unit) {
        if (!searching.compareAndSet(false, true)) return
        try {
            // Books put in line while a round ran (another import) get a round of their own.
            while (true) {
                val waiting = synchronized(queueLock) { read(dir) }
                if (waiting.isEmpty()) break
                round(dir, dao, waiting, changed)
            }
        } finally {
            searching.set(false)
        }
    }

    private suspend fun round(dir: File, dao: FamiliarDao, waiting: Map<Long, String>, changed: suspend () -> Unit) = coroutineScope {
        // A few at a time; one book failing or hanging never stops the others.
        val gate = Semaphore(3)
        val done = AtomicInteger()
        waiting.map { (id, isbn) ->
            launch {
                gate.withPermit {
                    val b = dao.book(id)
                    if (b != null && b.cover.isEmpty()) {
                        // On a thread of its own: a request stuck where its timeouts don't reach (a name lookup)
                        // can't be cancelled, so it is left behind after 40 seconds instead of holding up the rest.
                        val found = CompletableDeferred<String?>()
                        thread(isDaemon = true) {
                            found.complete(runCatching { runBlocking { Covers.find(b.title, b.author, isbn) } }.getOrNull())
                        }
                        val url = withTimeoutOrNull(40_000) { found.await() }
                        // Read again: the reader may have changed the book while the catalogues answered.
                        if (url != null) dao.book(id)?.takeIf { it.cover.isEmpty() }?.let { dao.updateBook(it.copy(cover = url)) }
                    }
                    synchronized(queueLock) { write(dir, read(dir) - id) }
                    if (done.incrementAndGet() % 10 == 0) changed()
                }
            }
        }.joinAll()
        changed()
    }

    private fun read(dir: File): Map<Long, String> = runCatching {
        val o = JSONObject(File(dir, QUEUE).readText())
        o.keys().asSequence().associate { it.toLong() to o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun write(dir: File, queue: Map<Long, String>) {
        val f = File(dir, QUEUE)
        if (queue.isEmpty()) f.delete() else f.writeText(JSONObject(queue.mapKeys { it.key.toString() }).toString())
    }

    private val SHELVES = setOf("read", "currently-reading", "to-read")
    /** Amazon's "(Russian Edition)", "(Kindle Edition)" and the like after a title. */
    private val EDITION = Regex("""\s*\([^()]*\bEdition\)\s*$""", RegexOption.IGNORE_CASE)
    private val CYCLE = Regex("""(.+?)\s*\(([^()#]+?),?\s*#(\d+)[^()]*\)""")

    /** Words in a site's genre names that tell which of ours it is, the more telling ones first. */
    private val WORDS = listOf(
        Genre.HORROR to listOf("ужас", "хоррор", "horror", "мистик"),
        // Before mystery: "Детективное фэнтези" is fantasy first.
        Genre.FANTASY to listOf("фэнтези", "fantasy", "сказк"),
        Genre.SCIFI to listOf("фантастик", "science fiction", "sci fi", "киберпанк", "стимпанк", "steampunk", "антиутоп", "постапокалип", "космич"),
        Genre.MYSTERY to listOf("детектив", "триллер", "mystery", "thriller", "crime"),
        Genre.ROMANCE to listOf("любов", "романтич", "romance"),
        Genre.POETRY to listOf("поэзи", "стих", "poetry"),
        Genre.HISTORY to listOf("истори", "history"),
        Genre.NONFICTION to listOf(
            "нон фикшн", "научно попул", "психолог", "бизнес", "саморазвит", "биограф", "мемуар", "публицист", "философ",
            "наука", "здоровье", "воспитан", "педагог", "nonfiction", "non fiction", "biography", "psychology", "self help", "business", "science",
        ),
        Genre.ADVENTURE to listOf("приключен", "вестерн", "adventure", "western"),
        Genre.CLASSIC to listOf("классик", "classic"),
    )

    /** Ours for "Фантастика; Зарубежная фантастика" and the like: the first of the site's genres (its main one leads) we know a word in. */
    fun genre(names: String): Genre {
        for (name in names.lowercase().replace('-', ' ').split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }) {
            WORDS.firstOrNull { (_, words) -> words.any { it in name } }?.let { return it.first }
        }
        return Genre.OTHER
    }

    private fun key(title: String, author: String) =
        title.lowercase().filter { it.isLetterOrDigit() } to author.lowercase().filter { it.isLetterOrDigit() }

    /** "2019/08/15" (Goodreads) or "2019-08-15", at noon so the day survives any time zone. */
    private fun date(s: String, zone: ZoneId): Long? = runCatching {
        LocalDate.parse(s.replace('/', '-')).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** RFC 4180: commas, quoted fields with doubled quotes, line breaks inside quotes. */
    private fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                quoted -> cell.append(c)
                c == ',' -> { row += cell.toString(); cell.clear() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotEmpty() }) rows += row
        return rows
    }
}
