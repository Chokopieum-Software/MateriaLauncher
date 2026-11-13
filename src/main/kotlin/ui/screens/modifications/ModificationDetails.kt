package ui.screens.modifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import funlauncher.net.Project
import funlauncher.net.Version
import ui.widgets.ImageLoader

@Composable
fun ModificationDetails(
    project: Project,
    isLoadingDetails: Boolean,
    detailsError: String? = null,
    onInstallClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Заголовок и кнопка установки
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(project.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(Modifier.width(16.dp))
            
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

        Divider()
        Spacer(Modifier.height(16.dp))

        // Основная информация (скроллируемая)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
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
                    base = "https://modrinth.com/"+project.projectType+"/"+project.slug,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}
