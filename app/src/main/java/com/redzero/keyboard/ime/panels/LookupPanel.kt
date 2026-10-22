package com.redzero.keyboard.ime.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.redzero.keyboard.ime.KbTheme
import com.redzero.keyboard.net.Lookup
import com.redzero.keyboard.net.Net

/** Definition and thesaurus for the word at the cursor. Tapping a synonym replaces the word. */
@SuppressLint("ViewConstructor")
class LookupPanel(
    context: Context,
    private val theme: KbTheme,
    private val word: String?,
    private val lang: String,
    startOnSynonyms: Boolean,
    private val onReplace: (String) -> Unit,
    onAbc: () -> Unit,
) : LinearLayout(context) {

    private val body = LinearLayout(context)
    private val tabDefine = Ui.chip(context, "Definition", theme)
    private val tabSyn = Ui.chip(context, "Synonyms", theme)
    private var generation = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 10f), Ui.dp(context, 6f), Ui.dp(context, 10f), Ui.dp(context, 2f))
        }
        header.addView(Ui.label(context, word ?: "", theme, 18f, bold = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(tabDefine, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(context, 6f) })
        header.addView(tabSyn)
        addView(header)
        val scroll = ScrollView(context)
        body.orientation = VERTICAL
        body.setPadding(Ui.dp(context, 12f), Ui.dp(context, 6f), Ui.dp(context, 12f), Ui.dp(context, 6f))
        scroll.addView(body)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(Ui.bottomBar(context, theme, onAbc, null, null), LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 46f)))
        tabDefine.setOnClickListener { showDefinitions() }
        tabSyn.setOnClickListener { showSynonyms() }
        if (startOnSynonyms) showSynonyms() else showDefinitions()
    }

    private fun selectTab(def: Boolean) {
        val r = Ui.dp(context, 16f).toFloat()
        tabDefine.background = Ui.pressable(context, if (def) theme.accent else theme.key, r, theme)
        tabDefine.setTextColor(if (def) theme.onAccent else theme.text)
        tabSyn.background = Ui.pressable(context, if (!def) theme.accent else theme.key, r, theme)
        tabSyn.setTextColor(if (!def) theme.onAccent else theme.text)
    }

    private fun message(text: String) {
        body.removeAllViews()
        body.addView(Ui.label(context, text, theme).apply { setTextColor(theme.hint) })
    }

    private fun showDefinitions() {
        selectTab(true)
        val w = word ?: return message("Type or select a word, then tap Define.")
        message("Looking up “$w”…")
        val gen = ++generation
        Net.async({ Lookup.define(w, lang) }) { r ->
            if (gen != generation) return@async
            val senses = r.getOrNull()
            if (senses.isNullOrEmpty()) return@async message("No definition found for “$w”.")
            body.removeAllViews()
            var lastPos = ""
            for (s in senses) {
                if (s.partOfSpeech != lastPos) {
                    body.addView(Ui.label(context, s.partOfSpeech, theme, 13f, bold = true).apply {
                        setTextColor(theme.accent); setPadding(0, Ui.dp(context, 8f), 0, Ui.dp(context, 2f))
                    })
                    lastPos = s.partOfSpeech
                }
                val text = SpannableStringBuilder("• ").append(s.definition)
                s.example?.let {
                    val start = text.length
                    text.append("\n   “").append(it).append("”")
                    text.setSpan(StyleSpan(Typeface.ITALIC), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                body.addView(TextView(context).apply {
                    this.text = text
                    setTextColor(theme.text)
                    textSize = 15f
                    setPadding(0, Ui.dp(context, 3f), 0, Ui.dp(context, 3f))
                })
            }
        }
    }

    private fun showSynonyms() {
        selectTab(false)
        val w = word ?: return message("Type or select a word, then tap Thesaurus.")
        message("Finding synonyms for “$w”…")
        val gen = ++generation
        Net.async({ Lookup.synonyms(w, lang) }) { r ->
            if (gen != generation) return@async
            val syns = r.getOrNull()
            if (syns.isNullOrEmpty()) return@async message("No synonyms found for “$w”.")
            body.removeAllViews()
            body.addView(Ui.label(context, "Tap to replace", theme, 12f).apply { setTextColor(theme.hint) })
            val flow = FlowLayout(context, Ui.dp(context, 6f), Ui.dp(context, 6f))
            flow.setPadding(0, Ui.dp(context, 6f), 0, 0)
            for (s in syns) {
                val chip = Ui.chip(context, s, theme)
                chip.setOnClickListener { onReplace(s) }
                flow.addView(chip)
            }
            body.addView(flow)
        }
    }
}
