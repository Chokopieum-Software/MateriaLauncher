package funlauncher

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.*

class SingleInstanceLockTest {
    @Test fun rejectsDuplicateAndAllowsRestart() {
        val directory = Files.createTempDirectory("launcher-lock-test")
        try {
            assertNotNull(SingleInstanceLock.acquire(directory)).use {
                assertNull(SingleInstanceLock.acquire(directory))
            }
            assertNotNull(SingleInstanceLock.acquire(directory)).close()
        } finally {
            Files.deleteIfExists(directory.resolve("launcher.lock"))
            Files.delete(directory)
        }
    }

    @Test fun releasesLockAfterProcessIsKilled() {
        val directory = Files.createTempDirectory("launcher-process-lock-test")
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
            LockHoldingProcess::class.java.name, directory.toString()).redirectErrorStream(true).start()
        try {
            val ready = process.inputStream.bufferedReader().readLine()
            assertEquals("locked", ready)
            assertNull(SingleInstanceLock.acquire(directory))
            process.destroyForcibly()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertNotNull(SingleInstanceLock.acquire(directory)).close()
        } finally {
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
            Files.deleteIfExists(directory.resolve("launcher.lock"))
            Files.delete(directory)
        }
    }
}

object LockHoldingProcess {
    @JvmStatic fun main(args: Array<String>) {
        checkNotNull(SingleInstanceLock.acquire(Path.of(args[0]))).use {
            println("locked")
            System.out.flush()
            Thread.sleep(60_000)
        }
    }
}
