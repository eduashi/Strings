package com.eduashi.strings

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MetronomeEngine(var bpm: Int, var beatsPerMeasure: Int, val onTick: (Int) -> Unit) {
    private var executor: ScheduledThreadPoolExecutor? = null
    private var currentBeat = 0
    var isRunning = false

    // Генерация звука "клика" в памяти
    private val sampleRate = 44100
    private val tickBuffer: ShortArray = generateClick(800.0)    // Обычный удар
    private val accentBuffer: ShortArray = generateClick(1200.0) // Акцент (первая доля)

    var multiplier: Double = 1.0 // По умолчанию 1.0 для четвертей

    fun start() {
        if (isRunning) return
        isRunning = true
        currentBeat = 0
        executor = ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }

        val interval = (60000L / (bpm * multiplier)).toLong()
        executor?.scheduleWithFixedDelay({
            currentBeat = (currentBeat % beatsPerMeasure) + 1

            // Играем звук (в отдельном потоке, чтобы не тормозить таймер)
            playClick(if (currentBeat == 1) accentBuffer else tickBuffer)

            // Обновляем UI (мигание шкалы)
            onTick(currentBeat)
        }, 0, interval, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        isRunning = false
        executor?.shutdownNow()
        executor = null
    }

    private fun generateClick(freq: Double): ShortArray {
        val duration = 0.02 // 20 мс - очень короткий щелчок
        val numSamples = (duration * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            // Затухающая синусоида для мягкого клика
            val envelope = Math.exp(-i.toDouble() / (numSamples / 3))
            buffer[i] = (Math.sin(2.0 * Math.PI * i / (sampleRate / freq)) * Short.MAX_VALUE * envelope).toInt().toShort()
        }
        return buffer
    }

    private fun playClick(buffer: ShortArray) {
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC, sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2, AudioTrack.MODE_STATIC
        )
        track.write(buffer, 0, buffer.size)
        track.play()
        // Освобождаем ресурсы после проигрывания
        track.setNotificationMarkerPosition(buffer.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onPeriodicNotification(t: AudioTrack?) {}
            override fun onMarkerReached(t: AudioTrack?) { t?.release() }
        })
    }
}