package ui.screens.modifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import funlauncher.net.Project
import org.chokopieum.software.ui.MateriaDetailsDialog
import ui.widgets.ImageLoader

@Composable
fun ModificationDetails(
    project: Project,
    projectUrl: String,
    isLoadingDetails: Boolean,
    detailsError: String? = null,
    onDismiss: () -> Unit,
    onInstallClick: () -> Unit
) {
    MateriaDetailsDialog(
        title = project.title,
        closeLabel = "Закрыть информацию о проекте",
        onDismiss = onDismiss,
        actions = {
            Button(
                onClick = onInstallClick,
                enabled = !isLoadingDetails && detailsError == null,
                modifier = Modifier.height(48.dp)
            ) {
                if (isLoadingDetails) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Установить")
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(project.description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            // Галерея
            if (project.gallery.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                ) {
                    items(project.gallery) { galleryImage ->
                        val painter = ImageLoader.rememberImagePainterFromUrl(galleryImage.url)
                        if (painter != null) {
                            Image(
                                painter = painter,
                                contentDescription = galleryImage.title,
                                modifier = Modifier.fillParentMaxHeight().clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Описание
            Text("Описание", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            if (detailsError != null) Text(detailsError, color = MaterialTheme.colorScheme.error)
            if (isLoadingDetails && project.body.isBlank()) {
                Text("Загрузка описания…")
            } else key(project.id) {
                MarkdownDescription(
                    markdown = project.body,
                    base = projectUrl,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}
