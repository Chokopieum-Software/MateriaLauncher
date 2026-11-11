package funlauncher.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransferMeterTest {
    @Test
    fun measuresParallelTrafficAndRemainingBytes() {
        var now = 0L
        val meter = TransferMeter { now }
        meter.beginPhase(mapOf("a" to 1000L, "b" to 1000L))
        meter.received("a", 300)
        meter.received("b", 200)
        now = 1_000_000_000
        assertEquals(TransferStats(500.0, 3), meter.snapshot())
        meter.complete("a") // The rest may already be cached.
        assertEquals(2L, meter.snapshot().secondsRemaining)
    }

    @Test
    fun retryRestoresRemainingWorkAndStalledTransferLosesSpeed() {
        var now = 0L
        val meter = TransferMeter { now }
        meter.beginPhase(mapOf("a" to 1000L))
        meter.received("a", 500)
        now = 1_000_000_000
        meter.snapshot()
        meter.retry("a", 1000L)
        assertEquals(2L, meter.snapshot().secondsRemaining)
        now = 7_000_000_000
        assertEquals(0.0, meter.snapshot().bytesPerSecond)
        assertNull(meter.snapshot().secondsRemaining)
    }

    @Test
    fun unknownSizeHasSpeedWithoutInventingEta() {
        var now = 0L
        val meter = TransferMeter { now }
        meter.beginPhase(mapOf("a" to null))
        meter.received("a", 100)
        now = 1_000_000_000
        assertEquals(TransferStats(100.0, null), meter.snapshot())
    }
}
