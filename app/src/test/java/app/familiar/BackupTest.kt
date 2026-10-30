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
}
