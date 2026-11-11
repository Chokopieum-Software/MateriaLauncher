package ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import funlauncher.Theme

@Composable
fun AnimatedAppTheme(
    theme: Theme,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (theme) {
        Theme.System -> rememberSystemDarkTheme()
        Theme.Light -> false
        Theme.Dark -> true
    }

    val colors = if (useDarkTheme) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    val animationSpec = tween<Color>(durationMillis = 500)

    val backgroundColor by animateColorAsState(
        targetValue = if (useDarkTheme) DarkBackground else LightBackground,
        animationSpec = animationSpec,
        label = "BackgroundColor"
    )

    MaterialTheme(
        colorScheme = colors
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(backgroundColor)
        ) {
            content()
        }
    }

}
