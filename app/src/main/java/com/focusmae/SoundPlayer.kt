package com.focusmae

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

class SoundPlayer {

    fun playClick()    = Thread { playClickSync() }.start()
    fun playWork()     = Thread { playToneSync(440f,  1000) }.start()
    fun playRest()     = Thread {
        playToneSync(659f, 600); Thread.sleep(60)
        playToneSync(392f, 600)
    }.start()
    fun playComplete() = Thread {
        playToneSync(440f, 400); Thread.sleep(60)
        playToneSync(523f, 400); Thread.sleep(60)
        playToneSync(659f, 600)
    }.start()

    // Wood-block click: 800 Hz fundamental + 0.25 of 1600 Hz harmonic,
    // 200 ms buffer (safely above device minimum), exp(-22) decay ≈ 40 ms audible.
    private fun playClickSync() {
        val rate = 44100
        val ms   = 200
        val n    = rate * ms / 1000   // 8820 samples — well above any device minimum
        val buf  = ShortArray(n)
        val attackSamples = (rate * 0.002).toInt()   // 2 ms attack
        for (i in buf.indices) {
            val env  = (if (i < attackSamples) i.toDouble() / attackSamples else 1.0) * exp(-22.0 * i / n)
            val wave = sin(2.0 * PI * 800.0 * i / rate) + 0.25 * sin(2.0 * PI * 1600.0 * i / rate)
            buf[i]   = (env * wave / 1.25 * 0.92 * Short.MAX_VALUE).toInt().toShort()
        }
        playBuf(buf, rate, ms)
    }

    private fun playToneSync(freq: Float, ms: Int) {
        val rate = 44100
        val n    = rate * ms / 1000
        val buf  = ShortArray(n)
        val attack = (rate * 0.008).toInt()
        for (i in buf.indices) {
            val env = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-4.5 * i / n)
            buf[i]  = (env * sin(2.0 * PI * freq * i / rate) * 0.95 * Short.MAX_VALUE).toInt().toShort()
        }
        playBuf(buf, rate, ms)
    }

    private fun playBuf(buf: ShortArray, rate: Int, ms: Int) {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
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
                .setBufferSizeInBytes(maxOf(buf.size * 2, minBuf))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buf, 0, buf.size)
            track.play()
            Thread.sleep(ms.toLong() + 40)
            track.stop()
            track.release()
        } catch (_: Exception) {}
    }
}
