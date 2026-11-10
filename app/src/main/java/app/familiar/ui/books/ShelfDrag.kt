package app.familiar.ui.books

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.familiar.data.Book
import app.familiar.data.Pose
import app.familiar.ui.theme.U
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A book being carried along the shelf of spines. A long press picks it up; let go where it was and it's a
 * plain long press ([onHold]); carried off and let go, it's put down by the book under the finger ([onDrop]).
 * Everything is in root coordinates.
 */
class ShelfDrag {
    var book by mutableStateOf<Book?>(null)
        private set
    /** A thing carried instead of a book. */
    var thing by mutableStateOf<Decor.Placed?>(null)
        private set
    val active: Boolean get() = book != null || thing != null
    var pose by mutableStateOf(Pose.STAND)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    /** Where the finger took hold, from the book's top left corner. */
    var grab = Offset.Zero
        private set
    private var start = Offset.Zero

    /** False where books can be held but not moved (the cycles). */
    var movable = true
    var onHold: (Book) -> Unit = {}
    /** The book carried, the one it's put by, and whether after it (to the right, or above in a pile). */
    var onDrop: (Book, Book, Boolean) -> Unit = { _, _, _ -> }

    var onThingHold: (Decor.Placed) -> Unit = {}
    var onThingDrop: (Decor.Placed, Book, Boolean) -> Unit = { _, _, _ -> }

    /** Where each book on screen is, and how it stands there. */
    internal val spots = HashMap<Long, Spot>()

    internal class Spot(val book: Book, val rect: Rect, val pose: Pose)

    fun pick(b: Book, pose: Pose, rect: Rect, at: Offset) {
        book = b; this.pose = pose; grab = at - rect.topLeft; pointer = at; start = at
    }

    fun pick(o: Decor.Placed, rect: Rect, at: Offset) {
        thing = o; grab = at - rect.topLeft; pointer = at; start = at
    }

    /** The finger let go: a drop if it went anywhere, otherwise a long press. */
    fun release(slop: Float) {
        val t = target()
        val still = !movable || (pointer - start).getDistance() < slop
        val b = book
        val o = thing
        book = null
        thing = null
        if (b != null) {
            if (still) onHold(b) else if (t != null && t.first.book.id != b.id) onDrop(b, t.first.book, t.second)
        } else if (o != null) {
            if (still) onThingHold(o) else if (t != null) onThingDrop(o, t.first.book, t.second)
        }
    }

    fun cancel() { book = null; thing = null }

    /** The book the carried one would be put by, and whether after it. */
    internal fun target(): Pair<Spot, Boolean>? {
        if (!active) return null
        val p = pointer
        val others = spots.values.filter { it.book.id != book?.id }
        val spot = others.firstOrNull { it.rect.contains(p) }
            // Between books, or past the last one on a plank: the nearest on the same plank.
            ?: others.filter { p.y in it.rect.top - 24f..it.rect.bottom + 8f }.minByOrNull { abs(it.rect.center.x - p.x) }
            ?: return null
        val after = if (spot.pose == Pose.LIE) p.y < spot.rect.center.y else p.x > spot.rect.center.x
        return spot to after
    }
}

val LocalShelfDrag = staticCompositionLocalOf<ShelfDrag?> { null }

/**
 * A book on the shelf: a tap opens it, a long press picks it up to carry (see [ShelfDrag]) or, where there's
 * nothing to carry it in, is a long press.
 */
fun Modifier.shelfPress(b: Book, pose: Pose, onClick: () -> Unit, onHold: (() -> Unit)?): Modifier = composed {
    val drag = LocalShelfDrag.current
    val haptic = LocalHapticFeedback.current
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHold)
    val book by rememberUpdatedState(b)
    var rect by remember { mutableStateOf(Rect.Zero) }
    if (drag != null) DisposableEffect(b.id) { onDispose { drag.spots.remove(b.id) } }
    val carried = drag?.book?.id == b.id
    this
        .graphicsLayer { alpha = if (carried) 0.25f else 1f }
        .onGloballyPositioned { c ->
            rect = c.boundsInRoot()
            drag?.spots?.set(b.id, ShelfDrag.Spot(book, rect, pose))
        }
        .semantics {
            role = Role.Button
            onClick(label = b.title) { click(); true }
            if (onHold != null) onLongClick { hold?.invoke(); true }
        }
        .pointerInput(b.id, drag) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Taken, the plank under it doesn't see a long press of its own.
                down.consume()
                var cancelled = false
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation().also { if (it == null) cancelled = true }
                }
                when {
                    // Scrolled away, or let go in time: a tap.
                    cancelled -> {}
                    up != null -> { up.consume(); click() }
                    else -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // The shelf follows the finger from here (see Carrying); without one, it's just a long press.
                        if (drag != null) drag.pick(book, pose, rect, rect.topLeft + down.position)
                        else hold?.invoke()
                    }
                }
            }
        }
}

/**
 * Around the shelf: follows a book picked up anywhere in [content], draws it under the finger and where it
 * would go, and lets it go. [origin] is where this box is in the root; [edge] scrolls the shelf near its ends.
 */
@Composable
fun Carrying(drag: ShelfDrag, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .pointerInput(drag) {
                awaitEachGesture {
                    // Before the books themselves: once one is picked up, the finger is the shelf's.
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (!drag.active) { if (ev.changes.none { it.pressed }) break else continue }
                        val ch = ev.changes.first()
                        drag.pointer = origin + ch.position
                        ev.changes.forEach { it.consume() }
                        if (!ch.pressed) { drag.release(viewConfiguration.touchSlop); break }
                    }
                }
            }
    ) {
        content()
        val b = drag.book
        val o = drag.thing
        if (b != null || o != null) {
            // Where it would go: a bar beside the book it'd stand by, or over or under one in a pile.
            val t = drag.target()
            val accent = U.c.accent
            Canvas(Modifier.fillMaxSize()) {
                if (t != null) {
                    val r = t.first.rect.translate(-origin)
                    val w = 3.dp.toPx()
                    if (t.first.pose == Pose.LIE) {
                        val y = if (t.second) r.top - w else r.bottom
                        drawRect(accent, Offset(r.left, y), androidx.compose.ui.geometry.Size(r.width, w))
                    } else {
                        val x = if (t.second) r.right else r.left - w
                        drawRect(accent, Offset(x, r.top), androidx.compose.ui.geometry.Size(w, r.height))
                    }
                }
            }
            Box(
                Modifier
                    .offset { (drag.pointer - drag.grab - origin).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                    .graphicsLayer { scaleX = 1.06f; scaleY = 1.06f; rotationZ = -3f }
                    .shadow(10.dp)
            ) { if (b != null) CarriedBook(b, drag.pose) else if (o != null) Ornament(o.key) }
        }
    }
}

/** A thing among the books: a tap to change it, a long press to carry it (or, let go where it was, to change it). */
fun Modifier.thingPress(o: Decor.Placed, onClick: () -> Unit): Modifier = composed {
    val drag = LocalShelfDrag.current
    val haptic = LocalHapticFeedback.current
    val click by rememberUpdatedState(onClick)
    val thing by rememberUpdatedState(o)
    var rect by remember { mutableStateOf(Rect.Zero) }
    val carried = drag?.thing?.id == o.id
    this
        .graphicsLayer { alpha = if (carried) 0.25f else 1f }
        .onGloballyPositioned { rect = it.boundsInRoot() }
        .semantics { role = Role.Button; onClick { click(); true } }
        .pointerInput(o.id, drag) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                var cancelled = false
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation().also { if (it == null) cancelled = true }
                }
                when {
                    cancelled -> {}
                    up != null -> { up.consume(); click() }
                    else -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (drag != null) drag.pick(thing, rect, rect.topLeft + down.position) else click()
                    }
                }
            }
        }
}
