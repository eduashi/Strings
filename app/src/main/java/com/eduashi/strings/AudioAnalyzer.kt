package com.eduashi.strings

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.sqrt

class AudioAnalyzer(private val onFrequencyDetected: (Float) -> Unit) {

    private val sampleRate = 44100
    private val bufferSize = 4096 // Увеличим для лучшего разрешения на низких частотах
    private var isRunning = false
    private val fft = FloatFFT_1D(bufferSize.toLong())

    // Настройка чувствительности (SNR)
    // 8.0 — золотая середина. Выше — строже, ниже — ловит больше шума.
    private val confidenceMultiplier = 8.0f
    var amplitudeThreshold = 145 // Порог "тишины". Подбери под свой микрофон (500-2000)

    @SuppressLint("MissingPermission")
    fun start() {
        isRunning = true
        Thread {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            val buffer = ShortArray(bufferSize)
            val fftBuffer = FloatArray(bufferSize * 2)

            audioRecord.startRecording()

            while (isRunning) {
                val read = audioRecord.read(buffer, 0, bufferSize)
                if (read > 0) {
                    // 1. Считаем среднюю громкость (RMS)
                    var sumSum = 0.0
                    for (s in buffer) sumSum += s.toDouble() * s
                    val rms = sqrt(sumSum / read)

                    // 2. Если в комнате слишком тихо — вообще не нагружаем FFT
                    if (rms < amplitudeThreshold) {
                        onFrequencyDetected(-1f)
                        continue
                    }
                    // 3. Если громко — делаем FFT как раньше
                    for (i in 0 until bufferSize) fftBuffer[i] = buffer[i].toFloat()
                    fft.realForward(fftBuffer)
                    val frequency = calculateDominantFrequency(fftBuffer)
                    onFrequencyDetected(frequency)
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }.start()
    }

    private fun calculateDominantFrequency(fftData: FloatArray): Float {
        var maxMagnitude = -1f
        var maxIndex = -1
        var sumMagnitude = 0f

        // Вычисляем магнитуды (амплитуды) для каждой корзины (bin)
        // В JTransforms результат лежит как [re, im, re, im...]
        for (i in 0 until bufferSize / 2) {
            val re = fftData[2 * i]
            val im = fftData[2 * i + 1]
            val magnitude = sqrt(re * re + im * im)

            sumMagnitude += magnitude

            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude
                maxIndex = i
            }
        }

        val averageMagnitude = sumMagnitude / (bufferSize / 2)

        // --- ФИЛЬТР ДОСТОВЕРНОСТИ ---
        // Проверяем: наш пик должен быть значительно выше среднего уровня шума
        if (maxMagnitude < averageMagnitude * confidenceMultiplier) {
            return -1.0f // Слишком много шума или тишина
        }

        // Переводим индекс корзины в герцы
        return maxIndex.toFloat() * sampleRate / bufferSize
    }

    fun stop() {
        isRunning = false
    }
}