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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

// --- RESOURCE PACKS ---
@OptIn(ExperimentalPathApi::class)
@Composable
internal fun ResourcePacksTab(build: MinecraftBuild) {
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
