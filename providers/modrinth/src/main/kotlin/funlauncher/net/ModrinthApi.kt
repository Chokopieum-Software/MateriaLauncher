package funlauncher.net

import funlauncher.managers.CacheManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.defaultRequest
import java.nio.channels.UnresolvedAddressException

private const val MODRINTH_API_URL = "https://api.modrinth.com/v2"

class ModrinthApi(private val cacheManager: CacheManager) : CatalogSession {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true // Полезно, если API вернет null там, где мы не ждем
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.BODY
        }
        // Add a default User-Agent to all requests, as recommended by Modrinth API docs
        defaultRequest {
            header("User-Agent", "chokopieum/materiakraft-launcher (chokopieum@gmail.com)")
        }
    }

    private suspend fun <T> safeApiCall(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: UnresolvedAddressException) {
            throw IllegalStateException("Failed to resolve host. Please check your internet connection.", e)
        }
    }

    override fun projectUrl(project: Project) = "https://modrinth.com/${project.projectType}/${project.slug}"

    override suspend fun getCategories(): List<CategoryTag> = rawCategories().flatMap { category ->
        if (category.project_type == "mod") listOf(category, category.copy(project_type = "datapack"))
        else listOf(category)
    }

    override suspend fun getLoaders(): List<LoaderTag> = rawLoaders().map { loader ->
        if (loader.name.lowercase() in setOf("paper", "spigot", "purpur"))
            loader.copy(supported_project_types = loader.supported_project_types - "mod")
        else loader
    }

    override suspend fun search(request: CatalogSearch): SearchResult {
        return search(request.query, Json.encodeToString(searchFacets(request)), request.limit, request.offset)
    }

    suspend fun search(
        query: String,
        facets: String,
        limit: Int = 20,
        offset: Int = 0
    ): SearchResult {
        val cacheKey = "modrinth_search_${query}_${facets}_${limit}_${offset}"
        return cacheManager.getOrFetch(cacheKey) {
            safeApiCall {
                val response = client.get("$MODRINTH_API_URL/search") {
                    parameter("query", query)
                    parameter("facets", facets)
                    parameter("limit", limit)
                    parameter("offset", offset)
                }
                response.body()
            }
        } ?: throw IllegalStateException("Failed to fetch search results")
    }

    override suspend fun getProject(id: String): Project {
        val cacheKey = "modrinth_project_$id"
        return cacheManager.getOrFetch(cacheKey) {
            safeApiCall {
                val response = client.get("$MODRINTH_API_URL/project/$id")
                response.body()
            }
        } ?: throw IllegalStateException("Failed to fetch project details")
    }

    override suspend fun getProjectVersions(id: String): List<Version> {
        val cacheKey = "modrinth_project_versions_$id"
        return cacheManager.getOrFetch(cacheKey) {
            safeApiCall {
                val response = client.get("$MODRINTH_API_URL/project/$id/version")
                response.body()
            }
        } ?: throw IllegalStateException("Failed to fetch project versions")
    }

    override suspend fun getVersion(id: String): Version = safeApiCall {
        client.get("$MODRINTH_API_URL/version/$id").body()
    }

    // --- RAW DATA METHODS ---

    private suspend fun rawCategories(): List<CategoryTag> {
        val cacheKey = "modrinth_categories"
        return cacheManager.getOrFetch(cacheKey) {
            safeApiCall {
                client.get("$MODRINTH_API_URL/tag/category").body()
            }
        } ?: emptyList()
    }

    private suspend fun rawLoaders(): List<LoaderTag> {
        val cacheKey = "modrinth_loaders"
        return cacheManager.getOrFetch(cacheKey) {
            safeApiCall {
                client.get("$MODRINTH_API_URL/tag/loader").body()
            }
        } ?: emptyList()
    }

    suspend fun getGameVersions(): List<ModrinthGameVersionTag> {
        val cacheKey = "modrinth_game_versions"
        return cacheManager.getOrFetch(cacheKey) {
            safeApiCall {
                client.get("$MODRINTH_API_URL/tag/game_version").body()
            }
        } ?: emptyList()
    }

    // --- SMART AGGREGATED FILTER METHOD ---

    /**
     * Запрашивает все теги и группирует их для удобного использования в UI.
     * Возвращает фильтры отдельно для модов, шейдеров, ресурспаков и т.д.
     */
    suspend fun getSmartFilters(): AppFilters {
        // Запрашиваем данные (можно параллельно через async/await, если CacheManager поддерживает)
        val categories = rawCategories()
        val loaders = rawLoaders()
        val gameVersionsRaw = getGameVersions()

        // 1. Фильтруем версии игры (только релизы и снепшоты, сортируем сами если нужно)
        val validVersions = gameVersionsRaw
            .filter { it.version_type == "release" } // Берем только релизы
            .map { it.version }

        // 2. Подготавливаем структуру
        val filterMap = mutableMapOf<String, ProjectTypeFilters>()
        val projectTypes = listOf("mod", "resourcepack", "shader", "modpack")

        // Инициализируем пустые фильтры
        projectTypes.forEach { type ->
            filterMap[type] = ProjectTypeFilters(categories = mutableListOf(), loaders = mutableListOf())
        }

        // 3. Распределяем Категории (Жанры)
        categories.forEach { cat ->
            if (filterMap.containsKey(cat.project_type)) {
                filterMap[cat.project_type]?.categories?.add(
                    FilterItem(name = cat.name, icon = cat.icon)
                )
            }
        }

        // 4. Распределяем Лоадеры (Fabric, Forge...)
        loaders.forEach { loader ->
            loader.supported_project_types.forEach { type ->
                if (filterMap.containsKey(type)) {
                    filterMap[type]?.loaders?.add(loader.name)
                }
            }
        }

        // 5. Специальная логика для Datapacks
        // Датапаки в Modrinth технически являются модами, но мы хотим для них отдельный фильтр.
        // Они используют категории модов, но лоадер у них жестко "datapack".
        val datapackFilters = ProjectTypeFilters(
            categories = filterMap["mod"]?.categories?.toMutableList() ?: mutableListOf(),
            loaders = mutableListOf("datapack")
        )

        return AppFilters(
            mods = filterMap["mod"]!!,
            resourcePacks = filterMap["resourcepack"]!!,
            shaders = filterMap["shader"]!!,
            modpacks = filterMap["modpack"]!!,
            datapacks = datapackFilters,
            gameVersions = validVersions
        )
    }

    internal suspend fun getPopularProjects(projectType: String): SearchResult {
        return safeApiCall {
            val response = client.get("$MODRINTH_API_URL/search") {
                parameter("facets", "[[\"project_type:$projectType\"]]")
                parameter("index", "downloads")
                parameter("limit", 20)
            }
            response.body()
        }
    }

    override fun close() {
        client.close()
    }
}

// --- NEW FILTER DATA CLASSES ---

@Serializable
data class AppFilters(
    val mods: ProjectTypeFilters,
    val resourcePacks: ProjectTypeFilters,
    val shaders: ProjectTypeFilters,
    val modpacks: ProjectTypeFilters,
    val datapacks: ProjectTypeFilters,
    val gameVersions: List<String>
)

@Serializable
data class ProjectTypeFilters(
    val categories: MutableList<FilterItem>, // Жанры (Adventure, Tech...)
    val loaders: MutableList<String>         // Загрузчики (Fabric, Iris...)
)

@Serializable
data class FilterItem(
    val name: String, // ID для поиска (в facets)
    val icon: String? // SVG иконка
)

// --- UPDATED API DTOs ---

@Serializable
data class ModrinthGameVersionTag(
    val version: String,
    val version_type: String, // release, snapshot, alpha, beta
    val date: String,
    val major: Boolean
)

internal fun searchFacets(request: CatalogSearch): List<List<String>> {
    val type = if (request.projectType == "datapack") "mod" else request.projectType
    return buildList {
        add(listOf("project_type:$type"))
        if (request.projectType == "datapack") add(listOf("categories:datapack"))
        if (request.versions.isNotEmpty()) add(request.versions.map { "versions:$it" })
        if (request.categories.isNotEmpty()) add(request.categories.map { "categories:$it" })
        (request.excludedCategories + request.excludedLoaders).distinct().forEach { add(listOf("categories!=$it")) }
        if (request.loaders.isNotEmpty()) add(request.loaders.map { "categories:$it" })
    }
}
