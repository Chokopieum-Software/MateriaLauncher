package ui.screens.modifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import funlauncher.net.ModificationInstaller
import funlauncher.MinecraftBuild
import ui.widgets.ImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSelectionDialog(
    builds: List<MinecraftBuild>,
    onDismissRequest: () -> Unit,
    selectedBuild: MinecraftBuild? = null,
    version: funlauncher.net.Version? = null,
    directory: String = "",
    onBuildSelected: (MinecraftBuild?) -> Unit
) {
    val candidates = remember(builds.toList(), version, directory) {
        builds.map { build ->
            build to (version?.let { ModificationInstaller.incompatibilityReasons(it, build, directory) } ?: emptyList())
        }.sortedBy { it.second.isNotEmpty() }
    }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(0.9f).fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Выберите сборку",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(candidates, key = { it.first.name }) { (build, reasons) ->
                        BuildSelectionCard(build, selected = build == selectedBuild, reasons = reasons) {
                            onBuildSelected(build)
                        }
                    }
                }
                if (builds.isEmpty()) {
                    Text("Пока нет созданных сборок", style = MaterialTheme.typography.bodyMedium)
                } else if (version != null && candidates.none { it.second.isEmpty() }) {
                    Text("Нет совместимых сборок", color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (version == null && selectedBuild != null) {
                        TextButton(onClick = { onBuildSelected(null) }) { Text("Сбросить выбор") }
                    }
                    Button(onClick = onDismissRequest) { Text("Закрыть") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildSelectionCard(
    build: MinecraftBuild,
    selected: Boolean,
    reasons: List<String>,
    onClick: () -> Unit
) {
    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    val painter = ImageLoader.rememberImagePainter(build.imagePath, coverSize)

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Card(
        onClick = onClick,
        enabled = reasons.isEmpty(),
        interactionSource = interaction,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, if (selected || hovered) MaterialTheme.colorScheme.primary else Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hovered) 12.dp else 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).onSizeChanged { coverSize = it }) {
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
                    ))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = build.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Minecraft ${ModificationInstaller.minecraftVersion(build)} · ${build.type.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                reasons.forEach { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
