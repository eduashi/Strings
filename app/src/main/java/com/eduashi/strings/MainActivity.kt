package com.eduashi.strings

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.eduashi.strings.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CENTS_THRESHOLD = 8.0f
        private const val ANIMATION_DURATION = 300L
        private const val PITCH_HISTORY_SIZE = 15
        private const val COLOR_PERFECT = "#2E7D32" // Dark Green
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var metronomeController: MetronomeController

    private var analyzer: AudioAnalyzer? = null
    private var currentTuning: InstrumentTuning? = null
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val pitchHistory = mutableListOf<Float>()
    private var hasVibrated = false

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoChanging = false
    private var autoStepRunnable: Runnable? = null
    private var isTunerActive = false
    private var backgroundAnimator: ValueAnimator? = null
    private var totalTicks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)

        binding = ActivityMainBinding.inflate(layoutInflater)
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupUI()
        setupMetronome()
        setupNavigation()
        setupSettings()
        setupSensitivitySlider()
        checkAudioPermission()

        binding.root.post { restoreScreenState() }
    }

    // --- Инициализация систем ---

    private fun setupUI() {
        binding.chipPresets.setOnClickListener { showPresetsDialog() }
        binding.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(R.id.chipChromatic)) resetToChromatic()
        }
    }

    private fun resetToChromatic() {
        pitchHistory.clear()
        currentTuning = null
        binding.statusText.text = getString(R.string.mode_chromatic)
        updateBackgroundStyled(getThemeColor(com.google.android.material.R.attr.colorSurface), false)
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // Если уходим с тюнера — сбрасываем всё ПЕРЕД переключением
            if (item.itemId != R.id.nav_tuner) resetTunerVisuals()

            when (item.itemId) {
                R.id.nav_tuner -> {
                    isTunerActive = true
                    switchLayout(binding.tunerGroup, listOf(binding.metronomeLayout, binding.settingsLayout))
                    startTuning()
                }
                R.id.nav_metronome -> {
                    switchLayout(binding.metronomeLayout, listOf(binding.tunerGroup, binding.settingsLayout))
                    updateMetronomeUI()
                }
                R.id.nav_settings -> {
                    metronomeController.stop()
                    switchLayout(binding.settingsLayout, listOf(binding.tunerGroup, binding.metronomeLayout))
                    updateMetronomeUI()
                }
            }
            true
        }
    }

    // --- Логика Тюнера ---

    private fun processFrequencyWithSmoothing(hz: Float) {
        // Если анализатор вернул -1, значит это шум.
        if (hz <= 0f) return
        if (hz <= 25f || hz > 1000f) return
        pitchHistory.add(hz)
        if (pitchHistory.size > PITCH_HISTORY_SIZE) pitchHistory.removeAt(0)

        val avgHz = pitchHistory.average().toFloat()
        val n = (12 * log2(avgHz / 440.0) + 69).toFloat()

        if (currentTuning == null) {
            val idx = n.roundToInt()
            val cents = (n - idx) * 100
            binding.tunerScale.setCents(cents)
            updateUI(cents, avgHz, idx)
        } else {
            val target = currentTuning!!.notes.minByOrNull { abs(it - n) } ?: n.roundToInt()
            if (abs(n - target) < 2.0f) {
                val cents = (n - target) * 100
                binding.tunerScale.setCents(cents)
                updatePresetUI(cents, avgHz, target)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(cents: Float, hz: Float, idx: Int) {
        if (!isTunerActive) return
        val noteName = "${noteNames[((idx % 12) + 12) % 12]}${idx / 12 - 1}"
        val isPerfect = abs(cents) < CENTS_THRESHOLD

        applyTuningVisuals(isPerfect, noteName, getString(R.string.pitch_detected, "%.1f".format(hz)))
    }

    @SuppressLint("SetTextI18n")
    private fun updatePresetUI(cents: Float, hz: Float, target: Int) {
        if (!isTunerActive) return
        val tuning = currentTuning ?: return
        val isPerfect = abs(cents) < CENTS_THRESHOLD

        val strIdx = tuning.notes.indexOf(target)
        val strNum = if (strIdx != -1) (tuning.notes.size - strIdx) else 0
        val noteName = "${noteNames[((target % 12) + 12) % 12]}${target / 12 - 1}"

        val status = if (isPerfect) {
            getString(R.string.string_number, strNum) + ": " + getString(R.string.perfect)
        } else {
            val direction = if (cents > 0) getString(R.string.too_high) else getString(R.string.too_low)
            "${getString(R.string.string_number, strNum)}: $direction\n%.1f Hz".format(hz)
        }

        applyTuningVisuals(isPerfect, noteName, status)
    }

    private fun applyTuningVisuals(isPerfect: Boolean, note: String, status: String) {
        binding.noteText.text = note
        binding.statusText.text = status

        if (isPerfect) {
            if (!hasVibrated) { triggerVibe(); hasVibrated = true }
            updateBackgroundStyled(COLOR_PERFECT.toColorInt(), true)
        } else {
            hasVibrated = false
            updateBackgroundStyled(getThemeColor(com.google.android.material.R.attr.colorSurface), false)
        }
    }

    // --- Анимации и Визуализация ---

    private fun updateBackgroundStyled(targetColor: Int, isPerfect: Boolean) {
        backgroundAnimator?.cancel()
        val colorFrom = (binding.rootLayout.background as? ColorDrawable)?.color ?: Color.TRANSPARENT

        backgroundAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, targetColor).apply {
            duration = ANIMATION_DURATION
            addUpdateListener {
                val color = it.animatedValue as Int
                binding.rootLayout.setBackgroundColor(color)
                val textColor = if (isPerfect) Color.WHITE else getThemeColor(com.google.android.material.R.attr.colorPrimary)
                binding.noteText.setTextColor(textColor)
            }
            start()
        }
    }

    private fun resetTunerVisuals() {
        isTunerActive = false
        analyzer?.stop()
        backgroundAnimator?.cancel()
        binding.rootLayout.clearAnimation()

        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.rootLayout.setBackgroundColor(surfaceColor)
        binding.noteText.setTextColor(getThemeColor(com.google.android.material.R.attr.colorPrimary))
        binding.statusText.text = ""
    }

    private fun switchLayout(toShow: View, toHide: List<View>) {
        if (toShow.isVisible) return
        toHide.forEach { view ->
            if (view.isVisible) {
                view.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(150).withEndAction { view.isVisible = false }
            }
        }
        toShow.apply {
            alpha = 0f; scaleX = 0.95f; scaleY = 0.95f; isVisible = true
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setStartDelay(100).start()
        }
    }

    // --- Вспомогательные функции ---

    private fun getThemeColor(attr: Int): Int = MaterialColors.getColor(binding.root, attr)

    private fun triggerVibe() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(50)
            }
        }
    }

    private fun startTuning() { // или как-то иначе названный метод запуска
        analyzer?.stop()

        analyzer = AudioAnalyzer { hz ->
            runOnUiThread { processFrequencyWithSmoothing(hz) }
        }
        // Мы берем текущее значение из слайдера и отдаем его анализатору
        analyzer?.amplitudeThreshold = binding.sliderSensitivity.value.toInt()

        analyzer?.start()
    }

    // --- Остальной код (Metronome, Settings, Dialogs) остается без изменений ---
    // (Я опустил их для краткости, они у тебя уже в порядке)

    private fun setupMetronome() {
        metronomeController = MetronomeController(
            onBpmChanged = { newBpm ->
                binding.bpmText.text = newBpm.toString()
                binding.bpmText.bounce()
            },
            onTick = { beat ->
                runOnUiThread {
                    totalTicks++
                    val direction = if (totalTicks % 2 != 0) 100f else -100f
                    binding.metronomeScale.animateTick(direction)
                    binding.metronomeScale.flashAccent(beat == 1)
                }
            }
        )
        setupMetronomeButtons()
    }

    private fun updateMetronomeUI() {
        val isRunning = metronomeController.isRunning()
        with(binding.btnStartStop) {
            if (isRunning) startPulse() else stopPulse()
            text = getString(if (isRunning) R.string.btn_stop else R.string.btn_start)
            setIconResource(if (isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMetronomeButtons() {
        binding.btnPlus.apply {
            setOnClickListener { if (!isAutoChanging) metronomeController.bpm += 1 }
            setOnTouchListener { _, e -> handleAuto(e, 1); false }
        }
        binding.btnMinus.apply {
            setOnClickListener { if (!isAutoChanging) metronomeController.bpm -= 1 }
            setOnTouchListener { _, e -> handleAuto(e, -1); false }
        }

        binding.btnStartStop.setOnClickListener {
            // Если метроном НЕ запущен, значит мы его сейчас включим
            if (!metronomeController.isRunning()) {
                totalTicks = 0 // Сбрасываем, чтобы маятник всегда начинал с одной стороны
            }

            metronomeController.toggle()
            updateMetronomeUI()
        }

        binding.btnTap.setOnClickListener { metronomeController.handleTap() }
        binding.btnTimeSignature.setOnClickListener { showTimeSignatureDialog() }
    }

    private fun handleAuto(event: MotionEvent, step: Int) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                autoStepRunnable = object : Runnable {
                    override fun run() {
                        isAutoChanging = true
                        metronomeController.bpm += (step * 10)
                        binding.bpmText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        handler.postDelayed(this, 300)
                    }
                }
                handler.postDelayed(autoStepRunnable!!, 500)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                autoStepRunnable?.let { handler.removeCallbacks(it) }
                autoStepRunnable = null
                handler.postDelayed({ isAutoChanging = false }, 50)
            }
        }
    }

    private fun showPresetsDialog() {
        val names = arrayOf(
            getString(R.string.tuning_guitar_6), getString(R.string.tuning_guitar_7),
            getString(R.string.tuning_bass_4), getString(R.string.tuning_bass_5),
            getString(R.string.tuning_drop_d), getString(R.string.tuning_drop_c)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.mode_presets))
            .setItems(names) { _, i ->
                currentTuning = when(i) {
                    0 -> guitar6Standard; 1 -> guitar7Standard; 2 -> bass4Standard;
                    3 -> bass5Standard; 4 -> DropD; else -> DropC
                }
                binding.chipPresets.isChecked = true
                binding.statusText.text = currentTuning?.name
            }.show()
    }

    private fun showTimeSignatureDialog() {
        val sigs = metronomeController.timeSignatures
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.title_select_signature))
            .setItems(sigs) { _, i ->
                metronomeController.setSignatureByPosition(i)
                binding.btnTimeSignature.text = sigs[i]
            }.show()
    }

    private fun setupSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.themeToggleGroup.check(R.id.btnLightTheme)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.themeToggleGroup.check(R.id.btnDarkTheme)
            else -> binding.themeToggleGroup.check(R.id.btnSystemTheme)
        }

        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.btnLightTheme -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btnDarkTheme -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                prefs.edit { putInt("theme_mode", mode) }
            }
        }
        binding.btnAbout.setOnClickListener { showAboutDialog() }
    }

    private fun showAboutDialog() {
        val versionName = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName
        } catch (e: Exception) { "1.0" }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_message, versionName))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun restoreScreenState() {
        val selectedId = binding.bottomNavigation.selectedItemId

        // Сбрасываем видимость и анимации всех слоев
        listOf(binding.tunerGroup, binding.metronomeLayout, binding.settingsLayout).forEach {
            it.isVisible = false
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
        }

        when (selectedId) {
            R.id.nav_tuner -> {
                binding.tunerGroup.isVisible = true
                startTuning()
            }
            R.id.nav_metronome -> {
                binding.metronomeLayout.isVisible = true
                // ГАРАНТИРУЕМ, что тюнер выключен
                stopTuningAndResetUI()
            }
            R.id.nav_settings -> {
                binding.settingsLayout.isVisible = true
                // ГАРАНТИРУЕМ, что тюнер выключен
                stopTuningAndResetUI()
            }
        }
    }

    // Вынесем сброс в отдельный метод, чтобы не дублировать код
    private fun stopTuningAndResetUI() {
        isTunerActive = false
        analyzer?.stop()
        backgroundAnimator?.cancel()

        // Возвращаем фону стандартный цвет поверхности,
        // чтобы "зелень" не залипла при смене темы
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.rootLayout.setBackgroundColor(surfaceColor)
    }

    private fun setupSensitivitySlider() {
        val prefs = getSharedPreferences("tuner_settings", Context.MODE_PRIVATE)
        // 1. Загружаем сохраненное значение (по умолчанию 150)
        val savedValue = prefs.getFloat("sensitivity", 150f)
        // Устанавливаем начальное состояние
        binding.sliderSensitivity.value = savedValue
        updateSensitivityText(savedValue.toInt()) // Обновляем заголовок при запуске

        // 2. Сразу передаем его в анализатор (если он уже создан)
        analyzer?.amplitudeThreshold = savedValue.toInt()

        // 3. Слушатель изменений
        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            // Обновляем порог в реальном времени
            analyzer?.amplitudeThreshold = value.toInt()

            updateSensitivityText(value.toInt())
            // Сохраняем в память
            prefs.edit().putFloat("sensitivity", value).apply()

            // Маленький виброотклик для приятного ощущения
            binding.sliderSensitivity.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    private fun updateSensitivityText(value: Int) {
        val baseText = getString(R.string.tuner_sensitivity)
        binding.tvSensitivityTitle.text = "$baseText ($value)"
    }

    private fun checkAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Запускаем ТОЛЬКО если выбрана вкладка тюнера
            if (binding.bottomNavigation.selectedItemId == R.id.nav_tuner) {
                startTuning()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::metronomeController.isInitialized) metronomeController.stop()
        analyzer?.stop()
    }
}