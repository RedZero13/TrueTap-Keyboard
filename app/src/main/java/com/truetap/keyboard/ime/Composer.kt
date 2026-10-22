package com.truetap.keyboard.ime

import com.truetap.keyboard.engine.Tap

/** The word currently being typed (shown underlined in the editor). */
class Composer {
    val text = StringBuilder()
    /** One entry per character; null for characters that didn't come from a letter tap. */
    val taps = ArrayList<Tap?>()
    /** First letter was typed with shift (manual or automatic). */
    var capsFirst = false
    /** Typed with caps lock on. */
    var allCaps = false
    /** Word came from glide typing; backspace removes it whole. */
    var fromGesture = false
    /** The user undid an autocorrection of this word; commit it as typed. */
    var keepAsTyped = false

    val isEmpty get() = text.isEmpty()
    val length get() = text.length

    fun add(c: String, tap: Tap?) {
        if (text.isEmpty()) {
            capsFirst = c.firstOrNull()?.isUpperCase() == true
        }
        text.append(c)
        repeat(c.length) { taps.add(tap) }
    }

    fun removeLast() {
        if (text.isEmpty()) return
        // Remove a whole surrogate pair if needed.
        val cut = if (text.length >= 2 && Character.isLowSurrogate(text[text.length - 1])) 2 else 1
        repeat(cut) {
            text.setLength(text.length - 1)
            taps.removeAt(taps.size - 1)
        }
        if (text.isEmpty()) clear()
    }

    /** All characters are plain letters with recorded taps, so the decoder can work on it. */
    fun decodable(): Boolean = text.isNotEmpty() && !fromGesture && taps.all { it != null } && text.all { it.isLetter() }

    fun tapList(): List<Tap> = taps.filterNotNull()

    fun set(word: String, wordTaps: List<Tap?>?, gesture: Boolean) {
        clear()
        text.append(word)
        if (wordTaps != null && wordTaps.size == word.length) taps.addAll(wordTaps) else repeat(word.length) { taps.add(null) }
        capsFirst = word.firstOrNull()?.isUpperCase() == true
        fromGesture = gesture
    }

    fun clear() {
        text.setLength(0)
        taps.clear()
        capsFirst = false
        allCaps = false
        fromGesture = false
        keepAsTyped = false
    }

    /** Apply the user's capitalisation to a decoder result. */
    fun applyCase(word: String): String = when {
        allCaps && word.length > 1 -> word.uppercase()
        capsFirst -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }
}
