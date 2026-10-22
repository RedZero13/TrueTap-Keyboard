package com.truetap.keyboard.net

import org.json.JSONArray

/**
 * Definitions from dictionaryapi.dev and synonyms from Datamuse. Both are free and keyless.
 * Calls block; run them from a background thread.
 */
object Lookup {
    data class Sense(val partOfSpeech: String, val definition: String, val example: String?)

    fun define(word: String, lang: String): List<Sense> {
        val code = if (lang in setOf("en", "es", "fr", "de", "it", "pt")) lang else "en"
        val json = JSONArray(Net.getText("https://api.dictionaryapi.dev/api/v2/entries/$code/${Net.enc(word.lowercase())}"))
        val out = ArrayList<Sense>()
        for (i in 0 until json.length()) {
            val meanings = json.getJSONObject(i).optJSONArray("meanings") ?: continue
            for (m in 0 until meanings.length()) {
                val meaning = meanings.getJSONObject(m)
                val pos = meaning.optString("partOfSpeech")
                val defs = meaning.optJSONArray("definitions") ?: continue
                for (d in 0 until minOf(defs.length(), 3)) {
                    val def = defs.getJSONObject(d)
                    out.add(Sense(pos, def.optString("definition"), def.optString("example").takeIf { it.isNotBlank() }))
                }
            }
        }
        return out
    }

    /** Synonyms first, then "means like" words to fill out short lists. */
    fun synonyms(word: String, lang: String): List<String> {
        val v = if (lang == "es") "&v=es" else ""
        val out = LinkedHashSet<String>()
        for (rel in listOf("rel_syn", "ml")) {
            val json = JSONArray(Net.getText("https://api.datamuse.com/words?$rel=${Net.enc(word.lowercase())}&max=24$v"))
            for (i in 0 until json.length()) {
                val w = json.getJSONObject(i).optString("word")
                if (w.isNotBlank() && !w.equals(word, ignoreCase = true)) out.add(w)
            }
            if (out.size >= 12) break
        }
        return out.take(24)
    }
}
