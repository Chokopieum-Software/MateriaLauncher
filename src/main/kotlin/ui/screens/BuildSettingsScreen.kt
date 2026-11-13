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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import funlauncher.AppSettings
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import ui.widgets.buildSettingsWarnings
import ui.widgets.BuildWarning
import ui.widgets.WarningLevel
import funlauncher.utils.validateLaunchOptions
import androidx.compose.ui.graphics.Color

internal enum class SettingsTab {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BuildSettingsScreen(
    build: MinecraftBuild,
    globalSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (newName: String, newVersion: String, newType: BuildType, newImagePath: String?, javaPath: String?, maxRam: Int?, javaArgs: String?, envVars: String?) -> Unit,
    pathManager: PathManager,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 1000.dp)
                .fillMaxWidth().heightIn(max = 760.dp).fillMaxHeight(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 16.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть настройки сборки")
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(build.name, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить сборку", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    BuildSettingsScreenWithoutDialog(
                        build = build,
                        globalSettings = globalSettings,
                        pathManager = pathManager,
                        onDismiss = onDismiss,
                        onSave = onSave
                    )
                }
            }
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

    val form = rememberBuildSettingsFormState(build)
    val visibleTabs = SettingsTab.entries.filter { tab ->
        tab != SettingsTab.Mods || form.selectedBuildType != BuildType.VANILLA
    }
    LaunchedEffect(form.selectedBuildType) {
        if (selectedTab !in visibleTabs) selectedTab = SettingsTab.Main
    }

    val javaManager = remember(pathManager) { JavaManager(pathManager) }
    val javaSelection = form.selectedJavaPath.orEmpty()
    val javaCheck = rememberBuildJavaCheck(javaManager, javaSelection)
    val totalRamMb = rememberBuildTotalRamMb()
    val launchOptions = validateLaunchOptions(form.javaArgs, form.envVars, form.maxRam)
    val warnings = buildSettingsWarnings(
        build.name, form.buildName, form.initialMcVersion, form.selectedMcVersion,
        form.maxRam, totalRamMb, form.selectedBuildType != BuildType.VANILLA,
        javaCheck.second?.takeIf { javaCheck.first == javaSelection }?.version,
        javaManager.getRecommendedJavaVersion(form.selectedMcVersion)
    ) + launchOptions.risks.map { BuildWarning(WarningLevel.Red, it) }
    var confirmHighRisk by remember { mutableStateOf(false) }
    val saveSettings = {
        onSave(form.buildName, form.finalVersion(), form.selectedBuildType, form.imagePath, form.selectedJavaPath, form.maxRam, form.javaArgs, form.envVars)
    }
    if (confirmHighRisk) {
        BuildSettingsRiskDialog(warnings, onDismiss = { confirmHighRisk = false },
            onConfirm = { confirmHighRisk = false; saveSettings() })
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BuildSettingsActions(
                selectedTab = selectedTab, build = build, onDismiss = onDismiss,
                canSave = javaCheck.first == javaSelection && launchOptions.valid &&
                    (form.selectedBuildType == BuildType.VANILLA || form.selectedLoaderVersion.isNotBlank()),
                onSave = {
                    if (warnings.any { it.level == WarningLevel.Red }) confirmHighRisk = true
                    else saveSettings()
                }
            )
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
                        form = form,
                        warnings = warnings,
                        pathManager = pathManager,
                        totalRamMb = totalRamMb
                    )
                    SettingsTab.Mods -> ModsTab(build = build)
                    SettingsTab.Worlds -> WorldsTab(build = build)
                    SettingsTab.ResourcePacks -> ResourcePacksTab(build = build)
                }
            }
        }
    }
}
