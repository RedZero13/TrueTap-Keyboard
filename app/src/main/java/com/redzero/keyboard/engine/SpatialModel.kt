package com.redzero.keyboard.engine

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** A letter key as the engine sees it: the full touch cell, not the drawn key cap. */
data class KeyGeom(val char: Char, val cx: Float, val cy: Float, val w: Float, val h: Float)

/** One tap on the keyboard. [size] is MotionEvent.touchMajor in px (0 if unknown). */
data class Tap(
    val x: Float,
    val y: Float,
    val size: Float = 0f,
    /** True when the tap sits in the lower part of a bottom-row letter key, i.e. it may be a missed space. */
    val nearSpace: Boolean = false,
)

/**
 * Per-key Gaussian touch model with learned offsets.
 *
 * People don't hit key centres: thumbs land low and towards the palm, and each person has their own
 * pattern. Each key keeps a mean offset and spread (in key-width units), shrunk towards a shared
 * keyboard-wide offset until the key has enough samples of its own.
 */
class TouchModel {
    private class Stats(var bx: Float = 0f, var by: Float = 0f, var vx: Float = DEFAULT_VAR_X, var vy: Float = DEFAULT_VAR_Y, var n: Int = 0)

    private val perKey = HashMap<Char, Stats>()
    private val global = Stats()
    var samples: Int = 0
        private set

    /** Offset (key-width units) and spread for [c], blending per-key and global statistics. */
    fun params(c: Char): FloatArray {
        val k = perKey[c]
        val gw = global.n / (global.n + GLOBAL_PRIOR)
        val gbx = global.bx * gw
        val gby = global.by * gw
        val gvx = DEFAULT_VAR_X + (global.vx - DEFAULT_VAR_X) * gw
        val gvy = DEFAULT_VAR_Y + (global.vy - DEFAULT_VAR_Y) * gw
        if (k == null || k.n == 0) return floatArrayOf(gbx, gby, sqrt(gvx), sqrt(gvy))
        val kw = k.n / (k.n + KEY_PRIOR)
        return floatArrayOf(
            gbx + (k.bx - gbx) * kw,
            gby + (k.by - gby) * kw,
            sqrt(gvx + (k.vx - gvx) * kw),
            sqrt(gvy + (k.vy - gvy) * kw),
        )
    }

    /** Record that a tap at ([dx], [dy]) key-widths from the centre of [c] was meant for [c]. */
    fun learn(c: Char, dx: Float, dy: Float) {
        // Ignore taps that are so far off they are probably not about this key at all.
        if (dx * dx + dy * dy > 1.2f) return
        update(global, dx, dy, 0.01f)
        update(perKey.getOrPut(c) { Stats() }, dx, dy, 0.05f)
        samples++
    }

    private fun update(s: Stats, dx: Float, dy: Float, minRate: Float) {
        s.n++
        val a = max(1f / s.n, minRate)
        val ex = dx - s.bx
        val ey = dy - s.by
        s.bx = (s.bx + a * ex).coerceIn(-MAX_BIAS, MAX_BIAS)
        s.by = (s.by + a * ey).coerceIn(-MAX_BIAS, MAX_BIAS)
        s.vx = ((1 - a) * s.vx + a * ex * ex).coerceIn(MIN_VAR, MAX_VAR)
        s.vy = ((1 - a) * s.vy + a * ey * ey).coerceIn(MIN_VAR, MAX_VAR)
    }

    fun reset() {
        perKey.clear()
        global.bx = 0f; global.by = 0f; global.vx = DEFAULT_VAR_X; global.vy = DEFAULT_VAR_Y; global.n = 0
        samples = 0
    }

    fun serialize(): String = buildString {
        append("v1|").append(samples)
        append("|*:").append(fmt(global))
        for ((c, s) in perKey) append('|').append(c.code).append(':').append(fmt(s))
    }

    private fun fmt(s: Stats) = "${s.bx},${s.by},${s.vx},${s.vy},${s.n}"

    fun load(data: String?) {
        reset()
        if (data == null || !data.startsWith("v1|")) return
        val parts = data.split('|')
        samples = parts.getOrNull(1)?.toIntOrNull() ?: 0
        for (p in parts.drop(2)) {
            val colon = p.indexOf(':')
            if (colon < 0) continue
            val v = p.substring(colon + 1).split(',')
            if (v.size != 5) continue
            val s = if (p.startsWith("*")) global else perKey.getOrPut(p.substring(0, colon).toIntOrNull()?.toChar() ?: continue) { Stats() }
            s.bx = v[0].toFloatOrNull() ?: 0f
            s.by = v[1].toFloatOrNull() ?: 0f
            s.vx = v[2].toFloatOrNull() ?: DEFAULT_VAR_X
            s.vy = v[3].toFloatOrNull() ?: DEFAULT_VAR_Y
            s.n = v[4].toIntOrNull() ?: 0
        }
    }

    companion object {
        // Spread in key-width units. Keys are taller than wide, so vertical spread is a smaller fraction.
        const val DEFAULT_VAR_X = 0.42f * 0.42f
        const val DEFAULT_VAR_Y = 0.34f * 0.34f
        const val MIN_VAR = 0.2f * 0.2f
        const val MAX_VAR = 0.7f * 0.7f
        const val MAX_BIAS = 0.5f
        const val GLOBAL_PRIOR = 30f
        const val KEY_PRIOR = 25f
    }
}

/**
 * Turns a tap into a likelihood for every letter key, using the learned [TouchModel].
 */
class SpatialModel(keys: List<KeyGeom>, val touch: TouchModel) {
    val keys: List<KeyGeom> = keys
    private val indexOf = HashMap<Char, Int>().apply { keys.forEachIndexed { i, k -> put(k.char, i) } }
    val keyWidth: Float = keys.map { it.w }.average().toFloat().takeIf { it > 0 } ?: 1f
    private val bottomRowY: Float = keys.maxOfOrNull { it.cy } ?: 0f

    /** Index of the key that types [c], folding accents onto their base letter. -1 if none. */
    fun keyIndex(c: Char): Int {
        indexOf[c]?.let { return it }
        val base = foldChar(c)
        return if (base != c) indexOf[base] ?: -1 else -1
    }

    /** Extra cost for typing [c] via its unaccented key. */
    fun foldPenalty(c: Char): Float = if (indexOf.containsKey(c)) 0f else ACCENT_PENALTY

    fun key(c: Char): KeyGeom? = keyIndex(c).let { if (it >= 0) keys[it] else null }

    /** Log-likelihood of [tap] for every key, shifted so the best key scores 0. */
    fun scores(tap: Tap): FloatArray {
        val out = FloatArray(keys.size)
        var best = Float.NEGATIVE_INFINITY
        val sizeScale = sizeScale(tap.size)
        for (i in keys.indices) {
            val k = keys[i]
            val p = touch.params(k.char)
            val sx = p[2] * k.w * sizeScale
            val sy = p[3] * k.w * sizeScale
            val dx = (tap.x - (k.cx + p[0] * k.w)) / sx
            val dy = (tap.y - (k.cy + p[1] * k.w)) / sy
            val ll = -0.5f * (dx * dx + dy * dy) - ln(sx * sy / (k.w * k.w))
            out[i] = ll
            if (ll > best) best = ll
        }
        for (i in out.indices) out[i] = max(out[i] - best, MIN_LL)
        return out
    }

    /** Key physically under the tap (plain nearest-centre rule, no model). */
    fun nearestKey(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in keys.indices) {
            val k = keys[i]
            val dx = (x - k.cx) / k.w
            val dy = (y - k.cy) / k.h
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    fun isBottomRow(i: Int): Boolean = keys[i].cy >= bottomRowY - 1f

    /** Learn from a confirmed (tap, intended char) pair. */
    fun learn(c: Char, tap: Tap) {
        val k = key(c) ?: return
        if (indexOf[c] == null) return
        touch.learn(c, (tap.x - k.cx) / k.w, (tap.y - k.cy) / k.w)
    }

    private fun sizeScale(size: Float): Float {
        if (size <= 0f) return 1f
        // A big contact patch means less certainty about where the finger was aimed.
        val ratio = size / (keyWidth * 0.9f)
        return ratio.coerceIn(1f, 1.35f)
    }

    companion object {
        const val MIN_LL = -14f
        const val ACCENT_PENALTY = 0.6f

        private val foldCache = HashMap<Char, Char>()

        fun foldChar(c: Char): Char = synchronized(foldCache) {
            foldCache.getOrPut(c) {
                when (c) {
                    'ß' -> 's'
                    'æ' -> 'a'
                    'œ' -> 'o'
                    'ø' -> 'o'
                    else -> {
                        val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                        d.firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() } ?: c
                    }
                }
            }
        }
    }
}
