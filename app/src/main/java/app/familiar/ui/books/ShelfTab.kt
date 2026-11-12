package app.familiar.ui.books

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Book
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import app.familiar.domain.OwnGenre
import app.familiar.domain.Series
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.bookColor
import app.familiar.ui.bookGenre
import app.familiar.ui.books
import app.familiar.ui.components.Bar
import app.familiar.ui.components.BookCover
import app.familiar.ui.components.Chip
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.UTextField
import app.familiar.ui.genreColor
import app.familiar.ui.genreName
import app.familiar.ui.locale
import app.familiar.ui.progressLabel
import app.familiar.ui.shelfName
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle

@Composable
fun ShelfTab(data: FamiliarData, go: Go) {
    // Which shelf is open: everything, a built-in one, the cycles, or one of your own ("own:<name>").
    var view by rememberSaveable { mutableStateOf(Shelf.READING.name) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Genre filters, one key per line (see Browse.genreKey).
    var genreKeys by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(Format.ANY) }
    var stars by rememberSaveable { mutableIntStateOf(0) }
    // The cycle filtered by, its volumes then in order; empty for any.
    var cycle by rememberSaveable { mutableStateOf("") }
    val browse = Browse.sorted(data.settings.shelfSort).copy(query = query, genres = genreKeys.split('\n').filter { it.isNotEmpty() }.toSet(), format = format, stars = stars, series = cycle)
    fun reset() { query = ""; genreKeys = ""; format = Format.ANY; stars = 0; cycle = "" }

    // A shelf just made may not be in settings for a moment yet; it's still the one to show.
    val own = view.removePrefix(OWN).takeIf { view.startsWith(OWN) }
    val shelf: Shelf? = Shelf.entries.firstOrNull { it.name == view }
    val all = when {
        view == ALL -> data.books
        own != null -> data.books.filter { own in it.tagList }
        shelf == Shelf.READING -> data.books.filter { it.shelfOf == Shelf.READING || it.shelfOf == Shelf.PAUSED }
        shelf != null -> data.books.filter { it.shelfOf == shelf }
        else -> emptyList()
    }
    val loc = locale()
    val genreNames = Genre.entries.associateWith { genreName(it) }
    val list = remember(all, data.readings, browse, loc) { browse.apply(all, data.readings, pausedLast = shelf == Shelf.READING, locale = loc, genreNames = genreNames) }
    val cycles = remember(data.books, query) {
        Series.all(data.books).filter { c -> query.isBlank() || query.trim().lowercase().let { q -> q in c.name.lowercase() || c.books.any { q in it.title.lowercase() } } }
    }
    val spineMode = data.settings.shelfSpines
    val listMode = data.settings.shelfList && !spineMode
    // The planks, packed for the width there is: the screen less the grid's side padding and the plank's own.
    val room = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 36 - 12
    // A cycle's volumes stand together under its name, unless the shelf is narrowed to that one cycle.
    // Decorating: each plank shows a place at its end to put something on.
    var decorMode by rememberSaveable { mutableStateOf(false) }
    // What stands at the end of each plank takes its room there.
    val decor = remember(data.settings.shelfDecor) { Decor.parse(data.settings.shelfDecor) }
    val look = PlankLook.of(data.settings.shelfWood, data.settings.shelfTrim)
    val planks = remember(list, room, spineMode, cycle, decor, decorMode) {
        if (spineMode) Spines.planks(Spines.groups(list, cycles = cycle.isBlank(), decor = decor), room) { if (decorMode) Decor.room(Decor.SLOT) else 0 } else emptyList()
    }
    // The book held down on the shelf of spines, to change how it stands and looks; the plank held, to decorate it.
    var holding by remember { mutableStateOf<Long?>(null) }
    var decorating by remember { mutableStateOf<Decor.Placed?>(null) }
    val container = androidx.compose.ui.platform.LocalContext.current.container
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val drag = remember { ShelfDrag() }
    drag.movable = view != CYCLES
    drag.onHold = { holding = it.id }
    drag.onThingHold = { decorating = it }
    // A thing put down by a book stands after it, or after the one before it.
    drag.onThingDrop = { o, by, after ->
        val shown = planks.flatten().flatMap { it.books }
        val i = shown.indexOfFirst { it.id == by.id }
        val anchor = if (after) by.id else shown.getOrNull(i - 1)?.id ?: 0L
        container.scope.launch {
            container.settings.update { s -> s.copy(shelfDecor = Decor.encode(Decor.parse(s.shelfDecor).map { if (it.id == o.id) it.copy(after = anchor) else it })) }
        }
    }
    drag.onDrop = { b, by, after ->
        // The books in the order they stand, the carried one moved; a pile it's put by takes it in, and one
        // carried out of a pile stands up.
        val shown = planks.flatten().flatMap { it.books }
        val order = shown.filter { it.id != b.id }.toMutableList()
        val at = order.indexOfFirst { it.id == by.id }
        if (at >= 0) {
            order.add(if (after) at + 1 else at, b)
            val pose = when {
                by.pile.isNotEmpty() -> by.pose
                by.poseOf == app.familiar.data.Pose.LIE -> app.familiar.data.Pose.LIE.name
                b.poseOf == app.familiar.data.Pose.LIE -> ""
                else -> b.pose
            }
            // Every book keeps its place among the others; those shown here take theirs in the new order.
            val ids = order.map { it.id }.toSet()
            val next = order.iterator()
            val all = data.books.sortedWith(Browse.manual).map { x -> if (x.id in ids) next.next() else x }
            container.write {
                all.forEachIndexed { i, x ->
                    val y = (if (x.id == b.id) x.copy(pose = pose) else x).copy(place = i + 1)
                    if (y != x) dao().updateBook(y)
                }
            }
            if (browse.sort != Sort.MANUAL) container.scope.launch { container.settings.update { it.copy(shelfSort = Sort.MANUAL.name) } }
        }
    }
    // Carried near the top or bottom, the shelf scrolls, faster the nearer.
    val gridSpan = remember { floatArrayOf(0f, 0f) }
    val carrying = drag.active
    androidx.compose.runtime.LaunchedEffect(carrying) {
        while (drag.active) {
            androidx.compose.runtime.withFrameNanos { }
            val y = drag.pointer.y - gridSpan[0]
            val h = gridSpan[1]
            val e = h * 0.12f
            val v = when { y < e -> -(e - y) / e * 22f; y > h - e -> (y - (h - e)) / e * 22f; else -> 0f }
            if (v != 0f) grid.scrollBy(v)
        }
    }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    var filtering by remember { mutableStateOf(false) }
    var changingGenre by remember { mutableStateOf<OwnGenre?>(null) }
    var removingGenre by remember { mutableStateOf<OwnGenre?>(null) }
    // The one own genre filtered by, to rename or delete it right here.
    val oneOwn = browse.genres.singleOrNull()?.let { k -> data.genres.firstOrNull { Browse.genreKey(it) == k } }
    // The tabs in the order they stand, so a swipe sideways goes to the one beside (#45).
    val tabs = listOf(ALL, Shelf.READING.name, Shelf.WANT.name, Shelf.DONE.name) +
        listOfNotNull(Shelf.ABANDONED.name.takeIf { a -> view == a || data.books.any { it.shelfOf == Shelf.ABANDONED } }) +
        listOf(CYCLES) + data.shelves.map { OWN + it }
    val tabRow = androidx.compose.foundation.lazy.rememberLazyListState()
    // The tab picked by a swipe comes into sight in the row of tabs.
    LaunchedEffect(view) { tabs.indexOf(view).takeIf { it >= 0 }?.let { tabRow.animateScrollToItem(maxOf(0, it - 1)) } }
    val swipe = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    androidx.compose.runtime.CompositionLocalProvider(LocalShelfDrag provides drag) {
    Carrying(drag, Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxSize().statusBarsPadding()
                // Sideways across the shelf: the next tab or the one before. A book being carried, the row of
                // tabs and the chips scroll on their own and don't count.
                .pointerInput(tabs, view) {
                    var moved = 0f
                    detectHorizontalDragGestures(onDragStart = { moved = 0f }, onDragEnd = {
                        val i = tabs.indexOf(view)
                        val next = when {
                            moved < -swipe -> i + 1
                            moved > swipe -> i - 1
                            else -> i
                        }
                        if (i >= 0 && next != i && next in tabs.indices) view = tabs[next]
                    }) { change, amount -> change.consume(); moved += amount }
                }
                .onGloballyPositioned { c -> c.boundsInRoot().let { r -> gridSpan[0] = r.top; gridSpan[1] = r.height } },
            state = grid,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (listMode) 10.dp else 16.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                Column {
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tab_shelf), style = UType.Display, modifier = Modifier.weight(1f))
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, stringResource(R.string.lib_search), tint = if (searching) U.c.accent else U.c.muted)
                        }
                        // Decorating the planks, only where there are planks.
                        if (spineMode) IconButton(onClick = { decorMode = !decorMode }) {
                            Icon(Icons.Rounded.LocalFlorist, stringResource(R.string.shelf_decorate), tint = if (decorMode) U.c.accent else U.c.muted)
                        }
                        ViewMenu(if (spineMode) View.SPINES else if (listMode) View.LIST else View.COVERS) { v ->
                            container.scope.launch { container.settings.update { it.copy(shelfList = v == View.LIST, shelfSpines = v == View.SPINES) } }
                        }
                    }
                    if (searching) UTextField(query, { query = it.take(60) }, Modifier.padding(top = 6.dp), placeholder = stringResource(R.string.lib_search_hint))
                    Spacer(Modifier.height(8.dp))
                    // Which shelf: tabs, underlined when open, so they never look like the filters below.
                    LazyRow(state = tabRow, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        fun count(sh: Shelf) = data.books.count { it.shelfOf == sh || sh == Shelf.READING && it.shelfOf == Shelf.PAUSED }
                        item { ShelfTabLabel(stringResource(R.string.lib_all), view == ALL, count = data.books.size) { view = ALL } }
                        // The abandoned shelf only once a book has been put down.
                        val shelves = listOf(Shelf.READING, Shelf.WANT, Shelf.DONE) + listOfNotNull(Shelf.ABANDONED.takeIf { a -> view == a.name || data.books.any { it.shelfOf == a } })
                        items(shelves) { sh -> ShelfTabLabel(shelfName(sh), view == sh.name, count = count(sh)) { view = sh.name } }
                        item { ShelfTabLabel(stringResource(R.string.cycles), view == CYCLES, count = cycles.size.takeIf { query.isBlank() }) { view = CYCLES } }
                        items(data.shelves) { n -> ShelfTabLabel(n, own == n, U.c.gold, count = data.books.count { n in it.tagList }) { view = OWN + n } }
                        item { ShelfTabLabel("+ " + stringResource(R.string.shelf_new), false, U.c.gold) { adding = true } }
                    }
                    HorizontalDivider(color = U.c.line)
                    if (view != CYCLES) {
                        Spacer(Modifier.height(10.dp))
                        // How the shelf is shown: plain buttons with icons, then the filters that are on, as chips to take off.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            item { SortChip(browse) { s -> container.scope.launch { container.settings.update { it.copy(shelfSort = s.saved) } } } }
                            item {
                                val n = browse.filters
                                ToolButton(Icons.Rounded.Tune, stringResource(R.string.lib_filter) + if (n > 0) " · $n" else "", n > 0) { filtering = true }
                            }
                            // The genres filtered by, each one tap away from going.
                            items(browse.genres.toList()) { k ->
                                val o = data.genres.firstOrNull { Browse.genreKey(it) == k }
                                val g = Genre.entries.firstOrNull { Browse.genreKey(it) == k }
                                Chip("✕ " + (o?.name ?: g?.let { genreName(it) } ?: k), true, { genreKeys = (browse.genres - k).joinToString("\n") }, o?.let { Color(it.argb) } ?: g?.let { genreColor(it) } ?: U.c.accent)
                            }
                            if (cycle.isNotBlank()) item { Chip("✕ " + cycle.take(28), true, { cycle = "" }, U.c.gold) }
                            if (browse.filters > 1 || browse.filters > 0 && query.isNotBlank()) item { TextButton(onClick = { reset() }) { Text(stringResource(R.string.lib_reset), color = U.c.accent, style = UType.Button) } }
                        }
                        Text(
                            if (browse.filters > 0 || query.isNotBlank()) stringResource(R.string.lib_of, list.size, all.size) else books(list.size),
                            style = UType.Small.copy(color = U.c.muted), maxLines = 1, modifier = Modifier.padding(start = 8.dp),
                        )
                        }
                    }
                    val narrowed = browse.filters > 0 || query.isNotBlank()
                    if (own != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        run {
                            TextButton(onClick = { renaming = own }) { Text(stringResource(R.string.shelf_rename), color = U.c.accent, style = UType.Button) }
                            TextButton(onClick = { removing = own }) { Text(stringResource(R.string.delete), color = U.c.muted, style = UType.Button) }
                        }
                    }
                    if (oneOwn != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.genre_line, oneOwn.name), style = UType.Small.copy(color = Color(oneOwn.argb)), modifier = Modifier.weight(1f))
                        TextButton(onClick = { changingGenre = oneOwn }) { Text(stringResource(R.string.genre_change_short), color = U.c.accent, style = UType.Button) }
                        TextButton(onClick = { removingGenre = oneOwn }) { Text(stringResource(R.string.delete), color = U.c.muted, style = UType.Button) }
                    }
                    if (view == CYCLES && cycles.isEmpty()) Text(stringResource(if (query.isBlank()) R.string.cycles_empty else R.string.lib_nothing), style = UType.Sub, modifier = Modifier.padding(top = 24.dp))
                    if (own != null && all.isEmpty()) Text(stringResource(R.string.shelf_own_empty), style = UType.Sub, modifier = Modifier.padding(top = 16.dp))
                    if (shelf == Shelf.WANT && all.size >= 2 && !narrowed) app.familiar.ui.components.SoftButton(
                        stringResource(R.string.lottery_short), { go.lottery() },
                        Modifier.fillMaxWidth().padding(top = 12.dp), color = U.c.gold, soft = U.c.goldSoft, icon = Icons.Rounded.Casino,
                    )
                    if (all.isEmpty() && own == null && view != CYCLES) Text(
                        stringResource(when (shelf) { Shelf.READING -> R.string.empty_reading; Shelf.WANT -> R.string.empty_want; Shelf.DONE -> R.string.empty_done; Shelf.ABANDONED -> R.string.empty_abandoned; else -> R.string.empty_all }),
                        style = UType.Sub, modifier = Modifier.padding(top = 24.dp),
                    )
                    if (shelf == Shelf.ABANDONED && all.isNotEmpty()) Text(stringResource(R.string.graveyard), style = UType.Sub.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(top = 8.dp))
                    if (all.isNotEmpty() && list.isEmpty()) Text(stringResource(R.string.lib_nothing), style = UType.Sub, modifier = Modifier.padding(top = 24.dp))
                }
            }
            if (view == CYCLES) items(cycles, key = { it.key }, span = { GridItemSpan(3) }) { c ->
                if (spineMode) CycleShelf(c, room + 12, { go.series(c.name) }, { holding = it.id }) { go.book(it.id) } else CycleCard(c) { go.series(c.name) }
            }
            else if (spineMode) {
                if (decorMode) item(span = { GridItemSpan(3) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.shelf_decorate_hint), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.weight(1f))
                        TextButton(onClick = { decorMode = false }) { Text(stringResource(R.string.done), color = U.c.accent, style = UType.Button) }
                    }
                }
                // Until a book has been turned or laid down, a word on how.
                else if (list.isNotEmpty() && decor.isEmpty() && data.books.none { it.pose.isNotEmpty() || it.spine.isNotEmpty() || it.place != 0 }) item(span = { GridItemSpan(3) }) {
                    Text(stringResource(R.string.pose_hint), style = UType.Small.copy(color = U.c.muted, fontStyle = FontStyle.Italic))
                }
                items(planks, key = { row -> "plank:" + row.first().key }, span = { GridItemSpan(3) }) { row ->
                    Plank(
                        row, { go.book(it.id) }, { holding = it.id }, look = look, onThing = { decorating = it },
                        onAdd = if (decorMode) { { decorating = Decor.Placed(System.currentTimeMillis(), row.lastOrNull()?.books?.lastOrNull()?.id ?: 0L, "") } } else null,
                    )
                }
            }
            else if (listMode) items(list, key = { it.id }, span = { GridItemSpan(3) }) { b -> BookRow(b, view == ALL) { go.book(b.id) } }
            else items(list, key = { it.id }) { b ->
                Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { go.book(b.id) }) {
                    Box {
                        // An abandoned book fades like an old gravestone, its epitaph the reader's reason.
                        BookCover(b, Modifier.fillMaxWidth().aspectRatio(0.68f).graphicsLayer { alpha = if (b.shelfOf == Shelf.ABANDONED) FADED else 1f })
                        if (b.audio) Box(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(U.c.bg.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Headphones, stringResource(R.string.f_audio), tint = U.c.ink, modifier = Modifier.size(13.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(b.title, style = UType.Small.copy(color = U.c.ink, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (b.author.isNotBlank()) Text(b.author, style = UType.Small.copy(color = U.c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (b.shelfOf == Shelf.ABANDONED && b.note.isNotBlank()) Text(b.note, style = UType.Small.copy(fontStyle = FontStyle.Italic), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (b.shelfOf == Shelf.READING || b.shelfOf == Shelf.PAUSED) {
                        Spacer(Modifier.height(4.dp))
                        val paused = b.shelfOf == Shelf.PAUSED
                        if (b.pages > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                            Bar(b.fraction, Modifier.weight(1f).height(5.dp), color = if (paused) U.c.faint else U.c.accent, track = U.c.accentSoft)
                            Text("${Book.percentOf(b.pages, b.page)}%", style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(start = 6.dp))
                        }
                        if (paused) Text(shelfName(Shelf.PAUSED), style = UType.Small.copy(color = U.c.muted))
                    }
                    if (b.shelfOf == Shelf.DONE && b.rating != null) Text("★".repeat(b.rating), style = UType.Small.copy(color = U.c.gold))
                }
            }
        }
        FloatingActionButton(
            onClick = { go.editBook() },
            containerColor = U.c.accent,
            contentColor = U.c.onAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Rounded.Add, stringResource(R.string.book_add)) }
    }
    }
    holding?.let { id -> data.books.firstOrNull { it.id == id }?.let { b -> BookLookSheet(b, data.books) { holding = null } } }
    decorating?.let { o -> ShelfLookSheet(data.settings.shelfWood, data.settings.shelfTrim, data.settings.shelfDecor, o, data.owned) { decorating = null } }
    if (filtering) FilterSheet(
        data, all, browse,
        onChange = { b -> genreKeys = b.genres.joinToString("\n"); format = b.format; stars = b.stars; cycle = b.series },
        onDismiss = { filtering = false },
    )
    if (adding) ShelfNameDialog(onDismiss = { adding = false }) { n ->
        container.scope.launch { Shelves.add(container, n) }
        view = OWN + n
    }
    renaming?.let { r -> ShelfNameDialog(r, onDismiss = { renaming = null }) { n -> container.scope.launch { Shelves.rename(container, r, n) }; view = OWN + n } }
    removing?.let { r ->
        app.familiar.ui.components.ConfirmDialog(
            title = stringResource(R.string.shelf_delete_title, r),
            text = stringResource(R.string.shelf_delete_text),
            confirm = stringResource(R.string.delete),
            danger = true,
            onConfirm = { container.scope.launch { Shelves.remove(container, r) }; view = Shelf.READING.name },
            onDismiss = { removing = null },
        )
    }
    changingGenre?.let { g ->
        GenreDialog(g.parent, g, data.genres, onDismiss = { changingGenre = null }) { to ->
            container.scope.launch { OwnGenres.change(container, g, to) }
            genreKeys = Browse.genreKey(to)
        }
    }
    removingGenre?.let { g ->
        app.familiar.ui.components.ConfirmDialog(
            title = stringResource(R.string.genre_delete_title, g.name),
            text = stringResource(R.string.genre_delete_text),
            confirm = stringResource(R.string.delete),
            danger = true,
            onConfirm = { container.scope.launch { OwnGenres.remove(container, g) }; genreKeys = "" },
            onDismiss = { removingGenre = null },
        )
    }
}

private const val OWN = "own:"
private const val CYCLES = "cycles"
private const val ALL = "all"

/** How the shelf is shown. */
private enum class View { COVERS, LIST, SPINES }

/** Covers, a list or spines on planks: the icon shows the one on, the menu offers all three. */
@Composable
private fun ViewMenu(current: View, onPick: (View) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun icon(v: View) = when (v) { View.COVERS -> Icons.Rounded.GridView; View.LIST -> Icons.Rounded.ViewList; View.SPINES -> Icons.Rounded.ViewWeek }
    @Composable fun name(v: View) = stringResource(when (v) { View.COVERS -> R.string.lib_as_covers; View.LIST -> R.string.lib_as_list; View.SPINES -> R.string.lib_as_spines })
    Box {
        IconButton(onClick = { open = true }) { Icon(icon(current), stringResource(R.string.lib_view), tint = U.c.muted) }
        DropdownMenu(open, { open = false }, containerColor = U.c.bg) {
            View.entries.forEach { v ->
                val on = v == current
                DropdownMenuItem(
                    text = { Text(name(v), style = if (on) UType.Strong.copy(color = U.c.accent) else UType.Body) },
                    leadingIcon = { Icon(icon(v), null, tint = if (on) U.c.accent else U.c.muted) },
                    onClick = { onPick(v); open = false },
                )
            }
        }
    }
}

/** The order of the shelf; picking the same one again turns it round. */
@Composable
private fun SortChip(browse: Browse, onSort: (Browse) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(Icons.AutoMirrored.Rounded.Sort, sortName(browse.sort) + if (browse.reverse) " ↑" else "", false) { open = true }
        DropdownMenu(open, { open = false }, containerColor = U.c.bg) {
            Sort.entries.forEach { s ->
                val on = s == browse.sort
                DropdownMenuItem(
                    text = { Text((if (on) (if (browse.reverse) "↑ " else "↓ ") else "    ") + sortName(s), style = if (on) UType.Strong.copy(color = U.c.accent) else UType.Body) },
                    onClick = { onSort(if (on) browse.copy(reverse = !browse.reverse) else browse.copy(sort = s, reverse = false)); open = false },
                )
            }
        }
    }
}

/** A shelf to open: its name, underlined in [color] while it's the one shown. */
@Composable
private fun ShelfTabLabel(text: String, selected: Boolean, color: Color = U.c.accent, count: Int? = null, onClick: () -> Unit) {
    Column(
        Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(top = 8.dp)
            .semantics { this.selected = selected; role = Role.Tab },
    ) {
        Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, maxLines = 1, style = if (selected) UType.Strong.copy(color = color) else UType.Body.copy(color = if (color == U.c.accent) U.c.muted else color.copy(alpha = 0.8f)))
            if (count != null && count > 0) Text(
                count.toString(), maxLines = 1,
                style = UType.Small.copy(color = if (selected) color else U.c.faint, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (selected) color else Color.Transparent))
    }
}

/** How the shelf is shown (order, filters): a small icon button, tinted while something is on. */
@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, on: Boolean, onClick: () -> Unit) {
    val tint = if (on) U.c.accent else U.c.muted
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = UType.Small.copy(color = tint, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun sortName(s: Sort): String = stringResource(
    when (s) {
        Sort.RECENT -> R.string.sort_recent
        Sort.ADDED -> R.string.sort_added
        Sort.TITLE -> R.string.sort_title
        Sort.AUTHOR -> R.string.sort_author
        Sort.RATING -> R.string.sort_rating
        Sort.PROGRESS -> R.string.sort_progress
        Sort.LENGTH -> R.string.sort_length
        Sort.SERIES -> R.string.sort_series
        Sort.MANUAL -> R.string.sort_manual
    }
)

/** A book as a line: a small cover, the title, author, genre and how far along. */
@Composable
private fun BookRow(b: Book, withShelf: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        BookCover(b, Modifier.width(46.dp).height(66.dp).graphicsLayer { alpha = if (b.shelfOf == Shelf.ABANDONED) FADED else 1f }, small = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, style = UType.Strong, maxLines = 2)
            if (b.author.isNotBlank()) Text(b.author, style = UType.Muted, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (b.audio) Icon(Icons.Rounded.Headphones, null, tint = U.c.faint, modifier = Modifier.padding(end = 4.dp).height(13.dp).width(13.dp))
                Text(bookGenre(b) + if (withShelf) " · " + shelfName(b.shelfOf) else "", style = UType.Small.copy(color = bookColor(b)), maxLines = 1)
            }
            when {
                b.shelfOf == Shelf.DONE -> if (b.rating != null) Text("★".repeat(b.rating), style = UType.Small.copy(color = U.c.gold))
                b.shelfOf == Shelf.ABANDONED -> if (b.note.isNotBlank()) Text("«" + b.note + "»", style = UType.Small.copy(fontStyle = FontStyle.Italic), maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                b.shelfOf == Shelf.READING || b.shelfOf == Shelf.PAUSED -> {
                    if (b.pages > 0) Bar(b.fraction, Modifier.padding(top = 4.dp).height(4.dp), color = if (b.shelfOf == Shelf.PAUSED) U.c.faint else U.c.accent, track = U.c.accentSoft)
                    Text(progressLabel(b) + if (b.shelfOf == Shelf.PAUSED) " · " + shelfName(Shelf.PAUSED) else "", style = UType.Small, modifier = Modifier.padding(top = 2.dp))
                }
                else -> {}
            }
        }
    }
}

/** Genres, paper or audio, and stars: what the shelf is narrowed to. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(data: FamiliarData, shown: List<Book>, browse: Browse, onChange: (Browse) -> Unit, onDismiss: () -> Unit) {
    val container = androidx.compose.ui.platform.LocalContext.current.container
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The sheet animates shut on the screen's own scope: the app's background one has no frame clock.
    val ui = androidx.compose.runtime.rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val names = Genre.entries.associateWith { genreName(it) }
    fun toggle(k: String) = onChange(browse.copy(genres = if (k in browse.genres) browse.genres - k else browse.genres + k))
    // Only the genres there are books in, so the sheet stays short; anything picked stays too.
    val present = shown.map { Genre.of(it.genre) }.toSet()
    val ownPresent = shown.mapNotNull { OwnGenre.of(it)?.key }.toSet()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = U.c.bg) {
        // The filters scroll; the buttons stay at the bottom. A fixed height: a sheet sized by what it holds
        // measures itself again as it's dragged, and jumps against the scroll inside.
        Column(Modifier.fillMaxHeight(SHEET_HEIGHT).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.lib_filter), style = UType.Title)
            SectionTitle(stringResource(R.string.f_genre))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Genre.entries.filter { it in present || Browse.genreKey(it) in browse.genres }.forEach { g ->
                    Chip(genreName(g), Browse.genreKey(g) in browse.genres, { toggle(Browse.genreKey(g)) }, genreColor(g))
                }
            }
            val owns = data.genres.filter { it.key in ownPresent || Browse.genreKey(it) in browse.genres }
            SectionTitle(stringResource(R.string.genres_own))
            if (owns.isEmpty()) Text(stringResource(R.string.genres_own_empty), style = UType.Muted, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                owns.forEach { o -> Chip(o.name, Browse.genreKey(o) in browse.genres, { toggle(Browse.genreKey(o)) }, Color(o.argb)) }
                Chip("+ " + stringResource(R.string.genre_own), false, { adding = true }, U.c.gold)
            }
            // The cycles on this shelf, one at a time: picking one shows its volumes in order.
            val cycles = remember(shown) { Series.all(shown).map { it.name }.sortedWith(java.text.Collator.getInstance(java.util.Locale.getDefault()).apply { strength = java.text.Collator.PRIMARY }) }
            if (cycles.isNotEmpty() || browse.series.isNotBlank()) {
                SectionTitle(stringResource(R.string.lib_cycle))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (cycles + listOfNotNull(browse.series.takeIf { it.isNotBlank() && cycles.none { c -> c.equals(it, true) } })).forEach { c ->
                        val on = c.equals(browse.series, true)
                        Chip(c.take(28), on, { onChange(browse.copy(series = if (on) "" else c)) }, U.c.gold)
                    }
                }
            }
            SectionTitle(stringResource(R.string.lib_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Format.ANY to R.string.lib_any, Format.PAPER to R.string.lib_paper, Format.EBOOK to R.string.lib_ebook, Format.AUDIO to R.string.lib_audio).forEach { (f, res) ->
                    Chip(stringResource(res), browse.format == f, { onChange(browse.copy(format = f)) })
                }
            }
            SectionTitle(stringResource(R.string.your_rating))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.lib_any), browse.stars == 0, { onChange(browse.copy(stars = 0)) }, U.c.gold)
                (3..5).forEach { n -> Chip("★".repeat(n) + if (n < 5) "+" else "", browse.stars == n, { onChange(browse.copy(stars = n)) }, U.c.gold) }
            }
        }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(browse.copy(genres = emptySet(), format = Format.ANY, stars = 0, series = "")) }) { Text(stringResource(R.string.lib_reset), color = U.c.muted, style = UType.Button) }
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.lib_show, books(shown.count { b -> browse.matches(b, names) })), {
                    ui.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                })
            }
        }
    }
    if (adding) GenreDialog(Genre.OTHER, taken = data.genres, onDismiss = { adding = false }) { g ->
        container.scope.launch { OwnGenres.add(container, g) }
    }
}

/** A cycle: its covers fanned out, and how many volumes are read. */
@Composable
fun CycleCard(c: Series, onClick: () -> Unit) {
    app.familiar.ui.components.UCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy((-22).dp)) {
                c.books.take(4).forEach { b ->
                    BookCover(b, Modifier.width(46.dp).height(66.dp), small = true)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = UType.Strong, maxLines = 2)
                Text(stringResource(R.string.cycle_of, c.done, c.books.size), style = UType.Muted.copy(color = if (c.complete) U.c.gold else U.c.muted))
                c.next?.takeIf { c.done > 0 }?.let { n -> Text(stringResource(R.string.cycle_next, n.title), style = UType.Small.copy(color = U.c.accent), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(6.dp))
                Bar(c.done / c.books.size.toFloat(), Modifier.height(5.dp), color = U.c.gold, track = U.c.goldSoft)
            }
        }
    }
}

/** How much of an abandoned book's cover still shows. */
private const val FADED = 0.45f

/** How much of the screen the filter sheet takes, whatever is in it. */
private const val SHEET_HEIGHT = 0.85f
