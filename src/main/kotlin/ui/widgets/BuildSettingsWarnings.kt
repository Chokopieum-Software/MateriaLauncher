package ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal enum class WarningLevel { Yellow, Red }
internal data class BuildWarning(val level: WarningLevel, val message: String)

internal fun compareReleaseVersions(first: String, second: String): Int? {
    fun parts(value: String): List<Int>? {
        val release = value.substringBefore('-')
        if (!release.matches(Regex("\\d+(\\.\\d+)+"))) return null
        return release.split('.').map { it.toIntOrNull() ?: return null }
    }
    val a = parts(first) ?: return null
    val b = parts(second) ?: return null
    for (i in 0 until maxOf(a.size, b.size)) {
        val comparison = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
        if (comparison != 0) return comparison
    }
    return 0
}

internal fun buildSettingsWarnings(
    originalName: String, name: String,
    originalVersion: String, version: String,
    ramMb: Int, totalRamMb: Int, modded: Boolean,
    javaVersion: Int?, requiredJava: Int
): List<BuildWarning> = buildList {
    if (originalName != name.trim()) add(BuildWarning(WarningLevel.Yellow,
        "Название сборки изменено. При сохранении каталог сборки будет переименован."))
    val comparison = compareReleaseVersions(version, originalVersion)
    if (comparison != null && comparison > 0) add(BuildWarning(WarningLevel.Yellow,
        "Версия игры повышается: $originalVersion → $version. Проверьте совместимость модификаций и создайте резервную копию миров."))
    if (comparison != null && comparison < 0) add(BuildWarning(WarningLevel.Red,
        "Версия игры понижается: $originalVersion → $version. Открытие миров в более ранней версии может привести к повреждению или потере данных. Создайте резервную копию."))
    if (totalRamMb > 0) {
        val guidance = ramGuidance(totalRamMb, version, modded)
        when {
            ramMb > guidance.safeMax -> add(BuildWarning(WarningLevel.Red,
                "Выделено слишком много ОЗУ: $ramMb МБ из $totalRamMb МБ. Рекомендуемый предел с резервом для системы: ${guidance.safeMax} МБ. Возможны нехватка памяти и снижение производительности системы."))
            ramMb > guidance.optimalMax -> add(BuildWarning(WarningLevel.Yellow,
                "Объём ОЗУ выше рекомендуемого диапазона: $ramMb МБ. Увеличение лимита обычно не повышает производительность игры."))
        }
    }
    if (javaVersion != null && javaVersion < requiredJava) add(BuildWarning(WarningLevel.Red,
        "Выбрана Java $javaVersion. Для Minecraft $version требуется Java $requiredJava или новее. Игра может не запуститься."))
}.sortedByDescending { it.level.ordinal }

@Composable
internal fun BuildSettingsWarnings(warnings: List<BuildWarning>) {
    warnings.forEach { warning ->
        val color = if (warning.level == WarningLevel.Red) MaterialTheme.colorScheme.error else Color(0xFFE0A23C)
        Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Warning, contentDescription = if (warning.level == WarningLevel.Red) "Высокий риск" else "Предупреждение", tint = color)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (warning.level == WarningLevel.Red) "Высокий риск" else "Обратите внимание", color = color, style = MaterialTheme.typography.titleSmall)
                    Text(warning.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
