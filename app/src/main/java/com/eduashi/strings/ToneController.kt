package com.eduashi.strings

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class ToneController {

    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false
    private var frequency = 440.0
    enum class Waveform { SINE, SQUARE, SAW }
    private var currentWaveform = Waveform.SINE


    fun start(freq: Double) {
        if (isPlaying) return // Не запускаем, если уже играет
        frequency = freq
        isPlaying = true

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        Thread {
            val samples = ShortArray(bufferSize)
            var angle = 0.0

            try {
                audioTrack?.play()
                while (isPlaying) {
                    for (i in samples.indices) { // Добавили скобку
                        val sampleValue: Double = when (currentWaveform) {
                            Waveform.SINE -> sin(angle)
                            Waveform.SQUARE -> if (angle < PI) 1.0 else -1.0
                            Waveform.SAW -> (angle / PI) - 1.0
                        }

                        // 1. Записываем значение в массив (с уменьшенной амплитудой 0.5)
                        samples[i] = (sampleValue * Short.MAX_VALUE * 0.5).toInt().toShort()

                        // 2. Обновляем угол (шаг фазы)
                        angle += 2.0 * PI * frequency / sampleRate
                        if (angle > 2.0 * PI) angle -= 2.0 * PI
                    } // Закрыли скобку цикла for

                    val track = audioTrack
                    if (track != null && track.state == AudioTrack.STATE_INITIALIZED && isPlaying) {
                        track.write(samples, 0, samples.size)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopAndRelease()
            }
        }.start()
    }

    fun stop() {
        isPlaying = false
        // Мы не вызываем release здесь, чтобы поток не упал на полуслове.
        // Мы просто ставим флаг, а поток сам завершится и вызовет release.
    }

    private fun stopAndRelease() {
        audioTrack?.apply {
            try {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    this@apply.stop()
                }
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioTrack = null
    }

    fun setFrequency(freq: Double) {
        frequency = freq
    }

    fun setWaveform(wave: Waveform) {
        currentWaveform = wave
    }

}

