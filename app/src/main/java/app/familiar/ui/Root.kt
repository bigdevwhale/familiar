package app.familiar.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.familiar.BuildConfig
import app.familiar.R
import app.familiar.container
import app.familiar.data.Book
import app.familiar.data.Find
import app.familiar.domain.Form
import app.familiar.domain.Guard
import app.familiar.domain.Season
import app.familiar.domain.Trait
import app.familiar.domain.Trinket
import app.familiar.domain.Visit
import app.familiar.data.Library
import app.familiar.data.Pet
import app.familiar.data.Reading
import app.familiar.data.Settings
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.domain.Stats
import app.familiar.notify.Alarms
import app.familiar.ui.books.BookEditScreen
import app.familiar.ui.books.BookScreen
import app.familiar.ui.books.LogScreen
import app.familiar.ui.books.ShelfTab
import app.familiar.ui.components.CelebrateDialog
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.rememberNow
import app.familiar.ui.den.BestiaryScreen
import app.familiar.ui.den.DenTab
import app.familiar.ui.me.SettingsScreen
import app.familiar.ui.onboarding.OnboardingScreen
import app.familiar.ui.ritual.FeastScreen
import app.familiar.ui.ritual.RitualScreen
import app.familiar.ui.theme.Nunito
import app.familiar.ui.theme.U
import app.familiar.ui.tree.SagaScreen
import app.familiar.ui.tree.TreeTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class Tab { DEN, SHELF, TREE }

/** Everything stored, plus what is derived from it, recomputed once per minute or change. */
@Immutable
class FamiliarData(
    val books: List<Book>,
    val readings: List<Reading>,
    val pets: List<Pet>,
    val settings: Settings,
    val now: Long,
    val finds: List<Find> = emptyList(),
) {
    val stats = Stats(books, readings, pets, now, ZoneId.systemDefault(), settings.dailyGoal, settings.guardedDays)
    val pet: Pet? = stats.active
    val species: Species? = stats.activeSpecies
    val trait: Trait? = pet?.let { Trait.of(it.trait, it.joined) }
    // Demo builds (for screenshots) show the den dressed for Samhain.
    val season: Season = if (BuildConfig.DEMO) Season.SAMHAIN else Season.of(stats.today)
    val reading: List<Book> = books.filter { it.shelfOf == app.familiar.data.Shelf.READING }.sortedByDescending { b -> readings.filter { it.bookId == b.id }.maxOfOrNull { it.end } ?: b.added }
    fun book(id: Long) = books.firstOrNull { it.id == id }
    /** Your own genres and subgenres, with any that only books remember (after a restore). */
    val genres: List<app.familiar.domain.OwnGenre> = app.familiar.domain.OwnGenre.all(settings.genres, books)
    /** Your own shelves, with any that only books remember. */
    val shelves: List<String> = (settings.shelves + books.flatMap { it.tagList }).distinct()
    fun look(chew: Boolean = false, sleeping: Boolean = false) = species?.let {
        lookOf(it, pet?.mana ?: 0, stats, true, sleeping, chew, pet?.shiny == true, Form.of(pet?.form ?: ""), season, trait)
    }
    fun lookOfPet(p: Pet) = lookOf(Species.of(p.species), p.mana, stats, p.active, shiny = p.shiny, form = Form.of(p.form), season = season, trait = Trait.of(p.trait, p.joined))

    /** A friend's familiar staying in the den today, or null. */
    val guest: Visit? = settings.guest.takeIf { settings.guestUntil > now }?.let { Visit.decode(it) }
    val guestLook: app.familiar.ui.pix.Look? = guest?.let {
        val st = Stage.of(it.mana)
        app.familiar.ui.pix.Look(it.species, st, shiny = it.shiny && st != Stage.EGG, form = if (st >= Stage.ADULT) it.form else Form.NONE, season = season, hatch = 0.5f)
    }
    val owned: Set<Trinket> = finds.mapNotNull { Trinket.of(it.kind) }.toSet()
}

/** Everything a tab can open. */
class Go(private val nav: NavHostController) {
    fun ritual(bookId: Long) = nav.navigate("ritual/$bookId") { launchSingleTop = true }
    fun log(bookId: Long) = nav.navigate("log/$bookId")
    fun feast() = nav.navigate("feast") { popUpTo("main") }
    fun book(id: Long) = nav.navigate("book/$id")
    fun editBook(id: Long = 0, series: String = "") = nav.navigate("edit/$id?series=" + android.net.Uri.encode(series))
    fun bestiary() = nav.navigate("bestiary")
    fun saga() = nav.navigate("saga")
    fun stats() = nav.navigate("stats")
    fun furnish() = nav.navigate("furnish")
    fun series(name: String) = nav.navigate("series/" + android.net.Uri.encode(name))
    fun settings() = nav.navigate("settings")
    fun casket() = nav.navigate("casket")
    fun lottery() = nav.navigate("lottery")
    fun visit() = nav.navigate("visit")
    fun share(kind: app.familiar.ui.share.Cards.Kind, bookId: Long = 0) = nav.navigate("share/${kind.name}?book=$bookId")
    fun back() = nav.popBackStack()
}

/** The last sitting's feast, for the screen that shows it. */
val lastFeast = MutableStateFlow<Library.Result?>(null)

@Composable
fun FamiliarRoot(settings: Settings, dim: (Boolean) -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val nav = rememberNavController()
    val go = remember(nav) { Go(nav) }
    var tab by rememberSaveable { mutableStateOf(Tab.DEN) }
    val start = remember { if (settings.onboarded) "main" else "welcome" }

    val dao = container.db.dao()
    val books by dao.observeBooks().collectAsStateWithLifecycle(initialValue = null)
    val readings by dao.observeReadings().collectAsStateWithLifecycle(initialValue = null)
    val pets by dao.observePets().collectAsStateWithLifecycle(initialValue = null)
    val finds by dao.observeFinds().collectAsStateWithLifecycle(initialValue = null)
    // Nothing is drawn from the database until all of it has arrived, so an empty den never flashes by.
    val loaded = books != null && readings != null && pets != null && finds != null
    val minute = rememberNow(60_000)
    val data = remember(books, readings, pets, finds, settings, minute) {
        FamiliarData(books.orEmpty(), readings.orEmpty(), pets.orEmpty(), settings, System.currentTimeMillis(), finds.orEmpty())
    }
    // Guardian candles burn by themselves for the quiet days since the last reading.
    LaunchedEffect(loaded, data.stats.today, settings.candles, readings?.size) {
        if (!loaded || !settings.onboarded) return@LaunchedEffect
        val burn = Guard.toBurn(data.stats.readDays, settings.guardedDays, settings.candles, data.stats.today)
        if (burn.isNotEmpty()) container.settings.update { s ->
            val days = burn.map { it.toString() }.filter { it !in s.guarded }
            if (days.isEmpty() || s.candles < days.size) s
            else s.copy(candles = s.candles - days.size, guarded = s.guarded + days, burnedNews = s.burnedNews + days)
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // A ritual left burning comes back to the front once, when the app opens.
    LaunchedEffect(Unit) {
        settings.ritual?.let { if (settings.onboarded) go.ritual(it.bookId) }
    }
    // Onboarded elsewhere (a restored backup, the demo seed): leave the welcome screens.
    LaunchedEffect(settings.onboarded, route) {
        if (settings.onboarded && route == "welcome") nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
        // Reset elsewhere (a fresh database): start over with an egg.
        if (!settings.onboarded && route == "main") nav.navigate("welcome") { popUpTo("main") { inclusive = true } }
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.scope.launch { container.changed() }
    }
    LaunchedEffect(settings.onboarded) {
        if (settings.onboarded && Build.VERSION.SDK_INT >= 33 && !Alarms.canNotify(context) && !BuildConfig.DEMO) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier.background(U.c.bg),
        enterTransition = { fadeIn(tween(240)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) },
    ) {
        composable("welcome") {
            OnboardingScreen { nav.navigate("main") { popUpTo("welcome") { inclusive = true } } }
        }
        composable("main") { if (loaded) MainTabs(tab, { tab = it }, go, data) else Box(Modifier.fillMaxSize().background(U.c.bg)) }
        composable("ritual/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            RitualScreen(e.arguments?.getLong("id") ?: 0L, data, go, dim)
        }
        composable("log/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            LogScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable("feast") { FeastScreen(data, go) }
        composable("book/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            BookScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable("edit/{id}?series={series}", arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("series") { type = NavType.StringType; defaultValue = "" })) { e ->
            BookEditScreen(e.arguments?.getLong("id") ?: 0L, data, go, e.arguments?.getString("series").orEmpty())
        }
        composable("bestiary") { BestiaryScreen(data, go) }
        composable("saga") { SagaScreen(data, go) }
        composable("stats") { app.familiar.ui.tree.StatsScreen(data, go) }
        composable("furnish") { app.familiar.ui.den.FurnishScreen(data, go) }
        composable("series/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) { e ->
            app.familiar.ui.books.SeriesScreen(e.arguments?.getString("name").orEmpty(), data, go)
        }
        composable("settings") { SettingsScreen(data, onBack = { go.back() }) }
        composable("casket") { app.familiar.ui.den.CasketScreen(data, go) }
        composable("lottery") { app.familiar.ui.books.LotteryScreen(data, go) }
        composable("visit") { app.familiar.ui.den.VisitScreen(data, go) }
        composable("share/{kind}?book={book}", arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("book") { type = NavType.LongType; defaultValue = 0L })) { e ->
            val kind = app.familiar.ui.share.Cards.Kind.entries.firstOrNull { it.name == e.arguments?.getString("kind") } ?: app.familiar.ui.share.Cards.Kind.MONTH
            if (loaded) app.familiar.ui.share.ShareScreen(kind, e.arguments?.getLong("book") ?: 0L, data, go)
        }
    }

    if (settings.onboarded && loaded && route == "main") Celebrations(data)
}

/** A hatching, a new stage, a newly found familiar: each celebrated once. */
@Composable
private fun Celebrations(data: FamiliarData) {
    val container = LocalContext.current.container
    val s = data.settings
    val pet = data.pet
    val sp = data.species
    val stageKey = pet?.let { "${it.id}:${data.stats.stage.name}" }
    val found = data.stats.unlocked.filter { it.name !in s.seenUnlocks && data.pets.none { p -> p.species == it.name } }
    when {
        pet != null && sp != null && data.stats.stage == Stage.ADULT && pet.form.isNotBlank() && stageKey !in s.seenStages -> {
            val form = Form.of(pet.form)
            CelebrateDialog(
                color = Color(form.argb),
                eyebrow = stringResource(R.string.cel_form),
                title = stringResource(R.string.form_line, formName(form)),
                text = stringResource(R.string.cel_form_text, pet.name, formName(form)),
                onDismiss = { container.scope.launch { container.settings.update { it.copy(seenStages = it.seenStages + stageKey!!) } } },
            ) { FamiliarArt(data.look()!!, Modifier.fillMaxSize()) }
        }
        pet != null && sp != null && data.stats.stage != Stage.EGG && stageKey !in s.seenStages -> {
            val hatched = data.stats.stage == Stage.BABY
            CelebrateDialog(
                color = U.c.accent,
                eyebrow = if (hatched && pet.shiny) stringResource(R.string.cel_shiny) else stringResource(if (hatched) R.string.cel_hatched else R.string.cel_grew),
                title = if (hatched) stringResource(R.string.cel_hatched_title, pet.name) else stageName(data.stats.stage),
                text = stringResource(if (hatched) R.string.cel_hatched_text else R.string.cel_grew_text, pet.name) +
                    if (hatched && pet.shiny) "\n\n" + stringResource(R.string.cel_shiny_text, Library.SHINY_ODDS) else "",
                onDismiss = { container.scope.launch { container.settings.update { it.copy(seenStages = it.seenStages + stageKey!!) } } },
            ) { FamiliarArt(data.look()!!, Modifier.fillMaxSize()) }
        }
        found.isNotEmpty() -> {
            val f = found.first()
            CelebrateDialog(
                color = U.c.gold,
                eyebrow = stringResource(R.string.cel_found),
                title = speciesName(f),
                text = stringResource(R.string.cel_found_text),
                onDismiss = { container.scope.launch { container.settings.update { it.copy(seenUnlocks = it.seenUnlocks + found.map { x -> x.name }) } } },
            ) { FamiliarArt(lookOf(f, 0, data.stats, false), Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun MainTabs(tab: Tab, onTab: (Tab) -> Unit, go: Go, data: FamiliarData) {
    Column(Modifier.fillMaxSize().background(U.c.bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.DEN -> DenTab(data, go, onTab)
                Tab.SHELF -> ShelfTab(data, go)
                Tab.TREE -> TreeTab(data, go)
            }
        }
        HorizontalDivider(color = U.c.line)
        Row(
            Modifier.fillMaxWidth().background(U.c.surface).navigationBarsPadding().height(66.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Pets, stringResource(R.string.tab_den), tab == Tab.DEN) { onTab(Tab.DEN) }
            NavItem(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.tab_shelf), tab == Tab.SHELF) { onTab(Tab.SHELF) }
            NavItem(Icons.Rounded.Forest, stringResource(R.string.tab_tree), tab == Tab.TREE) { onTab(Tab.TREE) }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) U.c.accent else U.c.faint
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) U.c.accentSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp),
        ) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
        Spacer(Modifier.height(3.dp))
        Text(label, fontFamily = Nunito, fontSize = 11.5.sp, color = color, maxLines = 1)
    }
}
