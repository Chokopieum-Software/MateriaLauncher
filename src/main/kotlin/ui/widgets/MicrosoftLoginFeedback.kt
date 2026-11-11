package ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.chokopieum.software.ui.MateriaCircularProgressIndicator
import org.chokopieum.software.ui.ProgressState

@Composable
fun MicrosoftLoginFeedback(state: ProgressState, error: String? = null) {
    val message = when (state) {
        ProgressState.Loading -> "Ожидаем завершения входа Microsoft…"
        ProgressState.Success -> "Вы успешно вошли!"
        ProgressState.Error -> error ?: "Не удалось войти через Microsoft."
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        MateriaCircularProgressIndicator(
            modifier = Modifier.size(88.dp).semantics { contentDescription = message },
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 6.dp,
            state = state
        )
        Text(message, color = if (state == ProgressState.Error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface)
    }
}
