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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import ui.widgets.ImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSelectionDialog(
    builds: List<MinecraftBuild>,
    onDismissRequest: () -> Unit,
    onBuildSelected: (MinecraftBuild?) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Выберите сборку для фильтрации",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Card(
                            onClick = { onBuildSelected(null) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Не выбрана", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    items(builds) { build ->
                        BuildSelectionCard(build = build, onClick = { onBuildSelected(build) })
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSelectionCard(
    build: MinecraftBuild,
    onClick: () -> Unit
) {
    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    val painter = ImageLoader.rememberImagePainter(build.imagePath, coverSize)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                        Brush.verticalGradient(listOf(Color(0xFF606060), Color(0xFF303030)))
                    ))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = build.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatBuildVersion(build),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatBuildVersion(build: MinecraftBuild): String {
    return when (build.type) {
        BuildType.VANILLA -> build.version
        BuildType.FABRIC -> {
            val parts = build.version.split("-fabric-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.FORGE -> {
            val parts = build.version.split("-forge-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.QUILT -> {
            val parts = build.version.split("-quilt-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.NEOFORGE -> {
            val parts = build.version.split("-neoforge-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
    }
}
