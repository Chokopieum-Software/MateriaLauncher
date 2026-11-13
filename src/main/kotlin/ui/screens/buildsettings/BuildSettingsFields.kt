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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.AwtWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import funlauncher.BuildType
import funlauncher.JavaInfo
import funlauncher.game.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import ui.widgets.ImageLoader

@Composable
internal fun ImageSelectionGroup(imagePath: String?, onImagePathChange: (File) -> Unit) {
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
internal fun VersionSelectionGroup(
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
internal fun JavaSelectionGroup(javaInstallations: List<JavaInfo>, selectedPath: String?, onPathSelected: (String?) -> Unit) {
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
