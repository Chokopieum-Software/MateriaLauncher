package funlauncher.net

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.game.VersionInfo
import funlauncher.managers.BuildManager
import funlauncher.managers.PathManager
import funlauncher.utils.Hashing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import java.nio.file.Files
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame
import java.nio.channels.UnresolvedAddressException
import java.net.UnknownHostException

class FileDownloaderTest {
    @Test
    fun cancellationStopsParallelDownloadsAndRetries() = runBlocking {
        val root = Files.createTempDirectory("cancel-download-test")
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val paths = PathManager(root)
            val info = VersionInfo(id = "test", libraries = (1..24).map { index ->
                VersionInfo.Library("test:library:$index", downloads = VersionInfo.LibraryDownloads(
                    artifact = VersionInfo.Artifact("test/$index.jar", "http://127.0.0.1:${server.address.port}/$index", 3)
                ))
            })
            val downloader = FileDownloader(info, paths, BuildManager(paths))
            val job = launch {
                downloader.downloadRequiredFiles(
                    MinecraftBuild("test", "test", BuildType.VANILLA, root.toString())
                ) { _, _ -> }
            }
            withTimeout(5_000) {
                while (requests.get() == 0) delay(10)
                // Allow active HTTP failures to enter their retry delay.
                delay(200)
                job.cancelAndJoin()
            }
            val requestsAtCancellation = requests.get()
            delay(3_200)
            assertEquals(requestsAtCancellation, requests.get(), "Cancelled downloads must not retry")
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun dnsFailureIncludesHostAndPreservesCause() {
        for (cause in listOf(UnresolvedAddressException(), UnknownHostException())) {
            val error = downloadFailure(
                "https://resources.download.minecraft.net/ab/hash", "Asset", cause
            )
            assertTrue(error.message.orEmpty().contains("resources.download.minecraft.net"))
            assertTrue(error.message.orEmpty().contains("DNS"))
            assertTrue(!error.message.orEmpty().contains("null"))
            assertSame(cause, error.cause)
        }
    }

    @Test
    fun repeatedLaunchReusesLibrariesWithUnknownIntegrityAndClassifiers() = runBlocking {
        val root = Files.createTempDirectory("library-cache-test")
        try {
            val paths = PathManager(root)
            val bytes = byteArrayOf(1, 2, 3)
            val artifactPath = "test/library/1/library-1.jar"
            val nativePath = "test/library/1/library-1-natives-windows.jar"
            val checkedPath = "test/checked/1/checked-1.jar"
            for (path in listOf(artifactPath, nativePath, checkedPath)) {
                paths.getGlobalLibrariesDir().resolve(path).apply {
                    parent.createDirectories()
                    writeBytes(bytes)
                }
            }
            // Unreachable URLs ensure a cache hit never makes a network request.
            val info = VersionInfo(id = "test", libraries = listOf(
                VersionInfo.Library("test:library:1", downloads = VersionInfo.LibraryDownloads(
                    artifact = VersionInfo.Artifact(artifactPath, "http://127.0.0.1:1/missing", 0, "")
                )),
                VersionInfo.Library("test:library:1:natives-windows", url = "http://127.0.0.1:1/"),
                // Minecraft 1.12.2 platform entries have classifiers but no main JAR.
                // Provide every OS mapping so this also checks native cache reuse on CI.
                VersionInfo.Library("net.java.jinput:jinput-platform:2.0.5",
                    url = "http://127.0.0.1:1/",
                    downloads = VersionInfo.LibraryDownloads(classifiers = mapOf(
                        "test-native" to VersionInfo.Artifact(nativePath, "http://127.0.0.1:1/missing", 3)
                    )),
                    natives = mapOf("windows" to "test-native", "linux" to "test-native", "osx" to "test-native")),
                VersionInfo.Library("test:legacy-platform:1", url = "http://127.0.0.1:1/",
                    natives = mapOf("unsupported-os" to "test-native")),
                VersionInfo.Library("test:checked:1", downloads = VersionInfo.LibraryDownloads(
                    artifact = VersionInfo.Artifact(checkedPath, "http://127.0.0.1:1/missing", 3,
                        Hashing.getSha1(paths.getGlobalLibrariesDir().resolve(checkedPath)).uppercase())
                ))
            ))
            val build = MinecraftBuild("test", "test", BuildType.VANILLA, root.toString())
            repeat(2) {
                // A swallowed 404/retry must fail the test too, rather than pass after delays.
                withTimeout(2_000L) {
                    FileDownloader(info, paths, BuildManager(paths)).downloadRequiredFiles(build) { _, _ -> }
                }
            }
            for (path in listOf(artifactPath, nativePath, checkedPath)) {
                assertContentEquals(bytes, paths.getGlobalLibrariesDir().resolve(path).readBytes())
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
