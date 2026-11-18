package app.familiar

import app.familiar.data.Backup
import app.familiar.data.Book
import app.familiar.data.Snapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    @Test fun coversTravelWithTheBackup() {
        val own = "file:///data/user/0/old.install/files/covers/own-1.jpg"
        val web = "https://covers.openlibrary.org/b/id/1-L.jpg"
        val books = listOf(
            Book(id = 1, title = "A", pages = 100, genre = "fantasy", added = 0, cover = own),
            Book(id = 2, title = "B", pages = 100, genre = "fantasy", added = 0, cover = web),
            Book(id = 3, title = "C", pages = 100, genre = "fantasy", added = 0, cover = "https://gone.example/x.jpg"),
        )
        val pictures = mapOf(own to byteArrayOf(1, 2, 3), web to byteArrayOf(4, 5))
        val json = Backup.encode(Snapshot(books, emptyList(), emptyList())) { pictures[it] }

        val restored = mutableMapOf<String, ByteArray>()
        val back = Backup.decode(json) { url, bytes ->
            restored[url] = bytes
            if (url == own) "file:///new/own-1.jpg" else url
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), restored[own])
        assertArrayEquals(byteArrayOf(4, 5), restored[web])
        assertEquals(listOf("file:///new/own-1.jpg", web, "https://gone.example/x.jpg"), back.books.map { it.cover })
        // Old backups without pictures still read.
        assertEquals(3, Backup.decode(Backup.encode(Snapshot(books, emptyList(), emptyList()))).books.size)
    }

    @Test fun quotesAndRereadsTravelWithTheBackup() {
        val books = listOf(Book(id = 1, title = "A", pages = 100, genre = "FANTASY", added = 0, reread = 2))
        val readings = listOf(app.familiar.data.Reading(id = 1, bookId = 1, petId = 1, start = 0, end = 60_000, fromPage = 0, toPage = 10, mana = 10, timed = true, ambience = "FIRE"))
        val quotes = listOf(app.familiar.data.Quote(7, 1, "Only in silence the word", 12, 5_000))
        val back = Backup.decode(Backup.encode(Snapshot(books, readings, emptyList(), emptyList(), quotes)))
        assertEquals(quotes, back.quotes)
        assertEquals(2, back.books.single().reread)
        assertEquals("FIRE", back.readings.single().ambience)
        // A backup from before quotes has none.
        assertEquals(0, Backup.decode(Backup.encode(Snapshot(books, readings, emptyList())).replace("quotes", "old")).quotes.size)
    }
    /** What lives in the settings (the den's things, candles, goals) and the reader's own backdrop go into the file and come back. */
    @Test fun theDenAndTheSettingsTravelWithTheBackup() = kotlinx.coroutines.runBlocking {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, app.familiar.data.FamiliarDatabase::class.java).allowMainThreadQueries().build()
        val store = app.familiar.data.SettingsStore(ctx)
        val ritual = app.familiar.data.Ritual(bookId = 1, start = 5, fromPage = 0, page = 3, stillMs = 0)
        store.update { it.copy(name = "Mara", spent = 40, owned = setOf("lamp", "rug"), candles = 3, yearGoal = 12, shelves = listOf("Summer"), backdrop = "own", ritual = ritual) }
        db.dao().replaceAll(Snapshot(listOf(Book(id = 1, title = "A", pages = 100, genre = "FANTASY", added = 0)), emptyList(), emptyList()))
        app.familiar.ui.Backdrops.ownFile(ctx).writeBytes(byteArrayOf(9, 8, 7))
        val text = Backup.save(ctx, db.dao(), store)

        store.update { app.familiar.data.Settings() }
        app.familiar.ui.Backdrops.ownFile(ctx).delete()
        db.dao().replaceAll(Snapshot(emptyList(), emptyList(), emptyList()))
        Backup.load(ctx, db.dao(), store, text)

        val s = store.now()
        assertEquals("Mara", s.name); assertEquals(40, s.spent); assertEquals(setOf("lamp", "rug"), s.owned); assertEquals(3, s.candles)
        assertEquals(12, s.yearGoal); assertEquals(listOf("Summer"), s.shelves); assertEquals("own", s.backdrop)
        assertEquals(true, s.onboarded)
        assertEquals(null, s.ritual)   // a sitting in progress stays on the phone it was started on
        assertArrayEquals(byteArrayOf(9, 8, 7), app.familiar.ui.Backdrops.ownFile(ctx).readBytes())
        assertEquals(listOf("A"), db.dao().books().map { it.title })

        // a backup from before the settings travelled leaves them alone
        store.update { it.copy(candles = 7) }
        Backup.load(ctx, db.dao(), store, Backup.encode(Snapshot(emptyList(), emptyList(), emptyList())))
        assertEquals(7, store.now().candles)
        db.close()
    }
}
