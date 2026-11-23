package ui.screens.modifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSelectionDialog(
    versions: List<String>,
    selectedVersions: MutableList<String>,
    onDismissRequest: () -> Unit
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
                    text = "Выберите версии (только релизы)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(versions) { version ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (version in selectedVersions) selectedVersions.remove(version) else selectedVersions.add(version)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = version in selectedVersions,
                                onCheckedChange = {
                                    if (it) selectedVersions.add(version) else selectedVersions.remove(version)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(version, style = MaterialTheme.typography.bodyLarge)
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