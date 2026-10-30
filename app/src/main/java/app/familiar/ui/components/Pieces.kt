package app.familiar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import app.familiar.domain.Genre
import app.familiar.domain.Trinket
import app.familiar.ui.pix.Dreamscape
import app.familiar.ui.pix.Trinkets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A find from the casket, in pixels; [dim] shows only the shape of one not found yet. */
@Composable
fun TrinketArt(t: Trinket, modifier: Modifier = Modifier, dim: Boolean = false) {
    val img = remember(t, dim) { Trinkets.draw(t, dim).bitmap() }
    Canvas(modifier) { pixels(img) }
}

/** A dream in its cloud, gently moving. */
@Composable
fun DreamArt(g: Genre, modifier: Modifier = Modifier, fps: Float = 3f) {
    val frames by produceState(null as List<ImageBitmap>?, g) {
        value = withContext(Dispatchers.Default) { List(Dreamscape.FRAMES) { Dreamscape.scene(g, it).bitmap() } }
    }
    val t by rememberTime()
    Canvas(modifier) { frames?.let { pixels(it[(t * fps).toInt() % it.size]) } }
}
