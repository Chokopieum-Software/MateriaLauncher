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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

// --- WORLDS ---
@OptIn(ExperimentalPathApi::class)
@Composable
internal fun WorldsTab(build: MinecraftBuild) {
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
