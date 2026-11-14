package app.familiar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import app.familiar.container
import app.familiar.data.Ritual

/**
 * The sleep timer of a listening ritual: when it goes off, whatever is playing (the audiobook, in its own app)
 * is paused, and the ritual counts no minutes past that moment ([Ritual.endAt]). An alarm stands behind the
 * app's own timer for when the phone sleeps, as with the candle's bell.
 */
object SleepTimer {
    internal const val ACTION = "app.familiar.notify.SLEEP"

    /** The lengths offered, in minutes. */
    val MINUTES = listOf(15, 30, 45, 60)

    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 4,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets the alarm for the ritual's sleep timer, or takes it away when there is none ahead. */
    fun schedule(context: Context, r: Ritual?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        if (r == null || r.sleepAt <= System.currentTimeMillis()) {
            am.cancel(p)
            return
        }
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.sleepAt, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.sleepAt, p)
    }

    /** Pauses what is playing if the ritual's timer is due; true if it was. Harmless to call twice. */
    suspend fun stopIfDue(context: Context): Boolean {
        val r = context.container.settings.now().ritual ?: return false
        if (r.sleepAt <= 0 || System.currentTimeMillis() < r.sleepAt - 1_000) return false
        pause(context)
        return true
    }

    /**
     * Asks the player to pause, the way a headset button does, and then takes the audio focus for good, which
     * stops players that ignore the button. The focus is given straight back: nothing of ours is playing.
     */
    private fun pause(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        if (!am.isMusicActive) return
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
        runCatching {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .build()
            am.requestAudioFocus(request)
            am.abandonAudioFocusRequest(request)
        }
    }
}
