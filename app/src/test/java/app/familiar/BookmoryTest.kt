package app.familiar

import android.database.sqlite.SQLiteDatabase
import app.familiar.data.Book
import app.familiar.data.Bookmory
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// A bare Application: Familiar's own starts its database work in the background, which outlives these quick tests.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookmoryTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A backup as Bookmory makes it: a zip with its sembast database, every record a JSON value in `entry`. */
    private fun backup(vararg entries: Triple<String, String, String>, deleted: String? = null): ByteArray {
        val db = File(tmp.newFolder(), "new_bookmory.db")
        SQLiteDatabase.openOrCreateDatabase(db, null).use { d ->
            d.execSQL("CREATE TABLE entry (id INTEGER PRIMARY KEY AUTOINCREMENT, store TEXT NON NULL, key BLOB NON NULL, value TEXT, deleted INTEGER, UNIQUE(store, key))")
            for ((store, key, value) in entries) d.execSQL("INSERT INTO entry (store, key, value) VALUES (?, ?, ?)", arrayOf(store, key, value))
            deleted?.let { d.execSQL("INSERT INTO entry (store, key, value, deleted) VALUES ('books', 'gone', ?, 1)", arrayOf(it)) }
        }
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("images/books/")); z.closeEntry()
                z.putNextEntry(ZipEntry("new_bookmory.db")); z.write(db.readBytes()); z.closeEntry()
            }
        }.toByteArray()
    }

    // Trimmed from a real backup.
    private val mistborn = """{"image":"https://m.media-amazon.com/images/S/compressed.photo.goodreads.com/books/1617768316i/68428.jpg",
        "title":"Mistborn: The Final Empire","author":"","authors":["Brandon Sanderson"],"isbn":"9780765311788",
        "tags":"#Fantasy #Fiction #Audiobook #HighFantasy","page_type":"PAGE","total_page":541.0,"cur_page":541.0,"wishlist":false,
        "collection_keys":["1793505536121680"],
        "reads":[{"nth":1,"start":1793505579159,"end":1793505579159,"star":4.0,"status":"DONE","book_type":"paperBook","page_type":"PAGE"}],
        "status_list":["DONE"],"book_type":"paperBook","book_types":["paperBook"],"created_at":1793505563089}"""

    private val reading = """{"title":"Пикник на обочине","authors":["Аркадий Стругацкий","Борис Стругацкий"],"page_type":"PAGE",
        "total_page":250.0,"cur_page":120.0,"collection_keys":[],
        "reads":[{"nth":1,"start":1793000000000,"end":null,"star":0.0,"status":"READING"}],"book_type":"paperBook","created_at":1792000000000}"""

    private val audio = """{"title":"Дюна","authors":["Фрэнк Герберт"],"page_type":"TIME","total_page":1260.0,"cur_page":0.0,"wishlist":true,
        "reads":[],"book_type":"audioBook","book_types":["audioBook"],"created_at":1791000000000}"""

    @Test fun readsABackup() {
        val bytes = backup(
            Triple("books", "1793505582320671", mistborn),
            Triple("books", "2", reading),
            Triple("books", "3", audio),
            Triple("collections", "1793505536121680", """{"name":"Favorite"}"""),
            Triple("tags", "1", """{"name":"#Fantasy"}"""),
            deleted = """{"title":"Удалённая","reads":[]}""",
        )
        assertTrue(Bookmory.isBackup(bytes))
        val r = Bookmory.parse(bytes, emptyList(), tmp.newFolder(), now = 0)
        assertEquals(listOf("Mistborn: The Final Empire", "Пикник на обочине", "Дюна"), r.books.map { it.title })
        val (m, picnic, dune) = r.books

        assertEquals("Brandon Sanderson", m.author)
        assertEquals(Shelf.DONE.name, m.shelf)
        assertEquals(541, m.pages)
        assertEquals(541, m.page)
        assertEquals(4, m.rating)
        assertEquals(1793505579159, m.finished)
        assertEquals(1793505563089, m.added)
        assertEquals(Genre.FANTASY.name, m.genre)
        assertEquals("Favorite", m.tags)
        assertTrue(m.cover.startsWith("https://"))
        assertFalse(m.audio)
        assertEquals("9780765311788", r.isbn.values.single())

        assertEquals("Аркадий Стругацкий, Борис Стругацкий", picnic.author)
        assertEquals(Shelf.READING.name, picnic.shelf)
        assertEquals(120, picnic.page)
        assertEquals(1793000000000, picnic.started)
        assertNull(picnic.finished)
        assertNull(picnic.rating)

        assertEquals(Shelf.WANT.name, dune.shelf)
        assertTrue(dune.audio)
        assertEquals(0, dune.pages) // counted in time, not pages
        assertNull(dune.started)
    }

    @Test fun givenUpGoesToAbandonedWithItsReason() {
        val dropped = """{"title":"Улисс","authors":["Джеймс Джойс"],"page_type":"PAGE","total_page":730.0,"cur_page":120.0,
            "reads":[{"nth":1,"start":1790000000000,"end":1791000000000,"star":0.0,"status":"GIVE_UP","comment":"Поток сознания не мой"}]}"""
        val b = Bookmory.parse(backup(Triple("books", "1", dropped)), emptyList(), tmp.newFolder(), now = 0).books.single()
        assertEquals(Shelf.ABANDONED.name, b.shelf)
        assertEquals(1791000000000, b.finished)
        assertEquals(120, b.page)
        assertEquals("Поток сознания не мой", b.note)
        assertNull(b.rating)
    }

    @Test fun skipsBooksAlreadyThere() {
        val have = listOf(Book(title = "mistborn the final empire", author = "Brandon Sanderson", pages = 0, genre = "OTHER", added = 0))
        val r = Bookmory.parse(backup(Triple("books", "1", mistborn)), have, tmp.newFolder(), now = 0)
        assertEquals(0, r.books.size)
        assertEquals(1, r.skipped)
    }

    @Test fun refusesOtherZips() {
        val zip = ByteArrayOutputStream().also { ZipOutputStream(it).use { z -> z.putNextEntry(ZipEntry("a.txt")); z.write(1); z.closeEntry() } }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { Bookmory.parse(zip, emptyList(), tmp.newFolder()) }
    }
}
