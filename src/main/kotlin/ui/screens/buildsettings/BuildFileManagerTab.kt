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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import kotlinx.coroutines.*
import java.io.File

// A generic composable for list-based tabs (Mods, ResourcePacks, Worlds)
@Composable
internal fun <T> FileManagerTab(
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

// --- Common UI Components ---
@Composable
internal fun ItemIcon(icon: ImageBitmap?, name: String) {
    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = "Icon for $name", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Star, contentDescription = "Default Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DeleteButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
    }
}
