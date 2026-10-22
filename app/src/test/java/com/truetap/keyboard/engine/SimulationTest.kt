package com.truetap.keyboard.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationTest {
    @Test
    fun beatsNearestKeyForFatFingers() {
        val r = Simulation.run(Simulation.newEngine(), sigma = 0.45f, offX = 0.15f, offY = 0.22f, seed = 7)
        println("fat finger: nearest-key ${r.nearestPct}% vs engine ${r.enginePct}%")
        assertTrue(r.enginePct > r.nearestPct + 40)
        assertTrue(r.enginePct > 80)
    }

    @Test
    fun leavesCarefulTypingAlone() {
        val r = Simulation.run(Simulation.newEngine(), sigma = 0.2f, offX = 0f, offY = 0f, seed = 3)
        assertTrue("careful typing accuracy ${r.enginePct}", r.enginePct > 97)
        assertTrue(Simulation.rareWordsKept(Simulation.newEngine(), 0.25f) >= 85.0)
    }
}
