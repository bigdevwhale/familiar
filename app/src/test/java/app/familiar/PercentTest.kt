package app.familiar

import app.familiar.data.Book
import org.junit.Assert.assertEquals
import org.junit.Test

/** A percent typed for an e-book or an audiobook reads back the same (#29). */
class PercentTest {
    @Test
    fun typedPercentReadsBack() {
        for (pages in 100..2000) for (p in 0..100) {
            assertEquals("$p% of $pages", p, Book.percentOf(pages, Book.atPercent(pages, p)))
        }
    }

    @Test
    fun theEndIsTheLastPage() {
        assertEquals(287, Book.atPercent(287, 100))
        assertEquals(0, Book.atPercent(287, 0))
    }
}
