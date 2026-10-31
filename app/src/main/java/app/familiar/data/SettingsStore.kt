package app.familiar.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Background sound during a ritual, synthesised on the phone. */
enum class Ambience { OFF, FIRE, RAIN }

/** A ritual in progress; kept in settings so it survives the app being closed. */
data class Ritual(val bookId: Long, val start: Long, val fromPage: Int, val page: Int, val stillMs: Long, val listen: Boolean = false)

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboarded: Boolean = false,
    val name: String = "",
    /** Pages a day that make the familiar full. */
    val dailyGoal: Int = 20,
    /** How long the ritual candle burns, minutes. */
    val candleMin: Int = 30,
    val ambience: Ambience = Ambience.FIRE,
    /** A rustle when a page is turned. */
    val pageSound: Boolean = true,
    val haptics: Boolean = true,
    /** Dim the screen while the phone lies face down. */
    val stillDim: Boolean = true,
    /** Daily reminder, minutes after midnight; -1 when off. */
    val remindAt: Int = -1,
    val ritual: Ritual? = null,
    /** Species already celebrated as found. */
    val seenUnlocks: Set<String> = emptySet(),
    /** "petId:stage" already celebrated. */
    val seenStages: Set<String> = emptySet(),
    /** "yyyy-mm:questId" whose reward was taken. */
    val claimed: Set<String> = emptySet(),
    /** Sparks spent on the den; sparks earned equal all mana ever eaten. */
    val spent: Int = 0,
    /** Den furniture bought, and the pieces standing in the room. */
    val owned: Set<String> = emptySet(),
    val placed: Set<String> = emptySet(),
    /** Books to finish this year; 0 when there's no goal. */
    val yearGoal: Int = 0,
    /** In a ritual, count pages at the end instead of turning them on screen. */
    val pagesAtEnd: Boolean = false,
    /** The user's own shelves, in the order they were made. */
    val shelves: List<String> = emptyList(),
    /** Guardian candles waiting to keep the streak. */
    val candles: Int = 0,
    /** Quiet days a candle burned for, yyyy-mm-dd. */
    val guarded: Set<String> = emptySet(),
    /** Days burned for that the den hasn't told about yet. */
    val burnedNews: Set<String> = emptySet(),
    /** The morning a dream was last told, yyyy-mm-dd. */
    val dreamDay: String = "",
    /** Genres dreamt of at least once, for the album. */
    val dreams: Set<String> = emptySet(),
    /** A guest's visiting card and when it leaves. */
    val guest: String = "",
    val guestUntil: Long = 0,
    /** "guest key|yyyy-mm-dd" of gifts already left, one a day per guest. */
    val gifts: Set<String> = emptySet(),
    /** The reader's own genres and subgenres, in the order they were made. */
    val genres: List<app.familiar.domain.OwnGenre> = emptyList(),
    /** How the shelf is sorted ([app.familiar.ui.books.Sort] name), and shown as a list rather than covers. */
    val shelfSort: String = "",
    val shelfList: Boolean = false,
    /** The tree bears only this year's books rather than every book ever finished. */
    val treeYear: Boolean = false,
) {
    val guardedDays: Set<java.time.LocalDate> get() = guarded.mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }.toSet()
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        theme = this[THEME]?.let { t -> runCatching { ThemeMode.valueOf(t) }.getOrNull() } ?: ThemeMode.SYSTEM,
        onboarded = this[ONBOARDED] ?: false,
        name = this[NAME] ?: "",
        dailyGoal = this[DAILY_GOAL] ?: 20,
        candleMin = this[CANDLE_MIN] ?: 30,
        ambience = this[AMBIENCE]?.let { a -> runCatching { Ambience.valueOf(a) }.getOrNull() } ?: Ambience.FIRE,
        pageSound = this[PAGE_SOUND] ?: true,
        haptics = this[HAPTICS] ?: true,
        stillDim = this[STILL_DIM] ?: true,
        remindAt = this[REMIND_AT] ?: -1,
        ritual = (this[R_BOOK] ?: 0L).takeIf { it > 0 }?.let {
            Ritual(it, this[R_START] ?: 0L, this[R_FROM] ?: 0, this[R_PAGE] ?: 0, this[R_STILL] ?: 0L, this[R_LISTEN] ?: false)
        },
        seenUnlocks = this[SEEN_UNLOCKS] ?: emptySet(),
        seenStages = this[SEEN_STAGES] ?: emptySet(),
        claimed = this[CLAIMED] ?: emptySet(),
        spent = this[SPENT] ?: 0,
        owned = this[OWNED] ?: emptySet(),
        placed = this[PLACED] ?: emptySet(),
        yearGoal = this[YEAR_GOAL] ?: 0,
        pagesAtEnd = this[PAGES_AT_END] ?: false,
        shelves = (this[SHELVES] ?: "").split('\n').filter { it.isNotBlank() },
        candles = this[CANDLES] ?: 0,
        guarded = this[GUARDED] ?: emptySet(),
        burnedNews = this[BURNED_NEWS] ?: emptySet(),
        dreamDay = this[DREAM_DAY] ?: "",
        dreams = this[DREAMS] ?: emptySet(),
        guest = this[GUEST] ?: "",
        guestUntil = this[GUEST_UNTIL] ?: 0L,
        gifts = this[GIFTS] ?: emptySet(),
        genres = (this[GENRES] ?: "").split('\n').mapNotNull { app.familiar.domain.OwnGenre.decode(it) },
        shelfSort = this[SHELF_SORT] ?: "",
        shelfList = this[SHELF_LIST] ?: false,
        treeYear = this[TREE_YEAR] ?: false,
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[THEME] = s.theme.name
            it[ONBOARDED] = s.onboarded
            it[NAME] = s.name
            it[DAILY_GOAL] = s.dailyGoal
            it[CANDLE_MIN] = s.candleMin
            it[AMBIENCE] = s.ambience.name
            it[PAGE_SOUND] = s.pageSound
            it[HAPTICS] = s.haptics
            it[STILL_DIM] = s.stillDim
            it[REMIND_AT] = s.remindAt
            val r = s.ritual
            it[R_BOOK] = r?.bookId ?: 0L
            it[R_START] = r?.start ?: 0L
            it[R_FROM] = r?.fromPage ?: 0
            it[R_PAGE] = r?.page ?: 0
            it[R_STILL] = r?.stillMs ?: 0L
            it[R_LISTEN] = r?.listen ?: false
            it[SEEN_UNLOCKS] = s.seenUnlocks
            it[SEEN_STAGES] = s.seenStages
            it[CLAIMED] = s.claimed
            it[SPENT] = s.spent
            it[OWNED] = s.owned
            it[PLACED] = s.placed
            it[YEAR_GOAL] = s.yearGoal
            it[PAGES_AT_END] = s.pagesAtEnd
            it[SHELVES] = s.shelves.joinToString("\n")
            it[CANDLES] = s.candles
            it[GUARDED] = s.guarded
            it[BURNED_NEWS] = s.burnedNews
            it[DREAM_DAY] = s.dreamDay
            it[DREAMS] = s.dreams
            it[GUEST] = s.guest
            it[GUEST_UNTIL] = s.guestUntil
            it[GIFTS] = s.gifts
            it[GENRES] = s.genres.joinToString("\n") { g -> g.encode() }
            it[SHELF_SORT] = s.shelfSort
            it[SHELF_LIST] = s.shelfList
            it[TREE_YEAR] = s.treeYear
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NAME = stringPreferencesKey("name")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val CANDLE_MIN = intPreferencesKey("candle_min")
        val AMBIENCE = stringPreferencesKey("ambience")
        val PAGE_SOUND = booleanPreferencesKey("page_sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val STILL_DIM = booleanPreferencesKey("still_dim")
        val REMIND_AT = intPreferencesKey("remind_at")
        val R_BOOK = longPreferencesKey("ritual_book")
        val R_START = longPreferencesKey("ritual_start")
        val R_FROM = intPreferencesKey("ritual_from")
        val R_PAGE = intPreferencesKey("ritual_page")
        val R_STILL = longPreferencesKey("ritual_still")
        val R_LISTEN = booleanPreferencesKey("ritual_listen")
        val SEEN_UNLOCKS = stringSetPreferencesKey("seen_unlocks")
        val SEEN_STAGES = stringSetPreferencesKey("seen_stages")
        val CLAIMED = stringSetPreferencesKey("claimed")
        val SPENT = intPreferencesKey("spent")
        val OWNED = stringSetPreferencesKey("owned")
        val PLACED = stringSetPreferencesKey("placed")
        val YEAR_GOAL = intPreferencesKey("year_goal")
        val PAGES_AT_END = booleanPreferencesKey("pages_at_end")
        val SHELVES = stringPreferencesKey("shelves")
        val CANDLES = intPreferencesKey("candles")
        val GUARDED = stringSetPreferencesKey("guarded")
        val BURNED_NEWS = stringSetPreferencesKey("burned_news")
        val DREAM_DAY = stringPreferencesKey("dream_day")
        val DREAMS = stringSetPreferencesKey("dreams")
        val GUEST = stringPreferencesKey("guest")
        val GUEST_UNTIL = longPreferencesKey("guest_until")
        val GIFTS = stringSetPreferencesKey("gifts")
        val GENRES = stringPreferencesKey("genres")
        val SHELF_SORT = stringPreferencesKey("shelf_sort")
        val SHELF_LIST = booleanPreferencesKey("shelf_list")
        val TREE_YEAR = booleanPreferencesKey("tree_year")
    }
}
