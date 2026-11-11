package ui.theme

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemThemeTest {
    @Test
    fun observesChangesWithoutEmittingDuplicatesOrUnavailableReadings() = runBlocking {
        val readings = listOf(null, false, false, null, true, true, false)
        var index = 0
        val result = withTimeout(5000) {
            pollSystemTheme(intervalMillis = 1) { readings[(index++).coerceAtMost(readings.lastIndex)] }
                .take(3).toList()
        }
        assertEquals(listOf(false, true, false), result)
    }
}
