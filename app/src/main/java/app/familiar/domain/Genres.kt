package app.familiar.domain

import app.familiar.data.Book
import kotlin.math.abs

/**
 * A genre of the reader's own: a subgenre inside one of the built-in genres ("Dark fantasy" in
 * fantasy), or one that stands by itself ("Manga") when its [parent] is OTHER. The built-in genre
 * still carries the magic (mood, tails, forms, finds, dreams); the own one sorts the shelf and
 * feeds the chimera and the magpie.
 */
class OwnGenre(val name: String, val parent: Genre) {
    val standalone: Boolean get() = parent == Genre.OTHER
    /** The same name may be a subgenre of several genres ("Mysticism" in horror and in mystery): each is its own. */
    val key: String get() = key(name, parent)

    /** A shade of the parent's colour, or a colour of its own for a genre standing by itself. */
    val argb: Long get() {
        // By the name alone, so the colours stay what they were before keys knew the parent.
        val h = Math.floorMod(key(name).hashCode(), 9_973)
        if (standalone) return PALETTE[h % PALETTE.size]
        val t = SHADES[h % SHADES.size]
        return mix(parent.argb, if (t > 0) 0xFFFFF6E0 else 0xFF1E0E28, abs(t))
    }

    fun encode(): String = parent.name + "|" + name

    override fun equals(other: Any?) = other is OwnGenre && other.key == key
    override fun hashCode() = key.hashCode()

    companion object {
        const val MAX_NAME = 30

        fun key(name: String): String = name.trim().lowercase()

        fun key(name: String, parent: Genre): String = parent.name + "|" + key(name)

        fun decode(line: String): OwnGenre? {
            val i = line.indexOf('|')
            val n = line.substring(i + 1).trim()
            if (i <= 0 || n.isEmpty()) return null
            return OwnGenre(n, Genre.of(line.substring(0, i)))
        }

        /** The own genre a book is in, or null. */
        fun of(b: Book): OwnGenre? = b.sub.trim().takeIf { it.isNotEmpty() }?.let { OwnGenre(it, Genre.of(b.genre)) }

        /** Every own genre: the ones made, in order, then any found only on books (after a restore, say). */
        fun all(saved: List<OwnGenre>, books: List<Book>): List<OwnGenre> =
            (saved + books.sortedBy { it.added }.mapNotNull { of(it) }).distinctBy { it.key }

        private val PALETTE = listOf(0xFF2E9A93, 0xFF9C9A3A, 0xFFD0663F, 0xFF6C7FD8, 0xFFB0589E, 0xFF5E8C6A, 0xFFCC9058, 0xFF7A6AA8)
        private val SHADES = listOf(-0.3f, -0.18f, 0.16f, 0.3f)

        private fun mix(a: Long, b: Long, t: Float): Long {
            fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t)).toLong()
            return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
