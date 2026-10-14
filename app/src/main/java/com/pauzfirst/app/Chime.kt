package com.pauzfirst.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * A soft chime for the Pause and Respond cues: one note at Pause, two rising notes at Respond
 * (the same pattern as the vibration). The sound is made here, so no sound file is needed.
 */
object Chime {
    private const val RATE = 22050

    private fun note(freq: Double, ms: Int): ShortArray {
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val fadeIn = Math.min(1.0, i / (RATE * 0.01))
            val fadeOut = Math.min(1.0, (n - i) / (RATE * 0.02))
            val env = Math.exp(-t * 7.0) * fadeIn * fadeOut
            val v = Math.sin(2 * Math.PI * freq * t) + 0.3 * Math.sin(4 * Math.PI * freq * t)
            out[i] = (v / 1.3 * env * 0.5 * 32767).toInt().toShort()
        }
        return out
    }

    private fun build(pulses: Int): ShortArray {
        val low = note(660.0, 450)
        if (pulses <= 1) return low
        val gap = ShortArray(RATE * 80 / 1000)
        val high = note(880.0, 450)
        return low + gap + high
    }

    /** volume: 0..1, applied on top of the phone's media volume. */
    fun play(pulses: Int, volume: Float) {
        Thread {
            var track: AudioTrack? = null
            try {
                val data = build(pulses)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val fmt = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val t = AudioTrack(attrs, fmt, data.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
                track = t
                t.write(data, 0, data.size)
                t.setVolume(volume.coerceIn(0f, 1f))
                t.play()
                Thread.sleep(data.size * 1000L / RATE + 150L)
            } catch (e: Exception) {
            } finally {
                try { track?.release() } catch (e: Exception) { }
            }
        }.start()
    }
}
