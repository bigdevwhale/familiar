package app.familiar.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import app.familiar.ui.Backdrops
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/** Plain JSON backup: everything stays on the device unless the user exports it. */
object Backup {
    private const val VERSION = 1

    /**
     * Writes the whole backup file, the same for the settings' «Save» and for Lingvale: the library, the pictures, the
     * settings and the reader's own backdrop. The pictures go out one at a time, so a big library doesn't have to fit in memory.
     */
    suspend fun write(context: Context, dao: FamiliarDao, store: SettingsStore, out: OutputStream) {
        val s = dao.snapshot()
        val body = body(s, store.export())
        JsonWriter(out.bufferedWriter()).use { w ->
            w.beginObject()
            body.keys().forEach { k -> w.name(k); w.any(body.get(k)) }
            w.name("covers").beginObject()
            s.books.flatMap { listOf(it.cover, it.spine) }.filter { it.isNotBlank() }.distinct().forEach { url ->
                Covers.bytes(context, url)?.let { w.name(url).value(Base64.getEncoder().encodeToString(it)) }
            }
            w.endObject()
            Backdrops.ownFile(context).takeIf { it.exists() }?.readBytes()?.let { w.name("backdrop").value(Base64.getEncoder().encodeToString(it)) }
            w.endObject()
        }
    }

    /**
     * Replaces what is on the phone with the backup read from [input]; throws [IllegalArgumentException], touching
     * nothing, when it is not a Familiar backup. The pictures wait in the cache until the whole file has been read.
     * A file from before the settings travelled leaves the settings as they are.
     */
    suspend fun read(context: Context, dao: FamiliarDao, store: SettingsStore, input: InputStream) {
        val stage = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            val root = JSONObject(); val pictures = LinkedHashMap<String, File>(); var backdrop: File? = null
            val parsed = runCatching {
                JsonReader(input.bufferedReader()).use { r ->
                    r.isLenient = true
                    r.beginObject()
                    while (r.hasNext()) when (val k = r.nextName()) {
                        "covers" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                val url = r.nextName(); val text = r.nextString()
                                runCatching { File(stage, "c${pictures.size}").also { it.writeBytes(Base64.getDecoder().decode(text)) } }.onSuccess { pictures[url] = it }
                            }
                            r.endObject()
                        }
                        "backdrop" -> { val text = r.nextString(); backdrop = runCatching { File(stage, "backdrop").also { it.writeBytes(Base64.getDecoder().decode(text)) } }.getOrNull() }
                        else -> root.put(k, r.any() ?: JSONObject.NULL)
                    }
                    r.endObject()
                }
            }
            require(parsed.isSuccess && root.optString("app") == "familiar") { "Not a Familiar backup" }
            val books = snapshot(root, emptyMap())   // read in full before a picture is put in place
            val moved = pictures.mapValues { (url, f) -> Covers.restore(context, url, f.readBytes()) }
            dao.replaceAll(Snapshot(books.books.map { b -> b.copy(cover = moved[b.cover] ?: b.cover, spine = moved[b.spine] ?: b.spine) }, books.readings, books.pets, books.finds, books.quotes))
            root.optJSONObject("settings")?.let { store.import(it) }
            backdrop?.let { f ->
                f.copyTo(Backdrops.ownFile(context), overwrite = true)
                // a fresh stamp, so the screens read the picture from the file again
                store.update { it.copy(backdropStamp = System.currentTimeMillis()) }
            }
        } finally { stage.deleteRecursively() }
    }

    /** [write] into a text, for a library small enough to hold in memory. */
    suspend fun save(context: Context, dao: FamiliarDao, store: SettingsStore): String =
        ByteArrayOutputStream().also { write(context, dao, store, it) }.toString("UTF-8")

    /** [read] from a text. */
    suspend fun load(context: Context, dao: FamiliarDao, store: SettingsStore, text: String) = read(context, dao, store, text.byteInputStream())

    private fun JsonWriter.any(v: Any?) {
        when (v) {
            null, JSONObject.NULL -> nullValue()
            is JSONObject -> { beginObject(); v.keys().forEach { name(it); any(v.get(it)) }; endObject() }
            is JSONArray -> { beginArray(); for (i in 0 until v.length()) any(v.get(i)); endArray() }
            is Boolean -> value(v)
            is Number -> value(v)
            else -> value(v.toString())
        }
    }

    private fun JsonReader.any(): Any? = when (peek()) {
        JsonToken.BEGIN_OBJECT -> JSONObject().also { o -> beginObject(); while (hasNext()) o.put(nextName(), any() ?: JSONObject.NULL); endObject() }
        JsonToken.BEGIN_ARRAY -> JSONArray().also { a -> beginArray(); while (hasNext()) a.put(any() ?: JSONObject.NULL); endArray() }
        JsonToken.BOOLEAN -> nextBoolean()
        JsonToken.NUMBER -> nextString().let { n -> n.toLongOrNull() ?: n.toDouble() }
        JsonToken.NULL -> { nextNull(); null }
        else -> nextString()
    }

    /**
     * [picture] gives the bytes of a cover kept on the phone, so the backup carries the pictures too.
     * [settings] is [SettingsStore.export], [backdrop] the reader's own backdrop picture.
     */
    fun encode(s: Snapshot, settings: JSONObject? = null, backdrop: ByteArray? = null, picture: (String) -> ByteArray? = { null }): String = body(s, settings)
        .put("covers", JSONObject().apply {
            s.books.flatMap { listOf(it.cover, it.spine) }.filter { it.isNotBlank() }.distinct().forEach { url ->
                picture(url)?.let { put(url, Base64.getEncoder().encodeToString(it)) }
            }
        })
        .apply { if (backdrop != null) put("backdrop", Base64.getEncoder().encodeToString(backdrop)) }
        .toString(2)

    /** Everything of the file but the pictures. */
    private fun body(s: Snapshot, settings: JSONObject?): JSONObject = JSONObject()
        .put("app", "familiar")
        .put("version", VERSION)
        .put("books", JSONArray(s.books.map {
            JSONObject().put("id", it.id).put("title", it.title).put("author", it.author).put("pages", it.pages).put("genre", it.genre)
                .put("shelf", it.shelf).put("page", it.page).put("added", it.added).put("started", it.started ?: JSONObject.NULL)
                .put("finished", it.finished ?: JSONObject.NULL).put("rating", it.rating ?: JSONObject.NULL).put("cover", it.cover).put("audio", it.audio).put("series", it.series).put("volume", it.volume ?: JSONObject.NULL).put("tags", it.tags).put("sub", it.sub).put("note", it.note).put("ebook", it.ebook).put("pose", it.pose).put("spine", it.spine).put("place", it.place).put("reread", it.reread).put("tall", it.tall).put("thick", it.thick)
        }))
        .put("readings", JSONArray(s.readings.map {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("petId", it.petId).put("start", it.start).put("end", it.end)
                .put("fromPage", it.fromPage).put("toPage", it.toPage).put("mana", it.mana).put("timed", it.timed)
                .put("stillSec", it.stillSec).put("item", it.item).put("finishedBook", it.finishedBook).put("listened", it.listened).put("ambience", it.ambience)
        }))
        .put("pets", JSONArray(s.pets.map {
            JSONObject().put("id", it.id).put("species", it.species).put("name", it.name).put("joined", it.joined).put("mana", it.mana).put("active", it.active).put("shiny", it.shiny)
                .put("trait", it.trait).put("form", it.form)
        }))
        .put("finds", JSONArray(s.finds.map {
            JSONObject().put("id", it.id).put("kind", it.kind).put("at", it.at).put("petId", it.petId).put("readingId", it.readingId)
        }))
        .put("quotes", JSONArray(s.quotes.map {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("text", it.text).put("page", it.page).put("at", it.at)
        }))
        .apply { if (settings != null) put("settings", settings) }

    /**
     * Throws [IllegalArgumentException] when the text is not a Familiar backup. [restore] puts each cover picture
     * the backup carries back on the phone and returns the address the book should use from now on.
     */
    fun decode(text: String, restore: (String, ByteArray) -> String = { url, _ -> url }): Snapshot {
        val root = root(text)
        val pictures = root.optJSONObject("covers")
        val moved = pictures?.keys()?.asSequence()?.mapNotNull { url ->
            runCatching { url to restore(url, Base64.getDecoder().decode(pictures.getString(url))) }.getOrNull()
        }?.toMap().orEmpty()
        return snapshot(root, moved)
    }

    private fun root(text: String): JSONObject {
        val root = runCatching { JSONObject(text) }.getOrNull()
        require(root != null && root.optString("app") == "familiar") { "Not a Familiar backup" }
        return root
    }

    /** [moved]: where each picture of the file lives on this phone now. */
    private fun snapshot(root: JSONObject, moved: Map<String, String>): Snapshot {
        return Snapshot(
            books = root.list("books") {
                Book(
                    id = getLong("id"), title = getString("title"), author = optString("author"), pages = getInt("pages"), genre = getString("genre"),
                    shelf = optString("shelf", Shelf.READING.name), page = optInt("page"), added = getLong("added"),
                    started = optL("started"), finished = optL("finished"), rating = optI("rating"), cover = optString("cover").let { moved[it] ?: it }, audio = optBoolean("audio"), series = optString("series"), volume = optI("volume"), tags = optString("tags"), sub = optString("sub"), note = optString("note"), ebook = optBoolean("ebook"),
                    pose = optString("pose"), place = optInt("place"), spine = optString("spine").let { moved[it] ?: it }, reread = optInt("reread"), tall = optInt("tall"), thick = optInt("thick"),
                )
            },
            readings = root.list("readings") {
                Reading(
                    id = getLong("id"), bookId = getLong("bookId"), petId = getLong("petId"), start = getLong("start"), end = getLong("end"),
                    fromPage = getInt("fromPage"), toPage = getInt("toPage"), mana = getInt("mana"), timed = optBoolean("timed"),
                    stillSec = optInt("stillSec"), item = optString("item"), finishedBook = optBoolean("finishedBook"), listened = optBoolean("listened"), ambience = optString("ambience"),
                )
            },
            pets = root.list("pets") { Pet(getLong("id"), getString("species"), getString("name"), getLong("joined"), getInt("mana"), optBoolean("active"), optBoolean("shiny"), optString("trait"), optString("form")) },
            finds = root.list("finds") { Find(getLong("id"), getString("kind"), getLong("at"), optLong("petId"), optLong("readingId")) },
            quotes = root.list("quotes") { Quote(getLong("id"), getLong("bookId"), getString("text"), optInt("page"), getLong("at")) },
        )
    }

    private fun JSONObject.optL(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optI(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

    private fun <T> JSONObject.list(key: String, read: JSONObject.() -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).read() }
    }
}
