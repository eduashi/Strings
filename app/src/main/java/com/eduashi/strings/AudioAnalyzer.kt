package com.eduashi.strings

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

class AudioAnalyzer(private val onFrequencyDetected: (Float) -> Unit,
                    private val onVolumeChanged: (Double) -> Unit) {

    private val sampleRate = 44100
    private val targetSampleRate = 22050
    private val rawBufferSize = 4096
    private val processedBufferSize = 2048
    @Volatile
    private var isRunning = false

    private val mpmDetector = MpmDetector(processedBufferSize, targetSampleRate)

    var amplitudeThreshold = 145
    private var lastSample = 0f

    fun start() {
        lastSample = 0f
        if (isRunning) return // ЕСЛИ УЖЕ ЗАПУЩЕН — НИЧЕГО НЕ ДЕЛАЕМ
        isRunning = true
        Thread {
            try {
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    rawBufferSize * 2
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    isRunning = false
                    return@Thread
                }

                val rawBuffer = ShortArray(rawBufferSize)
                val downsampledBuffer = FloatArray(processedBufferSize)
                audioRecord.startRecording()

                while (isRunning) {
                    val read = audioRecord.read(rawBuffer, 0, rawBufferSize)
                    if (read == rawBufferSize) {
                        var sumSum = 0.0
                        for (s in rawBuffer) sumSum += s.toDouble() * s
                        val rms = sqrt(sumSum / rawBufferSize)

                        onVolumeChanged(rms)

                        // Если звук ниже порога — шлем -1 (для тюнера это "тишина")
                        if (rms < amplitudeThreshold) {
                            onFrequencyDetected(-1f)
                            continue
                        }

                        // ... остальной код (FFT и MPM) без изменений ...
                        val floatRaw = FloatArray(rawBufferSize)
                        for (i in 0 until rawBufferSize) floatRaw[i] = rawBuffer[i] / 32768f
                        val filtered = applyLowPassFilter(floatRaw)
                        for (i in 0 until processedBufferSize) downsampledBuffer[i] = filtered[i * 2]

                        val pitch = mpmDetector.detectPitch(downsampledBuffer)
                        onFrequencyDetected(pitch)
                    }
                }
                audioRecord.stop()
                audioRecord.release()
            } catch (e: SecurityException) {
                e.printStackTrace()
                isRunning = false
            } catch (e: Exception) {
                e.printStackTrace()
                isRunning = false
            }
        }.start()
    }

    fun stop() {
        isRunning = false
    }

    private fun applyLowPassFilter(input: FloatArray): FloatArray {
        val LOW_PASS_FILTER_ALPHA = 0.25f //0.5f
        for (i in input.indices) {
            input[i] = lastSample + LOW_PASS_FILTER_ALPHA * (input[i] - lastSample)
            lastSample = input[i]
        }
        return input
    }
}