package ui.widgets

import kotlin.test.Test
import kotlin.test.assertEquals

class RamGuidanceTest {
    @Test
    fun modernVanillaHasLowOptimalAndExcessiveZones() {
        val guidance = ramGuidance(16384, "1.21.1")
        assertEquals(RamStatus.Low, guidance.status(2048))
        assertEquals(RamStatus.Optimal, guidance.status(4096))
        assertEquals(RamStatus.Excessive, guidance.status(8192))
    }

    @Test
    fun systemReserveTakesPriorityOverGameRequirements() {
        val guidance = ramGuidance(4096, "1.21.1", modded = true)
        assertEquals(2048, guidance.safeMax)
        assertEquals(RamStatus.Low, guidance.status(2048))
        assertEquals(RamStatus.Excessive, guidance.status(4096))
    }

    @Test
    fun versionAndModsAdjustTheEstimate() {
        assertEquals(RamStatus.Optimal, ramGuidance(16384, "1.12.2").status(2048))
        assertEquals(RamStatus.Low, ramGuidance(16384, "1.12.2", modded = true).status(2048))
        assertEquals(RamStatus.Optimal, ramGuidance(16384, modded = true).status(8192))
    }
}
