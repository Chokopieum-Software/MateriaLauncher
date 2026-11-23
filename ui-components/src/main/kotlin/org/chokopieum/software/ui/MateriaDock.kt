package org.chokopieum.software.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MateriaDockItem private constructor(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean?,
    val onClick: () -> Unit
) {
    companion object {
        fun tab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) =
            MateriaDockItem(label, icon, selected, onClick)

        fun action(label: String, icon: ImageVector, onClick: () -> Unit) =
            MateriaDockItem(label, icon, null, onClick)
    }
}

@Composable
fun MateriaDock(items: List<MateriaDockItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val itemShape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface.copy(alpha = 0.76f),
        shadowElevation = 4.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val itemWidth = maxWidth / items.size
            val selectedIndex = items.indexOfFirst { it.selected == true }
            if (selectedIndex >= 0) {
                val offset by animateDpAsState(itemWidth * selectedIndex)
                Box(Modifier.offset(x = offset).width(itemWidth).fillMaxHeight()
                    .background(colors.secondaryContainer.copy(alpha = 0.82f), itemShape))
            }
            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEach { item ->
                    val interactions = remember { MutableInteractionSource() }
                    val hovered by interactions.collectIsHoveredAsState()
                    val input = if (item.selected == null) {
                        Modifier.clickable(interactions, indication = null, role = Role.Button, onClick = item.onClick)
                    } else {
                        Modifier.selectable(item.selected, interactions, indication = null, role = Role.Tab, onClick = item.onClick)
                    }
                    val tint = if (item.selected == true) colors.onSecondaryContainer else colors.onSurface
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(itemShape)
                            .then(if (hovered) Modifier.background(tint.copy(alpha = 0.08f)) else Modifier)
                            .then(input),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = tint)
                        Text(item.label, color = tint, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
