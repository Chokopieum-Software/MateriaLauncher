package ui.widgets

import funlauncher.net.TransferStats
import java.util.Locale

internal fun formatTransferStats(stats: TransferStats): String {
    val speed = when {
        stats.bytesPerSecond >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f МБ/с", stats.bytesPerSecond / (1024 * 1024))
        stats.bytesPerSecond >= 1024 -> String.format(Locale.ROOT, "%.0f КБ/с", stats.bytesPerSecond / 1024)
        else -> "${stats.bytesPerSecond.toLong()} Б/с"
    }
    val seconds = stats.secondsRemaining
    val remaining = when {
        seconds == null -> "Оставшееся время уточняется"
        seconds <= 0 -> "Файлы этапа получены"
        seconds < 60 -> "До конца этапа ≈ $seconds с"
        seconds < 3600 -> "До конца этапа ≈ ${seconds / 60} мин ${seconds % 60} с"
        else -> "До конца этапа ≈ ${seconds / 3600} ч ${(seconds % 3600) / 60} мин"
    }
    return "$speed · $remaining"
}
