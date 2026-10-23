package com.truetap.keyboard.ime

import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Debug builds only: logs the on-screen position of every key and labelled control so the
 * screenshot script (tools/screenshots.sh) can drive the real keyboard with real taps.
 */
object DebugDump {
    private const val TAG = "TTDUMP"
    private val loc = IntArray(2)

    fun dump(root: View, kb: KeyboardView) {
        walk(root)
        if (!kb.isShown) return
        kb.getLocationOnScreen(loc)
        for (k in kb.keys) {
            val name = when (k.spec.code) {
                Codes.SHIFT -> "shift"
                Codes.DELETE -> "delete"
                Codes.SPACE -> "space"
                Codes.ENTER -> "enter"
                Codes.LANGUAGE -> "globe"
                Codes.EMOJI -> "emojikey"
                else -> k.spec.label
            }
            Log.i(TAG, "key|$name|${loc[0] + k.rect.centerX().toInt()}|${loc[1] + k.rect.centerY().toInt()}|${kb.unitPx.toInt()}")
        }
    }

    private fun walk(v: View) {
        if (!v.isShown) return
        val desc = v.contentDescription
        if (desc != null && v.width > 0) {
            v.getLocationOnScreen(loc)
            Log.i(TAG, "view|$desc|${loc[0] + v.width / 2}|${loc[1] + v.height / 2}|${v.width}")
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }
}
