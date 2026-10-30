package app.familiar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familiar.domain.Genre
import androidx.compose.runtime.produceState
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import app.familiar.domain.Species
import app.familiar.domain.Stage
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.familiar.ui.pix.Look
import app.familiar.ui.pix.Sprites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.familiar.ui.theme.Cormorant
import app.familiar.ui.theme.U
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seconds since this first appeared, ticking every frame while it's on screen. Read it only while drawing
 * (`val t by rememberTime()` used inside a Canvas), so each frame redraws the picture, not the whole screen.
 */
@Composable
fun rememberTime(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) t.floatValue = (withFrameMillis { it } - start) / 1000f
    }
    return t
}

/**
 * Beats of an idle loop played at [fps] beats a second. They add up frame by frame, so when the pace
 * changes (a familiar starts chewing) the loop carries on from where it was instead of jumping.
 */
@Composable
fun rememberBeats(fps: Float): State<Float> {
    val pace by rememberUpdatedState(fps)
    val beats = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            if (pace > 0f) beats.floatValue = (beats.floatValue + (now - last) / 1000f * pace) % (Sprites.BEATS * 1000f)
            last = now
        }
    }
    return beats
}

/** The frame of a familiar's idle loop for [beats]. */
fun List<ImageBitmap>.at(beats: Float): ImageBitmap = this[(beats * Sprites.STEP).toInt() % size]

/** How much a familiar's breath stretches it [t] seconds in, as a share of its height; 0 if it doesn't breathe. */
fun breath(look: Look, t: Float): Float {
    if (look.stage == Stage.EGG || look.stone >= 0.5f || look.species == Species.MOTH || look.species == Species.GHOST) return 0f
    val period = if (look.sleeping) 3.6f else 2.2f
    return (0.5f - 0.5f * cos(t / period * 2 * Math.PI.toFloat())) * 0.016f
}

/** Painted frames are kept per look: drawing a familiar takes a few dozen milliseconds. */
private object FrameCache {
    private val map = object : LinkedHashMap<Look, List<ImageBitmap>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Look, List<ImageBitmap>>?) = size > 16
    }

    @Synchronized fun get(look: Look) = map[look]
    @Synchronized fun put(look: Look, frames: List<ImageBitmap>) { map[look] = frames }
}

fun framesOf(look: Look): List<ImageBitmap> = FrameCache.get(look) ?: Sprites.frames(look).map { it.bitmap() }.also { FrameCache.put(look, it) }

/**
 * Draws pixel frames scaled up in whole steps, crisp, centred and resting on the bottom. [stretch] makes it
 * a little taller from the same bottom edge, for a breath.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.pixels(img: ImageBitmap, tint: androidx.compose.ui.graphics.ColorFilter? = null, bottom: Boolean = false, stretch: Float = 0f) {
    val k = maxOf(1, minOf(size.width / img.width, size.height / img.height).toInt())
    val w = img.width * k
    val h = img.height * k
    val tall = (h * (1f + stretch)).toInt()
    val y = if (bottom) (size.height - h).toInt() else (size.height - h).toInt().coerceAtLeast(0) / 2
    drawImage(
        img,
        dstOffset = IntOffset(((size.width - w) / 2).toInt(), y + h - tall),
        dstSize = IntSize(w, tall),
        filterQuality = FilterQuality.None,
        colorFilter = tint,
    )
}

/** A familiar, alive: it breathes, blinks and hops with a heart when you tap it. */
@Composable
fun FamiliarArt(look: Look, modifier: Modifier = Modifier, fps: Float = 6f, tint: androidx.compose.ui.graphics.ColorFilter? = null, onTap: (() -> Unit)? = null) {
    val frames by produceState(FrameCache.get(look), look) { value = withContext(Dispatchers.Default) { framesOf(look) } }
    val t by rememberTime()
    val beats by rememberBeats(fps)
    val hop = remember { Animatable(0f) }
    val love = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val heart = U.c.danger
    Canvas(
        modifier
            .graphicsLayer { translationY = -hop.value * size.height * 0.06f }
            .then(
                if (onTap == null) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        onTap()
                        scope.launch {
                            hop.animateTo(1f, tween(140))
                            hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        scope.launch { love.snapTo(0f); love.animateTo(1f, tween(1400)) }
                    }
                }
            )
    ) {
        val f = frames ?: return@Canvas
        // A still one (fps 0) doesn't breathe either.
        pixels(f.at(beats), tint, stretch = if (fps > 0f) breath(look, t) else 0f)
        val l = love.value
        if (l < 1f) {
            val s = size.minDimension * 0.012f
            val cx = size.width / 2 + sin(l * 9f) * size.minDimension * 0.04f
            val cy = size.height * (0.28f - l * 0.22f)
            // A pixel heart.
            listOf(".x.x.", "xxxxx", "xxxxx", ".xxx.", "..x..").forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch == 'x') drawRect(heart.copy(alpha = 1f - l), Offset(cx + (x - 2.5f) * s * 2, cy + y * s * 2), androidx.compose.ui.geometry.Size(s * 2, s * 2))
                }
            }
        }
    }
}

/** The warm den behind a familiar: candlelight, shadow and drifting mana. */
@Composable
fun Den(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glow = U.c.denGlow
    val dark = U.c.denDark
    val mana = U.c.mana
    val t by rememberTime()
    Box(
        modifier
            .clip(U.RXl)
            .background(Brush.radialGradient(listOf(glow, lerp(glow, dark, 0.55f), dark), radius = 900f))
            .border(1.dp, U.c.line, U.RXl),
    ) {
        Canvas(Modifier.fillMaxSize()) { motes(t, mana) }
        content()
    }
}

/** Square mana sparks drifting up, smoothly rather than from pixel to pixel. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.motes(t: Float, color: Color) {
    val px = size.minDimension / 90f
    for (i in 0 until 12) {
        val speed = 0.04f + ((i * 37) % 10) / 200f
        val ph = (t * speed + (i * 0.618f) % 1f) % 1f
        val x = size.width * (0.08f + 0.84f * ((i * 0.381f) % 1f)) + sin(t * 0.8f + i) * size.width * 0.02f
        val y = size.height * (1f - ph)
        val a = sin(ph * Math.PI.toFloat()) * 0.8f
        val s = if (i % 3 == 0) px * 2 else px
        drawRect(color.copy(alpha = a), Offset(x, y), androidx.compose.ui.geometry.Size(s, s))
    }
}

/** A book's cover, drawn in the colour of its own genre if it has one. */
@Composable
fun BookCover(b: app.familiar.data.Book, modifier: Modifier = Modifier, small: Boolean = false) =
    BookCover(b.title, Genre.of(b.genre), modifier, small, b.cover, app.familiar.domain.OwnGenre.of(b)?.argb)

/** A cover drawn from the genre colour (or [argb]), with the title on it. */
@Composable
fun BookCover(title: String, genre: Genre, modifier: Modifier = Modifier, small: Boolean = false, cover: String = "", argb: Long? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picture by produceState(app.familiar.data.Covers.cached(cover), cover) {
        // The state outlives a change of cover: start from this cover's picture, not the last one's.
        value = app.familiar.data.Covers.cached(cover)
        if (cover.isNotBlank() && value == null) value = app.familiar.data.Covers.load(context, cover)
    }
    picture?.let { img ->
        androidx.compose.foundation.Image(
            img, title,
            modifier.clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        return
    }
    val c = Color(argb ?: genre.argb)
    Box(
        modifier
            .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp))
            .background(Brush.linearGradient(listOf(lerp(c, Color.White, 0.18f), c, lerp(c, Color.Black, 0.25f)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.18f), size = size.copy(width = size.width * 0.08f))
            val gold = Color(0xFFF2D58A).copy(alpha = 0.8f)
            val y1 = size.height * 0.16f
            val y2 = size.height * 0.84f
            drawLine(gold, Offset(size.width * 0.18f, y1), Offset(size.width * 0.9f, y1), size.height * 0.012f)
            drawLine(gold, Offset(size.width * 0.18f, y2), Offset(size.width * 0.9f, y2), size.height * 0.012f)
        }
        Text(
            title,
            fontFamily = Cormorant,
            fontWeight = FontWeight.Bold,
            fontSize = if (small) 10.sp else 14.sp,
            lineHeight = if (small) 11.sp else 15.sp,
            color = Color(0xFFFFF6E4),
            textAlign = TextAlign.Center,
            maxLines = if (small) 3 else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(start = if (small) 6.dp else 10.dp, end = if (small) 4.dp else 8.dp),
        )
    }
}

/** Sparks of mana flying up once. */
@Composable
fun Sparks(modifier: Modifier = Modifier, key: Any = Unit) {
    val pieces = remember(key) {
        val rnd = Random(key.hashCode())
        List(70) { Spark(rnd.nextFloat(), 0.7f + rnd.nextFloat() * 0.4f, 0.25f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * 6.28f, 1.5f + rnd.nextFloat() * 3f) }
    }
    var elapsed by remember(key) { mutableLongStateOf(0L) }
    LaunchedEffect(key) {
        val start = withFrameMillis { it }
        while (elapsed < 3200) elapsed = withFrameMillis { it } - start
    }
    if (elapsed >= 3200) return
    Canvas(modifier) {
        val t = elapsed / 1000f
        pieces.forEach { p ->
            val life = (t / (1.6f + p.speed * 2)).coerceIn(0f, 1f)
            if (life >= 1f) return@forEach
            val y = (p.y - p.speed * t * 0.55f) * size.height
            val x = (p.x + 0.03f * sin(t * 3 + p.phase)) * size.width
            val c = lerp(Color(0xFFBFE6FF), Color(0xFFB79CFF), life)
            drawCircle(c.copy(alpha = 1f - life), p.r * (1f - life * 0.5f) * density, Offset(x, y))
        }
    }
}

private class Spark(val x: Float, val y: Float, val speed: Float, val phase: Float, val r: Float)
