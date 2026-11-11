package ui.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import java.nio.file.Path

class BuildSettingsWarningsTest {
    @Test
    fun javaRequirementsIncludeTransitionalMinecraftVersions() {
        val manager = JavaManager(PathManager(Path.of(".")))
        assertEquals(8, manager.getRecommendedJavaVersion("1.16.5"))
        assertEquals(16, manager.getRecommendedJavaVersion("1.17.1"))
        assertEquals(17, manager.getRecommendedJavaVersion("1.20.4"))
        assertEquals(21, manager.getRecommendedJavaVersion("1.20.5"))
        assertEquals(21, manager.getRecommendedJavaVersion("1.21.1"))
    }
    private fun warnings(name: String = "Test", version: String = "1.20.1", ram: Int = 4096, java: Int? = 17) =
        buildSettingsWarnings("Test", name, "1.20.1", version, ram, 16384, false, java, 17)

    @Test
    fun unchangedSettingsHaveNoWarnings() {
        assertTrue(warnings().isEmpty())
    }

    @Test
    fun renamingUpgradingAndModerateMemoryAreYellow() {
        assertEquals(listOf(WarningLevel.Yellow), warnings(name = "Renamed").map { it.level })
        assertEquals(listOf(WarningLevel.Yellow), warnings(version = "1.21.1").map { it.level })
        assertEquals(listOf(WarningLevel.Yellow), warnings(ram = 8192).map { it.level })
    }

    @Test
    fun downgradingCriticalMemoryAndOldJavaAreRed() {
        assertEquals(listOf(WarningLevel.Red), warnings(version = "1.19.4").map { it.level })
        assertEquals(listOf(WarningLevel.Red), warnings(ram = 14336).map { it.level })
        assertEquals(listOf(WarningLevel.Red), warnings(java = 8).map { it.level })
    }

    @Test
    fun automaticJavaIsNotReportedAsOutdatedAndRedComesFirst() {
        assertTrue(warnings(java = null).isEmpty())
        assertEquals(listOf(WarningLevel.Red, WarningLevel.Yellow), warnings(name = "Renamed", java = 8).map { it.level })
    }

    @Test
    fun releaseVersionsAreComparedNumerically() {
        assertTrue(compareReleaseVersions("1.10", "1.9")!! > 0)
        assertEquals(0, compareReleaseVersions("1.20", "1.20.0"))
        assertNull(compareReleaseVersions("24w14a", "1.20"))
    }
}
