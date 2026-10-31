package app.familiar.ui.me

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.familiar.BuildConfig
import app.familiar.R
import app.familiar.container
import app.familiar.data.Backup
import app.familiar.data.Covers
import app.familiar.data.Import
import app.familiar.data.Settings
import app.familiar.data.ThemeMode
import app.familiar.notify.Alarms
import app.familiar.ui.FamiliarData
import app.familiar.ui.Language
import app.familiar.ui.components.Chip
import app.familiar.ui.components.CommunityLinks
import app.familiar.ui.components.ConfirmDialog
import app.familiar.ui.components.InfoRow
import app.familiar.ui.components.SectionTitle
import app.familiar.ui.components.SwitchRow
import app.familiar.ui.components.TimeDialog
import app.familiar.ui.components.TopBar
import app.familiar.ui.components.UCard
import app.familiar.ui.components.UTextField
import app.familiar.ui.locale
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Where the website explains exporting from LiveLib (and that a Goodreads export works too). */
private const val EXPORT_HOWTO = "https://bigdevwhale.github.io/stillcraft/apps/familiar/livelib/"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(data: FamiliarData, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val settings = data.settings
    fun update(change: (Settings) -> Settings) {
        container.scope.launch {
            container.settings.update(change)
            container.changed()
        }
    }
    var canNotify by remember { mutableStateOf(Alarms.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = Alarms.canNotify(context) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
        container.scope.launch { container.changed() }
    }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(settings.name) }
    LaunchedEffect(name) {
        delay(400)
        if (name.trim() != settings.name) container.settings.update { it.copy(name = name.trim()) }
    }
    val l = locale()
    val exported = stringResource(R.string.backup_done)
    val failed = stringResource(R.string.backup_failed)
    val restored = stringResource(R.string.backup_restored)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.scope.launch {
            val ok = runCatching {
                val json = Backup.encode(container.db.dao().snapshot()) { Covers.bytes(context, it) }
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }.isSuccess
            withContext(Dispatchers.Main) { Toast.makeText(context, if (ok) exported else failed, Toast.LENGTH_SHORT).show() }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    val importDone = stringResource(R.string.import_done)
    val importFailed = stringResource(R.string.import_failed)
    val books = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.scope.launch {
            val result = runCatching {
                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                val dao = container.db.dao()
                val found = Import.parse(text, dao.books())
                dao.addBooks(found.books)
                Import.wantCovers(context.filesDir, dao, found)
                container.changed()
                found
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, result.map { importDone.format(it.books.size, it.skipped) }.getOrElse { importFailed }, Toast.LENGTH_LONG).show()
            }
            if (result.isSuccess) runCatching { Import.covers(context.filesDir, container.db.dao()) { container.changed() } }
        }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.settings), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            if (!canNotify) {
                Spacer(Modifier.height(12.dp))
                UCard(onClick = { if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.perm_title), style = UType.Strong.copy(color = U.c.accent))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.perm_text), style = UType.Muted)
                }
            }

            SectionTitle(stringResource(R.string.set_name))
            UTextField(name, { name = it.take(30) }, placeholder = stringResource(R.string.ob_name_hint))

            SectionTitle(stringResource(R.string.set_goal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 50, 80).forEach { n -> Chip(stringResource(R.string.pages_short, n), settings.dailyGoal == n, { update { it.copy(dailyGoal = n) } }) }
            }
            Text(stringResource(R.string.set_goal_note), style = UType.Small.copy(color = U.c.muted), modifier = Modifier.padding(top = 6.dp))

            SectionTitle(stringResource(R.string.set_ritual))
            UCard {
                Text(stringResource(R.string.set_candle), style = UType.Body)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { n -> Chip(stringResource(R.string.min_short, n), settings.candleMin == n, { update { it.copy(candleMin = n) } }) }
                }
                Spacer(Modifier.height(6.dp))
                SwitchRow(stringResource(R.string.set_page_sound), null, settings.pageSound, { v -> update { it.copy(pageSound = v) } })
                SwitchRow(stringResource(R.string.set_haptics), null, settings.haptics, { v -> update { it.copy(haptics = v) } })
                SwitchRow(stringResource(R.string.set_dim), stringResource(R.string.set_dim_sub), settings.stillDim, { v -> update { it.copy(stillDim = v) } })
            }

            SectionTitle(stringResource(R.string.set_remind))
            UCard {
                val t = settings.remindAt
                InfoRow(
                    Icons.Rounded.Notifications, stringResource(R.string.set_remind_daily),
                    if (t < 0) stringResource(R.string.off) else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(l).format(LocalTime.of(t / 60, t % 60)),
                ) { pickTime = true }
                if (t >= 0) TextButton(onClick = { update { it.copy(remindAt = -1) } }) { Text(stringResource(R.string.set_remind_off), color = U.c.muted, style = UType.Button) }
                Text(stringResource(R.string.set_remind_note), style = UType.Small.copy(color = U.c.muted))
            }
            Spacer(Modifier.height(10.dp))
            UCard(onClick = {
                context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) {
                Text(stringResource(R.string.autostart_title), style = UType.Strong)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.autostart_text), style = UType.Muted)
            }

            SectionTitle(stringResource(R.string.set_appearance))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.theme_system), settings.theme == ThemeMode.SYSTEM, { update { it.copy(theme = ThemeMode.SYSTEM) } })
                Chip(stringResource(R.string.theme_light), settings.theme == ThemeMode.LIGHT, { update { it.copy(theme = ThemeMode.LIGHT) } })
                Chip(stringResource(R.string.theme_dark), settings.theme == ThemeMode.DARK, { update { it.copy(theme = ThemeMode.DARK) } })
            }

            SectionTitle(stringResource(R.string.language))
            val current = Language.current()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.lang_system), current == Language.SYSTEM, { Language.set(Language.SYSTEM) })
                Chip("English", current == Language.EN, { Language.set(Language.EN) })
                Chip("Русский", current == Language.RU, { Language.set(Language.RU) })
            }

            SectionTitle(stringResource(R.string.backup))
            UCard {
                Text(stringResource(R.string.backup_text), style = UType.Muted)
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { export.launch("familiar-${LocalDate.now()}.json") }) {
                        Text(stringResource(R.string.backup_export), color = U.c.accent, style = UType.Button)
                    }
                    TextButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text(stringResource(R.string.backup_import), color = U.c.accent, style = UType.Button)
                    }
                }
            }

            SectionTitle(stringResource(R.string.import_books))
            UCard {
                Text(stringResource(R.string.import_text), style = UType.Muted)
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { books.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }) {
                        Text(stringResource(R.string.import_pick), color = U.c.accent, style = UType.Button)
                    }
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EXPORT_HOWTO))) }) {
                        Text(stringResource(R.string.import_how), color = U.c.accent, style = UType.Button)
                    }
                }
            }

            CommunityLinks()

            SectionTitle(stringResource(R.string.about))
            UCard {
                Text(stringResource(R.string.about_text), style = UType.Sub)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = UType.Small)
            }
        }
    }

    if (pickTime) TimeDialog(if (settings.remindAt < 0) 21 * 60 else settings.remindAt, { pickTime = false }) { m -> update { it.copy(remindAt = m) } }
    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_import_title),
            text = stringResource(R.string.backup_import_text),
            confirm = stringResource(R.string.backup_import),
            danger = true,
            onConfirm = {
                container.scope.launch {
                    val ok = runCatching {
                        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                        container.db.dao().replaceAll(Backup.decode(text) { url, bytes -> Covers.restore(context, url, bytes) })
                        container.changed()
                    }.isSuccess
                    withContext(Dispatchers.Main) { Toast.makeText(context, if (ok) restored else failed, Toast.LENGTH_SHORT).show() }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}
