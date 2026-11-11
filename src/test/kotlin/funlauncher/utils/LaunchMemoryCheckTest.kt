package funlauncher.utils

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LaunchMemoryCheckTest {
    private val build = MinecraftBuild("Test", "1.21.1", BuildType.VANILLA, "test", maxRamMb = 8192)

    @Test
    fun heapCeilingAboveAvailableMemoryDoesNotAloneTriggerWarning() {
        assertNull(launchMemoryWarning(build, 16384, 6144))
    }

    @Test
    fun lowAvailableMemoryIncludesNativeJvmOverhead() {
        assertNotNull(launchMemoryWarning(build, 16384, 3200))
        assertNull(launchMemoryWarning(build, 16384, 3584))
    }

    @Test
    fun physicalMemoryNeedsSystemReserve() {
        assertNotNull(launchMemoryWarning(build.copy(maxRamMb = 16384), 16384, 12000))
    }

    @Test
    fun unavailableMetricsDoNotTriggerWarning() {
        assertNull(launchMemoryWarning(build, -1, 0))
        assertNull(launchMemoryWarning(build, 16384, -1))
        assertNull(launchMemoryWarning(build, 16384, 20000))
    }

    @Test
    fun olderVanillaAndModdedBuildsHaveDifferentEstimates() {
        assertNull(launchMemoryWarning(build.copy(version = "1.12.2"), 16384, 3000))
        assertNotNull(launchMemoryWarning(build.copy(type = BuildType.FABRIC), 16384, 4096))
    }
}
