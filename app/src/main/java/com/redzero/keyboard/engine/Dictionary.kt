package com.redzero.keyboard.engine

import java.io.BufferedReader

/**
 * Compact first-child / next-sibling trie over lower-cased words.
 *
 * Every node stores the best word frequency found in its subtree so the decoder can rank
 * completions and estimate "what letter comes next" without walking the subtree.
 */
class Dictionary private constructor(
    private val chars: CharArray,
    private val firstChild: IntArray,
    private val nextSibling: IntArray,
    /** Frequency (1..255) of the word ending at this node, 0 if no word ends here. */
    private val wordFreq: IntArray,
    /** Highest word frequency anywhere in this subtree, including this node. */
    private val subtreeFreq: IntArray,
    /** Index into [displayForms] for terminal nodes, -1 otherwise. */
    private val wordIndex: IntArray,
    private val displayForms: Array<String>,
    val size: Int,
) {
    val root: Int get() = 0

    fun charAt(node: Int): Char = chars[node]
    fun firstChild(node: Int): Int = firstChild[node]
    fun nextSibling(node: Int): Int = nextSibling[node]
    fun isWord(node: Int): Boolean = wordFreq[node] > 0
    fun wordFreq(node: Int): Int = wordFreq[node]
    fun subtreeFreq(node: Int): Int = subtreeFreq[node]
    fun display(node: Int): String = displayForms[wordIndex[node]]

    fun child(node: Int, c: Char): Int {
        var n = firstChild[node]
        while (n >= 0) {
            if (chars[n] == c) return n
            n = nextSibling[n]
        }
        return -1
    }

    /** Node for [word] (case-insensitive), or -1. */
    fun find(word: String): Int {
        var n = root
        for (ch in word) {
            n = child(n, ch.lowercaseChar())
            if (n < 0) return -1
        }
        return n
    }

    fun contains(word: String): Boolean {
        val n = find(word)
        return n >= 0 && isWord(n)
    }

    fun frequency(word: String): Int {
        val n = find(word)
        return if (n >= 0) wordFreq[n] else 0
    }

    /** Display form ("I", "don't") for a word typed in any case, or null if unknown. */
    fun displayForm(word: String): String? {
        val n = find(word)
        return if (n >= 0 && isWord(n)) display(n) else null
    }

    /** Most frequent words at or below [node], best first. */
    fun topCompletions(node: Int, limit: Int): List<Int> {
        val out = ArrayList<Int>(limit)
        // Entries are packed as (priority << 32 | node << 1 | isEmit) in a max-heap.
        val heap = java.util.PriorityQueue<Long>(Comparator.reverseOrder())
        heap.add(pack(subtreeFreq[node], node, false))
        while (heap.isNotEmpty() && out.size < limit) {
            val e = heap.poll() ?: break
            val n = ((e and 0xFFFFFFFFL) shr 1).toInt()
            if (e and 1L == 1L) {
                out.add(n)
                continue
            }
            if (wordFreq[n] > 0) heap.add(pack(wordFreq[n], n, true))
            var c = firstChild[n]
            while (c >= 0) {
                heap.add(pack(subtreeFreq[c], c, false))
                c = nextSibling[c]
            }
        }
        return out
    }

    // Emits sort before expansions of equal priority so ties resolve to the shorter word.
    private fun pack(priority: Int, node: Int, emit: Boolean): Long =
        (priority.toLong() shl 32) or (node.toLong() shl 1) or (if (emit) 1L else 0L)

    companion object {
        /** Log-probability span (nats) represented by the 1..255 frequency scale. */
        const val FREQ_SPAN_NATS = 11.5f

        /** Natural-log probability of a frequency relative to the most common word. */
        fun logProb(freq: Int): Float = (freq - 255) / 254f * FREQ_SPAN_NATS

        fun load(reader: BufferedReader): Dictionary {
            val entries = ArrayList<Pair<String, Int>>(45000)
            reader.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val f = line.substring(tab + 1).trim().toIntOrNull() ?: return@forEachLine
                    entries.add(line.substring(0, tab) to f.coerceIn(1, 255))
                }
            }
            return build(entries)
        }

        fun build(entries: List<Pair<String, Int>>): Dictionary {
            val b = Builder()
            for ((w, f) in entries) b.add(w, f)
            return b.finish()
        }
    }

    private class Builder {
        val chars = StringBuilder().append('\u0000')
        val firstChild = IntList().apply { add(-1) }
        val nextSibling = IntList().apply { add(-1) }
        val wordFreq = IntList().apply { add(0) }
        val wordIndex = IntList().apply { add(-1) }
        val display = ArrayList<String>()

        fun add(word: String, freq: Int) {
            if (word.isEmpty()) return
            var n = 0
            for (raw in word) {
                val c = raw.lowercaseChar()
                var child = firstChild[n]
                var prev = -1
                while (child >= 0 && chars[child] != c) {
                    prev = child
                    child = nextSibling[child]
                }
                if (child < 0) {
                    child = chars.length
                    chars.append(c)
                    firstChild.add(-1)
                    nextSibling.add(-1)
                    wordFreq.add(0)
                    wordIndex.add(-1)
                    if (prev < 0) firstChild[n] = child else nextSibling[prev] = child
                }
                n = child
            }
            if (freq > wordFreq[n]) {
                wordFreq[n] = freq
                if (wordIndex[n] < 0) {
                    wordIndex[n] = display.size
                    display.add(word)
                }
            }
        }

        fun finish(): Dictionary {
            val size = chars.length
            val subtree = IntArray(size)
            // Children always have higher indices than parents, so a reverse sweep is a post-order.
            val parentOf = IntArray(size) { -1 }
            for (n in 0 until size) {
                var c = firstChild[n]
                while (c >= 0) {
                    parentOf[c] = n
                    c = nextSibling[c]
                }
            }
            for (n in size - 1 downTo 0) {
                if (wordFreq[n] > subtree[n]) subtree[n] = wordFreq[n]
                val p = parentOf[n]
                if (p >= 0 && subtree[n] > subtree[p]) subtree[p] = subtree[n]
            }
            return Dictionary(
                chars.toString().toCharArray(), firstChild.toArray(), nextSibling.toArray(),
                wordFreq.toArray(), subtree, wordIndex.toArray(), display.toTypedArray(), size,
            )
        }
    }
}

internal class IntList(initial: Int = 1024) {
    private var data = IntArray(initial)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]
    operator fun set(i: Int, v: Int) {
        data[i] = v
    }

    fun toArray(): IntArray = data.copyOf(size)
}
