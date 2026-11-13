package ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.PathManager
import funlauncher.net.Version
import org.jetbrains.compose.resources.stringResource
import org.chokopieum.software.materia_launcher.generated.resources.*
import ui.screens.modifications.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import funlauncher.net.DownloadManager
import funlauncher.net.ModificationDownloader
import funlauncher.net.ModificationInstaller
import funlauncher.modpack.ModpackInstaller
import funlauncher.net.ModrinthApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext

enum class FilterState {
    INCLUDED, EXCLUDED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ModificationsScreen(
    onBack: () -> Unit,
    buildManager: BuildManager,
    onModpackInstalled: () -> Unit,
    pathManager: PathManager,
    snackbarHostState: SnackbarHostState,
    cacheManager: CacheManager,
    installationScope: CoroutineScope
) {
    val scope = rememberCoroutineScope()
    
    // Инициализация ViewModel
    val viewModel = remember {
        ModificationsViewModel(buildManager, pathManager, cacheManager, scope).apply {
            init()
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.onDispose()
        }
    }
    
    val modificationDownloader = remember { ModificationDownloader() }
    val modpackInstaller = remember(buildManager, installationScope) { ModpackInstaller(buildManager, installationScope) }

    var pendingVersion by remember { mutableStateOf<Version?>(null) }
    var pendingType by remember { mutableStateOf(ModificationType.MODS) }
    var worldBuild by remember { mutableStateOf<funlauncher.MinecraftBuild?>(null) }
    var installing by remember { mutableStateOf(false) }

    val contentPaddingBottom by animateDpAsState(80.dp)

    fun installVersion(version: Version, build: funlauncher.MinecraftBuild, type: ModificationType, world: String? = null) {
        installationScope.launch {
            // Installation owns its API client; leaving the catalog closes only the catalog client.
            val installationApi = ModrinthApi(cacheManager)
            var taskId: String? = null
            try {
                val task = DownloadManager.startTask(version.name, coroutineContext.job)
                taskId = task.id
                ModificationInstaller(installationApi, modificationDownloader).install(version, build, type.installDir, world) { progress, status ->
                    DownloadManager.updateTask(task.id, progress, status)
                }
                DownloadManager.updateTask(task.id, 1f, "Завершено")
                snackbarHostState.showSnackbar("Установлено в '${build.name}'")
                delay(3000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Ошибка установки: ${e.message}")
                taskId?.let { DownloadManager.updateTask(it, 0f, "Ошибка: ${e.message}") }
                delay(5000)
            } finally {
                installationApi.close()
                installing = false
                taskId?.let { DownloadManager.removeTask(it) }
            }
        }
    }

    // Эффекты для обновления фильтров и поиска
    LaunchedEffect(viewModel.selectedType, viewModel.allCategories.size, viewModel.allLoaders.size) {
        viewModel.filterCategoriesAndLoaders()
    }

    LaunchedEffect(
        viewModel.searchQuery,
        viewModel.selectedType,
        viewModel.selectedVersions.toList(),
        viewModel.selectedLoaders.toMap(),
        viewModel.selectedCategories.toMap()
    ) {
        viewModel.search()
    }

    if (viewModel.showVersionSelectionDialog) {
        VersionSelectionDialog(
            versions = viewModel.allVanillaVersions,
            selectedVersions = viewModel.selectedVersions,
            onDismissRequest = { viewModel.showVersionSelectionDialog = false }
        )
    }

    if (viewModel.showInstallDialog) {
        VersionInstallDialog(
            versions = viewModel.projectVersions,
            onDismissRequest = { viewModel.showInstallDialog = false },
            onInstallClick = { version ->
                viewModel.showInstallDialog = false
                if (viewModel.selectedType == ModificationType.MODPACKS) {
                    installing = true
                    modpackInstaller.install(version, onComplete = {
                        installing = false
                        onModpackInstalled()
                    }, onError = { message ->
                        installing = false
                        installationScope.launch { snackbarHostState.showSnackbar(message) }
                    })
                } else {
                    pendingType = viewModel.selectedType
                    pendingVersion = version
                }
            }
        )
    }
    pendingVersion?.takeIf { worldBuild == null }?.let { version ->
        InstallModificationDialog(
            builds = viewModel.allBuilds,
            version = version,
            directory = pendingType.installDir,
            onDismiss = { pendingVersion = null },
            onInstall = { build ->
                if (pendingType == ModificationType.DATAPACKS) {
                    worldBuild = build
                } else {
                    pendingVersion = null
                    installing = true
                    installVersion(version, build, pendingType)
                }
            }
        )
    }

    worldBuild?.let { build ->
        val worlds = remember(build) { java.io.File(build.installPath, "saves").listFiles()?.filter { it.isDirectory && java.io.File(it, "level.dat").isFile } ?: emptyList() }
        AlertDialog(
            onDismissRequest = { worldBuild = null; pendingVersion = null },
            title = { Text("Выберите мир") },
            text = {
                Column {
                    if (worlds.isEmpty()) Text("В этой сборке нет сохранённых миров")
                    worlds.forEach { world ->
                        TextButton(onClick = {
                            val version = pendingVersion
                            pendingVersion = null
                            worldBuild = null
                            if (version != null) {
                                installing = true
                                installVersion(version, build, pendingType, world.name)
                            }
                        }) { Text(world.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { worldBuild = null; pendingVersion = null }) { Text("Отмена") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(targetState = viewModel.selectedProject) { project ->
                        if (project == null) {
                            OutlinedTextField(
                                value = viewModel.searchQuery,
                                onValueChange = { viewModel.searchQuery = it },
                                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                                modifier = Modifier.fillMaxWidth(0.6f).padding(end = 16.dp),
                                singleLine = true,
                                leadingIcon = {
                                    IconButton(onClick = { viewModel.isFilterPanelVisible = !viewModel.isFilterPanelVisible }) {
                                        Icon(Icons.Default.FilterList, contentDescription = "Фильтры")
                                    }
                                },
                                trailingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                                }
                            )
                        } else {
                            Text(project.title)
                        }
                    }
                },
                navigationIcon = {
                    AnimatedVisibility(visible = viewModel.selectedProject != null) {
                        IconButton(onClick = { viewModel.selectedProject = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                modifier = Modifier.shadow(4.dp)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(modifier = Modifier.fillMaxSize().padding(bottom = contentPaddingBottom)) {
                
                // Левое меню с фильтрами
                AnimatedVisibility(visible = viewModel.isFilterPanelVisible && viewModel.selectedProject == null) {
                    FilterPanel(viewModel = viewModel)
                }

                // Основной контент
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // A heavyweight WebView cannot follow Compose alpha transitions.
                    // Keep one details instance when the project metadata is refreshed.
                    val project = viewModel.selectedProject
                    key(project?.id) {
                        if (project != null) {
                            ModificationDetails(
                                project = project,
                                isLoadingDetails = viewModel.isLoadingProjectDetails || installing,
                                detailsError = viewModel.projectDetailsError,
                                onInstallClick = {
                                    viewModel.showInstallDialog = true
                                }
                            )
                        } else {
                            ModificationList(viewModel = viewModel)
                        }
                    }
                }
            }

            // NavigationBar (нижняя панель)
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { offset -> offset }),
                exit = slideOutVertically(targetOffsetY = { offset -> offset }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NavigationBar(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .width(500.dp)
                        .height(64.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = false,
                        onClick = onBack,
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) },
                        label = { Text(stringResource(Res.string.back)) }
                    )
                    val modificationTypes = ModificationType.entries
                    modificationTypes.forEach { type ->
                        NavigationBarItem(
                            selected = viewModel.selectedType == type,
                            onClick = { viewModel.selectedType = type },
                            icon = { Icon(type.icon, contentDescription = type.displayName) },
                            label = { Text(type.displayName) }
                        )
                    }
                }
            }
        }
    }
}

enum class ModificationType(
    val displayName: String,
    val icon: ImageVector,
    val projectType: String,
    val installDir: String
) {
    MODPACKS("Модпаки", Icons.Default.Home, "modpack", ""),
    MODS("Моды", Icons.Default.Star, "mod", "mods"),
    RESOURCE_PACKS("Ресурс-паки", Icons.Default.Settings, "resourcepack", "resourcepacks"),
    DATAPACKS("Датапаки", Icons.Default.DateRange, "datapack", "datapacks"),
    SHADERS("Шейдеры", Icons.Default.Build, "shader", "shaderpacks")
}
