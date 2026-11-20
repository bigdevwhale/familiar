package app.familiar.domain

import app.familiar.data.Book
import app.familiar.data.Reading
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import kotlin.random.Random

/** Days of the year when the den dresses up. Each comes back every year with its own find. */
enum class Season(private val from: MonthDay, private val to: MonthDay) {
    NONE(MonthDay.of(1, 1), MonthDay.of(1, 1)),
    SAMHAIN(MonthDay.of(10, 24), MonthDay.of(11, 2)),
    WINTER(MonthDay.of(12, 20), MonthDay.of(1, 7)),
    VALENTINE(MonthDay.of(2, 12), MonthDay.of(2, 15)),
    BOOKDAY(MonthDay.of(4, 21), MonthDay.of(4, 25)),
    MIDSUMMER(MonthDay.of(6, 20), MonthDay.of(6, 24));

    private fun has(d: MonthDay): Boolean = if (from <= to) d in from..to else d >= from || d <= to

    val trinket: Trinket? get() = Trinket.entries.firstOrNull { it.season == this }

    companion object {
        fun of(day: LocalDate): Season = entries.firstOrNull { it != NONE && it.has(MonthDay.from(day)) } ?: NONE
    }
}

/** Little things a familiar brings back from a story. Some come from one genre, some only in a season. */
enum class Trinket(val genre: Genre? = null, val season: Season? = null) {
    BOOKMARK, ACORN, BUTTON, CANDLE_STUB, TEACUP, DICE, RIBBON, SEASHELL, BELL, MOON_PEBBLE,
    DRAGON_SCALE(Genre.FANTASY), GEAR(Genre.SCIFI), MAGNIFIER(Genre.MYSTERY), SPIDER(Genre.HORROR), LOVE_LETTER(Genre.ROMANCE),
    COMPASS(Genre.ADVENTURE), QUILL(Genre.CLASSIC), OLD_COIN(Genre.HISTORY), SPECTACLES(Genre.NONFICTION), PRESSED_FLOWER(Genre.POETRY),
    PUMPKIN(season = Season.SAMHAIN), SNOW_GLOBE(season = Season.WINTER), VALENTINE(season = Season.VALENTINE),
    FIRST_EDITION(season = Season.BOOKDAY), FIREFLY_JAR(season = Season.MIDSUMMER);

    val common: Boolean get() = genre == null && season == null

    companion object {
        fun of(name: String): Trinket? = entries.firstOrNull { it.name == name }
    }
}

object Finds {
    /** A sitting of at least this many pages or minutes may bring something back. */
    const val MIN_AMOUNT = 5
    const val CHANCE = 0.35f

    /**
     * What a familiar brings back from a sitting, or null. Decided by [seed] (the sitting itself),
     * so the same sitting always brings the same thing. A new thing is likelier than one you have.
     */
    fun roll(seed: Long, amount: Int, genre: Genre, trait: Trait, season: Season, owned: Set<Trinket>): Trinket? {
        if (amount < MIN_AMOUNT) return null
        val rnd = Random(seed)
        val chance = CHANCE + (if (trait == Trait.CURIOUS) 0.15f else 0f) + (if (season != Season.NONE) 0.1f else 0f)
        if (rnd.nextFloat() >= chance) return null
        season.trinket?.let { if (rnd.nextFloat() < 0.5f) return it }
        val ofGenre = Trinket.entries.filter { it.genre == genre }
        val pool = if (ofGenre.isNotEmpty() && rnd.nextFloat() < 0.6f) ofGenre else Trinket.entries.filter { it.common }
        val fresh = pool.filter { it !in owned }
        val from = if (fresh.isNotEmpty() && rnd.nextFloat() < 0.7f) fresh else pool
        return from[rnd.nextInt(from.size)]
    }
}

/** Dreams after an evening of reading: the familiar dreams of the book read most the day before. */
object Dreams {
    /** The familiar wakes at six (it sleeps before that in the den): a night runs from waking to waking. */
    const val WAKE = 6
    /** The dream waits until evening, so a night owl who reads past midnight and sleeps late still sees it. */
    const val UNTIL = 18

    /** The genre dreamt of today (6 to 18 o'clock), or null when there is nothing to dream. */
    fun of(readings: List<Reading>, books: List<Book>, now: Long, zone: ZoneId): Genre? {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        if (t.hour !in WAKE until UNTIL) return null
        val today = t.toLocalDate()
        // Yesterday's day and the night after it, past midnight up to waking.
        val from = today.minusDays(1).atTime(WAKE, 0).atZone(zone).toInstant().toEpochMilli()
        val to = today.atTime(WAKE, 0).atZone(zone).toInstant().toEpochMilli()
        val byId = books.associateBy { it.id }
        return readings.filter { it.counts && it.end in from until to }
            .groupBy { Genre.of(byId[it.bookId]?.genre ?: "") }
            .maxByOrNull { (_, l) -> l.sumOf { it.amount } }?.key
    }
}

/** Guardian candles: bought for sparks, each one keeps the streak through one quiet day. */
object Guard {
    const val PRICE = 200
    const val MAX = 2

    /**
     * Quiet days to burn candles for: those between the last day of the chain and today, when the
     * candles cover all of them. A gap too long for the candles is left alone, so none are wasted.
     */
    fun toBurn(readDays: Set<LocalDate>, guarded: Set<LocalDate>, candles: Int, today: LocalDate): List<LocalDate> {
        if (candles <= 0) return emptyList()
        val last = (readDays + guarded).filter { it < today }.maxOrNull() ?: return emptyList()
        val missed = generateSequence(last.plusDays(1)) { it.plusDays(1) }.takeWhile { it < today }.toList()
        return if (missed.isNotEmpty() && missed.size <= candles) missed else emptyList()
    }
}

/** The closest little goal, to say "only N more pages until…". */
class Nudge(val goal: Goal, val left: Int) {
    enum class Goal { FED, HATCH, STAGE, BOOK, ITEM }

    companion object {
        /**
         * The nearest of: a full familiar today, hatching or the next stage, the end of the book,
         * the cheapest piece for the den. [extra] pages are counted as already read (in a ritual).
         * A page is about a mana and a spark.
         */
        fun next(s: Stats, sparks: Int, cheapest: Int?, book: Book?, extra: Int = 0): Nudge? {
            val mana = (s.active?.mana ?: return null) + extra
            val out = mutableListOf<Nudge>()
            val goal = s.dailyGoal
            if (goal > 0 && s.todayPages + extra < goal) out += Nudge(Goal.FED, goal - s.todayPages - extra)
            Stage.of(mana).next?.let { n -> (n.from - mana).takeIf { it in 1..80 }?.let { out += Nudge(if (n == Stage.BABY) Goal.HATCH else Goal.STAGE, it) } }
            if (book != null && book.pages > 0 && !book.audio) (book.pages - book.page - extra).takeIf { it in 1..50 }?.let { out += Nudge(Goal.BOOK, it) }
            cheapest?.let { p -> (p - sparks - extra).takeIf { it in 1..80 }?.let { out += Nudge(Goal.ITEM, it) } }
            return out.minByOrNull { it.left }
        }
    }
}

/** A familiar's visiting card, passed from phone to phone as a QR code. */
data class Visit(
    val species: Species,
    val name: String,
    val mana: Int,
    val shiny: Boolean,
    val form: Form,
    val trait: Trait,
    /** Who sent it. */
    val reader: String,
    /** A book the guest recommends, or blank. */
    val title: String,
    val author: String,
    val genre: Genre,
) {
    fun encode(): String {
        val fields = listOf(species.name, name, mana.toString(), if (shiny) "1" else "0", form.name, trait.name, reader, title, author, genre.name)
        val body = fields.joinToString(SEP) { it.replace(SEP, " ") }
        return PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray(Charsets.UTF_8))
    }

    /** Same familiar from the same reader: one gift a day per guest. */
    val key: String get() = "$reader|$species|$name".lowercase()

    companion object {
        private const val PREFIX = "FAMILIAR1:"
        private const val SEP = "\u001F"

        /** Reads a card, or null when the text is not one. */
        fun decode(text: String): Visit? = runCatching {
            val t = text.trim()
            if (!t.startsWith(PREFIX)) return null
            val f = String(java.util.Base64.getUrlDecoder().decode(t.removePrefix(PREFIX)), Charsets.UTF_8).split(SEP)
            if (f.size < 10) return null
            Visit(
                Species.entries.firstOrNull { it.name == f[0] } ?: return null, f[1].take(24), f[2].toInt().coerceIn(0, 1_000_000), f[3] == "1",
                Form.of(f[4]), Trait.of(f[5], 0), f[6].take(30), f[7].take(120), f[8].take(80), Genre.of(f[9]),
            )
        }.getOrNull()
    }
}
