package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.update.LauncherUpdate
import funlauncher.update.LauncherUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LauncherUpdateCard(onExitForUpdate: () -> Unit) {
    val scope = rememberCoroutineScope()
    val installation = remember { LauncherUpdates.installation() }
    var busy by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<LauncherUpdate?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Обновление лаунчера", style = MaterialTheme.typography.titleMedium)
            Text("Последняя успешная сборка sunrise-dev")
            if (installation == null) Text("Автообновление доступно в установленной Windows-версии лаунчера.")
            message?.let { Text(it) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy && installation != null, onClick = {
                    busy = true
                    latest = null
                    message = "Проверка обновлений…"
                    scope.launch {
                        try {
                            latest = withContext(Dispatchers.IO) { LauncherUpdates.check(installation!!) }
                            message = latest?.let { "Доступна сборка ${it.buildNumber} (${it.commit.take(7)})" }
                                ?: "Установлена последняя сборка."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { message = error.message ?: "Ошибка проверки обновлений" }
                        finally { busy = false }
                    }
                }) { Text("Проверить обновления") }
                if (latest != null) Button(enabled = !busy, onClick = { confirm = true }) { Text("Обновить") }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Обновить Materia Launcher?") },
        text = { Text("Лаунчер закроется. Отдельный обновлятор скачает и установит сборку ${latest?.buildNumber}, затем снова запустит лаунчер. Закройте другие экземпляры лаунчера перед продолжением.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            busy = true
            message = "Запуск обновлятора…"
            val selected = latest!!
            scope.launch {
                try {
                    withContext(NonCancellable) {
                        withContext(Dispatchers.IO) { LauncherUpdates.start(installation!!, selected) }
                        onExitForUpdate()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { message = error.message ?: "Не удалось запустить обновлятор" }
                finally { busy = false }
            }
        }) { Text("Закрыть и обновить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } }
    )
}
