package funlauncher.net

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class ModificationInstallerTest {
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
