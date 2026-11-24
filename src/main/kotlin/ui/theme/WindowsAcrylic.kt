package ui.theme

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Color
import java.awt.Container
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import funlauncher.Theme

internal val availableThemes = Theme.entries.filter { it != Theme.Acrylic || Platform.isWindows() }

internal fun themeLabel(theme: Theme) = when (theme) {
    Theme.System -> "Как в системе"
    Theme.Light -> "Светлая"
    Theme.Dark -> "Тёмная"
    Theme.Acrylic -> "Acrylic (эксп.)"
}

private fun Container.findRenderLayer(): SkiaLayer? =
    components.firstNotNullOfOrNull { child ->
        when (child) {
            is SkiaLayer -> child
            is Container -> child.findRenderLayer()
            else -> null
        }
    }

/** Only the rendering surface needs an alpha channel. The frame keeps its native decorations. */
internal fun enableWindowsAcrylic(window: ComposeWindow, dark: Boolean): (() -> Unit)? {
    if (!Platform.isWindows() || !window.isDisplayable || window.renderApi != GraphicsApi.OPENGL) return null
    val layer = window.contentPane.findRenderLayer() ?: return null
    val background = layer.background.takeIf { layer.isBackgroundSet }
    val transparent = layer.transparency
    val tint = if (dark) 0x80242120.toInt() else 0x80FAF6F5.toInt()
    if (!setAccent(window, tint)) return null
    layer.transparency = true
    layer.background = Color(0, 0, 0, 0)
    return {
        if (layer.isDisplayable) {
            layer.background = background
            layer.transparency = transparent
        }
        if (window.isDisplayable) setAccent(window, null)
    }
}

/** A null tint restores the default composition policy. */
private fun setAccent(window: ComposeWindow, tint: Int?): Boolean {
    return try {
        val setAttribute = NativeLibrary.getInstance("user32")
            .getFunction("SetWindowCompositionAttribute", Function.ALT_CONVENTION)
        Memory(16).use { accent ->
            accent.clear()
            if (tint != null) {
                accent.setInt(0, 4) // ACCENT_ENABLE_ACRYLICBLURBEHIND
                accent.setInt(8, tint) // ABGR
            }
            val word = Native.POINTER_SIZE.toLong()
            Memory(3 * word).use { data ->
                data.clear()
                data.setInt(0, 19) // WCA_ACCENT_POLICY
                data.setPointer(word, accent)
                data.setInt(2 * word, 16)
                setAttribute.invokeInt(arrayOf(Native.getWindowPointer(window), data)) != 0
            }
        }
    } catch (_: LinkageError) {
        false
    } catch (_: RuntimeException) {
        false
    }
}
