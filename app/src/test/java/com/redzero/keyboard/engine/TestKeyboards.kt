package com.redzero.keyboard.engine

import java.io.File
import kotlin.random.Random

/** Pixel 9 portrait QWERTY geometry: 1080 px wide, 56 dp rows at 2.625 px/dp. */
object TestKeyboards {
    const val WIDTH = 1080f
    const val ROW_H = 56f * 2.625f

    fun qwerty(): List<KeyGeom> {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val kw = WIDTH / 10f
        val out = ArrayList<KeyGeom>()
        rows.forEachIndexed { r, letters ->
            // Row 2 is inset by half a key; row 3 sits between shift and backspace (1.5 keys each).
            val start = when (r) { 0 -> 0f; 1 -> kw / 2; else -> kw * 1.5f }
            letters.forEachIndexed { i, c ->
                out.add(KeyGeom(c, start + kw * i + kw / 2, ROW_H * r + ROW_H / 2, kw, ROW_H))
            }
        }
        return out
    }

    fun dictionary(lang: String = "en"): Dictionary {
        val candidates = listOf(
            File("src/main/assets/dict/$lang.txt"),
            File("app/src/main/assets/dict/$lang.txt"),
        )
        val f = candidates.first { it.exists() }
        return f.bufferedReader().use { Dictionary.load(it) }
    }
}

/**
 * Simulates an imprecise typist: every tap lands around the key centre with Gaussian scatter plus a
 * consistent personal offset (thumbs land low and inward), and occasionally a tap is way off.
 */
class FatFinger(
    private val keys: List<KeyGeom>,
    private val sigma: Float,
    private val offsetX: Float,
    private val offsetY: Float,
    seed: Int,
) {
    private val rnd = Random(seed)
    private val byChar = keys.associateBy { it.char }

    private fun gauss(): Float {
        // Box-Muller.
        val u1 = rnd.nextDouble().coerceAtLeast(1e-9)
        val u2 = rnd.nextDouble()
        return (Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2)).toFloat()
    }

    fun tap(c: Char): Tap {
        val k = byChar.getValue(c)
        val w = k.w
        val x = k.cx + (offsetX + gauss() * sigma) * w
        val y = k.cy + (offsetY + gauss() * sigma) * w
        return Tap(x, y.coerceIn(1f, TestKeyboards.ROW_H * 3 - 1f), size = w * 0.8f)
    }
}
