package ui.screens.modifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import funlauncher.MinecraftBuild
import funlauncher.game.VersionMetadataFetcher
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.PathManager
import funlauncher.net.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.screens.FilterState
import ui.screens.ModificationType

class ModificationsViewModel(
    private val buildManager: BuildManager,
    private val pathManager: PathManager,
    private val cacheManager: CacheManager,
    private val coroutineScope: CoroutineScope
) {
    val api = ModrinthApi(cacheManager)
    private val versionMetadataFetcher = VersionMetadataFetcher(buildManager, pathManager)

    var searchQuery by mutableStateOf("")
    var searchResult by mutableStateOf<SearchResult?>(null)
    var isLoading by mutableStateOf(false)

    var showVersionSelectionDialog by mutableStateOf(false)
    var showInstallDialog by mutableStateOf(false)

    // Pagination states
    var currentPage by mutableStateOf(0)
    val pageSize = 20
    var isLoadingMore by mutableStateOf(false)
    var hasMoreResults by mutableStateOf(true)

    var selectedProject by mutableStateOf<Project?>(null)
    var projectVersions by mutableStateOf<List<Version>>(emptyList())
    var isLoadingProjectDetails by mutableStateOf(false)
    var projectDetailsError by mutableStateOf<String?>(null)
    private var projectDetailsJob: Job? = null

    val allBuilds = mutableStateListOf<MinecraftBuild>()

    var selectedType by mutableStateOf(ModificationType.MODS) // Default to MODS

    val allCategories = mutableStateListOf<ModrinthCategoryTag>()
    val allLoaders = mutableStateListOf<ModrinthLoaderTag>()

    val filteredCategories = mutableStateListOf<ModrinthCategoryTag>()
    val filteredLoaders = mutableStateListOf<ModrinthLoaderTag>()

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
                versionMetadataFetcher.getVanillaVersions()
            }
            if (cachedVersions != null) {
                withContext(Dispatchers.Main) {
                    allVanillaVersions.clear()
                    allVanillaVersions.addAll(cachedVersions)
                }
            }

            try {
                val freshVersions = versionMetadataFetcher.getVanillaVersions()
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

    fun buildFacetsList(): MutableList<List<String>> {
        val facetsList = mutableListOf<List<String>>()

        // Project Type
        val actualProjectType = if (selectedType == ModificationType.DATAPACKS) {
            ModificationType.MODS.projectType // Datapacks используют project_type 'mod' для поиска
        } else {
            selectedType.projectType
        }
        facetsList.add(listOf("project_type:$actualProjectType"))
        
        if (selectedType == ModificationType.DATAPACKS) facetsList.add(listOf("categories:datapack"))

        // Game Versions
        if (selectedVersions.isNotEmpty()) {
            facetsList.add(selectedVersions.map { "versions:$it" })
        }

        // Categories
        val includedCategories = selectedCategories.filter { it.value == FilterState.INCLUDED }.keys
        if (includedCategories.isNotEmpty()) {
            facetsList.add(includedCategories.map { "categories:$it" })
        }

        // Loaders
        val includedLoaders = selectedLoaders.filter { it.value == FilterState.INCLUDED }.keys
        if (includedLoaders.isNotEmpty()) {
            facetsList.add(includedLoaders.map { "categories:$it" })
        }

        return facetsList
    }

    // Helper to manually construct the facets string in the format Modrinth API expects
    fun buildFacetsString(facetsList: List<List<String>>): String {
        return facetsList.joinToString(separator = ",", prefix = "[", postfix = "]") { innerList ->
            innerList.joinToString(separator = ",", prefix = "[", postfix = "]") { facet ->
                "\"$facet\""
            }
        }
    }

    fun search() {
        if (selectedProject != null) return

        isLoading = true
        searchResult = null
        currentPage = 0
        hasMoreResults = true

        val facetsList = buildFacetsList()
        val facetsJson = buildFacetsString(facetsList)

        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    api.search(query = searchQuery, facets = facetsJson, offset = currentPage * pageSize, limit = pageSize)
                }
                searchResult = result
                hasMoreResults = result.hits.size == pageSize
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchResult = SearchResult(emptyList(), 0, pageSize, 0)
                hasMoreResults = false
            } finally {
                isLoading = false
            }
        }
    }

    fun loadNextPage() {
        if (isLoadingMore || !hasMoreResults || isLoading) return

        isLoadingMore = true
        currentPage++

        val facetsList = buildFacetsList()
        val facetsJson = buildFacetsString(facetsList)

        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    api.search(query = searchQuery, facets = facetsJson, offset = currentPage * pageSize, limit = pageSize)
                }

                searchResult = searchResult?.copy(
                    hits = searchResult!!.hits + result.hits
                ) ?: result

                hasMoreResults = result.hits.size == pageSize
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentPage--
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun filterCategoriesAndLoaders() {
        filteredCategories.clear()
        selectedCategories.clear() // Clear selections when type changes
        filteredLoaders.clear()
        selectedLoaders.clear() // Clear selections when type changes

        val targetProjectType = if (selectedType == ModificationType.DATAPACKS) {
            ModificationType.MODS.projectType // Datapacks use mod project type for categories
        } else {
            selectedType.projectType
        }

        filteredCategories.addAll(allCategories.filter { it.project_type == targetProjectType })

        // Исправленный список серверных ядер, которые нужно исключать из модов
        val serverCoreLoadersToExclude = setOf("paper", "spigot", "purpur")

        filteredLoaders.addAll(allLoaders.filter { loader ->
            loader.supported_project_types.contains(selectedType.projectType) &&
                    !(selectedType == ModificationType.MODS && serverCoreLoadersToExclude.contains(loader.name.lowercase()))
        })
    }

}
