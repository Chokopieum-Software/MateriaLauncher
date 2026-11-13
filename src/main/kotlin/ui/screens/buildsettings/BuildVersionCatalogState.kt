/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.runtime.*
import funlauncher.BuildType
import funlauncher.game.*
import funlauncher.managers.PathManager
import kotlinx.coroutines.*

internal data class BuildVersionCatalogState(
    val displayedMcVersions: List<String>,
    val fabricLoaderVersions: List<FabricVersion>,
    val applicableForgeVersions: List<ForgeVersion>,
    val quiltLoaderVersions: List<QuiltVersion>,
    val applicableNeoForgeVersions: List<NeoForgeVersion>,
    val isLoadingMc: Boolean,
    val isLoadingLoader: Boolean,
    val loaderError: String?,
    val refreshMc: () -> Unit,
    val refreshLoader: () -> Unit
)

@Composable
internal fun rememberBuildVersionCatalogState(
    pathManager: PathManager, selectedMcVersion: String, selectedBuildType: BuildType,
    selectedLoaderVersion: String, onSelectedLoaderVersionChange: (String) -> Unit
): BuildVersionCatalogState {
    val versionManager = remember { VersionManager(pathManager) }
    var allMcVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var fabricGameVersions by remember { mutableStateOf<List<FabricGameVersion>>(emptyList()) }
    var fabricLoaderVersions by remember { mutableStateOf<List<FabricVersion>>(emptyList()) }
    var forgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var applicableForgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var quiltGameVersions by remember { mutableStateOf<List<QuiltGameVersion>>(emptyList()) }
    var quiltLoaderVersions by remember { mutableStateOf<List<QuiltVersion>>(emptyList()) }
    var neoForgeVersions by remember { mutableStateOf<List<NeoForgeVersion>>(emptyList()) }
    var applicableNeoForgeVersions by remember { mutableStateOf<List<NeoForgeVersion>>(emptyList()) }
    var isLoadingMc by remember { mutableStateOf(true) }
    var isLoadingLoader by remember { mutableStateOf(false) }
    var catalogsReady by remember { mutableStateOf(false) }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var loaderRevision by remember { mutableIntStateOf(0) }
    var loaderError by remember { mutableStateOf<String?>(null) }

    val displayedMcVersions = remember(selectedBuildType, allMcVersions, fabricGameVersions, forgeVersions, quiltGameVersions, neoForgeVersions) {
        val releaseVersions = allMcVersions.filter { it.type == "release" }.map { it.id }
        val supported = when (selectedBuildType) {
            BuildType.FABRIC -> fabricGameVersions.mapTo(hashSetOf()) { it.version }
            BuildType.FORGE -> forgeVersions.mapTo(hashSetOf()) { it.mcVersion }
            BuildType.QUILT -> quiltGameVersions.mapTo(hashSetOf()) { it.version }
            BuildType.NEOFORGE -> neoForgeVersions.mapTo(hashSetOf()) { it.mcVersion }
            else -> null
        }
        if (supported == null) releaseVersions else releaseVersions.filter { it in supported }
    }

    LaunchedEffect(selectedMcVersion, selectedBuildType, loaderRevision,
        if (selectedBuildType == BuildType.FORGE || selectedBuildType == BuildType.NEOFORGE) catalogsReady else true) {
        loaderError = null
        fabricLoaderVersions = emptyList()
        quiltLoaderVersions = emptyList()
        applicableForgeVersions = emptyList()
        applicableNeoForgeVersions = emptyList()
        isLoadingLoader = false
        if (selectedMcVersion.isBlank() || selectedBuildType == BuildType.VANILLA) return@LaunchedEffect
        if (!catalogsReady && (selectedBuildType == BuildType.FORGE || selectedBuildType == BuildType.NEOFORGE)) return@LaunchedEffect
        isLoadingLoader = true
        try {
            when (selectedBuildType) {
                BuildType.FABRIC -> {
                    fabricLoaderVersions = versionManager.getFabricLoaderVersions(selectedMcVersion, forceRefresh = loaderRevision > 0)
                    if (selectedLoaderVersion.isBlank()) {
                        onSelectedLoaderVersionChange(fabricLoaderVersions.firstOrNull { it.stable }?.version ?: fabricLoaderVersions.firstOrNull()?.version ?: "")
                    }
                }
                BuildType.FORGE -> {
                    applicableForgeVersions = forgeVersions.filter { it.mcVersion == selectedMcVersion }
                    if (selectedLoaderVersion.isBlank()) {
                        val recommended = applicableForgeVersions.find { it.isRecommended }
                        val latest = applicableForgeVersions.find { it.isLatest }
                        onSelectedLoaderVersionChange(recommended?.forgeVersion ?: latest?.forgeVersion ?: applicableForgeVersions.firstOrNull()?.forgeVersion ?: "")
                    }
                }
                BuildType.QUILT -> {
                    quiltLoaderVersions = versionManager.getQuiltLoaderVersions(selectedMcVersion)
                    if (selectedLoaderVersion.isBlank()) {
                        onSelectedLoaderVersionChange(quiltLoaderVersions.firstOrNull()?.version ?: "")
                    }
                }
                BuildType.NEOFORGE -> {
                    applicableNeoForgeVersions = neoForgeVersions.filter { it.mcVersion == selectedMcVersion }
                    if (selectedLoaderVersion.isBlank()) {
                        onSelectedLoaderVersionChange(applicableNeoForgeVersions.firstOrNull()?.neoForgeVersion ?: "")
                    }
                }
                else -> {}
            }
            val empty = when (selectedBuildType) {
                BuildType.FABRIC -> fabricLoaderVersions.isEmpty()
                BuildType.FORGE -> applicableForgeVersions.isEmpty()
                BuildType.QUILT -> quiltLoaderVersions.isEmpty()
                BuildType.NEOFORGE -> applicableNeoForgeVersions.isEmpty()
                else -> false
            }
            if (empty) loaderError = "Для этой версии Minecraft список загрузчиков пуст. Повторите загрузку или выберите другую версию игры."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loaderError = "Не удалось загрузить версии загрузчика. Проверьте подключение и нажмите обновление справа."
        } finally {
            isLoadingLoader = false
        }
    }

    LaunchedEffect(catalogRevision, selectedBuildType) {
        isLoadingMc = true
        catalogsReady = false
        try {
            val mc = async(Dispatchers.IO) { versionManager.getMinecraftVersions() }
            val fabric = async(Dispatchers.IO) { if (selectedBuildType == BuildType.FABRIC) versionManager.getFabricGameVersions() else emptyList() }
            val forge = async(Dispatchers.IO) { if (selectedBuildType == BuildType.FORGE) versionManager.getForgeVersions() else emptyList() }
            val quilt = async(Dispatchers.IO) { if (selectedBuildType == BuildType.QUILT) versionManager.getQuiltGameVersions() else emptyList() }
            val neoforge = async(Dispatchers.IO) { if (selectedBuildType == BuildType.NEOFORGE) versionManager.getNeoForgeVersions() else emptyList() }
            allMcVersions = mc.await()
            fabricGameVersions = fabric.await()
            forgeVersions = forge.await()
            quiltGameVersions = quilt.await()
            neoForgeVersions = neoforge.await()
        } finally {
            catalogsReady = true
            isLoadingMc = false
        }
    }

    return BuildVersionCatalogState(displayedMcVersions, fabricLoaderVersions, applicableForgeVersions, quiltLoaderVersions, applicableNeoForgeVersions, isLoadingMc, isLoadingLoader, loaderError, { catalogRevision++ }, { loaderRevision++ })
}
