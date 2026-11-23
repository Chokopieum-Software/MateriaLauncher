package ui.screens.modifications

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.managers.CacheManager
import funlauncher.managers.PathManager
import funlauncher.modpack.CatalogBuilds
import funlauncher.net.*
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import ui.screens.FilterState
import ui.screens.ModificationType

class CatalogProviderTest {
    @Test fun catalogSearchesAndClosesTheSelectedProvider() = runBlocking {
        val directory = Files.createTempDirectory("catalog-provider")
        val builds = object : CatalogBuilds {
            override suspend fun loadBuilds() = emptyList<MinecraftBuild>()
            override fun getBuildPath(buildName: String) = error("unused")
            override suspend fun addBuild(name: String, version: String, type: BuildType, imagePath: String?): Path = error("unused")
        }
        val cache = CacheManager(PathManager(directory))
        try {
            for (source in listOf(TestProvider("first", 7), TestProvider("second", 12))) {
                val vm = ModificationsViewModel(builds, { emptyList() }, cache, source, this)
                vm.searchQuery = "example"
                vm.selectedType = ModificationType.DATAPACKS
                vm.selectedVersions.add("1.21")
                vm.selectedCategories["adventure"] = FilterState.INCLUDED
                vm.search()
                coroutineContext.job.children.toList().joinAll()
                assertEquals(source.total, vm.searchResult?.totalHits)
                assertEquals(CatalogSearch("example", "datapack", listOf("1.21"), listOf("adventure"),
                    emptyList(), 0, 20), source.request)
                val installation = source.open()
                vm.onDispose()
                assertEquals(1, source.closed)
                assertEquals(source.total, installation.search(source.request!!).totalHits)
                installation.close()
                assertEquals(2, source.closed)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class TestProvider(override val id: String, val total: Int) : CatalogProvider {
        override val displayName = id
        var request: CatalogSearch? = null
        var closed = 0

        override fun open() = object : CatalogSession {
            private var active = true
            override suspend fun search(request: CatalogSearch): SearchResult {
                check(active)
                this@TestProvider.request = request
                return SearchResult(emptyList(), request.offset, request.limit, total)
            }
            override fun close() { active = false; closed++ }
            override suspend fun getCategories() = emptyList<CategoryTag>()
            override suspend fun getLoaders() = emptyList<LoaderTag>()
            override suspend fun getProject(id: String): Project = error("unused")
            override suspend fun getVersion(id: String): Version = error("unused")
            override suspend fun getProjectVersions(id: String) = emptyList<Version>()
            override fun projectUrl(project: Project) = "https://example.org/" + project.id
        }

        override fun installModpack(version: Version, builds: CatalogBuilds, scope: CoroutineScope,
                                    onComplete: () -> Unit, onError: (String) -> Unit) = error("unused")
    }
}
