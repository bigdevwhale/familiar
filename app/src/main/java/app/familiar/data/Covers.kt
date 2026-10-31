package app.familiar.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Finds books by title in open catalogues and keeps their covers on the phone. This is the only
 * thing in the app that uses the internet: Fantlab for books typed in Cyrillic (it knows Russian
 * titles and translations), Open Library for the rest, each filling in for the other, and Google
 * Books when neither finds anything.
 */
object Covers {
    class Found(val title: String, val author: String, val pages: Int?, val year: Int?, val thumb: String, val cover: String)

    private fun get(url: String): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 12_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Familiar/1.0 (Android reading app)")
        try {
            if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    suspend fun search(query: String): List<Found>? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val cyrillic = query.any { it in 'Ѐ'..'ӿ' }
        // Both catalogues at once; which one leads depends on the alphabet of the query.
        val fan = async { fantlab(query) }
        val open = async { openLibrary(query) }
        val first = if (cyrillic) fan.await() else open.await()
        val second = if (cyrillic) open.await() else fan.await()
        val found = (first.orEmpty() + second.orEmpty().take(if (cyrillic) 4 else 6))
            .distinctBy { it.title.lowercase().trim() to it.author.lowercase().trim() }
        if (found.any { it.cover.isNotEmpty() }) return@withContext found
        val google = google(query)
        when {
            !google.isNullOrEmpty() -> (found + google).distinctBy { it.title.lowercase() to it.author.lowercase() }
            first == null && second == null -> null
            else -> found
        }
    }

    /**
     * A cover for a book that came without one: Open Library's for its [isbn] if it has one, else the first
     * catalogue match whose title is the book's. Null when nothing fits.
     */
    suspend fun find(title: String, author: String, isbn: String = ""): String? = withContext(Dispatchers.IO) {
        if (isbn.isNotEmpty()) {
            // default=false makes Open Library answer 404 instead of a blank picture.
            val url = "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"
            if (get(url) != null) return@withContext url
        }
        // Store titles carry "(Russian Edition)" or a subtitle in brackets that no catalogue knows.
        val plain = title.replace(Regex("""\s*\([^()]*\)\s*$"""), "").trim().ifEmpty { title }
        val name = plain.lowercase().filter { it.isLetterOrDigit() }
        fun List<Found>?.fitting() = this?.firstOrNull { f ->
            val other = f.title.lowercase().filter { it.isLetterOrDigit() }
            f.cover.isNotEmpty() && name.isNotEmpty() && other.isNotEmpty() && (other.startsWith(name) || name.startsWith(other))
        }?.cover
        // Title and author asked apart find the book itself rather than box sets; the author in another
        // alphabet than the title (Boris Akunin) is why the title alone comes last.
        openLibrary(plain, author).fitting()
            ?: search("$plain $author".trim()).fitting()
            ?: search(plain).fitting()
    }

    /** Kinds of Fantlab works that are books one reads, not poems, articles or reviews. */
    private val BOOKS = setOf("novel", "story", "collection", "epic", "popular", "monography", "documental", "anthology", "graphicnovel", "comix", "tale", "reference")

    /** Fantlab: Russian books and translations, with Russian titles. Pages come from a printed edition. */
    private suspend fun fantlab(q: String): List<Found>? = coroutineScope {
        val body = get("https://api.fantlab.ru/search-works?q=${enc(q)}&page=1&onlymatches=1") ?: return@coroutineScope null
        val arr = runCatching { org.json.JSONArray(body.decodeToString()) }.getOrNull() ?: return@coroutineScope null
        val works = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("name_eng") in BOOKS }
            .filter { (it.optString("rusname").ifBlank { it.optString("name") }).isNotBlank() }
            // Popular works first; Fantlab's own order already puts exact matches high.
            .sortedByDescending { if (it.optInt("markcount") > 0) 1 else 0 }
            .take(10)
        works.map { w ->
            async {
                val pic = w.optLong("pic_edition_id").takeIf { it > 0 } ?: w.optLong("pic_edition_id_auto")
                val pages = if (pic > 0) get("https://api.fantlab.ru/edition/$pic")
                    ?.let { b -> runCatching { JSONObject(b.decodeToString()).optInt("pages", 0) }.getOrNull() }?.takeIf { it > 0 } else null
                Found(
                    title = w.optString("rusname").ifBlank { w.optString("name") }.trim(),
                    author = w.optString("all_autor_rusname").ifBlank { w.optString("all_autor_name") }.trim(),
                    pages = pages,
                    year = w.optInt("year", 0).takeIf { it > 0 },
                    thumb = if (pic > 0) "https://fantlab.ru/images/editions/small/$pic" else "",
                    cover = if (pic > 0) "https://fantlab.ru/images/editions/big/$pic" else "",
                )
            }
        }.awaitAll()
    }

    private fun openLibrary(q: String): List<Found>? = openLibraryAt("q=${enc(q)}")

    private fun openLibrary(title: String, author: String): List<Found>? =
        openLibraryAt("title=${enc(title)}" + if (author.isNotBlank()) "&author=${enc(author)}" else "")

    private fun openLibraryAt(query: String): List<Found>? {
        val body = get("https://openlibrary.org/search.json?$query&limit=12&fields=title,author_name,number_of_pages_median,cover_i,first_publish_year")
            ?: return null
        val docs = runCatching { JSONObject(body.decodeToString()).getJSONArray("docs") }.getOrNull() ?: return null
        return (0 until docs.length()).map { docs.getJSONObject(it) }.map { d ->
            val id = d.optLong("cover_i", 0)
            Found(
                title = d.optString("title"),
                author = d.optJSONArray("author_name")?.optString(0).orEmpty(),
                pages = d.optInt("number_of_pages_median", 0).takeIf { it > 0 },
                year = d.optInt("first_publish_year", 0).takeIf { it > 0 },
                thumb = if (id > 0) "https://covers.openlibrary.org/b/id/$id-M.jpg" else "",
                cover = if (id > 0) "https://covers.openlibrary.org/b/id/$id-L.jpg" else "",
            )
        }.filter { it.title.isNotBlank() }
    }

    private fun google(q: String): List<Found>? {
        val body = get("https://www.googleapis.com/books/v1/volumes?q=${enc(q)}&maxResults=12&printType=books") ?: return null
        val items = runCatching { JSONObject(body.decodeToString()).optJSONArray("items") }.getOrNull() ?: return emptyList()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optJSONObject("volumeInfo") }.map { v ->
            val img = v.optJSONObject("imageLinks")?.optString("thumbnail").orEmpty().replace("http://", "https://")
            Found(
                title = v.optString("title"),
                author = v.optJSONArray("authors")?.optString(0).orEmpty(),
                pages = v.optInt("pageCount", 0).takeIf { it > 0 },
                year = v.optString("publishedDate").take(4).toIntOrNull(),
                thumb = img,
                cover = img,
            )
        }.filter { it.title.isNotBlank() }
    }

    private fun file(context: Context, url: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "covers").apply { mkdirs() }, "$h.jpg")
    }

    /** Covers picked from the gallery are stored as files and referred to like this. */
    const val LOCAL = "file://"

    /** Copies a picture the user picked into the app, scaled down to a cover; returns its address. */
    suspend fun importPicked(context: Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 900) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val out = File(dir, "own-${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            LOCAL + out.path
        }.getOrNull()
    }

    /** The picture of a cover as it lies on the phone, to go into a backup; null if it isn't there. */
    fun bytes(context: Context, url: String): ByteArray? {
        if (url.isBlank()) return null
        val f = if (url.startsWith(LOCAL)) File(url.removePrefix(LOCAL)) else file(context, url)
        return f.takeIf { it.exists() }?.readBytes()
    }

    /** Puts a picture from a backup back on the phone; returns the address the book should use now. */
    fun restore(context: Context, url: String, bytes: ByteArray): String {
        val f = if (url.startsWith(LOCAL)) {
            // The old path may belong to another phone or install; keep only the name.
            File(File(context.filesDir, "covers").apply { mkdirs() }, File(url.removePrefix(LOCAL)).name)
        } else file(context, url)
        f.writeBytes(bytes)
        val now = if (url.startsWith(LOCAL)) LOCAL + f.path else url
        synchronized(memory) { memory.remove(url); memory.remove(now) }
        return now
    }

    private val memory = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 40
    }

    fun cached(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    /** The cover from the phone, downloading it once if needed; null if there's none. */
    suspend fun load(context: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        cached(url)?.let { return@withContext it }
        val f = if (url.startsWith(LOCAL)) File(url.removePrefix(LOCAL)) else file(context, url)
        if (url.startsWith(LOCAL) && !f.exists()) return@withContext null
        if (!f.exists()) {
            val bytes = get(url) ?: return@withContext null
            // Open Library answers with a 1-pixel GIF when it has no picture.
            if (bytes.size < 1000) return@withContext null
            f.writeBytes(bytes)
        }
        val bmp = BitmapFactory.decodeFile(f.path)?.asImageBitmap() ?: return@withContext null
        synchronized(memory) { memory[url] = bmp }
        bmp
    }
}
