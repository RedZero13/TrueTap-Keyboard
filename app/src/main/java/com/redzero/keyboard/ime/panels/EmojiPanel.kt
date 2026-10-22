package com.redzero.keyboard.ime.panels

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.redzero.keyboard.ime.KbTheme

/** Emoji picker with recents and category tabs. */
@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val theme: KbTheme,
    private val recents: () -> List<String>,
    private val onEmoji: (String) -> Unit,
    onDelete: () -> Unit,
    onAbc: () -> Unit,
) : LinearLayout(context) {

    private val grid = GridView(context)
    private val tabs = LinearLayout(context)
    private var items: List<String> = emptyList()
    private var selectedTab = 0

    private val tabNames: List<Pair<String, () -> List<String>>> =
        listOf<Pair<String, () -> List<String>>>("🕘" to recents) + EmojiData.categories.map { c -> c.icon to { c.emoji } }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 50f))
            }
            tv.text = items[position]
            return tv
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        grid.numColumns = 8
        grid.adapter = adapter
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.selector = Ui.rounded(theme.keyPressed, Ui.dp(context, 8f).toFloat())
        grid.setOnItemClickListener { _, _, pos, _ -> onEmoji(items[pos]) }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        tabs.orientation = HORIZONTAL
        tabs.gravity = Gravity.CENTER_VERTICAL
        tabNames.forEachIndexed { i, (icon, _) ->
            val t = TextView(context).apply {
                text = icon
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setOnClickListener { select(i) }
            }
            tabs.addView(t, LayoutParams(0, Ui.dp(context, 40f), 1f))
        }
        addView(Ui.bottomBar(context, theme, onAbc, tabs, onDelete), LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 46f)))
        select(if (recents().isEmpty()) 1 else 0)
    }

    private fun select(i: Int) {
        selectedTab = i
        items = tabNames[i].second()
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        for (t in 0 until tabs.childCount) {
            tabs.getChildAt(t).background = if (t == i) Ui.rounded(theme.keyFunction, Ui.dp(context, 10f).toFloat()) else null
        }
    }
}
