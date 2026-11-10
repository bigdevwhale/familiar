package app.familiar

import app.familiar.content.Lines
import app.familiar.data.Book
import app.familiar.data.Pet
import app.familiar.data.Reading
import app.familiar.data.Shelf
import app.familiar.domain.DayPart
import app.familiar.domain.Genre
import app.familiar.domain.Insights
import app.familiar.domain.Mana
import app.familiar.domain.Mood
import app.familiar.domain.Part
import app.familiar.domain.Quest
import app.familiar.domain.Quests
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.domain.Stats
import app.familiar.domain.Unlocks
import app.familiar.domain.Dreams
import app.familiar.domain.Finds
import app.familiar.domain.Form
import app.familiar.domain.Guard
import app.familiar.domain.Nudge
import app.familiar.domain.Season
import app.familiar.domain.Trait
import app.familiar.domain.Trinket
import app.familiar.domain.Visit
import app.familiar.ui.pix.Glyph
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

class FamiliarTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 17)
    private val now = today.atTime(20, 0).toInstant(zone).toEpochMilli()
    private val pet = Pet(id = 1, species = Species.KITSUNE.name, name = "Yuzu", joined = 0, mana = 500, active = true)

    private fun at(daysAgo: Int, hour: Int = 20) = today.minusDays(daysAgo.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun read(daysAgo: Int, pages: Int = 20, hour: Int = 20, book: Long = 1, item: String = "", still: Int = 0, minutes: Int = 30) =
        Reading(bookId = book, petId = 1, start = at(daysAgo, hour), end = at(daysAgo, hour) + minutes * 60_000L, fromPage = 0, toPage = pages, mana = pages, timed = true, item = item, stillSec = still)

    private fun book(id: Long, genre: Genre = Genre.FANTASY, pages: Int = 300, done: Int? = null) =
        Book(id = id, title = "B$id", pages = pages, genre = genre.name, shelf = if (done != null) Shelf.DONE.name else Shelf.READING.name, added = 0, finished = done?.let { at(it) })

    private fun stats(readings: List<Reading>, books: List<Book> = listOf(book(1)), pets: List<Pet> = listOf(pet)) =
        Stats(books, readings, pets, now, zone, 20)

    private fun sitting(pages: Int = 30, timed: Boolean = true, minutes: Int = 30, still: Float = 0f, night: Boolean = false, genre: Genre = Genre.FANTASY, prev: Genre? = null, bookPages: Int = 300, streak: Int = 1, gap: Int = 0, finished: Boolean = false, item: String = "") =
        Mana.Sitting(pages, timed, minutes, still, night, genre, prev, bookPages, streak, gap, finished, item)

    @Test
    fun stagesByMana() {
        assertEquals(Stage.EGG, Stage.of(0))
        assertEquals(Stage.EGG, Stage.of(19))
        assertEquals(Stage.BABY, Stage.of(20))
        assertEquals(Stage.YOUNG, Stage.of(300))
        assertEquals(Stage.ANCIENT, Stage.of(99_999))
        assertEquals(0.5f, Stage.progress(160), 0.01f)
        assertEquals(1f, Stage.progress(10_000), 0f)
    }

    @Test
    fun pagesAreMana() {
        val f = Mana.feast(Species.MIMIC, sitting(pages = 30, timed = false))
        assertEquals(30, f.total)
        assertFalse(f.sniffed)
    }

    @Test
    fun candleAndStillnessAddBonus() {
        val f = Mana.feast(Species.MIMIC, sitting(pages = 40, still = 1f))
        assertEquals(40 + 4 + 8, f.total)
    }

    @Test
    fun tooFastIsSniffed() {
        // 200 pages in 10 minutes: only 28 are believable, the rest count half.
        val f = Mana.feast(Species.MIMIC, sitting(pages = 200, minutes = 10))
        assertTrue(f.sniffed)
        assertTrue(f.total < 130)
        // Logged by hand there's no clock to doubt.
        assertFalse(Mana.feast(Species.MIMIC, sitting(pages = 200, timed = false)).sniffed)
    }

    @Test
    fun favourites() {
        fun fav(sp: Species, s: Mana.Sitting) = Mana.feast(sp, s).parts.any { it.first == Part.FAVOURITE }
        assertTrue(fav(Species.KITSUNE, sitting(genre = Genre.HORROR, prev = Genre.FANTASY)))
        assertFalse(fav(Species.KITSUNE, sitting(genre = Genre.FANTASY, prev = Genre.FANTASY)))
        assertTrue(fav(Species.DRAGON, sitting(bookPages = 500)))
        assertTrue(fav(Species.MOTH, sitting(night = true)))
        assertTrue(fav(Species.GRIFFIN, sitting(streak = 4)))
        assertTrue(fav(Species.OWLBEAR, sitting(genre = Genre.HISTORY)))
        assertTrue(fav(Species.PHOENIX, sitting(gap = 6)))
        assertTrue(fav(Species.MIMIC, sitting(finished = true)))
        assertEquals(Mana.SLIME_ITEM, Mana.feast(Species.SLIME, sitting(pages = 0, timed = false, item = "sword")).total)
    }

    @Test
    fun streakCountsUpToYesterday() {
        val s = stats(listOf(read(1), read(2), read(3), read(5)))
        assertEquals(3, s.streak)
        assertEquals(3, s.bestStreak)
        assertEquals(4, stats(listOf(read(0), read(1), read(2), read(3))).streak)
    }

    @Test
    fun stoneCreepsInAfterTwoDays() {
        assertEquals(0f, stats(listOf(read(1))).stone, 0f)
        val three = stats(listOf(read(3))).stone
        assertTrue(three > 0f && three < 0.5f)
        assertEquals(1f, stats(listOf(read(9))).stone, 0f)
        // A brand-new familiar with nothing read yet is spared.
        assertEquals(0f, stats(emptyList()).stone, 0f)
    }

    @Test
    fun tailsAreGenres() {
        val books = listOf(book(1, Genre.FANTASY, done = 3), book(2, Genre.HORROR, done = 2), book(3, Genre.OTHER, done = 1), book(4, Genre.HORROR, done = 1))
        assertEquals(2, stats(emptyList(), books).tails)
        assertEquals(1, stats(emptyList(), emptyList()).tails)
    }

    @Test
    fun moodFromLastBookFadesAfterTwelveHours() {
        val books = listOf(book(1, Genre.HORROR))
        assertEquals(Mood.SCARED, stats(listOf(read(0, hour = 19)), books).mood)
        assertEquals(Mood.NONE, stats(listOf(read(1, hour = 6)), books).mood)
    }

    @Test
    fun unlocks() {
        val s0 = stats(emptyList())
        assertEquals(setOf(Species.KITSUNE), s0.unlocked)
        val two = stats(emptyList(), listOf(book(1, done = 2), book(2, done = 1)))
        assertTrue(Species.MIMIC in two.unlocked && Species.SLIME in two.unlocked)
        assertTrue(Species.DRAGON in stats(emptyList(), listOf(book(1, pages = Unlocks.DRAGON_PAGES, done = 1))).unlocked)
        assertTrue(Species.MOTH in stats((1..5).map { read(it, hour = 23) }).unlocked)
        assertTrue(Species.PHOENIX in stats(listOf(read(9), read(1))).unlocked)
        assertTrue(Species.GRIFFIN in stats((1..7).map { read(it) }).unlocked)
        assertTrue(Species.OWLBEAR in stats(emptyList(), listOf(book(1, Genre.HISTORY, done = 1))).unlocked)
    }

    @Test
    fun guildBoardIsStableForAMonth() {
        val m = YearMonth.of(2026, 10)
        assertEquals(Quest.of(m), Quest.of(m))
        assertEquals(3, Quest.of(m).toSet().size)
        val s = stats((0..9).map { read(it, pages = 60, still = 700) })
        Quests.state(s, m).forEach { q -> assertTrue(q.progress in 0..q.quest.target) }
    }

    @Test
    fun linesFitAnyName() {
        for (sp in Species.entries) for (st in Lines.State.entries) for (l in listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
            val line = Lines.line(l, sp, st, "Zed", 3)
            assertTrue("$sp $st $l", line.isNotBlank() && "{n}" !in line)
        }
    }

    @Test
    fun glyphsPickByWord() {
        assertEquals(Glyph.SWORD, Glyph.of("Старый меч"))
        assertEquals(Glyph.LETTER, Glyph.of("a letter from Mr. Darcy"))
        assertEquals(Glyph.of("something odd"), Glyph.of("something odd"))
    }

    @Test
    fun ritualWithoutPageEatsMinutes() {
        val f = Mana.feast(Species.MIMIC, sitting(pages = 0, minutes = 40))
        assertEquals(40, f.total)
        assertTrue(f.parts.any { it.first == Part.TIME })
        // Logged by hand with no pages there's nothing to eat.
        assertEquals(0, Mana.feast(Species.MIMIC, sitting(pages = 0, timed = false, minutes = 40)).total)
    }

    @Test
    fun unknownLengthHasNoLimit() {
        val b = book(1, pages = 0)
        assertEquals(99_999, b.cap)
        assertEquals(0f, b.fraction, 0f)
    }

    @Test
    fun listeningWithoutPageEatsMinutesListened() {
        val f = Mana.feast(Species.MIMIC, Mana.Sitting(0, true, 25, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", listened = true))
        assertEquals(25, f.total)
        assertTrue(f.parts.any { it.first == Part.LISTEN })
    }

    @Test
    fun newcomersAreFoundAndFed() {
        fun listen(d: Int) = read(d, pages = 0).copy(listened = true)
        assertTrue(Species.TANUKI in stats((1..5).map { listen(it) }).unlocked)
        assertTrue(Species.SPROUT in stats((1..5).map { read(it, hour = 7) }).unlocked)
        assertTrue(Species.GHOST in stats(emptyList(), listOf(book(1, Genre.MYSTERY, done = 1))).unlocked)
        assertTrue(Species.WYRM in stats((1..3).map { read(it, minutes = 50) }).unlocked)
        assertFalse(Species.WYRM in stats((1..3).map { read(it, minutes = 30) }).unlocked)
        fun fav(sp: Species, s: Mana.Sitting) = Mana.feast(sp, s).parts.any { it.first == Part.FAVOURITE }
        assertTrue(fav(Species.GHOST, sitting(genre = Genre.HORROR)))
        assertTrue(fav(Species.WYRM, sitting(minutes = 60)))
        assertTrue(fav(Species.SPROUT, sitting().let { Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", morning = true) }))
        // Listening without a page still feeds the tanuki's favourite.
        assertTrue(fav(Species.TANUKI, Mana.Sitting(0, true, 40, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", listened = true)))
    }

    @Test
    fun findsAreStableAndLikeNewThings() {
        val a = (1L..400L).map { Finds.roll(it, 30, Genre.FANTASY, Trait.SLEEPY, Season.NONE, emptySet()) }
        assertEquals(a, (1L..400L).map { Finds.roll(it, 30, Genre.FANTASY, Trait.SLEEPY, Season.NONE, emptySet()) })
        val share = a.count { it != null } / 400f
        assertTrue("$share", share in 0.25f..0.45f)
        // Nothing from a sitting too short, and no season find out of its season.
        assertNull(Finds.roll(5, 2, Genre.FANTASY, Trait.CURIOUS, Season.NONE, emptySet()))
        assertTrue(a.none { it?.season != null })
        assertTrue(a.filterNotNull().all { it.common || it.genre == Genre.FANTASY })
        val curious = (1L..400L).count { Finds.roll(it, 30, Genre.FANTASY, Trait.CURIOUS, Season.NONE, emptySet()) != null }
        assertTrue(curious > a.count { it != null })
        assertTrue((1L..200L).any { Finds.roll(it, 30, Genre.OTHER, Trait.BRAVE, Season.SAMHAIN, emptySet()) == Trinket.PUMPKIN })
    }

    @Test
    fun seasonsByDate() {
        assertEquals(Season.SAMHAIN, Season.of(LocalDate.of(2026, 10, 31)))
        assertEquals(Season.NONE, Season.of(LocalDate.of(2026, 10, 20)))
        assertEquals(Season.WINTER, Season.of(LocalDate.of(2026, 12, 31)))
        assertEquals(Season.WINTER, Season.of(LocalDate.of(2027, 1, 3)))
        assertEquals(Season.BOOKDAY, Season.of(LocalDate.of(2027, 4, 23)))
        assertEquals(Season.NONE, Season.of(LocalDate.of(2027, 3, 1)))
        Season.entries.filter { it != Season.NONE }.forEach { assertNotNull(it.trinket) }
    }

    @Test
    fun guardianCandlesKeepTheStreak() {
        val days = setOf(today.minusDays(3), today.minusDays(2))
        // One quiet day (yesterday), one candle: it burns.
        assertEquals(listOf(today.minusDays(1)), Guard.toBurn(days, emptySet(), 1, today))
        // Two quiet days and one candle: nothing is wasted.
        assertEquals(emptyList<LocalDate>(), Guard.toBurn(setOf(today.minusDays(3)), emptySet(), 1, today))
        assertEquals(2, Guard.toBurn(setOf(today.minusDays(3)), emptySet(), 2, today).size)
        assertEquals(emptyList<LocalDate>(), Guard.toBurn(setOf(today.minusDays(1)), emptySet(), 2, today))
        // A burned day keeps the chain and the stone away.
        val s = Stats(listOf(book(1)), listOf(read(3), read(2)), listOf(pet), now, zone, 20, setOf(today.minusDays(1)))
        assertEquals(3, s.streak)
        assertEquals(0f, s.stone, 0f)
    }

    @Test
    fun visitingCardsTravel() {
        val v = Visit(Species.GHOST, "Бу", 1500, true, Form.SHADOW, Trait.BRAVE, "Мира", "Пикник на обочине", "Стругацкие", Genre.SCIFI)
        assertEquals(v, Visit.decode(v.encode()))
        assertNull(Visit.decode("hello"))
        assertNull(Visit.decode("FAMILIAR1:!!!"))
    }

    @Test
    fun formsFollowTheMeals() {
        assertEquals(Form.SHADOW, Form.choose(listOf(Genre.HORROR to 300, Genre.FANTASY to 100, Genre.MYSTERY to 50)))
        assertEquals(Form.ASTRAL, Form.choose(listOf(Genre.SCIFI to 100, Genre.FANTASY to 100, Genre.ROMANCE to 150)))
        assertEquals(Form.NONE, Form.choose(emptyList()))
        Genre.entries.forEach { Form.of(it) }
    }

    @Test
    fun nudgePicksTheNearestGoal() {
        val s = stats(listOf(read(0, pages = 15)))
        // Five pages to a full familiar today beats everything further away.
        val n = Nudge.next(s, sparks = 0, cheapest = 1000, book = null)
        assertEquals(Nudge.Goal.FED, n?.goal)
        assertEquals(5, n?.left)
        assertEquals(2, Nudge.next(s, 0, null, null, extra = 3)?.left)
        val nearEnd = book(1, pages = 300).copy(page = 297)
        assertEquals(Nudge.Goal.BOOK, Nudge.next(s, 0, null, nearEnd)?.goal)
    }

    @Test
    fun dreamsInTheMorningAfterReading() {
        val books = listOf(book(1, Genre.HORROR), book(2, Genre.POETRY))
        val morning = today.atTime(8, 0).toInstant(zone).toEpochMilli()
        val rs = listOf(read(1, pages = 40, book = 1), read(1, pages = 10, book = 2))
        assertEquals(Genre.HORROR, Dreams.of(rs, books, morning, zone))
        assertNull(Dreams.of(rs, books, today.atTime(14, 0).toInstant(zone).toEpochMilli(), zone))
        assertNull(Dreams.of(listOf(read(3, book = 1)), books, morning, zone))
    }

    @Test
    fun traitLinesFitAnyName() {
        for (t in Trait.entries) for (l in listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
            val line = Lines.trait(l, t, "Zed", 1)
            assertTrue(line.isNotBlank() && "{n}" !in line)
        }
        assertEquals(Trait.of("", 123_000L), Trait.of("", 123_000L))
    }

    @Test
    fun minutesCountAsADayOfReading() {
        // Listened yesterday without a page: still part of the streak, and today's minutes feed the familiar.
        val listened = Reading(bookId = 1, petId = 1, start = at(1), end = at(1) + 30 * 60_000L, fromPage = 10, toPage = 10, mana = 30, timed = true, listened = true)
        val today = Reading(bookId = 1, petId = 1, start = at(0, 18), end = at(0, 18) + 25 * 60_000L, fromPage = 10, toPage = 10, mana = 25, timed = true)
        val s = stats(listOf(read(2), listened, today))
        assertEquals(3, s.streak)
        assertEquals(25, s.todayPages)
    }

    @Test
    fun ownGenresFindTheChimeraAndTheMagpie() {
        fun own(id: Long, genre: Genre, sub: String) = book(id, genre, done = 1).copy(sub = sub)
        val books = listOf(own(1, Genre.FANTASY, "Dark fantasy"), own(2, Genre.FANTASY, "dark fantasy "), own(3, Genre.OTHER, "Manga"))
        val s = stats(emptyList(), books)
        assertEquals(2, s.ownFinished.size)
        assertFalse(Species.CHIMERA in s.unlocked || Species.MAGPIE in s.unlocked)
        val more = stats(emptyList(), books + own(4, Genre.FANTASY, "Urban fantasy"))
        assertTrue(Species.CHIMERA in more.unlocked && Species.MAGPIE in more.unlocked)
        fun fav(sp: Species, s: Mana.Sitting) = Mana.feast(sp, s).parts.any { it.first == Part.FAVOURITE }
        assertTrue(fav(Species.CHIMERA, Mana.Sitting(30, true, 30, 0f, false, Genre.FANTASY, null, 300, 1, 0, false, "", own = true)))
        assertFalse(fav(Species.CHIMERA, sitting()))
        assertTrue(fav(Species.MAGPIE, sitting(genre = Genre.HORROR, prev = Genre.HORROR)))
        assertFalse(fav(Species.MAGPIE, sitting(genre = Genre.HORROR, prev = Genre.FANTASY)))
    }

    @Test
    fun ownGenresKeepTheirParentAndShowUpFromBooks() {
        val g = app.familiar.domain.OwnGenre.decode("FANTASY|Dark fantasy")!!
        assertEquals(Genre.FANTASY, g.parent)
        assertEquals(g.encode(), "FANTASY|Dark fantasy")
        assertNull(app.familiar.domain.OwnGenre.decode("FANTASY|  "))
        val all = app.familiar.domain.OwnGenre.all(listOf(g), listOf(book(1, Genre.OTHER).copy(sub = "Manga"), book(2).copy(sub = "DARK FANTASY")))
        assertEquals(listOf("Dark fantasy", "Manga"), all.map { it.name })
        assertTrue(all[1].standalone)
        // One name in two genres is two subgenres: "Mysticism" in horror and in mystery.
        val two = app.familiar.domain.OwnGenre.all(emptyList(), listOf(book(1, Genre.HORROR).copy(sub = "Mysticism"), book(2, Genre.MYSTERY).copy(sub = "mysticism")))
        assertEquals(listOf(Genre.HORROR, Genre.MYSTERY), two.map { it.parent })
    }

    @Test
    fun theShelfSearchesFiltersAndSorts() {
        val b = listOf(
            book(1).copy(title = "Dune", author = "Frank Herbert", rating = 5),
            book(2, Genre.HORROR).copy(title = "Dracula", author = "Bram Stoker", audio = true, rating = 3),
            book(3).copy(title = "Hobbit", author = "J. R. R. Tolkien", sub = "Fairy-tale"),
        )
        fun titles(br: app.familiar.ui.books.Browse) = br.apply(b, emptyList(), locale = Locale.ENGLISH).map { it.title }
        val by = app.familiar.ui.books.Browse(sort = app.familiar.ui.books.Sort.AUTHOR)
        assertEquals(listOf("Dune", "Dracula", "Hobbit"), titles(by))
        assertEquals(listOf("Hobbit", "Dracula", "Dune"), titles(by.copy(reverse = true)))
        assertEquals(listOf("Dracula"), titles(by.copy(format = app.familiar.ui.books.Format.AUDIO)))
        assertEquals(listOf("Dune", "Hobbit"), titles(by.copy(genres = setOf("g:FANTASY"))))
        assertEquals(listOf("Hobbit"), titles(by.copy(genres = setOf("o:FANTASY|fairy-tale"))))
        // Paper, e-book and audio each on their own.
        val withEbook = b.map { if (it.title == "Dune") it.copy(ebook = true) else it }
        fun formats(f: app.familiar.ui.books.Format) = by.copy(format = f).apply(withEbook, emptyList(), locale = Locale.ENGLISH).map { it.title }
        assertEquals(listOf("Hobbit"), formats(app.familiar.ui.books.Format.PAPER))
        assertEquals(listOf("Dune"), formats(app.familiar.ui.books.Format.EBOOK))
        assertEquals(listOf("Dune"), titles(by.copy(stars = 4)))
        assertEquals(listOf("Hobbit"), titles(by.copy(query = "tolk")))
        assertEquals(listOf("Dracula"), by.copy(query = "ужас").apply(b, emptyList(), genreNames = mapOf(Genre.HORROR to "Ужасы")).map { it.title })
        assertEquals(by.copy(reverse = true), app.familiar.ui.books.Browse.sorted("AUTHOR:r"))
    }

    @Test
    fun theShelfShowsACycleInVolumeOrder() {
        val b = listOf(
            book(1).copy(title = "Children of Dune", series = "Dune", volume = 3, added = 3),
            book(2).copy(title = "Hobbit", added = 9),
            book(3).copy(title = "Dune", series = "Dune", volume = 1, added = 1),
            book(4).copy(title = "Dune Messiah", series = " dune ", volume = 2, added = 5),
            book(5).copy(title = "Mort", series = "Discworld", volume = 4, added = 2),
            book(6).copy(title = "Guards! Guards!", series = "Discworld", volume = null, added = 7),
        )
        fun titles(br: app.familiar.ui.books.Browse) = br.apply(b, emptyList(), locale = Locale.ENGLISH).map { it.title }
        // One cycle: its volumes in order whatever the sort, case and spaces aside.
        val dune = app.familiar.ui.books.Browse(series = "DUNE")
        assertEquals(listOf("Dune", "Dune Messiah", "Children of Dune"), titles(dune))
        assertEquals(listOf("Dune", "Dune Messiah", "Children of Dune"), titles(dune.copy(sort = app.familiar.ui.books.Sort.TITLE, reverse = true)))
        assertEquals(1, dune.filters)
        // By cycle: cycles by name, each in volume order (unnumbered last), books outside a cycle at the end.
        assertEquals(
            listOf("Mort", "Guards! Guards!", "Dune", "Dune Messiah", "Children of Dune", "Hobbit"),
            titles(app.familiar.ui.books.Browse(sort = app.familiar.ui.books.Sort.SERIES)),
        )
    }

    @Test
    fun percentToPageAndBack() {
        assertEquals(150, Book.atPercent(300, 50))
        assertEquals(0, Book.atPercent(300, 0))
        assertEquals(300, Book.atPercent(300, 140))
        // An audiobook of 9 h 30 min, 37% in.
        assertEquals(211, Book.atPercent(570, 37))
        assertEquals(37, Book.percentOf(570, 211))
        assertEquals(0, Book.percentOf(0, 40))
        assertEquals(100, Book.percentOf(300, 300))
    }

    @Test
    fun paceIsPagesAnHourByGenre() {
        val books = listOf(book(1), book(2, Genre.HISTORY))
        val n = Insights(stats(listOf(read(1, 30), read(2, 30), read(3, 10, book = 2)), books))
        assertEquals(46, n.pace)
        assertEquals(listOf(Genre.FANTASY to 60, Genre.HISTORY to 20), n.paceByGenre)
        // Too little timed reading says nothing yet.
        assertNull(Insights(stats(listOf(read(1, 30, minutes = 20)))).pace)
    }

    @Test
    fun habitsAndMonths() {
        val n = Insights(stats(listOf(read(0, hour = 7), read(1), read(2), read(20))))
        assertEquals(DayPart.EVENING, n.bestPart)
        assertEquals(1, n.dayParts[DayPart.MORNING])
        assertEquals(30, n.avgSitting)
        assertEquals(20, n.perDay)
        assertEquals(6, n.months.size)
        assertEquals(YearMonth.of(2026, 10), n.months.last().month)
        assertEquals(60, n.months.last().amount)
        assertEquals(20, n.months[4].amount)
    }

    @Test
    fun forecastFromTheLastTwoWeeks() {
        val b = book(1).copy(page = 60)
        // Begun yesterday, 30 pages a day: 240 left is eight more days.
        assertEquals(today.plusDays(8), Insights.forecast(b, stats(listOf(read(1, 30), read(0, 30)), listOf(b))))
        // Read a month ago only: no pace to go by.
        assertNull(Insights.forecast(b, stats(listOf(read(30, 60)), listOf(b))))
        assertNull(Insights.forecast(b.copy(shelf = Shelf.PAUSED.name), stats(listOf(read(0, 30)), listOf(b))))
        assertNull(Insights.forecast(b.copy(pages = 0), stats(listOf(read(0, 30)), listOf(b))))
    }

    @Test
    fun finishedBooksAddUp() {
        val books = listOf(
            book(1, pages = 150, done = 10).copy(author = "Le Guin", rating = 5, started = at(14)),
            book(2, Genre.HORROR, pages = 450, done = 3).copy(author = "le guin ", rating = 4),
            book(3, pages = 800, done = 400).copy(author = "King", rating = 3),
            book(4, pages = 600).copy(audio = true, shelf = Shelf.DONE.name, finished = at(1)),
            book(5),
        )
        val year = app.familiar.domain.Finished(stats(emptyList(), books), today.year)
        assertEquals(3, year.books.size)
        assertEquals(600, year.pages)
        assertEquals(300, year.avgLength)
        assertEquals(4.5f, year.avgRating!!, 0.01f)
        assertEquals(5, year.avgDays)
        assertEquals(listOf(1, 0, 1, 0), year.lengths)
        assertEquals(listOf("Le Guin" to 2), year.authors)
        assertEquals(1, year.authorCount)
        assertEquals(12, year.byMonth.size)
        assertEquals(3, year.byMonth[today.monthValue - 1])
        assertTrue(year.byYear.isEmpty())

        val all = app.familiar.domain.Finished(stats(emptyList(), books), null)
        assertEquals(4, all.books.size)
        assertEquals(listOf(1, 0, 1, 1), all.lengths)
        assertEquals(listOf(today.year - 1 to 1, today.year to 3), all.byYear)
        assertEquals(Genre.FANTASY to 3, all.genres.first())
    }

    @Test
    fun aBookFillsItsDaysFromStartToFinish() {
        // Brought over with no sittings: started 6 days ago, finished 3 days ago; another still open since 4 days ago.
        val imported = book(5, done = 3).copy(started = at(6))
        val open = book(6).copy(started = at(4))
        val s = stats(listOf(read(1, 10, book = 6)), listOf(imported, open))
        val days = app.familiar.domain.dayBooks(s, YearMonth.from(today))
        fun ids(n: Long) = days[today.minusDays(n)]?.map { it.book.id }
        if (today.minusDays(6).month == today.month) assertEquals(listOf(5L), ids(6))
        if (today.minusDays(4).month == today.month) assertEquals(listOf(6L, 5L), ids(4))
        if (today.minusDays(3).month == today.month) assertEquals(listOf(5L), ids(3))
        if (today.minusDays(1).month == today.month) assertEquals(listOf(6L), ids(1))
    }

    @Test
    fun anOpenBookFillsNoDayAfterItsLastSitting() {
        // Shelved as being read 6 days ago and never opened; another started 6 days ago, last read 3 days ago.
        val untouched = book(7).copy(started = at(6))
        val paused = book(8).copy(started = at(6))
        val s = stats(listOf(read(3, 10, book = 8)), listOf(untouched, paused))
        val days = app.familiar.domain.dayBooks(s, YearMonth.from(today))
        fun ids(n: Long) = days[today.minusDays(n)]?.map { it.book.id }
        if (today.minusDays(5).month == today.month) assertEquals(listOf(8L), ids(5))
        assertEquals(null, ids(1))
        assertEquals(null, ids(0))
    }

    @Test
    fun theDayShowsTheBookFinishedOrReadMost() {
        val books = listOf(book(1), book(2), book(3, done = 2))
        val s = stats(listOf(read(1, 10, book = 1), read(1, 40, book = 2), read(2, 5, book = 1)), books)
        val days = app.familiar.domain.dayBooks(s, YearMonth.from(today))
        assertEquals(listOf(2L, 1L), days[today.minusDays(1)]!!.map { it.book.id })
        assertFalse(days[today.minusDays(1)]!!.first().finished)
        assertEquals(listOf(3L, 1L), days[today.minusDays(2)]!!.map { it.book.id })
        assertTrue(days[today.minusDays(2)]!!.first().finished)
        assertTrue(app.familiar.domain.dayBooks(s, YearMonth.from(today).minusMonths(1)).isEmpty())
    }

    @Test
    fun theNextVolumeIsTheFirstUnread() {
        val cycle = listOf(
            book(1, done = 9).copy(series = "Earthsea", volume = 1),
            book(2).copy(series = "Earthsea", volume = 3, shelf = Shelf.WANT.name),
            book(3).copy(series = "earthsea", volume = 2, shelf = Shelf.ABANDONED.name),
        )
        assertEquals(2L, app.familiar.domain.Series.all(cycle).single().next?.id)
        assertNull(app.familiar.domain.Series.all(cycle.take(1)).single().next)
    }

    @Test
    fun spinesFillThePlanks() {
        val thin = book(1, pages = 0)
        val thick = book(2, pages = 2000)
        assertEquals(22, app.familiar.ui.books.Spines.width(thin))
        assertEquals(44, app.familiar.ui.books.Spines.width(thick))
        assertEquals(16, app.familiar.ui.books.Spines.width(book(3, pages = 10)))
        val h = app.familiar.ui.books.Spines.height(thick)
        assertTrue(h in 132..164)
        assertEquals(h, app.familiar.ui.books.Spines.height(thick))
        // 22 + 2 + 44 = 68 fits in 70; the next book goes on a new plank.
        val rows = app.familiar.ui.books.Spines.rows(listOf(thin, thick, thin), room = 70)
        assertEquals(listOf(listOf(1L, 2L), listOf(1L)), rows.map { r -> r.map { it.id } })
    }

    @Test
    fun cyclesStandTogetherUnderTheirName() {
        val s = app.familiar.ui.books.Spines
        val loose = book(1, pages = 0)
        val v2 = book(2, pages = 0).copy(series = "Earthsea", volume = 2)
        val other = book(3, pages = 0)
        val v1 = book(4, pages = 0).copy(series = "earthsea", volume = 1)
        val groups = s.groups(listOf(loose, v2, other, v1))
        // The cycle gathers where its first volume came, its volumes in order.
        assertEquals(listOf("", "Earthsea", ""), groups.map { it.name })
        assertEquals(listOf(4L, 2L), groups[1].pieces.flatMap { p -> p.books.map { it.id } })
        // Neighbours lying down make one pile; one facing out is as wide as its cover.
        val pile = s.pieces(listOf(loose.copy(pose = "LIE"), other.copy(pose = "LIE"), v1.copy(pose = "FACE")))
        assertEquals(listOf(app.familiar.data.Pose.LIE, app.familiar.data.Pose.FACE), pile.map { it.pose })
        assertEquals(2, pile[0].books.size)
        assertEquals(s.faceWidth(v1), pile[1].width)
        // A cycle that doesn't fit after the loose book goes whole to the next plank: 22 + 12 + 46 > 70.
        val planks = s.planks(s.groups(listOf(loose, v1, v2)), room = 70)
        assertEquals(listOf(listOf(""), listOf("earthsea")), planks.map { r -> r.map { it.name } })
    }
}
