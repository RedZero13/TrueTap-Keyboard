package com.redzero.keyboard.engine

import kotlin.math.ln
import kotlin.math.min

/**
 * What this user actually writes: word counts, word pairs, and words they insisted on.
 *
 * Feeds personalised boosts into the [Decoder], next-word predictions after a space, and a small
 * user dictionary for words the main list doesn't know (names, slang).
 */
class UserHistory {
    private val unigrams = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    /** Unknown words typed literally; promoted to the user dictionary on the second use. */
    private val pending = HashMap<String, Int>()
    private val userWords = LinkedHashMap<String, Int>()
    /** Corrections the user undid: (typed -> rejected correction). */
    private val rejected = HashMap<String, MutableSet<String>>()
    var changed = false
        private set

    fun record(prev: String?, word: String) {
        val w = word.lowercase()
        unigrams[w] = (unigrams[w] ?: 0) + 1
        if (prev != null) {
            val m = bigrams.getOrPut(prev.lowercase()) { HashMap() }
            m[w] = (m[w] ?: 0) + 1
        }
        trim()
        changed = true
    }

    /** Called when a word not in any dictionary was committed as typed. Returns true if it was promoted. */
    fun recordUnknown(word: String): Boolean {
        if (word.length < 2 || word.length > 30) return false
        val c = (pending[word] ?: 0) + 1
        changed = true
        if (c >= 2) {
            pending.remove(word)
            addUserWord(word)
            return true
        }
        pending[word] = c
        return false
    }

    fun addUserWord(word: String) {
        userWords[word] = 200
        changed = true
    }

    fun removeUserWord(word: String) {
        userWords.remove(word)
        changed = true
    }

    fun userWords(): Map<String, Int> = userWords

    fun rejectCorrection(typed: String, correction: String) {
        rejected.getOrPut(typed.lowercase()) { HashSet() }.add(correction.lowercase())
        changed = true
    }

    fun isRejected(typed: String, correction: String): Boolean =
        rejected[typed.lowercase()]?.contains(correction.lowercase()) == true

    /** Log-score bonus for [word] following [prev] in this user's writing. */
    fun boost(prev: String?, word: String): Float {
        val w = word.lowercase()
        var b = 0f
        unigrams[w]?.let { b += min(0.25f * ln(1f + it), 1.2f) }
        if (prev != null) bigrams[prev.lowercase()]?.get(w)?.let { b += min(0.9f * ln(1f + it), 3f) }
        return b
    }

    /** Most likely words to follow [prev], best first. */
    fun predictions(prev: String?, limit: Int): List<String> {
        if (prev == null) return emptyList()
        return bigrams[prev.lowercase()]?.entries?.sortedByDescending { it.value }?.take(limit)?.map { it.key } ?: emptyList()
    }

    private fun trim() {
        if (unigrams.size > MAX_UNIGRAMS) {
            val keep = unigrams.entries.sortedByDescending { it.value }.take(MAX_UNIGRAMS * 3 / 4)
            unigrams.clear(); keep.forEach { unigrams[it.key] = it.value }
        }
        if (bigrams.size > MAX_BIGRAM_HEADS) {
            val keep = bigrams.entries.sortedByDescending { e -> e.value.values.sum() }.take(MAX_BIGRAM_HEADS * 3 / 4)
            bigrams.clear(); keep.forEach { bigrams[it.key] = it.value }
        }
    }

    fun serialize(): String = buildString {
        append("v1\n")
        for ((w, c) in unigrams) append("u\t").append(w).append('\t').append(c).append('\n')
        for ((p, m) in bigrams) for ((w, c) in m) append("b\t").append(p).append('\t').append(w).append('\t').append(c).append('\n')
        for ((w, c) in pending) append("p\t").append(w).append('\t').append(c).append('\n')
        for ((w, c) in userWords) append("w\t").append(w).append('\t').append(c).append('\n')
        for ((t, s) in rejected) for (r in s) append("r\t").append(t).append('\t').append(r).append('\n')
        changed = false
    }

    fun load(data: String?) {
        unigrams.clear(); bigrams.clear(); pending.clear(); userWords.clear(); rejected.clear()
        if (data == null || !data.startsWith("v1")) return
        for (line in data.lineSequence()) {
            val f = line.split('\t')
            when (f[0]) {
                "u" -> if (f.size == 3) unigrams[f[1]] = f[2].toIntOrNull() ?: 1
                "b" -> if (f.size == 4) bigrams.getOrPut(f[1]) { HashMap() }[f[2]] = f[3].toIntOrNull() ?: 1
                "p" -> if (f.size == 3) pending[f[1]] = f[2].toIntOrNull() ?: 1
                "w" -> if (f.size == 3) userWords[f[1]] = f[2].toIntOrNull() ?: 200
                "r" -> if (f.size == 3) rejected.getOrPut(f[1]) { HashSet() }.add(f[2])
            }
        }
        changed = false
    }

    fun clear() {
        load(null)
        changed = true
    }

    companion object {
        const val MAX_UNIGRAMS = 20000
        const val MAX_BIGRAM_HEADS = 8000
    }
}
