package app.familiar

import android.database.sqlite.SQLiteDatabase
import app.familiar.data.FamiliarDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** An update keeps what's there: a database from the last release opens with its books and familiars. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun aVersion3DatabaseKeepsItsBooks() = runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath(NAME).apply { parentFile?.mkdirs(); delete() }
        // Build the 1.2.0 database exactly as its exported schema describes it.
        val schema = JSONObject(File("schemas/app.familiar.data.FamiliarDatabase/3.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO books (id, title, author, pages, genre, shelf, page, added, cover, audio, series, tags) VALUES (1, 'Dune', 'Frank Herbert', 612, 'SCIFI', 'DONE', 612, 0, '', 0, '', '')")
            db.execSQL("INSERT INTO pets (id, species, name, joined, mana, active, shiny, trait, form) VALUES (1, 'KITSUNE', 'Yuzu', 0, 900, 1, 0, '', '')")
            db.version = 3
        }
        val db = FamiliarDatabase.create(context, NAME)
        val books = db.dao().books()
        assertEquals(listOf("Dune"), books.map { it.title })
        assertEquals("", books.single().sub)
        assertEquals("", books.single().note)
        assertEquals(900, db.dao().pets().single().mana)
        db.close()
    }

    // Not the app's own file: the test app opens that one at start.
    private companion object { const val NAME = "migration-test.db" }
}
