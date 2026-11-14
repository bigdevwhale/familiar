package app.familiar.domain

/** What a book is about. Each one the kitsune tastes grows her a tail of its colour. */
enum class Genre(val argb: Long, val mood: Mood) {
    FANTASY(0xFF8E6BD8, Mood.SPARKLE),
    SCIFI(0xFF3FB0C9, Mood.STARS),
    MYSTERY(0xFF4F6C9E, Mood.SUSPICIOUS),
    HORROR(0xFFB8394A, Mood.SCARED),
    ROMANCE(0xFFE2789C, Mood.HEARTS),
    ADVENTURE(0xFFE08A3C, Mood.SPARKLE),
    CLASSIC(0xFFC9A04A, Mood.GLASSES),
    HISTORY(0xFFA4673E, Mood.GLASSES),
    NONFICTION(0xFF4E9A69, Mood.GLASSES),
    POETRY(0xFFC27FD0, Mood.HEARTS),
    OTHER(0xFF8C8577, Mood.NONE);

    companion object {
        fun of(name: String): Genre = entries.firstOrNull { it.name == name } ?: OTHER

        /** Genres that grow a tail: all but OTHER, nine of them at most. */
        val tails = entries.filter { it != OTHER }
    }
}

/** How the familiar looks after the last book: hiding after horror, blushing after romance. */
enum class Mood { NONE, SPARKLE, STARS, SUSPICIOUS, SCARED, HEARTS, GLASSES }

/** The familiars of the den. The first three can hatch at the start; the rest are found by reading. */
enum class Species(val starter: Boolean) {
    MIMIC(true), KITSUNE(true), SLIME(true),
    DRAGON(false), PHOENIX(false), GRIFFIN(false), OWLBEAR(false), MOTH(false),
    TANUKI(false), SPROUT(false), GHOST(false), WYRM(false),
    CHIMERA(false), MAGPIE(false),
    HYDRA(false), BAYUN(false), SNAIL(false), SALAMANDER(false), AUTOMATON(false),
    PIXIE(false), RAVEN(false), SPHINX(false), OWLET(false);

    companion object {
        fun of(name: String): Species = entries.firstOrNull { it.name == name } ?: MIMIC
    }
}

/** Growth by mana eaten. An egg hatches after its first twenty pages. */
enum class Stage(val from: Int) {
    EGG(0), BABY(20), YOUNG(300), ADULT(1200), ANCIENT(4000);

    val next: Stage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(mana: Int): Stage = entries.last { mana >= it.from }

        /** 0..1 on the way to the next stage; 1 when ancient. */
        fun progress(mana: Int): Float {
            val s = of(mana)
            val n = s.next ?: return 1f
            return ((mana - s.from).toFloat() / (n.from - s.from)).coerceIn(0f, 1f)
        }
    }
}

/** A familiar's character, given when the egg is taken. It colours what it says and a habit or two. */
enum class Trait {
    /** Falls asleep an hour earlier and dozes on your lap sooner. */
    SLEEPY,
    /** Chews longer at the feast and always asks for more. */
    GLUTTON,
    /** Brings back a find more often. */
    CURIOUS,
    /** Wants a longer stroke before it purrs. */
    GRUMPY,
    /** Hops twice when tapped. */
    PLAYFUL,
    /** Never trembles after horror. */
    BRAVE;

    companion object {
        /** The stored trait, or one picked by the day it joined for familiars from before traits. */
        fun of(name: String, joined: Long): Trait = entries.firstOrNull { it.name == name } ?: entries[Math.floorMod((joined / 1000).toInt() * 31 + 7, entries.size)]
    }
}

/**
 * The form a familiar takes when it grows up, after the kind of books it was fed on. It is chosen
 * when the familiar becomes grown and stays for good.
 */
enum class Form(val argb: Long, vararg val genres: Genre) {
    /** Not chosen yet, or nothing was read. */
    NONE(0xFF8C8577),
    ASTRAL(0xFF5C6CE0, Genre.FANTASY, Genre.SCIFI),
    SHADOW(0xFF5B3F86, Genre.MYSTERY, Genre.HORROR),
    BLOOM(0xFFE47FA6, Genre.ROMANCE, Genre.POETRY),
    SAGE(0xFFC9A04A, Genre.CLASSIC, Genre.HISTORY, Genre.NONFICTION),
    WILD(0xFF5FA85A, Genre.ADVENTURE, Genre.OTHER);

    companion object {
        fun of(name: String): Form = entries.firstOrNull { it.name == name } ?: NONE
        fun of(g: Genre): Form = entries.first { g in it.genres }

        /** Share of each form in what a familiar ate: genre of each sitting's book, weighed by its mana. */
        fun leaning(meals: List<Pair<Genre, Int>>): Map<Form, Float> {
            val total = meals.sumOf { it.second }.takeIf { it > 0 } ?: return emptyMap()
            return meals.groupBy { of(it.first) }.mapValues { (_, l) -> l.sumOf { it.second } / total.toFloat() }
        }

        /** The leading form; ties go to the one listed first. */
        fun choose(meals: List<Pair<Genre, Int>>): Form =
            leaning(meals).entries.sortedWith(compareByDescending<Map.Entry<Form, Float>> { it.value }.thenBy { it.key.ordinal }).firstOrNull()?.key ?: NONE
    }
}
