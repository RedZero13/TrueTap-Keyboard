package com.truetap.keyboard.ime.panels

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.truetap.keyboard.ime.Icon
import com.truetap.keyboard.ime.IconButton
import com.truetap.keyboard.ime.KbTheme

/** Small view factories shared by the panels. */
object Ui {
    fun dp(c: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    fun pressable(c: Context, color: Int, radius: Float, theme: KbTheme): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(theme.keyPressed), rounded(color, radius), rounded(0xFFFFFFFF.toInt(), radius))

    fun chip(c: Context, text: String, theme: KbTheme, selected: Boolean = false): TextView = TextView(c).apply {
        this.text = text
        setTextColor(if (selected) theme.onAccent else theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(c, 14f), dp(c, 7f), dp(c, 14f), dp(c, 7f))
        background = pressable(c, if (selected) theme.accent else theme.key, dp(c, 16f).toFloat(), theme)
        isClickable = true
    }

    fun label(c: Context, text: String, theme: KbTheme, sizeSp: Float = 14f, bold: Boolean = false): TextView = TextView(c).apply {
        this.text = text
        setTextColor(theme.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    /** A bottom bar with an "ABC" button on the left, optional middle content, optional delete on the right. */
    fun bottomBar(c: Context, theme: KbTheme, onAbc: () -> Unit, middle: View?, onDelete: (() -> Unit)?): LinearLayout {
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(theme.background)
        val abc = TextView(c).apply {
            text = "ABC"
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            background = pressable(c, theme.keyFunction, dp(c, 18f).toFloat(), theme)
            setOnClickListener { onAbc() }
        }
        bar.addView(abc, LinearLayout.LayoutParams(dp(c, 52f), dp(c, 36f)).apply { leftMargin = dp(c, 6f); rightMargin = dp(c, 4f) })
        bar.addView(middle ?: View(c), LinearLayout.LayoutParams(0, dp(c, 44f), 1f))
        if (onDelete != null) {
            val del = IconButton(c, Icon.DELETE, theme.text, 0.55f)
            del.setOnClickListener { onDelete() }
            bar.addView(del, LinearLayout.LayoutParams(dp(c, 52f), dp(c, 44f)))
        }
        return bar
    }
}
