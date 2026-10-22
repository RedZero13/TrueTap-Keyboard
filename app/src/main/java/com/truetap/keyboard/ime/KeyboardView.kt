package com.truetap.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.truetap.keyboard.engine.KeyGeom
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Draws a [KeyboardLayout] and turns raw touches into key events.
 *
 * Letter taps are not resolved here: the view reports where the finger landed and asks the
 * [Listener] (backed by the typing engine) which letter it meant.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, var theme: KbTheme) : View(context) {

    class Key(val spec: KeySpec, val rect: RectF, val touch: RectF, val row: Int)

    interface Listener {
        /** Which letter key a tap meant; return null to use the key under the finger. */
        fun resolveLetter(x: Float, y: Float, size: Float, nearSpace: Boolean): Key?
        fun onLetter(key: Key, x: Float, y: Float, size: Float, nearSpace: Boolean)
        /** Any non-letter key. [nearSpace] is set for taps on the edge of '.' that touches the space bar. */
        fun onKey(key: Key, nearSpace: Boolean)
        fun onText(text: String)
        fun onPress(key: Key)
        fun onLongPress(key: Key): Boolean = false
        fun onCursorMove(steps: Int) {}
        fun onDeleteSwipe(words: Int) {}
        fun onDeleteSwipeEnd(words: Int) {}
        fun onGesture(trail: List<FloatArray>) {}
    }

    var listener: Listener? = null
    var overlay: PreviewOverlay? = null

    var layout: KeyboardLayout? = null
        set(v) {
            field = v
            cancelAll()
            requestLayout()
            buildKeys()
            invalidate()
        }

    var keyHeightPx: Float = dp(56f)
        set(v) { field = v; requestLayout() }

    /** 0 = lower case, 1 = shift, 2 = caps lock. */
    var shiftState = 0
        set(v) { if (field != v) { field = v; invalidate() } }

    var spaceLabel: String = ""
        set(v) { field = v; invalidate() }

    var showPreview = true
    var glideEnabled = true
    var showTouchPoints = false
        set(v) { field = v; if (!v) touchDots.clear(); invalidate() }

    var keys: List<Key> = emptyList()
        private set

    /** Bumped whenever key positions change, so the typing engine can rebuild its geometry. */
    var geometryVersion = 0
        private set

    /** Width of one key unit in px. */
    var unitPx = 1f
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val trailPath = Path()
    private val handler = Handler(Looper.getMainLooper())

    private val padTop = dp(4f)
    private val padBottom = dp(6f)
    private val padH = dp(3f)
    private val gapH = dp(5f)
    private val gapV = dp(9f)
    private val radius = dp(10f)
    private val labelFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private enum class Mode { NORMAL, LONG_PRESS, CURSOR, DELETE_SWIPE, GESTURE, DONE }

    private inner class Ptr(val id: Int, val key: Key, val downX: Float, val downY: Float, val size: Float, val nearSpace: Boolean) {
        var resolved: Key = key
        var mode = Mode.NORMAL
        var lastX = downX
        var cursorAcc = 0f
        var deleteWords = 0
        var repeated = false
        val trail = ArrayList<FloatArray>()
        val longPress = Runnable { onLongPressTimeout(this) }
        val repeat = object : Runnable {
            override fun run() {
                if (mode != Mode.NORMAL) return
                repeated = true
                listener?.onKey(key, false)
                handler.postDelayed(this, REPEAT_INTERVAL)
            }
        }
    }

    private val pointers = LinkedHashMap<Int, Ptr>()
    private var popupFor: Ptr? = null
    private var popupItems: List<String> = emptyList()

    private class Dot(val x: Float, val y: Float)
    private val touchDots = ArrayDeque<Dot>()

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout?.rowHeights?.sum() ?: 4f
        val h = (padTop + padBottom + rows * keyHeightPx).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        buildKeys()
    }

    private fun buildKeys() {
        val l = layout ?: run { keys = emptyList(); return }
        if (width == 0) return
        val out = ArrayList<Key>()
        unitPx = (width - 2 * padH) / l.unitsPerRow
        var y = padTop
        l.rows.forEachIndexed { r, row ->
            val h = keyHeightPx * l.rowHeights[r]
            val rowUnits = row.sumOf { it.width.toDouble() }.toFloat()
            var x = padH + (l.unitsPerRow - rowUnits) / 2f * unitPx
            row.forEachIndexed { i, spec ->
                val w = spec.width * unitPx
                val touch = RectF(x, y, x + w, y + h)
                // Touch cells tile the whole keyboard: edge keys own the margins, top/bottom rows the padding.
                if (i == 0) touch.left = 0f
                if (i == row.size - 1) touch.right = width.toFloat()
                if (r == 0) touch.top = 0f
                if (r == l.rows.size - 1) touch.bottom = height.toFloat().coerceAtLeast(y + h)
                val rect = RectF(x + gapH / 2, y + gapV / 2, x + w - gapH / 2, y + h - gapV / 2)
                out.add(Key(spec, rect, touch, r))
                x += w
            }
            y += h
        }
        keys = out
        geometryVersion++
    }

    /** Letter-key geometry for the typing engine (touch cell centres and unit size). */
    fun letterGeometry(): List<KeyGeom> = keys.filter { it.spec.type == KeyType.LETTER }.map {
        KeyGeom(it.spec.label[0], it.rect.centerX(), it.rect.centerY(), unitPx, it.touch.height())
    }

    fun keyAt(x: Float, y: Float): Key? {
        if (keys.isEmpty()) return null
        val l = layout ?: return null
        val rowCount = l.rows.size
        var row = rowCount - 1
        for (k in keys) if (y < k.touch.bottom) { row = k.row; break }
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (k in keys) {
            if (k.row != row) continue
            if (x >= k.touch.left && x < k.touch.right) return k
            val d = minOf(abs(x - k.touch.left), abs(x - k.touch.right))
            if (d < bestD) { bestD = d; best = k }
        }
        return best
    }

    private fun isNearSpace(key: Key, x: Float, y: Float): Boolean {
        val l = layout ?: return false
        if (!l.isAlphabetic) return false
        val bottomLetterRow = keys.filter { it.spec.type == KeyType.LETTER }.maxOfOrNull { it.row } ?: return false
        if (key.spec.type == KeyType.LETTER && key.row == bottomLetterRow) {
            // Lower part of c/v/b/n/m, right above the space bar.
            val space = keys.firstOrNull { it.spec.type == KeyType.SPACE } ?: return false
            return y > key.touch.bottom - key.touch.height() * 0.3f && x > space.touch.left - unitPx * 0.5f && x < space.touch.right + unitPx * 0.5f
        }
        if (key.spec.code == '.'.code) return x < key.touch.left + key.touch.width() * 0.35f
        return false
    }

    // ---------------------------------------------------------------- touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i), e.getTouchMajor(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = pointers[e.getPointerId(i)] ?: continue
                    for (h in 0 until e.historySize) move(p, e.getHistoricalX(i, h), e.getHistoricalY(i, h))
                    move(p, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                pointers.remove(e.getPointerId(i))?.let { up(it, e.getX(i), e.getY(i)) }
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float, size: Float) {
        // A palm or the side of a hand: ignore.
        if (size > unitPx * 3.5f) return
        val key = keyAt(x, y) ?: return
        // Fast typists press the next key before lifting the last one; commit the earlier key now
        // so letters stay in order.
        for (p in pointers.values.toList()) {
            if (p.mode == Mode.NORMAL && p.key.spec.code != Codes.SHIFT && p.key.spec.code != Codes.DELETE) {
                fire(p)
                p.mode = Mode.DONE
            }
        }
        val nearSpace = isNearSpace(key, x, y)
        val p = Ptr(id, key, x, y, size, nearSpace)
        if (key.spec.type == KeyType.LETTER) {
            p.resolved = listener?.resolveLetter(x, y, size, nearSpace) ?: key
        }
        pointers[id] = p
        p.trail.add(floatArrayOf(x, y))
        listener?.onPress(p.resolved)
        if (showTouchPoints) {
            touchDots.addLast(Dot(x, y))
            while (touchDots.size > 40) touchDots.removeFirst()
        }
        if (key.spec.code == Codes.SHIFT) {
            // Shift acts on press so it can be chorded with a letter.
            listener?.onKey(key, false)
            p.mode = Mode.DONE
        } else if (key.spec.code == Codes.DELETE) {
            handler.postDelayed(p.repeat, REPEAT_DELAY)
        } else {
            handler.postDelayed(p.longPress, LONG_PRESS_TIMEOUT)
        }
        showKeyPreview(p)
        invalidate()
    }

    private fun move(p: Ptr, x: Float, y: Float) {
        val dx = x - p.downX
        val dist = hypot(dx, y - p.downY)
        when (p.mode) {
            Mode.LONG_PRESS -> overlay?.selectAt(this, x, y)
            Mode.CURSOR -> {
                p.cursorAcc += x - p.lastX
                val step = unitPx * 0.45f
                val steps = (p.cursorAcc / step).toInt()
                if (steps != 0) {
                    p.cursorAcc -= steps * step
                    listener?.onCursorMove(steps)
                }
            }
            Mode.DELETE_SWIPE -> {
                val words = if (dx < -unitPx * 0.8f) 1 + ((-dx - unitPx * 0.8f) / unitPx).toInt() else 0
                if (words != p.deleteWords) {
                    p.deleteWords = words
                    listener?.onDeleteSwipe(words)
                }
            }
            Mode.GESTURE -> {
                p.trail.add(floatArrayOf(x, y))
                invalidate()
            }
            Mode.NORMAL -> {
                val spec = p.key.spec
                if (spec.type == KeyType.SPACE && abs(dx) > unitPx * 0.6f) {
                    p.mode = Mode.CURSOR
                    handler.removeCallbacks(p.longPress)
                    p.cursorAcc = dx
                    hidePreview()
                } else if (spec.code == Codes.DELETE && dx < -unitPx * 0.8f) {
                    p.mode = Mode.DELETE_SWIPE
                    handler.removeCallbacks(p.repeat)
                    move(p, x, y)
                } else if (spec.type == KeyType.LETTER && glideEnabled && pointers.size == 1 && dist > unitPx * 0.75f &&
                    layout?.isAlphabetic == true
                ) {
                    p.mode = Mode.GESTURE
                    handler.removeCallbacks(p.longPress)
                    hidePreview()
                    p.trail.add(floatArrayOf(x, y))
                    invalidate()
                } else {
                    p.trail.add(floatArrayOf(x, y))
                }
            }
            Mode.DONE -> {}
        }
        p.lastX = x
    }

    private fun up(p: Ptr, x: Float, y: Float) {
        handler.removeCallbacks(p.longPress)
        handler.removeCallbacks(p.repeat)
        when (p.mode) {
            Mode.NORMAL -> if (!p.repeated) fire(p)
            Mode.LONG_PRESS -> {
                val sel = overlay?.selectedIndex ?: -1
                if (sel in popupItems.indices) listener?.onText(popupItems[sel])
                overlay?.hideAlternates()
                popupFor = null
            }
            Mode.DELETE_SWIPE -> listener?.onDeleteSwipeEnd(p.deleteWords)
            Mode.GESTURE -> {
                p.trail.add(floatArrayOf(x, y))
                listener?.onGesture(p.trail)
            }
            Mode.CURSOR, Mode.DONE -> {}
        }
        p.mode = Mode.DONE
        if (pointers.isEmpty()) handler.postDelayed(hidePreviewRunnable, 60)
        invalidate()
    }

    private fun fire(p: Ptr) {
        if (p.key.spec.type == KeyType.LETTER) {
            listener?.onLetter(p.resolved, p.downX, p.downY, p.size, p.nearSpace)
        } else {
            listener?.onKey(p.key, p.nearSpace)
        }
    }

    private fun onLongPressTimeout(p: Ptr) {
        if (p.mode != Mode.NORMAL || pointers[p.id] !== p) return
        val key = p.resolved
        if (listener?.onLongPress(key) == true) {
            p.mode = Mode.DONE
            hidePreview()
            return
        }
        val alts = key.spec.alternates
        if (alts.isEmpty()) return
        popupItems = if (shiftState > 0 && key.spec.type == KeyType.LETTER) alts.map { it.uppercase() } else alts
        p.mode = Mode.LONG_PRESS
        popupFor = p
        hidePreview()
        overlay?.showAlternates(this, key.rect, popupItems, theme)
        listener?.onPress(key)
    }

    private fun cancelAll() {
        for (p in pointers.values) {
            handler.removeCallbacks(p.longPress)
            handler.removeCallbacks(p.repeat)
        }
        pointers.clear()
        overlay?.hideAlternates()
        hidePreview()
    }

    private val hidePreviewRunnable = Runnable { overlay?.hidePreview() }

    private fun hidePreview() {
        handler.removeCallbacks(hidePreviewRunnable)
        overlay?.hidePreview()
    }

    private fun showKeyPreview(p: Ptr) {
        handler.removeCallbacks(hidePreviewRunnable)
        val spec = p.resolved.spec
        if (!showPreview || (spec.type != KeyType.LETTER && spec.type != KeyType.CHAR)) {
            overlay?.hidePreview()
            return
        }
        overlay?.showPreview(this, p.resolved.rect, labelFor(spec), theme)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAll()
    }

    // ---------------------------------------------------------------- drawing

    private fun labelFor(spec: KeySpec): String =
        if (spec.type == KeyType.LETTER && shiftState > 0) spec.label.uppercase() else spec.label

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val scale = keyHeightPx / dp(56f)
        val pressed = pointers.values.filter { it.mode == Mode.NORMAL }.map { it.resolved }.toSet()
        for (k in keys) {
            val spec = k.spec
            val bg = when {
                k in pressed -> theme.keyPressed
                spec.code == Codes.SHIFT && shiftState == 2 -> theme.accent
                spec.type == KeyType.FUNCTION -> theme.keyFunction
                else -> theme.key
            }
            paint.color = bg
            paint.style = Paint.Style.FILL
            val pill = spec.type == KeyType.ENTER || spec.code == Codes.SYMBOLS || spec.code == Codes.ALPHA
            val r = if (pill) k.rect.height() / 2 else radius
            canvas.drawRoundRect(k.rect, r, r, paint)

            val fg = if (spec.code == Codes.SHIFT && shiftState == 2) theme.onAccent else theme.text
            val icon = when {
                spec.code == Codes.SHIFT -> when (shiftState) { 1 -> Icon.SHIFT_ON; 2 -> Icon.SHIFT_LOCK; else -> Icon.SHIFT }
                else -> spec.icon
            }
            if (icon != null) {
                Icons.draw(canvas, icon, k.rect.centerX(), k.rect.centerY(), dp(22f) * scale.coerceAtMost(1.15f), fg, paint)
                continue
            }
            if (spec.type == KeyType.SPACE) {
                textPaint.color = theme.text
                textPaint.textSize = sp(15f)
                textPaint.typeface = Typeface.DEFAULT
                drawCentered(canvas, spaceLabel, k.rect.centerX(), k.rect.centerY())
                continue
            }
            val label = labelFor(spec)
            textPaint.color = fg
            textPaint.typeface = labelFace
            textPaint.textSize = when {
                spec.type == KeyType.FUNCTION -> sp(15f)
                label.length > 1 -> sp(16f)
                k.row == 0 && layout?.rowHeights?.firstOrNull() == Layouts.NUMBER_ROW_HEIGHT && layout?.isAlphabetic == true -> sp(18f) * scale
                else -> sp(22f) * scale
            }
            drawCentered(canvas, label, k.rect.centerX(), k.rect.centerY())
            spec.hint?.let {
                textPaint.color = theme.hint
                textPaint.textSize = sp(10f)
                canvas.drawText(it, k.rect.right - dp(7f), k.rect.top + dp(12f), textPaint)
            }
        }

        if (showTouchPoints && touchDots.isNotEmpty()) {
            paint.style = Paint.Style.FILL
            var a = 60
            for (d in touchDots) {
                paint.color = theme.touchDot
                paint.alpha = a.coerceAtMost(220)
                canvas.drawCircle(d.x, d.y, dp(4f), paint)
                a += 5
            }
            paint.alpha = 255
        }

        val g = pointers.values.firstOrNull { it.mode == Mode.GESTURE }
        if (g != null && g.trail.size > 1) {
            trailPath.reset()
            trailPath.moveTo(g.trail[0][0], g.trail[0][1])
            for (i in 1 until g.trail.size) trailPath.lineTo(g.trail[i][0], g.trail[i][1])
            trailPaint.color = theme.accent
            trailPaint.alpha = 200
            trailPaint.strokeWidth = dp(6f)
            canvas.drawPath(trailPath, trailPaint)
        }
    }

    private fun drawCentered(canvas: Canvas, text: String, cx: Float, cy: Float) {
        val fm = textPaint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    companion object {
        const val LONG_PRESS_TIMEOUT = 350L
        const val REPEAT_DELAY = 400L
        const val REPEAT_INTERVAL = 50L
    }
}
