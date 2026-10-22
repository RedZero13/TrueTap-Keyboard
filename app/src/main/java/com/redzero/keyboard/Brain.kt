package com.redzero.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.redzero.keyboard.engine.Dictionary
import com.redzero.keyboard.engine.KeyGeom
import com.redzero.keyboard.engine.SpatialModel
import com.redzero.keyboard.engine.TouchModel
import com.redzero.keyboard.engine.TypingEngine
import com.redzero.keyboard.engine.UserHistory
import java.io.File
import java.util.concurrent.Executors

/**
 * Process-wide owner of dictionaries and everything learned about the user (touch offsets per
 * layout, word history). Shared by the keyboard service, calibration and settings screens.
 * All mutation happens on the main thread; disk I/O on a background thread.
 */
object Brain {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "redzero-brain").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private val dictionaries = HashMap<String, Dictionary>()
    private val loading = HashMap<String, MutableList<(Dictionary) -> Unit>>()
    private val histories = HashMap<String, UserHistory>()
    private val userDictionaries = HashMap<String, Dictionary>()
    private val touchModels = HashMap<String, TouchModel>()
    private val dirtyTouch = HashSet<String>()

    /** Calls [ready] on the main thread once the language's dictionary is loaded (immediately if cached). */
    fun withDictionary(context: Context, lang: String, ready: (Dictionary) -> Unit) {
        dictionaries[lang]?.let { ready(it); return }
        val waiting = loading[lang]
        if (waiting != null) { waiting.add(ready); return }
        loading[lang] = mutableListOf(ready)
        val app = context.applicationContext
        io.execute {
            val dict = runCatching {
                app.assets.open("dict/$lang.txt").bufferedReader().use { Dictionary.load(it) }
            }.getOrElse { Dictionary.build(emptyList()) }
            main.post {
                dictionaries[lang] = dict
                loading.remove(lang)?.forEach { it(dict) }
            }
        }
    }

    fun dictionaryIfLoaded(lang: String): Dictionary? = dictionaries[lang]

    fun history(context: Context, lang: String): UserHistory = histories.getOrPut(lang) {
        UserHistory().apply { load(read(context, "history_$lang.txt")) }
    }

    /** Small dictionary of words this user added or kept typing; rebuilt when it changes. */
    fun userDictionary(context: Context, lang: String): Dictionary =
        userDictionaries.getOrPut(lang) { Dictionary.build(history(context, lang).userWords().map { it.key to it.value }) }

    fun invalidateUserDictionary(lang: String) {
        userDictionaries.remove(lang)
    }

    fun touchModel(context: Context, layoutId: String): TouchModel = touchModels.getOrPut(layoutId) {
        TouchModel().apply { load(read(context, "touch_$layoutId.txt")) }
    }

    fun markTouchDirty(layoutId: String) {
        dirtyTouch.add(layoutId)
    }

    fun engine(context: Context, lang: String, layoutId: String, geometry: List<KeyGeom>, dict: Dictionary): TypingEngine {
        val spatial = SpatialModel(geometry, touchModel(context, layoutId))
        return TypingEngine(spatial, dict, history(context, lang), userDictionary(context, lang))
    }

    fun totalLearnedTaps(context: Context): Int =
        Languages.all.map { it.layout }.distinct().sumOf { touchModel(context, it).samples }

    fun save(context: Context) {
        val writes = ArrayList<Pair<String, String>>()
        for ((lang, h) in histories) if (h.changed) writes.add("history_$lang.txt" to h.serialize())
        for (id in dirtyTouch) touchModels[id]?.let { writes.add("touch_$id.txt" to it.serialize()) }
        dirtyTouch.clear()
        if (writes.isEmpty()) return
        val dir = context.applicationContext.filesDir
        io.execute { for ((name, data) in writes) writeAtomic(File(dir, name), data) }
    }

    fun resetTouchLearning(context: Context) {
        for (m in touchModels.values) m.reset()
        val dir = context.applicationContext.filesDir
        io.execute { dir.listFiles { f -> f.name.startsWith("touch_") }?.forEach { it.delete() } }
    }

    fun clearHistory(context: Context) {
        for (h in histories.values) h.clear()
        userDictionaries.clear()
        val dir = context.applicationContext.filesDir
        io.execute { dir.listFiles { f -> f.name.startsWith("history_") }?.forEach { it.delete() } }
    }

    private fun read(context: Context, name: String): String? {
        val f = File(context.applicationContext.filesDir, name)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    private fun writeAtomic(f: File, data: String) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(data)
        tmp.renameTo(f)
    }
}
