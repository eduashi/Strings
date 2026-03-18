package com.eduashi.strings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import com.google.android.material.color.MaterialColors

// Плавная смена цвета фона
fun View.animateBackgroundColor(targetColor: Int, duration: Long = 300) {
    val colorFrom = (this.background as? ColorDrawable)?.color
        ?: MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)

    if (colorFrom == targetColor) return

    ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, targetColor).apply {
        this.duration = duration
        addUpdateListener { setBackgroundColor(it.animatedValue as Int) }
        start()
    }
}

// Эффект прыжка (для текста BPM или нот)
fun View.bounce() {
    this.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction {
        this.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
    }.start()
}

// Пульсация (для кнопки Старт/Стоп)
fun View.startPulse() {
    val anim = ScaleAnimation(1f, 1.05f, 1f, 1.05f,
        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f
    ).apply {
        duration = 500
        repeatCount = Animation.INFINITE
        repeatMode = Animation.REVERSE
    }
    this.startAnimation(anim)
}

fun View.stopPulse() = this.clearAnimation()