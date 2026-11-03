package app.familiar.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.familiar.MainActivity
import app.familiar.R
import app.familiar.container
import app.familiar.data.Ritual
import app.familiar.localized
import app.familiar.read.Sound

/**
 * Tells once that the ritual candle has burned out, so reading doesn't run on unnoticed: a chime and a
 * buzz while the app is in front, a notification when it isn't. An alarm stands behind the app's own
 * timer for when the phone sleeps or the app was closed.
 */
object CandleBell {
    private const val CHANNEL = "candle_v1"
    private const val ID = 3
    internal const val ACTION = "app.familiar.notify.CANDLE"
    private val PATTERN = longArrayOf(0, 220, 160, 220)

    /** The app is on screen; set by [MainActivity]. */
    @Volatile var front = false

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val l = context.localized()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, l.getString(R.string.channel_candle), NotificationManager.IMPORTANCE_HIGH).apply {
                description = l.getString(R.string.channel_candle_desc)
                enableVibration(true)
                vibrationPattern = PATTERN
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            }
        )
    }

    /** When the candle of [r] burns out. */
    fun due(r: Ritual, candleMin: Int): Long = r.start + candleMin * 60_000L

    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 2,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets the alarm for the ritual burning now, or takes it away when there's none (or the bell is off). */
    fun schedule(context: Context, r: Ritual?, candleMin: Int, on: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        if (r == null || r.rang || !on) {
            am.cancel(p)
            return
        }
        val at = due(r, candleMin)
        // To the minute when the phone allows exact alarms; otherwise the app's own timer is the sure one.
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    /** Rings for the ritual burning now if it is due and hasn't rung yet; true if it rang. */
    suspend fun ringIfDue(context: Context): Boolean {
        val c = context.container
        val s = c.settings.now()
        val r = s.ritual ?: return false
        if (!s.candleBell || r.rang || System.currentTimeMillis() < due(r, s.candleMin) - 1_000) return false
        var mine = false
        // Marked rung first, so the app's timer and the alarm never both ring.
        c.settings.update { st ->
            val now = st.ritual
            if (now != null && now.start == r.start && !now.rang) { mine = true; st.copy(ritual = now.copy(rang = true)) } else st
        }
        if (!mine) return false
        if (front) {
            Sound.chime()
            buzz(context)
        } else notify(context, s.candleMin)
        return true
    }

    private fun buzz(context: Context) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        runCatching { v?.vibrate(VibrationEffect.createWaveform(PATTERN, -1)) }
    }

    private fun notify(context: Context, minutes: Int) {
        if (!Alarms.canNotify(context)) return
        ensureChannel(context)
        val l = context.localized()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(l.getString(R.string.candle_bell_title))
            .setContentText(l.getString(R.string.candle_bell_text, minutes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(PATTERN)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 101,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
        }
    }
}
