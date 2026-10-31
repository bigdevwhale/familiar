package app.familiar

import app.familiar.data.Book
import app.familiar.data.Import
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ImportTest {
    private val utc = ZoneOffset.UTC
    private fun day(ms: Long?) = ms?.let { java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate() }

    @Test fun livelibExport() {
        val csv = "﻿Title,Author,ISBN,My Rating,Number of Pages,Year Published,Date Read,Date Added,Bookshelves,Exclusive Shelf,Cover\r\n" +
            "Солярис,Станислав Лем,,0,,,2019/01/19,2019/01/20,read,read,https://i.livelib.ru/a.jpg\r\n" +
            "\"Автостопом по Галактике. Ресторан \"\"У конца Вселенной\"\"\",Дуглас Адамс,,4,,2005,2018/11/01,2018/12/02,read,read,\r\n"
        val r = Import.parse(csv, emptyList(), now = 0, zone = utc)
        assertEquals(2, r.books.size)
        val (solaris, adams) = r.books
        assertEquals(Shelf.DONE.name, solaris.shelf)
        assertEquals(LocalDate.of(2019, 1, 19), day(solaris.finished))
        assertEquals(LocalDate.of(2019, 1, 20), day(solaris.added))
        assertNull(solaris.rating)
        assertEquals("https://i.livelib.ru/a.jpg", solaris.cover)
        assertEquals(0, solaris.pages)
        assertEquals("Автостопом по Галактике. Ресторан \"У конца Вселенной\"", adams.title)
        assertEquals(4, adams.rating)
    }

    @Test fun goodreadsExport() {
        val csv = "Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review\n" +
            "1,Dune,Frank Herbert,\"Herbert, Frank\",,=\"0441013597\",,5,4.25,Ace,Paperback,604,2005,1965,,2024/03/02,\"favorites, read\",,read,\n" +
            "2,Emma,Jane Austen,\"Austen, Jane\",,,,0,4.0,,,474,2003,1815,,2024/05/01,currently-reading,,currently-reading,\n" +
            "3,Ulysses,James Joyce,\"Joyce, James\",,,,0,3.7,,,730,1990,1922,,2024/06/01,to-read,,to-read,\n"
        val parsed = Import.parse(csv, emptyList(), now = 0, zone = utc)
        assertEquals(listOf("0441013597"), parsed.isbn.values.toList())
        val (dune, emma, ulysses) = parsed.books
        assertEquals(604, dune.pages)
        assertEquals(604, dune.page)
        assertEquals(LocalDate.of(2024, 3, 2), day(dune.finished)) // no Date Read: the day it was added
        assertEquals("favorites", dune.tags)
        assertEquals(Shelf.READING.name, emma.shelf)
        assertNull(emma.finished)
        assertEquals(Shelf.WANT.name, ulysses.shelf)
    }

    @Test fun goodreadsAsItReallyComes() {
        val csv = "Book Id,Title,Author,My Rating,Number of Pages,Date Read,Date Added,Bookshelves,Exclusive Shelf\n" +
            "1,\"Not Till We Are Lost (Bobiverse, #5)\",Dennis E. Taylor,5.0,12,2026/04/06,2026/04/06,,read\n" +
            "2,Sapiens,Yuval Noah Harari,4.0,443,2025/01/10,2025/01/02,popular-science,popular-science\n" +
            "3,Вастум (Russian Edition),Анна Мезенцева,5.0,416,2025/02/01,2025/02/01,,read\n"
        val (bob, sapiens, vastum) = Import.parse(csv, emptyList(), now = 0, zone = utc).books
        assertEquals("Вастум", vastum.title)
        assertEquals("Not Till We Are Lost", bob.title)
        assertEquals("Bobiverse", bob.series)
        assertEquals(5, bob.volume)
        assertEquals(5, bob.rating)
        assertEquals(4, sapiens.rating)
        assertEquals(Shelf.DONE.name, sapiens.shelf)
        assertEquals(Genre.NONFICTION.name, sapiens.genre)
        assertEquals("popular-science", sapiens.tags)
    }

    @Test fun skipsBooksAlreadyThere() {
        val have = listOf(Book(title = "Солярис", author = "Станислав  Лем", pages = 0, genre = "OTHER", added = 0))
        val csv = "Title,Author,Exclusive Shelf\nсолярис!,Станислав Лем,read\nНепобедимый,Станислав Лем,read\nНепобедимый,Станислав Лем,read\n"
        val r = Import.parse(csv, have, now = 0)
        assertEquals(listOf("Непобедимый"), r.books.map { it.title })
        assertEquals(2, r.skipped)
    }

    @Test fun genresSeriesAndAudio() {
        val csv = "Title,Author,Number of Pages,Exclusive Shelf,Genres,Series,Series Number,Audio\n" +
            "Пока мы не сбились с пути,Деннис Тейлор,370,read,Фантастика; Зарубежная фантастика,Вселенная Боба,5,\n" +
            "Атлант,Айн Рэнд,0,read,Современная зарубежная проза; Философия,,,yes\n" +
            "Dune (Dune Chronicles #1),Frank Herbert,,read,,,,\n"
        val (bob, rand, dune) = Import.parse(csv, emptyList(), now = 0).books
        assertEquals(Genre.SCIFI.name, bob.genre)
        assertEquals("Вселенная Боба", bob.series)
        assertEquals(5, bob.volume)
        assertEquals(370, bob.pages)
        assertEquals(Genre.NONFICTION.name, rand.genre)
        assertEquals(true, rand.audio)
        assertEquals("Dune", dune.title)
        assertEquals("Dune Chronicles", dune.series)
        assertEquals(1, dune.volume)
        assertEquals(Genre.OTHER.name, dune.genre)
        assertEquals(Genre.FANTASY, Import.genre("Фэнтези; Героическое фэнтези"))
        assertEquals(Genre.MYSTERY, Import.genre("Триллеры"))
        assertEquals(Genre.FANTASY, Import.genre("Детективное фэнтези; Зарубежные детективы"))
        assertEquals(Genre.HORROR, Import.genre("Ужасы, мистика"))
        assertEquals(Genre.SCIFI, Import.genre("Стимпанк"))
        assertEquals(Genre.CLASSIC, Import.genre("Зарубежная классика"))
    }

    @Test fun refusesOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { Import.parse("{\"app\":\"familiar\"}", emptyList()) }
    }
}
