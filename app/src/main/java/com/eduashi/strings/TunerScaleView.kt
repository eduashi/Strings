package com.eduashi.strings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.toColorInt

class TunerScaleView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private fun isDarkTheme(): Boolean {
        return (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private val linePaint = Paint().apply {
        color = if (isDarkTheme()) Color.DKGRAY else Color.LTGRAY
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val pointerPaint = Paint().apply {
        color = "#3F51B5".toColorInt() // Основной синий цвет кружка
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // ТЕ ПЕРЕМЕННЫЕ, КОТОРЫЕ НУЖНЫ
    private var targetCents: Float = 0f
    private var currentDisplayCents: Float = 0f
    private val lerpFactor = 0.15f // Плавность для тюнера

    // Метод для Тюнера (плавное движение)
    fun setCents(cents: Float) {
        // Для метронома мы расширим диапазон до 100, чтобы он летал от края до края
        targetCents = cents.coerceIn(-100f, 100f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Плавное приближение (LERP)
        if (Math.abs(currentDisplayCents - targetCents) > 0.01f) {
            currentDisplayCents += (targetCents - currentDisplayCents) * lerpFactor
        }

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        // 2. Рисуем шкалу
        canvas.drawLine(50f, centerY, width - 50f, centerY, linePaint)
        canvas.drawCircle(width / 2, centerY - 20, 10f, linePaint)

        // 3. Рисуем указатель
        // Диапазон шкалы теперь считаем как 100 единиц в каждую сторону
        val range = width - 100f
        val xPos = (width / 2) + (currentDisplayCents / 100f) * (range / 2)
        canvas.drawCircle(xPos, centerY, 20f, pointerPaint)

        postInvalidateOnAnimation()
    }

    // Метод для Метронома (резкий удар)
    fun animateTick(toCents: Float) {
        val animator = ValueAnimator.ofFloat(this.targetCents, toCents)
        animator.duration = 100
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            setCents(animation.animatedValue as Float)
        }
        animator.start()

        // Эффект отскока
        this.postDelayed({
            val backAnim = ValueAnimator.ofFloat(toCents, toCents * 0.4f)
            backAnim.duration = 150
            backAnim.addUpdateListener {
                setCents(it.animatedValue as Float)
            }
            backAnim.start()
        }, 110)
    }

    // Метод для вспышки фона
    fun flashAccent(isAccent: Boolean) {
        val startColor = if (isAccent) {
            Color.argb(120, 255, 255, 255) // Белая вспышка для акцента
        } else {
            Color.argb(60, 187, 134, 252) // Слабая фиолетовая для обычной доли
        }

        val colorAnim = ValueAnimator.ofArgb(startColor, Color.TRANSPARENT)
        colorAnim.duration = 350
        colorAnim.addUpdateListener { animator ->
            this.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnim.start()
    }
}