package com.redzero.keyboard.engine

import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Glide typing: matches a finger trail against the ideal key-to-key path of each word
 * (a simplified SHARK2 location channel plus a "did the trail pass near every letter" check).
 */
class GestureDecoder(
    private val spatial: SpatialModel,
    private val dict: Dictionary,
    private val history: UserHistory? = null,
    private val maxWords: Int = 30000,
) {
    private class Entry(val word: String, val freq: Int, val keys: IntArray)

    private val byFirstKey: Array<MutableList<Entry>> by lazy { buildIndex() }

    private fun buildIndex(): Array<MutableList<Entry>> {
        val index = Array<MutableList<Entry>>(spatial.keys.size) { ArrayList() }
        for (node in dict.topCompletions(dict.root, maxWords)) {
            val word = dict.display(node)
            val keys = ArrayList<Int>(word.length)
            var ok = true
            for (ch in word.lowercase()) {
                if (ch == '\'' || ch == '-') continue
                val k = spatial.keyIndex(ch)
                if (k < 0) { ok = false; break }
                if (keys.isEmpty() || keys.last() != k) keys.add(k)
            }
            if (!ok || word.length < 2) continue
            index[keys[0]].add(Entry(word, dict.wordFreq(node), keys.toIntArray()))
        }
        return index
    }

    /** Warm the index on a background thread so the first glide is instant. */
    fun prepare() {
        byFirstKey.size
    }

    fun decode(trail: List<FloatArray>, prevWord: String?, limit: Int = 5): List<Candidate> {
        if (trail.size < 2) return emptyList()
        val kw = spatial.keyWidth
        val path = resample(trail, N)
        if (path.isEmpty()) return emptyList()
        val start = trail.first()
        val end = trail.last()
        val starts = keysNear(start[0], start[1], 1.1f)
        val ends = keysNear(end[0], end[1], 1.1f).toHashSet()
        val out = ArrayList<Candidate>()
        for (s in starts) {
            for (e in byFirstKey[s]) {
                if (e.keys.last() !in ends) continue
                val template = resample(e.keys.map { floatArrayOf(spatial.keys[it].cx, spatial.keys[it].cy) }, N)
                if (template.isEmpty() && e.keys.size > 1) continue
                var loc = 0f
                for (i in 0 until N) {
                    val t = if (template.isEmpty()) floatArrayOf(spatial.keys[e.keys[0]].cx, spatial.keys[e.keys[0]].cy) else template[i]
                    loc += hypot(path[i][0] - t[0], path[i][1] - t[1]) / kw
                }
                loc /= N
                var miss = 0f
                for (k in e.keys) {
                    val key = spatial.keys[k]
                    var best = Float.MAX_VALUE
                    for (p in trail) best = min(best, hypot(p[0] - key.cx, p[1] - key.cy) / kw)
                    if (best > 0.75f) miss += (best - 0.75f) * (best - 0.75f) * 8f
                }
                // Where the finger lands and lifts is deliberate; weigh the endpoints heavily.
                val k0 = spatial.keys[e.keys.first()]
                val k1 = spatial.keys[e.keys.last()]
                val d0 = hypot(start[0] - k0.cx, start[1] - k0.cy) / kw
                val d1 = hypot(end[0] - k1.cx, end[1] - k1.cy) / kw
                val spatialScore = -(loc * loc) * 6f - miss - 4f * (d0 * d0 + d1 * d1)
                if (spatialScore < -25f) continue
                var score = spatialScore + LM_WEIGHT * Dictionary.logProb(e.freq)
                if (history != null) score += history.boost(prevWord, e.word)
                out.add(Candidate(e.word, score, spatialScore, false, 0))
            }
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    private fun keysNear(x: Float, y: Float, radius: Float): List<Int> {
        val r = spatial.keyWidth * radius
        val near = spatial.keys.indices.filter { hypot(spatial.keys[it].cx - x, spatial.keys[it].cy - y) <= r }
        return near.ifEmpty { listOf(spatial.nearestKey(x, y)) }
    }

    companion object {
        const val N = 40
        const val LM_WEIGHT = 0.8f

        fun resample(points: List<FloatArray>, n: Int): List<FloatArray> {
            if (points.isEmpty()) return emptyList()
            var total = 0f
            for (i in 1 until points.size) total += dist(points[i - 1], points[i])
            if (total <= 0f) return List(n) { points[0] }
            val step = total / (n - 1)
            val out = ArrayList<FloatArray>(n)
            out.add(points[0])
            var acc = 0f
            var prev = points[0]
            var i = 1
            while (i < points.size && out.size < n) {
                val cur = points[i]
                val d = dist(prev, cur)
                if (acc + d >= step && d > 0f) {
                    val t = (step - acc) / d
                    val q = floatArrayOf(prev[0] + t * (cur[0] - prev[0]), prev[1] + t * (cur[1] - prev[1]))
                    out.add(q)
                    prev = q
                    acc = 0f
                } else {
                    acc += d
                    prev = cur
                    i++
                }
            }
            while (out.size < n) out.add(points.last())
            return out
        }

        private fun dist(a: FloatArray, b: FloatArray) = sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]))
    }
}
