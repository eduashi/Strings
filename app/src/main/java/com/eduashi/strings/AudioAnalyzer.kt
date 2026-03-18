package com.eduashi.strings

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

class AudioAnalyzer(private val onPitchDetected: (Float) -> Unit) {

    private val sampleRate = 22050
    private val bufferSize = 2048 // Оптимально для скорости и точности
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun start() {
        isRecording = true
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        val buffer = ShortArray(bufferSize)
        audioRecord.startRecording()

        Thread {
            while (isRecording) {
                val read = audioRecord.read(buffer, 0, bufferSize)
                if (read > 0) {
                    val volume = calculateRMS(buffer, read)
                    // Порог чувствительности: игнорируем тишину
                    if (volume > 40) {
                        val freq = autoCorrelate(buffer, read)
                        if (freq > 30f && freq < 1000f) {
                            onPitchDetected(freq)
                        }
                    }
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }.start()
    }

    private fun autoCorrelate(buffer: ShortArray, size: Int): Float {
        var bestOffset = -1
        var maxCorrelation = -1f

        // Диапазон поиска: от 30 Гц до 1000 Гц
        // Превращаем частоту в смещение (лаг) в семплах
        val minLag = sampleRate / 1000 // Для высоких частот
        val maxLag = sampleRate / 30   // Для низких частот (около 735 семплов)

        for (lag in minLag until maxLag) {
            var correlation = 0f
            for (i in 0 until size - lag) {
                correlation += (buffer[i].toFloat() * buffer[i + lag].toFloat())
            }

            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestOffset = lag
            }
        }

        return if (bestOffset != -1) {
            sampleRate.toFloat() / bestOffset
        } else {
            -1f
        }
    }

    private fun calculateRMS(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) sum += buffer[i] * buffer[i]
        return sqrt(sum / read)
    }

    fun stop() { isRecording = false }
}