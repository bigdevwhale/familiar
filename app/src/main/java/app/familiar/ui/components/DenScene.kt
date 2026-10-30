package app.familiar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.familiar.domain.Item
import app.familiar.domain.Season
import app.familiar.ui.pix.Look
import app.familiar.ui.pix.Room
import app.familiar.ui.pix.Sprites
import app.familiar.ui.theme.U
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class RoomKey(val placed: Set<Item>, val books: List<Int>, val night: Boolean, val season: Season)

private object RoomCache {
    private var key: RoomKey? = null
    private var frames: List<ImageBitmap>? = null

    @Synchronized fun get(k: RoomKey) = if (k == key) frames else null
    @Synchronized fun put(k: RoomKey, f: List<ImageBitmap>) { key = k; frames = f }
}

/**
 * The den with the familiar in it, both in the same pixels. Tap to make it hop ([hops] times),
 * stroke it ([stroke] pixels' worth) to make it purr. A [guest] sits beside it for a day.
 */
@Composable
fun DenScene(
    look: Look,
    placed: Set<Item>,
    books: List<Int>,
    night: Boolean,
    modifier: Modifier = Modifier,
    season: Season = Season.NONE,
    guest: Look? = null,
    hops: Int = 1,
    stroke: Float = 260f,
    onTap: () -> Unit = {},
    onStroke: () -> Unit = {},
) {
    val key = RoomKey(placed, books, night, season)
    val room by produceState(RoomCache.get(key), key) {
        value = RoomCache.get(key) ?: withContext(Dispatchers.Default) {
            List(Room.FRAMES) { Room.scene(placed, books, night, it, season).bitmap() }
        }.also { RoomCache.put(key, it) }
    }
    val pet by produceState(null as List<ImageBitmap>?, look) { value = withContext(Dispatchers.Default) { framesOf(look) } }
    val visitor by produceState(null as List<ImageBitmap>?, guest) { value = guest?.let { withContext(Dispatchers.Default) { framesOf(it) } } }
    val t by rememberTime()
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var stroked = 0f
    Canvas(
        modifier
            .clip(U.RXl)
            .pointerInput(Unit) {
                detectTapGestures {
                    onTap()
                    scope.launch {
                        repeat(hops) {
                            hop.animateTo(1f, tween(140))
                            hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                // Sideways strokes only, so a vertical swipe still scrolls the page.
                detectHorizontalDragGestures(onDragStart = { stroked = 0f }) { change, amount ->
                    change.consume()
                    stroked += kotlin.math.abs(amount)
                    if (stroked > stroke) { stroked = 0f; onStroke() }
                }
            }
    ) {
        val frames = room ?: return@Canvas
        // Fill the box in whole-pixel steps, cropping a little off the edges rather than leaving a frame.
        val k = maxOf(1, kotlin.math.ceil(maxOf(size.width / Room.W, size.height / Room.H)).toInt())
        val ox = ((size.width - Room.W * k) / 2).toInt()
        val oy = ((size.height - Room.H * k) / 2).toInt()
        val f = frames[(t * 3 * Room.STEP).toInt() % frames.size]
        drawRect(androidx.compose.ui.graphics.Color(0xFF2A2030), size = size)
        drawImage(f, dstOffset = IntOffset(ox, oy), dstSize = IntSize(Room.W * k, Room.H * k), filterQuality = FilterQuality.None)
        // Familiars stand on the floor and breathe up from it; the hop lifts them smoothly, off the room's grid.
        fun familiar(img: ImageBitmap, x: Int, y: Int, stretch: Float) {
            val tall = (img.height * k * (1f + stretch)).toInt()
            drawImage(img, dstOffset = IntOffset(x, y + img.height * k - tall), dstSize = IntSize(img.width * k, tall), filterQuality = FilterQuality.None)
        }
        visitor?.let { v ->
            // The guest sits to the left, a little out of step with the host.
            val img = v.at(t * 5 + 3)
            familiar(img, ox + (Room.W / 2 - 44 - img.width / 2) * k, oy + (Room.FLOOR_Y - img.height + 1) * k, guest?.let { breath(it, t + 0.8f) } ?: 0f)
        }
        pet?.let { p ->
            val img = p.at(t * 6)
            familiar(img, ox + (Room.W - img.width) / 2 * k, oy + (Room.FLOOR_Y - img.height + 3) * k - (hop.value * 6 * k).toInt(), breath(look, t))
        }
    }
}

