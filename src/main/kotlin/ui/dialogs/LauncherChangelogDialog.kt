package ui.dialogs

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.compose.MarkdownElement

@Composable
internal fun ChangelogContent(markdown: String) {
    Markdown(markdown, modifier = Modifier.heightIn(max = 360.dp), success = { content, components, layout ->
        LazyColumn(layout) {
            items(content.node.children) { MarkdownElement(it, components, content.content) }
        }
    })
}

@Composable
internal fun LauncherChangelogDialog(markdown: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что нового") },
        text = { ChangelogContent(markdown) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
