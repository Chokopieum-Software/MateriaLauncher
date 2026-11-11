package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive

/** A single background reader shared by the window decorations and Compose content. */
private object SystemThemeMonitor {
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val changes = pollSystemTheme {
        try {
            val value = Advapi32Util.registryGetIntValue(HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme")
            when (value) {
                0 -> true
                1 -> false
                else -> null
            }
        } catch (_: RuntimeException) {
            // Keep the last known theme when the registry is temporarily inaccessible.
            null
        } catch (_: LinkageError) {
            null
        }
    }.shareIn(CoroutineScope(SupervisorJob() + Dispatchers.IO),
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), replay = 1)
}

internal fun pollSystemTheme(intervalMillis: Long = 500, read: () -> Boolean?) = flow {
    while (currentCoroutineContext().isActive) {
        read()?.let { emit(it) }
        delay(intervalMillis)
    }
}.distinctUntilChanged()

@Composable
fun rememberSystemDarkTheme(): Boolean {
    val fallback = isSystemInDarkTheme()
    if (!SystemThemeMonitor.windows) return fallback
    val dark by SystemThemeMonitor.changes.collectAsState(initial = null)
    return dark ?: fallback
}
