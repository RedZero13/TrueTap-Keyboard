package com.redzero.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * Transparent layer above the whole keyboard that draws key previews and long-press popups,
 * so they can extend above the key rows (over the suggestion strip).
 */
class PreviewOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var theme: KbTheme = KbTheme.LIGHT

    private var previewRect: RectF? = null
    private var previewLabel = ""

    private var popupRect: RectF? = null
    private var popupItems: List<String> = emptyList()
    private var cellW = 0f
    private var cellH = 0f
    private var perRow = 1
    var selectedIndex = -1
        private set

    private val loc = IntArray(2)
    private val myLoc = IntArray(2)

    init {
        isClickable = false
        isFocusable = false
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    /** Translate a rect from [source]'s coordinates into ours. */
    private fun toLocal(source: View, r: RectF): RectF {
        source.getLocationInWindow(loc)
        getLocationInWindow(myLoc)
        val dx = (loc[0] - myLoc[0]).toFloat()
        val dy = (loc[1] - myLoc[1]).toFloat()
        return RectF(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
    }

    fun showPreview(source: View, keyRect: RectF, label: String, theme: KbTheme) {
        this.theme = theme
        val k = toLocal(source, keyRect)
        val w = maxOf(k.width() * 1.25f, dp(44f))
        val h = k.height() * 1.1f
        val bottom = k.top - dp(4f)
        previewRect = RectF(k.centerX() - w / 2, (bottom - h).coerceAtLeast(0f), k.centerX() + w / 2, bottom)
        previewLabel = label
        invalidate()
    }

    fun hidePreview() {
        if (previewRect != null) {
            previewRect = null
            invalidate()
        }
    }

    fun showAlternates(source: View, keyRect: RectF, items: List<String>, theme: KbTheme) {
        this.theme = theme
        val k = toLocal(source, keyRect)
        cellW = maxOf(k.width(), dp(38f))
        cellH = k.height() * 1.05f
        perRow = maxOf(1, minOf(items.size, ((width - dp(8f)) / cellW).toInt()))
        val rows = (items.size + perRow - 1) / perRow
        val total = cellW * perRow
        var left = k.centerX() - cellW / 2
        // Lay the popup out to the left when the key is near the right edge.
        if (left + total > width - dp(4f)) left = (width - dp(4f) - total)
        left = left.coerceAtLeast(dp(4f))
        val bottom = k.top - dp(4f)
        popupRect = RectF(left, (bottom - cellH * rows).coerceAtLeast(0f), left + total, bottom)
        popupItems = items
        // Start on the item over the key, which is the first one unless we shifted left.
        selectedIndex = ((k.centerX() - left) / cellW).toInt().coerceIn(0, minOf(perRow, items.size) - 1)
        invalidate()
    }

    /** Item rectangles: row 0 sits nearest the finger (bottom), further rows stack upwards. */
    private fun cell(r: RectF, i: Int): RectF {
        val row = i / perRow
        val col = i % perRow
        val bottom = r.bottom - row * cellH
        return RectF(r.left + col * cellW, bottom - cellH, r.left + (col + 1) * cellW, bottom)
    }

    /** Update the highlighted alternate from a finger position in [source]'s coordinates. */
    fun selectAt(source: View, x: Float, y: Float) {
        val r = popupRect ?: return
        val p = toLocal(source, RectF(x, y, x, y))
        var newSel = -1
        if (p.left >= r.left - cellW && p.left <= r.right + cellW) {
            val col = ((p.left - r.left) / cellW).toInt().coerceIn(0, perRow - 1)
            // Finger below the popup still selects from the nearest row.
            val row = ((r.bottom - p.top) / cellH).toInt().coerceIn(0, (popupItems.size - 1) / perRow)
            newSel = (row * perRow + col).coerceAtMost(popupItems.size - 1)
        }
        if (newSel != selectedIndex) {
            selectedIndex = newSel
            invalidate()
        }
    }

    fun hideAlternates() {
        if (popupRect != null) {
            popupRect = null
            popupItems = emptyList()
            selectedIndex = -1
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val radius = dp(10f)
        previewRect?.let { r ->
            paint.color = 0x33000000
            canvas.drawRoundRect(RectF(r.left, r.top + dp(1.5f), r.right, r.bottom + dp(1.5f)), radius, radius, paint)
            paint.color = theme.preview
            canvas.drawRoundRect(r, radius, radius, paint)
            textPaint.color = theme.text
            textPaint.textSize = r.height() * 0.55f
            val fm = textPaint.fontMetrics
            canvas.drawText(previewLabel, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
        }
        popupRect?.let { r ->
            paint.color = 0x33000000
            canvas.drawRoundRect(RectF(r.left, r.top + dp(1.5f), r.right, r.bottom + dp(1.5f)), radius, radius, paint)
            paint.color = theme.preview
            canvas.drawRoundRect(r, radius, radius, paint)
            val textBase = cellH
            popupItems.forEachIndexed { i, s ->
                val cell = cell(r, i)
                if (i == selectedIndex) {
                    paint.color = theme.accent
                    canvas.drawRoundRect(RectF(cell.left + dp(3f), cell.top + dp(3f), cell.right - dp(3f), cell.bottom - dp(3f)), radius * 0.7f, radius * 0.7f, paint)
                }
                textPaint.color = if (i == selectedIndex) theme.onAccent else theme.text
                textPaint.textSize = if (s.length > 2) textBase * 0.3f else textBase * 0.45f
                val fm = textPaint.fontMetrics
                canvas.drawText(s, cell.centerX(), cell.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
            }
        }
    }
}
