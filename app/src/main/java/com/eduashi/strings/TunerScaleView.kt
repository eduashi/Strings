package com.eduashi.strings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.R

class TunerScaleView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // --- НАСТРОЙКИ КРАСОК (Paint) ---

    // 1. Краска для жирного "трека" (подложки) - теперь как у системного Slider
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 24f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // 2. Краска для центральной отметки (тюнер) и ограничителей (метроном)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // 3. Краска для самого индикатора (ползунка)
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // --- СОСТОЯНИЕ ---
    var isMetronomeMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var targetCents: Float = 0f
    private var currentDisplayCents: Float = 0f
    private val lerpFactor = 0.15f

    private val indicatorHeight = 50f
    private val boundaryHeight = 40f

    // --- УТИЛИТЫ ---

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /**
     * Обновляет цвета всех элементов в соответствии с текущей темой Material 3
     */
    private fun updateThemeColors() {
        // Основной акцентный цвет (фиолетовый или динамический)
        val colorPrimary = getThemeColor(R.attr.colorPrimary)

        // Цвет подложки (мягкий серый/цвет поверхности как у Slider)
        val colorTrack = getThemeColor(R.attr.colorSurfaceVariant)

        // Цвет рисок (контурный цвет системы)
        val colorOutline = getThemeColor(R.attr.colorOutline)

        pointerPaint.color = colorPrimary
        linePaint.color = colorTrack
        markerPaint.color = colorOutline
        // Для метронома используем тот же цвет рисок, но чуть тоньше (настроим в onDraw)
    }

    fun setCents(cents: Float) {
        targetCents = cents.coerceIn(-100f, 100f)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Обновляем цвета перед отрисовкой, чтобы они всегда были актуальны (тема, динамические цвета)
        updateThemeColors()

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val centerY = heightF / 2
        val padding = 60f
        val range = widthF - (padding * 2)

        // 1. Рисуем "трек" (подложку)
        canvas.drawLine(padding, centerY, widthF - padding, centerY, linePaint)

        val xPos: Float
        if (isMetronomeMode) {
            // РЕЖИМ МЕТРОНОМА
            markerPaint.strokeWidth = 4f // Делаем боковые границы чуть тоньше
            canvas.drawLine(padding, centerY - boundaryHeight, padding, centerY + boundaryHeight, markerPaint)
            canvas.drawLine(widthF - padding, centerY - boundaryHeight, widthF - padding, centerY + boundaryHeight, markerPaint)

            xPos = (widthF / 2) + (targetCents / 100f) * (range / 2)
        } else {
            // РЕЖИМ ТЮНЕРА
            markerPaint.strokeWidth = 6f
            canvas.drawLine(widthF / 2, centerY - 25, widthF / 2, centerY + 25, markerPaint)

            if (Math.abs(currentDisplayCents - targetCents) > 0.01f) {
                currentDisplayCents += (targetCents - currentDisplayCents) * lerpFactor
                postInvalidateOnAnimation()
            }
            xPos = (widthF / 2) + (currentDisplayCents / 100f) * (range / 2)
        }

        // 2. Рисуем индикатор (ползунок)
        canvas.drawLine(xPos, centerY - indicatorHeight, xPos, centerY + indicatorHeight, pointerPaint)
    }

    // --- Анимация для Метронома ---
    fun animateTick(toCents: Float) {
        val animator = ValueAnimator.ofFloat(this.targetCents, toCents)
        animator.duration = 100
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            setCents(animation.animatedValue as Float)
        }
        animator.start()

        this.postDelayed({
            val backAnim = ValueAnimator.ofFloat(toCents, toCents * 0.4f)
            backAnim.duration = 150
            backAnim.addUpdateListener {
                setCents(it.animatedValue as Float)
            }
            backAnim.start()
        }, 110)
    }
}