package com.eduashi.strings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.eduashi.strings.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.*
import androidx.core.graphics.toColorInt
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var metronomeController: MetronomeController

    private var analyzer: AudioAnalyzer? = null
    private var currentTuning: InstrumentTuning? = null
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val pitchHistory = mutableListOf<Float>()
    private val historySize = 15
    private var hasVibrated = false

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoChanging = false
    private var autoStepRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Применяем тему ПЕРЕД установкой контента
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)

        binding = ActivityMainBinding.inflate(layoutInflater)
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // 2. Инициализация всех систем
        setupUI()
        setupMetronome()
        setupNavigation()
        setupSettings()
        checkAudioPermission()

        // 3. ФИКС: Восстанавливаем экран после перезагрузки темы
        // post гарантирует, что код выполнится, когда меню уже восстановило нажатую кнопку
        binding.root.post {
            restoreScreenState()
        }
    }

    private fun setupUI() {
        // Обработка кликов через binding
        binding.chipPresets.setOnClickListener { showPresetsDialog() }

        binding.modeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(R.id.chipChromatic)) resetToChromatic()
        }
    }

    private fun resetToChromatic() {
        pitchHistory.clear()
        currentTuning = null
        binding.statusText.text = getString(R.string.mode_chromatic)
        binding.rootLayout.animateBackgroundColor(
            MaterialColors.getColor(binding.rootLayout, com.google.android.material.R.attr.colorSurface)
        )
    }

    private fun setupMetronome() {
        metronomeController = MetronomeController(
            onBpmChanged = { newBpm ->
                binding.bpmText.text = newBpm.toString()
                binding.bpmText.bounce()
            },
            onTick = { beat ->
                runOnUiThread {
                    binding.metronomeScale.animateTick(if (beat % 2 != 0) 100f else -100f)
                    binding.metronomeScale.flashAccent(beat == 1)
                }
            }
        )
        setupMetronomeButtons()
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // Сбрасываем фон и текст ноты при переключении
            binding.rootLayout.animateBackgroundColor(
                MaterialColors.getColor(binding.rootLayout, com.google.android.material.R.attr.colorSurface)
            )
            binding.noteText.text = ""

            when (item.itemId) {
                R.id.nav_tuner -> {
                    // Показываем тюнер, скрываем метроном и настройки
                    switchLayout(binding.tunerGroup, listOf(binding.metronomeLayout, binding.settingsLayout))
                    metronomeController.stop()
                    updateMetronomeUI()
                    startTuning()
                    true
                }
                R.id.nav_metronome -> {
                    // Показываем метроном, скрываем тюнер и настройки
                    switchLayout(binding.metronomeLayout, listOf(binding.tunerGroup, binding.settingsLayout))
                    analyzer?.stop()
                    true
                }
                R.id.nav_settings -> {
                    // Показываем настройки, скрываем тюнер и метроном
                    switchLayout(binding.settingsLayout, listOf(binding.tunerGroup, binding.metronomeLayout))
                    analyzer?.stop()
                    metronomeController.stop()
                    updateMetronomeUI()
                    true
                }
                else -> false
            }
        }
    }

    private fun switchLayout(toShow: View, toHide: List<View>) {
        if (toShow.isVisible) return

        // 1. Прячем старые экраны с анимацией ухода (Fade out + Scale down)
        toHide.forEach { view ->
            if (view.isVisible) {
                view.animate()
                    .alpha(0f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(150)
                    .withEndAction {
                        view.isVisible = false
                    }
            }
        }

        // 2. Показываем новый экран (Fade in + Scale up)
        toShow.apply {
            alpha = 0f
            scaleX = 0.95f
            scaleY = 0.95f
            isVisible = true
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setStartDelay(100) // Небольшая пауза, чтобы старый экран успел начать исчезать
                .start()
        }
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
            setOnTouchListener { _, e -> handleAuto(e, 1); false } // Шаг 1 для авто
        }
        binding.btnMinus.apply {
            setOnClickListener { if (!isAutoChanging) metronomeController.bpm -= 1 }
            setOnTouchListener { _, e -> handleAuto(e, -1); false }
        }
        binding.btnStartStop.setOnClickListener {
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
                        // Увеличиваем шаг до 10 для долгого нажатия
                        val bigStep = step * 10
                        metronomeController.bpm += bigStep

                        // Виброотклик для тактильного ощущения "прыжка"
                        binding.bpmText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                        // Повторяем каждые 350 мс
                        handler.postDelayed(this, 300)
                    }
                }
                // Задержка перед началом авто-повтора (500 мс)
                handler.postDelayed(autoStepRunnable!!, 500)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                autoStepRunnable?.let { handler.removeCallbacks(it) }
                autoStepRunnable = null
                // Небольшая задержка, чтобы обычный клик не считался за "авто"
                handler.postDelayed({ isAutoChanging = false }, 50)
            }
        }
    }

    private fun processFrequencyWithSmoothing(hz: Float) {
        if (hz <= 25f || hz > 1000f) return
        pitchHistory.add(hz)
        if (pitchHistory.size > historySize) pitchHistory.removeAt(0)

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
        binding.noteText.text = "${noteNames[((idx % 12) + 12) % 12]}${idx / 12 - 1}"

        if (abs(cents) < 8) {
            if (!hasVibrated) { triggerVibe(); hasVibrated = true }
            binding.rootLayout.animateBackgroundColor("#2E7D32".toColorInt()) // Dark Green
            binding.noteText.setTextColor(Color.WHITE)
            binding.statusText.text = getString(R.string.perfect)
        } else {
            hasVibrated = false
            binding.rootLayout.animateBackgroundColor(
                MaterialColors.getColor(binding.rootLayout, com.google.android.material.R.attr.colorSurface)
            )
            binding.noteText.setTextColor(MaterialColors.getColor(binding.noteText, com.google.android.material.R.attr.colorPrimary))
            binding.statusText.text = getString(R.string.pitch_detected, "%.1f".format(hz))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePresetUI(cents: Float, hz: Float, target: Int) {
        val tuning = currentTuning ?: return

        // 1. Вычисляем номер струны. Если не нашли — 0 (для безопасности форматтера %d)
        val strIdx = tuning.notes.indexOf(target)
        val strNum = if (strIdx != -1) (tuning.notes.size - strIdx) else 0

        // 2. Отображаем ноту
        binding.noteText.text = "${noteNames[((target % 12) + 12) % 12]}${target / 12 - 1}"

        if (abs(cents) < 8) {
            // ИДЕАЛЬНО
            if (!hasVibrated) { triggerVibe(); hasVibrated = true }
            binding.rootLayout.animateBackgroundColor("#2E7D32".toColorInt())

            // Используем getString с аргументами. В strings.xml должно быть что-то вроде:
            // "STR %1$d: %2$s"
            binding.statusText.text = getString(R.string.string_number, strNum) + ": " + getString(R.string.perfect)

        } else {
            // НЕ В ТОНЕ
            hasVibrated = false
            binding.rootLayout.animateBackgroundColor(
                MaterialColors.getColor(binding.rootLayout, com.google.android.material.R.attr.colorSurface)
            )

            val direction = if (cents > 0) getString(R.string.too_high) else getString(R.string.too_low)

            // Собираем финальную строку без хардкода "STR" и "\n"
            // Рекомендую в strings.xml сделать ключ:
            // <string name="tuning_status_error">STR %1$d: %2$s\n%3$.1f Hz</string>
            val pitchInfo = "%.1f Hz".format(hz)
            binding.statusText.text = "${getString(R.string.string_number, strNum)}: $direction\n$pitchInfo"
        }
    }

    private fun showPresetsDialog() {
        val names = arrayOf(
            getString(R.string.tuning_guitar_6),
            getString(R.string.tuning_guitar_7),
            getString(R.string.tuning_bass_4),
            getString(R.string.tuning_bass_5),
            getString(R.string.tuning_drop_d),
            getString(R.string.tuning_drop_c)
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

    private fun triggerVibe() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    private fun setupSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        // 1. Устанавливаем правильную кнопку при открытии (по сохраненному значению)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.themeToggleGroup.check(R.id.btnLightTheme)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.themeToggleGroup.check(R.id.btnDarkTheme)
            else -> binding.themeToggleGroup.check(R.id.btnSystemTheme)
        }

        // 2. Слушатель переключения кнопок
        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.btnLightTheme -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btnDarkTheme -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                // Применяем тему
                AppCompatDelegate.setDefaultNightMode(mode)
                // Сохраняем выбор
                prefs.edit { putInt("theme_mode", mode) }
            }
        }

        // 3. Кнопка "О приложении"
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        // Получаем версию программно из BuildConfig или PackageManager
        val versionName = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName
        } catch (e: Exception) {
            "1.0" // Резервный вариант
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            // Используем только getString(ID, аргумент)
            .setMessage(getString(R.string.about_message, versionName))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun restoreScreenState() {
        val selectedId = binding.bottomNavigation.selectedItemId

        // Сбрасываем состояние всех групп
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
            R.id.nav_metronome -> binding.metronomeLayout.isVisible = true
            R.id.nav_settings -> binding.settingsLayout.isVisible = true
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