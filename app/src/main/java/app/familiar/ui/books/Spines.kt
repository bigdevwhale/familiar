package app.familiar.ui.books

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familiar.data.Book
import app.familiar.data.Covers
import app.familiar.data.Pose
import app.familiar.data.Shelf
import app.familiar.data.SpineStyle
import app.familiar.domain.Genre
import app.familiar.domain.OwnGenre
import app.familiar.ui.components.BookCover
import app.familiar.ui.theme.Cormorant
import app.familiar.ui.theme.U

/** How the shelf is laid out on the planks: which books stand together on each one. */
object Spines {
    /** Thin books are thin: about a dp for every 28 pages, an audiobook's minutes counted at half. */
    fun width(b: Book): Int {
        val pages = if (b.audio) b.pages / 2 else b.pages
        return if (pages <= 0) 22 else (14 + pages / 28).coerceIn(16, 44)
    }

    /** Books aren't all one height; each keeps its own, the same every time it's shown. */
    fun height(b: Book): Int = 132 + Math.floorMod((b.title.hashCode() * 31 + b.id.toInt()), 33)

    /** Lying down, a book reaches this far along the plank. */
    fun length(b: Book): Int = height(b) * 3 / 4

    /** Facing out, a book is as wide as its cover. */
    fun faceWidth(b: Book): Int = height(b) * 2 / 3

    /** Something on a plank: one book on end or facing out, a pile of books lying one on another, or an [ornament]. */
    class Piece(val pose: Pose, val books: List<Book>, val ornament: Decor.Placed? = null) {
        constructor(o: Decor.Placed) : this(Pose.STAND, emptyList(), o)
        val width: Int = when {
            ornament != null -> Decor.width(ornament.key)
            pose == Pose.STAND -> width(books.first())
            pose == Pose.FACE -> faceWidth(books.first())
            else -> books.maxOf { length(it) }
        }
    }

    /** Books that stand together: a cycle's volumes in order under its [name], or loose books ([name] empty). */
    class Group(val name: String, val pieces: List<Piece>) {
        val width: Int = pieces.sumOf { it.width } + GAP * (pieces.size - 1).coerceAtLeast(0)
        /** Its own for as long as its first book (or thing) stays first. */
        val key: String get() = pieces.first().let { p -> p.books.firstOrNull()?.let { "b" + it.id } ?: ("o" + p.ornament?.id) }
        val books: List<Book> get() = pieces.flatMap { it.books }
    }

    /**
     * Neighbours lying down make one pile, no taller than [PILE] books; a pile made by hand ([Book.pile])
     * holds just its own books, however many.
     */
    fun pieces(books: List<Book>, decor: Map<Long, List<Decor.Placed>> = emptyMap()): List<Piece> {
        val out = mutableListOf<Piece>()
        var pile = mutableListOf<Book>()
        // A thing put after a book stands right after it, or after the pile it lies in.
        fun emit(p: Piece) { out += p; p.books.forEach { b -> decor[b.id]?.forEach { out += Piece(it) } } }
        fun flush() { if (pile.isNotEmpty()) emit(Piece(Pose.LIE, pile)); pile = mutableListOf() }
        for (b in books) {
            if (b.poseOf == Pose.LIE) {
                val last = pile.lastOrNull()
                if (last != null && (last.pile != b.pile || b.pile.isEmpty() && pile.size == PILE)) flush()
                pile += b
            } else {
                flush()
                emit(Piece(b.poseOf, listOf(b)))
            }
        }
        flush()
        return out
    }

    /**
     * The books in order, a cycle's volumes gathered where its first one comes, in their own order and under
     * its name once there are two of them. With [cycles] off they all stay where they are. A pile made by hand
     * gathers where its first book comes, and its books leave their cycles to lie in it.
     */
    fun groups(books: List<Book>, cycles: Boolean = true, decor: List<Decor.Placed> = emptyList()): List<Group> {
        val out = mutableListOf<Group>()
        val after = decor.groupBy { it.after }
        // Things put before every book; those after a book not shown here aren't shown either.
        after[0L]?.let { l -> out += Group("", l.map { Piece(it) }) }
        val seen = HashSet<String>()
        val piles = books.filter { it.pile.isNotEmpty() }.groupBy { it.pile }
        val seenPiles = HashSet<String>()
        var loose = mutableListOf<Book>()
        fun flush() { if (loose.isNotEmpty()) out += Group("", pieces(loose, after)); loose = mutableListOf() }
        for (b in books) {
            if (b.pile.isNotEmpty()) { if (seenPiles.add(b.pile)) loose += piles.getValue(b.pile); continue }
            val key = b.series.trim().lowercase()
            if (!cycles || key.isEmpty()) { loose += b; continue }
            if (!seen.add(key)) continue
            val volumes = books.filter { it.pile.isEmpty() && it.series.trim().lowercase() == key }.sortedWith(compareBy({ it.volume ?: Int.MAX_VALUE }, { it.added }))
            if (volumes.size < 2) { loose += b; continue }
            flush()
            out += Group(b.series.trim(), pieces(volumes, after))
        }
        flush()
        return out
    }

    /**
     * The groups on planks [room] dp wide. A cycle moves whole to the next plank when it doesn't fit;
     * loose books (and a cycle too long for any plank) fill what's left and go on. [reserve] keeps room at the
     * end of a plank (by its number) for what decorates it.
     */
    fun planks(groups: List<Group>, room: Int, reserve: (Int) -> Int = { 0 }): List<List<Group>> {
        val planks = mutableListOf<MutableList<Group>>()
        var used = 0
        // The room on the plank being filled, or on the next one.
        fun roomOf(i: Int) = (room - reserve(i)).coerceAtLeast(room / 2)
        fun free() = if (planks.isEmpty()) -1 else roomOf(planks.size - 1) - used - GROUP_GAP
        fun put(g: Group) {
            val row = planks.lastOrNull()
            if (row == null || row.isEmpty()) { if (row == null) planks += mutableListOf(g) else row += g; used = g.width }
            else { row += g; used += GROUP_GAP + g.width }
        }
        fun next() { planks += mutableListOf<Group>(); used = 0 }
        for (g in groups) {
            if (g.name.isNotEmpty()) {
                val last = planks.lastOrNull()
                if (last != null && last.isNotEmpty() && g.width <= free()) { put(g); continue }
                val open = last != null && last.isEmpty()
                if (g.width <= roomOf(if (open) planks.size - 1 else planks.size)) { if (!open) next(); put(g); continue }
            }
            var chunk = mutableListOf<Piece>()
            var w = 0
            var empty = planks.lastOrNull()?.isNotEmpty() != true
            if (planks.isEmpty()) next()
            var limit = if (empty) roomOf(planks.size - 1) else free()
            for (p in g.pieces) {
                val need = if (chunk.isEmpty()) p.width else w + GAP + p.width
                if (need <= limit || chunk.isEmpty() && empty) { chunk += p; w = need; continue }
                if (chunk.isNotEmpty()) put(Group(g.name, chunk))
                next()
                empty = true
                limit = roomOf(planks.size - 1)
                chunk = mutableListOf(p)
                w = p.width
            }
            if (chunk.isNotEmpty()) put(Group(g.name, chunk))
        }
        return planks.filter { it.isNotEmpty() }
    }

    /** All the books in order, as many on each plank as fit in [room] dp: [groups] laid on [planks]. */
    fun rows(books: List<Book>, room: Int): List<List<Book>> =
        planks(groups(books, cycles = false), room).map { row -> row.flatMap { g -> g.books } }

    const val GAP = 2
    const val GROUP_GAP = 12
    const val PILE = 6
}

/**
 * One plank of the shelf: its books standing (or lying) on it, the things put among them, a cycle's name on a
 * brass plate under its volumes. [onHold] is a long press on a book that isn't dragged, to change how it stands
 * and looks; [onThing] a tap (or long press) on a thing, to change it. [onAdd], while decorating: the place at the
 * plank's end to put something new, after the plank's last book.
 */
@Composable
fun Plank(
    groups: List<Spines.Group>,
    onBook: (Book) -> Unit,
    onHold: ((Book) -> Unit)? = null,
    labels: Boolean = true,
    look: PlankLook = PlankLook(),
    onThing: ((Decor.Placed) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
) {
    val named = labels && groups.any { it.name.isNotEmpty() }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spines.GROUP_GAP.dp), verticalAlignment = Alignment.Bottom) {
                groups.forEach { g ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spines.GAP.dp), verticalAlignment = Alignment.Bottom) {
                        g.pieces.forEach { p ->
                            val o = p.ornament
                            when {
                                o != null -> Box(Modifier.thingPress(o) { onThing?.invoke(o) }) { Ornament(o.key) }
                                p.pose == Pose.STAND -> p.books.first().let { b -> Spine(b, Spines.width(b).dp, Spines.height(b).dp, { onBook(b) }, onHold?.let { h -> { h(b) } }) }
                                p.pose == Pose.FACE -> p.books.first().let { b -> FacingOut(b, { onBook(b) }, onHold?.let { h -> { h(b) } }) }
                                else -> Pile(p, onBook, onHold)
                            }
                        }
                    }
                }
            }
            // Decorating: a place at the end of the plank to put something.
            if (onAdd != null) Box(
                Modifier.padding(start = Spines.GROUP_GAP.dp).size(Decor.width(Decor.SLOT).dp, 64.dp)
                    .clip(RoundedCornerShape(6.dp)).border(1.5.dp, U.c.accent, RoundedCornerShape(6.dp)).clickable { onAdd() },
                contentAlignment = Alignment.Center,
            ) { Text("+", style = TextStyle(fontSize = 26.sp, color = U.c.accent, fontWeight = FontWeight.Bold)) }
        }
        Board(look)
        if (named) Plates(groups)
    }
}

/** The brass plates, each centred under its cycle, half on the plank's front. */
@Composable
private fun Plates(groups: List<Spines.Group>) {
    // Where each group's middle is along the plank.
    val centres = mutableListOf<Int>()
    var x = 6
    groups.forEach { g -> centres += x + g.width / 2; x += g.width + Spines.GROUP_GAP }
    val shown = groups.indices.filter { groups[it].name.isNotEmpty() }
    Layout(
        content = {
            shown.forEach { i ->
                val g = groups[i]
                Text(
                    g.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF3B2A12)),
                    modifier = Modifier.widthIn(max = maxOf(g.width + 48, 150).dp)
                        .shadow(2.dp, RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFF3DC9C), Color(0xFFC9A253))), RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().offset(y = (-12).dp),
    ) { measurables, c ->
        val ps = measurables.map { it.measure(Constraints(maxWidth = c.maxWidth)) }
        val h = ps.maxOfOrNull { it.height } ?: 0
        // The plates overlap the plank above, so they take less room below it than they are tall.
        layout(c.maxWidth, (h - 12.dp.roundToPx()).coerceAtLeast(0)) {
            ps.forEachIndexed { k, p ->
                val centre = centres[shown[k]].dp.roundToPx()
                p.place((centre - p.width / 2).coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), 0)
            }
        }
    }
}

private fun Modifier.press(b: Book, pose: Pose, onClick: () -> Unit, onHold: (() -> Unit)?) = shelfPress(b, pose, onClick, onHold)

/** The picture a book's spine is drawn from: the reader's own, or the cover's. */
@Composable
private fun spinePicture(b: Book): ImageBitmap? {
    val context = LocalContext.current
    val url = b.spinePicture ?: b.cover.takeIf { b.spineStyle == SpineStyle.AUTO }.orEmpty()
    val picture by produceState(Covers.cached(url), url) {
        value = Covers.cached(url)
        if (url.isNotBlank() && value == null) value = Covers.load(context, url)
    }
    return picture
}

/**
 * A book standing on the shelf: a strip of its cover, cloth, leather, paper, night-black, or the reader's own
 * picture of it, rounded by light and shade, the title running down it. A book being read has a ribbon in it;
 * an abandoned one fades.
 */
@Composable
fun Spine(b: Book, width: Dp, height: Dp, onClick: () -> Unit, onHold: (() -> Unit)? = null) {
    val reading = b.shelfOf == Shelf.READING || b.shelfOf == Shelf.PAUSED
    // A book being read has its ribbon sticking out over the top, a paused one a faded one.
    if (reading) Column {
        Box(
            Modifier.padding(start = width * 0.55f).width((width.value * 0.3f).coerceAtLeast(5f).dp).height(9.dp)
                .background(if (b.shelfOf == Shelf.PAUSED) U.c.faint else U.c.ember)
        )
        BookBody(b, width, height, false, Modifier.press(b, Pose.STAND, onClick, onHold))
    } else BookBody(b, width, height, false, Modifier.press(b, Pose.STAND, onClick, onHold))
}

/** A spine as it would look, for picking a style. */
@Composable
fun SpineSample(b: Book, width: Dp, height: Dp, modifier: Modifier = Modifier) = BookBody(b, width, height, false, modifier)

/** A book as it's carried while being dragged, in the pose it had. */
@Composable
fun CarriedBook(b: Book, pose: Pose) = when (pose) {
    Pose.STAND -> BookBody(b, Spines.width(b).dp, Spines.height(b).dp, false, Modifier)
    Pose.FACE -> BookCover(b, Modifier.size(Spines.faceWidth(b).dp, Spines.height(b).dp), small = true)
    Pose.LIE -> BookBody(b, Spines.length(b).dp, Spines.width(b).dp, true, Modifier)
}

/** A book facing out: its cover, leaning on the wall with a shadow behind. */
@Composable
private fun FacingOut(b: Book, onClick: () -> Unit, onHold: (() -> Unit)?) {
    val h = Spines.height(b).dp
    BookCover(
        b,
        Modifier.size(Spines.faceWidth(b).dp, h)
            .shadow(4.dp, RoundedCornerShape(2.dp))
            .graphicsLayer { alpha = if (b.shelfOf == Shelf.ABANDONED) 0.45f else 1f }
            .press(b, Pose.FACE, onClick, onHold),
        small = true,
    )
}

/** Books lying one on another, the first of them at the bottom. */
@Composable
private fun Pile(p: Spines.Piece, onBook: (Book) -> Unit, onHold: ((Book) -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        p.books.asReversed().forEach { b ->
            BookBody(b, Spines.length(b).dp, Spines.width(b).dp, true, Modifier.press(b, Pose.LIE, { onBook(b) }, onHold?.let { h -> { h(b) } }))
        }
    }
}

/** The book itself, [width] × [height] as it lies on the screen; [lying] turns the spine on its side. */
@Composable
private fun BookBody(b: Book, width: Dp, height: Dp, lying: Boolean, modifier: Modifier) {
    val picture = spinePicture(b)
    val own = b.spinePicture != null && picture != null
    val style = when {
        b.spineStyle != SpineStyle.AUTO -> b.spineStyle
        picture != null -> SpineStyle.AUTO
        else -> SpineStyle.CLOTH
    }
    val base = Color(OwnGenre.of(b)?.argb ?: Genre.of(b.genre).argb)
    val thick = if (lying) height else width
    val long = if (lying) width else height
    val numbered = b.volume != null && b.series.isNotBlank() && !lying
    Box(
        modifier.size(width, height)
            .graphicsLayer { alpha = if (b.shelfOf == Shelf.ABANDONED) 0.45f else 1f }
            .clip(RoundedCornerShape(2.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Drawn as if standing; lying, the drawing turns so the top of the book is on the left.
            if (lying) withTransform({ translate(0f, size.height); rotate(-90f, Offset.Zero) }) {
                spine(style, own, picture, base, Size(size.height, size.width), numbered, b.id)
            } else spine(style, own, picture, base, size, numbered, b.id)
        }
        // The reader's own picture has its own lettering.
        if (!own) {
            val l = letters(style)
            val text = TextStyle(
                fontFamily = l.font,
                fontWeight = l.weight,
                fontStyle = if (l.italic) FontStyle.Italic else FontStyle.Normal,
                fontSize = (if (thick < 22.dp) 11 else 13).let { if (l.font == Cormorant) it else it - 2 }.sp,
                letterSpacing = l.spacing,
                color = l.ink,
                shadow = if (l.shadow) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 2f) else null,
            )
            // Where the title runs: clear of the bands, labels and blocks each style puts at the ends.
            val (top, bottom) = when (style) {
                SpineStyle.PAPER -> 0.08f to 0.24f
                SpineStyle.LEATHER -> 0.24f to 0.24f
                SpineStyle.MODERN -> 0.06f to (if (numbered) 0.22f else 0.12f)
                else -> 0.13f to (if (numbered) 0.2f else 0.13f)
            }
            Text(
                if (l.upper) b.title.uppercase() else b.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = text,
                modifier = if (lying) Modifier.align(Alignment.Center).padding(horizontal = long * 0.13f)
                else Modifier.align(Alignment.Center)
                    .padding(top = long * top, bottom = long * bottom)
                    .vertical()
                    .rotate(90f),
            )
            if (numbered) Text(
                b.volume.toString(),
                maxLines = 1,
                style = text.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = if (style == SpineStyle.PAPER) Color.White else l.ink),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = long * (if (style == SpineStyle.PAPER) 0.08f else 0.07f)),
            )
        }
    }
}

/** How a style letters its spine. */
private class Letters(val font: FontFamily, val weight: FontWeight, val ink: Color, val italic: Boolean = false, val upper: Boolean = false, val shadow: Boolean = true, val spacing: TextUnit = 0.sp)

private val Gilt = Color(0xFFF2D58A)

private fun letters(style: SpineStyle) = when (style) {
    SpineStyle.LEATHER -> Letters(Cormorant, FontWeight.Bold, Gilt)
    SpineStyle.NIGHT -> Letters(Cormorant, FontWeight.SemiBold, Gilt, upper = true, shadow = false, spacing = 1.5.sp)
    SpineStyle.PAPER -> Letters(FontFamily.SansSerif, FontWeight.Bold, Color(0xFF1E1A16), upper = true, shadow = false, spacing = 0.5.sp)
    SpineStyle.VELLUM -> Letters(Cormorant, FontWeight.SemiBold, Color(0xFF4A2A14), italic = true, shadow = false)
    SpineStyle.MODERN -> Letters(FontFamily.SansSerif, FontWeight.Black, Color.White, upper = true, spacing = 1.sp)
    else -> Letters(Cormorant, FontWeight.Bold, Color(0xFFFFF6E4))
}

/** A standing spine of [s] in the given style; the caller turns the canvas for one lying down. [seed] keeps its grain the same each time. */
private fun DrawScope.spine(style: SpineStyle, own: Boolean, img: ImageBitmap?, base: Color, s: Size, numbered: Boolean, seed: Long) {
    val w = s.width
    val h = s.height
    fun band(y: Float, t: Float, c: Color, inset: Float = 0f) = drawRect(c, Offset(w * inset, h * y), Size(w * (1 - 2 * inset), h * t))
    fun diamond(cy: Float, r: Float, c: Color) = drawPath(
        androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, cy - r); lineTo(w / 2 + r, cy); lineTo(w / 2, cy + r); lineTo(w / 2 - r, cy); close() }, c,
    )
    // A few spots scattered the same way on each drawing of the same book.
    val rnd = java.util.Random(seed * 7919 + style.ordinal)
    when {
        own && img != null -> drawImage(img, dstSize = IntSize(w.toInt(), h.toInt()))
        style == SpineStyle.AUTO && img != null -> {
            // A thin strip near the binding, stretched across: the cover's colours run down it in streaks
            // without its lettering, which would only fight the title.
            val srcW = (img.width * 0.06f).toInt().coerceAtLeast(1)
            drawImage(
                img,
                srcOffset = IntOffset((img.width * 0.1f).toInt().coerceAtMost(img.width - srcW), 0),
                srcSize = IntSize(srcW, img.height),
                dstSize = IntSize(w.toInt(), h.toInt()),
            )
            drawRect(Color.Black.copy(alpha = 0.32f), size = s)
        }
        style == SpineStyle.LEATHER -> {
            // Old oxblood leather whatever the genre: grain, raised bands, a black label with a gilt rule.
            drawRect(lerp(Color(0xFF5A2316), base, 0.12f), size = s)
            repeat((h / 4).toInt()) {
                drawCircle(Color.Black.copy(alpha = 0.12f), radius = 0.6f + rnd.nextFloat() * 1.4f, center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
            }
            listOf(0.07f, 0.16f, 0.83f, 0.92f).forEach { y ->
                band(y + 0.014f, 0.016f, Color.Black.copy(alpha = 0.45f))
                band(y, 0.02f, Color(0xFF7A3524))
                band(y, 0.004f, Color.White.copy(alpha = 0.25f))
            }
            listOf(0.12f, 0.885f).forEach { y -> diamond(h * y, w * 0.12f, Gilt.copy(alpha = 0.85f)) }
            band(0.21f, 0.58f, Color(0xFF17100C), inset = 0.1f)
            drawRect(Gilt.copy(alpha = 0.8f), Offset(w * 0.14f, h * 0.22f), Size(w * 0.72f, h * 0.56f), style = Stroke(1f))
        }
        style == SpineStyle.PAPER -> {
            // A paperback: white card, a thin rule of colour at the top, a block of it at the foot with the
            // publisher's round mark (or the volume's number).
            drawRect(Color(0xFFF7F3EA), size = s)
            band(0.025f, 0.03f, base)
            band(0.8f, 0.2f, base)
            if (!numbered) drawCircle(Color.White, radius = w * 0.2f, center = Offset(w / 2, h * 0.9f))
        }
        style == SpineStyle.NIGHT -> {
            // Black with gilt: a fine double frame, diamonds at the ends.
            drawRect(Color(0xFF121010), size = s)
            val m = w * 0.12f
            drawRect(Gilt.copy(alpha = 0.7f), Offset(m, h * 0.025f), Size(w - 2 * m, h * 0.95f), style = Stroke(1.4f))
            drawRect(Gilt.copy(alpha = 0.35f), Offset(m + 3f, h * 0.025f + 3f), Size(w - 2 * m - 6f, h * 0.95f - 6f), style = Stroke(0.8f))
            listOf(0.075f, 0.925f).forEach { y -> diamond(h * y, w * 0.13f, Gilt) }
        }
        style == SpineStyle.VELLUM -> {
            // Old parchment: pale and blotched, browner toward the ends, red labels and ink rules.
            drawRect(Color(0xFFE6D6AC), size = s)
            repeat(5) {
                val c = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                val r = w * (0.5f + rnd.nextFloat())
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8A6A3A).copy(alpha = 0.22f), Color.Transparent), c, r), r, c)
            }
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF6B4A22).copy(alpha = 0.35f), 0.08f to Color.Transparent,
                    0.92f to Color.Transparent, 1f to Color(0xFF6B4A22).copy(alpha = 0.35f),
                    startY = 0f, endY = h,
                ),
                size = s,
            )
            band(0.05f, 0.06f, Color(0xFF8C2A1E), inset = 0.08f)
            listOf(0.13f, 0.14f, 0.86f, 0.87f).forEach { y -> band(y, 0.004f, Color(0xFF4A2A14).copy(alpha = 0.7f), inset = 0.08f) }
            band(0.89f, 0.06f, Color(0xFF8C2A1E), inset = 0.08f)
        }
        style == SpineStyle.MODERN -> {
            // New and bright: the genre's colour running dark, a slash of white near the foot.
            drawRect(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.2f), lerp(base, Color(0xFF120E2A), 0.6f)), startY = 0f, endY = h), size = s)
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset(0f, 0f), Offset(w, h * 0.4f)), size = s)
            band(0.8f, 0.012f, Color.White.copy(alpha = 0.9f))
            band(0.82f, 0.004f, Color.White.copy(alpha = 0.5f))
        }
        else -> {
            // Cloth in the genre's colour: a fine weave, double gold bands at each end.
            drawRect(base, size = s)
            var y = 0f
            while (y < h) { drawRect(Color.Black.copy(alpha = 0.07f), Offset(0f, y), Size(w, 1f)); y += 3f }
            var x = 0f
            while (x < w) { drawRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(1f, h)); x += 3f }
            listOf(0.07f, 0.1f, 0.9f, 0.93f).forEach { band(it, 0.018f, Gilt.copy(alpha = 0.75f)) }
        }
    }
    // A round back: light down the left third, shade toward the right edge; a paperback's is flat.
    val k = if (style == SpineStyle.PAPER) 0.5f else 1f
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.25f * k),
            0.18f to Color.White.copy(alpha = if (own) 0.1f else 0.16f * k),
            0.45f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.35f * k),
            startX = 0f, endX = w,
        ),
        size = s,
    )
}

/** Lays text out along the height instead of the width, to be turned on its side. */
private fun Modifier.vertical() = layout { measurable, c ->
    val p = measurable.measure(Constraints(maxWidth = c.maxHeight, maxHeight = c.maxWidth))
    layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
}

/** A cycle on the shelf of spines: its name and how far along, its volumes in order on their own planks. */
@Composable
fun CycleShelf(c: app.familiar.domain.Series, room: Int, onCycle: () -> Unit, onHold: ((Book) -> Unit)? = null, onBook: (Book) -> Unit) {
    app.familiar.ui.components.UCard(onClick = onCycle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, style = app.familiar.ui.theme.UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                androidx.compose.ui.res.stringResource(app.familiar.R.string.cycle_of, c.done, c.books.size),
                style = app.familiar.ui.theme.UType.Small.copy(color = if (c.complete) U.c.gold else U.c.muted),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        // The card's own padding and the plank's take their share of the width.
        Spines.planks(Spines.groups(c.books, cycles = false), room - 32 - 12).forEach { row -> Plank(row, onBook, onHold, labels = false) }
    }
}
