package com.truetap.keyboard.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val engine = Simulation.newEngine()
    private val keys = engine.spatial.keys
    private fun center(c: Char) = keys.first { it.char == c }.let { Tap(it.cx, it.cy) }
    private fun taps(word: String) = word.map { center(it) }

    @Test
    fun dictionaryLookups() {
        val d = engine.dictionary
        assertTrue(d.contains("the"))
        assertTrue(d.contains("don't"))
        assertEquals("I", d.displayForm("i"))
        assertTrue(d.frequency("the") > d.frequency("walrus"))
    }

    @Test
    fun cleanTapsTypeExactlyWhatWasHit() {
        val word = "keyboard"
        val t = taps(word)
        val literal = t.indices.joinToString("") { keys[engine.resolve(t.subList(0, it), t[it])].char.toString() }
        assertEquals(word, literal)
        assertEquals(word, engine.finishWord(t, literal, null).text)
    }

    @Test
    fun neighbourSlipIsCorrected() {
        // "thw" -> "the": last tap landed on w instead of e.
        val t = taps("thw")
        assertEquals("the", engine.finishWord(t, "thw", null).text)
    }

    @Test
    fun missingApostropheIsAdded() {
        val t = taps("dont")
        assertEquals("don't", engine.finishWord(t, "dont", null).text)
    }

    @Test
    fun missedSpaceBarSplitsWords() {
        // "this is" where the space tap landed on the bottom of 'n', just above the space bar.
        val t = taps("this").toMutableList()
        val n = keys.first { it.char == 'n' }
        t.add(Tap(n.cx, n.cy + n.h * 0.4f, nearSpace = true))
        t.addAll(taps("is"))
        assertEquals("this is", engine.finishWord(t, "thisnis", null).text)
    }

    @Test
    fun runTogetherCommonWordsSplit() {
        assertEquals("this is", engine.finishWord(taps("thisis"), "thisis", null).text)
    }

    @Test
    fun loneLowercaseIBecomesCapital() {
        assertEquals("I", engine.finishWord(taps("i"), "i", null).text)
    }

    @Test
    fun boundaryTapLeansTowardsTheWord() {
        // After "th", a tap exactly between 'e' and 'r' should type 'e'.
        val e = keys.first { it.char == 'e' }
        val r = keys.first { it.char == 'r' }
        val between = Tap((e.cx + r.cx) / 2, e.cy)
        val k = engine.resolve(taps("th"), between)
        assertEquals('e', keys[k].char)
    }

    @Test
    fun learnedOffsetIsApplied() {
        val model = TouchModel()
        repeat(200) { model.learn('g', 0.3f, 0.25f) }
        val p = model.params('g')
        assertTrue(p[0] > 0.2f && p[1] > 0.15f)
        val copy = TouchModel().apply { load(model.serialize()) }
        assertEquals(p[0], copy.params('g')[0], 1e-4f)
    }

    @Test
    fun glideFindsWord() {
        val trail = ArrayList<FloatArray>()
        val word = "hello"
        for (i in 0 until word.length - 1) {
            val a = center(word[i]); val b = center(word[i + 1])
            for (s in 0..10) trail.add(floatArrayOf(a.x + (b.x - a.x) * s / 10f, a.y + (b.y - a.y) * s / 10f))
        }
        val g = GestureDecoder(engine.spatial, engine.dictionary)
        assertEquals("hello", g.decode(trail, null).first().word)
    }

    @Test
    fun userHistoryRoundTrips() {
        val h = UserHistory()
        h.record("good", "morning")
        h.recordUnknown("Zorblax"); h.recordUnknown("Zorblax")
        val copy = UserHistory().apply { load(h.serialize()) }
        assertEquals(listOf("morning"), copy.predictions("good", 3))
        assertTrue(copy.userWords().containsKey("Zorblax"))
    }
}
