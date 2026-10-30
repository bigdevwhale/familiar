package app.familiar.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Plain JSON backup: everything stays on the device unless the user exports it. */
object Backup {
    private const val VERSION = 1

    /** [picture] gives the bytes of a cover kept on the phone, so the backup carries the pictures too. */
    fun encode(s: Snapshot, picture: (String) -> ByteArray? = { null }): String = JSONObject()
        .put("app", "familiar")
        .put("version", VERSION)
        .put("books", JSONArray(s.books.map {
            JSONObject().put("id", it.id).put("title", it.title).put("author", it.author).put("pages", it.pages).put("genre", it.genre)
                .put("shelf", it.shelf).put("page", it.page).put("added", it.added).put("started", it.started ?: JSONObject.NULL)
                .put("finished", it.finished ?: JSONObject.NULL).put("rating", it.rating ?: JSONObject.NULL).put("cover", it.cover).put("audio", it.audio).put("series", it.series).put("volume", it.volume ?: JSONObject.NULL).put("tags", it.tags).put("sub", it.sub)
        }))
        .put("readings", JSONArray(s.readings.map {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("petId", it.petId).put("start", it.start).put("end", it.end)
                .put("fromPage", it.fromPage).put("toPage", it.toPage).put("mana", it.mana).put("timed", it.timed)
                .put("stillSec", it.stillSec).put("item", it.item).put("finishedBook", it.finishedBook).put("listened", it.listened)
        }))
        .put("pets", JSONArray(s.pets.map {
            JSONObject().put("id", it.id).put("species", it.species).put("name", it.name).put("joined", it.joined).put("mana", it.mana).put("active", it.active).put("shiny", it.shiny)
                .put("trait", it.trait).put("form", it.form)
        }))
        .put("finds", JSONArray(s.finds.map {
            JSONObject().put("id", it.id).put("kind", it.kind).put("at", it.at).put("petId", it.petId).put("readingId", it.readingId)
        }))
        .put("covers", JSONObject().apply {
            s.books.map { it.cover }.filter { it.isNotBlank() }.distinct().forEach { url ->
                picture(url)?.let { put(url, Base64.getEncoder().encodeToString(it)) }
            }
        })
        .toString(2)

    /**
     * Throws [IllegalArgumentException] when the text is not a Familiar backup. [restore] puts each cover picture
     * the backup carries back on the phone and returns the address the book should use from now on.
     */
    fun decode(text: String, restore: (String, ByteArray) -> String = { url, _ -> url }): Snapshot {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "familiar") { "Not a Familiar backup" }
        val pictures = root.optJSONObject("covers")
        val moved = pictures?.keys()?.asSequence()?.mapNotNull { url ->
            runCatching { url to restore(url, Base64.getDecoder().decode(pictures.getString(url))) }.getOrNull()
        }?.toMap().orEmpty()
        return Snapshot(
            books = root.list("books") {
                Book(
                    id = getLong("id"), title = getString("title"), author = optString("author"), pages = getInt("pages"), genre = getString("genre"),
                    shelf = optString("shelf", Shelf.READING.name), page = optInt("page"), added = getLong("added"),
                    started = optL("started"), finished = optL("finished"), rating = optI("rating"), cover = optString("cover").let { moved[it] ?: it }, audio = optBoolean("audio"), series = optString("series"), volume = optI("volume"), tags = optString("tags"), sub = optString("sub"),
                )
            },
            readings = root.list("readings") {
                Reading(
                    id = getLong("id"), bookId = getLong("bookId"), petId = getLong("petId"), start = getLong("start"), end = getLong("end"),
                    fromPage = getInt("fromPage"), toPage = getInt("toPage"), mana = getInt("mana"), timed = optBoolean("timed"),
                    stillSec = optInt("stillSec"), item = optString("item"), finishedBook = optBoolean("finishedBook"), listened = optBoolean("listened"),
                )
            },
            pets = root.list("pets") { Pet(getLong("id"), getString("species"), getString("name"), getLong("joined"), getInt("mana"), optBoolean("active"), optBoolean("shiny"), optString("trait"), optString("form")) },
            finds = root.list("finds") { Find(getLong("id"), getString("kind"), getLong("at"), optLong("petId"), optLong("readingId")) },
        )
    }

    private fun JSONObject.optL(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optI(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

    private fun <T> JSONObject.list(key: String, read: JSONObject.() -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).read() }
    }
}
