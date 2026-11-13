/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.runtime.*
import funlauncher.AppSettings
import funlauncher.BuildType
import funlauncher.MinecraftBuild

internal class BuildSettingsFormState(build: MinecraftBuild) {
    private val initialVersion = parseBuildVersion(build.version, build.type)
    val initialMcVersion = initialVersion.first
    var buildName by mutableStateOf(build.name)
    var selectedMcVersion by mutableStateOf(initialVersion.first)
        private set
    var selectedLoaderVersion by mutableStateOf(initialVersion.second)
    var selectedBuildType by mutableStateOf(build.type)
        private set
    var imagePath by mutableStateOf(build.imagePath)
    var selectedJavaPath by mutableStateOf<String?>(build.javaPath ?: "")
    var maxRam by mutableStateOf(build.maxRamMb ?: AppSettings().maxRamMb)
    var javaArgs by mutableStateOf(build.javaArgs ?: "")
    var envVars by mutableStateOf(build.envVars ?: "")

    fun selectMinecraftVersion(version: String) {
        if (version != selectedMcVersion) {
            selectedLoaderVersion = ""
            selectedMcVersion = version
        }
    }

    fun selectBuildType(type: BuildType) {
        if (type != selectedBuildType) {
            selectedLoaderVersion = ""
            selectedBuildType = type
        }
    }

    fun finalVersion(): String = when (selectedBuildType) {
        BuildType.FABRIC -> "$selectedMcVersion-fabric-$selectedLoaderVersion"
        BuildType.FORGE -> "$selectedMcVersion-forge-$selectedLoaderVersion"
        BuildType.QUILT -> "$selectedMcVersion-quilt-$selectedLoaderVersion"
        BuildType.NEOFORGE -> "$selectedMcVersion-neoforge-$selectedLoaderVersion"
        else -> selectedMcVersion
    }
}

@Composable
internal fun rememberBuildSettingsFormState(build: MinecraftBuild): BuildSettingsFormState =
    remember { BuildSettingsFormState(build) }

private fun parseBuildVersion(version: String, type: BuildType): Pair<String, String> {
    val delimiter = when (type) {
        BuildType.FABRIC -> "-fabric-"
        BuildType.FORGE -> "-forge-"
        BuildType.QUILT -> "-quilt-"
        BuildType.NEOFORGE -> "-neoforge-"
        else -> return version to ""
    }
    val parts = version.split(delimiter)
    return parts.getOrElse(0) { version } to parts.getOrElse(1) { "" }
}
