package com.truetap.keyboard.ime.panels

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.truetap.keyboard.ime.KbTheme
import com.truetap.keyboard.net.Giphy
import com.truetap.keyboard.net.ImageLoader
import com.truetap.keyboard.net.Net

/** GIF or sticker browser backed by GIPHY: trending, quick categories and search. */
@SuppressLint("ViewConstructor")
class MediaPanel(
    context: Context,
    private val theme: KbTheme,
    private val kind: Giphy.Kind,
    private val giphy: Giphy?,
    private val images: ImageLoader,
    private val lang: String,
    initialQuery: String,
    private val onPick: (Giphy.Item) -> Unit,
    private val onSearch: () -> Unit,
    onAbc: () -> Unit,
) : LinearLayout(context) {

    private val chips = LinearLayout(context)
    private val grid = GridView(context)
    private val status = TextView(context)
    private val items = ArrayList<Giphy.Item>()
    private var query = initialQuery
    private var loading = false
    private var exhausted = false
    private var generation = 0
    private val cellPx = Ui.dp(context, if (kind == Giphy.Kind.GIFS) 112f else 96f)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val iv = (convertView as? ImageView) ?: ImageView(context).apply {
                scaleType = if (kind == Giphy.Kind.GIFS) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellPx)
                background = Ui.rounded(theme.key, Ui.dp(context, 6f).toFloat())
                clipToOutline = true
            }
            val item = items[position]
            iv.contentDescription = item.title
            images.load(item.previewUrl, iv, cellPx * 2)
            if (position >= items.size - 6) loadMore()
            return iv
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        val scroller = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        chips.orientation = HORIZONTAL
        chips.setPadding(Ui.dp(context, 6f), Ui.dp(context, 6f), Ui.dp(context, 6f), Ui.dp(context, 6f))
        scroller.addView(chips)
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        buildChips()

        val body = FrameLayout(context)
        grid.numColumns = if (kind == Giphy.Kind.GIFS) 2 else 3
        grid.horizontalSpacing = Ui.dp(context, 4f)
        grid.verticalSpacing = Ui.dp(context, 4f)
        grid.setPadding(Ui.dp(context, 4f), 0, Ui.dp(context, 4f), 0)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> onPick(items[pos]) }
        body.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        status.setTextColor(theme.hint)
        status.gravity = Gravity.CENTER
        status.setPadding(Ui.dp(context, 24f), 0, Ui.dp(context, 24f), 0)
        body.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val attribution = TextView(context).apply {
            text = "Powered by GIPHY"
            setTextColor(theme.hint)
            textSize = 11f
            gravity = Gravity.CENTER
        }
        addView(Ui.bottomBar(context, theme, onAbc, attribution, null), LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 46f)))
        reload()
    }

    private fun buildChips() {
        chips.removeAllViews()
        val search = Ui.chip(context, if (query.isNotEmpty()) "🔍 $query" else "🔍 Search", theme, query.isNotEmpty())
        search.setOnClickListener { onSearch() }
        chips.addView(search, chipParams())
        val trending = Ui.chip(context, "Trending", theme, query.isEmpty())
        trending.setOnClickListener { setQuery("") }
        chips.addView(trending, chipParams())
        for (c in QUICK) {
            val chip = Ui.chip(context, c, theme, query == c)
            chip.setOnClickListener { setQuery(c) }
            chips.addView(chip, chipParams())
        }
    }

    private fun chipParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(context, 6f) }

    fun setQuery(q: String) {
        query = q
        buildChips()
        reload()
    }

    private fun reload() {
        generation++
        items.clear()
        exhausted = false
        loading = false
        adapter.notifyDataSetChanged()
        if (giphy == null) {
            showStatus("Add a free GIPHY API key in TrueTap settings to search ${if (kind == Giphy.Kind.GIFS) "GIFs" else "stickers"}.\n(developers.giphy.com → Create an App)")
            return
        }
        loadMore()
    }

    private fun loadMore() {
        val g = giphy ?: return
        if (loading || exhausted) return
        loading = true
        if (items.isEmpty()) showStatus("Loading…")
        val gen = generation
        val offset = items.size
        Net.async({ g.fetch(kind, query, offset, lang) }) { r ->
            if (gen != generation) return@async
            loading = false
            r.onSuccess { page ->
                if (page.isEmpty()) exhausted = true
                items.addAll(page)
                adapter.notifyDataSetChanged()
                showStatus(if (items.isEmpty()) "Nothing found" else null)
            }.onFailure {
                showStatus("Couldn't reach GIPHY. Check your connection or API key.")
            }
        }
    }

    private fun showStatus(text: String?) {
        status.text = text ?: ""
        status.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    companion object {
        val QUICK = listOf("haha", "love", "yes", "no", "thank you", "omg", "sad", "happy birthday", "hello", "wow", "dance", "congrats")
    }
}
