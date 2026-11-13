package app.familiar.data

import app.familiar.domain.Feast
import app.familiar.domain.Finds
import app.familiar.domain.Form
import app.familiar.domain.Genre
import app.familiar.domain.Mana
import app.familiar.domain.Season
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.domain.Trait
import app.familiar.domain.Trinket
import app.familiar.domain.isMorning
import app.familiar.domain.isNight
import java.time.Instant
import java.time.ZoneId

/** The writes that feed the familiar: a sitting with a book, a guild reward. */
object Library {
    const val SHINY_ODDS = 16

    class Result(val reading: Reading, val feast: Feast, val petId: Long, val manaBefore: Int, val find: Trinket? = null)

    /** Records reading [book] up to [toPage] and feeds the active familiar. */
    suspend fun sit(
        dao: FamiliarDao,
        book: Book,
        fromPage: Int,
        toPage: Int,
        start: Long,
        end: Long,
        timed: Boolean,
        stillSec: Int = 0,
        listened: Boolean = false,
        item: String = "",
        finish: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
        /** Quiet days kept by guardian candles, part of the streak. */
        guarded: Set<java.time.LocalDate> = emptySet(),
    ): Result? {
        val pet = dao.activePet() ?: dao.pets().firstOrNull() ?: return null
        val sp = Species.of(pet.species)
        val to = toPage.coerceIn(0, book.cap)
        val done = finish || (book.pages > 0 && to >= book.pages)
        val past = dao.readings()
        val books = dao.books().associateBy { it.id }
        val previousGenre = past.filter { it.bookId != book.id }.maxByOrNull { it.end }?.let { books[it.bookId] }?.let { Genre.of(it.genre) }
        val lastEnd = past.filter { it.counts }.maxOfOrNull { it.end }
        val gapDays = lastEnd?.let { ((start - it) / 86_400_000L).toInt() } ?: 0
        val days = (past.filter { it.counts }.map { it.end } + end).map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet() + guarded
        var d = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        var streak = 0
        while (d in days) { streak++; d = d.minusDays(1) }
        val minutes = ((end - start) / 60_000).toInt()
        val feast = Mana.feast(
            sp,
            Mana.Sitting(
                pages = (to - fromPage).coerceAtLeast(0), timed = timed, minutes = minutes,
                stillFraction = if (end > start) stillSec * 1000f / (end - start) else 0f,
                night = isNight(start, zone), genre = Genre.of(book.genre), previousGenre = previousGenre,
                bookPages = book.pages, streak = streak, gapDays = gapDays, finishedBook = done && book.shelfOf != Shelf.DONE,
                item = item, listened = listened, morning = isMorning(start, zone), own = book.sub.isNotBlank(),
                ebook = book.ebook,
            ),
        )
        val reading = Reading(
            bookId = book.id, petId = pet.id, start = start, end = end, fromPage = fromPage, toPage = to, mana = feast.total,
            timed = timed, stillSec = stillSec, item = item.trim(), finishedBook = done && book.shelfOf != Shelf.DONE, listened = listened,
        )
        val id = dao.insertReading(reading)
        dao.updateBook(
            book.copy(
                page = if (done && book.pages > 0) book.pages else maxOf(book.page, to),
                shelf = if (done) Shelf.DONE.name else Shelf.READING.name,
                started = book.started ?: start,
                finished = if (done) (book.finished ?: end) else book.finished,
            )
        )
        dao.feed(pet.id, feast.total)
        if (pet.mana < Stage.ADULT.from && pet.mana + feast.total >= Stage.ADULT.from) settleForms(dao)
        // Now and then the familiar brings something back from the story.
        val day = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        val find = Finds.roll(
            seed = id * 7_919 + pet.id * 104_729 + start, amount = reading.amount, genre = Genre.of(book.genre),
            trait = Trait.of(pet.trait, pet.joined), season = Season.of(day), owned = dao.finds().mapNotNull { Trinket.of(it.kind) }.toSet(),
        )
        if (find != null) dao.insertFind(Find(kind = find.name, at = end, petId = pet.id, readingId = id))
        return Result(reading.copy(id = id), feast, pet.id, pet.mana, find)
    }

    /** Gives every grown familiar without a form the one its meals lean to. Forms never change once given. */
    suspend fun settleForms(dao: FamiliarDao) {
        val pets = dao.pets().filter { it.form.isBlank() && Stage.of(it.mana) >= Stage.ADULT }
        if (pets.isEmpty()) return
        val books = dao.books().associateBy { it.id }
        val readings = dao.readings()
        for (p in pets) dao.updatePet(p.copy(form = Form.choose(meals(p.id, readings, books)).name))
    }

    /** What a familiar ate, as the genre of each sitting's book and its mana. */
    fun meals(petId: Long, readings: List<Reading>, books: Map<Long, Book>): List<Pair<Genre, Int>> =
        readings.filter { it.petId == petId && it.mana > 0 }.map { Genre.of(books[it.bookId]?.genre ?: "") to it.mana }

    /** A guest leaves a little gift, once a day; returns it, or null if it already left one today. */
    suspend fun guestGift(dao: FamiliarDao, seed: Long, now: Long = System.currentTimeMillis()): Trinket? {
        val pet = dao.activePet() ?: return null
        val owned = dao.finds().mapNotNull { Trinket.of(it.kind) }.toSet()
        val pool = Trinket.entries.filter { it.common }
        val fresh = pool.filter { it !in owned }.ifEmpty { pool }
        val t = fresh[Math.floorMod(seed, fresh.size.toLong()).toInt()]
        dao.insertFind(Find(kind = t.name, at = now, petId = pet.id))
        return t
    }

    suspend fun reward(dao: FamiliarDao, mana: Int) {
        dao.activePet()?.let { dao.feed(it.id, mana) }
        settleForms(dao)
    }

    /** Brings a familiar into the den (as an egg) and makes it the one you read with. */
    suspend fun adopt(dao: FamiliarDao, sp: Species, name: String, now: Long = System.currentTimeMillis()): Long {
        val existing = dao.pets().firstOrNull { it.species == sp.name }
        // One egg in sixteen hatches in a rare colouring.
        val id = existing?.id ?: dao.insertPet(
            Pet(species = sp.name, name = name, joined = now, shiny = kotlin.random.Random.nextInt(SHINY_ODDS) == 0, trait = Trait.entries.random().name)
        )
        dao.activate(id)
        return id
    }
}
