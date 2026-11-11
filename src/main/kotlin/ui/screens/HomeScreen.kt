/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.chokopieum.software.materia_launcher.generated.resources.Res
import org.chokopieum.software.materia_launcher.generated.resources.monocraft
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.window.TitleBarScope
import org.jetbrains.jewel.window.utils.clientRegion
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import ui.viewmodel.HomeViewModel
import ui.widgets.AvatarImage
import ui.widgets.ImageLoader
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import ui.widgets.SquircleShape
import funlauncher.Theme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel
) {
    val filteredBuilds by rememberUpdatedState(viewModel.filteredBuilds)
    var expandedBuild by remember { mutableStateOf<MinecraftBuild?>(null) }
    
    val lazyGridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        if (viewModel.searchQuery.isBlank()) {
            viewModel.onBuildsReordered(from.index, to.index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.padding(paddingValues),
                exit = fadeOut(animationSpec = tween(durationMillis = 200))
            ) {
                if (viewModel.builds.isEmpty()) {
                    EmptyState(Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Adaptive(minSize = 220.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredBuilds, key = { it.name }) { build ->
                                ReorderableItem(reorderableState, key = build.name) { isDragging ->
                                    AnimatedVisibility(
                                        visible = build.name !in viewModel.buildsPendingDeletion,
                                        exit = shrinkVertically(animationSpec = tween(durationMillis = 300)) + fadeOut(
                                            animationSpec = tween(durationMillis = 250)
                                        )
                                    ) {
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val dragModifier = if (viewModel.searchQuery.isBlank()) {
                                            Modifier.draggableHandle(interactionSource = interactionSource)
                                        } else {
                                            Modifier
                                        }
                                        
                                        AnimatedBuildCard(
                                            build = build,
                                            isRunning = build == viewModel.runningBuild,
                                            isPreparing = build.name == viewModel.isLaunchingBuildId,
                                            isDragging = isDragging,
                                            onLaunchClick = { viewModel.onLaunchClick(build) },
                                            onOpenFolderClick = { viewModel.onOpenFolderClick(build) },
                                            onSettingsClick = { expandedBuild = build },
                                            onCardClick = { expandedBuild = build },
                                            index = filteredBuilds.indexOf(build),
                                            modifier = Modifier.animateItem(
                                                placementSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            ).then(dragModifier)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        expandedBuild?.let { build ->
            Dialog(
                onDismissRequest = { expandedBuild = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.padding(24.dp).widthIn(max = 1000.dp)
                        .fillMaxWidth().heightIn(max = 760.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 16.dp
                ) {
                    ExpandedBuildScreenWrapper(
                        build = build,
                        viewModel = viewModel,
                        onDismiss = { expandedBuild = null }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandedBuildScreenWrapper(
    build: MinecraftBuild,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Заголовок и действия окна настроек.
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть настройки сборки")
                }
                Spacer(Modifier.width(16.dp))
                Text(build.name, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                
                // Кнопка удаления сборки (перенесена из карточки)
                var showDeleteConfirm by remember { mutableStateOf(false) }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить сборку", tint = MaterialTheme.colorScheme.error)
                }
                
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Удалить сборку?") },
                        text = { Text("Вы уверены, что хотите удалить сборку '${build.name}'? Это действие нельзя отменить.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.onDeleteBuildClick(build)
                                    showDeleteConfirm = false
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Удалить")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
            }

            // Встраиваем экран настроек (без его собственного Scaffold/TopBar, только контент)
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                BuildSettingsScreenWithoutDialog(
                    build = build,
                    globalSettings = viewModel.globalSettings,
                    pathManager = viewModel.pathManager,
                    onDismiss = onDismiss,
                    onSave = { newName, newVersion, newType, newImagePath, javaPath, maxRam, javaArgs, envVars ->
                        viewModel.onSaveBuildSettings(
                            oldBuildName = build.name,
                            newName = newName,
                            newVersion = newVersion,
                            newType = newType,
                            newImagePath = newImagePath,
                            javaPath = javaPath,
                            maxRam = maxRam,
                            javaArgs = javaArgs,
                            envVars = envVars
                        )
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun TitleBarScope.TitleBarActions(
    viewModel: HomeViewModel,
    theme: Theme = viewModel.globalSettings.theme
) {
    val isDark = when(theme) {
        Theme.System -> ui.theme.rememberSystemDarkTheme()
        Theme.Light -> false
        Theme.Dark -> true
    }
    val textColor = if (isDark) Color.White else LocalContentColor.current
    val backgroundColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    var showAccountPopup by remember { mutableStateOf(false) }
    
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

    Box(modifier = Modifier.align(Alignment.Start)) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clientRegion("account-selector")
                .clickable { showAccountPopup = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AvatarImage(
                account = viewModel.currentAccount,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
            )
            Text(
                text = viewModel.currentAccount?.username ?: "Offline",
                style = TextStyle(
                    fontFamily = FontFamily(Font(Res.font.monocraft)),
                    color = textColor
                )
            )
        }

        if (showAccountPopup) {
            AccountScreen(
                accountManager = viewModel.accountManager,
                onDismiss = { showAccountPopup = false },
                onAccountSelected = { account ->
                    viewModel.onAccountSelected(account)
                    showAccountPopup = false
                },
                colorScheme = colorScheme
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Сборки не найдены.\nНажмите \"Добавить\", чтобы добавить новую.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}


private fun formatBuildVersion(build: MinecraftBuild): String {
    return when (build.type) {
        BuildType.VANILLA -> build.version
        BuildType.FABRIC -> {
            val parts = build.version.split("-fabric-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.FORGE -> {
            val parts = build.version.split("-forge-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.QUILT -> {
            val parts = build.version.split("-quilt-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.NEOFORGE -> {
            val parts = build.version.split("-neoforge-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimatedBuildCard(
    build: MinecraftBuild,
    isRunning: Boolean,
    isPreparing: Boolean,
    isDragging: Boolean,
    onLaunchClick: () -> Unit,
    onOpenFolderClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCardClick: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier
) {
    val animatedScale = remember { Animatable(0.8f) }
    val animatedAlpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = build.name) {
        val animationDelay = (index * 75L).coerceAtMost(375L)

        launch {
            delay(animationDelay)
            animatedScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        launch {
            delay(animationDelay)
            animatedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250)
            )
        }
    }

    BuildCard(
        build = build,
        isRunning = isRunning,
        isPreparing = isPreparing,
        isDragging = isDragging,
        onLaunchClick = onLaunchClick,
        onOpenFolderClick = onOpenFolderClick,
        onSettingsClick = onSettingsClick,
        onCardClick = onCardClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                alpha = animatedAlpha.value
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BuildCard(
    build: MinecraftBuild,
    isRunning: Boolean,
    isPreparing: Boolean,
    isDragging: Boolean,
    onLaunchClick: () -> Unit,
    onOpenFolderClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    val painter = ImageLoader.rememberImagePainter(build.imagePath, coverSize)

    val borderColor by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 200)
    )
    
    val elevation by animateDpAsState(if (isDragging) 16.dp else if (isHovered) 12.dp else 4.dp)

    Card(
        modifier = modifier
            .hoverable(interactionSource)
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Image section
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).onSizeChanged { coverSize = it }) {
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF606060), Color(0xFF303030)))
                    ))
                }
            }

            // Info and actions section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val marqueeModifier = if (isHovered) {
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                        initialDelayMillis = 500,
                        velocity = 30.dp
                    )
                } else {
                    Modifier
                }

                Text(
                    text = build.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = marqueeModifier
                )

                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = formatBuildVersion(build),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LaunchButton(
                        onClick = onLaunchClick,
                        isPreparing = isPreparing,
                        isRunning = isRunning,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings, 
                            contentDescription = "Настройки"
                        )
                    }
                    Button(
                        onClick = onOpenFolderClick,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder, 
                            contentDescription = "Открыть папку"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LaunchButton(
    onClick: () -> Unit,
    isPreparing: Boolean,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isPreparing -> MaterialTheme.colorScheme.surfaceVariant
                isRunning -> MaterialTheme.colorScheme.error
                else -> Color(0xFF4CAF50) // Green color for "Play"
            },
            contentColor = when {
                isPreparing -> MaterialTheme.colorScheme.onSurfaceVariant
                isRunning -> MaterialTheme.colorScheme.onError
                else -> Color.White
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .height(40.dp),
        enabled = !isPreparing,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text("ЗАПУСК...", style = MaterialTheme.typography.labelMedium)
            } else {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRunning) "ОСТАНОВИТЬ" else "ИГРАТЬ",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
