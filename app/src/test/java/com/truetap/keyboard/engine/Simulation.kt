package com.truetap.keyboard.engine

/**
 * Word accuracy of plain nearest-key typing vs. the TrueTap engine on simulated fat-finger taps.
 * Run with `main` for a report; [SimulationTest] asserts the improvement.
 */
object Simulation {
    data class Result(val words: Int, val nearestCorrect: Int, val engineCorrect: Int) {
        val nearestPct get() = 100.0 * nearestCorrect / words
        val enginePct get() = 100.0 * engineCorrect / words
    }

    fun run(engine: TypingEngine, sigma: Float, offX: Float, offY: Float, seed: Int, learn: Boolean = false): Result {
        val keys = engine.spatial.keys
        val finger = FatFinger(keys, sigma, offX, offY, seed)
        var words = 0
        var nearest = 0
        var ours = 0
        for (sentence in SENTENCES) {
            var prev: String? = null
            for (word in sentence.split(' ')) {
                val letters = word.filter { it.isLetter() }
                val taps = letters.map { finger.tap(it) }
                val naive = taps.joinToString("") { keys[engine.spatial.nearestKey(it.x, it.y)].char.toString() }
                val literal = StringBuilder()
                for (i in taps.indices) {
                    val k = engine.resolve(taps.subList(0, i), taps[i])
                    literal.append(keys[k].char)
                }
                val result = engine.finishWord(taps, literal.toString(), prev)
                words++
                if (naive == letters) nearest++
                if (result.text.equals(word, ignoreCase = true)) ours++
                if (learn && result.text.equals(word, ignoreCase = true)) {
                    val clean = result.text.lowercase().filter { it.isLetter() }
                    if (clean.length == taps.size) clean.forEachIndexed { i, c -> engine.spatial.learn(c, taps[i]) }
                }
                prev = result.text
            }
        }
        return Result(words, nearest, ours)
    }

    /** Share of uncommon-but-real words that survive autocorrect when typed with care. */
    fun rareWordsKept(engine: TypingEngine, sigma: Float): Double {
        val rare = RARE_WORDS
        val finger = FatFinger(engine.spatial.keys, sigma, 0f, 0f, 99)
        var kept = 0
        for (w in rare) {
            val taps = w.map { finger.tap(it) }
            val literal = StringBuilder()
            for (i in taps.indices) literal.append(engine.spatial.keys[engine.resolve(taps.subList(0, i), taps[i])].char)
            if (engine.finishWord(taps, literal.toString(), null).text == w) kept++
        }
        return 100.0 * kept / rare.size
    }

    val RARE_WORDS = listOf(
        "quay", "fjord", "gnome", "lynx", "kiln", "jinx", "crypt", "nymph", "wry", "pique", "vex", "glib",
        "quell", "terse", "plume", "gauze", "sloth", "trite", "orchid", "walrus", "gravel", "anvil",
        "ember", "zest", "quip", "smirk", "deft", "brine",
    )

    fun newEngine(): TypingEngine =
        TypingEngine(SpatialModel(TestKeyboards.qwerty(), TouchModel()), TestKeyboards.dictionary())

    @JvmStatic
    fun main(args: Array<String>) {
        val engine = newEngine()
        val profiles = listOf(
            Triple("careful typist", 0.25f, 0f),
            Triple("average thumb", 0.35f, 0.1f),
            Triple("fat finger", 0.45f, 0.15f),
            Triple("very sloppy", 0.55f, 0.2f),
        )
        for ((name, sigma, off) in profiles) {
            var n = 0; var a = 0; var b = 0
            for (seed in 1..3) {
                val r = run(engine, sigma, off, off * 1.5f, seed)
                n += r.words; a += r.nearestCorrect; b += r.engineCorrect
            }
            println("%-15s sigma=%.2f  nearest-key %.1f%%  TrueTap %.1f%%".format(name, sigma, 100.0 * a / n, 100.0 * b / n))
        }
        val kept = rareWordsKept(engine, 0.25f)
        println("rare valid words typed carefully and left alone: %.1f%%".format(kept))
        // Learning: same sloppy typist with a strong personal offset, before and after adaptation.
        val learner = newEngine()
        val before = run(learner, 0.4f, 0.2f, 0.3f, 10)
        run(learner, 0.4f, 0.2f, 0.3f, 11, learn = true)
        run(learner, 0.4f, 0.2f, 0.3f, 12, learn = true)
        val after = run(learner, 0.4f, 0.2f, 0.3f, 10)
        println("offset typist   before learning %.1f%%  after learning %.1f%%  (nearest-key %.1f%%)".format(before.enginePct, after.enginePct, before.nearestPct))
    }
}
