package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import funlauncher.managers.CacheManager
import funlauncher.managers.PathManager
import kotlinx.coroutines.delay
import org.chokopieum.software.materia_launcher.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import state.AppState
import ui.screens.*
import ui.theme.AnimatedAppTheme
import ui.viewmodel.AppViewModel
import ui.viewmodel.HomeViewModel

// Перечисление для вкладок навигации в приложении.
enum class AppTab { Home, Modifications, Settings }

/**
 * Главный компонент приложения, который управляет состоянием и отображением основного интерфейса.
 * @param viewModel ViewModel, управляющая состоянием и логикой UI.
 * @param appState Текущее состояние приложения (необходимое для некоторых дочерних компонентов).
 * @param pathManager Менеджер путей к файлам приложения.
 * @param cacheManager Менеджер кэша.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App(
    viewModel: AppViewModel,
    appState: AppState, // appState is still needed for theme and some screen-specific logic
    pathManager: PathManager,
    cacheManager: CacheManager,
    homeViewModel: HomeViewModel,
    onExitForUpdate: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var createButtonBounds by remember { mutableStateOf<Rect?>(null) }


    // Основная тема и разметка приложения.
    AnimatedAppTheme(appState.settings.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent // Делаем фон Scaffold прозрачным
        ) {
            // Анимированные отступы для контента в зависимости от положения навигационной панели.
            val contentPaddingBottom by animateDpAsState(if (viewModel.currentTab != AppTab.Modifications) 80.dp else 0.dp)

            Box(modifier = Modifier.fillMaxSize()) {
                // Плавный переход между экранами (вкладками).
                Box(modifier = Modifier.fillMaxSize().padding(bottom = contentPaddingBottom)) {
                    Crossfade(targetState = viewModel.currentTab, animationSpec = tween(300)) { tab ->
                        when (tab) {
                            AppTab.Home -> HomeScreen(homeViewModel)
                            AppTab.Modifications -> ModificationsScreen(
                                onBack = { viewModel.currentTab = AppTab.Home },
                                buildManager = viewModel.buildManager,
                                onModpackInstalled = {
                                    viewModel.refreshBuilds()
                                    if (viewModel.currentTab == AppTab.Modifications) viewModel.currentTab = AppTab.Home
                                },
                                pathManager = pathManager,
                                snackbarHostState = snackbarHostState,
                                installationScope = viewModel.viewModelScope,
                                cacheManager = cacheManager
                            )
                            AppTab.Settings -> SettingsTab(
                                currentSettings = appState.settings,
                                onSave = viewModel::onSettingsChanged,
                                onOpenJavaManager = { viewModel.showJavaManagerWindow = true },
                                accountManager = viewModel.accountManager,
                                coroutineScope = scope,
                                cacheManager = cacheManager,
                                startupSettings = viewModel.startupSettings,
                                saving = viewModel.settingsSaving,
                                saveError = viewModel.settingsError,
                                saved = viewModel.settingsSaved,
                                onRetrySave = viewModel::retrySettingsSave,
                                onExitForUpdate = onExitForUpdate
                            )
                        }
                    }
                }

                // Нижняя панель
                BottomBar(
                    viewModel = viewModel,
                    homeViewModel = homeViewModel,
                    onCreateButtonBounds = { createButtonBounds = it }
                )
            }
        }

        // Компонент для отображения всех оверлеев (диалоги, всплывающие окна).
        AppOverlays(
            createButtonBounds = createButtonBounds,
            viewModel = viewModel,
            appState = appState,
            pathManager = pathManager
        )
    }
}

@Composable
private fun BoxScope.BottomBar(
    viewModel: AppViewModel,
    homeViewModel: HomeViewModel,
    onCreateButtonBounds: (Rect) -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Поиск
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            AnimatedVisibility(
                visible = viewModel.currentTab == AppTab.Home,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ExpandableSearchBar(
                    searchQuery = homeViewModel.searchQuery,
                    onSearchQueryChange = homeViewModel::onSearchQueryChanged
                )
            }
        }

        // Навигационная панель
        Box(modifier = Modifier.align(Alignment.Center)) {
            AppNavigation(
                currentTab = viewModel.currentTab,
                onTabSelected = { viewModel.currentTab = it }
            )
        }

        // Правые нижние кнопки
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Кнопка "Создать"
            AnimatedVisibility(
                visible = viewModel.currentTab == AppTab.Home,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.onGloballyPositioned { onCreateButtonBounds(it.boundsInWindow()) }
                        .graphicsLayer { alpha = if (viewModel.showAddBuildDialog) 0f else 1f },
                    onClick = { homeViewModel.onAddBuildClick() },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Добавить сборку") },
                    text = { Text("Создать") }
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    // Нижняя навигационная панель.
    AnimatedVisibility(
        visible = currentTab != AppTab.Modifications,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        NavigationBar(
            modifier = Modifier
                .width(300.dp)
                .height(64.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
        ) {
            NavigationBarItem(
                selected = currentTab == AppTab.Home,
                onClick = { onTabSelected(AppTab.Home) },
                icon = { Icon(Icons.Default.Home, contentDescription = stringResource(Res.string.tab_home)) },
                label = { Text(stringResource(Res.string.tab_home)) }
            )
            NavigationBarItem(
                selected = currentTab == AppTab.Modifications,
                onClick = { onTabSelected(AppTab.Modifications) },
                icon = { Icon(Icons.Default.Build, contentDescription = stringResource(Res.string.tab_modifications)) },
                label = { Text(stringResource(Res.string.tab_modifications)) }
            )
            NavigationBarItem(
                selected = currentTab == AppTab.Settings,
                onClick = { onTabSelected(AppTab.Settings) },
                icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.tab_settings)) },
                label = { Text(stringResource(Res.string.tab_settings)) }
            )
        }
    }
}

@Composable
private fun ExpandableSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    // Флаг, указывающий, что поле уже получало фокус.
    // Нужен, чтобы onFocusChanged не закрывал поле сразу при создании, когда фокус еще не пришел.
    var wasFocused by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val width by animateDpAsState(
        targetValue = if (isExpanded) 250.dp else 48.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = Modifier
            .height(48.dp)
            .width(width)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isExpanded) {
                    isExpanded = true
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка поиска
            IconButton(
                onClick = {
                    if (!isExpanded) {
                        isExpanded = true
                    } else {
                        focusRequester.requestFocus()
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Поиск", modifier = Modifier.size(24.dp))
            }

            // Поле ввода и кнопка закрытия
            if (isExpanded) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                wasFocused = true
                            }
                            // Закрываем только если фокус был потерян ПОСЛЕ того, как он был получен,
                            // и поле пустое.
                            if (!focusState.isFocused && wasFocused && searchQuery.isEmpty()) {
                                isExpanded = false
                                wasFocused = false // Сбрасываем флаг
                            }
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Поиск...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = {
                        onSearchQueryChange("")
                        isExpanded = false
                        wasFocused = false
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            // Небольшая задержка, чтобы UI успел перестроиться
            delay(50)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }
}
