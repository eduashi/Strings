package com.eduashi.strings

class MetronomeController(
    private val onBpmChanged: (Int) -> Unit,
    private val onTick: (Int) -> Unit) {

    var bpm = 120
        set(value) {
            field = value.coerceIn(30, 300) // Разрешаем от 30 до 300
            onBpmChanged(field)
            if (engine.isRunning) restart()
        }

    var beatsPerMeasure = 4
        set(value) {
            field = value
            engine.beatsPerMeasure = value
            if (engine.isRunning) restart()
        }

    private val engine = MetronomeEngine(bpm, beatsPerMeasure, onTick)
    private val tapTimes = mutableListOf<Long>()

    val timeSignatures = arrayOf(
        "1/4", "2/4", "3/4", "4/4", "5/4", "7/4",
        "5/8", "6/8", "7/8", "9/8", "12/8"
    )

    fun toggle() {
        if (engine.isRunning) engine.stop() else engine.start()
    }

    fun isRunning() = engine.isRunning

    fun restart() {
        engine.stop()
        engine.bpm = bpm
        engine.start()
    }

    fun stop() = engine.stop()

    fun handleTap() {
        val currentTime = System.currentTimeMillis()
        tapTimes.add(currentTime)
        if (tapTimes.size > 4) tapTimes.removeAt(0)

        if (tapTimes.size >= 2) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until tapTimes.size) {
                intervals.add(tapTimes[i] - tapTimes[i - 1])
            }
            val avg = intervals.average()
            bpm = (60000 / avg).toInt()
        }
    }

    fun setSignatureByPosition(position: Int) {
        val selected = timeSignatures[position]
        val parts = selected.split("/")
        val beats = parts[0].toInt()
        val noteValue = parts[1].toInt() // Нижнее число: 4 или 8

        beatsPerMeasure = beats

        // Если знаменатель 8, мы ускоряем движок в 2 раза,
        // чтобы "клики" соответствовали восьмым нотам
        engine.multiplier = if (noteValue == 8) 2.0 else 1.0

        if (engine.isRunning) restart()
    }

}