package com.eduashi.strings

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.sin

class MetronomeEngine(var bpm: Int, var beatsPerMeasure: Int, val onTick: (Int) -> Unit) {
    private var executor: ScheduledThreadPoolExecutor? = null
    private var currentBeat = 0
    var isRunning = false

    private val sampleRate = 44100
    private var tickTrack: AudioTrack? = null
    private var accentTrack: AudioTrack? = null

    var multiplier: Double = 1.0

    init {
        // Подготавливаем треки заранее один раз
        val  TICK_FREQ = generateClick(800.0)
        val ACCENT_FREQ = generateClick(1200.0)

        tickTrack = createStaticTrack(TICK_FREQ)
        accentTrack = createStaticTrack(ACCENT_FREQ)
    }

    private fun createStaticTrack(buffer: ShortArray): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build().apply {
                write(buffer, 0, buffer.size)
            }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        currentBeat = 0
        executor = ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }

        val intervalNanos = (60_000_000_000.0 / (bpm * multiplier)).toLong()

        // Используем scheduleAtFixedRate для строгого соблюдения темпа
        executor?.scheduleWithFixedDelay({
            currentBeat = (currentBeat % beatsPerMeasure) + 1

            // Проигрываем заранее созданный трек
            if (currentBeat == 1) {
                accentTrack?.stop()
                accentTrack?.reloadStaticData()
                accentTrack?.play()
            } else {
                tickTrack?.stop()
                tickTrack?.reloadStaticData()
                tickTrack?.play()
            }

            onTick(currentBeat)
        }, 0, intervalNanos, TimeUnit.NANOSECONDS)
    }

    fun stop() {
        isRunning = false
        executor?.shutdownNow()
        executor = null
        tickTrack?.pause()
        accentTrack?.pause()
    }

    private fun generateClick(freq: Double): ShortArray {
        val duration = 0.02
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val envelope = exp(-i.toDouble() / (numSamples / 3))
            buffer[i] = (sin(2.0 * Math.PI * i / (sampleRate / freq)) * Short.MAX_VALUE * envelope).toInt().toShort()
        }
        return buffer
    }
}