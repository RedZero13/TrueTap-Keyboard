package com.truetap.keyboard.ime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Recently copied text. Unpinned items expire after an hour, like Gboard. */
class ClipboardStore(context: Context) {
    class Clip(val text: String, var pinned: Boolean, val time: Long)

    private val sp = context.getSharedPreferences("truetap_clipboard", Context.MODE_PRIVATE)
    private val clips = ArrayList<Clip>()

    init {
        runCatching {
            val a = JSONArray(sp.getString("clips", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                clips.add(Clip(o.getString("t"), o.optBoolean("p"), o.optLong("at")))
            }
        }
    }

    fun add(text: String) {
        if (text.isBlank() || text.length > 5000) return
        val existing = clips.firstOrNull { it.text == text }
        val pinned = existing?.pinned ?: false
        clips.remove(existing)
        clips.add(0, Clip(text, pinned, System.currentTimeMillis()))
        while (clips.count { !it.pinned } > MAX) clips.remove(clips.last { !it.pinned })
        save()
    }

    fun items(): List<Clip> {
        val cutoff = System.currentTimeMillis() - TTL_MS
        if (clips.removeAll { !it.pinned && it.time < cutoff }) save()
        return clips.sortedByDescending { if (it.pinned) Long.MAX_VALUE else it.time }
    }

    fun togglePin(c: Clip) { c.pinned = !c.pinned; save() }

    fun clearUnpinned() { clips.removeAll { !it.pinned }; save() }

    private fun save() {
        val a = JSONArray()
        for (c in clips) a.put(JSONObject().put("t", c.text).put("p", c.pinned).put("at", c.time))
        sp.edit().putString("clips", a.toString()).apply()
    }

    companion object {
        const val MAX = 30
        const val TTL_MS = 60 * 60 * 1000L
    }
}
