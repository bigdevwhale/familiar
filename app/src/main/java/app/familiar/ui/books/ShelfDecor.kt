package app.familiar.ui.books

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.domain.Trinket
import app.familiar.ui.components.Chip
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.TrinketArt
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch

/** What the planks are made of. [OAK] takes its colour from the theme. */
enum class Wood { OAK, WALNUT, EBONY, BIRCH, WHITE }

/** What runs along the front of every plank. */
enum class Trim { NONE, BRASS, LIGHTS, IVY, LACE }

class PlankLook(val wood: Wood = Wood.OAK, val trim: Trim = Trim.NONE) {
    companion object {
        fun of(wood: String, trim: String) = PlankLook(
            Wood.entries.firstOrNull { it.name == wood } ?: Wood.OAK,
            Trim.entries.firstOrNull { it.name == trim } ?: Trim.NONE,
        )
    }
}

/** Things to stand among the books, drawn here; the familiar's finds stand there too ("T:<trinket>"). */
enum class Ornament { PLANT, CANDLES, CRYSTAL, HOURGLASS, GLOBE, BOOKEND }

/**
 * What stands among the books: each thing ([key], an [Ornament] name or "T:<trinket>") right after the book
 * [after] (0: before them all), so it moves with that book. Kept a line each: "<id>|<after>|<key>".
 */
object Decor {
    const val FIND = "T:"
    /** The empty place shown at a plank's end while decorating. */
    const val SLOT = "SLOT"

    data class Placed(val id: Long, val after: Long, val key: String)

    fun parse(saved: String): List<Placed> = saved.split('\n').mapNotNull { l ->
        val p = l.split('|')
        if (p.size != 3 || p[2].isEmpty()) null else Placed(p[0].toLongOrNull() ?: return@mapNotNull null, p[1].toLongOrNull() ?: 0L, p[2])
    }

    fun encode(l: List<Placed>) = l.joinToString("\n") { "${it.id}|${it.after}|${it.key}" }

    /** How much of a plank it takes, in dp, the gap before it included. */
    fun room(key: String): Int = if (key.isEmpty()) 0 else width(key) + Spines.GROUP_GAP

    fun width(key: String): Int = when {
        key.startsWith(FIND) -> 40
        key == Ornament.CANDLES.name || key == Ornament.PLANT.name -> 52
        else -> 44
    }
}

/** The plank itself: a lit top edge, the front with its grain, its shadow on the wall, and the trim. */
@Composable
fun Board(look: PlankLook) {
    val wood = when (look.wood) {
        Wood.OAK -> lerp(U.c.gold, U.c.denDark, 0.45f)
        Wood.WALNUT -> Color(0xFF6B4226)
        Wood.EBONY -> Color(0xFF2E2724)
        Wood.BIRCH -> Color(0xFFDCC195)
        Wood.WHITE -> Color(0xFFEDE8DE)
    }
    val hangs = look.trim == Trim.LIGHTS || look.trim == Trim.IVY
    Canvas(Modifier.fillMaxWidth().height(17.dp)) { board(wood, look.trim) }
    if (hangs) Spacer(Modifier.height(10.dp))
}

private fun DrawScope.board(wood: Color, trim: Trim) {
    val w = size.width
    val top = 3.dp.toPx()
    val front = 8.dp.toPx()
    val y1 = top + front
    drawRect(lerp(wood, Color.White, 0.25f), size = Size(w, top))
    drawRect(Brush.verticalGradient(listOf(wood, lerp(wood, Color.Black, 0.3f)), startY = top, endY = y1), Offset(0f, top), Size(w, front))
    // The grain: a few long darker streaks, the same on every plank.
    val rnd = java.util.Random(11)
    repeat(4) {
        val y = top + front * (0.2f + rnd.nextFloat() * 0.6f)
        val x0 = rnd.nextFloat() * w * 0.5f
        drawLine(Color.Black.copy(alpha = 0.1f), Offset(x0, y), Offset(x0 + w * (0.3f + rnd.nextFloat() * 0.4f), y + rnd.nextFloat() * 2f), 1f)
    }
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), startY = y1, endY = y1 + 6.dp.toPx()), Offset(0f, y1), Size(w, 6.dp.toPx()))
    val gold = Color(0xFFE2BE6A)
    when (trim) {
        Trim.NONE -> {}
        Trim.BRASS -> {
            // A brass strip on the front edge and brackets holding the plank up.
            drawRect(Brush.verticalGradient(listOf(Color(0xFFF6DE9A), Color(0xFFB08A3E)), startY = y1 - 3.dp.toPx(), endY = y1), Offset(0f, y1 - 3.dp.toPx()), Size(w, 3.dp.toPx()))
            listOf(0.08f, 0.5f, 0.92f).forEach { f ->
                val x = w * f
                val path = Path().apply { moveTo(x - 5.dp.toPx(), y1); lineTo(x + 5.dp.toPx(), y1); lineTo(x, y1 + 9.dp.toPx()); close() }
                drawPath(path, gold)
                drawCircle(Color(0xFF8A6A2E), 1.5.dp.toPx(), Offset(x, y1 + 2.5.dp.toPx()))
            }
        }
        Trim.LIGHTS -> {
            // A string of little bulbs sagging under the front.
            val span = 46.dp.toPx()
            val sag = 9.dp.toPx()
            val wire = Path()
            var x = 0f
            wire.moveTo(0f, y1)
            while (x < w) { wire.quadraticTo(x + span / 2, y1 + sag * 2, x + span, y1); x += span }
            drawPath(wire, Color(0xFF2F3B2A), style = Stroke(1.2f))
            val colours = listOf(Color(0xFFFFD36B), Color(0xFFFF8FA8), Color(0xFF8FE3FF), Color(0xFFB8FF8F))
            var i = 0
            x = 0f
            while (x < w) {
                // On the curve: half way along each span, and a quarter in from each end.
                listOf(0.25f, 0.5f, 0.75f).forEach { t ->
                    val px = x + span * t
                    val py = y1 + 2 * sag * 2 * t * (1 - t)
                    val c = colours[i++ % colours.size]
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), Offset(px, py + 2.dp.toPx()), 7.dp.toPx()), 7.dp.toPx(), Offset(px, py + 2.dp.toPx()))
                    drawCircle(c, 2.2.dp.toPx(), Offset(px, py + 2.dp.toPx()))
                }
                x += span
            }
        }
        Trim.IVY -> {
            // Ivy trailing over the front edge, longer here and shorter there.
            val leaf = Color(0xFF4E8A3E)
            val dark = Color(0xFF2F5E27)
            var x = 6.dp.toPx()
            while (x < w) {
                val len = (6 + rnd.nextInt(14)).dp.toPx()
                drawLine(dark, Offset(x, top), Offset(x + 2.dp.toPx(), y1 + len), 1.2f)
                var y = top + 2.dp.toPx()
                var side = if (rnd.nextBoolean()) 1 else -1
                while (y < y1 + len) {
                    val c = Offset(x + side * 3.dp.toPx() + (y - top) / (y1 + len) * 2.dp.toPx(), y)
                    rotate(side * 35f, c) { drawOval(if (side > 0) leaf else lerp(leaf, dark, 0.4f), Offset(c.x - 3.dp.toPx(), c.y - 2.dp.toPx()), Size(6.dp.toPx(), 4.dp.toPx())) }
                    side = -side
                    y += 4.5.dp.toPx()
                }
                x += (18 + rnd.nextInt(26)).dp.toPx()
            }
        }
        Trim.LACE -> {
            // A lace edge: little scallops, each with a hole.
            val r = 4.dp.toPx()
            var x = r
            val lace = Color(0xFFF8F3E8)
            while (x < w) {
                drawCircle(lace, r, Offset(x, y1))
                drawCircle(lerp(wood, Color.Black, 0.25f), r * 0.3f, Offset(x, y1 + r * 0.35f))
                x += r * 2
            }
            drawRect(lace, Offset(0f, y1 - 1.5.dp.toPx()), Size(w, 1.5.dp.toPx()))
        }
    }
}

/** What stands at the end of a plank. */
@Composable
fun Ornament(key: String) {
    val w = Decor.width(key)
    if (key.startsWith(Decor.FIND)) {
        val t = Trinket.of(key.removePrefix(Decor.FIND)) ?: return
        TrinketArt(t, Modifier.size(w.dp))
        return
    }
    val o = Ornament.entries.firstOrNull { it.name == key } ?: return
    val h = when (o) { Ornament.PLANT -> 92; Ornament.CANDLES -> 78; Ornament.CRYSTAL -> 60; Ornament.HOURGLASS -> 70; Ornament.GLOBE -> 84; Ornament.BOOKEND -> 96 }
    Canvas(Modifier.size(w.dp, h.dp)) { ornament(o) }
}

private fun DrawScope.ornament(o: Ornament) {
    val w = size.width
    val h = size.height
    val d = 1.dp.toPx()
    when (o) {
        Ornament.PLANT -> {
            // A terracotta pot with a leafy plant.
            val potTop = h * 0.62f
            val pot = Path().apply { moveTo(w * 0.2f, potTop); lineTo(w * 0.8f, potTop); lineTo(w * 0.7f, h); lineTo(w * 0.3f, h); close() }
            drawPath(pot, Brush.horizontalGradient(listOf(Color(0xFFC2643C), Color(0xFF8E3F22))))
            drawRect(Color(0xFFD27850), Offset(w * 0.16f, potTop - 4 * d), Size(w * 0.68f, 6 * d))
            val leaves = listOf(-55f to 0.9f, -30f to 1f, -8f to 1.1f, 15f to 1f, 40f to 0.9f, 62f to 0.75f, -70f to 0.7f)
            leaves.forEachIndexed { i, (a, l) ->
                val base = Offset(w / 2, potTop - 2 * d)
                rotate(a, base) {
                    val len = h * 0.5f * l
                    drawLine(Color(0xFF2F5E27), base, Offset(base.x, base.y - len * 0.7f), 1.5f * d)
                    drawOval(if (i % 2 == 0) Color(0xFF5E9E48) else Color(0xFF477F36), Offset(base.x - 6 * d, base.y - len), Size(12 * d, len * 0.6f))
                }
            }
        }
        Ornament.CANDLES -> {
            // Three candles on a brass dish, the flames glowing.
            drawRoundRect(Color(0xFFC9A253), Offset(0f, h - 5 * d), Size(w, 5 * d), CornerRadius(3 * d))
            listOf(Triple(0.08f, 0.55f, 0.26f), Triple(0.38f, 0.8f, 0.26f), Triple(0.68f, 0.42f, 0.26f)).forEach { (x, tall, cw) ->
                val left = w * x
                val cwPx = w * cw
                val top = h - 5 * d - (h - 22 * d) * tall
                drawRect(Brush.horizontalGradient(listOf(Color(0xFFFFF4DC), Color(0xFFE6D3AE)), startX = left, endX = left + cwPx), Offset(left, top), Size(cwPx, h - 5 * d - top))
                val c = Offset(left + cwPx / 2, top - 7 * d)
                drawCircle(Brush.radialGradient(listOf(Color(0x66FFC24A), Color.Transparent), c, 14 * d), 14 * d, c)
                drawOval(Color(0xFFFFB22E), Offset(c.x - 3 * d, c.y - 5 * d), Size(6 * d, 10 * d))
                drawOval(Color(0xFFFFF0B0), Offset(c.x - 1.5f * d, c.y - 1 * d), Size(3 * d, 5 * d))
                drawLine(Color(0xFF3A2A1A), Offset(c.x, top), Offset(c.x, top - 2 * d), 1f * d)
            }
        }
        Ornament.CRYSTAL -> {
            // A cluster of violet crystals.
            listOf(Triple(0.5f, 1f, 0f), Triple(0.28f, 0.7f, -18f), Triple(0.74f, 0.65f, 20f), Triple(0.4f, 0.45f, -35f)).forEach { (x, tall, a) ->
                val base = Offset(w * x, h)
                rotate(a, base) {
                    val cw = w * 0.22f
                    val t = h * tall
                    val p = Path().apply { moveTo(base.x - cw / 2, base.y); lineTo(base.x - cw / 2, base.y - t * 0.8f); lineTo(base.x, base.y - t); lineTo(base.x + cw / 2, base.y - t * 0.8f); lineTo(base.x + cw / 2, base.y); close() }
                    drawPath(p, Brush.horizontalGradient(listOf(Color(0xFFD9B8FF), Color(0xFF8D5BD6), Color(0xFF5B2E9E)), startX = base.x - cw / 2, endX = base.x + cw / 2))
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(base.x - cw * 0.2f, base.y - t * 0.1f), Offset(base.x - cw * 0.2f, base.y - t * 0.75f), 1f * d)
                }
            }
        }
        Ornament.HOURGLASS -> {
            // Wooden ends, the glass between, sand running down.
            val wood = Color(0xFF7A4B28)
            drawRoundRect(wood, Offset(0f, 0f), Size(w, 6 * d), CornerRadius(2 * d))
            drawRoundRect(wood, Offset(0f, h - 6 * d), Size(w, 6 * d), CornerRadius(2 * d))
            drawRect(wood, Offset(3 * d, 6 * d), Size(3 * d, h - 12 * d))
            drawRect(wood, Offset(w - 6 * d, 6 * d), Size(3 * d, h - 12 * d))
            val m = h / 2
            val glass = Path().apply {
                moveTo(w * 0.22f, 6 * d); lineTo(w * 0.78f, 6 * d); lineTo(w * 0.53f, m); lineTo(w * 0.78f, h - 6 * d)
                lineTo(w * 0.22f, h - 6 * d); lineTo(w * 0.47f, m); close()
            }
            drawPath(glass, Color(0x55CFE9F5))
            drawPath(glass, Color(0xAAFFFFFF), style = Stroke(1f * d))
            val sand = Color(0xFFE2B866)
            drawPath(Path().apply { moveTo(w * 0.32f, m * 0.55f); lineTo(w * 0.68f, m * 0.55f); lineTo(w * 0.5f, m * 0.95f); close() }, sand)
            drawLine(sand, Offset(w / 2, m), Offset(w / 2, h - 12 * d), 1f * d)
            drawPath(Path().apply { moveTo(w * 0.25f, h - 6 * d); lineTo(w * 0.5f, h - 18 * d); lineTo(w * 0.75f, h - 6 * d); close() }, sand)
        }
        Ornament.GLOBE -> {
            // A globe on a brass stand.
            val r = w * 0.46f
            val c = Offset(w / 2, r + 4 * d)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF7FC4E8), Color(0xFF2E6C9A)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.4f), r, c)
            val land = Color(0xFF6FA35A)
            drawOval(land, Offset(c.x - r * 0.6f, c.y - r * 0.5f), Size(r * 0.7f, r * 0.5f))
            drawOval(land, Offset(c.x + r * 0.05f, c.y - r * 0.1f), Size(r * 0.55f, r * 0.75f))
            drawOval(land, Offset(c.x - r * 0.5f, c.y + r * 0.35f), Size(r * 0.4f, r * 0.3f))
            drawArc(Color(0xFFC9A253), 100f, 250f, false, Offset(c.x - r - 3 * d, c.y - r - 3 * d), Size((r + 3 * d) * 2, (r + 3 * d) * 2), style = Stroke(2 * d))
            drawLine(Color(0xFFC9A253), Offset(w / 2, c.y + r + 3 * d), Offset(w / 2, h - 5 * d), 3 * d)
            drawRoundRect(Color(0xFF8A6A2E), Offset(w * 0.15f, h - 5 * d), Size(w * 0.7f, 5 * d), CornerRadius(2 * d))
        }
        Ornament.BOOKEND -> {
            // A stone bookend, an arch with a crescent moon in it.
            val p = Path().apply {
                moveTo(0f, h); lineTo(0f, w * 0.5f)
                arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, w, w), 180f, 180f, false)
                lineTo(w, h); close()
            }
            drawPath(p, Brush.horizontalGradient(listOf(Color(0xFF8C8577), Color(0xFF5E584E))))
            drawPath(p, Color.Black.copy(alpha = 0.25f), style = Stroke(1f * d))
            val c = Offset(w / 2, w * 0.55f)
            drawCircle(Color(0xFFF2D58A), w * 0.2f, c)
            drawCircle(Color(0xFF746D61), w * 0.17f, Offset(c.x + w * 0.09f, c.y - w * 0.04f))
            listOf(0.62f, 0.72f).forEach { f -> drawCircle(Color(0xFFF2D58A), 1.2f * d, Offset(w * (0.3f + f * 0.4f), h * f)) }
        }
    }
}

/** The planks' wood and trim, and the thing [target] among the books: picking puts it there, "nothing" takes it away. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShelfLookSheet(wood: String, trim: String, decor: String, target: Decor.Placed, finds: Set<Trinket>, onDismiss: () -> Unit) {
    val container = LocalContext.current.container
    val look = PlankLook.of(wood, trim)
    val here = Decor.parse(decor).firstOrNull { it.id == target.id }?.key.orEmpty()
    fun put(key: String) = container.scope.launch {
        container.settings.update { s ->
            val rest = Decor.parse(s.shelfDecor).filter { it.id != target.id }
            s.copy(shelfDecor = Decor.encode(if (key.isEmpty()) rest else rest + target.copy(key = key)))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = U.c.bg) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 40.dp)) {
            Text(stringResource(R.string.shelf_look), style = UType.Title)
            // A plank as it would look, to see the wood and trim on.
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))) { Spacer(Modifier.height(6.dp)); Board(look) }
            SectionTitle(stringResource(R.string.shelf_wood))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Wood.entries.forEach { wd ->
                    Chip(stringResource(woodName(wd)), look.wood == wd, { container.scope.launch { container.settings.update { it.copy(shelfWood = wd.name) } } }, U.c.gold)
                }
            }
            SectionTitle(stringResource(R.string.shelf_trim))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Trim.entries.forEach { t ->
                    Chip(stringResource(trimName(t)), look.trim == t, { container.scope.launch { container.settings.update { it.copy(shelfTrim = t.name) } } }, U.c.gold)
                }
            }
            SectionTitle(stringResource(R.string.shelf_decor_here))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(stringResource(R.string.decor_none), here.isEmpty(), { put("") }) {}
                Ornament.entries.forEach { o ->
                    Tile(stringResource(ornamentName(o)), here == o.name, { put(o.name) }) { Ornament(o.name) }
                }
            }
            if (finds.isNotEmpty()) {
                SectionTitle(stringResource(R.string.shelf_decor_finds))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    finds.sortedBy { it.ordinal }.forEach { t ->
                        val key = Decor.FIND + t.name
                        Tile(null, here == key, { put(key) }) { TrinketArt(t, Modifier.size(40.dp)) }
                    }
                }
            }
        }
    }
}

/** One thing to put on the plank, ringed while it's the one there. */
@Composable
private fun Tile(name: String?, on: Boolean, onClick: () -> Unit, art: @Composable () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(10.dp))
            .border(2.dp, if (on) U.c.accent else U.c.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(if (name == null) 44.dp else 100.dp), contentAlignment = Alignment.BottomCenter) {
            art()
        }
        if (name != null) Text(name, style = UType.Small.copy(color = if (on) U.c.accent else U.c.muted), textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun woodName(w: Wood) = when (w) {
    Wood.OAK -> R.string.wood_oak; Wood.WALNUT -> R.string.wood_walnut; Wood.EBONY -> R.string.wood_ebony
    Wood.BIRCH -> R.string.wood_birch; Wood.WHITE -> R.string.wood_white
}

private fun trimName(t: Trim) = when (t) {
    Trim.NONE -> R.string.trim_none; Trim.BRASS -> R.string.trim_brass; Trim.LIGHTS -> R.string.trim_lights
    Trim.IVY -> R.string.trim_ivy; Trim.LACE -> R.string.trim_lace
}

private fun ornamentName(o: Ornament) = when (o) {
    Ornament.PLANT -> R.string.decor_plant; Ornament.CANDLES -> R.string.decor_candles; Ornament.CRYSTAL -> R.string.decor_crystal
    Ornament.HOURGLASS -> R.string.decor_hourglass; Ornament.GLOBE -> R.string.decor_globe; Ornament.BOOKEND -> R.string.decor_bookend
}
