package com.eduashi.strings

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

object MusicUtils {
    // Список названий нот (единый для всех функций)
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octaves = (0..8).toList()

    // Функция 1: Из частоты в название (для слайдера)
    fun frequencyToNoteName(frequency: Double): String {
        if (frequency <= 0) return "--"
        val n = (12 * log2(frequency / 440.0)).roundToInt()
        val noteIndex = ((n + 9) % 12).let { if (it < 0) it + 12 else it }
        val octave = (n + 57) / 12
        return noteNames[noteIndex] + octave
    }

    // Функция 2: Из ноты в частоту (для выпадающих списков)
    fun getFrequency(noteIndex: Int, octave: Int): Double {
        val midiNote = (octave + 1) * 12 + noteIndex
        return 440.0 * 2.0.pow((midiNote - 69) / 12.0)
    }

    // Функция 3: Поиск индекса по имени
    fun getNoteIndex(noteName: String): Int {
        return noteNames.indexOf(noteName)
    }
}