package com.truetap.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import com.truetap.keyboard.Prefs

/** Keyboard colours. Uses the wallpaper-based Material You palette on Android 12+ like Gboard. */
data class KbTheme(
    val dark: Boolean,
    val background: Int,
    val key: Int,
    val keyFunction: Int,
    val keyPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val onAccent: Int,
    val strip: Int,
    val stripText: Int,
    val divider: Int,
    val preview: Int,
    val touchDot: Int,
) {
    companion object {
        fun load(context: Context, prefs: Prefs): KbTheme {
            val dark = when (prefs.theme) {
                "dark" -> true
                "light" -> false
                else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            if (prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return dynamic(context, dark)
            return if (dark) DARK else LIGHT
        }

        @android.annotation.TargetApi(Build.VERSION_CODES.S)
        private fun dynamic(context: Context, dark: Boolean): KbTheme {
            fun sys(id: Int) = context.getColor(id)
            return if (dark) KbTheme(
                dark = true,
                background = sys(android.R.color.system_neutral1_900),
                key = sys(android.R.color.system_neutral2_800),
                keyFunction = sys(android.R.color.system_neutral2_700),
                keyPressed = sys(android.R.color.system_neutral2_600),
                text = sys(android.R.color.system_neutral1_50),
                hint = sys(android.R.color.system_neutral2_300),
                accent = sys(android.R.color.system_accent1_200),
                onAccent = sys(android.R.color.system_accent1_800),
                strip = sys(android.R.color.system_neutral1_900),
                stripText = sys(android.R.color.system_neutral1_100),
                divider = sys(android.R.color.system_neutral2_700),
                preview = sys(android.R.color.system_neutral2_700),
                touchDot = sys(android.R.color.system_accent3_300),
            ) else KbTheme(
                dark = false,
                background = sys(android.R.color.system_neutral1_100),
                key = sys(android.R.color.system_neutral1_10),
                keyFunction = sys(android.R.color.system_neutral2_200),
                keyPressed = sys(android.R.color.system_neutral2_300),
                text = sys(android.R.color.system_neutral1_900),
                hint = sys(android.R.color.system_neutral2_600),
                accent = sys(android.R.color.system_accent1_600),
                onAccent = sys(android.R.color.system_accent1_0),
                strip = sys(android.R.color.system_neutral1_100),
                stripText = sys(android.R.color.system_neutral1_800),
                divider = sys(android.R.color.system_neutral2_200),
                preview = sys(android.R.color.system_neutral1_10),
                touchDot = sys(android.R.color.system_accent3_500),
            )
        }

        val LIGHT = KbTheme(
            dark = false,
            background = Color.parseColor("#E8EAED"),
            key = Color.WHITE,
            keyFunction = Color.parseColor("#C8CCD2"),
            keyPressed = Color.parseColor("#B0B4BA"),
            text = Color.parseColor("#202124"),
            hint = Color.parseColor("#5F6368"),
            accent = Color.parseColor("#3B4BDB"),
            onAccent = Color.WHITE,
            strip = Color.parseColor("#E8EAED"),
            stripText = Color.parseColor("#3C4043"),
            divider = Color.parseColor("#C8CCD2"),
            preview = Color.WHITE,
            touchDot = Color.parseColor("#FF6D3A"),
        )

        val DARK = KbTheme(
            dark = true,
            background = Color.parseColor("#1F1F1F"),
            key = Color.parseColor("#3C3C3C"),
            keyFunction = Color.parseColor("#2B2B2B"),
            keyPressed = Color.parseColor("#555555"),
            text = Color.parseColor("#E8EAED"),
            hint = Color.parseColor("#9AA0A6"),
            accent = Color.parseColor("#AAB4FF"),
            onAccent = Color.parseColor("#0E1550"),
            strip = Color.parseColor("#1F1F1F"),
            stripText = Color.parseColor("#E8EAED"),
            divider = Color.parseColor("#3C3C3C"),
            preview = Color.parseColor("#4A4A4A"),
            touchDot = Color.parseColor("#FF9E7A"),
        )
    }
}
