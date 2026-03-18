package com.eduashi.strings

data class InstrumentTuning(val name: String, val notes: List<Int>)

val guitar6Standard = InstrumentTuning("Гитара 6-стр", listOf(40, 45, 50, 55, 59, 64))
val guitar7Standard = InstrumentTuning("Гитара 7-стр", listOf(35, 40, 45, 50, 55, 59, 64))
val bass4Standard = InstrumentTuning("Бас 4-стр", listOf(28, 33, 38, 43))
val bass5Standard = InstrumentTuning("Бас 5-стр", listOf(23, 28, 33, 38, 43))
val DropD = InstrumentTuning("Drop D", listOf(38, 45, 50, 55, 59, 64))
val DropC = InstrumentTuning("Drop C", listOf(36, 43, 48, 53, 57, 62))
val DropA = InstrumentTuning("Drop A", listOf(33, 40, 45, 50, 54, 59, 64))
val DropG = InstrumentTuning("Drop G", listOf(31, 38, 43, 48, 52, 57, 62))