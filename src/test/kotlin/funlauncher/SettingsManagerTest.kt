package funlauncher

import funlauncher.managers.PathManager
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsManagerTest {
    @Test
    fun savedSettingsSurviveReloadAndReplacement() = runBlocking {
        val directory = Files.createTempDirectory("materia-settings-test")
        try {
            val manager = SettingsManager(PathManager(directory))
            manager.saveSettings(AppSettings())
            val updated = AppSettings(theme = Theme.Dark, maxRamMb = 6144, javaArgs = "-XX:+UseG1GC")
            manager.saveSettings(updated)
            assertEquals(updated, SettingsManager(PathManager(directory)).loadSettings())
            Files.list(directory).use { assertEquals(1L, it.count()) }
        } finally {
            Files.deleteIfExists(directory.resolve("settings.json"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun writeFailureReachesCallerAndPreservesExistingData() = runBlocking {
        val directory = Files.createTempDirectory("materia-settings-failure")
        val blockedRoot = directory.resolve("blocked")
        try {
            blockedRoot.writeText("existing data")
            val manager = SettingsManager(PathManager(blockedRoot))
            assertFailsWith<java.io.IOException> { manager.saveSettings(AppSettings()) }
            assertEquals("existing data", blockedRoot.readText())
        } finally {
            Files.deleteIfExists(blockedRoot)
            Files.deleteIfExists(directory)
        }
    }
}
