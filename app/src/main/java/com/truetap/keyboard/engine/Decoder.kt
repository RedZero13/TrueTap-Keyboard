package com.truetap.keyboard.engine

import kotlin.math.exp
import kotlin.math.ln

data class Candidate(
    /** Lower-case or dictionary display form; the caller applies the user's capitalisation. */
    val word: String,
    val score: Float,
    val spatial: Float,
    val isCompletion: Boolean,
    val edits: Int,
    val fromUserDictionary: Boolean = false,
)

/**
 * Finds the words a sequence of taps most likely meant.
 *
 * Each tap is a fuzzy observation over nearby keys ([SpatialModel]); the decoder walks the
 * dictionary trie with a beam, combining how well each word's letters explain the taps with how
 * common the word is (and how often *this* user writes it after the previous word). It tolerates one
 * missed, extra or swapped letter on longer words, and skips apostrophes so "dont" finds "don't".
 */
class Decoder(
    private val spatial: SpatialModel,
    private val main: Dictionary,
    private val history: UserHistory? = null,
    private val user: Dictionary? = null,
) {
    var lmWeight = 0.75f
    var beamWidth = 64

    private class State(val node: Int, val tap: Int, val score: Float, val edits: Int)

    private inner class Run(val dict: Dictionary, val tapScores: Array<FloatArray>) {
        val n = tapScores.size

        fun ll(tap: Int, c: Char): Float {
            val k = spatial.keyIndex(c)
            if (k < 0) return SpatialModel.MIN_LL
            return tapScores[tap][k] - spatial.foldPenalty(c)
        }

        fun maxEdits() = when {
            n <= 3 -> 0
            n <= 7 -> 1
            else -> 2
        }

        /** Beam search; returns every surviving state that consumed all taps. */
        fun search(onComplete: (State) -> Unit) {
            var beam = listOf(State(dict.root, 0, 0f, 0))
            val maxDepth = n + 3
            for (depth in 0..maxDepth) {
                val next = HashMap<Long, State>()
                fun offer(s: State) {
                    if (s.score < PRUNE_FLOOR) return
                    val key = (s.node.toLong() shl 8) or s.tap.toLong()
                    val old = next[key]
                    if (old == null || old.score < s.score) next[key] = s
                }
                for (s in beam) {
                    if (s.tap == n) onComplete(s)
                    var c = dict.firstChild(s.node)
                    while (c >= 0) {
                        val ch = dict.charAt(c)
                        if (ch == '\'' || ch == '-') {
                            offer(State(c, s.tap, s.score - PUNCT_SKIP, s.edits))
                        } else {
                            if (s.tap < n) offer(State(c, s.tap + 1, s.score + ll(s.tap, ch), s.edits))
                            if (s.edits < maxEdits() && depth > 0) {
                                // User missed this letter.
                                offer(State(c, s.tap, s.score - DELETION, s.edits + 1))
                                // User hit an extra key before this letter.
                                if (s.tap + 1 < n) offer(State(c, s.tap + 2, s.score + ll(s.tap + 1, ch) - INSERTION, s.edits + 1))
                                // Two letters swapped.
                                if (s.tap + 1 < n) {
                                    var g = dict.firstChild(c)
                                    while (g >= 0) {
                                        val gc = dict.charAt(g)
                                        if (gc != ch && gc != '\'' && gc != '-') {
                                            offer(State(g, s.tap + 2, s.score + ll(s.tap, gc) + ll(s.tap + 1, ch) - TRANSPOSE, s.edits + 1))
                                        }
                                        g = dict.nextSibling(g)
                                    }
                                }
                            }
                        }
                        c = dict.nextSibling(c)
                    }
                }
                if (next.isEmpty()) break
                // Rank by spatial fit plus the best word still reachable, so rare prefixes don't crowd the beam.
                beam = next.values.sortedByDescending { it.score + lmWeight * Dictionary.logProb(dict.subtreeFreq(it.node)) }
                    .take(beamWidth)
            }
        }
    }

    private fun tapScores(taps: List<Tap>) = Array(taps.size) { spatial.scores(taps[it]) }

    /**
     * Ranked candidates for [taps]. With [completions], prefixes are also extended to likely words
     * (for the suggestion strip); those are never used for autocorrection.
     */
    fun decode(taps: List<Tap>, prevWord: String? = null, limit: Int = 6, completions: Boolean = true): List<Candidate> {
        if (taps.isEmpty()) return emptyList()
        val scores = tapScores(taps)
        val best = HashMap<String, Candidate>()
        fun add(c: Candidate) {
            val key = c.word.lowercase()
            val old = best[key]
            if (old == null || old.score < c.score) best[key] = c
        }
        for ((dict, isUser) in dictionaries()) {
            val run = Run(dict, scores)
            val prefixes = ArrayList<State>()
            run.search { s ->
                if (dict.isWord(s.node)) {
                    val w = dict.display(s.node)
                    add(Candidate(w, s.score + lm(dict, s.node, w, prevWord, isUser), s.score, false, s.edits, isUser))
                }
                prefixes.add(s)
            }
            if (completions && taps.size >= 1) {
                prefixes.sortByDescending { it.score + lmWeight * Dictionary.logProb(dict.subtreeFreq(it.node)) }
                for (s in prefixes.take(6)) {
                    for (node in dict.topCompletions(s.node, 4)) {
                        if (node == s.node) continue
                        val w = dict.display(node)
                        val extra = w.length - taps.size
                        add(Candidate(w, s.score + lm(dict, node, w, prevWord, isUser) - COMPLETION - 0.15f * extra, s.score, true, s.edits, isUser))
                    }
                }
            }
        }
        return best.values.sortedByDescending { it.score }.take(limit)
    }

    /** Score of a specific word for these taps, or null if the word can't explain them. */
    fun scoreOf(word: String, taps: List<Tap>, prevWord: String?): Float? =
        decode(taps, prevWord, limit = 40, completions = false).firstOrNull { it.word.equals(word, ignoreCase = true) }?.score

    private fun lm(dict: Dictionary, node: Int, word: String, prevWord: String?, isUser: Boolean): Float {
        var s = lmWeight * Dictionary.logProb(dict.wordFreq(node))
        if (history != null) s += history.boost(prevWord, word)
        if (isUser) s += USER_WORD_BONUS
        return s
    }

    private fun dictionaries(): List<Pair<Dictionary, Boolean>> =
        if (user != null && user.size > 1) listOf(main to false, user to true) else listOf(main to false)

    /**
     * Probability of each next letter given the taps so far, from the decoder's beam.
     * This is what lets an ambiguous tap between two keys resolve to the one that continues a word.
     */
    fun nextCharPrior(taps: List<Tap>): Map<Char, Float> {
        val scores = tapScores(taps)
        val mass = HashMap<Char, Double>()
        var total = 0.0
        for ((dict, _) in dictionaries()) {
            val run = Run(dict, scores)
            run.search { s ->
                var c = dict.firstChild(s.node)
                while (c >= 0) {
                    var ch = dict.charAt(c)
                    var node = c
                    // Look through apostrophes: after "don" the next *typed* letter is 't'.
                    if (ch == '\'' || ch == '-') {
                        val g = dict.firstChild(c)
                        if (g < 0) { c = dict.nextSibling(c); continue }
                        ch = dict.charAt(g); node = g
                    }
                    val w = exp((s.score + lmWeight * Dictionary.logProb(dict.subtreeFreq(node))).toDouble())
                    val base = SpatialModel.foldChar(ch)
                    mass[base] = (mass[base] ?: 0.0) + w
                    total += w
                    c = dict.nextSibling(c)
                }
            }
        }
        if (total <= 0.0) return emptyMap()
        return mass.mapValues { (it.value / total).toFloat() }
    }

    /**
     * Pick the key for a fresh tap: spatial likelihood plus a capped language-model nudge.
     * A tap clearly inside a key always types that key; only taps near a boundary get pulled
     * towards the letter that makes sense.
     */
    fun resolveTap(tap: Tap, prior: Map<Char, Float>, nudgeWeight: Float = 0.5f): Int {
        val s = spatial.scores(tap)
        if (prior.isEmpty()) return s.indices.maxByOrNull { s[it] } ?: -1
        val k = spatial.keys.size
        var best = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in s.indices) {
            val p = prior[spatial.keys[i].char] ?: 0f
            val smoothed = (p + PRIOR_FLOOR) / (1f + PRIOR_FLOOR * k)
            val nudge = (nudgeWeight * ln(smoothed * k)).coerceIn(-MAX_NUDGE, MAX_NUDGE)
            val total = s[i] + nudge
            if (total > bestScore) { bestScore = total; best = i }
        }
        return best
    }

    /**
     * Try reading the taps as two words: either the user missed the space bar and hit a bottom-row
     * letter instead (a tap flagged [Tap.nearSpace]), or skipped the space entirely.
     */
    fun decodeSplit(taps: List<Tap>, prevWord: String?): Pair<Candidate, Candidate>? {
        if (taps.size < 4) return null
        var best: Triple<Candidate, Candidate, Float>? = null
        for (j in 2..taps.size - 2) {
            val viaSpaceTap = taps[j].nearSpace && j + 1 <= taps.size - 2
            val rightStart = if (viaSpaceTap) j + 1 else j
            val penalty = if (viaSpaceTap) SPLIT_VIA_SPACE else SPLIT_MISSING_SPACE
            val l = decode(taps.subList(0, j), prevWord, 1, completions = false).firstOrNull() ?: continue
            if (l.edits > 0) continue
            val r = decode(taps.subList(rightStart, taps.size), l.word, 1, completions = false).firstOrNull() ?: continue
            if (r.edits > 0) continue
            // Without a stray space tap as evidence, only split into common words ("thisis" -> "this is").
            if (!viaSpaceTap && minOf(main.frequency(l.word), main.frequency(r.word)) < COMMON_WORD_FREQ) continue
            val total = l.score + r.score - penalty
            if (best == null || total > best.third) best = Triple(l, r, total)
        }
        return best?.let { Pair(it.first.copy(score = it.third), it.second) }
    }

    companion object {
        const val DELETION = 4.5f
        const val INSERTION = 4.0f
        const val TRANSPOSE = 3.5f
        const val PUNCT_SKIP = 0.15f
        const val COMPLETION = 0.8f
        const val USER_WORD_BONUS = 0.5f
        const val PRUNE_FLOOR = -30f
        const val PRIOR_FLOOR = 0.002f
        const val MAX_NUDGE = 2.2f
        const val SPLIT_VIA_SPACE = 2.0f
        const val SPLIT_MISSING_SPACE = 6.0f
        const val COMMON_WORD_FREQ = 150
    }
}
