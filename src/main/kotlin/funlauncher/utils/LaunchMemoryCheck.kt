package funlauncher.utils

import funlauncher.AppSettings
import funlauncher.BuildType
import funlauncher.MinecraftBuild

/** A launch estimate, not a prediction of peak memory usage. All values are MiB. */
internal fun launchMemoryWarning(build: MinecraftBuild, totalMb: Long, availableMb: Long): String? {
    // Unsupported or inconsistent OS measurements must not produce a false warning.
    if (totalMb <= 0 || availableMb < 0 || availableMb > totalMb) return null
    val heapMb = (build.maxRamMb ?: AppSettings().maxRamMb).toLong()
    if (heapMb <= 0) return null
    val minor = build.version.substringBefore('-').split('.').getOrNull(1)?.toIntOrNull()
    val estimatedHeapMb = when {
        build.type != BuildType.VANILLA -> 4096L
        minor != null && minor < 18 -> 2048L
        else -> 3072L
    }
    // Xmx is a ceiling. Estimate the working heap and allow for native JVM memory.
    val estimatedProcessMb = minOf(heapMb, estimatedHeapMb) + 512L
    val reason = when {
        heapMb + 1024L > totalMb ->
            "Лимит памяти игры оставляет менее 1024 МБ для системы при полном использовании кучи."
        availableMb < estimatedProcessMb ->
            "Доступной памяти меньше ориентировочной потребности игры и JVM ($estimatedProcessMb МБ). Закройте другие приложения перед запуском."
        else -> return null
    }
    return "$reason\n\nВсего ОЗУ: $totalMb МБ. Доступно сейчас: $availableMb МБ. Лимит кучи Java: $heapMb МБ.\n\n" +
        "Оценка приблизительная: фактическое потребление зависит от модификаций и настроек игры. Продолжить запуск?"
}
