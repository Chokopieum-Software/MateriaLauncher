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
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import ui.screens.CatalogCart
import ui.screens.CartItem
import ui.screens.FilterState
import ui.screens.ModificationType

class CatalogProviderTest {
    private val builds = object : CatalogBuilds {
        override suspend fun loadBuilds() = emptyList<MinecraftBuild>()
        override fun getBuildPath(buildName: String) = error("unused")
        override suspend fun addBuild(name: String, version: String, type: BuildType, imagePath: String?): Path = error("unused")
    }

    @Test fun catalogSearchesAndClosesTheSelectedProvider() = runBlocking {
        val directory = Files.createTempDirectory("catalog-provider")
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

    @Test fun selectedBuildControlsTabsAndCompatibilityFilters() = runBlocking {
        val directory = Files.createTempDirectory("catalog-build-selection")
        try {
            val vm = ModificationsViewModel(builds, { emptyList() }, CacheManager(PathManager(directory)),
                TestProvider("test", 0), this)
            vm.selectedVersions.add("1.18")
            vm.selectedLoaders["forge"] = FilterState.INCLUDED
            vm.selectedLoaders["quilt"] = FilterState.EXCLUDED
            vm.selectBuild(MinecraftBuild("Fabric", "fabric-loader-0.16.1-1.21", BuildType.FABRIC, "unused"))
            assertEquals(listOf("1.21"), vm.searchRequest(0).versions)
            assertEquals(listOf("fabric"), vm.searchRequest(1).loaders)
            val fabric = vm.selectedBuild
            vm.selectedType = ModificationType.MODPACKS
            assertNull(vm.filterBuild)
            assertEquals(listOf("1.18"), vm.searchRequest(0).versions)
            assertEquals(listOf("forge"), vm.searchRequest(1).loaders)
            assertEquals(listOf("quilt"), vm.searchRequest(0).excludedLoaders)
            vm.selectedType = ModificationType.MODS
            assertSame(fabric, vm.filterBuild)
            assertEquals(listOf("1.21"), vm.searchRequest(0).versions)
            assertEquals(listOf("fabric"), vm.searchRequest(0).loaders)
            vm.selectBuild(MinecraftBuild("Vanilla", "1.20.4", BuildType.VANILLA, "unused"))
            assertEquals(ModificationType.RESOURCE_PACKS, vm.selectedType)
            assertFalse(ModificationType.MODS in vm.availableTypes)
            assertFalse(ModificationType.SHADERS in vm.availableTypes)
            for (type in vm.availableTypes - ModificationType.MODPACKS) {
                vm.selectedType = type
                val request = vm.searchRequest(0)
                assertEquals(listOf("1.20.4"), request.versions)
                assertTrue(request.loaders.isEmpty(), type.name)
                assertTrue(request.excludedLoaders.isEmpty(), type.name)
            }
            vm.selectBuild(null)
            assertEquals(ModificationType.entries.toList(), vm.availableTypes)
            assertEquals(listOf("1.18"), vm.searchRequest(0).versions)
            vm.onDispose()
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun checkoutInstallsMixedContentAndKeepsFailedGroupsForRetry() = runBlocking {
        val root = Files.createTempDirectory("catalog-cart")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bytes = "catalog file".toByteArray()
        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        server.createContext("/file") { exchange ->
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val source = TestProvider("test", 0)
            val build = MinecraftBuild("Game", "1.21", BuildType.FABRIC, root.toString())
            val cart = CatalogCart()
            fun item(type: ModificationType, id: String, hash: String = sha1): CartItem {
                val file = File(Hashes(hash, ""), "http://127.0.0.1:${server.address.port}/file", id, true, bytes.size)
                val version = Version(id, id, null, false, id, "1", null, null, "2026-01-01T00:00:00Z",
                    0, "release", listOf(file), gameVersions = listOf("1.21"), loaders = listOf("fabric"))
                return CartItem(source, id, type, version)
            }
            val mod = item(ModificationType.MODS, "mod.jar")
            val shader = item(ModificationType.SHADERS, "shader.zip")
            val pack = item(ModificationType.RESOURCE_PACKS, "pack.zip")
            cart.add(mod)
            cart.add(mod.copy(version = mod.version.copy(versionNumber = "2")))
            cart.add(item(shader.type, shader.title, "0".repeat(40)))
            cart.add(pack)
            assertEquals(3, cart.items.size)
            assertEquals("2", cart.items.first().version.versionNumber)
            cart.install(build.copy(type = BuildType.VANILLA), this)
            assertFalse(cart.busy)
            cart.install(build, this)
            coroutineContext.job.children.toList().joinAll()
            assertContentEquals(bytes, Files.readAllBytes(root.resolve("mods/mod.jar")))
            assertEquals(listOf(shader.title, pack.title), cart.items.map { it.title })
            cart.add(shader)
            cart.install(build, this)
            coroutineContext.job.children.toList().joinAll()
            assertContentEquals(bytes, Files.readAllBytes(root.resolve("shaderpacks/shader.zip")))
            assertContentEquals(bytes, Files.readAllBytes(root.resolve("resourcepacks/pack.zip")))
            assertTrue(cart.items.isEmpty())
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
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
