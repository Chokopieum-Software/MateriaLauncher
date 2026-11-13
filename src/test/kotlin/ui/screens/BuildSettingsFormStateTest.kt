package ui.screens

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import kotlin.test.*

class BuildSettingsFormStateTest {
    private fun build(type: BuildType = BuildType.FABRIC, version: String = "1.20.1-fabric-0.16.0") =
        MinecraftBuild("Test", version, type, "builds/test", imagePath = "cover.png",
            javaPath = "java", maxRamMb = 6144, javaArgs = "-Dtest=true", envVars = "TEST=1")

    @Test fun preservesVersionsForEveryBuildType() {
        for (type in BuildType.entries) {
            val version = if (type == BuildType.VANILLA) "1.20.1" else "1.20.1-${type.name.lowercase()}-1.2.3"
            val form = BuildSettingsFormState(build(type, version))
            assertEquals("1.20.1", form.selectedMcVersion)
            assertEquals(if (type == BuildType.VANILLA) "" else "1.2.3", form.selectedLoaderVersion)
            assertEquals(version, form.finalVersion())
        }
    }

    @Test fun changesResetLoaderButPreserveOtherFields() {
        val form = BuildSettingsFormState(build())
        form.buildName = "Edited"
        form.selectMinecraftVersion("1.21.1")
        assertEquals("", form.selectedLoaderVersion)
        form.selectedLoaderVersion = "2.0"
        form.selectBuildType(BuildType.FORGE)
        assertEquals("", form.selectedLoaderVersion)
        form.selectedLoaderVersion = "3.0"
        assertEquals("1.21.1-forge-3.0", form.finalVersion())
        assertEquals("Edited", form.buildName)
        assertEquals("cover.png", form.imagePath)
        assertEquals("java", form.selectedJavaPath)
        assertEquals(6144, form.maxRam)
        assertEquals("-Dtest=true", form.javaArgs)
        assertEquals("TEST=1", form.envVars)
    }

    @Test fun reselectingCurrentVersionAndTypePreservesLoader() {
        val form = BuildSettingsFormState(build())
        form.selectMinecraftVersion(form.selectedMcVersion)
        form.selectBuildType(form.selectedBuildType)
        assertEquals("0.16.0", form.selectedLoaderVersion)
        form.selectBuildType(BuildType.VANILLA)
        assertEquals("1.20.1", form.finalVersion())
    }
}
