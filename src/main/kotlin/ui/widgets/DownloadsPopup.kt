/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import funlauncher.net.DownloadManager
import funlauncher.net.DownloadTask

@Composable
fun DownloadsPopup(
    onDismissRequest: () -> Unit
) {
    // Позиционируем Popup относительно родительского контейнера
    val popupPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: androidx.compose.ui.unit.IntRect,
            windowSize: androidx.compose.ui.unit.IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            popupContentSize: androidx.compose.ui.unit.IntSize
        ): androidx.compose.ui.unit.IntOffset {
            return androidx.compose.ui.unit.IntOffset(
                x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                y = anchorBounds.bottom
            )
        }
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest
    ) {
        Card(
            modifier = Modifier
                .width(350.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Активные загрузки", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                if (DownloadManager.tasks.isEmpty()) {
                    Text("Нет активных загрузок.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(DownloadManager.tasks) { task ->
                            DownloadTaskItem(task)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(task: DownloadTask) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(task.status.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(task.progress.value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { DownloadManager.cancelTask(task.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Отменить загрузку",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val stats = task.transferStats.value
        Text(
            text = formatTransferStats(stats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { task.progress.value },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
