package com.truetap.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.truetap.keyboard.Prefs
import com.truetap.keyboard.ime.panels.Ui

/**
 * Top-level view of the keyboard window. Arranges the keyboard "card" for the normal,
 * one-handed and floating modes, and hosts the preview overlay above everything.
 */
@SuppressLint("ViewConstructor")
class InputRoot(
    context: Context,
    val mode: String,
    private val prefs: Prefs,
    private val theme: KbTheme,
    private val onExitMode: () -> Unit,
    private val onSwitchSide: () -> Unit,
) : FrameLayout(context) {

    /** Suggestion strip + keys/panel, stacked. */
    val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val overlay = PreviewOverlay(context)
    val floating get() = mode == "floating"
    private val oneHanded get() = mode == "one_left" || mode == "one_right"
    private var navInset = 0
    private val screenW = resources.displayMetrics.widthPixels

    init {
        clipChildren = false
        when {
            floating -> {
                setBackgroundColor(Color.TRANSPARENT)
                card.setBackgroundColor(theme.background)
                card.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, Ui.dp(context, 16f).toFloat())
                    }
                }
                card.clipToOutline = true
                card.elevation = Ui.dp(context, 8f).toFloat()
                addView(card, LayoutParams((screenW * 0.86f).toInt(), LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
            }
            oneHanded -> {
                setBackgroundColor(theme.background)
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                val side = sidePanel()
                val cardW = (screenW * 0.8f).toInt()
                if (mode == "one_right") {
                    row.addView(side, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                    row.addView(card, LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT))
                } else {
                    row.addView(card, LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT))
                    row.addView(side, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                }
                addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            }
            else -> {
                setBackgroundColor(theme.background)
                addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            }
        }
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnApplyWindowInsetsListener { _, insets ->
            applyNavInset(insets)
            insets
        }
    }

    private fun applyNavInset(insets: WindowInsets) {
        // Keep the bottom row clear of the system's keyboard-hide / switcher bar.
        navInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        if (!floating) setPadding(0, 0, 0, navInset)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A rebuilt keyboard view doesn't get insets dispatched until something asks.
        rootWindowInsets?.let { applyNavInset(it) }
        requestApplyInsets()
    }

    private fun sidePanel(): View {
        val side = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val size = Ui.dp(context, 48f)
        val expand = IconButton(context, Icon.EXPAND, theme.text, 0.5f).apply {
            contentDescription = "Full-width keyboard"
            setOnClickListener { onExitMode() }
        }
        val move = IconButton(context, if (mode == "one_right") Icon.CHEVRON_LEFT else Icon.CHEVRON_RIGHT, theme.text, 0.55f).apply {
            contentDescription = "Move keyboard to other side"
            setOnClickListener { onSwitchSide() }
        }
        side.addView(expand, LinearLayout.LayoutParams(size, size))
        side.addView(move, LinearLayout.LayoutParams(size, size))
        return side
    }

    /** The draggable bar under a floating keyboard. Tap the expand icon to dock it again. */
    @SuppressLint("ClickableViewAccessibility")
    fun floatingHandle(): View {
        val bar = FrameLayout(context)
        bar.setBackgroundColor(theme.background)
        val pill = View(context).apply { background = Ui.rounded(theme.hint, Ui.dp(context, 3f).toFloat()) }
        bar.addView(pill, LayoutParams(Ui.dp(context, 48f), Ui.dp(context, 5f), Gravity.CENTER))
        val dock = IconButton(context, Icon.EXPAND, theme.text, 0.55f).apply {
            contentDescription = "Dock keyboard"
            setOnClickListener { onExitMode() }
        }
        bar.addView(dock, LayoutParams(Ui.dp(context, 40f), Ui.dp(context, 28f), Gravity.END or Gravity.CENTER_VERTICAL))
        var lastX = 0f
        var lastY = 0f
        bar.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    card.x = (card.x + e.rawX - lastX).coerceIn(0f, (width - card.width).toFloat().coerceAtLeast(0f))
                    card.y = (card.y + e.rawY - lastY).coerceIn(0f, (height - card.height).toFloat().coerceAtLeast(0f))
                    lastX = e.rawX; lastY = e.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.floatX = if (width > card.width) card.x / (width - card.width) else 0f
                    prefs.floatY = if (height > card.height) card.y / (height - card.height) else 0f
                }
            }
            true
        }
        return bar
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (floating && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            // Take the whole window so the card can be dragged anywhere; only the card is touchable.
            val h = MeasureSpec.getSize(heightMeasureSpec)
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (floating) {
            val maxX = (width - card.width).coerceAtLeast(0)
            val maxY = (height - card.height - navInset).coerceAtLeast(0)
            card.x = prefs.floatX.coerceIn(0f, 1f) * maxX
            card.y = prefs.floatY.coerceIn(0f, 1f) * maxY
        }
    }

    /** Card bounds in window coordinates, for the touchable region of a floating keyboard. */
    fun cardBoundsInWindow(out: Rect) {
        val loc = IntArray(2)
        card.getLocationInWindow(loc)
        out.set(loc[0], loc[1], loc[0] + card.width, loc[1] + card.height)
    }
}
