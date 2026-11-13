/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.managers.PathManager
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import ui.widgets.RamSelector
import ui.widgets.BuildSettingsWarnings
import ui.widgets.BuildWarning
import ui.widgets.LaunchOptionsFields

@Composable
internal fun MainSettingsTab(
    form: BuildSettingsFormState,
    warnings: List<BuildWarning>,
    pathManager: PathManager,
    totalRamMb: Int
) = with(form) {
    val scope = rememberCoroutineScope()
    var loadJavaInstallations by remember { mutableStateOf(false) }
    val javaInstallations = rememberBuildJavaInstallations(pathManager, enabled = loadJavaInstallations)
    val catalog = rememberBuildVersionCatalogState(pathManager, selectedMcVersion, selectedBuildType, selectedLoaderVersion, { selectedLoaderVersion = it })
    val mcVersions = remember(selectedMcVersion, catalog.displayedMcVersions) {
        (listOf(selectedMcVersion).filter { it.isNotBlank() } + catalog.displayedMcVersions).distinct()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "warnings") { BuildSettingsWarnings(warnings) }
        item(key = "cover") {
            ImageSelectionGroup(
                imagePath = imagePath,
                onImagePathChange = { selectedFile ->
                    scope.launch(Dispatchers.IO) {
                        val coversDir = pathManager.getAppDataDirectory().resolve("covers")
                        Files.createDirectories(coversDir)
                        val newPath = coversDir.resolve(selectedFile.name)
                        Files.copy(selectedFile.toPath(), newPath, StandardCopyOption.REPLACE_EXISTING)
                        withContext(Dispatchers.Main) { imagePath = newPath.toString() }
                    }
                }
            )
        }
        item(key = "name") {
            OutlinedTextField(value = buildName, onValueChange = { buildName = it }, label = { Text("Название установки") }, modifier = Modifier.fillMaxWidth())
        }
        item(key = "version") {
            VersionSelectionGroup(
                mcVersions = mcVersions,
                fabricVersions = catalog.fabricLoaderVersions,
                forgeVersions = catalog.applicableForgeVersions,
                quiltVersions = catalog.quiltLoaderVersions,
                neoForgeVersions = catalog.applicableNeoForgeVersions,
                selectedMcVersion = selectedMcVersion,
                onMcVersionSelected = ::selectMinecraftVersion,
                selectedLoaderVersion = selectedLoaderVersion,
                onLoaderVersionSelected = { selectedLoaderVersion = it },
                buildType = selectedBuildType,
                onBuildTypeSelected = ::selectBuildType,
                isLoadingMc = catalog.isLoadingMc,
                isLoadingLoader = catalog.isLoadingLoader,
                onRefreshMc = catalog.refreshMc,
                onRefreshLoader = catalog.refreshLoader
            )
        }
        catalog.loaderError?.let { error ->
            item(key = "loader-error") { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        item(key = "java") {
            LaunchedEffect(Unit) { loadJavaInstallations = true }
            JavaSelectionGroup(javaInstallations, selectedJavaPath, { selectedJavaPath = it })
        }
        item(key = "ram") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RamSelector(maxRam, totalRamMb, { maxRam = it }, selectedMcVersion, selectedBuildType != BuildType.VANILLA)
            }
        }
        item(key = "launch-options") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LaunchOptionsFields(javaArgs, { javaArgs = it }, envVars, { envVars = it }, maxRam)
            }
        }
    }
}
