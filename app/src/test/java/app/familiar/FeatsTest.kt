package app.familiar

import app.familiar.content.FeatNames
import app.familiar.data.Book
import app.familiar.data.Pet
import app.familiar.data.Quote
import app.familiar.data.Reading
import app.familiar.data.Ritual
import app.familiar.data.Shelf
import app.familiar.domain.DenState
import app.familiar.domain.Feat
import app.familiar.domain.Feats
import app.familiar.domain.Genre
import app.familiar.domain.Mana
import app.familiar.domain.Part
import app.familiar.domain.Sky
import app.familiar.domain.Species
import app.familiar.domain.Stats
import app.familiar.domain.Unlocks
import app.familiar.domain.Wrapped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The nine familiars that came last, the achievements, quotes and the year looked back on. */
class FeatsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 14)
    private val now = today.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
    private fun at(daysAgo: Int, hour: Int = 20) = today.minusDays(daysAgo.toLong()).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val pet = Pet(id = 1, species = Species.MIMIC.name, name = "Chesty", joined = at(100), active = true)

    private fun book(id: Long, genre: Genre = Genre.FANTASY, pages: Int = 300, done: Int? = null) =
        Book(id = id, title = "B$id", pages = pages, genre = genre.name, shelf = if (done != null) Shelf.DONE.name else Shelf.READING.name, added = 0, finished = done?.let { at(it) })

    private fun read(daysAgo: Int, book: Long = 1, pages: Int = 20, hour: Int = 20, still: Int = 0, ambience: String = "", finished: Boolean = false) =
        Reading(bookId = book, petId = 1, start = at(daysAgo, hour), end = at(daysAgo, hour) + 30 * 60_000L, fromPage = 0, toPage = pages, mana = pages, timed = true, stillSec = still, ambience = ambience, finishedBook = finished)

    private fun stats(books: List<Book>, readings: List<Reading> = emptyList(), quotes: List<Quote> = emptyList(), visits: Int = 0, pets: List<Pet> = listOf(pet)) =
        Stats(books, readings, pets, now, zone, 20, quotes = quotes, visits = visits)

    @Test
    fun theNineAreFoundByWhatTheyLove() {
        assertTrue(stats(listOf(book(1))).unlocked.none { it.ordinal > Species.MAGPIE.ordinal })
        // Three volumes of one cycle.
        val cycle = (1L..3L).map { book(it, done = it.toInt()).copy(series = "Earthsea", volume = it.toInt()) }
        assertTrue(Species.HYDRA in stats(cycle).unlocked)
        assertFalse(Species.HYDRA in stats(cycle.take(2)).unlocked)
        // A book read again and finished again.
        assertFalse(Species.BAYUN in stats(listOf(book(1, done = 1))).unlocked)
        assertFalse(Species.BAYUN in stats(listOf(book(1).copy(reread = 1))).unlocked)
        assertTrue(Species.BAYUN in stats(listOf(book(1, done = 1).copy(reread = 1))).unlocked)
        // Ten quiet minutes face down, five times; the fire, ten times.
        assertTrue(Species.SNAIL in stats(listOf(book(1)), (1..Unlocks.SNAIL_SITS).map { read(it, still = 600) }).unlocked)
        assertFalse(Species.SNAIL in stats(listOf(book(1)), (1..Unlocks.SNAIL_SITS).map { read(it, still = 599) }).unlocked)
        assertTrue(Species.SALAMANDER in stats(listOf(book(1)), (1..Unlocks.SALAMANDER_FIRES).map { read(it, ambience = "FIRE") }).unlocked)
        assertFalse(Species.SALAMANDER in stats(listOf(book(1)), (1..Unlocks.SALAMANDER_FIRES).map { read(it, ambience = "RAIN") }).unlocked)
        // Genres and little books.
        assertTrue(Species.AUTOMATON in stats(listOf(book(1, Genre.SCIFI, done = 1), book(2, Genre.SCIFI, done = 2))).unlocked)
        assertTrue(Species.SPHINX in stats(listOf(book(1, Genre.CLASSIC, done = 1), book(2, Genre.CLASSIC, done = 2))).unlocked)
        assertTrue(Species.PIXIE in stats(listOf(book(1, pages = 120, done = 1), book(2, Genre.POETRY, pages = 400, done = 2), book(3, pages = 90, done = 3))).unlocked)
        assertFalse(Species.PIXIE in stats(listOf(book(1, pages = 150, done = 1), book(2, pages = 0, done = 2), book(3, pages = 90, done = 3))).unlocked)
        // Quotes and guests.
        val quotes = (1L..5L).map { Quote(id = it, bookId = 1, text = "Line $it", at = at(1)) }
        assertTrue(Species.RAVEN in stats(listOf(book(1)), quotes = quotes).unlocked)
        assertFalse(Species.RAVEN in stats(listOf(book(1)), quotes = quotes.take(4)).unlocked)
        assertTrue(Species.OWLET in stats(listOf(book(1)), visits = 1).unlocked)
    }

    @Test
    fun theNineHaveTheirFavourites() {
        fun fav(sp: Species, s: Mana.Sitting) = Mana.feast(sp, s).parts.any { it.first == Part.FAVOURITE }
        fun sit(genre: Genre = Genre.FANTASY, bookPages: Int = 300, still: Float = 0f) = Mana.Sitting(30, true, 30, still, false, genre, null, bookPages, 1, 0, false, "")
        val plain = sit()
        for (sp in Species.entries.filter { it.ordinal > Species.MAGPIE.ordinal }) assertFalse(sp.name, fav(sp, plain))
        assertTrue(fav(Species.HYDRA, Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", series = true)))
        assertTrue(fav(Species.BAYUN, Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", reread = true)))
        assertTrue(fav(Species.SNAIL, sit(still = 0.6f)))
        assertTrue(fav(Species.SALAMANDER, Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", fire = true)))
        assertTrue(fav(Species.AUTOMATON, sit(Genre.SCIFI)))
        assertTrue(fav(Species.PIXIE, sit(bookPages = 120)))
        assertTrue(fav(Species.PIXIE, sit(Genre.POETRY)))
        // An audiobook of a hundred minutes is not a little book.
        assertFalse(fav(Species.PIXIE, Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 100, 1, 0, false, "", audio = true)))
        assertTrue(fav(Species.RAVEN, Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", quoted = true)))
        assertTrue(fav(Species.SPHINX, sit(Genre.CLASSIC)))
        assertTrue(fav(Species.OWLET, Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", guest = true)))
    }

    @Test
    fun everyAchievementHasItsNameAndItsOwnId() {
        assertTrue(FeatNames.complete())
        assertEquals(Feats.all.size, Feats.all.map { it.id }.toSet().size)
        assertTrue(Feats.all.size > 150)
        for (f in Feat.entries) {
            assertEquals(f.name, f.steps.toList(), f.steps.sorted())
            assertEquals(f.name, f.steps.size, f.steps.toSet().size)
        }
        assertTrue(Feats.all.all { it.rank in 0..4 && it.reward > 0 })
        // The last step of the measures that count everything is the whole of it.
        assertEquals(Species.entries.size, Feat.PETS.steps.last())
        assertEquals(app.familiar.domain.Item.entries.size, Feat.FURNITURE.steps.last())
        assertEquals(app.familiar.domain.Trinket.entries.size, Feat.FINDS.steps.last())
        assertEquals(Genre.entries.size, Feat.DREAMS.steps.last())
    }

    @Test
    fun achievementsAreMeasuredFromTheShelf() {
        val books = listOf(
            book(1, Genre.FANTASY, pages = 620, done = 3).copy(author = "Le Guin", rating = 5),
            book(2, Genre.SCIFI, done = 2).copy(author = "Le Guin"),
            book(3, Genre.HORROR, done = 1).copy(author = "le guin "),
            book(4).copy(shelf = Shelf.WANT.name),
        )
        // Saturday the 7th and Sunday the 8th of November 2026, and a day of 120 pages finished in one go.
        val readings = listOf(read(7, pages = 30), read(6, pages = 40), read(1, book = 3, pages = 120, finished = true), read(0, hour = 2, pages = 10))
        val v = Feats.values(stats(books, readings, quotes = listOf(Quote(1, 1, "A line", 0, at(1)))), DenState(furniture = 2, quests = 1, yearGoal = 3))
        assertEquals(3, v[Feat.BOOKS])
        assertEquals(200, v[Feat.PAGES])
        assertEquals(620, v[Feat.BRICK])
        assertEquals(3, v[Feat.GENRES])
        assertEquals(1, v[Feat.FANTASY])
        assertEquals(3, v[Feat.AUTHOR])
        assertEquals(1, v[Feat.AUTHORS])
        assertEquals(1, v[Feat.FIVE_STARS])
        assertEquals(1, v[Feat.QUOTES])
        assertEquals(1, v[Feat.WANT])
        assertEquals(1, v[Feat.WEEKENDS])
        assertEquals(1, v[Feat.ONE_DAY])
        assertEquals(1, v[Feat.MIDNIGHT])
        assertEquals(120, v[Feat.DAY_PAGES])
        assertEquals(1, v[Feat.YEAR_GOAL])
        assertEquals(2, v[Feat.FURNITURE])
        val met = Feats.met(v)
        assertTrue("BOOKS:1" in met && "BRICK:500" in met && "GENRES:3" in met && "AUTHOR:3" in met && "YEAR_GOAL:1" in met)
        assertFalse("BOOKS:5" in met || "BRICK:800" in met)
    }

    @Test
    fun theSleepTimerEndsTheSitting() {
        val r = Ritual(bookId = 1, start = 1_000, fromPage = 0, page = 0, stillMs = 0, listen = true)
        assertEquals(9_000L, r.endAt(9_000))
        assertEquals(5_000L, r.copy(sleepAt = 5_000).endAt(9_000))
        // Not gone off yet: the sitting runs on.
        assertEquals(4_000L, r.copy(sleepAt = 5_000).endAt(4_000))
    }

    @Test
    fun theYearLooksBackOnItsOwnBooks() {
        val books = listOf(
            book(1, Genre.FANTASY, pages = 620, done = 30).copy(author = "Le Guin", rating = 5, title = "First"),
            book(2, Genre.FANTASY, done = 2).copy(author = "Le Guin", title = "Last"),
            book(3, Genre.HORROR, done = 400).copy(title = "Last year's"),
        )
        val readings = listOf(read(30, pages = 50), read(2, book = 2, pages = 80, hour = 23), read(400, book = 3, pages = 300))
        val w = Wrapped(stats(books, readings, quotes = listOf(Quote(1, 1, "A line", 0, at(2)))), 2026)
        assertEquals(listOf("First", "Last"), w.books.map { it.title })
        assertEquals(130, w.pages)
        assertEquals(2, w.days)
        assertEquals("First", w.first?.title)
        assertEquals("Last", w.last?.title)
        assertEquals("First", w.longest?.title)
        assertEquals(Genre.FANTASY, w.genres.first().first)
        assertEquals("Le Guin" to 2, w.author)
        assertEquals(1, w.nights)
        assertEquals(1, w.fiveStars.size)
        assertEquals(1, w.quotes.size)
        assertFalse(w.empty)
        assertTrue(Wrapped(stats(books, readings), 2024).empty)
        assertEquals(2025, Wrapped.yearToShow(LocalDate.of(2026, 1, 20)))
        assertEquals(2026, Wrapped.yearToShow(LocalDate.of(2026, 2, 1)))
    }

    @Test
    fun theWeatherKeepsToTheTimeOfYear() {
        val year = (0L until 365L).map { LocalDate.of(2026, 1, 1).plusDays(it) }
        assertTrue(year.filter { it.monthValue in 6..8 }.none { Sky.of(it) == Sky.SNOW || Sky.of(it) == Sky.LEAVES })
        assertTrue(year.filter { it.monthValue in listOf(12, 1, 2) }.all { Sky.of(it) == Sky.SNOW || Sky.of(it) == Sky.CLEAR })
        // Every kind of weather comes round in a year, and a day's weather is always the same.
        assertEquals(Sky.entries.toSet(), year.map { Sky.of(it) }.toSet())
        assertEquals(Sky.of(today), Sky.of(today))
    }
}
