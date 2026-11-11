/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import ui.widgets.MorphingCreationPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import funlauncher.*
import funlauncher.game.*
import funlauncher.managers.PathManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBuildDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, BuildType, String?) -> Unit,
    pathManager: PathManager,
    sourceBounds: Rect? = null
) {
    var name by remember { mutableStateOf("") }
    var selectedMcVersion by remember { mutableStateOf<MinecraftVersion?>(null) }
    var selectedLoaderVersion by remember { mutableStateOf("") }
    var modLoader by remember { mutableStateOf(BuildType.VANILLA) }

    var versionListExpanded by remember { mutableStateOf(true) }
    var versionsLoading by remember { mutableStateOf(true) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var versionsRetry by remember { mutableStateOf(0) }
    var loaderError by remember { mutableStateOf<String?>(null) }

    var vanillaVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var fabricLoaderVersions by remember { mutableStateOf<List<FabricVersion>>(emptyList()) }
    var forgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var applicableForgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var quiltLoaderVersions by remember { mutableStateOf<List<QuiltVersion>>(emptyList()) }
    var neoForgeVersions by remember { mutableStateOf<List<NeoForgeVersion>>(emptyList()) }
    var applicableNeoForgeVersions by remember { mutableStateOf<List<NeoForgeVersion>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }

    val versionManager = remember { VersionManager(pathManager) }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var pendingCreation by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(visibleState.isIdle, visibleState.currentState, visibleState.targetState) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            val create = pendingCreation
            pendingCreation = null
            onDismiss()
            create?.invoke()
        }
    }

    LaunchedEffect(versionsRetry) {
        versionsLoading = true
        versionsError = null
        try {
            vanillaVersions = versionManager.getMinecraftVersions()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            versionsError = "Не удалось загрузить список версий. Проверьте подключение к сети."
        } finally {
            versionsLoading = false
        }
    }

    LaunchedEffect(selectedMcVersion, modLoader) {
        selectedLoaderVersion = ""
        loaderError = null
        isLoading = false
        val mcVersion = selectedMcVersion ?: return@LaunchedEffect
        if (!mcVersion.supportsModLoaders()) {
            modLoader = BuildType.VANILLA
            return@LaunchedEffect
        }
        if (modLoader == BuildType.VANILLA) return@LaunchedEffect
        isLoading = true
        try {
            when (modLoader) {
                BuildType.FABRIC -> {
                    fabricLoaderVersions = versionManager.getFabricLoaderVersions(mcVersion.id)
                    selectedLoaderVersion = fabricLoaderVersions.firstOrNull { it.stable }?.version ?: fabricLoaderVersions.firstOrNull()?.version ?: ""
                }
                BuildType.FORGE -> {
                    if (forgeVersions.isEmpty()) forgeVersions = versionManager.getForgeVersions()
                    applicableForgeVersions = forgeVersions.filter { it.mcVersion == mcVersion.id }
                    selectedLoaderVersion = (applicableForgeVersions.find { it.isRecommended }
                        ?: applicableForgeVersions.find { it.isLatest } ?: applicableForgeVersions.firstOrNull())?.forgeVersion ?: ""
                }
                BuildType.QUILT -> {
                    quiltLoaderVersions = versionManager.getQuiltLoaderVersions(mcVersion.id)
                    selectedLoaderVersion = quiltLoaderVersions.firstOrNull()?.version ?: ""
                }
                BuildType.NEOFORGE -> {
                    if (neoForgeVersions.isEmpty()) neoForgeVersions = versionManager.getNeoForgeVersions()
                    applicableNeoForgeVersions = neoForgeVersions.filter { it.mcVersion == mcVersion.id }
                    selectedLoaderVersion = applicableNeoForgeVersions.firstOrNull()?.neoForgeVersion ?: ""
                }
                else -> Unit
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loaderError = "Не удалось загрузить версии загрузчика. Выберите другой загрузчик или повторите выбор."
        } finally {
            isLoading = false
        }
    }

    MorphingCreationPanel(visibleState, sourceBounds) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Новая сборка", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))

                    InlineVersionPicker(
                        versions = vanillaVersions, selected = selectedMcVersion,
                        expanded = versionListExpanded, loading = versionsLoading, error = versionsError,
                        onRetry = { versionsRetry++ }, onExpandedChange = { versionListExpanded = it },
                        onSelected = {
                            selectedMcVersion = it
                            modLoader = BuildType.VANILLA
                            selectedLoaderVersion = ""
                            versionListExpanded = false
                        }
                    )
                    Spacer(Modifier.height(16.dp))

                    if (selectedMcVersion?.supportsModLoaders() == false && !versionListExpanded) {
                        Text("Для альфа- и бета-версий используется Vanilla без загрузчика модов.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible = selectedMcVersion?.supportsModLoaders() == true && !versionListExpanded) {
                        Column {
                            Text("Загрузчик модов", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                val types = BuildType.entries
                                types.forEachIndexed { index, type ->
                                    val shape = when (index) {
                                        0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                                        types.lastIndex -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                        else -> RoundedCornerShape(0.dp)
                                    }
                                    val isSelected = modLoader == type
                                    OutlinedButton(
                                        onClick = { modLoader = type },
                                        modifier = Modifier.weight(1f),
                                        shape = shape,
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        )
                                    ) {
                                        Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            if (modLoader != BuildType.VANILLA) {
                                var loaderExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(expanded = loaderExpanded, onExpandedChange = { loaderExpanded = !loaderExpanded }) {
                                    OutlinedTextField(
                                        value = selectedLoaderVersion.ifEmpty { if (isLoading) "Загрузка..." else "Нет совместимой версии" },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Версия загрузчика") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loaderExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        enabled = selectedMcVersion != null
                                    )
                                    ExposedDropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                                        if (isLoading) {
                                            DropdownMenuItem(text = { Text("Загрузка...") }, onClick = {}, enabled = false)
                                        } else {
                                            when (modLoader) {
                                                BuildType.FABRIC -> {
                                                    fabricLoaderVersions.forEach { v ->
                                                        DropdownMenuItem(text = { Text(v.version) }, onClick = {
                                                            selectedLoaderVersion = v.version
                                                            loaderExpanded = false
                                                        })
                                                    }
                                                }
                                                BuildType.FORGE -> {
                                                    applicableForgeVersions.forEach { v ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                val label = buildString {
                                                                    append(v.forgeVersion)
                                                                    if (v.isRecommended) append(" (рекомендуемая)")
                                                                    else if (v.isLatest) append(" (последняя)")
                                                                }
                                                                Text(label)
                                                            },
                                                            onClick = {
                                                                selectedLoaderVersion = v.forgeVersion
                                                                loaderExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                                BuildType.QUILT -> {
                                                    quiltLoaderVersions.forEach { v ->
                                                        DropdownMenuItem(text = { Text(v.version) }, onClick = {
                                                            selectedLoaderVersion = v.version
                                                            loaderExpanded = false
                                                        })
                                                    }
                                                }
                                                BuildType.NEOFORGE -> {
                                                    applicableNeoForgeVersions.forEach { v ->
                                                        DropdownMenuItem(
                                                            text = { Text(v.neoForgeVersion) },
                                                            onClick = {
                                                                selectedLoaderVersion = v.neoForgeVersion
                                                                loaderExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                if (loaderError != null) Text(loaderError!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { visibleState.targetState = false }) { Text("Отмена") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val finalVersion = when (modLoader) {
                                    BuildType.FABRIC -> "${selectedMcVersion!!.id}-fabric-$selectedLoaderVersion"
                                    BuildType.FORGE -> "${selectedMcVersion!!.id}-forge-$selectedLoaderVersion"
                                    BuildType.QUILT -> "${selectedMcVersion!!.id}-quilt-$selectedLoaderVersion"
                                    BuildType.NEOFORGE -> "${selectedMcVersion!!.id}-neoforge-$selectedLoaderVersion"
                                    else -> selectedMcVersion!!.id
                                }
                                pendingCreation = { onAdd(name, finalVersion, modLoader, null) }
                                visibleState.targetState = false
                            },
                            enabled = name.isNotBlank() && selectedMcVersion != null && !versionListExpanded && !isLoading &&
                                (modLoader == BuildType.VANILLA || selectedLoaderVersion.isNotBlank())
                        ) { Text("Создать") }
                    }
                }
            }
    }
}
