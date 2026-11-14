package app.familiar.ui.den

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.container
import app.familiar.data.Library
import app.familiar.domain.Form
import app.familiar.domain.Genre
import app.familiar.domain.Trait
import app.familiar.domain.Trinket
import app.familiar.domain.Visit
import app.familiar.ui.FamiliarData
import app.familiar.ui.Go
import app.familiar.ui.components.CelebrateDialog
import app.familiar.ui.components.Chip
import app.familiar.ui.components.FamiliarArt
import app.familiar.ui.components.PrimaryButton
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.SoftButton
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.TrinketArt
import app.familiar.ui.components.UCard
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import app.familiar.ui.trinketName
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Visits: your familiar's card as a QR code, and a friend's familiar welcomed into the den for a day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisitScreen(data: FamiliarData, go: Go) {
    val context = LocalContext.current
    val container = context.container
    val pet = data.pet ?: return
    val sp = data.species ?: return
    // Finished books to recommend, the best rated first.
    val recs = remember(data.books) { data.stats.finished.sortedWith(compareByDescending<app.familiar.data.Book> { it.rating ?: 0 }.thenByDescending { it.finished ?: 0 }).take(8) }
    var rec by rememberSaveable { mutableStateOf(recs.firstOrNull()?.id ?: 0L) }
    val book = recs.firstOrNull { it.id == rec }
    val card = remember(pet, book, data.settings.name) {
        Visit(
            sp, pet.name, pet.mana, pet.shiny, Form.of(pet.form), Trait.of(pet.trait, pet.joined), data.settings.name,
            book?.title.orEmpty(), book?.author.orEmpty(), Genre.of(book?.genre ?: ""),
        ).encode()
    }
    val qr = remember(card) { qr(card, 640) }
    var welcomed by remember { mutableStateOf<Pair<Visit, Trinket?>?>(null) }

    fun welcome(text: String?) {
        val v = text?.let { Visit.decode(it) }
        if (v == null) { Toast.makeText(context, R.string.visit_bad, Toast.LENGTH_SHORT).show(); return }
        if (v.species == sp && v.name == pet.name && v.reader == data.settings.name) { Toast.makeText(context, R.string.visit_own, Toast.LENGTH_SHORT).show(); return }
        container.scope.launch {
            val now = System.currentTimeMillis()
            val stamp = v.key + "|" + data.stats.today
            val gift = if (stamp in container.settings.now().gifts) null else Library.guestGift(container.db.dao(), v.key.hashCode().toLong() + now / 86_400_000, now)
            // A guest counts once a day, however many times its card is shown.
            container.settings.update { it.copy(guest = text!!, guestUntil = now + 24 * 3_600_000L, gifts = (it.gifts + stamp).toList().takeLast(60).toSet(), visits = it.visits + if (stamp in it.gifts) 0 else 1) }
            withContext(Dispatchers.Main) { welcomed = v to gift }
        }
    }

    val scan = rememberLauncherForActivityResult(ScanContract()) { r -> if (r.contents != null) welcome(r.contents) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) container.scope.launch {
            val text = withContext(Dispatchers.Default) { read(context, uri) }
            withContext(Dispatchers.Main) { welcome(text) }
        }
    }

    Column(Modifier.fillMaxSize().background(U.screen).statusBarsPadding()) {
        TopBar(stringResource(R.string.visit), { go.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.visit_text), style = UType.Sub)
            Spacer(Modifier.height(12.dp))
            UCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FamiliarArt(data.look()!!, Modifier.size(84.dp), fps = 4f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pet.name, style = UType.Title)
                        if (book != null) Text(stringResource(R.string.guest_recommends, book.title), style = UType.Muted, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(U.RMd).background(Color.White).padding(12.dp)) {
                    Image(qr.asImageBitmap(), null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.visit_share), {
                    container.scope.launch {
                        val f = withContext(Dispatchers.Default) { Postcard.save(context, VisitCard.draw(context, data, card, book), "visit.png") }
                        withContext(Dispatchers.Main) { Postcard.send(context, f) }
                    }
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
            }
            if (recs.isNotEmpty()) {
                SectionTitle(stringResource(R.string.visit_recommend))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recs.forEach { b -> Chip(b.title.take(28), rec == b.id, { rec = b.id }) }
                    Chip(stringResource(R.string.visit_no_book), rec == 0L, { rec = 0L }, U.c.muted)
                }
            }
            SectionTitle(stringResource(R.string.visit_guest))
            PrimaryButton(stringResource(R.string.visit_scan), {
                scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false).setPrompt(""))
            }, Modifier.fillMaxWidth(), color = U.c.gold, icon = Icons.Rounded.QrCodeScanner)
            Spacer(Modifier.height(8.dp))
            SoftButton(stringResource(R.string.visit_pick), { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Image)
        }
    }
    welcomed?.let { (v, gift) ->
        val st = app.familiar.domain.Stage.of(v.mana)
        CelebrateDialog(
            color = U.c.gold,
            eyebrow = stringResource(R.string.visit_guest),
            title = stringResource(R.string.visit_welcome, v.name),
            text = stringResource(R.string.visit_welcome_text) + (gift?.let { "\n\n" + stringResource(R.string.visit_gift, trinketName(it).lowercase()) } ?: ""),
            onDismiss = { welcomed = null; go.back() },
        ) {
            Box(Modifier.fillMaxSize()) {
                FamiliarArt(app.familiar.ui.pix.Look(v.species, st, shiny = v.shiny, form = if (st >= app.familiar.domain.Stage.ADULT) v.form else Form.NONE, hatch = 0.5f), Modifier.fillMaxSize())
                gift?.let { TrinketArt(it, Modifier.align(Alignment.BottomEnd).size(44.dp)) }
            }
        }
    }
}

/** A QR code of [text], black on white, [size] pixels square. */
fun qr(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
    val px = IntArray(m.width * m.height) { if (m[it % m.width, it / m.width]) 0xFF1A0E22.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

/** Finds a QR code in a picture (a screenshot of a friend's card); null if there is none. */
private fun read(context: Context, uri: Uri): String? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 1400) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
    val pixels = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
    val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))).text
}.getOrNull()
