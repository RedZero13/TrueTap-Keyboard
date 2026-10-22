package com.truetap.keyboard.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.truetap.keyboard.Brain
import com.truetap.keyboard.Languages
import com.truetap.keyboard.Prefs

/** Setup steps and every keyboard option, built in code (no layout XML needed). */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var setupStatus: TextView
    private lateinit var micStatus: TextView
    private lateinit var learnedStatus: TextView

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(16f), dp(20f), dp(32f))
        }
        scroll.addView(list)
        setContentView(scroll)
        build()
        if (intent.getBooleanExtra(EXTRA_REQUEST_MIC, false)) requestMic()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The IME picker is a dialog; re-check when it closes.
        if (hasFocus) refreshStatus()
    }

    private fun build() {
        title("TrueTap Keyboard", 26f)
        text("A Gboard-style keyboard that types what you aim for. Every tap is matched against where your thumb actually lands and which words make sense, so near-misses come out right.")

        section("Set up")
        setupStatus = text("")
        button("1. Enable TrueTap in system settings") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("2. Switch to TrueTap") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        val test = EditText(this).apply {
            hint = "Try it here"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
        }
        list.addView(test, lp())

        section("Typing accuracy")
        text("Autocorrect strength", bold = true)
        radios(listOf("Off", "Gentle", "Normal", "Aggressive"), prefs.autocorrectLevel) { prefs.autocorrectLevel = it }
        toggle("Learn my touch pattern", "Adapts each key's target to where you really tap", prefs.adaptiveLearning) { prefs.adaptiveLearning = it }
        toggle("Fix missed space bar", "“hello.world” → “hello world” when the period was hit at the space bar's edge", prefs.smartPunctuation) { prefs.smartPunctuation = it }
        toggle("Split run-together words", "“thisis” → “this is”", prefs.splitWords) { prefs.splitWords = it }
        toggle("Show touch points", "Dots where your taps land (handy while testing)", prefs.showTouchPoints) { prefs.showTouchPoints = it }
        button("Calibrate typing (2 minutes)") { startActivity(Intent(this, CalibrationActivity::class.java)) }
        learnedStatus = text("")
        button("Reset learned touch data") { Brain.resetTouchLearning(this); refreshStatus() }
        button("Clear learned words & history") { Brain.clearHistory(this) }

        section("Languages")
        text("Tap the globe key (or long-press space) to switch.")
        for (l in Languages.all) {
            val cb = CheckBox(this).apply {
                text = l.name
                isChecked = l.code in prefs.languages
                setOnCheckedChangeListener { v, checked ->
                    val cur = prefs.languages.toMutableList()
                    if (checked) { if (l.code !in cur) cur.add(l.code) } else cur.remove(l.code)
                    if (cur.isEmpty()) { v.isChecked = true; return@setOnCheckedChangeListener }
                    prefs.languages = Languages.all.map { it.code }.filter { it in cur }
                }
            }
            list.addView(cb, lp())
        }

        section("Layout")
        toggle("Number row", "Show 1–0 above the letters", prefs.numberRow) { prefs.numberRow = it }
        slider("Key height", prefs.keyHeightDp, 44, 72, "dp (Pixel 9 default: ${Prefs.DEFAULT_KEY_HEIGHT_DP})") { prefs.keyHeightDp = it }
        toggle("Glide typing", "Slide across letters to type a word", prefs.glideTyping) { prefs.glideTyping = it }
        toggle("Key preview popup", null, prefs.keyPreview) { prefs.keyPreview = it }
        toggle("Vibrate on keypress", null, prefs.haptics) { prefs.haptics = it }
        toggle("Sound on keypress", null, prefs.sound) { prefs.sound = it }
        text("One-handed and floating modes are in the keyboard toolbar (⊞ → One-handed / Floating).")

        section("Text")
        toggle("Auto-capitalisation", null, prefs.autoCap) { prefs.autoCap = it }
        toggle("Double-space for period", null, prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
        toggle("Next-word suggestions", "Learned from what you type, on this device only", prefs.nextWordPrediction) { prefs.nextWordPrediction = it }
        toggle("Clipboard history", "Keeps copied text for an hour unless pinned", prefs.clipboardHistory) { prefs.clipboardHistory = it }

        section("Appearance")
        radios(listOf("System", "Light", "Dark"), listOf("system", "light", "dark").indexOf(prefs.theme).coerceAtLeast(0)) {
            prefs.theme = listOf("system", "light", "dark")[it]
        }
        toggle("Material You colours", "Match your wallpaper colours (Android 12+)", prefs.dynamicColor) { prefs.dynamicColor = it }

        section("GIFs & stickers")
        text("GIF and sticker search uses GIPHY. Create a free API key at developers.giphy.com (Create an App → API) and paste it here.")
        val key = EditText(this).apply {
            hint = "GIPHY API key"
            setSingleLine()
            setText(prefs.sp.getString("giphy_key", "") ?: "")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { prefs.giphyKey = s?.toString() ?: "" }
            })
        }
        list.addView(key, lp())
        button("Open developers.giphy.com") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.giphy.com/dashboard/"))) }

        section("Voice typing")
        micStatus = text("")
        button("Allow microphone") { requestMic() }

        section("About")
        text("Definitions: dictionaryapi.dev · Synonyms: Datamuse · GIFs: GIPHY. Word lists from the wordfreq project (CC BY-SA 4.0). Everything TrueTap learns about your typing stays on this device.")
    }

    private fun refreshStatus() {
        if (!::setupStatus.isInitialized) return
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        setupStatus.text = when {
            current -> "✅ TrueTap is your active keyboard."
            enabled -> "Step 1 done ✅  Now switch to TrueTap."
            else -> "TrueTap isn't enabled yet."
        }
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.text = if (mic) "✅ Microphone allowed — tap the mic in the keyboard toolbar." else "Voice typing needs microphone access."
        val taps = Brain.totalLearnedTaps(this)
        learnedStatus.text = if (taps == 0) "No touch data learned yet." else "Learned from $taps taps."
    }

    private fun requestMic() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    // ---------------------------------------------------------------- tiny view helpers

    private fun lp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun title(t: String, size: Float) {
        list.addView(TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTypeface(typeface, Typeface.BOLD)
        }, lp())
    }

    private fun section(t: String) {
        list.addView(TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF3B4BDB.toInt())
            setPadding(0, dp(24f), 0, dp(6f))
        }, lp())
    }

    private fun text(t: String, bold: Boolean = false): TextView {
        val tv = TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4f), 0, dp(4f))
        }
        list.addView(tv, lp())
        return tv
    }

    private fun button(t: String, onClick: () -> Unit) {
        list.addView(Button(this).apply {
            text = t
            isAllCaps = false
            setOnClickListener { onClick() }
        }, lp())
    }

    @Suppress("DEPRECATION")
    private fun toggle(t: String, summary: String?, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(this).apply {
            text = if (summary != null) "$t\n$summary" else t
            isChecked = value
            setPadding(0, dp(6f), 0, dp(6f))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        list.addView(sw, lp())
    }

    private fun radios(options: List<String>, selected: Int, onChange: (Int) -> Unit) {
        val g = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        options.forEachIndexed { i, o ->
            g.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = o
                isChecked = i == selected
                setOnClickListener { onChange(i) }
            })
        }
        list.addView(g, lp())
    }

    private fun slider(t: String, value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
        val label = text("$t: $value $unit")
        list.addView(SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = "$t: $progress $unit"
                    if (fromUser) onChange(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }, lp())
        label.gravity = Gravity.START
    }

    companion object {
        const val EXTRA_REQUEST_MIC = "request_mic"
    }
}
