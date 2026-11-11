package funlauncher.modpack

import com.sun.net.httpserver.HttpServer
import funlauncher.database.DatabaseManager
import funlauncher.managers.BuildManager
import funlauncher.managers.PathManager
import funlauncher.net.Version
import funlauncher.net.File as ModFile
import funlauncher.net.Hashes
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ModpackLifecycleTest {
    @Test fun installationSurvivesLeavingScreen() = runBlocking {
        val root = Files.createTempDirectory("modpack-lifecycle-")
        val paths = PathManager(root)
        DatabaseManager.init(paths)
        val builds = BuildManager(paths)
        val bytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write("""{"formatVersion":1,"game":"minecraft","versionId":"test","name":"Lifecycle test","files":[],"dependencies":{"minecraft":"1.21.1"}}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/config/test.txt"))
                zip.write("installed".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val hash = MessageDigest.getInstance("SHA-512").digest(bytes).joinToString("") { "%02x".format(it) }
        val requested = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val completed = CompletableDeferred<Unit>()
        val application = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/pack") { exchange ->
            requested.complete(Unit)
            release.await(10, TimeUnit.SECONDS)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val version = Version("test", "project", null, false, "Test", "1", null, null,
                "2026-01-01", 0, "release", listOf(ModFile(Hashes("", hash),
                    "http://127.0.0.1:"+server.address.port+"/pack", "test.mrpack", true, bytes.size)))
            val installer = ModpackInstaller(builds, application)
            val screenJob = screen.launch {
                installer.install(version, { completed.complete(Unit) }, { completed.completeExceptionally(IllegalStateException(it)) })
                awaitCancellation()
            }
            withTimeout(10_000) { requested.await() }
            screenJob.cancelAndJoin()
            screen.cancel()
            release.countDown()
            withTimeout(15_000) { completed.await() }
            val build = builds.loadBuilds().single { it.name == "Lifecycle test" }
            assertEquals("installed", Files.readString(java.nio.file.Path.of(build.installPath).resolve("config/test.txt")))
        } finally {
            release.countDown()
            screen.cancel()
            application.coroutineContext[Job]?.cancelAndJoin()
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }
}
