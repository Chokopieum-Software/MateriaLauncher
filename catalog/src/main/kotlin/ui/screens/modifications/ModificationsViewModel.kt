package ui.screens.modifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.modpack.CatalogBuilds
import funlauncher.managers.CacheManager
import funlauncher.net.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.screens.FilterState
import ui.screens.ModificationType

class ModificationsViewModel(
    private val buildManager: CatalogBuilds,
    private val loadVanillaVersions: suspend () -> List<String>,
    private val cacheManager: CacheManager,
    provider: CatalogProvider,
    private val coroutineScope: CoroutineScope
) {

    val api = provider.open()

    var searchQuery by mutableStateOf("")
    var searchResult by mutableStateOf<SearchResult?>(null)
    var isLoading by mutableStateOf(false)

    var showVersionSelectionDialog by mutableStateOf(false)
    var showInstallDialog by mutableStateOf(false)

    // Pagination states
    var currentPage by mutableStateOf(0)
    val pageSize = 20
    var isLoadingMore by mutableStateOf(false)
    var pageLoadFailed by mutableStateOf(false)
        private set
    var hasMoreResults by mutableStateOf(true)

    var selectedProject by mutableStateOf<Project?>(null)
    var projectVersions by mutableStateOf<List<Version>>(emptyList())
    var isLoadingProjectDetails by mutableStateOf(false)
    var projectDetailsError by mutableStateOf<String?>(null)
    private var projectDetailsJob: Job? = null

    val allBuilds = mutableStateListOf<MinecraftBuild>()

    var selectedType by mutableStateOf(ModificationType.MODS) // Default to MODS

    val allCategories = mutableStateListOf<CategoryTag>()
    val allLoaders = mutableStateListOf<LoaderTag>()

    val filteredCategories = mutableStateListOf<CategoryTag>()
    val filteredLoaders = mutableStateListOf<LoaderTag>()

    val selectedCategories = mutableStateMapOf<String, FilterState>() // Key is category.name
    val selectedLoaders = mutableStateMapOf<String, FilterState>() // Key is loader.name
    
    val allVanillaVersions = mutableStateListOf<String>()
    val selectedVersions = mutableStateListOf<String>()
    
    var categoriesExpanded by mutableStateOf(true)
    var loadersExpanded by mutableStateOf(true)
    
    var isFilterPanelVisible by mutableStateOf(true)

    fun init() {
        coroutineScope.launch {
            allBuilds.addAll(buildManager.loadBuilds())
        }
        loadInitialData()
        loadVersions()
    }

    private fun loadInitialData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val fetchedCategories = api.getCategories()
                withContext(Dispatchers.Main) {
                    allCategories.addAll(fetchedCategories)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Handle error
            }
            try {
                val fetchedLoaders = api.getLoaders()
                withContext(Dispatchers.Main) {
                    allLoaders.addAll(fetchedLoaders)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun loadVersions() {
        coroutineScope.launch(Dispatchers.IO) {
            val cachedVersions = cacheManager.getOrFetch<List<String>>("vanilla_versions_release") {
                loadVanillaVersions()
            }
            if (cachedVersions != null) {
                withContext(Dispatchers.Main) {
                    allVanillaVersions.clear()
                    allVanillaVersions.addAll(cachedVersions)
                }
            }

            try {
                val freshVersions = loadVanillaVersions()
                if (freshVersions.sorted() != cachedVersions?.sorted()) {
                    withContext(Dispatchers.Main) {
                        allVanillaVersions.clear()
                        allVanillaVersions.addAll(freshVersions)
                    }
                    cacheManager.getOrFetch<List<String>>("vanilla_versions_release") { freshVersions }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Handle error
            }
        }
    }


    fun onDispose() {
        api.close()
    }

    fun closeProject() {
        projectDetailsJob?.cancel()
        selectedProject = null
    }

    fun selectProject(hit: Hit) {
        projectDetailsJob?.cancel()
        projectDetailsError = null
        // Create a temporary Project object from the Hit to show immediately
        val tempProject = Project(
            id = hit.projectId,
            slug = hit.slug,
            projectType = hit.projectType,
            team = "", // Not available in Hit
            title = hit.title,
            description = hit.description,
            body = "", // Body is not available in Hit, will be loaded later
            bodyUrl = null,
            published = hit.dateCreated,
            updated = hit.dateModified,
            status = "approved", // Assume approved if it's in search results
            license = License(id = hit.license, name = hit.license, url = ""),
            clientSide = "required", // Default values
            serverSide = "required",
            downloads = hit.downloads,
            followers = hit.follows,
            categories = hit.categories,
            versionIds = emptyList(),
            iconUrl = hit.iconUrl,
            gameVersions = emptyList(),
            loaders = emptyList(),
            gallery = emptyList()
        )
        
        selectedProject = tempProject
        projectVersions = emptyList()
        isLoadingProjectDetails = true

        projectDetailsJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val fullProject = api.getProject(hit.projectId)
                withContext(Dispatchers.Main) {
                    if (selectedProject?.id != hit.projectId) return@withContext
                    selectedProject = fullProject
                }
                val versions = api.getProjectVersions(hit.projectId)
                withContext(Dispatchers.Main) {
                    if (selectedProject?.id == hit.projectId) projectVersions = versions
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (selectedProject?.id == hit.projectId) projectDetailsError = "Не удалось загрузить данные проекта. Проверьте подключение и откройте проект ещё раз."
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (selectedProject?.id == hit.projectId) isLoadingProjectDetails = false
                }
            }
        }
    }

    var selectedBuild by mutableStateOf<MinecraftBuild?>(null)
        private set

    internal val filterBuild: MinecraftBuild?
        get() = if (selectedType == ModificationType.MODPACKS) null else selectedBuild

    val availableTypes: List<ModificationType>
        get() = ModificationType.entries.filter {
            selectedBuild?.type != BuildType.VANILLA || it !in listOf(ModificationType.MODS, ModificationType.SHADERS)
        }

    fun selectBuild(build: MinecraftBuild?, keepProject: Boolean = false) {
        selectedBuild = build
        if (!keepProject || selectedType !in availableTypes) closeProject()
        if (selectedType !in availableTypes) selectedType = ModificationType.RESOURCE_PACKS
    }

    private val buildLoader: String?
        get() = filterBuild?.type?.takeUnless { it == BuildType.VANILLA }?.name?.lowercase()?.takeIf {
            selectedType == ModificationType.MODS
        }

    internal fun searchRequest(page: Int) = CatalogSearch(
        query = searchQuery,
        projectType = selectedType.projectType,
        versions = filterBuild?.let { listOf(ModificationInstaller.minecraftVersion(it)) } ?: selectedVersions.toList(),
        categories = selectedCategories.filterValues { it == FilterState.INCLUDED }.keys.toList(),
        loaders = if (filterBuild != null) listOfNotNull(buildLoader) else selectedLoaders.filterValues { it == FilterState.INCLUDED }.keys.toList(),
        offset = page * pageSize,
        limit = pageSize,
        excludedCategories = selectedCategories.filterValues { it == FilterState.EXCLUDED }.keys.toList(),
        excludedLoaders = if (filterBuild != null) emptyList() else selectedLoaders.filterValues { it == FilterState.EXCLUDED }.keys.toList()
    )

    private var searchJob: Job? = null
    private var pageJob: Job? = null

    fun search() {
        searchJob?.cancel()
        pageJob?.cancel()
        isLoadingMore = false
        pageLoadFailed = false
        isLoading = true
        searchResult = null
        currentPage = 0
        hasMoreResults = true

        val request = searchRequest(currentPage)

        searchJob = coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    api.search(request)
                }
                searchResult = result
                hasMoreResults = result.hits.size == pageSize
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchResult = SearchResult(emptyList(), 0, pageSize, 0)
                hasMoreResults = false
            } finally {
                if (kotlinx.coroutines.currentCoroutineContext().isActive) isLoading = false
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingMore || !hasMoreResults || isLoading) return

        isLoadingMore = true
        pageLoadFailed = false
        currentPage++

        val request = searchRequest(currentPage)

        pageJob = coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    api.search(request)
                }

                searchResult = searchResult?.copy(
                    hits = searchResult!!.hits + result.hits
                ) ?: result

                hasMoreResults = result.hits.size == pageSize
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentPage--
                pageLoadFailed = true
            } finally {
                if (kotlinx.coroutines.currentCoroutineContext().isActive) isLoadingMore = false
            }
        }
    }

    fun filterCategoriesAndLoaders() {
        filteredCategories.clear()
        selectedCategories.clear() // Clear selections when type changes
        filteredLoaders.clear()
        selectedLoaders.clear() // Clear selections when type changes

        filteredCategories.addAll(allCategories.filter { it.project_type == selectedType.projectType })
        filteredLoaders.addAll(allLoaders.filter { selectedType.projectType in it.supported_project_types })
    }
}
