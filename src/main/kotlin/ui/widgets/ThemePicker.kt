package ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import funlauncher.Theme

@Composable
fun ThemePicker(current: Theme, enabled: Boolean, onSelect: (Theme) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val compact = maxWidth < 420.dp
        val choices = listOf(Theme.System to "Как в системе", Theme.Light to "Светлая", Theme.Dark to "Тёмная")
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.forEach { (theme, title) ->
                    ThemeOption(theme, title, current == theme, enabled, { onSelect(theme) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.forEach { (theme, title) ->
                    ThemeOption(theme, title, current == theme, enabled, { onSelect(theme) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(theme: Theme, title: String, selected: Boolean, enabled: Boolean,
                        onClick: () -> Unit, modifier: Modifier) {
    val outline by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = background,
        border = BorderStroke(if (selected) 2.dp else 1.dp, outline)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(8.dp))) {
                when (theme) {
                    Theme.Light -> windowPreview(false)
                    Theme.Dark -> windowPreview(true)
                    Theme.System -> {
                        clipRect(right = size.width / 2) { windowPreview(false) }
                        clipRect(left = size.width / 2) { windowPreview(true) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// A decorative miniature launcher window; dimensions are relative to its canvas.
private fun DrawScope.windowPreview(dark: Boolean) {
    val background = if (dark) Color(0xFF1B1D24) else Color(0xFFF3F4FA)
    val panel = if (dark) Color(0xFF292C36) else Color.White
    val muted = if (dark) Color(0xFF444855) else Color(0xFFDCE0EC)
    val accent = if (dark) Color(0xFFBCA7FF) else Color(0xFF7352C7)
    fun block(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 4f) {
        drawRoundRect(color, Offset(size.width * x, size.height * y),
            Size(size.width * w, size.height * h), CornerRadius(radius.dp.toPx()))
    }
    drawRect(background)
    drawRect(panel, size = Size(size.width, size.height * .17f))
    repeat(3) { index ->
        drawCircle(muted, 2.dp.toPx(), Offset(10.dp.toPx() + index * 7.dp.toPx(), size.height * .085f))
    }
    block(.05f, .25f, .22f, .66f, panel)
    block(.08f, .3f, .16f, .07f, accent)
    block(.08f, .46f, .12f, .04f, muted)
    block(.08f, .57f, .14f, .04f, muted)
    block(.33f, .27f, .38f, .06f, muted)
    block(.33f, .41f, .61f, .28f, panel)
    block(.37f, .47f, .19f, .05f, muted)
    block(.37f, .57f, .36f, .035f, muted)
    block(.65f, .77f, .29f, .14f, accent)
}
