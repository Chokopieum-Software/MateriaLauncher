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
import funlauncher.modpack.CatalogBuilds
import funlauncher.managers.CacheManager
import funlauncher.net.Version
import org.jetbrains.compose.resources.stringResource
import org.chokopieum.software.catalog.resources.*
import ui.screens.modifications.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import funlauncher.net.DownloadManager
import funlauncher.net.ModificationDownloader
import funlauncher.net.ModificationInstaller
import funlauncher.net.CatalogProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext
import org.chokopieum.software.ui.MateriaDock
import org.chokopieum.software.ui.MateriaDockItem

enum class FilterState {
    INCLUDED, EXCLUDED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CatalogScreen(
    cart: CatalogCart,
    providers: List<CatalogProvider>,
    consentStore: ProviderConsentStore,
    onBack: () -> Unit,
    buildManager: CatalogBuilds,
    onModpackInstalled: () -> Unit,
    loadVanillaVersions: suspend () -> List<String>,
    snackbarHostState: SnackbarHostState,
    cacheManager: CacheManager,
    installationScope: CoroutineScope
) {
    if (providers.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Нет подключённых провайдеров")
            TextButton(onClick = onBack) { Text(stringResource(Res.string.back)) }
        }
        return
    }
    require(providers.distinctBy { it.id }.size == providers.size) { "Provider IDs must be unique" }
    var selectedId by remember { mutableStateOf(providers.first().id) }
    val provider = providers.find { it.id == selectedId } ?: providers.first()
    key(provider) {
        WithProviderConsent(provider, consentStore, onBack) {
            ProviderCatalog(cart, provider, providers, { selectedId = it.id }, onBack, buildManager,
                onModpackInstalled, loadVanillaVersions, snackbarHostState, cacheManager, installationScope)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
private fun ProviderCatalog(
    cart: CatalogCart,
    provider: CatalogProvider,
    providers: List<CatalogProvider>,
    onSelectProvider: (CatalogProvider) -> Unit,
    onBack: () -> Unit,
    buildManager: CatalogBuilds,
    onModpackInstalled: () -> Unit,
    loadVanillaVersions: suspend () -> List<String>,
    snackbarHostState: SnackbarHostState,
    cacheManager: CacheManager,
    installationScope: CoroutineScope
) {
    val scope = rememberCoroutineScope()
    
    // Инициализация ViewModel
    val viewModel = remember {
        ModificationsViewModel(buildManager, loadVanillaVersions, cacheManager, provider, scope).apply {
            init()
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.onDispose()
        }
    }
    
    val modificationDownloader = remember { ModificationDownloader() }

    var pendingVersion by remember { mutableStateOf<Version?>(null) }
    var worldBuild by remember { mutableStateOf<funlauncher.MinecraftBuild?>(null) }
    var installing by remember { mutableStateOf(false) }

    val contentPaddingBottom by animateDpAsState(80.dp)

    fun installVersion(version: Version, build: funlauncher.MinecraftBuild, type: ModificationType, world: String? = null) {
        installationScope.launch {
            // Installation owns its API client; leaving the catalog closes only the catalog client.
            val installationApi = provider.open()
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

    var searchText by remember(viewModel) { mutableStateOf(viewModel.searchQuery) }
    LaunchedEffect(viewModel, searchText) {
        if (searchText != viewModel.searchQuery) {
            delay(1_500)
            viewModel.searchQuery = searchText
        }
    }

    LaunchedEffect(
        viewModel.searchQuery,
        viewModel.selectedBuild,
        viewModel.selectedType,
        viewModel.selectedVersions.toList(),
        viewModel.selectedLoaders.toMap(),
        viewModel.selectedCategories.toMap()
    ) {
        viewModel.search()
    }

    var pickingBuild by remember { mutableStateOf(false) }
    var showingCart by remember { mutableStateOf(false) }
    var fromList by remember { mutableStateOf(false) }
    fun closeVersions() {
        viewModel.showInstallDialog = false
        if (fromList) viewModel.closeProject()
        fromList = false
    }
    if (showingCart) {
        CatalogCartDialog(cart, viewModel.selectedBuild, onChooseBuild = { pickingBuild = true },
            onInstall = { cart.install(it, installationScope) }, onDismiss = { showingCart = false })
    }
    if (pickingBuild) {
        BuildSelectionDialog(viewModel.allBuilds, onDismissRequest = { pickingBuild = false },
            selectedBuild = viewModel.selectedBuild, onBuildSelected = {
                viewModel.selectBuild(it)
                pickingBuild = false
            })
    }
    viewModel.selectedProject?.takeUnless { fromList }?.let { project ->
        key(project.id) {
            ModificationDetails(
                project = project,
                projectUrl = viewModel.api.projectUrl(project),
                isLoadingDetails = viewModel.isLoadingProjectDetails || installing || (cart.busy && viewModel.selectedType.usesCart),
                detailsError = viewModel.projectDetailsError,
                onDismiss = viewModel::closeProject,
                onInstallClick = { viewModel.showInstallDialog = true }
            )
        }
    }
    if (viewModel.showVersionSelectionDialog) {
        VersionSelectionDialog(
            versions = viewModel.allVanillaVersions,
            selectedVersions = viewModel.selectedVersions,
            onDismissRequest = { viewModel.showVersionSelectionDialog = false }
        )
    }

    if (viewModel.showInstallDialog && viewModel.selectedType.usesCart && viewModel.selectedBuild == null) {
        BuildSelectionDialog(viewModel.allBuilds, onDismissRequest = ::closeVersions, onBuildSelected = { build ->
            val type = viewModel.selectedType
            viewModel.selectBuild(build, keepProject = true)
            if (type != viewModel.selectedType) closeVersions()
        })
    } else if (viewModel.showInstallDialog) {
        VersionInstallDialog(
            addToCart = viewModel.selectedType.usesCart,
            loading = viewModel.isLoadingProjectDetails,
            error = viewModel.projectDetailsError,
            versions = viewModel.filterBuild?.let { build ->
                viewModel.projectVersions.filter { ModificationInstaller.isCompatible(it, build, viewModel.selectedType.installDir) }
            } ?: viewModel.projectVersions,
            onDismissRequest = ::closeVersions,
            onInstallClick = { version ->
                val project = viewModel.selectedProject
                closeVersions()
                if (viewModel.selectedType.usesCart) {
                    if (project == null) return@VersionInstallDialog
                    cart.add(CartItem(provider, project.title, viewModel.selectedType, version))
                    viewModel.closeProject()
                } else if (viewModel.selectedType == ModificationType.MODPACKS) {
                    installing = true
                    provider.installModpack(version, buildManager, installationScope, onComplete = {
                        installing = false
                        onModpackInstalled()
                    }, onError = { message ->
                        installing = false
                        installationScope.launch { snackbarHostState.showSnackbar(message) }
                    })
                } else {
                    pendingVersion = version
                    worldBuild = viewModel.selectedBuild
                }
            }
        )
    }
    pendingVersion?.takeIf { worldBuild == null }?.let { version ->
        InstallModificationDialog(
            builds = viewModel.allBuilds,
            version = version,
            directory = ModificationType.DATAPACKS.installDir,
            onDismiss = { pendingVersion = null },
            onInstall = { build ->
                worldBuild = build
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
                                installVersion(version, build, ModificationType.DATAPACKS, world.name)
                            }
                        }) { Text(world.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { worldBuild = null; pendingVersion = null }) { Text("Отмена") } }
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        shape = RoundedCornerShape(percent = 50),
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                        modifier = Modifier.fillMaxWidth(0.6f).padding(end = 16.dp).height(52.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
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
                },
                actions = {
                    if (viewModel.selectedType != ModificationType.MODPACKS) {
                        TextButton(onClick = { showingCart = true }) { Text("Корзина (${cart.items.size})") }
                        OutlinedButton(onClick = { pickingBuild = true }) {
                            Text(viewModel.selectedBuild?.name ?: "Выбрать сборку", maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                        }
                    }
                    if (providers.size > 1) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) { Text(provider.displayName) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                providers.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.displayName) }, onClick = {
                                        expanded = false
                                        onSelectProvider(option)
                                    })
                                }
                            }
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
                AnimatedVisibility(visible = viewModel.isFilterPanelVisible) {
                    FilterPanel(viewModel = viewModel)
                }

                // Основной контент
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    ModificationList(viewModel = viewModel, installEnabled = !installing && (!cart.busy || !viewModel.selectedType.usesCart)) { hit ->
                        fromList = true
                        viewModel.selectProject(hit)
                        viewModel.showInstallDialog = true
                    }
                }
            }

            MateriaDock(
                items = listOf(
                    MateriaDockItem.action(stringResource(Res.string.back), Icons.AutoMirrored.Filled.ArrowBack, onBack)
                ) + viewModel.availableTypes.map { type ->
                    MateriaDockItem.tab(type.displayName, type.icon, viewModel.selectedType == type) {
                        viewModel.selectedProject = null
                        viewModel.selectedType = type
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).width(500.dp)
            )
        }
    }
}

@Composable
private fun LegacyModificationsNavigation(
    viewModel: ModificationsViewModel,
    onBack: () -> Unit
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
