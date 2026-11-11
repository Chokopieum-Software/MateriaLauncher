package ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.game.MinecraftVersion

private enum class VersionCategory(val type: String, val label: String) {
    Release("release", "Релизы"), Snapshot("snapshot", "Снапшоты"),
    Alpha("old_alpha", "Альфа"), Beta("old_beta", "Бета")
}

internal fun MinecraftVersion.supportsModLoaders() = type != "old_alpha" && type != "old_beta"

@Composable
internal fun InlineVersionPicker(
    versions: List<MinecraftVersion>, selected: MinecraftVersion?, expanded: Boolean,
    loading: Boolean, error: String?, onRetry: () -> Unit,
    onExpandedChange: (Boolean) -> Unit, onSelected: (MinecraftVersion) -> Unit
) {
    var category by remember { mutableStateOf(VersionCategory.Release) }
    val filtered = remember(versions, category) { versions.filter { it.type == category.type } }
    val listState = rememberLazyListState()
    LaunchedEffect(category) { listState.scrollToItem(0) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(selected?.let { "Minecraft ${it.id}" } ?: "Версия Minecraft", style = MaterialTheme.typography.titleMedium)
            if (selected != null) TextButton(onClick = { onExpandedChange(!expanded) }) {
                Text(if (expanded) "Свернуть" else "Изменить")
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VersionCategory.entries.forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item.label) })
                    }
                }
                when {
                    loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
                    error != null -> {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Повторить загрузку") }
                    }
                    filtered.isEmpty() -> Text("В этой категории нет доступных версий.", Modifier.padding(vertical = 16.dp))
                    else -> OutlinedCard {
                        LazyColumn(Modifier.fillMaxWidth().height(260.dp), state = listState) {
                            items(filtered, key = { it.id }) { version ->
                                ListItem(
                                    headlineContent = { Text(version.id) },
                                    supportingContent = { Text(version.releaseTime.substringBefore('T')) },
                                    trailingContent = { if (selected?.id == version.id) Text("Выбрано", color = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { onSelected(version) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
