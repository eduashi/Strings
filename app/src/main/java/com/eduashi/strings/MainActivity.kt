package com.eduashi.strings

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    // UI элементы
    private lateinit var noteText: TextView
    private lateinit var statusText: TextView
    private lateinit var rootLayout: View
    private lateinit var tunerScale: TunerScaleView
    private lateinit var metronomeController: MetronomeController

    // Состояние
    private var analyzer: AudioAnalyzer? = null
    private var currentTuning: InstrumentTuning? = null
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val pitchHistory = mutableListOf<Float>()
    private val historySize = 15
    private var hasVibrated = false

    // Для авто-изменения BPM
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isAutoChanging = false
    private var autoStepRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupMetronome()
        setupNavigation()
        checkAudioPermission()
    }

    private fun initViews() {
        noteText = findViewById(R.id.noteText)
        statusText = findViewById(R.id.statusText)
        rootLayout = findViewById(R.id.rootLayout)
        tunerScale = findViewById(R.id.tunerScale)

        findViewById<Chip>(R.id.chipPresets).setOnClickListener { showPresetsDialog() }

        findViewById<ChipGroup>(R.id.modeChipGroup).setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(R.id.chipChromatic)) resetToChromatic()
        }
    }

    private fun resetToChromatic() {
        pitchHistory.clear()
        currentTuning = null
        statusText.text = "Хроматический режим"
        rootLayout.animateBackgroundColor(MaterialColors.getColor(rootLayout, com.google.android.material.R.attr.colorSurface))
    }

    private fun setupMetronome() {
        metronomeController = MetronomeController(
            onBpmChanged = { newBpm ->
                val tv = findViewById<TextView>(R.id.bpmText)
                tv?.text = newBpm.toString()
                tv?.bounce() // Используем расширение из ViewExtensions
            },
            onTick = { beat ->
                runOnUiThread {
                    val scale = findViewById<TunerScaleView>(R.id.metronomeScale)
                    scale?.animateTick(if (beat % 2 != 0) 100f else -100f)
                    scale?.flashAccent(beat == 1)
                }
            }
        )
        setupMetronomeButtons()
    }

    private fun setupNavigation() {
        val tunerGroup = findViewById<View>(R.id.tunerGroup)
        val metronomeGroup = findViewById<View>(R.id.metronomeLayout)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNav.setOnItemSelectedListener { item ->
            rootLayout.animateBackgroundColor(MaterialColors.getColor(rootLayout, com.google.android.material.R.attr.colorSurface))
            noteText.text = ""

            when (item.itemId) {
                R.id.nav_tuner -> {
                    switchLayout(tunerGroup, metronomeGroup)
                    metronomeController.stop()
                    updateMetronomeUI()
                    startTuning()
                    true
                }
                R.id.nav_metronome -> {
                    switchLayout(metronomeGroup, tunerGroup)
                    analyzer?.stop()
                    true
                }
                else -> false
            }
        }
    }

    private fun switchLayout(toShow: View, toHide: View) {
        if (toShow.isVisible) return
        toShow.apply { visibility = View.VISIBLE; alpha = 0f; animate().alpha(1f).setDuration(300) }
        toHide.animate().alpha(0f).setDuration(300).withEndAction { toHide.visibility = View.GONE }
    }

    private fun updateMetronomeUI() {
        val btn = findViewById<MaterialButton>(R.id.btnStartStop) ?: return
        val isRunning = metronomeController.isRunning()

        if (isRunning) btn.startPulse() else btn.stopPulse()

        btn.text = getString(if (isRunning) R.string.btn_stop else R.string.btn_start)
        btn.setIconResource(if (isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMetronomeButtons() {
        findViewById<View>(R.id.btnPlus).apply {
            setOnClickListener { if (!isAutoChanging) metronomeController.bpm += 1 }
            setOnTouchListener { _, e -> handleAuto(e, 10); false }
        }
        findViewById<View>(R.id.btnMinus).apply {
            setOnClickListener { if (!isAutoChanging) metronomeController.bpm -= 1 }
            setOnTouchListener { _, e -> handleAuto(e, -10); false }
        }
        findViewById<View>(R.id.btnStartStop).setOnClickListener {
            metronomeController.toggle(); updateMetronomeUI()
        }
        findViewById<View>(R.id.btnTap).setOnClickListener { metronomeController.handleTap() }
        findViewById<View>(R.id.btnTimeSignature).setOnClickListener { showTimeSignatureDialog() }
    }

    private fun handleAuto(event: android.view.MotionEvent, step: Int) {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                isAutoChanging = false
                autoStepRunnable = object : Runnable {
                    override fun run() {
                        metronomeController.bpm += step
                        findViewById<View>(R.id.bpmText)?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        handler.postDelayed(this, 150)
                    }
                }
                handler.postDelayed(autoStepRunnable!!, 500)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                autoStepRunnable?.let { handler.removeCallbacks(it) }
                autoStepRunnable = null
                isAutoChanging = false
            }
        }
    }

    // --- Логика тюнера (упрощенная за счет выноса анимаций) ---
    private fun processFrequencyWithSmoothing(hz: Float) {
        if (hz <= 25f || hz > 1000f) return
        pitchHistory.add(hz); if (pitchHistory.size > historySize) pitchHistory.removeAt(0)
        val avgHz = pitchHistory.average().toFloat()
        val n = (12 * log2(avgHz / 440.0) + 69).toFloat()

        if (currentTuning == null) {
            val idx = n.roundToInt(); val cents = (n - idx) * 100
            tunerScale.setCents(cents)
            updateUI(cents, avgHz, idx)
        } else {
            val target = currentTuning!!.notes.minByOrNull { abs(it - n) } ?: n.roundToInt()
            if (abs(n - target) < 1.5f) {
                val cents = (n - target) * 100
                tunerScale.setCents(cents)
                updatePresetUI(cents, avgHz, target)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(cents: Float, hz: Float, idx: Int) {
        noteText.text = "${noteNames[((idx % 12) + 12) % 12]}${idx / 12 - 1}"
        if (abs(cents) < 8) {
            if (!hasVibrated) { triggerVibe(); hasVibrated = true }
            rootLayout.animateBackgroundColor(resources.getColor(android.R.color.holo_green_dark, null))
            noteText.setTextColor(android.graphics.Color.WHITE)
            statusText.text = "PERFECT"
        } else {
            hasVibrated = false
            rootLayout.animateBackgroundColor(MaterialColors.getColor(rootLayout, com.google.android.material.R.attr.colorSurface))
            noteText.setTextColor(MaterialColors.getColor(noteText, com.google.android.material.R.attr.colorPrimary))
            statusText.text = "%.1f Hz".format(hz)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePresetUI(cents: Float, hz: Float, target: Int) {
        val tuning = currentTuning ?: return
        val strIdx = tuning.notes.indexOf(target)
        val strNum = if (strIdx != -1) tuning.notes.size - strIdx else "?"
        noteText.text = "${noteNames[((target % 12) + 12) % 12]}${target / 12 - 1}"

        if (abs(cents) < 8) {
            if (!hasVibrated) { triggerVibe(); hasVibrated = true }
            rootLayout.animateBackgroundColor(resources.getColor(android.R.color.holo_green_dark, null))
            statusText.text = "СТРУНА $strNum: ИДЕАЛЬНО"
        } else {
            hasVibrated = false
            rootLayout.animateBackgroundColor(MaterialColors.getColor(rootLayout, com.google.android.material.R.attr.colorSurface))
            statusText.text = "СТРУНА $strNum: ${if (cents > 0) "ОСЛАБЬ ↓" else "НАТЯНИ ↑"}\n%.1f Hz".format(hz)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showPresetsDialog() {
        val names = arrayOf("Гитара 6-стр", "Гитара 7-стр", "Бас 4-стр", "Бас 5-стр", "Drop D", "Drop C", "Drop A", "Drop G")
        MaterialAlertDialogBuilder(this).setTitle("Выберите строй").setItems(names) { _, i ->
            currentTuning = when(i) { 0->guitar6Standard; 1->guitar7Standard; 2->bass4Standard; 3->bass5Standard; 4->DropD; 5->DropC; 6->DropA; else->DropG }
            findViewById<Chip>(R.id.chipPresets).isChecked = true
            statusText.text = "Настройка: ${currentTuning?.name}"
        }.show()
    }

    private fun showTimeSignatureDialog() {
        val sigs = metronomeController.timeSignatures
        MaterialAlertDialogBuilder(this).setTitle("Размер").setItems(sigs) { _, i ->
            metronomeController.setSignatureByPosition(i)
            findViewById<MaterialButton>(R.id.btnTimeSignature)?.text = sigs[i]
        }.show()
    }

    private fun triggerVibe() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }

        if (vibrator.hasVibrator()) {
            when {
                // Для Android 10 (API 29) и выше используем четкие системные пресеты
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                }
                // Для Android 8.0 (API 26) до Android 9 используем создание эффекта вручную
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                }
                // Для совсем старых устройств
                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        }
    }

    private fun checkAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else startTuning()
    }

    private fun startTuning() {
        analyzer?.stop()
        analyzer = AudioAnalyzer { hz -> runOnUiThread { processFrequencyWithSmoothing(hz) } }
        analyzer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::metronomeController.isInitialized) metronomeController.stop()
        analyzer?.stop()
    }
}