package com.truetap.keyboard.ime

import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.io.File

/**
 * Debug builds only: records the on-screen position of every key and labelled control so the
 * screenshot script (tools/screenshots.sh) can drive the real keyboard with real taps. The latest
 * snapshot is written to files/ttdump.txt (read with `run-as`) and also logged under TTDUMP.
 */
object DebugDump {
    private const val TAG = "TTDUMP"
    private val loc = IntArray(2)

    fun dump(root: View, kb: KeyboardView) {
        val out = StringBuilder()
        walk(root, out)
        if (kb.isShown) {
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
                out.append("key|$name|${loc[0] + k.rect.centerX().toInt()}|${loc[1] + k.rect.centerY().toInt()}|${kb.unitPx.toInt()}\n")
            }
        }
        val text = out.toString()
        runCatching { File(root.context.filesDir, "ttdump.txt").writeText(text) }
        text.lineSequence().filter { it.isNotEmpty() }.forEach { Log.i(TAG, it) }
    }

    private fun walk(v: View, out: StringBuilder) {
        if (!v.isShown) return
        val desc = v.contentDescription
        if (desc != null && v.width > 0) {
            v.getLocationOnScreen(loc)
            out.append("view|$desc|${loc[0] + v.width / 2}|${loc[1] + v.height / 2}|${v.width}\n")
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), out)
    }
}
