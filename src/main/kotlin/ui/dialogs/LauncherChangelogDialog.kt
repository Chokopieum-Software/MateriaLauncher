package ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.update.LauncherChangelog

@Composable
internal fun LauncherChangelogDialog(update: LauncherChangelog?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(update?.let { "Что нового в сборке №${it.current}" } ?: "Что нового") },
        text = {
            if (update == null) Text("Пока нет сохранённого списка изменений.") else
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Изменения после сборки №${update.previous}") }
                if (update.changes.isEmpty()) item { Text("Новых коммитов нет.") }
                items(update.changes) { Text("• $it") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
