package app.familiar.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import app.familiar.BuildConfig
import app.familiar.FamiliarApp
import app.familiar.AppContainer
import app.familiar.domain.Genre
import app.familiar.domain.Item
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.ui.pix.Look
import app.familiar.ui.pix.Px
import app.familiar.ui.pix.Room
import app.familiar.ui.pix.Sprites
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * The bridge to Lingvale, the village where the pets of all the -ling apps live together.
 *
 * Lingvale asks through ContentResolver.call() and nobody else gets an answer: the caller must be Lingvale signed
 * with its release key (a debug or demo build also answers a Lingvale built with the same debug key). It only reads;
 * nothing here changes the app's data. Protocol v1, the same in every -ling app:
 *  - "hello"    → {v}
 *  - "snapshot" → {json}: pets, species, days of activity, the last week's events
 *  - "art"      → {bytes}: one picture, painted here the way the app paints its familiars (pet_*, egg_*, den_bg)
 */
class VillageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!trusted(ctx)) throw SecurityException("Only Lingvale may ask")
        return when (method) {
            "hello" -> Bundle().apply { putInt("v", VERSION) }
            "snapshot" -> Bundle().apply { putString("json", runBlocking { snapshot(ctx, container(ctx)) }.toString()) }
            "art" -> art(ctx, arg)
            else -> null
        }
    }

    private fun trusted(ctx: Context): Boolean {
        val pkg = callingPackage ?: return false
        if (pkg != HUB && !pkg.startsWith("$HUB.")) return false
        val pm = ctx.packageManager
        if (HUB_CERTS.any { pm.hasSigningCertificate(pkg, it, PackageManager.CERT_INPUT_SHA256) }) return true
        return BuildConfig.DEBUG && pm.checkSignatures(pkg, ctx.packageName) == PackageManager.SIGNATURE_MATCH
    }

    private fun art(ctx: Context, name: String?): Bundle? {
        if (name == null || !ART.matches(name)) return null
        val bytes = paintedArt(ctx, name) ?: return null
        return Bundle().apply { putByteArray("bytes", bytes) }
    }

    /** The provider can be asked before the application has finished starting; wait for it a little. */
    private fun container(ctx: Context): AppContainer {
        repeat(100) {
            runCatching { return (ctx.applicationContext as FamiliarApp).container }
            Thread.sleep(30)
        }
        error("not ready")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val VERSION = 1
        const val HUB = "app.lingvale"
        /** SHA-256 of Lingvale's release certificate. */
        private val HUB_CERTS = listOf(hex("CEF40A1786896D829CFA53115693AC9A53FC0349EAB84BAF64EFA648592D1ABF"))
        private val ART = Regex("""(pet|egg|item|find)_[a-z0-9_]+|[a-z]+_bg""")
        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        private fun color(c: Long) = "#%06X".format(c and 0xFFFFFF)
        private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

        /** The den behind the familiar, as the share card paints it. */
        private const val SCENE = "den_bg"
        /** Familiars and eggs painted once; the den is painted afresh, since it follows the furniture and the hour. */
        private val cache = HashMap<String, ByteArray>()

        private fun idOf(sp: Species) = sp.name.lowercase(Locale.ROOT)
        private fun speciesOf(id: String) = Species.entries.firstOrNull { idOf(it) == id }

        /** Familiar's pictures are painted in code (ui/pix): a familiar of a kind at a stage, an egg, or the den. */
        private fun paintedArt(ctx: Context, name: String): ByteArray? {
            if (name == SCENE) return runCatching { den(ctx) }.getOrNull()
            synchronized(cache) { cache[name]?.let { return it } }
            val look = when {
                name.startsWith("egg_") -> {
                    val sp = speciesOf(name.removePrefix("egg_")) ?: return null
                    Look(sp, Stage.EGG, hatch = 0.5f)
                }
                name.startsWith("pet_") -> {
                    val sp = speciesOf(name.removePrefix("pet_").substringBeforeLast('_')) ?: return null
                    when (name.substringAfterLast('_')) {
                        "0" -> Look(sp, Stage.BABY)
                        "1" -> Look(sp, Stage.YOUNG)
                        "2", "3" -> Look(sp, Stage.ADULT)
                        "4" -> Look(sp, Stage.ANCIENT)
                        "5" -> Look(sp, Stage.ADULT, sleeping = true)
                        else -> return null
                    }
                }
                else -> return null
            }
            val bytes = runCatching { png(fitted(Sprites.frame(look, 0))) }.getOrNull() ?: return null
            synchronized(cache) { cache[name] = bytes }
            return bytes
        }

        /**
         * The familiar cut out of its 80-pixel frame (the faint ground shadow left out, as on the widget), set in
         * the middle of a square and scaled up without smoothing to about 256 pixels so the pixels stay crisp.
         */
        private fun fitted(px: Px): Bitmap {
            val w = px.fw; val h = px.fh
            val c = px.fine
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            for (y in 0 until h) for (x in 0 until w) if ((c[y * w + x] ushr 24) > 150) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            if (x1 < 0) { x0 = 0; y0 = 0; x1 = w - 1; y1 = h - 1 }
            val cw = x1 - x0 + 1; val ch = y1 - y0 + 1
            val side = maxOf(cw, ch) + 4
            val square = IntArray(side * side)
            val ox = (side - cw) / 2; val oy = (side - ch) / 2
            for (y in 0 until ch) for (x in 0 until cw) square[(oy + y) * side + ox + x] = c[(y0 + y) * w + x0 + x]
            val raw = Bitmap.createBitmap(square, side, side, Bitmap.Config.ARGB_8888)
            val k = (256 / side).coerceAtLeast(1)
            return if (k == 1) raw else Bitmap.createScaledBitmap(raw, side * k, side * k, false)
        }

        /** The den as it stands now: the furniture bought, by day or by night, no familiar in it. */
        private fun den(ctx: Context): ByteArray {
            val c = (ctx.applicationContext as FamiliarApp).container
            val (placed, books) = runBlocking {
                val s = c.settings.now()
                val finished = c.db.dao().books().filter { it.finished != null && it.shelfOf == app.familiar.data.Shelf.DONE }.sortedBy { it.finished }
                s.placed.mapNotNull { Item.of(it) }.toSet() to finished.map { Genre.of(it.genre).argb.toInt() }.takeLast(24)
            }
            val hour = LocalTime.now().hour
            val room = Room.scene(placed, books, hour < 6 || hour >= 20, Room.STEP, toys = false)
            Room.toys(placed, hour < 6 || hour >= 20, Room.STEP)?.let { room.blit(it, 0, 0) }
            val raw = Bitmap.createBitmap(room.fine, room.fw, room.fh, Bitmap.Config.ARGB_8888)
            return png(Bitmap.createScaledBitmap(raw, room.fw * 2, room.fh * 2, false))
        }

        private fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        /** A colour for each kind, close to how it is painted. */
        private val COLORS = mapOf(
            Species.MIMIC to 0x9A6A3C, Species.KITSUNE to 0xE0802E, Species.SLIME to 0x6CC46A, Species.DRAGON to 0xC2453A,
            Species.PHOENIX to 0xF0A030, Species.GRIFFIN to 0xC9A04A, Species.OWLBEAR to 0x8A6440, Species.MOTH to 0x8C7CC8,
            Species.TANUKI to 0x7A5A3E, Species.SPROUT to 0x5FA85A, Species.GHOST to 0xB8C8E8, Species.WYRM to 0x4A8A7A,
            Species.CHIMERA to 0xB5523E, Species.MAGPIE to 0x3A4A6A, Species.HYDRA to 0x3E8F6E, Species.BAYUN to 0x5C6CA0,
            Species.SNAIL to 0xC8A070, Species.SALAMANDER to 0xE05A2A, Species.AUTOMATON to 0xB08A3A, Species.PIXIE to 0xE47FA6,
            Species.RAVEN to 0x3A3550, Species.SPHINX to 0xD4B060, Species.OWLET to 0xA08060,
        )
        /** The hardest to meet. */
        private val RARE = setOf(Species.PHOENIX, Species.DRAGON, Species.GRIFFIN, Species.SPHINX)

        private fun inLocale(ctx: Context, lang: String): Context =
            ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { setLocale(Locale(lang)) })

        @Suppress("DiscouragedApi")
        private fun str(c: Context, name: String): String {
            val id = c.resources.getIdentifier(name, "string", c.packageName)
            return if (id == 0) "" else c.getString(id)
        }

        /** Everything Lingvale shows of Familiar: reading sessions as days, familiars by the mana they've eaten. */
        suspend fun snapshot(ctx: Context, c: AppContainer): JSONObject {
            val dao = c.db.dao()
            val pets = dao.pets()
            val readings = dao.readings().sortedBy { it.end }
            val books = dao.books().associateBy { it.id }
            val today = LocalDate.now().toEpochDay()
            val active = pets.firstOrNull { it.active } ?: pets.firstOrNull()
            val tags = AppCompatDelegate.getApplicationLocales()
            val ru = (if (tags.isEmpty) Locale.getDefault() else tags[0] ?: Locale.getDefault()).language == "ru"

            val petsJson = JSONArray()
            pets.forEach { p ->
                val stage = Stage.of(p.mana)
                // The day it hatched: when the mana eaten from readings first reached a baby's.
                val hatched = if (stage == Stage.EGG) 0L else {
                    var sum = 0
                    readings.filter { it.petId == p.id }.firstOrNull { sum += it.mana; sum >= Stage.BABY.from }?.let { dayOf(it.end) } ?: dayOf(p.joined)
                }
                petsJson.put(JSONObject()
                    .put("id", p.id).put("species", idOf(Species.of(p.species))).put("name", p.name)
                    .put("stage", stage.ordinal).put("progress", Stage.progress(p.mana).toDouble())
                    .put("slot", if (p.id == active?.id) 0 else -1).put("shiny", p.shiny)
                    .put("found", dayOf(p.joined)).put("hatched", hatched))
            }
            val ruCtx = inLocale(ctx, "ru")
            val enCtx = inLocale(ctx, "en")
            val speciesJson = JSONArray()
            Species.entries.forEach { s ->
                val key = s.name.lowercase(Locale.ROOT)
                speciesJson.put(JSONObject().put("id", idOf(s))
                    .put("ru", str(ruCtx, "sp_$key")).put("en", str(enCtx, "sp_$key"))
                    .put("color", color((COLORS[s] ?: 0x9A6A3C).toLong()))
                    .put("aboutRu", "любит " + str(ruCtx, "loves_$key")).put("aboutEn", "loves " + str(enCtx, "loves_$key"))
                    .put("rare", s in RARE))
            }
            val counted = readings.filter { it.counts }
            val daysJson = JSONArray()
            counted.groupBy { dayOf(it.start) }.toSortedMap().forEach { (d, l) ->
                if (d > today - 400) daysJson.put(JSONObject().put("d", d).put("n", l.size).put("min", l.filter { it.timed }.sumOf { it.minutes }))
            }
            val eventsJson = JSONArray()
            counted.filter { dayOf(it.start) > today - 8 }.forEach { r ->
                val title = books[r.bookId]?.title.orEmpty()
                val min = if (r.timed) r.minutes else 0
                val (lr, le) = if (r.pages > 0) "${r.pages} стр." to "${r.pages} p." else "$min мин" to "$min min"
                eventsJson.put(JSONObject().put("at", r.start).put("min", min)
                    .put("ru", "$title · $lr").put("en", "$title · $le"))
            }
            return JSONObject()
                .put("v", VERSION).put("app", "familiar").put("version", BuildConfig.VERSION_NAME)
                .put("ru", ru).put("scene", SCENE)
                .put("pets", petsJson).put("species", speciesJson).put("days", daysJson).put("events", eventsJson)
        }
    }
}
