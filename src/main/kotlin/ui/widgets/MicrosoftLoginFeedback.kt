package ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import org.chokopieum.software.ui.MateriaCircularProgressIndicator
import org.chokopieum.software.ui.ProgressState

@Composable
fun MicrosoftLoginFeedback(state: ProgressState, error: String? = null, colors: ColorScheme = MaterialTheme.colorScheme) {
    val message = when (state) {
        ProgressState.Loading -> "Ожидаем завершения входа Microsoft…"
        ProgressState.Success -> "Вы успешно вошли!"
        ProgressState.Error -> error ?: "Не удалось войти через Microsoft."
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        MateriaCircularProgressIndicator(
            modifier = Modifier.size(88.dp).semantics { contentDescription = message },
            color = colors.primary,
            strokeWidth = 6.dp,
            state = state
        )
        Text(message, color = if (state == ProgressState.Error) colors.error else colors.onSurface,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}
