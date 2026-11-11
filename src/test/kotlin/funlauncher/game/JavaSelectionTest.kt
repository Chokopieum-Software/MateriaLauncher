package funlauncher.game

import funlauncher.BuildType
import funlauncher.JavaInfo
import funlauncher.MinecraftBuild
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import java.nio.file.Paths
import kotlin.test.*

class JavaSelectionTest {
    private val manager = JavaManager(PathManager(Paths.get("build/test-java")))
    private fun java(version: Int, bits64: Boolean = true) = JavaInfo("java-$version", version, "test", bits64, false)

    @Test fun oldMinecraftDoesNotFallBackToNewerJava() {
        assertNull(manager.selectJava("1.12.2-forge-14.23.5.2864", listOf(java(25), java(17))))
        assertNull(manager.selectJava("1.12.2-forge-14.23.5.2864", listOf(java(8, false), java(25))))
        assertEquals(8, manager.selectJava("1.12.2-forge-14.23.5.2864", listOf(java(25), java(8)))?.version)
    }

    @Test fun modernMinecraftUsesItsRecommendedJava() {
        assertEquals(17, manager.selectJava("1.20.1-fabric-0.16.0", listOf(java(25), java(17)))?.version)
        assertEquals(21, manager.selectJava("1.21.1-neoforge-21.1.1", listOf(java(25), java(21)))?.version)
        assertNull(manager.selectJava("1.21.1", listOf(java(17))))
    }

    @Test fun explicitIncompatibleJavaForLegacyForgeHasClearError() {
        val build = MinecraftBuild("test", "1.12.2-forge-14.23.5.2864", BuildType.FORGE, "test")
        val failure = assertFailsWith<IllegalArgumentException> { manager.validateJavaForBuild(build, java(25)) }
        assertTrue(failure.message.orEmpty().contains("Java 8"))
        manager.validateJavaForBuild(build, java(8))
        assertFalse(manager.requiresJava8(build.copy(type = BuildType.FABRIC)))
        assertFalse(manager.requiresJava8(build.copy(version = "1.20.1-forge-47.2.0")))
    }
}
