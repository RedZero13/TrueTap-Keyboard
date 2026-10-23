package com.truetap.keyboard.demo

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A plain chat screen to show the keyboard in context for screenshots. Debug builds only. */
class DemoActivity : Activity() {
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) Color.parseColor("#121318") else Color.parseColor("#F7F7FB")
        val fg = if (dark) Color.parseColor("#E4E4EA") else Color.parseColor("#1B1B21")
        val them = if (dark) Color.parseColor("#2A2C36") else Color.WHITE
        val me = Color.parseColor("#3B4BDB")

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsets.CONSUMED
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(12f).toInt())
        }
        header.addView(TextView(this).apply {
            text = "A"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF6D3A")) }
        }, LinearLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt()))
        header.addView(TextView(this).apply {
            text = "Alex"
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12f).toInt(), 0, 0, 0)
        })
        root.addView(header)

        val thread = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(12f).toInt(), 0, dp(12f).toInt(), dp(8f).toInt())
        }
        fun bubble(text: String, mine: Boolean) {
            thread.addView(TextView(this).apply {
                this.text = text
                setTextColor(if (mine) Color.WHITE else fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(14f).toInt(), dp(10f).toInt(), dp(14f).toInt(), dp(10f).toInt())
                background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(if (mine) me else them) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (mine) Gravity.END else Gravity.START
                topMargin = dp(6f).toInt()
            })
        }
        bubble("Are you still coming tonight? 🎉", false)
        bubble("Yes! Just leaving work now", true)
        bubble("Great, the table is booked for 8", false)
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(thread) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val input = EditText(this).apply {
            hint = "Message"
            setTextColor(fg)
            setHintTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 4
            background = GradientDrawable().apply { cornerRadius = dp(24f); setColor(them) }
            setPadding(dp(18f).toInt(), dp(12f).toInt(), dp(18f).toInt(), dp(12f).toInt())
        }
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(10f).toInt(), dp(4f).toInt(), dp(10f).toInt(), dp(8f).toInt())
        })
        setContentView(root)
        input.requestFocus()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        var current: DemoActivity? = null
    }
}
