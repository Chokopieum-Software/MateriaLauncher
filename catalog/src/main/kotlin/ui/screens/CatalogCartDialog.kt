package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import org.chokopieum.software.ui.MateriaDetailsDialog

@Composable
internal fun CatalogCartDialog(
    cart: CatalogCart,
    build: MinecraftBuild?,
    onChooseBuild: () -> Unit,
    onInstall: (MinecraftBuild) -> Unit,
    onDismiss: () -> Unit
) {
    MateriaDetailsDialog("Корзина", "Закрыть корзину", onDismiss) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !cart.busy, onClick = onChooseBuild) {
                Text(build?.let { "Сборка: ${it.name}" } ?: "Выбрать сборку")
            }
            if (cart.items.isEmpty()) Text("Корзина пуста")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cart.items) { item ->
                    OutlinedCard {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text("${item.type.displayName} · ${item.version.versionNumber}")
                                if (build != null && !item.compatible(build)) {
                                    Text("Несовместимо со сборкой", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            TextButton(enabled = !cart.busy, onClick = { cart.remove(item) }) { Text("Убрать") }
                        }
                    }
                }
            }
            if (cart.status.isNotEmpty()) Text(cart.status)
            Button(enabled = !cart.busy && cart.items.isNotEmpty() &&
                (build == null || cart.items.all { it.compatible(build) }),
                onClick = { if (build == null) onChooseBuild() else onInstall(build) }) {
                Text(if (build == null) "Выбрать сборку для установки" else "Установить всё")
            }
        }
    }
}
