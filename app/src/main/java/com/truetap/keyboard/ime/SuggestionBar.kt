package com.truetap.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

enum class Action(val icon: Icon, val title: String) {
    MORE(Icon.GRID, "More"),
    EMOJI(Icon.EMOJI, "Emoji"),
    GIF(Icon.GIF, "GIFs"),
    STICKER(Icon.STICKER, "Stickers"),
    CLIPBOARD(Icon.CLIPBOARD, "Clipboard"),
    DEFINE(Icon.BOOK, "Define"),
    THESAURUS(Icon.THESAURUS, "Thesaurus"),
    ONE_HANDED(Icon.ONE_HANDED, "One-handed"),
    FLOATING(Icon.FLOATING, "Floating"),
    VOICE(Icon.MIC, "Voice"),
    CALIBRATE(Icon.TARGET, "Calibrate"),
    THEME(Icon.PALETTE, "Theme"),
    SETTINGS(Icon.SETTINGS, "Settings"),
    KEYBOARD(Icon.KEYBOARD, "Keyboard"),
}

/**
 * The strip above the keys: Gboard-style toolbar when idle, word suggestions while typing,
 * or a search box while typing a GIF/sticker search.
 */
@SuppressLint("ViewConstructor")
class SuggestionBar(context: Context, private val theme: KbTheme) : LinearLayout(context) {

    interface Listener {
        fun onSuggestion(index: Int, text: String)
        fun onSuggestionLongPress(text: String)
        fun onAction(action: Action)
    }

    var listener: Listener? = null

    private val leftButton = IconButton(context, Icon.GRID, theme.stripText, 0.55f)
    private val rightButton = IconButton(context, Icon.MIC, theme.stripText, 0.55f)
    private val content = FrameLayout(context)
    private val toolbar = LinearLayout(context)
    private val suggestionRow = LinearLayout(context)
    private val searchText = TextView(context)
    private val slots = List(3) { TextView(context) }
    private val dividers = ArrayList<View>()
    private var current: List<String> = emptyList()
    private var leftAction = Action.MORE

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    val barHeight = dp(50f)

    init {
        orientation = HORIZONTAL
        setBackgroundColor(theme.strip)
        gravity = Gravity.CENTER_VERTICAL
        addView(leftButton, LayoutParams(dp(52f), barHeight))
        addView(content, LayoutParams(0, barHeight, 1f))
        addView(rightButton, LayoutParams(dp(52f), barHeight))
        rightButton.background = android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(theme.keyFunction)
            },
            IconDrawable(Icon.MIC, theme.stripText, 0.55f),
        )).apply { setLayerInset(0, dp(5f), dp(4f), dp(5f), dp(4f)) }
        leftButton.setOnClickListener { listener?.onAction(leftAction) }
        rightButton.setOnClickListener { listener?.onAction(Action.VOICE) }

        toolbar.orientation = HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        for (a in TOOLBAR) {
            val b = IconButton(context, a.icon, theme.stripText, 0.52f)
            b.contentDescription = a.title
            b.setOnClickListener { listener?.onAction(a) }
            toolbar.addView(b, LayoutParams(0, barHeight, 1f))
        }
        content.addView(toolbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        suggestionRow.orientation = HORIZONTAL
        suggestionRow.gravity = Gravity.CENTER_VERTICAL
        slots.forEachIndexed { i, tv ->
            tv.gravity = Gravity.CENTER
            tv.setTextColor(theme.stripText)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            tv.maxLines = 1
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.setPadding(dp(4f), 0, dp(4f), 0)
            tv.setOnClickListener { current.getOrNull(slotToIndex(i))?.let { s -> listener?.onSuggestion(slotToIndex(i), s) } }
            tv.setOnLongClickListener {
                current.getOrNull(slotToIndex(i))?.let { s -> listener?.onSuggestionLongPress(s) }
                true
            }
            if (i > 0) suggestionRow.addView(divider().also { dividers.add(it) }, LayoutParams(dp(1f), dp(22f)))
            suggestionRow.addView(tv, LayoutParams(0, barHeight, 1f))
        }
        suggestionRow.visibility = View.GONE
        content.addView(suggestionRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        searchText.setTextColor(theme.stripText)
        searchText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        searchText.gravity = Gravity.CENTER_VERTICAL
        searchText.setPadding(dp(12f), 0, dp(12f), 0)
        searchText.maxLines = 1
        searchText.ellipsize = TextUtils.TruncateAt.START
        searchText.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            setColor(theme.key)
        }
        searchText.visibility = View.GONE
        content.addView(searchText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(36f), Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(4f); rightMargin = dp(4f)
        })
    }

    private fun divider() = View(context).apply { setBackgroundColor(theme.divider) }

    /** Suggestions are given best-first; the best goes in the middle slot like Gboard. */
    private fun slotToIndex(slot: Int) = when (slot) { 1 -> 0; 0 -> 1; else -> 2 }

    fun showToolbar() {
        current = emptyList()
        toolbar.visibility = View.VISIBLE
        suggestionRow.visibility = View.GONE
        searchText.visibility = View.GONE
        setLeft(Action.MORE)
        rightButton.visibility = View.VISIBLE
    }

    /**
     * Show [items] best-first. [boldFirst] marks the first as what the space bar will commit.
     * [left] is an optional item for the left slot (the typed word, when it would be corrected).
     */
    fun showSuggestions(items: List<String>, boldFirst: Boolean) {
        if (items.isEmpty()) { showToolbar(); return }
        current = items
        toolbar.visibility = View.GONE
        searchText.visibility = View.GONE
        suggestionRow.visibility = View.VISIBLE
        dividers.forEach { it.visibility = View.VISIBLE }
        setLeft(Action.MORE)
        slots.forEachIndexed { slot, tv ->
            val i = slotToIndex(slot)
            tv.text = items.getOrNull(i) ?: ""
            tv.typeface = if (i == 0 && boldFirst) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    fun showSearch(query: String, hint: String) {
        toolbar.visibility = View.GONE
        suggestionRow.visibility = View.GONE
        searchText.visibility = View.VISIBLE
        if (query.isEmpty()) {
            searchText.text = hint
            searchText.alpha = 0.6f
        } else {
            searchText.text = "$query|"
            searchText.alpha = 1f
        }
        setLeft(Action.KEYBOARD)
        rightButton.visibility = View.INVISIBLE
    }

    /** While a panel (emoji, GIFs...) covers the keys, the left button returns to the keyboard. */
    fun showPanelMode(title: String) {
        toolbar.visibility = View.GONE
        suggestionRow.visibility = View.VISIBLE
        searchText.visibility = View.GONE
        current = emptyList()
        dividers.forEach { it.visibility = View.GONE }
        slots.forEachIndexed { slot, tv ->
            tv.text = if (slot == 1) title else ""
            tv.typeface = Typeface.DEFAULT_BOLD
        }
        setLeft(Action.KEYBOARD)
        rightButton.visibility = View.VISIBLE
    }

    private fun setLeft(a: Action) {
        leftAction = a
        leftButton.background = IconDrawable(if (a == Action.KEYBOARD) Icon.BACK else Icon.GRID, theme.stripText, 0.5f)
        leftButton.contentDescription = a.title
    }

    companion object {
        /** Kept short like Gboard's; everything else lives in the ⊞ grid. */
        val TOOLBAR = listOf(Action.EMOJI, Action.GIF, Action.CLIPBOARD, Action.SETTINGS)
    }
}
