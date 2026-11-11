package ui.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ui.theme.rememberSystemDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.net.DownloadManager
import funlauncher.Theme
import kotlinx.coroutines.delay
import org.jetbrains.jewel.window.TitleBarScope
import org.jetbrains.jewel.window.utils.clientRegion
import ui.viewmodel.AppViewModel

@Composable
fun TitleBarScope.TitleBarDownloads(viewModel: AppViewModel, theme: Theme) {
    val isDark = when (theme) {
        Theme.System -> rememberSystemDarkTheme()
        Theme.Light -> false
        Theme.Dark -> true
    }
    val tasks = DownloadManager.tasks.toList()
    val isDownloading = tasks.isNotEmpty()
    val progress = if (isDownloading) {
        tasks.map { it.progress.value.coerceIn(0f, 1f) }.average().toFloat()
    } else 0f
    var hadDownloads by remember { mutableStateOf(false) }
    LaunchedEffect(isDownloading) {
        if (isDownloading) {
            hadDownloads = true
            viewModel.showCheckmark = false
        } else if (hadDownloads) {
            viewModel.showCheckmark = true
            delay(2000)
            viewModel.showCheckmark = false
            hadDownloads = false
        }
    }
    // End alignment respects the space reserved by Jewel for window controls.
    Box(Modifier.align(Alignment.End).padding(horizontal = 8.dp)) {
        MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
            AnimatedContent(
                targetState = isDownloading,
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = {
                    (fadeIn(tween(180, delayMillis = 100)) togetherWith
                        fadeOut(tween(100))).using(
                        SizeTransform(clip = true) { _, _ ->
                            tween(300, easing = FastOutSlowInEasing)
                        }
                    )
                },
                label = "Title bar downloads"
            ) { downloading ->
                if (downloading) {
                    Row(
                        modifier = Modifier
                            .size(width = 168.dp, height = 32.dp)
                            .clientRegion("downloads-progress")
                            .clickable { viewModel.showDownloadsPopup = !viewModel.showDownloadsPopup }
                            .semantics { contentDescription = "Показать загрузки" }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(6.dp)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.showDownloadsPopup = !viewModel.showDownloadsPopup },
                        modifier = Modifier.size(32.dp).clientRegion("downloads-button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        if (viewModel.showCheckmark) {
                            Icon(Icons.Default.Check, "Загрузки завершены")
                        } else {
                            Icon(Icons.Default.Download, "Загрузки")
                        }
                    }
                }
            }
            if (viewModel.showDownloadsPopup) {
                DownloadsPopup(onDismissRequest = { viewModel.showDownloadsPopup = false })
            }
        }
    }
}
