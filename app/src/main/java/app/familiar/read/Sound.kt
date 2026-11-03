package app.familiar.read

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.familiar.data.Ambience
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The study's sounds, synthesised on the fly so the app ships no audio files:
 * a fire crackling or rain on the window during a ritual, and the rustle of a page turned.
 */
object Sound {
    private const val RATE = 22_050

    @Volatile private var playing: Ambience = Ambience.OFF
    private var thread: Thread? = null

    fun ambience(kind: Ambience) {
        if (kind == playing && thread?.isAlive == true) return
        stop()
        if (kind == Ambience.OFF) return
        playing = kind
        thread = Thread({ loop(kind) }, "ambience").apply { isDaemon = true; start() }
    }

    fun stop() {
        playing = Ambience.OFF
        thread?.join(400)
        thread = null
    }

    private fun attrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun loop(kind: Ambience) {
        val chunk = 2048
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(attrs())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, chunk * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        val gen = if (kind == Ambience.FIRE) Fire() else Rain()
        val buf = ShortArray(chunk)
        var n = 0L
        try {
            track.play()
            while (playing == kind) {
                for (i in 0 until chunk) {
                    // Fade in over the first two seconds.
                    val fade = (n / (RATE * 2.0)).coerceAtMost(1.0)
                    buf[i] = (gen.next() * fade * 0.55 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                    n++
                }
                track.write(buf, 0, chunk)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private interface Gen { fun next(): Double }

    /** Brown noise for the roar, with crackles: short bursts of bright noise. */
    private class Fire : Gen {
        private val rnd = Random(11)
        private var brown = 0.0
        private var crack = 0.0
        private var crackDecay = 0.0
        private var prev = 0.0
        private var t = 0.0

        override fun next(): Double {
            t += 1.0 / RATE
            brown = (brown + rnd.nextDouble(-1.0, 1.0) * 0.02) * 0.998
            val swell = 0.75 + 0.25 * sin(t * 0.7) * sin(t * 0.23 + 1.0)
            if (rnd.nextDouble() < 9.0 / RATE) {
                crack = 0.4 + rnd.nextDouble() * 0.6
                crackDecay = 0.9 + rnd.nextDouble() * 0.08
            }
            val white = rnd.nextDouble(-1.0, 1.0)
            val bright = white - prev
            prev = white
            val c = bright * crack
            crack *= crackDecay
            return brown * 2.2 * swell + c * 0.45
        }
    }

    /** Soft noise for the rain and tiny pings of drops on the glass. */
    private class Rain : Gen {
        private val rnd = Random(5)
        private var low = 0.0
        private var dropF = 0.0
        private var dropA = 0.0
        private var dropT = 0.0
        private var t = 0.0

        override fun next(): Double {
            t += 1.0 / RATE
            low += (rnd.nextDouble(-1.0, 1.0) - low) * 0.35
            if (rnd.nextDouble() < 28.0 / RATE) {
                dropF = 1800.0 + rnd.nextDouble() * 2600.0
                dropA = 0.05 + rnd.nextDouble() * 0.12
                dropT = 0.0
            }
            dropT += 1.0 / RATE
            val drop = dropA * sin(2 * PI * dropF * dropT) * exp(-dropT * 160)
            val swell = 0.8 + 0.2 * sin(t * 0.31)
            return low * 0.28 * swell + drop
        }
    }

    private var bell: ShortArray? = null

    /** Two soft strokes of a small bell: a few inharmonic partials dying away, the way metal rings. */
    fun chime() {
        val pcm = bell ?: run {
            val stroke = (1.4 * RATE).toInt()
            val gap = (0.55 * RATE).toInt()
            val partials = listOf(1.0 to 1.0, 2.76 to 0.45, 5.4 to 0.22, 8.93 to 0.1)
            ShortArray(gap + stroke) { i ->
                fun ring(t: Double, f: Double): Double = if (t < 0) 0.0 else
                    partials.sumOf { (k, a) -> a * sin(2 * PI * f * k * t) * exp(-t * (2.2 + k * 0.9)) } * (t * 400).coerceAtMost(1.0)
                val t = i.toDouble() / RATE
                val v = ring(t, 1046.5) + 0.85 * ring(t - gap.toDouble() / RATE, 784.0)
                (v * 0.22 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }.also { bell = it }
        }
        Thread {
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@Thread
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(pcm.size * 1000L / RATE + 200)
            track.release()
        }.start()
    }

    private var rustle: ShortArray? = null

    /** The swish of a page: band-limited noise that rises and falls in a quarter of a second. */
    fun page() {
        val pcm = rustle ?: run {
            val rnd = Random(3)
            val n = (0.26 * RATE).toInt()
            var low = 0.0
            var prev = 0.0
            ShortArray(n) { i ->
                val x = i.toDouble() / n
                val env = sin(PI * x).let { it * it } * (1 - x * 0.4)
                val white = rnd.nextDouble(-1.0, 1.0)
                low += (white - low) * 0.5
                val band = low - prev
                prev = low
                (band * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
            }.also { rustle = it }
        }
        Thread {
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(attrs())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@Thread
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(400)
            track.release()
        }.start()
    }
}
