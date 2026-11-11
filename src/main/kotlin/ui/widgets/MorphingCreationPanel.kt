package ui.widgets

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun MorphingCreationPanel(
    visibleState: MutableTransitionState<Boolean>,
    sourceBounds: Rect?,
    content: @Composable () -> Unit
) {
    val transition = rememberTransition(visibleState, label = "Create instance morph")
    val progress by transition.animateFloat(
        transitionSpec = { tween(420, easing = FastOutSlowInEasing) }, label = "Expansion"
    ) { if (it) 1f else 0f }
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .focusRequester(focus).onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                    visibleState.targetState = false; true
                } else false
            }.focusable()
    ) {
        val targetWidth = minOf(640.dp, maxWidth - 32.dp)
        val targetHeight = minOf(660.dp, maxHeight - 32.dp)
        val startWidth = with(density) { sourceBounds?.width?.toDp() } ?: 126.dp
        val startHeight = with(density) { sourceBounds?.height?.toDp() } ?: 56.dp
        val startX = with(density) { sourceBounds?.let { (it.left - origin.x).toDp() } } ?: (maxWidth - startWidth - 16.dp)
        val startY = with(density) { sourceBounds?.let { (it.top - origin.y).toDp() } } ?: (maxHeight - startHeight - 16.dp)
        val x = startX + ((maxWidth - targetWidth) / 2 - startX) * progress
        val y = startY + ((maxHeight - targetHeight) / 2 - startY) * progress
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * progress))
            .clickable(remember { MutableInteractionSource() }, indication = null) { visibleState.targetState = false })
        Surface(
            modifier = Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                .size(startWidth + (targetWidth - startWidth) * progress, startHeight + (targetHeight - startHeight) * progress)
                .pointerInput(Unit) { detectTapGestures {} },
            shape = RoundedCornerShape(16.dp + 8.dp * progress),
            color = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface, progress),
            shadowElevation = 6.dp + 10.dp * progress
        ) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                if (progress < 0.5f) {
                    Row(Modifier.align(Alignment.Center).graphicsLayer { alpha = (1f - progress * 3).coerceIn(0f, 1f) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Add, null)
                        Text("Создать")
                    }
                }
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(targetWidth, targetHeight)
                    .graphicsLayer { alpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f) }) {
                    // Keep form dimensions stable while its surrounding surface expands.
                    if (progress > 0.45f) content()
                }
            }
        }
    }
}
