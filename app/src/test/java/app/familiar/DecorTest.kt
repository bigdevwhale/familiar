package app.familiar

import app.familiar.ui.books.Decor
import org.junit.Assert.assertEquals
import org.junit.Test

/** Things on the shelf of spines: each stands in one place, can be moved, and can be taken away. */
class DecorTest {
    private val saved = "1|89|PLANT\n2|9|CANDLES\n3|93|SKULL\n4|93|SKULL"

    @Test
    fun aThingSavedTwiceStandsOnce() {
        assertEquals(listOf("PLANT", "CANDLES", "SKULL"), Decor.parse(saved).map { it.key })
        assertEquals(3L, Decor.parse(saved).single { it.key == "SKULL" }.id)
    }

    @Test
    fun takingAwayLeavesNothingBehind() {
        val skull = Decor.parse(saved).single { it.key == "SKULL" }
        val after = Decor.parse(Decor.put(saved, skull, ""))
        assertEquals(listOf("PLANT", "CANDLES"), after.map { it.key })
    }

    @Test
    fun aNewPlaceForAThingOnTheShelfMovesIt() {
        // "+" at the end of another plank, and the plant picked: it moves there, it isn't copied.
        val fresh = Decor.Placed(99, 150, "")
        val after = Decor.parse(Decor.put(saved, fresh, "PLANT"))
        assertEquals(1, after.count { it.key == "PLANT" })
        assertEquals(150L, after.single { it.key == "PLANT" }.after)
        // Picking something else for the same place replaces it.
        val again = Decor.parse(Decor.put(Decor.encode(after), fresh, "OWL"))
        assertEquals(listOf("CANDLES", "SKULL", "OWL"), again.map { it.key })
        // A new place left empty saves nothing.
        assertEquals(Decor.parse(saved), Decor.parse(Decor.put(saved, Decor.Placed(100, 5, ""), "")))
    }
}
