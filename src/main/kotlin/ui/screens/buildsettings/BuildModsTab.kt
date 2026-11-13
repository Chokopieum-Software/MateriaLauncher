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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import kotlinx.coroutines.*
import java.io.File

// --- MODS ---
@Composable
internal fun ModsTab(build: MinecraftBuild) {
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
