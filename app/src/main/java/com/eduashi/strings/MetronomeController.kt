package com.eduashi.strings

class MetronomeController(
    private val onBpmChanged: (Int) -> Unit,
    private val onTick: (Int) -> Unit) {

    var bpm = 120
        set(value) {
            val validatedBpm = value.coerceIn(30, 300)
            field = validatedBpm

            engine.bpm = validatedBpm
            onBpmChanged(validatedBpm)
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
        if (engine.isRunning) {
            engine.stop()
        } else {
            engine.bpm = bpm
            engine.start()
        }
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

        if (tapTimes.isNotEmpty() && currentTime - tapTimes.last() > 2000) {
            tapTimes.clear()
        }

        tapTimes.add(currentTime)

        if (tapTimes.size > 5) tapTimes.removeAt(0)

        if (tapTimes.size >= 2) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until tapTimes.size) {
                intervals.add(tapTimes[i] - tapTimes[i - 1])
            }
            val avg = intervals.average()

            val newBpm = (60000 / avg).toInt().coerceIn(30, 300)
            bpm = newBpm
        }
    }

    fun setSignatureByPosition(position: Int) {
        val selected = timeSignatures[position]
        val parts = selected.split("/")
        val beats = parts[0].toInt()
        val noteValue = parts[1].toInt()

        beatsPerMeasure = beats

        engine.multiplier = if (noteValue == 8) 2.0 else 1.0

        if (engine.isRunning) restart()
    }

}