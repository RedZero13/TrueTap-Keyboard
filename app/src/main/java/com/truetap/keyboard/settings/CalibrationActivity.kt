package com.truetap.keyboard.settings

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.truetap.keyboard.Brain
import com.truetap.keyboard.Languages
import com.truetap.keyboard.Prefs
import com.truetap.keyboard.engine.SpatialModel
import com.truetap.keyboard.engine.Tap
import com.truetap.keyboard.ime.Icon
import com.truetap.keyboard.ime.KbTheme
import com.truetap.keyboard.ime.KeyType
import com.truetap.keyboard.ime.KeyboardView
import com.truetap.keyboard.ime.Layouts
import com.truetap.keyboard.ime.PreviewOverlay
import kotlin.math.hypot

/**
 * Typing calibration: the user types a few pangrams on the real keyboard layout while we record
 * where each intended key was actually hit. Teaches the touch model the user's personal offsets.
 */
class CalibrationActivity : Activity(), KeyboardView.Listener {
    private lateinit var prefs: Prefs
    private lateinit var kb: KeyboardView
    private lateinit var target: TextView
    private lateinit var status: TextView
    private lateinit var layoutId: String
    private var spatial: SpatialModel? = null

    private var phrase = 0
    private var pos = 0
    private val samples = ArrayList<Pair<Char, Tap>>()
    private var rawHits = 0
    private var total = 0

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val theme = KbTheme.load(this, prefs)
        val lang = Languages.get(prefs.currentLanguage)
        layoutId = lang.layout

        val root = android.widget.FrameLayout(this)
        root.fitsSystemWindows = true
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
        }
        val pad = dp(20f).toInt()
        col.addView(TextView(this).apply {
            text = "Calibrate typing"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(theme.text)
            setPadding(pad, pad, pad, 0)
        })
        col.addView(TextView(this).apply {
            text = "Type each phrase at your normal speed, holding the phone the way you usually do. Don't fix mistakes — just keep going. TrueTap learns where your thumbs really land."
            setTextColor(theme.hint)
            setPadding(pad, dp(8f).toInt(), pad, 0)
        })
        target = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(theme.text)
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        col.addView(target, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            setTextColor(theme.hint)
            gravity = Gravity.CENTER
            setPadding(pad, 0, pad, dp(8f).toInt())
        }
        col.addView(status)
        val done = Button(this).apply {
            text = "Done"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        col.addView(done, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })

        kb = KeyboardView(this, theme)
        kb.keyHeightPx = prefs.keyHeightDp * resources.displayMetrics.density
        kb.showTouchPoints = true
        kb.glideEnabled = false
        kb.listener = this
        kb.layout = Layouts.letters(lang.code, layoutId, prefs.numberRow, false, Icon.ENTER, false)
        col.addView(kb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(col)
        val overlay = PreviewOverlay(this)
        root.addView(overlay)
        kb.overlay = overlay
        setContentView(root)
        render()
    }

    override fun onPause() {
        super.onPause()
        Brain.save(this)
    }

    private fun model(): SpatialModel? {
        spatial?.let { return it }
        val geom = kb.letterGeometry()
        if (geom.isEmpty()) return null
        return SpatialModel(geom, Brain.touchModel(this, layoutId)).also { spatial = it }
    }

    private fun current() = PHRASES.getOrNull(phrase)

    private fun render() {
        val p = current()
        if (p == null) {
            showResults()
            return
        }
        val s = SpannableString(p)
        if (pos < p.length) {
            s.setSpan(BackgroundColorSpan(0x33C62828), pos, pos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(UnderlineSpan(), pos, pos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        s.setSpan(ForegroundColorSpan(0x88888888.toInt()), 0, pos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        target.text = s
        status.text = "Phrase ${phrase + 1} of ${PHRASES.size}" + if (total > 0) " · key under thumb was right ${100 * rawHits / total}% of the time" else ""
    }

    private fun advance() {
        val p = current() ?: return
        pos++
        if (pos >= p.length) {
            phrase++
            pos = 0
        }
        render()
    }

    private fun showResults() {
        val m = model()
        var modelHits = 0
        var dx = 0f
        var dy = 0f
        if (m != null) {
            for ((c, tap) in samples) {
                val scores = m.scores(tap)
                val best = scores.indices.maxByOrNull { scores[it] } ?: -1
                if (best >= 0 && m.keys[best].char == c) modelHits++
                m.key(c)?.let { k -> dx += (tap.x - k.cx) / k.w; dy += (tap.y - k.cy) / k.w }
            }
        }
        val n = samples.size.coerceAtLeast(1)
        val raw = 100 * rawHits / total.coerceAtLeast(1)
        val fitted = 100 * modelHits / n
        val horiz = (100 * dx / n).toInt()
        val vert = (100 * dy / n).toInt()
        target.textSize = 16f
        target.text = buildString {
            append("All done!\n\n")
            append("Key under your thumb: $raw% correct\n")
            append("With your personal touch model: $fitted% correct\n\n")
            append("On average you tap ")
            append(if (vert >= 0) "$vert% of a key low" else "${-vert}% of a key high")
            append(" and ")
            append(if (horiz >= 0) "$horiz% right" else "${-horiz}% left")
            append(" of centre. TrueTap now aims for where you actually tap, and keeps learning as you type.")
        }
        status.text = "You can run this again any time from settings."
        Brain.markTouchDirty(layoutId)
        Brain.save(this)
    }

    // ------------------------------------------------------------------ keyboard input

    override fun resolveLetter(x: Float, y: Float, size: Float, nearSpace: Boolean): KeyboardView.Key? = null

    override fun onLetter(key: KeyboardView.Key, x: Float, y: Float, size: Float, nearSpace: Boolean) {
        val p = current() ?: return
        val expected = p[pos]
        if (expected == ' ') return
        val m = model() ?: return
        val k = m.key(expected) ?: run { advance(); return }
        val tap = Tap(x, y, size)
        total++
        if (key.spec.label[0] == expected) rawHits++
        // Only learn from taps that were plausibly aimed at the expected key.
        if (hypot((x - k.cx) / k.w, (y - k.cy) / k.w) <= 1.3f) {
            m.learn(expected, tap)
            samples.add(expected to tap)
        }
        advance()
    }

    override fun onKey(key: KeyboardView.Key, nearSpace: Boolean) {
        val p = current() ?: return
        if (key.spec.type == KeyType.SPACE && p[pos] == ' ') advance()
    }

    override fun onText(text: String) {}
    override fun onPress(key: KeyboardView.Key) {}

    companion object {
        val PHRASES = listOf(
            "the quick brown fox jumps over the lazy dog",
            "pack my box with five dozen liquor jugs",
            "how vexingly quick daft zebras jump",
            "sphinx of black quartz judge my vow",
            "we promptly judged antique ivory buckles for the next prize",
            "just keep examining every low bid quoted for zinc etchings",
        )
    }
}
