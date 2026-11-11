package funlauncher.net

import kotlin.math.ceil

data class TransferStats(val bytesPerSecond: Double = 0.0, val secondsRemaining: Long? = null)

/** Counts network traffic separately from remaining work, so retries do not inflate completion. */
class TransferMeter(private val clock: () -> Long = System::nanoTime) {
    private val remaining = mutableMapOf<String, Long?>()
    private val samples = ArrayDeque<Pair<Long, Long>>()
    private var received = 0L
    private var estimateAvailable = false

    @Synchronized
    fun beginPhase(files: Map<String, Long?>, estimateAvailable: Boolean = true) {
        remaining.clear()
        remaining.putAll(files)
        this.estimateAvailable = estimateAvailable
        samples.clear()
        samples.addLast(clock() to received)
    }

    @Synchronized
    fun received(id: String, bytes: Long) {
        if (bytes <= 0) return
        received += bytes
        remaining[id]?.let { remaining[id] = (it - bytes).coerceAtLeast(0) }
    }

    @Synchronized
    fun retry(id: String, size: Long?) { remaining[id] = size }

    @Synchronized
    fun complete(id: String) { remaining.remove(id) }

    @Synchronized
    fun snapshot(): TransferStats {
        val now = clock()
        if (samples.isEmpty()) samples.addLast(now to received)
        while (samples.size > 1 && now - samples.first().first > 5_000_000_000L &&
            now - samples[1].first >= 5_000_000_000L) samples.removeFirst()
        val first = samples.first()
        val elapsed = (now - first.first) / 1_000_000_000.0
        val speed = if (elapsed >= 0.25) (received - first.second) / elapsed else 0.0
        samples.addLast(now to received)
        val eta = if (estimateAvailable && remaining.values.none { it == null } && speed > 0) {
            ceil(remaining.values.sumOf { it ?: 0L } / speed).toLong()
        } else null
        return TransferStats(speed, eta)
    }
}
