package app.familiar.ui.books

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Book
import app.familiar.data.Covers
import app.familiar.data.Pose
import app.familiar.data.SpineStyle
import app.familiar.ui.components.Chip
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.SoftButton
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.launch

/** How a book looks in the spines view: standing, facing out or lying, and what its spine is like. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookLookSheet(b: Book, all: List<Book>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    // Each change is made to the book as it stands now, not to the copy this sheet was drawn with: a slider
    // let go after another change would otherwise put back what that one changed (a height set, then lost).
    fun save(change: (Book) -> Book) = container.write { dao().book(b.id)?.let { dao().updateBook(change(it)) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) container.scope.launch { Covers.importPicked(context, uri)?.let { pic -> save { it.copy(spine = pic) } } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = U.c.bg) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 40.dp)) {
            Text(b.title, style = UType.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            SectionTitle(stringResource(R.string.pose_title))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Pose.STAND to R.string.pose_stand, Pose.FACE to R.string.pose_face, Pose.LIE to R.string.pose_lie).forEach { (p, res) ->
                    Chip(stringResource(res), b.poseOf == p, { save { it.copy(pose = if (p == Pose.STAND) "" else p.name) } })
                }
            }
            // Lying down: with whatever lies next to it, in a pile of its own, or in a pile made before.
            if (b.poseOf == Pose.LIE) {
                SectionTitle(stringResource(R.string.pile_title))
                val piles = all.filter { it.pile.isNotEmpty() }.groupBy { it.pile }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(stringResource(R.string.pile_auto), b.pile.isEmpty(), { save { it.copy(pose = Pose.LIE.name) } }, U.c.gold)
                    piles.forEach { (k, books) ->
                        if (k == b.pile && books.size == 1) return@forEach
                        Chip(stringResource(R.string.pile_of, books.first().title.take(18), books.size), b.pile == k, { save { it.copy(pose = "LIE:$k") } }, U.c.gold)
                    }
                    Chip("+ " + stringResource(R.string.pile_new), b.pile.isNotEmpty() && piles[b.pile]?.size == 1, { save { it.copy(pose = "LIE:${b.id}") } }, U.c.gold)
                }
            }
            SectionTitle(stringResource(R.string.spine_title))
            // Each style as this very book's spine would look in it; the reader's own picture first when there is one.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                b.spinePicture?.let { own -> Sample(b, own, stringResource(R.string.spine_own), true) { } }
                SpineStyle.entries.forEach { s ->
                    Sample(b, if (s == SpineStyle.AUTO) "" else s.name, stringResource(styleName(s)), b.spinePicture == null && b.spineStyle == s) {
                        save { it.copy(spine = if (s == SpineStyle.AUTO) "" else s.name) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SoftButton(stringResource(R.string.spine_upload), {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Image)
            Text(stringResource(R.string.spine_upload_hint), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))
            if (b.spinePicture != null) SoftButton(stringResource(R.string.spine_own_remove), { save { it.copy(spine = "") } }, Modifier.fillMaxWidth().padding(top = 8.dp), color = U.c.muted, soft = U.c.surface2)
            // Its own height and thickness on the plank (#58), over the size every book has.
            SectionTitle(stringResource(R.string.book_size))
            SizeSlider(stringResource(R.string.book_height), Spines.height(b), Spines.TALL, "") { v -> save { it.copy(tall = v) } }
            SizeSlider(stringResource(R.string.book_thickness), Spines.width(b), Spines.THICK, "") { v -> save { it.copy(thick = v) } }
            if (b.tall > 0 || b.thick > 0) SoftButton(stringResource(R.string.book_size_auto), { save { it.copy(tall = 0, thick = 0) } }, Modifier.fillMaxWidth().padding(top = 8.dp), color = U.c.muted, soft = U.c.surface2)
        }
    }
}

/** One style to pick: the spine in it, its name under it, ringed when it's the one on. */
@Composable
private fun Sample(b: Book, spine: String, name: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.RadioButton, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpineSample(
            b.copy(spine = spine, shelf = app.familiar.data.Shelf.DONE.name),
            34.dp, 128.dp,
            Modifier.border(2.dp, if (on) U.c.accent else Color.Transparent, RoundedCornerShape(3.dp)).padding(3.dp),
        )
        Text(name, style = UType.Small.copy(color = if (on) U.c.accent else U.c.muted), modifier = Modifier.padding(top = 4.dp))
    }
}

private fun styleName(s: SpineStyle) = when (s) {
    SpineStyle.AUTO -> R.string.spine_auto
    SpineStyle.CLOTH -> R.string.spine_cloth
    SpineStyle.LEATHER -> R.string.spine_leather
    SpineStyle.PAPER -> R.string.spine_paper
    SpineStyle.NIGHT -> R.string.spine_night
    SpineStyle.VELLUM -> R.string.spine_vellum
    SpineStyle.MODERN -> R.string.spine_modern
}

/**
 * A size to set with a slider: [label] and the value with its [unit] above it. It's saved when the finger
 * lifts ([onSet]), not on every step of the drag, so the shelf isn't packed again all the way.
 */
@Composable
fun SizeSlider(label: String, value: Int, range: IntRange, unit: String, onSet: (Int) -> Unit) {
    var v by androidx.compose.runtime.remember(value) { androidx.compose.runtime.mutableFloatStateOf(value.toFloat()) }
    val set by androidx.compose.runtime.rememberUpdatedState(onSet)
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = UType.Body, modifier = Modifier.weight(1f))
            Text(v.toInt().toString() + unit, style = UType.Small.copy(color = U.c.muted))
        }
        androidx.compose.material3.Slider(
            value = v.coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { v = it },
            onValueChangeFinished = { set(v.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = U.c.accent, activeTrackColor = U.c.accent, inactiveTrackColor = U.c.surface2),
        )
    }
}
