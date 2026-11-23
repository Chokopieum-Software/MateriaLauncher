package funlauncher.net

import com.sun.net.httpserver.HttpServer
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class ModificationInstallerTest {
    private fun modVersion(loader: String = "fabric", minecraft: String = "1.21.11") = Version(
        id = "version", projectId = "project", authorId = null, featured = false,
        name = "Test mod", versionNumber = "1.0", changelog = null, changelogUrl = null,
        datePublished = "2026-01-01T00:00:00Z", downloads = 0, versionType = "release",
        files = emptyList(), gameVersions = listOf(minecraft), loaders = listOf(loader)
    )

    @Test fun acceptsMatchingMinecraftAndLoaderForNativeAndImportedProfiles() {
        for (type in BuildType.entries.filter { it != BuildType.VANILLA }) {
            val loader = type.name.lowercase()
            val versions = mutableListOf("1.21.11", "1.21.11-$loader-0.18.0")
            if (type == BuildType.FABRIC || type == BuildType.QUILT) {
                versions += "$loader-loader-0.18.0-1.21.11"
                versions += "$loader-loader-0.18.0-1.21.11-rc1"
            }
            for (id in versions) {
                val minecraft = if (id.endsWith("-rc1")) "1.21.11-rc1" else "1.21.11"
                val build = MinecraftBuild("Test", id, type, "unused")
                assertTrue(ModificationInstaller.isCompatible(modVersion(loader, minecraft), build, "mods"), id)
                assertFalse(ModificationInstaller.isCompatible(modVersion(loader, "1.21.1"), build, "mods"), id)
            }
        }
    }

    @Test fun rejectsWrongLoaderAndVanillaEvenWhenMinecraftMatches() {
        for (type in listOf(BuildType.FORGE, BuildType.NEOFORGE, BuildType.VANILLA)) {
            val build = MinecraftBuild("Test", "1.21.11", type, "unused")
            assertFalse(ModificationInstaller.isCompatible(modVersion(), build, "mods"))
            assertTrue(ModificationInstaller.isCompatible(modVersion(), build, "resourcepacks"))
        }
    }

    @Test fun explainsMinecraftAndLoaderMismatchesIndependently() {
        val build = MinecraftBuild("Test", "fabric-loader-0.18.0-1.21.11", BuildType.FABRIC, "unused")
        assertTrue(ModificationInstaller.incompatibilityReasons(modVersion(), build, "mods").isEmpty())
        val minecraftReasons = ModificationInstaller.incompatibilityReasons(modVersion(minecraft = "1.21.1"), build, "mods")
        assertEquals(1, minecraftReasons.size)
        assertContains(minecraftReasons.single(), "Версия Minecraft")
        assertContains(minecraftReasons.single(), "в инстансе 1.21.11;")
        assertContains(minecraftReasons.single(), "поддерживаются: 1.21.1")
        val loaderReasons = ModificationInstaller.incompatibilityReasons(modVersion(loader = "forge"), build, "mods")
        assertEquals(1, loaderReasons.size)
        assertContains(loaderReasons.single(), "Загрузчик")
        assertContains(loaderReasons.single(), "FABRIC")
        assertContains(loaderReasons.single(), "forge")
        val wrongBoth = modVersion(loader = "forge", minecraft = "1.21.1")
        assertEquals(2, ModificationInstaller.incompatibilityReasons(wrongBoth, build, "mods").size)
        assertEquals(1, ModificationInstaller.incompatibilityReasons(wrongBoth, build, "resourcepacks").size)
        assertEquals(2, ModificationInstaller.incompatibilityReasons(
            modVersion().copy(gameVersions = emptyList(), loaders = emptyList()), build, "mods").size)
    }

    @Test fun rejectsEscapingPaths() {
        val root = Files.createTempDirectory("mod-path-test")
        try {
            for (path in listOf("../outside", "/outside", "C:/outside", "mods/../../outside", "mods\\evil", "")) {
                assertFailsWith<IllegalArgumentException>(path) { ModificationDownloader.safeResolve(root, path) }
            }
            assertEquals(root.resolve("mods/test.jar"), ModificationDownloader.safeResolve(root, "mods/test.jar"))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun verifiesDownloadsBeforeReplacingExistingFile() = runBlocking {
        val root = Files.createTempDirectory("mod-download-test")
        val data = "verified mod".toByteArray()
        val digest = MessageDigest.getInstance("SHA-512").digest(data).joinToString("") { "%02x".format(it) }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { it.sendResponseHeaders(200, data.size.toLong()); it.responseBody.use { output -> output.write(data) } }
        server.createContext("/error") { it.sendResponseHeaders(500, -1); it.close() }
        server.start()
        try {
            val destination = root.resolve("test.jar")
            Files.writeString(destination, "original")
            val downloader = ModificationDownloader()
            val base = "http://127.0.0.1:"+server.address.port
            suspend fun rejected(url: String, size: Long, hash: String) {
                var failed = false
                try { downloader.downloadVerified(url, destination, size, mapOf("sha512" to hash)) }
                catch (e: IllegalStateException) { failed = true }
                assertTrue(failed)
                assertEquals("original", Files.readString(destination))
                assertEquals(1L, Files.list(root).use { it.count() })
            }
            rejected(base+"/error", data.size.toLong(), digest)
            rejected(base+"/ok", data.size.toLong(), "0".repeat(128))
            rejected(base+"/ok", data.size.toLong()+1, digest)
            downloader.downloadVerified(base+"/ok", destination, data.size.toLong(), mapOf("sha512" to digest))
            assertContentEquals(data, Files.readAllBytes(destination))
        } finally { server.stop(0); root.toFile().deleteRecursively() }
    }
}
