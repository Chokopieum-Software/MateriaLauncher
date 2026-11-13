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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import java.awt.Desktop
import java.io.File
import ui.widgets.BuildWarning
import ui.widgets.WarningLevel

private fun openFolder(path: String) = runCatching { Desktop.getDesktop().open(File(path)) }

@Composable
internal fun BuildSettingsRiskDialog(warnings: List<BuildWarning>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            Button(onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Сохранить, несмотря на риски")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Вернуться к настройкам") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BuildSettingsActions(selectedTab: SettingsTab, build: MinecraftBuild, canSave: Boolean,
    onDismiss: () -> Unit, onSave: () -> Unit) {
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
            Button(enabled = canSave, onClick = onSave) { Text("Сохранить") }
        }
    }
}
