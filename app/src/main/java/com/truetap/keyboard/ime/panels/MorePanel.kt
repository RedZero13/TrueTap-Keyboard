package com.truetap.keyboard.ime.panels

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.truetap.keyboard.ime.Action
import com.truetap.keyboard.ime.IconDrawable
import com.truetap.keyboard.ime.KbTheme

/** Grid of every keyboard feature, like Gboard's expanded toolbar. */
@SuppressLint("ViewConstructor")
class MorePanel(context: Context, theme: KbTheme, actions: List<Action>, onAction: (Action) -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        setPadding(Ui.dp(context, 8f), Ui.dp(context, 8f), Ui.dp(context, 8f), Ui.dp(context, 8f))
        actions.chunked(4).forEach { rowActions ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (a in rowActions) {
                val tile = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    background = Ui.pressable(context, theme.key, Ui.dp(context, 14f).toFloat(), theme)
                    setOnClickListener { onAction(a) }
                }
                val icon = android.view.View(context).apply { background = IconDrawable(a.icon, theme.text, 0.75f) }
                tile.addView(icon, LayoutParams(Ui.dp(context, 30f), Ui.dp(context, 30f)))
                tile.addView(TextView(context).apply {
                    text = a.title
                    setTextColor(theme.text)
                    textSize = 12f
                    gravity = Gravity.CENTER
                })
                row.addView(tile, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(Ui.dp(context, 4f), Ui.dp(context, 4f), Ui.dp(context, 4f), Ui.dp(context, 4f))
                })
            }
            repeat(4 - rowActions.size) { row.addView(android.view.View(context), LayoutParams(0, 1, 1f)) }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}
