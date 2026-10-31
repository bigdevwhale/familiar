package app.familiar.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class Shelf { READING, WANT, DONE, PAUSED }

/** A book on the shelf. [page] is the last page read. */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    /** Total pages (minutes for an audiobook); 0 when not known. */
    val pages: Int,
    /** [app.familiar.domain.Genre] name. */
    val genre: String,
    /** [Shelf] name. */
    val shelf: String = Shelf.READING.name,
    val page: Int = 0,
    val added: Long,
    val started: Long? = null,
    val finished: Long? = null,
    /** 1..5 once finished, or null. */
    val rating: Int? = null,
    /** Cover picture address from a book catalogue, or empty for a drawn cover. */
    val cover: String = "",
    /** An audiobook: [pages] and [page] are minutes. */
    val audio: Boolean = false,
    /** The cycle this book belongs to, and its number in it; empty for a standalone book. */
    val series: String = "",
    val volume: Int? = null,
    /** The user's own shelves this book is on, one name per line. */
    @ColumnInfo(defaultValue = "") val tags: String = "",
    /** The reader's own genre or subgenre ([app.familiar.domain.OwnGenre] name); empty for none. */
    @ColumnInfo(defaultValue = "") val sub: String = "",
) {
    val tagList: List<String> get() = tags.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    /** The last page you can reach: the book's end, or no limit when its length isn't known. */
    val cap: Int get() = if (pages > 0) pages else 99_999

    val shelfOf: Shelf get() = runCatching { Shelf.valueOf(shelf) }.getOrDefault(Shelf.READING)
    val fraction: Float get() = if (pages <= 0) 0f else (page.toFloat() / pages).coerceIn(0f, 1f)

    companion object {
        /** The page (or minute) [percent] of the way through a book of [pages]. */
        fun atPercent(pages: Int, percent: Int): Int = (pages * percent.coerceIn(0, 100) + 50) / 100

        /** How far [page] is through a book of [pages], in whole percent; 0 when its length isn't known. */
        fun percentOf(pages: Int, page: Int): Int = if (pages <= 0) 0 else (page.coerceIn(0, pages) * 100 / pages)
    }
}

/** One sitting with a book: a ritual with the candle, or pages logged by hand. */
@Entity(tableName = "readings")
data class Reading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** The familiar that ate the mana. */
    val petId: Long,
    val start: Long,
    val end: Long,
    val fromPage: Int,
    val toPage: Int,
    val mana: Int,
    /** Read with the candle (a timed ritual) rather than logged afterwards. */
    val timed: Boolean,
    /** Seconds the phone lay face down. */
    val stillSec: Int = 0,
    /** Something from the book the slime swallowed. */
    val item: String = "",
    /** This sitting finished the book. */
    val finishedBook: Boolean = false,
    /** Listened rather than read, even if the book is on paper too. */
    @ColumnInfo(defaultValue = "0") val listened: Boolean = false,
) {
    val pages: Int get() = (toPage - fromPage).coerceAtLeast(0)
    /** What this sitting gave: its pages, or its minutes when no page was given (listening, reading by time). */
    val amount: Int get() = if (pages > 0) pages else if (timed) minutes else 0
    /** It counts as a day of reading. */
    val counts: Boolean get() = amount > 0
    val minutes: Int get() = ((end - start) / 60_000).toInt()
}

/** A familiar that has joined the den. [mana] is everything it has eaten. */
@Entity(tableName = "pets")
data class Pet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [app.familiar.domain.Species] name. */
    val species: String,
    val name: String,
    val joined: Long,
    val mana: Int = 0,
    val active: Boolean = false,
    /** Hatched in a rare colouring. */
    val shiny: Boolean = false,
    /** [app.familiar.domain.Trait] name; empty for familiars from before traits. */
    @ColumnInfo(defaultValue = "") val trait: String = "",
    /** [app.familiar.domain.Form] name, chosen once it is grown; empty before. */
    @ColumnInfo(defaultValue = "") val form: String = "",
)

/** Something a familiar brought back from a sitting (or a guest left), kept in the casket. */
@Entity(tableName = "finds")
data class Find(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [app.familiar.domain.Trinket] name. */
    val kind: String,
    val at: Long,
    val petId: Long,
    /** The sitting it came from; 0 for a guest's gift. */
    val readingId: Long = 0,
)

class Snapshot(val books: List<Book>, val readings: List<Reading>, val pets: List<Pet>, val finds: List<Find> = emptyList())

@Dao
abstract class FamiliarDao {
    @Query("SELECT * FROM books ORDER BY added DESC")
    abstract fun observeBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY added DESC")
    abstract suspend fun books(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id")
    abstract suspend fun book(id: Long): Book?

    @Insert abstract suspend fun insertBook(b: Book): Long
    @Update abstract suspend fun updateBook(b: Book)
    @Delete abstract suspend fun deleteBook(b: Book)

    @Query("SELECT * FROM readings ORDER BY start")
    abstract fun observeReadings(): Flow<List<Reading>>

    @Query("SELECT * FROM readings ORDER BY start")
    abstract suspend fun readings(): List<Reading>

    @Insert abstract suspend fun insertReading(r: Reading): Long
    @Update abstract suspend fun updateReading(r: Reading)
    @Delete abstract suspend fun deleteReading(r: Reading)

    @Query("DELETE FROM readings WHERE bookId = :bookId")
    abstract suspend fun deleteReadingsOf(bookId: Long)

    @Query("SELECT * FROM pets ORDER BY joined")
    abstract fun observePets(): Flow<List<Pet>>

    @Query("SELECT * FROM pets ORDER BY joined")
    abstract suspend fun pets(): List<Pet>

    @Query("SELECT * FROM pets WHERE active = 1 LIMIT 1")
    abstract suspend fun activePet(): Pet?

    @Insert abstract suspend fun insertPet(p: Pet): Long
    @Update abstract suspend fun updatePet(p: Pet)

    @Query("UPDATE pets SET mana = mana + :mana WHERE id = :id")
    abstract suspend fun feed(id: Long, mana: Int)

    @Query("UPDATE pets SET active = (id = :id)")
    abstract suspend fun activate(id: Long)

    @Query("SELECT * FROM finds ORDER BY at")
    abstract fun observeFinds(): Flow<List<Find>>

    @Query("SELECT * FROM finds ORDER BY at")
    abstract suspend fun finds(): List<Find>

    @Insert abstract suspend fun insertFind(f: Find): Long

    /** Removes a book with its sittings. */
    @Transaction
    open suspend fun removeBook(b: Book) {
        deleteReadingsOf(b.id)
        deleteBook(b)
    }

    @Transaction
    open suspend fun snapshot() = Snapshot(books(), readings(), pets(), finds())

    @Query("DELETE FROM books") protected abstract suspend fun clearBooks()
    @Query("DELETE FROM readings") protected abstract suspend fun clearReadings()
    @Query("DELETE FROM pets") protected abstract suspend fun clearPets()
    @Query("DELETE FROM finds") protected abstract suspend fun clearFinds()
    @Insert protected abstract suspend fun insertFinds(l: List<Find>)
    @Insert protected abstract suspend fun insertBooks(l: List<Book>)
    @Insert protected abstract suspend fun insertReadings(l: List<Reading>)
    @Insert protected abstract suspend fun insertPets(l: List<Pet>)

    /** Books brought from another site, all or none. */
    @Transaction
    open suspend fun addBooks(l: List<Book>) = insertBooks(l)

    @Transaction
    open suspend fun replaceAll(s: Snapshot) {
        clearBooks(); clearReadings(); clearPets(); clearFinds()
        insertBooks(s.books); insertReadings(s.readings); insertPets(s.pets); insertFinds(s.finds)
    }
}

// Every schema change gets a new version and a migration that keeps what's there (1.1.0 lost data without one).
@Database(
    entities = [Book::class, Reading::class, Pet::class, Find::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class FamiliarDatabase : RoomDatabase() {
    abstract fun dao(): FamiliarDao

    companion object {
        fun create(context: Context, name: String = "familiar.db"): FamiliarDatabase =
            Room.databaseBuilder(context.applicationContext, FamiliarDatabase::class.java, name)
                // Only a database older than the migrations (1.0.0) starts fresh (and onboarding again, see FamiliarApp).
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
