package app.familiar.data

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * A Bookmory backup (Settings → Backup in Bookmory, a .bookmory file): a zip around the app's database,
 * where every record is a JSON value in an `entry` table, filed by store ("books", "collections").
 * Each book carries its reads, the last of which says where it stands.
 */
object Bookmory {
    private const val DB = "new_bookmory.db"

    /** Whether [bytes] look like a zip, as a Bookmory backup is. */
    fun isBackup(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** The books in the backup [bytes] not already among [have]; [tmp] is where its database is opened. */
    fun parse(bytes: ByteArray, have: List<Book>, tmp: File, now: Long = System.currentTimeMillis()): Import.Result {
        val file = File(tmp, "bookmory-${System.nanoTime()}.db")
        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.firstOrNull { it.name.substringAfterLast('/') == DB }
                    ?: throw IllegalArgumentException("Not a Bookmory backup")
                file.outputStream().use { zip.copyTo(it) }
            }
            val (books, collections) = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                fun store(name: String) = db.rawQuery(
                    "SELECT key, value FROM entry WHERE store = ? AND (deleted IS NULL OR deleted = 0)", arrayOf(name),
                ).use { c -> buildList { while (c.moveToNext()) c.getString(1)?.let { add(c.getString(0) to it) } } }
                store("books").map { it.second } to store("collections").associate { (k, v) -> k to JSONObject(v).optString("name") }
            }
            return books(books.map { JSONObject(it) }, collections, have, now)
        } finally {
            file.delete()
        }
    }

    /** The books among Bookmory's [records], their collections named by [collections]. */
    fun books(records: List<JSONObject>, collections: Map<String, String>, have: List<Book>, now: Long): Import.Result {
        val seen = have.map { Import.key(it.title, it.author) }.toMutableSet()
        var skipped = 0
        val isbn = mutableMapOf<Pair<String, String>, String>()
        val books = records.mapNotNull { b ->
            val title = b.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null
            val author = b.strings("authors").ifEmpty { listOf(b.optString("author")) }.filter { it.isNotBlank() }.joinToString(", ")
            if (!seen.add(Import.key(title, author))) { skipped++; return@mapNotNull null }
            b.optString("isbn").filter { it.isDigit() || it == 'X' }.takeIf { it.length >= 10 }?.let { isbn[Import.key(title, author)] = it }

            val reads = b.optJSONArray("reads")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
            val read = reads.maxByOrNull { it.optInt("nth") }
            val status = read?.optString("status").orEmpty().uppercase()
            val shelf = when {
                read == null -> Shelf.WANT
                status == "DONE" -> Shelf.DONE
                status.startsWith("READ") -> Shelf.READING
                listOf("GIVE", "STOP", "DROP", "ABANDON").any { it in status } -> Shelf.ABANDONED
                listOf("PAUS", "HOLD").any { it in status } -> Shelf.PAUSED
                else -> Shelf.WANT
            }
            // Pages only when counted in pages: an e-book in percent or an audiobook in time says nothing of length.
            val inPages = b.optString("page_type", "PAGE") == "PAGE"
            val pages = if (inPages) b.optDouble("total_page", 0.0).toInt().coerceAtLeast(0) else 0
            val start = read?.time("start")
            val end = read?.time("end")
            val finished = when (shelf) {
                Shelf.DONE -> end ?: start ?: b.time("last_read_done_date") ?: now
                Shelf.ABANDONED -> end ?: b.time("last_give_up_date")
                else -> null
            }
            val star = read?.optDouble("star", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
            Book(
                title = title,
                author = author,
                pages = pages,
                // Bookmory's tags are the catalogue's genres: "#Fantasy #Fiction #EpicFantasy".
                genre = Import.genre(b.optString("tags").replace('#', ';')).name,
                shelf = shelf.name,
                page = when (shelf) {
                    Shelf.DONE -> pages
                    Shelf.READING, Shelf.PAUSED, Shelf.ABANDONED -> if (inPages) b.optDouble("cur_page", 0.0).toInt().coerceIn(0, pages.takeIf { it > 0 } ?: Int.MAX_VALUE) else 0
                    else -> 0
                },
                added = b.time("created_at") ?: start ?: finished ?: now,
                started = if (shelf == Shelf.WANT) null else start,
                finished = finished,
                rating = star.roundToInt().takeIf { shelf == Shelf.DONE && it in 1..5 },
                cover = b.optString("image").takeIf { it.startsWith("https://") }.orEmpty(),
                audio = (b.strings("book_types") + b.optString("book_type")).any { it.contains("audio", ignoreCase = true) },
                ebook = (b.strings("book_types") + b.optString("book_type")).let { t -> t.none { it.contains("audio", ignoreCase = true) } && t.any { it.contains("ebook", ignoreCase = true) || it.contains("e_book", ignoreCase = true) || it.contains("electronic", ignoreCase = true) } },
                // The reader's own collections ("Favorite") become their shelves.
                // What the reader wrote of this read: a review, or why it was given up.
                note = read?.optString("comment").orEmpty().trim(),
                tags = b.strings("collection_keys").mapNotNull { collections[it]?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString("\n"),
            )
        }
        return Import.Result(books, skipped, isbn)
    }

    private fun JSONObject.strings(key: String) = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

    /** A time in milliseconds, or null when there is none. */
    private fun JSONObject.time(key: String): Long? = if (isNull(key)) null else optLong(key).takeIf { it > 0 }
}
