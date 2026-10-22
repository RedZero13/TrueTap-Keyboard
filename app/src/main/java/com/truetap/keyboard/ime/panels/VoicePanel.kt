package com.truetap.keyboard.ime.panels

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.truetap.keyboard.ime.Icon
import com.truetap.keyboard.ime.Icons
import com.truetap.keyboard.ime.KbTheme

/** Listening UI for speech-to-text: pulsing mic (follows the voice level) and live transcript. */
@SuppressLint("ViewConstructor")
class VoicePanel(context: Context, private val theme: KbTheme, onStop: () -> Unit, onAbc: () -> Unit) : LinearLayout(context) {
    private val status = Ui.label(context, "Listening…", theme, 15f).apply { setTextColor(theme.hint); gravity = Gravity.CENTER }
    private val transcript = Ui.label(context, "", theme, 18f).apply { gravity = Gravity.CENTER }
    private var level = 0f

    private val mic = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pulse = 0f
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val base = minOf(width, height) * 0.28f
            paint.style = Paint.Style.FILL
            paint.color = theme.accent
            paint.alpha = (70 * (1 - pulse)).toInt()
            canvas.drawCircle(cx, cy, base * (1.1f + 0.6f * pulse + level * 0.4f), paint)
            paint.alpha = 255
            canvas.drawCircle(cx, cy, base, paint)
            Icons.draw(canvas, Icon.MIC, cx, cy, base * 1.1f, theme.onAccent, paint)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(theme.background)
        addView(transcript, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 14f) })
        addView(mic, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        mic.setOnClickListener { onStop() }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(Ui.bottomBar(context, theme, onAbc, null, null), LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 46f)))
    }

    fun setStatus(s: String) { status.text = s }
    fun setTranscript(s: String) { transcript.text = s }
    fun setLevel(rmsDb: Float) { level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f) }
}
