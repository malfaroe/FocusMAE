package com.focusmae

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

class SoundPlayer {

    fun playClick()    = playTone(1200f, 70,   decay = 25.0)
    fun playWork()     = playTone(440f,  1000)
    fun playRest()     = Thread {
        playToneSync(659f, 600); Thread.sleep(60)
        playToneSync(392f, 600)
    }.start()
    fun playComplete() = Thread {
        playToneSync(440f, 400); Thread.sleep(60)
        playToneSync(523f, 400); Thread.sleep(60)
        playToneSync(659f, 600)
    }.start()

    private fun playTone(freq: Float, ms: Int, decay: Double = 4.5) =
        Thread { playToneSync(freq, ms, decay) }.start()

    private fun playToneSync(freq: Float, ms: Int, decay: Double = 4.5) {
        val rate = 44100
        val n    = rate * ms / 1000
        val buf  = ShortArray(n)
        val attack = (rate * 0.008).toInt()
        for (i in buf.indices) {
            val env = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-decay * i / n)
            buf[i]  = (env * sin(2.0 * PI * freq * i / rate) * 0.95 * Short.MAX_VALUE).toInt().toShort()
        }
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buf, 0, n)
            track.play()
            Thread.sleep(ms.toLong() + 40)
            track.stop()
            track.release()
        } catch (_: Exception) {}
    }
}
