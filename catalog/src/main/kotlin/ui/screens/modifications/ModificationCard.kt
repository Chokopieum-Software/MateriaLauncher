package ui.screens.modifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import funlauncher.net.Hit
import ui.widgets.ImageLoader

@Composable
fun ModificationCard(hit: Hit, onClick: () -> Unit) {
    val painter = ImageLoader.rememberImagePainterFromUrl(hit.iconUrl)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                painter?.let {
                    Image(
                        painter = it,
                        contentDescription = "${hit.title} icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Placeholder icon",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium)
                Text("by ${hit.author}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    hit.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
        }
    }
}
