package ui.screens.modifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

@Composable
internal fun HtmlDescription(markdown: String, base: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    fun Color.css() = "#%02x%02x%02x".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
    val foreground = colors.onSurface.css()
    val background = colors.surface.css()
    val accent = colors.primary.css()
    var html by remember { mutableStateOf<String?>(null) }
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    val panelResult = remember(retry) { runCatching {
        WebView2Runtime.requireAvailable()
        DescriptionWebPanel(java.awt.Color.decode(background))
    } }
    val panel = panelResult.getOrNull()
    DisposableEffect(panel) { onDispose { panel?.dispose() } }
    LaunchedEffect(markdown, base, foreground, background, accent) {
        error = null
        html = withContext(Dispatchers.Default) {
            DescriptionHtml.render(markdown.ifBlank { "Нет описания." }, base, foreground, background, accent)
        }
    }
    Box(modifier) {
        val content = html
        if (panel == null) Column {
            Text(panelResult.exceptionOrNull()?.message ?: "Не удалось запустить WebView2", color = colors.error)
            TextButton(onClick = { pendingLink = WebView2Runtime.DOWNLOAD_URL }) { Text("Скачать WebView2 Runtime") }
            TextButton(onClick = { retry++ }) { Text("Проверить снова") }
        } else if (content == null) CircularProgressIndicator() else SwingPanel(
            factory = { panel.apply { setSurfaceColor(java.awt.Color.decode(background)) } },
            modifier = Modifier.fillMaxSize(),
            update = {
                it.setSurfaceColor(java.awt.Color.decode(background))
                it.showHtml(content, base, { link -> pendingLink = link }, { error = "Не удалось отобразить описание" })
            }
        )
        error?.let { Text(it, color = colors.error) }
    }
    pendingLink?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingLink = null },
            title = { Text("Открыть ссылку в браузере?") },
            text = {
                Column {
                    Text("Вы перейдёте из лаунчера на внешний сайт:")
                    SelectionContainer { Text(url) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingLink = null
                    try {
                        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                        Desktop.getDesktop().browse(URI(url))
                    } catch (e: Exception) {
                        error = "Не удалось открыть браузер. Адрес: $url"
                    }
                }) { Text("Открыть в браузере") }
            },
            dismissButton = { TextButton(onClick = { pendingLink = null }) { Text("Отмена") } }
        )
    }
}
