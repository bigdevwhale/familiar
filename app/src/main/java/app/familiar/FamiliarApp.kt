package app.familiar

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.familiar.data.DemoSeed
import app.familiar.data.FamiliarDatabase
import app.familiar.data.Import
import app.familiar.data.SettingsStore
import app.familiar.notify.Alarms
import app.familiar.widget.DenWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {
    val db = FamiliarDatabase.create(context)
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget so a write survives the screen being closed right after tapping save. */
    fun write(block: suspend FamiliarDatabase.() -> Unit) {
        scope.launch {
            db.block()
            changed()
        }
    }

    /** Re-arm the reminder and redraw the home-screen widget. */
    suspend fun changed() {
        Alarms.reschedule(context)
        DenWidget.refresh(context)
    }
}

class FamiliarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Alarms.ensureChannels(this)
        // Re-arm on every start: OEM task killers (HyperOS & co.) silently drop alarms.
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(container.db, container.settings)
            // The database was started fresh (a changed schema): meet a new familiar instead of an empty den.
            val s = container.settings.now()
            // Familiars grown before forms existed take theirs now.
            app.familiar.data.Library.settleForms(container.db.dao())
            if (s.onboarded && container.db.dao().pets().isEmpty()) {
                container.settings.update { app.familiar.data.Settings(theme = it.theme, name = it.name, remindAt = it.remindAt, dailyGoal = it.dailyGoal) }
            }
            container.changed()
        }
        // Covers still being looked up for imported books when the app was last closed.
        container.scope.launch { runCatching { Import.covers(filesDir, container.db.dao()) { container.changed() } } }
    }
}

val Context.container: AppContainer get() = (applicationContext as FamiliarApp).container

/** Resources in the in-app language, for receivers that AppCompat doesn't localise before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}
