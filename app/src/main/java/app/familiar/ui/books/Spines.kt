package app.familiar.ui.books

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familiar.data.Book
import app.familiar.data.Covers
import app.familiar.data.Shelf
import app.familiar.domain.Genre
import app.familiar.domain.OwnGenre
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

    /** The books in order, as many on each plank as fit in [room] dp with [gap] between them. */
    fun rows(books: List<Book>, room: Int, gap: Int = GAP): List<List<Book>> {
        val rows = mutableListOf<MutableList<Book>>()
        var used = 0
        for (b in books) {
            val w = width(b)
            val row = rows.lastOrNull()
            if (row == null || used + gap + w > room) {
                rows += mutableListOf(b)
                used = w
            } else {
                row += b
                used += gap + w
            }
        }
        return rows
    }

    const val GAP = 2
}

/** One plank of the shelf, its books standing on it side by side. */
@Composable
fun SpineRow(books: List<Book>, onBook: (Book) -> Unit) {
    val wood = lerp(U.c.gold, U.c.denDark, 0.45f)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(Spines.GAP.dp), verticalAlignment = Alignment.Bottom) {
            books.forEach { b -> Spine(b, Spines.width(b).dp, Spines.height(b).dp) { onBook(b) } }
        }
        // The plank: a lit top edge, the front, and its shadow on the wall.
        Box(Modifier.fillMaxWidth().height(3.dp).background(lerp(wood, Color.White, 0.25f)))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Brush.verticalGradient(listOf(wood, lerp(wood, Color.Black, 0.3f)))))
        Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent))))
    }
}

/**
 * A book standing on the shelf: a strip of its cover (or its genre's colour with gold bands),
 * rounded by light and shade, the title running down it. A book being read has a ribbon in it;
 * an abandoned one fades.
 */
@Composable
fun Spine(b: Book, width: Dp, height: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val picture by produceState(Covers.cached(b.cover), b.cover) {
        value = Covers.cached(b.cover)
        if (b.cover.isNotBlank() && value == null) value = Covers.load(context, b.cover)
    }
    val base = Color(OwnGenre.of(b)?.argb ?: Genre.of(b.genre).argb)
    val reading = b.shelfOf == Shelf.READING || b.shelfOf == Shelf.PAUSED
    // A book being read has its ribbon sticking out over the top, a paused one a faded one.
    if (reading) Column {
        Box(
            Modifier.padding(start = width * 0.55f).width((width.value * 0.3f).coerceAtLeast(5f).dp).height(9.dp)
                .background(if (b.shelfOf == Shelf.PAUSED) U.c.faint else U.c.ember)
        )
        SpineBody(b, width, height, picture, base, onClick)
    } else SpineBody(b, width, height, picture, base, onClick)
}

@Composable
private fun SpineBody(b: Book, width: Dp, height: Dp, picture: androidx.compose.ui.graphics.ImageBitmap?, base: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(width, height)
            .graphicsLayer { alpha = if (b.shelfOf == Shelf.ABANDONED) 0.45f else 1f }
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
            .clickable(role = Role.Button, onClickLabel = b.title, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val img = picture
            if (img != null) {
                // A thin strip near the binding, stretched across: the cover's colours run down it in streaks
                // without its lettering, which would only fight the title.
                val srcW = (img.width * 0.06f).toInt().coerceAtLeast(1)
                drawImage(
                    img,
                    srcOffset = IntOffset((img.width * 0.1f).toInt().coerceAtMost(img.width - srcW), 0),
                    srcSize = IntSize(srcW, img.height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
                drawRect(Color.Black.copy(alpha = 0.32f))
            } else {
                drawRect(base)
                val gold = Color(0xFFF2D58A).copy(alpha = 0.75f)
                val band = size.height * 0.018f
                for (y in listOf(0.07f, 0.1f, 0.9f, 0.93f)) drawRect(gold, Offset(0f, size.height * y), Size(size.width, band))
            }
            // A round back: light down the left third, shade toward the right edge.
            drawRect(Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.25f),
                0.18f to Color.White.copy(alpha = 0.16f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.35f),
            ))
        }
        Text(
            b.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = Cormorant,
                fontWeight = FontWeight.Bold,
                fontSize = if (width < 22.dp) 11.sp else 13.sp,
                color = Color(0xFFFFF6E4),
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 2f),
            ),
            modifier = Modifier.align(Alignment.Center)
                .padding(vertical = height * 0.13f)
                .vertical()
                .rotate(90f),
        )
    }
}

/** Lays text out along the height instead of the width, to be turned on its side. */
private fun Modifier.vertical() = layout { measurable, c ->
    val p = measurable.measure(Constraints(maxWidth = c.maxHeight, maxHeight = c.maxWidth))
    layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
}

/** A cycle on the shelf of spines: its name and how far along, its volumes in order on their own planks. */
@Composable
fun CycleShelf(c: app.familiar.domain.Series, room: Int, onCycle: () -> Unit, onBook: (Book) -> Unit) {
    app.familiar.ui.components.UCard(onClick = onCycle) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, style = app.familiar.ui.theme.UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                androidx.compose.ui.res.stringResource(app.familiar.R.string.cycle_of, c.done, c.books.size),
                style = app.familiar.ui.theme.UType.Small.copy(color = if (c.complete) U.c.gold else U.c.muted),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        // The card's own padding and the plank's take their share of the width.
        Spines.rows(c.books, room - 32 - 12).forEach { row -> SpineRow(row, onBook) }
    }
}
