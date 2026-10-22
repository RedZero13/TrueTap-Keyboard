package com.redzero.keyboard.ime.panels

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.redzero.keyboard.ime.ClipboardStore
import com.redzero.keyboard.ime.KbTheme

/** Recent copies (kept for an hour unless pinned). Tap to paste, long-press to pin or unpin. */
@SuppressLint("ViewConstructor")
class ClipboardPanel(
    context: Context,
    private val theme: KbTheme,
    private val store: ClipboardStore,
    private val enabled: Boolean,
    private val onPaste: (String) -> Unit,
    onAbc: () -> Unit,
) : LinearLayout(context) {

    private val list = LinearLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val scroll = ScrollView(context)
        list.orientation = VERTICAL
        list.setPadding(Ui.dp(context, 8f), Ui.dp(context, 6f), Ui.dp(context, 8f), Ui.dp(context, 6f))
        scroll.addView(list)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val clear = Ui.chip(context, "Clear unpinned", theme)
        clear.setOnClickListener { store.clearUnpinned(); render() }
        val middle = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(clear)
        }
        addView(Ui.bottomBar(context, theme, onAbc, middle, null), LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 46f)))
        render()
    }

    private fun render() {
        list.removeAllViews()
        val clips = store.items()
        if (!enabled || clips.isEmpty()) {
            list.addView(Ui.label(context, if (!enabled) "Clipboard history is off. Turn it on in RedZero settings." else "Copied text shows up here for an hour. Long-press an item to pin it.", theme).apply {
                setTextColor(theme.hint)
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(context, 40f), 0, 0)
            })
            return
        }
        for (clip in clips) {
            val tv = TextView(context).apply {
                text = (if (clip.pinned) "📌 " else "") + clip.text
                setTextColor(theme.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(Ui.dp(context, 12f), Ui.dp(context, 10f), Ui.dp(context, 12f), Ui.dp(context, 10f))
                background = Ui.pressable(context, theme.key, Ui.dp(context, 10f).toFloat(), theme)
                setOnClickListener { onPaste(clip.text) }
                setOnLongClickListener { store.togglePin(clip); render(); true }
            }
            list.addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = Ui.dp(context, 6f) })
        }
    }
}
