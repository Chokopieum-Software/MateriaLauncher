package ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal enum class RamStatus { Low, Optimal, Excessive }

internal data class RamGuidance(val minimum: Int, val optimalMax: Int, val safeMax: Int) {
    fun status(value: Int): RamStatus = when {
        value > safeMax -> RamStatus.Excessive
        value < minimum -> RamStatus.Low
        value > optimalMax -> RamStatus.Excessive
        else -> RamStatus.Optimal
    }
}

// Estimates heap needs; the actual requirement depends on mods and game settings.
internal fun ramGuidance(totalRam: Int, version: String? = null, modded: Boolean = false): RamGuidance {
    val minor = version?.substringBefore('-')?.split('.')?.getOrNull(1)?.toIntOrNull()
    val modern = minor == null || minor >= 18
    val minimum = if (modded) 4096 else if (modern) 3072 else 2048
    val preferredMax = if (modded) 8192 else if (modern) 6144 else 4096
    val safeMax = (totalRam - maxOf(2048, totalRam / 4)).coerceAtLeast(0)
    return RamGuidance(minimum, minOf(preferredMax, safeMax), safeMax)
}

@Composable
fun RamSelector(
    currentRam: Int,
    totalRam: Int,
    onRamChange: (Int) -> Unit,
    version: String? = null,
    modded: Boolean = false
) {
    val maximum = totalRam.coerceAtLeast(512)
    val minimum = minOf(1024, maximum)
    val guidance = remember(totalRam, version, modded) { ramGuidance(totalRam, version, modded) }
    val lowColor = Color(0xFFE0A23C)
    val optimalColor = Color(0xFF55AF75)
    val excessColor = MaterialTheme.colorScheme.error
    val status = guidance.status(currentRam)
    val color = when (status) {
        RamStatus.Low -> lowColor
        RamStatus.Optimal -> optimalColor
        RamStatus.Excessive -> excessColor
    }
    var input by remember(currentRam) { mutableStateOf(currentRam.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Выделение ОЗУ", modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = input,
                onValueChange = { text ->
                    if (text.all { it.isDigit() }) {
                        input = text
                        text.toIntOrNull()?.takeIf { it in minimum..maximum }?.let(onRamChange)
                    }
                },
                isError = input.toIntOrNull()?.let { it !in minimum..maximum } ?: true,
                suffix = { Text("МБ") },
                modifier = Modifier.width(140.dp),
                singleLine = true
            )
        }
        Slider(
            value = currentRam.coerceIn(minimum, maximum).toFloat(),
            onValueChange = { onRamChange(((it / 256).roundToInt() * 256).coerceIn(minimum, maximum)) },
            valueRange = minimum.toFloat()..maxOf(minimum + 1, maximum).toFloat(),
            enabled = maximum > minimum,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier.fillMaxWidth()
        )
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val span = (maximum - minimum).coerceAtLeast(1).toFloat()
            fun position(mb: Int) = ((mb - minimum) / span).coerceIn(0f, 1f) * size.width
            val lowEnd = position(minOf(guidance.minimum, guidance.safeMax))
            val optimalEnd = position(maxOf(minOf(guidance.minimum, guidance.safeMax), guidance.optimalMax))
            drawRect(lowColor, size = Size(lowEnd, size.height))
            drawRect(optimalColor, topLeft = Offset(lowEnd, 0f), size = Size(optimalEnd - lowEnd, size.height))
            drawRect(excessColor, topLeft = Offset(optimalEnd, 0f), size = Size(size.width - optimalEnd, size.height))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Недостаточно", color = lowColor, style = MaterialTheme.typography.labelSmall)
            Text("Оптимально", color = optimalColor, style = MaterialTheme.typography.labelSmall)
            Text("Избыточно", color = excessColor, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            when {
                currentRam > guidance.safeMax -> "Превышен рекомендуемый предел: системе и другим приложениям может не хватить памяти."
                status == RamStatus.Low -> "Недостаточный объём памяти: возможны снижение производительности и ошибки выделения памяти."
                status == RamStatus.Excessive -> "Избыточный объём памяти: дальнейшее увеличение обычно не повышает производительность игры."
                else -> "Оптимальный объём памяти: соответствует расчётным требованиям игры и сохраняет резерв для системы."
            },
            color = color, style = MaterialTheme.typography.bodySmall
        )
        Text(
            if (guidance.safeMax < guidance.minimum)
                "Объём оперативной памяти компьютера ниже расчётных требований. Рекомендуемый предел выделения: ${guidance.safeMax} МБ."
            else "Рекомендуемый диапазон: ${guidance.minimum}–${guidance.optimalMax} МБ. Всего в системе: $totalRam МБ. " +
                if (modded) "Сборкам с большим количеством модификаций может требоваться дополнительная память." else "Фактическое потребление зависит от настроек игры.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
