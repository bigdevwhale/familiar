package app.familiar

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import app.familiar.data.FamiliarDatabase
import app.familiar.data.M10_11
import app.familiar.data.M11_12
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A database left by 1.18 (version 10) opens with its books, and their stars become halves (#60, #67). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test fun version10KeepsItsBooksAndDoublesTheStars() = runBlocking {
        val ctx: android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
        val name = "m10.db"
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val schema = JSONObject(File("schemas/app.familiar.data.FamiliarDatabase/10.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { ix -> for (k in 0 until ix.length()) db.execSQL(ix.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO books (id, title, author, pages, genre, shelf, page, added, rating, cover, audio, series, tags, sub, note, ebook) VALUES (1, 'Dune', '', 600, 'FANTASY', 'DONE', 600, 0, 4, '', 0, '', '', '', '', 0)")
            db.version = 10
        }
        val room = Room.databaseBuilder(ctx, FamiliarDatabase::class.java, name).addMigrations(M10_11, M11_12).allowMainThreadQueries().build()
        val books = room.dao().books()
        assertEquals(1, books.size)
        assertEquals(8, books.single().rating)
        assertFalse(room.dao().quotes().any { it.note })
        room.close()
    }
}
