package com.eduashi.strings

import kotlin.math.*

class MpmDetector(private val windowSize: Int, private val sampleRate: Int) {

    private val nFft = windowSize * 2
    private val fftProcessor = FastFFT(nFft)
    private val re = DoubleArray(nFft)
    private val im = DoubleArray(nFft)

    fun detectPitch(audioBuffer: FloatArray): Float {
        if (audioBuffer.size != windowSize) return -1f

        // 1. Убираем смещение (DC Offset)
        var sum = 0f
        for (x in audioBuffer) sum += x
        val mean = sum / windowSize

        // 2. Готовим данные (ZERO PADDING - добиваем нулями)
        for (i in 0 until nFft) {
            if (i < windowSize) {
                re[i] = (audioBuffer[i] - mean).toDouble()
            } else {
                re[i] = 0.0 // Пустота предотвращает "склейку" конца с началом
            }
            im[i] = 0.0
        }

        // 3. Автокорреляция через FFT
        fftProcessor.fft(re, im)
        for (i in 0 until nFft) {
            val mag = re[i] * re[i] + im[i] * im[i]
            re[i] = mag
            im[i] = 0.0
        }
        fftProcessor.fft(re, im)

        // Вытаскиваем истинный ACF (он теперь идеально чистый)
        val acf = FloatArray(windowSize)
        for (i in 0 until windowSize) {
            acf[i] = (re[i] / nFft).toFloat()
        }

        // 4. Динамический расчет энергии (m_t) - исправляет искажение формы пика
        val m = FloatArray(windowSize)
        var currentM = 0f
        for (i in 0 until windowSize) {
            val centered = audioBuffer[i] - mean
            currentM += centered * centered * 2f
        }
        m[0] = currentM

        val nsdf = FloatArray(windowSize)
        nsdf[0] = if (m[0] > 0.00001f) 2f * acf[0] / m[0] else 0f

        for (t in 1 until windowSize) {
            val prevVal = audioBuffer[t - 1] - mean
            val nextVal = audioBuffer[windowSize - t] - mean
            currentM -= prevVal * prevVal
            currentM -= nextVal * nextVal
            m[t] = currentM

            nsdf[t] = if (m[t] > 0.00001f) 2f * acf[t] / m[t] else 0f
        }

        return findPitchFromNsdf(nsdf)
    }

    private fun findPitchFromNsdf(nsdf: FloatArray): Float {
        val peaks = mutableListOf<Int>()
        for (i in 20 until nsdf.size - 1) {
            if (nsdf[i] > nsdf[i - 1] && nsdf[i] > nsdf[i + 1]) peaks.add(i)
        }

        if (peaks.isEmpty()) return -1f

        var maxVal = 0f
        for (p in peaks) if (nsdf[p] > maxVal) maxVal = nsdf[p]

        if (maxVal < 0.35f) return -1f // Порог для бас-гитары

        // Берем первый значимый пик, чтобы не поймать обертон
        val threshold = maxVal * 0.6f //0.8f
        var chosenPeak = -1
        for (p in peaks) {
            if (nsdf[p] >= threshold) {
                chosenPeak = p
                break
            }
        }

        return if (chosenPeak != -1) calculateFrequency(chosenPeak, nsdf) else -1f
    }

    private fun calculateFrequency(peakIndex: Int, nsdf: FloatArray): Float {
        if (peakIndex <= 0 || peakIndex >= nsdf.size - 1) return sampleRate.toFloat() / peakIndex

        val y1 = nsdf[peakIndex - 1]
        val y2 = nsdf[peakIndex]
        val y3 = nsdf[peakIndex + 1]

        val denom = 2f * y2 - y1 - y3
        if (abs(denom) < 1e-6) return sampleRate.toFloat() / peakIndex

        // Параболическая интерполяция
        val delta = (y3 - y1) / (2f * denom)
        val refinedPeriod = peakIndex + delta

        return sampleRate.toFloat() / refinedPeriod
    }
}


class FastFFT(private val n: Int) {
    private val m = (ln(n.toDouble()) / ln(2.0)).toInt()
    private val cosTable = DoubleArray(n / 2)
    private val sinTable = DoubleArray(n / 2)

    init {
        for (i in 0 until n / 2) {
            cosTable[i] = cos(-2.0 * PI * i / n)
            sinTable[i] = sin(-2.0 * PI * i / n)
        }
    }

    fun fft(re: DoubleArray, im: DoubleArray) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
            var k = n shr 1
            while (k <= j) { j -= k; k = k shr 1 }
            j += k
        }

        var length = 1
        for (step in 0 until m) {
            val prevLength = length
            length = length shl 1
            val wStep = n / length
            for (i in 0 until n step length) {
                for (k in 0 until prevLength) {
                    val wr = cosTable[k * wStep]
                    val wi = sinTable[k * wStep]
                    val tr = re[i + k + prevLength] * wr - im[i + k + prevLength] * wi
                    val ti = re[i + k + prevLength] * wi + im[i + k + prevLength] * wr
                    re[i + k + prevLength] = re[i + k] - tr
                    im[i + k + prevLength] = im[i + k] - ti
                    re[i + k] += tr
                    im[i + k] += ti
                }
            }
        }
    }
}