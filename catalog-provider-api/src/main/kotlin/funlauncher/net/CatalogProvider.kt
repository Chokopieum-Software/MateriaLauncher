package funlauncher.net

import funlauncher.modpack.CatalogBuilds
import kotlinx.coroutines.CoroutineScope

data class ProviderLegal(val revision: String, val termsUrl: String, val privacyUrl: String)

/** Registered by the launcher. Each catalog screen and installation owns its own session. */
interface CatalogProvider {
    val id: String
    val displayName: String
    val legal: ProviderLegal? get() = null
    fun open(): CatalogSession
    fun installModpack(version: Version, builds: CatalogBuilds, scope: CoroutineScope,
                       onComplete: () -> Unit, onError: (String) -> Unit)
}

data class CatalogSearch(
    val query: String,
    val projectType: String,
    val versions: List<String>,
    val categories: List<String>,
    val loaders: List<String>,
    val offset: Int,
    val limit: Int,
    val excludedCategories: List<String> = emptyList(),
    val excludedLoaders: List<String> = emptyList()
)

interface CatalogSession : AutoCloseable {
    suspend fun search(request: CatalogSearch): SearchResult
    suspend fun getProject(id: String): Project
    suspend fun getProjectVersions(id: String): List<Version>
    suspend fun getVersion(id: String): Version
    suspend fun getCategories(): List<CategoryTag>
    suspend fun getLoaders(): List<LoaderTag>
    fun projectUrl(project: Project): String
}
