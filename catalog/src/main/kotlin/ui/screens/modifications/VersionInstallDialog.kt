package ui.screens.modifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import funlauncher.net.Version

@Composable
fun VersionInstallDialog(
    versions: List<Version>,
    onDismissRequest: () -> Unit,
    onInstallClick: (Version) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.7f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Выберите версию для установки",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (versions.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Нет подходящих версий.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(versions) { version ->
                            DialogVersionCard(version, onInstallClick)
                        }
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

@Composable
private fun DialogVersionCard(version: Version, onInstallClick: (Version) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(version.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Версия: ${version.versionNumber}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                version.gameVersions.forEach {
                    Badge(contentColor = MaterialTheme.colorScheme.onPrimaryContainer, containerColor = MaterialTheme.colorScheme.primaryContainer) { Text(it, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                }
                version.loaders.forEach {
                    Badge(contentColor = MaterialTheme.colorScheme.onSecondaryContainer, containerColor = MaterialTheme.colorScheme.secondaryContainer) { Text(it, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = { onInstallClick(version) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Установить")
            }
        }
    }
}