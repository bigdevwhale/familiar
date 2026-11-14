package app.familiar.domain

import java.time.LocalDate

/**
 * Where a piece stands in the den; one piece per place. [TOY]: a plaything on the floor, which the familiar
 * bats about. [ROOM]: the den itself, its walls and floor, in place of the attic it starts as.
 */
enum class Place { WINDOW, WALL, TOP, FLOOR_LEFT, FLOOR_RIGHT, RUG, LIGHT, TOY, ROOM }

/** Den furniture, bought with sparks. */
enum class Item(val place: Place, val price: Int) {
    WINDOW_MOON(Place.WINDOW, 300),
    WINDOW_STAINED(Place.WINDOW, 650),
    BOOKSHELF(Place.WALL, 450),
    PORTRAIT(Place.WALL, 350),
    BANNER(Place.TOP, 250),
    GARLAND(Place.TOP, 300),
    ARMCHAIR(Place.FLOOR_LEFT, 450),
    CAULDRON(Place.FLOOR_LEFT, 550),
    TELESCOPE(Place.FLOOR_LEFT, 750),
    MANDRAKE(Place.FLOOR_RIGHT, 250),
    TREASURE(Place.FLOOR_RIGHT, 600),
    GLOBE(Place.FLOOR_RIGHT, 500),
    RUG_RED(Place.RUG, 200),
    RUG_STARS(Place.RUG, 400),
    CANDLES(Place.LIGHT, 150),
    LANTERN(Place.LIGHT, 350),

    // The second lot: a hearth, things for the walls, playthings, and two other dens to live in.
    FIREPLACE(Place.FLOOR_LEFT, 900),
    AQUARIUM(Place.FLOOR_RIGHT, 650),
    MUSHROOMS(Place.FLOOR_RIGHT, 300),
    CLOCK(Place.WALL, 500),
    MAP(Place.WALL, 400),
    DREAMCATCHER(Place.TOP, 350),
    RUG_MOSS(Place.RUG, 300),
    FIREFLIES(Place.LIGHT, 400),
    YARN(Place.TOY, 150),
    PLUSH(Place.TOY, 250),
    ROOM_TOWER(Place.ROOM, 1200),
    ROOM_BURROW(Place.ROOM, 1200);

    companion object {
        fun of(name: String): Item? = entries.firstOrNull { it.name == name }
    }
}

/** What goes on outside the den's window: it follows the time of year, and changes from day to day. */
enum class Sky {
    CLEAR, RAIN, SNOW, LEAVES, PETALS;

    companion object {
        /** The same for everyone on [day]: snow on most winter days, petals in spring, leaves in autumn, rain now and then. */
        fun of(day: LocalDate): Sky {
            val h = day.toEpochDay() * 1_103_515_245L + 12_345L
            val roll = Math.floorMod((h ushr 16) xor (h ushr 7), 10L).toInt()
            return when (day.monthValue) {
                12, 1, 2 -> if (roll < 6) SNOW else CLEAR
                3, 4, 5 -> if (roll < 4) PETALS else if (roll < 6) RAIN else CLEAR
                6, 7, 8 -> if (roll < 2) RAIN else CLEAR
                else -> if (roll < 4) LEAVES else if (roll < 7) RAIN else CLEAR
            }
        }
    }
}

object Den {
    /** Sparks: every mana a familiar ever ate leaves one; furniture costs them, growth doesn't. */
    fun sparks(pets: List<app.familiar.data.Pet>, spent: Int): Int = (pets.sumOf { it.mana } - spent).coerceAtLeast(0)

    /** Placing [item] takes its place from whatever stood there. */
    fun place(placed: Set<String>, item: Item): Set<String> =
        placed.filter { Item.of(it)?.place != item.place }.toSet() + item.name
}
