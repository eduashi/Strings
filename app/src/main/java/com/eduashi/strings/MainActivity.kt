package com.eduashi.strings

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import com.google.android.material.color.MaterialColors
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.TooltipCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.eduashi.strings.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
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
    private lateinit var toneController: ToneController
    private var isGeneratorPlaying = false

    private var analyzer: AudioAnalyzer? = null
    private var currentTuning: InstrumentTuning? = null
    private var silenceCounter = 0
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val pitchHistory = mutableListOf<Float>()
    private val GEN_MIN_FREQ = 20.0
    private val GEN_MAX_FREQ = 5000.0
    private var hasVibrated = false

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoChanging = false
    private var autoStepRunnable: Runnable? = null
    private var isTunerActive = false
    private var isPresetMode = false
    private val freqBuffer = mutableListOf<Float>()
    private var backgroundAnimator: ValueAnimator? = null
    private var totalTicks = 0
    private var isMicTesting = false
    private lateinit var sharedPrefs: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeMode = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val isDynamicEnabled = sharedPrefs.getBoolean("dynamic_colors", false)

        AppCompatDelegate.setDefaultNightMode(themeMode)
        if (isDynamicEnabled) DynamicColors.applyToActivityIfAvailable(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMetronome()
        analyzer = AudioAnalyzer(
            onFrequencyDetected = { hz -> runOnUiThread { processFrequencyWithSmoothing(hz) } },
            onVolumeChanged = { rms ->
                if (isMicTesting) {
                    runOnUiThread {
                        val threshold = binding.sliderSensitivity.value
                        binding.micLevelIndicator.progress = rms.toInt().coerceIn(0, 2000)
                        val colorAttr = if (rms >= threshold) com.google.android.material.R.attr.colorPrimary
                        else com.google.android.material.R.attr.colorOutline
                        binding.micLevelIndicator.setIndicatorColor(MaterialColors.getColor(binding.micLevelIndicator, colorAttr))
                    }
                }
            }
        )
        toneController = ToneController()
        setupNoteSelectors()
        setupUI()
        setupNavigation()
        setupSensitivitySlider()
        setupGeneratorUI()
        checkAudioPermission()
        setupSettings()
        restoreScreenState()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.bottomNavigation.selectedItemId != R.id.nav_tuner) {
                binding.bottomNavigation.selectedItemId = R.id.nav_tuner
            } else finish()
        }
    }

    // --- Жизненный цикл для Android 11-16 ---

    override fun onResume() {
        super.onResume()
        if (binding.bottomNavigation.selectedItemId == R.id.nav_tuner) {
            startTuning()
        }
    }

    override fun onPause() {
        super.onPause()
        // Немедленно освобождаем микрофон и останавливаем анимации
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopTuningAndResetUI()

        if (isMicTesting) {
            resetSettingsUI()
        }

        if (isGeneratorPlaying) {
            toneController.stop()
            isGeneratorPlaying = false
            binding.btnGeneratorStart.text = getString(R.string.btn_start)
            binding.btnGeneratorStart.setIconResource(android.R.drawable.ic_media_play)
        }
    }

    // --- Навигация и UI ---

    private fun setupUI() {
        binding.btnModeChromatic.setOnClickListener {
            resetToChromatic()
            updateModeButtonsUI()
        }

        binding.btnModePresets.setOnClickListener {
            binding.btnModeChromatic.updateStyle(R.style.Widget_Strings_ModeButton_Unselected)
            binding.btnModePresets.updateStyle(R.style.Widget_Strings_ModeButton_Selected)
            showPresetsDialog()
        }
    }

    private fun resetToChromatic() {
        pitchHistory.clear()
        currentTuning = null
        binding.statusText.text = getString(R.string.mode_chromatic)
        updateBackgroundStyled(getThemeColor(com.google.android.material.R.attr.colorSurface), false)
    }

    private fun updateModeButtonsUI() {
        val isPresetActive = currentTuning != null
        if (isPresetActive) {
            binding.btnModeChromatic.updateStyle(R.style.Widget_Strings_ModeButton_Unselected)
            binding.btnModePresets.updateStyle(R.style.Widget_Strings_ModeButton_Selected)
        } else {
            binding.btnModeChromatic.updateStyle(R.style.Widget_Strings_ModeButton_Selected)
            binding.btnModePresets.updateStyle(R.style.Widget_Strings_ModeButton_Unselected)
        }
    }

    @SuppressLint("ResourceType")
    private fun Button.updateStyle(@StyleRes styleId: Int) {
        this.setTextAppearance(styleId)
        val attrs = intArrayOf(
            com.google.android.material.R.attr.backgroundTint,
            com.google.android.material.R.attr.cornerRadius
        )
        val typedArray = context.obtainStyledAttributes(styleId, attrs)
        val colorStateList = typedArray.getColorStateList(0)
        val radius = typedArray.getDimensionPixelSize(1, 0)
        typedArray.recycle()

        if (this is com.google.android.material.button.MaterialButton) {
            this.backgroundTintList = colorStateList
            this.cornerRadius = radius
            this.insetTop = 0
            this.insetBottom = 0
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            sharedPrefs.edit { putInt("last_tab", item.itemId) }
            handleNavigation(item.itemId)
            true
        }
    }

    private fun handleNavigation(itemId: Int) {
        if (!::metronomeController.isInitialized || !::toneController.isInitialized) return

        // Сброс всех активных процессов при смене вкладки
        if (itemId != R.id.nav_tuner) stopTuningAndResetUI()
        if (itemId != R.id.nav_metronome) metronomeController.stop()
        if (itemId != R.id.nav_settings) resetSettingsUI()

        if (itemId != R.id.nav_generator && isGeneratorPlaying) {
            toneController.stop()
            isGeneratorPlaying = false
            binding.btnGeneratorStart.text = getString(R.string.btn_start)
            binding.btnGeneratorStart.setIconResource(android.R.drawable.ic_media_play)
        }

        when (itemId) {
            R.id.nav_tuner -> {
                switchLayout(binding.tunerGroup, listOf(binding.metronomeLayout, binding.generatorLayout, binding.settingsLayout))
                startTuning()
                updateModeButtonsUI()
                binding.tunerScale.isMetronomeMode = false
            }
            R.id.nav_metronome -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                switchLayout(binding.metronomeLayout, listOf(binding.tunerGroup, binding.generatorLayout, binding.settingsLayout))
                updateMetronomeUI()
                binding.metronomeScale.isMetronomeMode = true
            }
            R.id.nav_generator -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                switchLayout(binding.generatorLayout, listOf(binding.tunerGroup, binding.metronomeLayout, binding.settingsLayout))
            }
            R.id.nav_settings -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                switchLayout(binding.settingsLayout, listOf(binding.tunerGroup, binding.metronomeLayout, binding.generatorLayout))
            }
        }
    }

    // --- Логика Тюнера ---
    private fun processFrequencyWithSmoothing(hz: Float) {
        if (hz <= 20f) {
            silenceCounter++
            // Сбрасываем визуал только если тишина реальная (длится долго)
            if (silenceCounter > 10) {
                resetTunerVisuals()
                freqBuffer.clear()
            }
            return
        }

        // Звук обнаружен — сбрасываем счетчик тишины
        silenceCounter = 0

        // Сглаживание
        freqBuffer.add(hz)
        if (freqBuffer.size > PITCH_HISTORY_SIZE) freqBuffer.removeAt(0)
        val smoothHz = freqBuffer.sorted()[freqBuffer.size / 2]

        // Синхронизируем крутилки генератора с входящим звуком
        updateNoteSelectors(smoothHz.toDouble())

        val n = 12.0 * log2(smoothHz.toDouble() / 440.0) + 69.0

        if (currentTuning != null) {
            val targetDouble = currentTuning!!.notes
                .map { it.toDouble() }
                .minByOrNull { abs(it - n) } ?: n
            val targetMidi = targetDouble.roundToInt()
            val cents = ((n - targetDouble) * 100.0).toFloat()
            binding.tunerScale.setCents(cents)
            updatePresetUI(cents, smoothHz, targetMidi)
        } else {
            val targetMidi = n.roundToInt()
            val cents = ((n - targetMidi.toDouble()) * 100.0).toFloat()
            binding.tunerScale.setCents(cents)
            updateUI(cents, smoothHz, targetMidi)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(cents: Float, hz: Float, idx: Int) {
        if (!isTunerActive) return

        // Если частота -1 (тишина), сбрасываем визуал
        if (hz < 0) {
            resetTunerVisuals()
            return
        }

        // Рассчитываем имя ноты (например, E2, A4)
        val noteName = "${MusicUtils.noteNames[((idx % 12) + 12) % 12]}${idx / 12 - 1}"
        val isPerfect = abs(cents) < CENTS_THRESHOLD

        // Формируем строку статуса: "Частота: 440.0 Гц"
        val status = getString(R.string.pitch_detected, String.format(Locale.US, "%.1f", hz))

        binding.tunerScale.setCents(cents)
        applyTuningVisuals(isPerfect, noteName, status)
    }

    @SuppressLint("SetTextI18n")
    private fun updatePresetUI(cents: Float, hz: Float, target: Int) {
        if (!isTunerActive) return
        val tuning = currentTuning ?: return

        if (hz < 0) {
            resetTunerVisuals()
            return
        }

        val isPerfect = abs(cents) < CENTS_THRESHOLD
        val strIdx = tuning.notes.indexOf(target)
        // Номер струны (от тонкой к толстой, напр. 1-я, 2-я...)
        val strNum = if (strIdx != -1) (tuning.notes.size - strIdx) else 0
        val noteName = "${MusicUtils.noteNames[((target % 12) + 12) % 12]}${target / 12 - 1}"

        val status = if (isPerfect) {
            "${getString(R.string.string_number, strNum)}: ${getString(R.string.perfect)}"
        } else {
            val direction = if (cents > 0) getString(R.string.too_high) else getString(R.string.too_low)
            "${getString(R.string.string_number, strNum)}: $direction\n${String.format(Locale.US, "%.1f Hz", hz)}"
        }

        binding.tunerScale.setCents(cents)
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

    private fun updateBackgroundStyled(targetColor: Int, isPerfect: Boolean) {
        backgroundAnimator?.cancel()
        val colorFrom = (binding.rootLayout.background as? ColorDrawable)?.color ?: Color.TRANSPARENT
        backgroundAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, targetColor).apply {
            duration = ANIMATION_DURATION
            addUpdateListener {
                val color = it.animatedValue as Int
                binding.rootLayout.setBackgroundColor(color)

                if (isPerfect) {
                    binding.noteText.setTextColor(Color.WHITE)
                    binding.statusText.setTextColor(Color.WHITE)
                } else {
                    binding.noteText.setTextColor(getThemeColor(com.google.android.material.R.attr.colorPrimary))
                    binding.statusText.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOutline))
                }
            }
            start()
        }
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

    private fun getThemeColor(attr: Int): Int = MaterialColors.getColor(binding.root, attr)

    private fun triggerVibe() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }

    private fun startTuning() {
        if (isFinishing || isDestroyed) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isTunerActive = true
            analyzer?.start()
            binding.statusText.text = getString(R.string.status_listening)
        } else {
            isTunerActive = false
            binding.statusText.text = getString(R.string.tuner_mic_off)
        }
    }

    // --- Метроном ---
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
                    flashEntireScreen(beat == 1)
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
            if (!metronomeController.isRunning()) totalTicks = 0
            metronomeController.toggle()
            updateMetronomeUI()
        }
        binding.btnTap.setOnClickListener { metronomeController.handleTap() }
        binding.btnTimeSignature.setOnClickListener { showTimeSignatureDialog() }
    }

    private fun flashEntireScreen(isAccent: Boolean) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val baseThemeColor = typedValue.data

        val startColor = if (isAccent) {
            if (isDark) Color.argb(180, 255, 255, 255)
            else (baseThemeColor and 0x00FFFFFF) or (255 shl 24)
        } else {
            (baseThemeColor and 0x00FFFFFF) or (40 shl 24)
        }

        val colorAnim = ValueAnimator.ofArgb(startColor, Color.TRANSPARENT)
        colorAnim.duration = if (isAccent) 300L else 200L
        colorAnim.addUpdateListener { animator -> binding.rootLayout.setBackgroundColor(animator.animatedValue as Int) }
        colorAnim.start()
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

    // --- Диалоги ---

    private fun showPresetsDialog() {
        val presetNames = Tunings.all.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.mode_presets))
            .setItems(presetNames) { _, which ->
                val selectedTuning = Tunings.all[which]

                currentTuning = selectedTuning
                isPresetMode = true
                binding.statusText.text = currentTuning?.name
                updateModeButtonsUI()
            }
            .setOnCancelListener { updateModeButtonsUI() }
            .show()
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

    // --- Генератор Тона ---
    private fun freqToSliderValue(freq: Double): Float {
        val clamped = freq.coerceIn(GEN_MIN_FREQ, GEN_MAX_FREQ)
        return (ln(clamped / GEN_MIN_FREQ) / ln(GEN_MAX_FREQ / GEN_MIN_FREQ)).toFloat()
    }

    private fun sliderValueToFreq(value: Float): Double {
        return GEN_MIN_FREQ * (GEN_MAX_FREQ / GEN_MIN_FREQ).pow(value.toDouble())
    }

    @SuppressLint("SetTextI18n")
    private fun setupGeneratorUI() {
        binding.chipWaveSine.isChecked = true

        // Настройка слайдера: ВСЕГДА 0.0 - 1.0
        binding.sliderGenerator.valueFrom = 0f
        binding.sliderGenerator.valueTo = 1f
        if (binding.sliderGenerator.value !in 0f..1f) {
            binding.sliderGenerator.value = freqToSliderValue(440.0)
        }

        binding.sliderGenerator.addOnChangeListener { slider, value, fromUser ->
            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: переводим 0..1 в Герцы
            val freq = sliderValueToFreq(value)
            toneController.setFrequency(freq)

            binding.tvGeneratorFreq.text = String.format(Locale.US, "%.1f Hz", freq)
            binding.tvGeneratorNote.text = MusicUtils.frequencyToNoteName(freq)

            if (fromUser) {
                updateNoteSelectorsUI(freq)
                if ((value * 100).toInt() % 5 == 0) {
                    slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }

        binding.tvGeneratorFreq.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            // Передаем логику того, что сделать с числом после ввода
            showFrequencyInputDialog { newFreq ->
                // Ограничиваем ввод диапазоном 20-5000 Гц
                val clampedFreq = newFreq.coerceIn(GEN_MIN_FREQ, GEN_MAX_FREQ)

                // Устанавливаем положение слайдера (это само обновит текст и звук)
                binding.sliderGenerator.value = freqToSliderValue(clampedFreq)

                // Явно обновляем выпадающие списки ноты и октавы
                updateNoteSelectorsUI(clampedFreq)
            }
        }

        binding.btnGeneratorStart.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val currentFreq = sliderValueToFreq(binding.sliderGenerator.value)
            if (isGeneratorPlaying) {
                toneController.stop()
                binding.btnGeneratorStart.text = getString(R.string.btn_start)
                binding.btnGeneratorStart.setIconResource(android.R.drawable.ic_media_play)
            } else {
                toneController.start(currentFreq)
                binding.btnGeneratorStart.text = getString(R.string.btn_stop)
                binding.btnGeneratorStart.setIconResource(android.R.drawable.ic_media_pause)
            }
            isGeneratorPlaying = !isGeneratorPlaying
        }

        binding.chipWaveSine.setOnClickListener { selectWaveform(ToneController.Waveform.SINE) }
        binding.chipWaveSquare.setOnClickListener { selectWaveform(ToneController.Waveform.SQUARE) }
        binding.chipWaveSaw.setOnClickListener { selectWaveform(ToneController.Waveform.SAW) }
    }

    private fun selectWaveform(waveform: ToneController.Waveform) {
        // Снимаем выделение со всех
        binding.chipWaveSine.isChecked = false
        binding.chipWaveSquare.isChecked = false
        binding.chipWaveSaw.isChecked = false

        // Включаем нужный (принудительно true) и обновляем звук
        when (waveform) {
            ToneController.Waveform.SINE -> {
                binding.chipWaveSine.isChecked = true
                toneController.setWaveform(ToneController.Waveform.SINE)
                showTopNotification(getString(R.string.wave_sine_desc))
            }
            ToneController.Waveform.SQUARE -> {
                binding.chipWaveSquare.isChecked = true
                toneController.setWaveform(ToneController.Waveform.SQUARE)
                showTopNotification(getString(R.string.wave_square_desc))
            }
            ToneController.Waveform.SAW -> {
                binding.chipWaveSaw.isChecked = true
                toneController.setWaveform(ToneController.Waveform.SAW)
                showTopNotification(getString(R.string.wave_saw_desc))
            }
        }
    }

    private fun setupNoteSelectors() {
        val noteAdapter = ArrayAdapter(this, R.layout.list_item_dropdown, MusicUtils.noteNames)
        val octaveAdapter = ArrayAdapter(this, R.layout.list_item_dropdown, MusicUtils.octaves.map { it.toString() })

        binding.autoCompleteNote.setAdapter(noteAdapter)
        binding.autoCompleteOctave.setAdapter(octaveAdapter)

        val updateFromSelectors = {
            val noteName = binding.autoCompleteNote.text.toString()
            val octaveStr = binding.autoCompleteOctave.text.toString()
            val noteIndex = MusicUtils.getNoteIndex(noteName)
            val octave = octaveStr.toIntOrNull() ?: 4

            if (noteIndex != -1) {
                // 1. Получаем реальную частоту выбранной ноты
                val freq = MusicUtils.getFrequency(noteIndex, octave)

                // 2. ИСПРАВЛЕНИЕ: Используем переменную 'freq', которую создали выше
                // Переводим Гц в значение прогресса 0.0 - 1.0 для логарифмического слайдера
                binding.sliderGenerator.value = freqToSliderValue(freq)

                // Звук и текст обновятся автоматически через Listener слайдера
            }
        }

        binding.autoCompleteNote.setOnItemClickListener { _, _, _, _ -> updateFromSelectors() }
        binding.autoCompleteOctave.setOnItemClickListener { _, _, _, _ -> updateFromSelectors() }
    }

    // Вспомогательный метод для чистоты кода
    private fun updateNoteSelectorsUI(freq: Double) {
        val fullNoteName = MusicUtils.frequencyToNoteName(freq)
        val notePart = fullNoteName.filter { it.isLetter() || it == '#' }
        val octavePart = fullNoteName.filter { it.isDigit() || it == '-' }
        binding.autoCompleteNote.setText(notePart, false)
        binding.autoCompleteOctave.setText(octavePart, false)
    }

    // --- Настройки и Системное ---

    private fun setupSettings() {
        val savedMode = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val isDynamicEnabled = sharedPrefs.getBoolean("dynamic_colors", false)

        binding.switchDynamicColor.setOnCheckedChangeListener(null)
        binding.switchDynamicColor.isChecked = isDynamicEnabled
        binding.switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            binding.switchDynamicColor.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            sharedPrefs.edit { putBoolean("dynamic_colors", isChecked) }
            smoothRecreate()
        }

        binding.themeToggleGroup.clearOnButtonCheckedListeners()
        when (savedMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.themeToggleGroup.check(R.id.btnLightTheme)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.themeToggleGroup.check(R.id.btnDarkTheme)
            else -> binding.themeToggleGroup.check(R.id.btnSystemTheme)
        }

        binding.themeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    R.id.btnLightTheme -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btnDarkTheme -> AppCompatDelegate.MODE_NIGHT_YES
                    R.id.btnSystemTheme -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    else -> savedMode
                }
                if (newMode != sharedPrefs.getInt("theme_mode", -100)) {
                    group.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    sharedPrefs.edit { putInt("theme_mode", newMode) }
                    smoothRecreate()
                }
            }
        }

        binding.btnAbout.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val versionName = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName
        } catch (e: Exception) { "1.3" }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(getString(R.string.about_message, versionName))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun restoreScreenState() {
        val lastTab = sharedPrefs.getInt("last_tab", R.id.nav_tuner)
        binding.bottomNavigation.selectedItemId = lastTab
        binding.tunerGroup.isVisible = false
        binding.metronomeLayout.isVisible = false
        binding.settingsLayout.isVisible = false
        handleNavigation(lastTab)
    }

    private fun stopTuningAndResetUI() {
        isTunerActive = false
        analyzer?.stop()
        backgroundAnimator?.end()

        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        binding.rootLayout.setBackgroundColor(surfaceColor)

        // Очистка текста ноты для избежания эффекта "зависания"
        binding.noteText.text = getString(R.string.symbol_dash)
        binding.statusText.text = ""
        binding.tunerScale.setCents(0f)
    }

    private fun resetSettingsUI() {
        if (!isMicTesting) return
        isMicTesting = false

        // Плавно сворачиваем шкалу RMS
        val cardParent = binding.micLevelIndicator.parent as ViewGroup
        TransitionManager.beginDelayedTransition(cardParent, AutoTransition())

        binding.micLevelIndicator.visibility = View.GONE
        binding.micLevelIndicator.progress = 0
        binding.btnTestMic.setIconResource(R.drawable.ic_mic)

        // Если мы не на вкладке тюнера, гасим микрофон полностью
        if (binding.bottomNavigation.selectedItemId != R.id.nav_tuner) {
            analyzer?.stop()
        }
    }

    private fun resetTunerVisuals() {
        // 1. Плавный возврат фона к стандартному
        val surfaceColor = MaterialColors.getColor(binding.rootLayout, com.google.android.material.R.attr.colorSurface)
        binding.rootLayout.animateBackgroundColor(surfaceColor)

        // 2. Сбрасываем цвета текста к стандартным
        val colorPrimary = MaterialColors.getColor(binding.noteText, com.google.android.material.R.attr.colorPrimary)
        val colorOutline = MaterialColors.getColor(binding.statusText, com.google.android.material.R.attr.colorOutline)

        binding.noteText.setTextColor(colorPrimary)
        binding.statusText.setTextColor(colorOutline)

        // 3. Сбрасываем ползунок и тексты
        binding.tunerScale.setCents(0f)
        if (isPresetMode) {
            binding.noteText.text = currentTuning?.name ?: getString(R.string.symbol_dash)
            binding.statusText.text = ""
        } else {
            binding.noteText.text = getString(R.string.symbol_dash)
            binding.statusText.text = getString(R.string.status_listening)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupSensitivitySlider() {
        // 1. Загрузка данных и начальная настройка
        val savedValue = sharedPrefs.getFloat("sensitivity", 150f)
        binding.sliderSensitivity.value = savedValue
        analyzer?.amplitudeThreshold = savedValue.toInt()

        // Подсказка при долгом нажатии
        TooltipCompat.setTooltipText(binding.btnTestMic, getString(R.string.mic_test))

        // Вспомогательная функция для определения текстового порога
        fun getSensitivityLabel(value: Float): String {
            return when {
                value <= 600 -> getString(R.string.sensitivity_high)
                value <= 1300 -> getString(R.string.sensitivity_medium)
                else -> getString(R.string.sensitivity_low)
            }
        }

        // Переменная для отслеживания смены текста (чтобы вибрировать только в моменты переключения)
        var lastLabel = getSensitivityLabel(savedValue)

        // Устанавливаем начальный заголовок и формат "пузырька" над слайдером
        binding.tvSensitivityTitle.text = "${getString(R.string.tuner_sensitivity)} ($lastLabel)"
        binding.sliderSensitivity.setLabelFormatter { value -> getSensitivityLabel(value) }

        // 2. Слушатель изменений слайдера
        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            analyzer?.amplitudeThreshold = intValue

            val currentLabel = getSensitivityLabel(value)

            // Обновляем текст в заголовке
            binding.tvSensitivityTitle.text = "${getString(R.string.tuner_sensitivity)} ($currentLabel)"

            // ВИБРАЦИЯ: Срабатывает только если текст изменился (пересекли границу зон)
            if (currentLabel != lastLabel) {
                binding.sliderSensitivity.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                lastLabel = currentLabel
            }

            sharedPrefs.edit { putFloat("sensitivity", value) }
        }

        // 3. Логика кнопки теста с плавной анимацией раскрытия карточки
        binding.btnTestMic.setOnClickListener {
            isMicTesting = !isMicTesting

            // Анимируем изменения внутри карточки
            val cardParent = binding.micLevelIndicator.parent as ViewGroup
            val transition = AutoTransition().apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
            }
            TransitionManager.beginDelayedTransition(cardParent, transition)

            if (isMicTesting) {
                analyzer?.start()
                binding.micLevelIndicator.visibility = View.VISIBLE
                binding.btnTestMic.setIconResource(R.drawable.ic_close)
            } else {
                // Останавливаем анализатор, если мы не на главном экране тюнера
                if (binding.bottomNavigation.selectedItemId != R.id.nav_tuner) {
                    analyzer?.stop()
                }
                binding.micLevelIndicator.visibility = View.GONE
                binding.micLevelIndicator.progress = 0
                binding.btnTestMic.setIconResource(R.drawable.ic_mic)
            }
        }
    }

    @SuppressLint("UnsafeIntentLaunch")
    private fun smoothRecreate() {
        analyzer?.stop()
        if (::metronomeController.isInitialized) metronomeController.stop()
        val intent = intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        finish()
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION") overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun showFrequencyInputDialog(onFrequencyEntered: (Double) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.default_frequency)
        }

        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(60, 20, 60, 0)
            addView(input, params)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.generator_enter_freq))
            .setView(container)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val text = input.text.toString()
                val freq = text.toDoubleOrNull() ?: 440.0
                onFrequencyEntered(freq) // Вызываем callback
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun updateNoteSelectors(freq: Double) {
        val fullNoteName = MusicUtils.frequencyToNoteName(freq)
        val notePart = fullNoteName.filter { it.isLetter() || it == '#' }
        val octavePart = fullNoteName.filter { it.isDigit() }
        binding.autoCompleteNote.setText(notePart, false)
        binding.autoCompleteOctave.setText(octavePart, false)
    }

    private fun showTopNotification(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        val layout = snackbar.view as ViewGroup
        layout.setBackgroundColor(Color.TRANSPARENT)
        layout.elevation = 0f
        layout.findViewById<View>(com.google.android.material.R.id.snackbar_text)?.parent?.let { (it as View).setBackgroundColor(Color.TRANSPARENT) }
        val snackText = layout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackText.visibility = View.INVISIBLE

        val customView = TextView(this).apply {
            text = message
            // Цвет текста (динамический)
            val textColor = getThemeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
            setTextColor(textColor)

            textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 16f

            // Получаем drawable и красим его в цвет контейнера темы (динамический)
            val backgroundDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_notification_top)
            backgroundDrawable?.setTint(getThemeColor(com.google.android.material.R.attr.colorSecondaryContainer))
            background = backgroundDrawable

            setPadding(60, 24, 60, 24)
        }

        val layoutParams = layout.layoutParams as FrameLayout.LayoutParams
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutParams.topMargin = 150
        layout.layoutParams = layoutParams
        layout.removeAllViews()
        layout.addView(customView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER })

        customView.translationY = -300f
        snackbar.show()
        customView.animate().translationY(0f).setDuration(400).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun checkAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (binding.bottomNavigation.selectedItemId == R.id.nav_tuner) startTuning()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (binding.bottomNavigation.selectedItemId == R.id.nav_tuner) startTuning()
            } else {
                isTunerActive = false
                binding.statusText.text = getString(R.string.tuner_mic_off)
            }
        }
    }

    override fun onDestroy() {
        autoStepRunnable?.let { handler.removeCallbacks(it) }
        backgroundAnimator?.cancel()
        if (::metronomeController.isInitialized) metronomeController.stop()
        analyzer?.stop()
        super.onDestroy()
    }
}