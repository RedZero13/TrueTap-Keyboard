package com.redzero.keyboard.engine

/** Outcome of finishing a word. */
data class WordResult(
    /** Text to commit, without the trailing space. May contain a space when a word split was applied. */
    val text: String,
    val corrected: Boolean,
    /** The letters as they were resolved while typing. */
    val literal: String,
)

/**
 * The typing brain shared by the keyboard service, the calibration screen and the simulator:
 * resolves each tap to a key and decides what a finished word should be.
 */
class TypingEngine(
    val spatial: SpatialModel,
    val dictionary: Dictionary,
    val history: UserHistory = UserHistory(),
    userDictionary: Dictionary? = null,
) {
    val decoder = Decoder(spatial, dictionary, history, userDictionary)

    /** 0 = off, 1 = gentle, 2 = normal, 3 = aggressive. */
    var autocorrectLevel = 2
    var splitWords = true

    /** Key index (into [SpatialModel.keys]) for a new tap following [previous] taps of this word. */
    fun resolve(previous: List<Tap>, tap: Tap, nudge: Boolean = true): Int {
        val prior = if (nudge) decoder.nextCharPrior(previous) else emptyMap()
        val i = decoder.resolveTap(tap, if (previous.isEmpty() && nudge) firstLetterPrior() else prior)
        return i
    }

    private var firstPrior: Map<Char, Float>? = null

    /** Distribution of first letters, so even the first tap of a word gets a gentle nudge. */
    private fun firstLetterPrior(): Map<Char, Float> {
        firstPrior?.let { return it }
        val mass = HashMap<Char, Float>()
        var c = dictionary.firstChild(dictionary.root)
        var total = 0f
        while (c >= 0) {
            val w = kotlin.math.exp(Dictionary.logProb(dictionary.subtreeFreq(c)) * 0.5f)
            val base = SpatialModel.foldChar(dictionary.charAt(c))
            mass[base] = (mass[base] ?: 0f) + w
            total += w
            c = dictionary.nextSibling(c)
        }
        return mass.mapValues { it.value / total }.also { firstPrior = it }
    }

    /** Spatial + language score of reading [taps] as exactly [word] (letters aligned 1:1). */
    fun exactScore(word: String, taps: List<Tap>, prevWord: String?): Float? {
        val letters = word.lowercase().filter { it != '\'' && it != '-' }
        if (letters.length != taps.size) return null
        var s = 0f
        for (i in taps.indices) {
            val k = spatial.keyIndex(letters[i])
            if (k < 0) return null
            s += spatial.scores(taps[i])[k] - spatial.foldPenalty(letters[i])
        }
        val f = dictionary.frequency(word).let { if (it == 0 && isUserWord(word)) USER_WORD_FREQ else it }
        if (f == 0) return null
        return s + decoder.lmWeight * Dictionary.logProb(f) + history.boost(prevWord, word)
    }

    fun suggestions(taps: List<Tap>, prevWord: String?, limit: Int = 5): List<Candidate> =
        decoder.decode(taps, prevWord, limit, completions = true)

    /**
     * Decide what to commit for a finished word. [literal] is what the user saw while typing.
     */
    fun finishWord(taps: List<Tap>, literal: String, prevWord: String?): WordResult {
        val keep = WordResult(literal, false, literal)
        if (autocorrectLevel == 0 || taps.isEmpty() || taps.size != literal.length) return keep
        if (literal.any { !it.isLetter() && it != '\'' }) return keep
        val candidates = decoder.decode(taps, prevWord, 8, completions = false)
        val literalKnown = dictionary.contains(literal) || isUserWord(literal)
        val literalScore = exactScore(literal, taps, prevWord)
        val best = candidates.firstOrNull { !history.isRejected(literal, it.word) }

        // Single letters: only hop to an adjacent key ("o" -> "I"), never further.
        if (taps.size == 1) {
            val d = dictionary.displayForm(literal)
            if (d != null && d != literal && dictionary.frequency(literal) > 0) return WordResult(d, true, literal)
            if (literalKnown) return keep
            val one = best ?: return keep
            return if (one.spatial >= -4f) WordResult(one.word, true, literal) else keep
        }

        if (best != null && best.word.equals(literal, ignoreCase = true)) {
            // Same letters; still apply the dictionary display form (dont -> don't handled elsewhere, i'm -> I'm).
            return if (best.word != literal && best.word.lowercase() == literal.lowercase() && best.word.any { it.isUpperCase() })
                WordResult(best.word, true, literal) else keep
        }

        // Longer words carry more evidence, so they may absorb more sloppy taps.
        val perExtraLetter = (taps.size - 3).coerceAtLeast(0)
        val minSpatial = when (autocorrectLevel) { 1 -> -6.5f - 0.7f * perExtraLetter; 2 -> -8.5f - 1.2f * perExtraLetter; else -> -11f - 1.5f * perExtraLetter }
        val knownMargin = when (autocorrectLevel) { 1 -> 4f; 2 -> 1.8f; else -> 0.8f }

        if (best != null && best.spatial >= minSpatial) {
            val accept = if (literalKnown && literalScore != null) {
                best.score - literalScore >= knownMargin
            } else {
                // Unknown letters: the user almost certainly mistyped. Make sure the winner is clear.
                val second = candidates.firstOrNull { it !== best && !history.isRejected(literal, it.word) }
                second == null || best.score - second.score >= 0.4f || best.edits == 0
            }
            if (accept) {
                if (!literalKnown && splitWords) split(taps, prevWord, best.score)?.let { return WordResult(it, true, literal) }
                return WordResult(best.word, true, literal)
            }
        }
        if (!literalKnown && splitWords) split(taps, prevWord, best?.score ?: Float.NEGATIVE_INFINITY)?.let {
            return WordResult(it, true, literal)
        }
        return keep
    }

    private fun split(taps: List<Tap>, prevWord: String?, singleScore: Float): String? {
        val s = decoder.decodeSplit(taps, prevWord) ?: return null
        val total = s.first.score
        return if (total > singleScore + 1f) "${s.first.word} ${s.second.word}" else null
    }

    private fun isUserWord(word: String) = history.userWords().keys.any { it.equals(word, ignoreCase = true) }

    companion object {
        const val USER_WORD_FREQ = 200
    }
}
