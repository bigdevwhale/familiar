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
import org.json.JSONArray
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
 * Books from another reading site: a Goodreads export (My Books → Import and export), the LiveLib export
 * made on our website (the same columns plus a cover address), one made by Livelib Backup
 * (chimildic.github.io/livelib-backup) in any of its three kinds, or the Yandex Books export made by
 * tools/yandex_books.py. Only books come across: no sittings, so they feed no familiar. Pages and genre
 * are whatever the file knows, often nothing.
 */
object Import {
    /** [isbn] keeps what the file knew of each book's ISBN, to find covers by once the books are in. */
    class Result(val books: List<Book>, val skipped: Int, val isbn: Map<Pair<String, String>, String> = emptyMap())

    /**
     * The books in [text] that aren't already among [have]. [name] is the file's name: Livelib Backup tells
     * the shelf only there ("livelib-reader-wish-01.11.2026.csv"). Throws [IllegalArgumentException] when it
     * isn't such a file.
     */
    fun parse(text: String, have: List<Book>, name: String = "", now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Result {
        val body = text.removePrefix(Char(0xFEFF).toString()).trim()
        val json = body.startsWith("[")
        val head = body.lineSequence().first()
        val rows = if (json) backupJson(body) else table(body)
        // Livelib Backup's files: the shelf is in the file's name (or, made for Goodreads, its Shelves column);
        // with neither it is the Read page, the site's first choice.
        val backup = json || "Название" in head || "Shelves" in head
        val hint = BACKUP_PAGE.find(name)?.groupValues?.get(1)?.let { PAGES[it] }

        val seen = have.map { key(it.title, it.author) }.toMutableSet()
        var skipped = 0
        val isbn = mutableMapOf<Pair<String, String>, String>()
        val books = rows.mapNotNull { r ->
            fun of(column: String) = r[column]?.trim().orEmpty()
            // Goodreads keeps the cycle in the title: "Dune (Dune Chronicles, #1)".
            val cycle = CYCLE.matchEntire(of("Title"))
            val title = (cycle?.groupValues?.get(1)?.trim() ?: of("Title")).replace(EDITION, "").trim()
            if (title.isEmpty()) return@mapNotNull null
            // Livelib Backup joins co-authors with a bare comma.
            val author = of("Author").replace(Regex(""",(?=\S)"""), ", ")
            if (!seen.add(key(title, author))) { skipped++; return@mapNotNull null }
            // Goodreads writes ISBNs as ="0441013597"; LiveLib lists every edition's, "978-5-17-1, 978-5-04-2".
            (of("ISBN13").ifEmpty { of("ISBN") }).split(',', ';')
                .map { s -> s.filter { it.isDigit() || it == 'X' } }.firstOrNull { it.length >= 10 }
                ?.let { isbn[key(title, author)] = it }
            val exclusive = of("Exclusive Shelf").ifEmpty { of("Shelves") }.ifEmpty { hint ?: if (backup) "read" else "" }.lowercase()
            val shelf = when {
                exclusive == "read" -> Shelf.DONE
                exclusive == "currently-reading" -> Shelf.READING
                exclusive == "unread" -> Shelf.PAUSED
                // A shelf of the reader's own on Goodreads: read if it says when.
                exclusive != "to-read" && of("Date Read").isNotEmpty() -> Shelf.DONE
                else -> Shelf.WANT
            }
            val pages = of("Number of Pages").toIntOrNull()?.coerceAtLeast(0) ?: 0
            val read = date(of("Date Read"), zone)
            val added = date(of("Date Added"), zone)
            val finished = if (shelf == Shelf.DONE) read ?: added ?: now else null
            Book(
                title = title,
                author = author,
                pages = pages,
                // Goodreads has no genres, but a reader's own shelves often are ones: "fantasy", "popular-science".
                genre = genre(of("Genres").ifEmpty { of("Bookshelves") + "," + exclusive }).name,
                shelf = shelf.name,
                page = if (shelf == Shelf.DONE) pages else 0,
                added = added ?: finished ?: now,
                started = if (shelf == Shelf.READING) added ?: now else null,
                finished = finished,
                // Goodreads writes "4.0", LiveLib "4.5".
                rating = of("My Rating").replace(',', '.').toDoubleOrNull()?.roundToInt()?.takeIf { shelf == Shelf.DONE && it in 1..5 },
                cover = of("Cover").takeIf { it.startsWith("https://") }.orEmpty(),
                audio = of("Audio").isNotEmpty(),
                series = of("Series").ifEmpty { cycle?.groupValues?.get(2)?.trim().orEmpty() },
                volume = (of("Series Number").ifEmpty { cycle?.groupValues?.get(3).orEmpty() }).toIntOrNull(),
                // Goodreads lists every shelf a book is on; the ones besides read / to-read become the reader's own shelves.
                tags = (of("Bookshelves").split(',') + exclusive).map { it.trim() }.filter { it.isNotEmpty() && it !in SHELVES }.distinct().joinToString("\n"),
            )
        }
        return Result(books, skipped, isbn)
    }

    /** A CSV with a header row, by column name; Livelib Backup's Russian names are read as Goodreads' ones. */
    private fun table(body: String): List<Map<String, String>> {
        val head = body.lineSequence().first()
        // Livelib Backup's plain CSV is split by semicolons, everything else by commas.
        val rows = csv(body, if (head.count { it == ';' } > head.count { it == ',' }) ';' else ',')
        val names = rows.firstOrNull()?.map { it.trim().let { n -> RUSSIAN[n] ?: n } }.orEmpty()
        require("Title" in names && "Author" in names) { "Not a book export" }
        return rows.drop(1).map { r -> names.withIndex().associate { (i, n) -> n to r.getOrNull(i).orEmpty() } }
    }

    /** Livelib Backup's JSON: [{title, authors: [{name}], readDate, rating: {user}, details: {isbn}, genres: [{name}], coverHref}]. */
    private fun backupJson(body: String): List<Map<String, String>> {
        val list = runCatching { JSONArray(body) }.getOrElse { throw IllegalArgumentException("Not a book export") }
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.map { o ->
            fun names(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) } }.orEmpty()
            mapOf(
                "Title" to o.optString("title"),
                "Author" to names("authors").joinToString(", "),
                "Date Read" to o.optString("readDate"),
                "My Rating" to (o.optJSONObject("rating")?.optString("user") ?: ""),
                "ISBN" to (o.optJSONObject("details")?.optString("isbn") ?: ""),
                "Genres" to names("genres").joinToString("; "),
                "Cover" to o.optString("coverHref"),
            )
        }.also { require(it.any { r -> r["Title"]!!.isNotBlank() }) { "Not a book export" } }
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

    private val SHELVES = setOf("read", "currently-reading", "to-read", "unread")
    /** Livelib Backup's column names, as Goodreads calls them; its publisher's "Серия" is no cycle and stays out. */
    private val RUSSIAN = mapOf(
        "Название" to "Title", "Автор" to "Author", "Дата прочтения" to "Date Read", "Моя оценка" to "My Rating",
        "Жанры" to "Genres", "Ссылка на обложку" to "Cover", "Дата издания" to "Year Published",
    )
    /** Livelib Backup's pages, in the names of its files, as Goodreads shelves. */
    private val PAGES = mapOf("read" to "read", "unread" to "unread", "reading" to "currently-reading", "wish" to "to-read")
    private val BACKUP_PAGE = Regex("""livelib-.+-(read|unread|reading|wish)-""")
    private val MONTHS = listOf("январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")
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
            // "Детская проза и поэзия" (Yandex Books) is a shelf for any children's book, not poems.
            WORDS.firstOrNull { (genre, words) -> words.any { it in name } && !(genre == Genre.POETRY && "проз" in name) }?.let { return it.first }
        }
        return Genre.OTHER
    }

    /** Which books count as the same: case, spaces and punctuation aside. */
    internal fun key(title: String, author: String) =
        title.lowercase().filter { it.isLetterOrDigit() } to author.lowercase().filter { it.isLetterOrDigit() }

    /**
     * "2019/08/15" (Goodreads), "2019-08-15" or LiveLib's "Декабрь 2025 г." (the first of that month), at noon
     * so the day survives any time zone.
     */
    private fun date(s: String, zone: ZoneId): Long? {
        val day = runCatching { LocalDate.parse(s.replace('/', '-')) }.getOrNull() ?: Regex("""(\p{L}+)\s+(\d{4})""").find(s.lowercase())?.let { m ->
            val month = MONTHS.indexOfFirst { m.groupValues[1].startsWith(it) }
            if (month < 0) null else LocalDate.of(m.groupValues[2].toInt(), month + 1, 1)
        }
        return day?.atTime(LocalTime.NOON)?.atZone(zone)?.toInstant()?.toEpochMilli()
    }

    /**
     * RFC 4180: [sep]-separated, quoted fields with doubled quotes, line breaks inside quotes. A quote opens a
     * quoted field only at its start: Livelib Backup quotes nothing, so a title may hold a bare one.
     */
    private fun csv(text: String, sep: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted -> cell.append(c)
                c == '"' && cell.isEmpty() -> quoted = true
                c == sep -> { row += cell.toString(); cell.clear() }
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
