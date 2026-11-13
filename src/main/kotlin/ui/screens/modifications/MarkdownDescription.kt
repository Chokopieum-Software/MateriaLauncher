package ui.screens.modifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

@Composable
internal fun MarkdownDescription(markdown: String, base: String, modifier: Modifier = Modifier) {
    var state by remember(markdown, base) { mutableStateOf<State?>(null) }
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(markdown, base) {
        state = withContext(Dispatchers.Default) { parseMarkdown(DescriptionMarkdown.prepare(markdown, base)) }
    }
    val uriHandler = remember(base) { object : UriHandler {
        override fun openUri(uri: String) {
            DescriptionHtml.browserLink(uri, base)?.let { pendingLink = it }
        }
    } }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        val parsed = state
        if (parsed == null) Box(modifier) { CircularProgressIndicator() }
        else SelectionContainer(modifier) {
            Markdown(
                state = parsed,
                modifier = Modifier.fillMaxSize(),
                imageTransformer = Coil3ImageTransformerImpl,
                success = { content, components, layout ->
                    LazyColumn(layout) {
                        items(content.node.children) { node -> MarkdownElement(node, components, content.content) }
                    }
                },
                error = { Text("Не удалось отобразить описание", color = MaterialTheme.colorScheme.error) }
            )
        }
    }
    pendingLink?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingLink = null },
            title = { Text("Открыть ссылку в браузере?") },
            text = { SelectionContainer { Text(url) } },
            confirmButton = { TextButton(onClick = {
                pendingLink = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { Desktop.getDesktop().browse(URI(url)) }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = "Не удалось открыть браузер. Адрес: $url" }
                }
            }) { Text("Открыть") } },
            dismissButton = { TextButton(onClick = { pendingLink = null }) { Text("Отмена") } }
        )
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("Ошибка") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Закрыть") } })
    }
}
