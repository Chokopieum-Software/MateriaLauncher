/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.AwtWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.akuleshov7.ktoml.Toml
import funlauncher.AppSettings
import funlauncher.BuildType
import funlauncher.JavaInfo
import funlauncher.MinecraftBuild
import funlauncher.game.*
import funlauncher.managers.BuildManager
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import ui.widgets.RamSelector
import ui.widgets.ImageLoader
import ui.widgets.BuildSettingsWarnings
import ui.widgets.buildSettingsWarnings
import ui.widgets.BuildWarning
import ui.widgets.WarningLevel
import ui.widgets.LaunchOptionsFields
import funlauncher.utils.validateLaunchOptions
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.Color // Added import

private enum class SettingsTab {
    Main, Mods, Worlds, ResourcePacks
}

private fun getTabName(tab: SettingsTab): String {
    return when (tab) {
        SettingsTab.Main -> "Основные"
        SettingsTab.Mods -> "Моды"
        SettingsTab.Worlds -> "Миры"
        SettingsTab.ResourcePacks -> "Ресурспаки"
    }
}

// --- Data Classes & Parsers ---

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

// -- Mods --
@Serializable private data class FabricModJson(val id: String, val version: String, val name: String? = null, val icon: String? = null)
@Serializable data class ModsToml(val mods: List<ModTomlInfo> = emptyList())
@Serializable data class ModTomlInfo(val modId: String, val version: String, val displayName: String, val logoFile: String? = null)
data class ModInfo(val id: String, val name: String, val version: String, val icon: ImageBitmap?, val file: File, val isEnabled: Boolean)

// -- Resource Packs --
data class ResourcePackInfo(val name: String, val icon: ImageBitmap?, val file: File, val isEnabled: Boolean)

// -- Worlds --
data class WorldInfo(val name: String, val icon: ImageBitmap?, val file: File)


private suspend fun parseModFile(modFile: File): ModInfo = withContext(Dispatchers.IO) {
    val isEnabled = !modFile.name.endsWith(".disabled")
    val actualFile = if (isEnabled) modFile else File(modFile.parent, modFile.name.removeSuffix(".disabled"))
    try {
        if (!actualFile.exists()) throw IllegalStateException("Original mod file not found for ${modFile.name}")
        ZipFile(actualFile).use { zip ->
            zip.getEntry("fabric.mod.json")?.let {
                val fabricMod = json.decodeFromString<FabricModJson>(zip.getInputStream(it).bufferedReader().readText())
                val imageBitmap = fabricMod.icon?.let { iconPath -> zip.readImage(iconPath) }
                return@withContext ModInfo(fabricMod.id, fabricMod.name ?: fabricMod.id, fabricMod.version, imageBitmap, modFile, isEnabled)
            }
            zip.getEntry("META-INF/mods.toml")?.let {
                val modsToml = Toml.decodeFromString(ModsToml.serializer(), zip.getInputStream(it).bufferedReader().readText())
                modsToml.mods.firstOrNull()?.let { modInfoToml ->
                    val imageBitmap = modInfoToml.logoFile?.let { iconPath -> zip.readImage(iconPath) }
                    return@withContext ModInfo(modInfoToml.modId, modInfoToml.displayName, modInfoToml.version, imageBitmap, modFile, isEnabled)
                }
            }
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { e.printStackTrace() }
    return@withContext ModInfo(modFile.nameWithoutExtension.removeSuffix(".jar"), modFile.name, "N/A", null, modFile, isEnabled)
}

internal suspend fun parseResourcePack(packFile: File): ResourcePackInfo = withContext(Dispatchers.IO) {
    val isEnabled = !packFile.name.endsWith(".disabled")
    val actualFile = packFile
    var packName = actualFile.name.removeSuffix(".disabled").removeSuffix(".zip")
    var icon: ImageBitmap? = null

    try {
        if (actualFile.isDirectory) {
            val metaFile = File(actualFile, "pack.mcmeta")
            val iconFile = File(actualFile, "pack.png")
            if (metaFile.exists()) json.decodeFromString<PackMcMeta>(metaFile.readText()).pack.description.toPackDescription().takeIf { it.isNotBlank() }?.let { packName = it }
            if (iconFile.exists()) icon = iconFile.readBytes().toImageBitmap()
        } else if (actualFile.isFile) {
            ZipFile(actualFile).use { zip ->
                zip.getEntry("pack.mcmeta")?.let {
                    json.decodeFromString<PackMcMeta>(zip.getInputStream(it).bufferedReader().readText()).pack.description.toPackDescription().takeIf { it.isNotBlank() }?.let { packName = it }
                }
                zip.getEntry("pack.png")?.let { icon = zip.readImage("pack.png") }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) { e.printStackTrace() }
    return@withContext ResourcePackInfo(packName, icon, packFile, isEnabled)
}

private suspend fun parseWorld(worldDir: File): WorldInfo = withContext(Dispatchers.IO) {
    var icon: ImageBitmap? = null
    try {
        val iconFile = File(worldDir, "icon.png")
        if (iconFile.exists()) {
            icon = iconFile.readBytes().toImageBitmap()
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { e.printStackTrace() }
    // TODO: Read level name from level.dat NBT file
    return@withContext WorldInfo(worldDir.name, icon, worldDir)
}

private fun ZipFile.readImage(path: String): ImageBitmap? = getEntry(path)?.let {
    runCatching { getInputStream(it).readBytes().toImageBitmap() }.getOrNull()
}
private fun ByteArray.toImageBitmap(): ImageBitmap = org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()

private fun openFolder(path: String) = runCatching { Desktop.getDesktop().open(File(path)) }


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BuildSettingsScreen(
    build: MinecraftBuild,
    globalSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (newName: String, newVersion: String, newType: BuildType, newImagePath: String?, javaPath: String?, maxRam: Int?, javaArgs: String?, envVars: String?) -> Unit,
    pathManager: PathManager
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val dismiss = { visibleState.targetState = false }
    LaunchedEffect(visibleState.currentState) { if (!visibleState.currentState && !visibleState.targetState) onDismiss() }

    Dialog(onDismissRequest = dismiss) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 8 }
        ) {
            BuildSettingsScreenWithoutDialog(
                build = build,
                globalSettings = globalSettings,
                pathManager = pathManager,
                onDismiss = dismiss,
                onSave = onSave
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BuildSettingsScreenWithoutDialog(
    build: MinecraftBuild,
    globalSettings: AppSettings,
    pathManager: PathManager,
    onDismiss: () -> Unit,
    onSave: (newName: String, newVersion: String, newType: BuildType, newImagePath: String?, javaPath: String?, maxRam: Int?, javaArgs: String?, envVars: String?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.Main) }

    // --- State for Main Tab ---
    var buildName by remember { mutableStateOf(build.name) }
    val (initialMcVersion, initialLoaderVersion) = remember(build.version, build.type) { parseBuildVersion(build.version, build.type) }
    var selectedMcVersion by remember { mutableStateOf(initialMcVersion) }
    var selectedLoaderVersion by remember { mutableStateOf(initialLoaderVersion) }
    var selectedBuildType by remember { mutableStateOf(build.type) }
    val visibleTabs = SettingsTab.entries.filter { tab ->
        tab != SettingsTab.Mods || selectedBuildType != BuildType.VANILLA
    }
    LaunchedEffect(selectedBuildType) {
        if (selectedTab !in visibleTabs) selectedTab = SettingsTab.Main
    }
    var imagePath by remember { mutableStateOf(build.imagePath) }
    var selectedJavaPath by remember { mutableStateOf<String?>(build.javaPath ?: "") }
    var maxRam by remember { mutableStateOf(build.maxRamMb ?: AppSettings().maxRamMb) }
    var javaArgs by remember { mutableStateOf(build.javaArgs ?: "") }
    var envVars by remember { mutableStateOf(build.envVars ?: "") }

    val javaManager = remember(pathManager) { JavaManager(pathManager) }
    val javaSelection = selectedJavaPath.orEmpty()
    val javaCheck by produceState<Pair<String, JavaInfo?>>("" to null, javaSelection) {
        value = javaSelection to if (javaSelection.isBlank()) null
            else runCatching { javaManager.getJavaInfo(javaSelection) }.getOrNull()
    }
    val totalRamMb = remember {
        ((ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / (1024 * 1024)).toInt()
    }
    val launchOptions = validateLaunchOptions(javaArgs, envVars, maxRam)
    val warnings = buildSettingsWarnings(
        build.name, buildName, initialMcVersion, selectedMcVersion,
        maxRam, totalRamMb, selectedBuildType != BuildType.VANILLA,
        javaCheck.second?.takeIf { javaCheck.first == javaSelection }?.version,
        javaManager.getRecommendedJavaVersion(selectedMcVersion)
    ) + launchOptions.risks.map { BuildWarning(WarningLevel.Red, it) }
    var confirmHighRisk by remember { mutableStateOf(false) }
    val saveSettings = {
        val finalVersion = when (selectedBuildType) {
            BuildType.FABRIC -> "$selectedMcVersion-fabric-$selectedLoaderVersion"
            BuildType.FORGE -> "$selectedMcVersion-forge-$selectedLoaderVersion"
            BuildType.QUILT -> "$selectedMcVersion-quilt-$selectedLoaderVersion"
            BuildType.NEOFORGE -> "$selectedMcVersion-neoforge-$selectedLoaderVersion"
            else -> selectedMcVersion
        }
        onSave(buildName, finalVersion, selectedBuildType, imagePath, selectedJavaPath, maxRam, javaArgs, envVars)
    }
    if (confirmHighRisk) {
        AlertDialog(
            onDismissRequest = { confirmHighRisk = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Подтверждение изменений с высоким риском") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Обнаружены следующие риски:")
                    warnings.filter { it.level == WarningLevel.Red }.forEach { Text("• ${it.message}") }
                    Text("Сохранить настройки, несмотря на указанные риски?")
                }
            },
            confirmButton = {
                Button(onClick = { confirmHighRisk = false; saveSettings() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Сохранить, несмотря на риски")
                }
            },
            dismissButton = { TextButton(onClick = { confirmHighRisk = false }) { Text("Вернуться к настройкам") } }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showOpenFolderButton = selectedTab in listOf(SettingsTab.Mods, SettingsTab.Worlds, SettingsTab.ResourcePacks)
                Box(modifier = Modifier.size(48.dp)) {
                    if (showOpenFolderButton) {
                        TooltipArea(tooltip = { Surface(shape = RoundedCornerShape(4.dp), shadowElevation = 4.dp) { Text("Открыть папку", modifier = Modifier.padding(8.dp)) } }) {
                            IconButton(onClick = {
                                val folderToOpen = when (selectedTab) {
                                    SettingsTab.Mods -> "mods"
                                    SettingsTab.Worlds -> "saves"
                                    SettingsTab.ResourcePacks -> "resourcepacks"
                                    else -> ""
                                }
                                if (folderToOpen.isNotEmpty()) {
                                    File(build.installPath, folderToOpen).apply { if (!exists()) mkdirs() }.let { openFolder(it.absolutePath) }
                                }
                            }) { Icon(Icons.Default.Folder, contentDescription = "Открыть папку") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss) { Text("Отмена") }
                    Button(enabled = javaCheck.first == javaSelection && launchOptions.valid &&
                        (selectedBuildType == BuildType.VANILLA || selectedLoaderVersion.isNotBlank()), onClick = {
                        if (warnings.any { it.level == WarningLevel.Red }) confirmHighRisk = true
                        else saveSettings()
                    }) { Text("Сохранить") }
                }
            }
        }
    ) { paddingValues ->
        Row(Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier.width(190.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleTabs.forEach { tab ->
                    NavigationDrawerItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(getTabName(tab)) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    SettingsTab.Main -> Icons.Default.Settings
                                    SettingsTab.Mods -> Icons.Default.Extension
                                    SettingsTab.Worlds -> Icons.Default.Public
                                    SettingsTab.ResourcePacks -> Icons.Default.Texture
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
            when (selectedTab) {
                SettingsTab.Main -> MainSettingsTab(
                    warnings = warnings,
                    build = build,
                    globalSettings = globalSettings,
                    buildName = buildName, onBuildNameChange = { buildName = it },
                    selectedMcVersion = selectedMcVersion, onSelectedMcVersionChange = { selectedMcVersion = it },
                    selectedLoaderVersion = selectedLoaderVersion, onSelectedLoaderVersionChange = { selectedLoaderVersion = it },
                    selectedBuildType = selectedBuildType, onSelectedBuildTypeChange = { selectedBuildType = it },
                    imagePath = imagePath, onImagePathChange = { imagePath = it },
                    selectedJavaPath = selectedJavaPath, onSelectedJavaPathChange = { selectedJavaPath = it },
                    maxRam = maxRam, onMaxRamChange = { maxRam = it },
                    javaArgs = javaArgs, onJavaArgsChange = { javaArgs = it },
                    envVars = envVars, onEnvVarsChange = { envVars = it },
                    pathManager = pathManager
                )
                SettingsTab.Mods -> ModsTab(build = build)
                SettingsTab.Worlds -> WorldsTab(build = build)
                SettingsTab.ResourcePacks -> ResourcePacksTab(build = build)
            }
            }
        }
    }
}

// A generic composable for list-based tabs (Mods, ResourcePacks, Worlds)
@Composable
private fun <T> FileManagerTab(
    folderName: String,
    build: MinecraftBuild,
    fileFilter: (File, String) -> Boolean,
    parser: suspend (File) -> T,
    emptyText: String,
    itemContent: @Composable LazyItemScope.(T, () -> Unit) -> Unit
) {
    var items by remember { mutableStateOf<List<T>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        isLoading = true
        val dir = withContext(Dispatchers.IO) { File(build.installPath, folderName).also { if (!it.exists()) it.mkdirs() } }
        val files = withContext(Dispatchers.IO) { dir.listFiles(fileFilter)?.toList() ?: emptyList() }
        items = files.map { scope.async { parser(it) } }.awaitAll()
        isLoading = false
    }

    fun removeItem(item: T) {
        items = items.filterNot { it == item }
    }

    LaunchedEffect(build.installPath) { refresh() }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyText) }
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { item ->
                        itemContent(item) { removeItem(item) }
                    }
                }
                VerticalScrollbar(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), adapter = rememberScrollbarAdapter(listState))
            }
        }
    }
}

// --- MODS ---
@Composable
private fun ModsTab(build: MinecraftBuild) {
    var modToDelete by remember { mutableStateOf<ModInfo?>(null) }
    val scope = rememberCoroutineScope()

    fun deleteMod(mod: ModInfo, onDeleted: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            if (mod.file.delete()) {
                onDeleted()
            }
        }
    }

    FileManagerTab(
        folderName = "mods",
        build = build,
        fileFilter = { _, name -> name.endsWith(".jar", true) || name.endsWith(".jar.disabled", true) },
        parser = { file -> parseModFile(file) },
        emptyText = "Папка 'mods' пуста."
    ) { modInfo, onDeleted ->
        ModListItem(
            modInfo = modInfo,
            onEnableChange = { isEnabled ->
                scope.launch(Dispatchers.IO) {
                    val oldFile = modInfo.file
                    val newFile = if (isEnabled) File(oldFile.parent, oldFile.name.removeSuffix(".disabled")) else File(oldFile.parent, "${oldFile.name}.disabled")
                    oldFile.renameTo(newFile)
                    // The list won't auto-refresh here, which is a limitation of this simplified generic approach.
                    // For a better UX, a full refresh would be needed, or a more complex state management.
                }
            },
            onDeleteRequest = { modToDelete = modInfo }
        )
    }

    modToDelete?.let { mod ->
        AlertDialog(
            onDismissRequest = { modToDelete = null },
            title = { Text("Удалить мод?") },
            text = { Text("Вы уверены, что хотите удалить файл '${mod.file.name}'? Это действие необратимо.") },
            confirmButton = { Button(onClick = { deleteMod(mod) { modToDelete = null } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") } },
            dismissButton = { Button(onClick = { modToDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun ModListItem(modInfo: ModInfo, onEnableChange: (Boolean) -> Unit, onDeleteRequest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = if (modInfo.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = modInfo.isEnabled, onCheckedChange = onEnableChange)
            ItemIcon(modInfo.icon, modInfo.name)
            Column(modifier = Modifier.weight(1f)) {
                Text(modInfo.name, style = MaterialTheme.typography.bodyLarge)
                Text("v${modInfo.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DeleteButton(onDeleteRequest)
        }
    }
}

// --- RESOURCE PACKS ---
@OptIn(ExperimentalPathApi::class)
@Composable
private fun ResourcePacksTab(build: MinecraftBuild) {
    var packToDelete by remember { mutableStateOf<ResourcePackInfo?>(null) }
    val scope = rememberCoroutineScope()

    fun deletePack(pack: ResourcePackInfo, onDeleted: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            if (pack.file.isDirectory) pack.file.toPath().deleteRecursively() else pack.file.delete()
            onDeleted()
        }
    }

    FileManagerTab(
        folderName = "resourcepacks",
        build = build,
        fileFilter = { file, name -> file.isDirectory || name.endsWith(".zip", true) || name.endsWith(".zip.disabled", true) },
        parser = { file -> parseResourcePack(file) },
        emptyText = "Папка 'resourcepacks' пуста."
    ) { packInfo, onDeleted ->
        ResourcePackListItem(
            packInfo = packInfo,
            onEnableChange = { isEnabled ->
                scope.launch(Dispatchers.IO) {
                    val oldFile = packInfo.file
                    val suffix = if (oldFile.isDirectory) "" else ".zip"
                    val newFile = if (isEnabled) File(oldFile.parent, oldFile.name.removeSuffix(".disabled")) else File(oldFile.parent, "${oldFile.name.removeSuffix(suffix)}.disabled")
                    oldFile.renameTo(newFile)
                }
            },
            onDeleteRequest = { packToDelete = packInfo }
        )
    }

    packToDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { packToDelete = null },
            title = { Text("Удалить ресурспак?") },
            text = { Text("Вы уверены, что хотите удалить '${pack.file.name}'? Это действие необратимо.") },
            confirmButton = { Button(onClick = { deletePack(pack) { packToDelete = null } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") } },
            dismissButton = { Button(onClick = { packToDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun ResourcePackListItem(packInfo: ResourcePackInfo, onEnableChange: (Boolean) -> Unit, onDeleteRequest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = if (packInfo.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = packInfo.isEnabled, onCheckedChange = onEnableChange)
            ItemIcon(packInfo.icon, packInfo.name)
            Text(packInfo.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            DeleteButton(onDeleteRequest)
        }
    }
}

// --- WORLDS ---
@OptIn(ExperimentalPathApi::class)
@Composable
private fun WorldsTab(build: MinecraftBuild) {
    var worldToDelete by remember { mutableStateOf<WorldInfo?>(null) }
    val scope = rememberCoroutineScope()

    fun deleteWorld(world: WorldInfo, onDeleted: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            world.file.toPath().deleteRecursively()
            onDeleted()
        }
    }

    FileManagerTab(
        folderName = "saves",
        build = build,
        fileFilter = { file, _ -> file.isDirectory },
        parser = { file -> parseWorld(file) },
        emptyText = "Папка 'saves' пуста."
    ) { worldInfo, onDeleted ->
        WorldListItem(
            worldInfo = worldInfo,
            onDeleteRequest = { worldToDelete = worldInfo }
        )
    }

    worldToDelete?.let { world ->
        AlertDialog(
            onDismissRequest = { worldToDelete = null },
            title = { Text("Удалить мир?") },
            text = { Text("Вы уверены, что хотите удалить мир '${world.name}'? Это действие необратимо.") },
            confirmButton = { Button(onClick = { deleteWorld(world) { worldToDelete = null } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") } },
            dismissButton = { Button(onClick = { worldToDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun WorldListItem(worldInfo: WorldInfo, onDeleteRequest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemIcon(worldInfo.icon, worldInfo.name)
            Text(worldInfo.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            DeleteButton(onDeleteRequest)
        }
    }
}

// --- Common UI Components ---
@Composable
private fun ItemIcon(icon: ImageBitmap?, name: String) {
    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = "Icon for $name", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Star, contentDescription = "Default Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MainSettingsTab(
    warnings: List<BuildWarning>,
    build: MinecraftBuild, globalSettings: AppSettings,
    buildName: String, onBuildNameChange: (String) -> Unit,
    selectedMcVersion: String, onSelectedMcVersionChange: (String) -> Unit,
    selectedLoaderVersion: String, onSelectedLoaderVersionChange: (String) -> Unit,
    selectedBuildType: BuildType, onSelectedBuildTypeChange: (BuildType) -> Unit,
    imagePath: String?, onImagePathChange: (String?) -> Unit,
    selectedJavaPath: String?, onSelectedJavaPathChange: (String?) -> Unit,
    maxRam: Int, onMaxRamChange: (Int) -> Unit,
    javaArgs: String, onJavaArgsChange: (String) -> Unit,
    envVars: String, onEnvVarsChange: (String) -> Unit,
    pathManager: PathManager
) {
    val scope = rememberCoroutineScope()
    val versionManager = remember { VersionManager(pathManager) }
    val buildManager = remember { BuildManager(pathManager) }
    val javaManager = remember { JavaManager(pathManager) }

    var javaInstallations by remember { mutableStateOf<List<JavaInfo>>(emptyList()) }
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

    val totalSystemMemory = remember { (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / (1024 * 1024) }

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

    LaunchedEffect(Unit) {
        javaInstallations = javaManager.findJavaInstallations().let { it.launcher + it.system }
    }

    LaunchedEffect(catalogRevision) {
        isLoadingMc = true
        catalogsReady = false
        try {
            val mc = async(Dispatchers.IO) { versionManager.getMinecraftVersions() }
            val fabric = async(Dispatchers.IO) { versionManager.getFabricGameVersions() }
            val forge = async(Dispatchers.IO) { versionManager.getForgeVersions() }
            val quilt = async(Dispatchers.IO) { versionManager.getQuiltGameVersions() }
            val neoforge = async(Dispatchers.IO) { versionManager.getNeoForgeVersions() }
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

    run {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BuildSettingsWarnings(warnings)
            ImageSelectionGroup(
                imagePath = imagePath,
                onImagePathChange = { selectedFile ->
                    scope.launch(Dispatchers.IO) {
                        val coversDir = buildManager.getLauncherDataPath().resolve("covers")
                        Files.createDirectories(coversDir)
                        val newPath = coversDir.resolve(selectedFile.name)
                        Files.copy(selectedFile.toPath(), newPath, StandardCopyOption.REPLACE_EXISTING)
                        onImagePathChange(newPath.toString())
                    }
                }
            )
            OutlinedTextField(value = buildName, onValueChange = onBuildNameChange, label = { Text("Название установки") }, modifier = Modifier.fillMaxWidth())
            VersionSelectionGroup(
                mcVersions = (listOf(selectedMcVersion).filter { it.isNotBlank() } + displayedMcVersions).distinct(),
                fabricVersions = fabricLoaderVersions,
                forgeVersions = applicableForgeVersions,
                quiltVersions = quiltLoaderVersions,
                neoForgeVersions = applicableNeoForgeVersions,
                selectedMcVersion = selectedMcVersion,
                onMcVersionSelected = { version ->
                    if (version != selectedMcVersion) {
                        onSelectedLoaderVersionChange("")
                        onSelectedMcVersionChange(version)
                    }
                },
                selectedLoaderVersion = selectedLoaderVersion,
                onLoaderVersionSelected = onSelectedLoaderVersionChange,
                buildType = selectedBuildType,
                onBuildTypeSelected = { type ->
                    if (type != selectedBuildType) {
                        onSelectedLoaderVersionChange("")
                        onSelectedBuildTypeChange(type)
                    }
                },
                isLoadingMc = isLoadingMc,
                isLoadingLoader = isLoadingLoader,
                onRefreshMc = { catalogRevision++ },
                onRefreshLoader = { loaderRevision++ }
            )
            loaderError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            JavaSelectionGroup(javaInstallations, selectedJavaPath, onSelectedJavaPathChange)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RamSelector(maxRam, totalSystemMemory.toInt(), onMaxRamChange, selectedMcVersion, selectedBuildType != BuildType.VANILLA)
            }
            LaunchOptionsFields(javaArgs, onJavaArgsChange, envVars, onEnvVarsChange, maxRam)
            Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun ImageSelectionGroup(imagePath: String?, onImagePathChange: (File) -> Unit) {
    var showFileDialog by remember { mutableStateOf(false) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val painter = ImageLoader.rememberImagePainter(imagePath, previewSize)
    if (showFileDialog) {
        AwtWindow(create = { object : FileDialog(null as Frame?, "Выберите обложку", FileDialog.LOAD) {
            override fun setVisible(b: Boolean) {
                super.setVisible(b)
                if (b) files.firstOrNull()?.let(onImagePathChange)
                showFileDialog = false
            }
        } }, dispose = { it.dispose() })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Обложка сборки", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(128.dp, 80.dp).onSizeChanged { previewSize = it }.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                if (painter != null) Image(painter = painter, contentDescription = "Превью обложки", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.BrokenImage, contentDescription = "Нет изображения", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { showFileDialog = true }) {
                Icon(Icons.Default.Image, contentDescription = "Выбрать изображение", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Выбрать...")
            }
        }
    }
}

@Composable
private fun VersionSelectionGroup(
    mcVersions: List<String>,
    fabricVersions: List<FabricVersion>,
    forgeVersions: List<ForgeVersion>,
    quiltVersions: List<QuiltVersion>,
    neoForgeVersions: List<NeoForgeVersion>,
    selectedMcVersion: String,
    onMcVersionSelected: (String) -> Unit,
    selectedLoaderVersion: String,
    onLoaderVersionSelected: (String) -> Unit,
    buildType: BuildType,
    onBuildTypeSelected: (BuildType) -> Unit,
    isLoadingMc: Boolean,
    isLoadingLoader: Boolean,
    onRefreshMc: () -> Unit,
    onRefreshLoader: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Версия игры", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (isLoadingMc) CircularProgressIndicator(Modifier.size(24.dp))
            else IconButton(onClick = onRefreshMc) { Icon(Icons.Default.Refresh, "Обновить версии Minecraft") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) { Dropdown("Minecraft", mcVersions, selectedMcVersion, onMcVersionSelected, mcVersions.isNotEmpty()) }
            Box(modifier = Modifier.weight(1f)) { Dropdown("Тип", BuildType.entries.map { it.name }, buildType.name, { onBuildTypeSelected(BuildType.valueOf(it)) }) }
        }
        if (buildType != BuildType.VANILLA) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    when (buildType) {
                        BuildType.FABRIC -> Dropdown("Fabric Loader", fabricVersions.map { it.version }, selectedLoaderVersion, onLoaderVersionSelected, fabricVersions.isNotEmpty())
                        BuildType.FORGE -> {
                            val items = forgeVersions.map {
                                val label = buildString {
                                    append(it.forgeVersion)
                                    if (it.isRecommended) append(" (рекомендуемая)")
                                    else if (it.isLatest) append(" (последняя)")
                                }
                                label to it.forgeVersion
                            }
                            DropdownWithLabels("Forge", items, selectedLoaderVersion, onLoaderVersionSelected, items.isNotEmpty())
                        }
                        BuildType.QUILT -> Dropdown("Quilt Loader", quiltVersions.map { it.version }, selectedLoaderVersion, onLoaderVersionSelected, quiltVersions.isNotEmpty())
                        BuildType.NEOFORGE -> Dropdown("NeoForge", neoForgeVersions.map { it.neoForgeVersion }, selectedLoaderVersion, onLoaderVersionSelected, neoForgeVersions.isNotEmpty())
                        else -> {}
                    }
                }
                if (isLoadingLoader) CircularProgressIndicator(Modifier.size(24.dp).padding(start = 8.dp))
                else IconButton(onClick = onRefreshLoader, enabled = selectedMcVersion.isNotBlank()) { Icon(Icons.Default.Refresh, "Обновить версии загрузчика") }
            }
        }
    }
}

@Composable
private fun JavaSelectionGroup(javaInstallations: List<JavaInfo>, selectedPath: String?, onPathSelected: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Версия Java", style = MaterialTheme.typography.titleMedium)
        JavaSelection(javaInstallations, selectedPath, onPathSelected)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, items: List<String>, selected: String, onSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(selected, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), enabled = enabled)
        ExposedDropdownMenu(expanded, { expanded = false }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onSelected(item); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownWithLabels(label: String, items: List<Pair<String, String>>, selectedValue: String, onSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.find { it.second == selectedValue }?.first ?: selectedValue
    ExposedDropdownMenuBox(expanded, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(selectedLabel, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), enabled = enabled)
        ExposedDropdownMenu(expanded, { expanded = false }) {
            items.forEach { (itemLabel, itemValue) ->
                DropdownMenuItem(text = { Text(itemLabel) }, onClick = { onSelected(itemValue); expanded = false })
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JavaSelection(installedJavas: List<JavaInfo>, selectedPath: String?, onPathSelected: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val automaticText = "Автоматически (Рекомендуется)"
    val currentDisplayName = when (selectedPath) {
        null, "" -> automaticText
        else -> installedJavas.find { it.path == selectedPath }?.displayName ?: "Путь не найден"
    }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(currentDisplayName, {}, readOnly = true, label = { Text("Версия Java") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(automaticText) }, onClick = { onPathSelected(""); expanded = false })
            HorizontalDivider()
            installedJavas.forEach { javaInfo -> DropdownMenuItem(text = { Text(javaInfo.displayName) }, onClick = { onPathSelected(javaInfo.path); expanded = false }) }
        }
    }
}
