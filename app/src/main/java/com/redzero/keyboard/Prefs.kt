package com.redzero.keyboard

import android.content.Context
import android.content.SharedPreferences

/** All user settings, shared by the keyboard service and the settings screens. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences("redzero", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

    var languages: List<String>
        get() = (sp.getString("languages", null) ?: "en").split(',').filter { it in Languages.codes }.ifEmpty { listOf("en") }
        set(v) = sp.edit().putString("languages", v.joinToString(",")).apply()

    var currentLanguage: String
        get() = sp.getString("current_language", null)?.takeIf { it in languages } ?: languages.first()
        set(v) = sp.edit().putString("current_language", v).apply()

    var numberRow by BoolPref("number_row", true)
    var keyPreview by BoolPref("key_preview", true)
    var haptics by BoolPref("haptics", true)
    var sound by BoolPref("sound", false)
    var autoCap by BoolPref("auto_cap", true)
    var doubleSpacePeriod by BoolPref("double_space_period", true)
    var glideTyping by BoolPref("glide", true)
    var adaptiveLearning by BoolPref("adaptive_learning", true)
    var smartPunctuation by BoolPref("smart_punctuation", true)
    var splitWords by BoolPref("split_words", true)
    var showTouchPoints by BoolPref("show_touch_points", false)
    var clipboardHistory by BoolPref("clipboard_history", true)
    var dynamicColor by BoolPref("dynamic_color", true)
    var nextWordPrediction by BoolPref("next_word_prediction", true)

    /** Row height in dp. 56 dp is tuned for the Pixel 9 (1080 x 2424, 420 dpi). */
    var keyHeightDp: Int
        get() = sp.getInt("key_height_dp", DEFAULT_KEY_HEIGHT_DP)
        set(v) = sp.edit().putInt("key_height_dp", v.coerceIn(40, 80)).apply()

    /** 0 off, 1 gentle, 2 normal, 3 aggressive. */
    var autocorrectLevel: Int
        get() = sp.getInt("autocorrect_level", 2)
        set(v) = sp.edit().putInt("autocorrect_level", v.coerceIn(0, 3)).apply()

    /** "system", "light" or "dark". */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    var giphyKey: String
        get() = sp.getString("giphy_key", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.GIPHY_API_KEY
        set(v) = sp.edit().putString("giphy_key", v.trim()).apply()

    /** "normal", "one_left", "one_right" or "floating". */
    var mode: String
        get() = sp.getString("mode", "normal") ?: "normal"
        set(v) = sp.edit().putString("mode", v).apply()

    var floatX: Float
        get() = sp.getFloat("float_x", 0.08f)
        set(v) = sp.edit().putFloat("float_x", v).apply()

    var floatY: Float
        get() = sp.getFloat("float_y", 0.35f)
        set(v) = sp.edit().putFloat("float_y", v).apply()

    var recentEmoji: List<String>
        get() = sp.getString("recent_emoji", "")!!.split('\u0001').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("recent_emoji", v.take(32).joinToString("\u0001")).apply()

    private inner class BoolPref(val key: String, val def: Boolean) {
        operator fun getValue(thisRef: Prefs, p: kotlin.reflect.KProperty<*>) = bool(key, def)
        operator fun setValue(thisRef: Prefs, p: kotlin.reflect.KProperty<*>, v: Boolean) = setBool(key, v)
    }

    companion object {
        const val DEFAULT_KEY_HEIGHT_DP = 56
    }
}

object Languages {
    data class Language(val code: String, val name: String, val layout: String, val locale: String)

    val all = listOf(
        Language("en", "English", "qwerty", "en-US"),
        Language("es", "Español", "qwerty_es", "es-ES"),
        Language("fr", "Français", "azerty", "fr-FR"),
        Language("de", "Deutsch", "qwertz", "de-DE"),
        Language("it", "Italiano", "qwerty", "it-IT"),
        Language("pt", "Português", "qwerty", "pt-BR"),
    )
    val codes = all.map { it.code }.toSet()
    fun get(code: String) = all.firstOrNull { it.code == code } ?: all[0]
}
