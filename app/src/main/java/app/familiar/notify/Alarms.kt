package app.familiar.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.familiar.MainActivity
import app.familiar.R
import app.familiar.container
import app.familiar.content.Lines
import app.familiar.domain.Species
import app.familiar.domain.Stage
import app.familiar.domain.Stats
import app.familiar.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One gentle evening nudge, told by the familiar. Quiet on days you've read. */
object Alarms {
    private const val CHANNEL_REMIND = "remind_v1"
    private const val ID_REMIND = 2
    private const val ACTION_REMIND = "app.familiar.notify.REMIND"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val l = context.localized()
        if (nm.getNotificationChannel(CHANNEL_REMIND) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REMIND, l.getString(R.string.channel_remind), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = l.getString(R.string.channel_remind_desc)
                }
            )
        }
    }

    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Next time the clock shows [minutes] after midnight, strictly after now. */
    fun nextAt(minutes: Int, now: LocalDateTime): LocalDateTime {
        val m = Math.floorMod(minutes, 24 * 60)
        val today = now.toLocalDate().atTime(m / 60, m % 60)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    suspend fun reschedule(context: Context) {
        val s = context.container.settings.now()
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        if (s.remindAt < 0 || !s.onboarded) {
            am.cancel(p)
            return
        }
        val zone = ZoneId.systemDefault()
        val at = nextAt(s.remindAt, LocalDateTime.now(zone)).atZone(zone).toInstant().toEpochMilli()
        // A nudge doesn't need to-the-second precision; this avoids the exact-alarm permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    suspend fun fire(context: Context) {
        val c = context.container
        val s = c.settings.now()
        if (s.remindAt < 0 || !canNotify(context)) return
        val dao = c.db.dao()
        val zone = ZoneId.systemDefault()
        val st = Stats(dao.books(), dao.readings(), dao.pets(), System.currentTimeMillis(), zone, s.dailyGoal, s.guardedDays)
        if (st.todayPages > 0 || s.ritual != null) return
        val pet = st.active ?: return
        val sp = Species.of(pet.species)
        val l = context.localized()
        val locale = l.resources.configuration.locales[0]
        val state = when {
            st.stage == Stage.EGG -> Lines.State.EGG
            st.stone > 0.3f && sp == Species.PHOENIX -> Lines.State.ASH
            st.stone > 0.3f -> Lines.State.STONE
            else -> Lines.State.HUNGRY
        }
        val text = Lines.line(locale, sp, state, pet.name, LocalDate.now(zone).toEpochDay().toInt(), app.familiar.domain.Genre.tails.count { it in st.genresFinished })
        val n = NotificationCompat.Builder(context, CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(l.getString(R.string.notif_title, pet.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 100,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_REMIND, n)
        } catch (_: SecurityException) {
        }
    }

    internal const val REMIND = ACTION_REMIND
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.REMIND && intent.action != CandleBell.ACTION && intent.action != SleepTimer.ACTION) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                if (intent.action == CandleBell.ACTION) {
                    CandleBell.ringIfDue(context)
                    return@launch
                }
                if (intent.action == SleepTimer.ACTION) {
                    SleepTimer.stopIfDue(context)
                    return@launch
                }
                Alarms.fire(context)
                context.container.changed()
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                context.container.changed()
            } finally {
                pending.finish()
            }
        }
    }
}
