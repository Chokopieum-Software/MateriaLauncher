package funlauncher.game

import funlauncher.managers.PathManager
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class FabricLoaderCacheTest {
    @Test
    fun recentLoaderCacheIsUsedWithoutNetworkAndIsScopedToMinecraftVersion() = runBlocking {
        val root = Files.createTempDirectory("materia-fabric-cache")
        val paths = PathManager(root)
        val cache = Files.createDirectories(paths.getCacheDir())
        try {
            cache.resolve("fabric_loader_1.20.1.json").writeText("""[{"version":"0.16.10","stable":true}]""")
            cache.resolve("fabric_loader_1.21.1.json").writeText("""[{"version":"0.16.14","stable":true}]""")
            val manager = VersionManager(paths)
            assertEquals(listOf("0.16.10"), manager.getFabricLoaderVersions("1.20.1").map { it.version })
            assertEquals(listOf("0.16.14"), manager.getFabricLoaderVersions("1.21.1").map { it.version })
        } finally {
            Files.walk(root).use { files -> files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
